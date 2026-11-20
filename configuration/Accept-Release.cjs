#!/usr/bin/env node
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const os = require('node:os');
const path = require('node:path');
const {createHash} = require('node:crypto');
const {spawnSync} = require('node:child_process');
const {parseArgs, parseEnv} = require('node:util');
const {inflateRawSync} = require('node:zlib');
const {validateEnvironment, validateGeocoderEnvironment} = require('./Check-Release.cjs');

const root = path.resolve(__dirname, '..');
const status = Object.freeze({pass:'PASS', fail:'FAIL', blocked:'BLOCKED_EXTERNAL', untested:'NOT_TESTED', missing:'NOT_IMPLEMENTED'});
const verdicts = new Set(Object.values(status));
const readJson = file => JSON.parse(fs.readFileSync(file, 'utf8'));
const project = 'cartiq-staging';
const names = {
  preflight:'01. Release preflight', engine:'02a. Docker Engine', composeVersion:'02b. Docker Compose v2',
  compose:'03a. Compose base and Firebase override', composeResolved:'03b. Compose environment rendering', nginx:'03c. Nginx syntax',
  env:'04. Production variables and secrets', https:'05. HTTPS trust, hostname and API routing',
  spring:'06a. Spring Boot readiness', forecast:'06b. FastAPI health and authentication',
  mysql:'07. Isolated MySQL connectivity and schema', firebase:'08a. Firebase backend and Web configuration',
  nativePush:'08b. Native notification configuration', pushDelivery:'08c. Firebase device registration and delivery',
  geocoder:'09a. Geocoder service configuration', pin:'09b. Indian PIN adapter and validation', geocoderLive:'09c. Live PIN coverage',
  eas:'10a. EAS project and Android/iOS build configuration', signing:'10b. Android/iOS signing readiness',
  artifacts:'11. Release artifact versions and SHA-256', backup:'12a. Isolated backup and restore', rollback:'12b. Rollback compatibility',
  retailer:'13. Retailer configuration and credit safety',
};

function shellEnv(extra = {}) {
  const safe = Object.fromEntries(Object.entries(process.env).filter(([key]) =>
    /^(PATH|PATHEXT|SYSTEMROOT|WINDIR|USERPROFILE|HOME|TEMP|TMP|JAVA_HOME|MAVEN_HOME|M2_HOME|APPDATA|LOCALAPPDATA|COMSPEC)$/i.test(key)));
  return {...safe, ...extra};
}

function execute(report, command, bin, args, {cwd=root, env=shellEnv(), timeout=30000} = {}) {
  const startedAt = new Date().toISOString();
  const result = spawnSync(bin, args, {cwd, env, timeout, encoding:'utf8', windowsHide:true, maxBuffer:2*1024*1024});
  report.commands.push({command, startedAt, finishedAt:new Date().toISOString(), exitCode:result.status,
    outcome:result.error?.code === 'ENOENT' ? 'unavailable' : result.error?.code === 'ETIMEDOUT' ? 'timeout' :
      result.error || result.signal ? 'failed_to_finish' : 'exited'});
  return result;
}

function settings(raw, keys) {
  assert(raw && typeof raw === 'object' && !Array.isArray(raw) && Object.keys(raw).every(key => keys.includes(key)), 'Unexpected acceptance input shape.');
  return raw;
}

function nonblank(value) { return typeof value === 'string' && value.trim().length > 0; }
function configuredPorts(input) {
  return Object.entries(input).filter(([key]) => key.endsWith('Port')).every(([, port]) => Number.isInteger(port) && port >= 1024 && port <= 65535);
}

function localhostProbe(port, url, headers={}) {
  return new Promise((resolve, reject) => {
    const request = http.get({host:'127.0.0.1', port, path:url, headers, timeout:4000, agent:false}, response => {
      const parts = [];
      let length = 0;
      response.on('data', chunk => {
        if ((length += chunk.length) > 65536) request.destroy(new Error('Local health body exceeded limit.'));
        else parts.push(chunk);
      });
      response.on('end', () => resolve({status:response.statusCode, body:Buffer.concat(parts).toString('utf8')}));
      response.on('error', reject);
    });
    request.on('timeout', () => request.destroy(new Error('Local health probe timed out.')));
    request.on('error', reject);
  });
}

function readVersion(file, expression) {
  return fs.readFileSync(path.join(root, file), 'utf8').match(expression)?.[1] || null;
}

// Read one bounded manifest from a packaged Spring Boot JAR without executing it.
function jarVersion(file) {
  const fd = fs.openSync(file, 'r');
  try {
    const size = fs.fstatSync(fd).size;
    const read = (start, length) => {
      assert(start >= 0 && length >= 0 && start + length <= size);
      const bytes = Buffer.alloc(length);
      assert.equal(fs.readSync(fd, bytes, 0, length, start), length);
      return bytes;
    };
    const tail = read(Math.max(0, size - 65557), Math.min(size, 65557));
    let end = tail.length - 22;
    while (end >= 0 && tail.readUInt32LE(end) !== 0x06054b50) end--;
    assert(end >= 0);
    const centralLength = tail.readUInt32LE(end + 12);
    assert(centralLength <= 8*1024*1024);
    const central = read(tail.readUInt32LE(end + 16), centralLength);
    for (let offset=0; offset+46<=central.length;) {
      assert.equal(central.readUInt32LE(offset), 0x02014b50);
      const name = central.readUInt16LE(offset+28);
      if (central.toString('utf8', offset+46, offset+46+name) === 'META-INF/MANIFEST.MF') {
        const local = central.readUInt32LE(offset+42);
        const header = read(local, 30);
        assert.equal(header.readUInt32LE(0), 0x04034b50);
        const packed = central.readUInt32LE(offset+20);
        const method = central.readUInt16LE(offset+10);
        assert(packed <= 65536 && central.readUInt32LE(offset+24) <= 65536 && [0,8].includes(method));
        const bytes = read(local+30+header.readUInt16LE(26)+header.readUInt16LE(28), packed);
        return (method === 8 ? inflateRawSync(bytes,{maxOutputLength:65536}) : bytes).toString('utf8')
          .match(/^Implementation-Version: ([\d.]+)\r?$/m)?.[1] || null;
      }
      offset += 46+name+central.readUInt16LE(offset+30)+central.readUInt16LE(offset+32);
    }
    return null;
  } finally { fs.closeSync(fd); }
}

async function artifactHash(file) {
  const hash = createHash('sha256');
  for await (const chunk of fs.createReadStream(file)) hash.update(chunk);
  return hash.digest('hex');
}

function fixedVersion(result, regex) {
  return `${result.stdout || ''}\n${result.stderr || ''}`.match(regex)?.[1] || null;
}

async function accept(options, env=process.env) {
  const report = {schemaVersion:1, startedAt:new Date().toISOString(), completedAt:null, overall:null, versions:{node:process.versions.node}, checks:[], commands:[]};
  const add = (id, result, summary, evidence=[]) => report.checks.push({id, requirement:names[id], status:result, timestamp:new Date().toISOString(), summary, evidence});
  const dir = path.resolve(options['output-dir'] || path.join(root, '.release', 'acceptance', report.startedAt.replace(/[:.]/g,'-')));
  let input;
  try {
    input = options.inputs ? settings(readJson(options.inputs), ['nginx','https','isolatedMysql','artifacts','priorEvidence','signing']) : {};
    if (input.nginx) {
      settings(input.nginx, ['binary','configuration','directory']);
      assert([input.nginx.binary,input.nginx.configuration,input.nginx.directory].every(nonblank));
    }
    if (input.https) {
      settings(input.https, ['origin','httpPort','ca','backendPort','forecastPort']);
      assert(configuredPorts(input.https));
    }
    if (input.isolatedMysql) {
      const mysql = settings(input.isolatedMysql,['binDirectory','artifact','previousArtifact','previousSha256','outputDirectory','edge']);
      assert([mysql.binDirectory,mysql.artifact,mysql.outputDirectory].every(nonblank));
      assert(mysql.previousArtifact === undefined || nonblank(mysql.previousArtifact) && /^[0-9a-f]{64}$/i.test(mysql.previousSha256));
      if (mysql.edge) {
        settings(mysql.edge,['origin','httpPort','ca','backendPort']);
        assert([mysql.edge.origin,mysql.edge.ca].every(nonblank) && mysql.edge.httpPort && mysql.edge.backendPort && configuredPorts(mysql.edge));
      }
    }
    if (input.signing) {
      const signing = settings(input.signing,['android','ios']);
      if (signing.android) {
        settings(signing.android,['keystore','alias']);
        assert(nonblank(signing.android.keystore) && /^[a-zA-Z0-9_.-]{1,100}$/.test(signing.android.alias));
      }
      if (signing.ios) {
        settings(signing.ios,['profile']);
        assert(nonblank(signing.ios.profile));
      }
    }
    assert(input.artifacts === undefined || Array.isArray(input.artifacts));
    assert(input.priorEvidence === undefined || Array.isArray(input.priorEvidence));
    add('preflight',status.pass,'Acceptance inputs parsed. Checks report only sanitized results and are read-only except for the disposable MySQL rehearsal.');
  } catch {
    add('preflight',status.fail,'Acceptance input JSON is unreadable or does not conform to the checked template.');
    return finish(report,dir);
  }

  try {
    const ledgerFile = path.join(os.homedir(),'.cartiq','quickcommerce-budget.txt');
    const initialLedger = await readLedger(ledgerFile);
    const java = execute(report,'java -version','java',['-version']);
    report.versions.java = fixedVersion(java,/^(?:openjdk|java) version "([\d._+-]+)"/m);
    const python = execute(report,'python --version','python',['--version']);
    report.versions.python = fixedVersion(python,/^Python ([\d.]+)\r?$/m);
    report.versions.expo = readJson(path.join(root, 'package-lock.json')).packages?.['node_modules/expo']?.version || null;
    report.versions.springBoot = readVersion('backend/pom.xml', /spring-boot-starter-parent<\/artifactId><version>([\d.]+)<\/version>/);
    report.versions.reactNative = readJson(path.join(root,'package.json')).dependencies['react-native'];
    report.versions.fastapiRequirement = readVersion('ai-service/requirements.txt', /^fastapi([<=>0-9.,]+)\r?$/m);

    const v2 = execute(report,'docker compose version --short','docker',['compose','version','--short']);
    const composeVersion = fixedVersion(v2,/^v?([1-9][0-9]*\.[0-9]+\.[0-9]+)(?:[^\r\n]*)$/m);
    report.versions.compose = Number(composeVersion?.split('.')[0]) >= 2 ? composeVersion : null;
    add('composeVersion',v2.status === 0 && report.versions.compose ? status.pass : status.blocked,
      report.versions.compose ? 'Docker Compose v2 command returned a version.' : 'Install the Docker CLI and Compose v2 plugin on the private staging host.');
    const endpoint = execute(report,'docker context inspect default (local endpoint check)','docker',
      ['context','inspect','default','--format','{{.Endpoints.docker.Host}}']);
    const localDocker = endpoint.status === 0 && /^(unix:\/\/(?:\/var\/run\/docker\.sock|\/run\/docker\.sock)|npipe:\/\/\/\/\.\/pipe\/docker_engine)$/i.test(endpoint.stdout.trim());
    if (!localDocker) add('engine',status.blocked,'A standard local Docker Engine context is required. Remote daemon contexts are excluded.');
    else {
      const engine = execute(report,'docker --context default version (engine)','docker',['--context','default','version','--format','{{.Server.Version}}']);
      report.versions.dockerEngine = fixedVersion(engine,/^([\d]+\.[\d]+\.[\d]+)(?:[.\w+-]*)$/m);
      add('engine',engine.status === 0 && report.versions.dockerEngine ? status.pass : status.blocked,
        engine.status === 0 && report.versions.dockerEngine ? 'Local Docker Engine responded.' : 'Start the local Docker Engine before acceptance.');
    }

    const compose = execute(report,'node configuration/Check-Release.cjs',process.execPath,['configuration/Check-Release.cjs']);
    add('compose',compose.status === 0 ? status.pass : status.fail,'Static topology, isolation and Firebase override checks.', ['compose.yml','configuration/compose.firebase.example.yml']);
    let values = {};
    let validEnv = false;
    if (!options['env-file']) add('env',status.blocked,'Supply the protected isolated staging environment via --env-file. No secret values were inspected.', ['configuration/production.env.example']);
    else {
      try {
        values = parseEnv(fs.readFileSync(options['env-file'],'utf8'));
        validateEnvironment(values,options.firebase);
        validEnv = true;
        add('env',status.pass,'Validated database, JWT, TLS, HTTPS origins and optional service settings. Values are omitted.', ['configuration/production.env.example']);
      } catch {
        add('env',status.fail,'The supplied staging environment file failed required presence, shape or isolation validation. Run Check-Release.cjs separately for field names.', ['configuration/Check-Release.cjs']);
      }
    }
    if (compose.status === 0 && validEnv && localDocker && report.versions.compose) {
      const baseArgs = ['--context','default','compose','--project-name',project,'--env-file',path.resolve(options['env-file']),'-f','compose.yml'];
      const base = execute(report,'docker compose config --quiet (base)','docker',[...baseArgs,'--profile','migration','config','--quiet'],{env:shellEnv(values)});
      const overlay = options.firebase ? execute(report,'docker compose config --quiet (Firebase override)','docker',
        [...baseArgs,'-f','configuration/compose.firebase.example.yml','--profile','migration','config','--quiet'],{env:shellEnv(values)}) : null;
      add('composeResolved',base.status === 0 && (!overlay || overlay.status === 0) ? status.pass : status.fail,
        options.firebase ? 'Rendered base and Firebase configurations with the supplied isolated environment.' : 'Rendered base configuration. Firebase rendering requires --firebase and its approved configuration.');
    } else add('composeResolved',status.blocked,'Compose v2, a local daemon context and a valid staging environment are needed for resolved validation.');

    if (input.nginx) {
      const {binary, configuration, directory} = input.nginx;
      const version = execute(report,'nginx -v',path.resolve(binary),['-v'],{cwd:path.resolve(directory)});
      report.versions.nginx = fixedVersion(version,/nginx\/([\d.]+)/);
      const syntax = execute(report,'nginx -t -c <rendered-local-config>',path.resolve(binary),['-t','-c',configuration],{cwd:path.resolve(directory)});
      add('nginx',syntax.status === 0 ? status.pass : status.fail,'Portable Nginx syntax check against the supplied rendered configuration.', ['configuration/nginx.conf','configuration/Render-LoopbackStagingProxy.ps1']);
    } else {
      const version = execute(report,'nginx -v','nginx',['-v']);
      report.versions.nginx = fixedVersion(version,/nginx\/([\d.]+)/);
      add('nginx',version.status === 0 ? status.untested : status.blocked,'Supply a rendered local Nginx configuration and its portable or installed binary.', ['configuration/Render-LoopbackStagingProxy.ps1']);
    }

    const tls = input.https || {};
    const mysqlEdge = !tls.origin && input.isolatedMysql?.edge;
    if ([tls.origin,tls.ca].every(nonblank) && tls.httpPort) {
      const check = execute(report,'node configuration/Test-StagingRelease.cjs (edge probe)',process.execPath,
        ['configuration/Test-StagingRelease.cjs','--origin',tls.origin,'--http-port',String(tls.httpPort),'--ca',path.resolve(tls.ca)]);
      add('https',check.status === 0 ? status.pass : status.fail,check.status === 0 ? 'Trusted localhost certificate, canonical redirect, security headers and safe API routes passed.' :
        'Local HTTPS trust, hostname, redirects or API routing failed. No response bodies were recorded.', ['configuration/Test-StagingRelease.cjs']);
    } else if (!mysqlEdge) add('https',status.blocked,'Supply the running loopback staging origin, HTTP redirect port and staging CA.');

    const recordHttp = async (id, command, probe, accepted='Local health probe passed.') => {
      const startedAt = new Date().toISOString();
      try {
        const info = await probe();
        report.commands.push({command,startedAt,finishedAt:new Date().toISOString(),exitCode:null,
          outcome:'http',httpStatuses:info.statuses});
        add(id,info.result,info.summary || accepted);
      } catch {
        report.commands.push({command,startedAt,finishedAt:new Date().toISOString(),exitCode:null,outcome:'connection_or_validation_failed'});
        add(id,status.fail,'The supplied local endpoint failed the request or response check.');
      }
    };
    if (tls.backendPort) await recordHttp('spring','GET loopback /actuator/health/readiness',async () => {
      const health = await localhostProbe(tls.backendPort,'/actuator/health/readiness');
      return {result:health.status === 200 && JSON.parse(health.body).status === 'UP' ? status.pass : status.fail,statuses:[health.status]};
    });
    if (tls.forecastPort) await recordHttp('forecast','GET loopback /health and /models/status',async () => {
      const health = await localhostProbe(tls.forecastPort,'/health');
      const anonymous = await localhostProbe(tls.forecastPort,'/models/status');
      const codes = [health.status,anonymous.status];
      if (health.status !== 200 || anonymous.status !== 401) return {result:status.fail,statuses:codes};
      const describedHealth = JSON.parse(health.body);
      if (describedHealth.status !== 'ok' || describedHealth.livePriceProvider !== false) return {result:status.fail,statuses:codes};
      const secret = (validEnv && values.AI_SERVICE_SECRET) || env.AI_SERVICE_SECRET;
      if (!nonblank(secret)) return {result:status.blocked,statuses:codes,summary:'Health and anonymous rejection passed. Supply the authorized internal secret to verify the authenticated route.'};
      const result = await localhostProbe(tls.forecastPort,'/models/status',{'X-Service-Token':secret});
      return {result:result.status === 200 ? status.pass : status.fail,statuses:[...codes,result.status]};
    });
    else add('forecast',status.blocked,'Supply the isolated forecast service loopback port and authorized service secret.');

    let pins = [];
    try {
      pins = await checkArtifacts(input.artifacts || [],{...env,...values});
      add('artifacts',pins.length ? status.pass : status.untested,pins.length ? 'Checked supplied component versions and pinned SHA-256. JAR manifests are verified when applicable.' :
        'Supply independently approved SHA-256 and version values for the release artifacts.', pins.map(item => item.file));
    } catch {
      add('artifacts',status.fail,'A supplied artifact, expected version or SHA-256 is missing or differs from its release pin.');
    }
    report.artifacts = pins.map(({sourcePath,...item}) => item);

    if (input.isolatedMysql && pins.some(pin => pin.component === 'backend' && pin.sourcePath === path.resolve(input.isolatedMysql.artifact))) {
      const mysql = input.isolatedMysql;
      const args = ['configuration/Test-IsolatedMySql.cjs','--artifact',path.resolve(mysql.artifact),
        '--mysql-bin',path.resolve(mysql.binDirectory),'--output-dir',path.resolve(mysql.outputDirectory)];
      let previousValid = false;
      if (mysql.previousArtifact) {
        try { previousValid = await artifactHash(mysql.previousArtifact) === mysql.previousSha256.toLowerCase(); } catch { /* Read failure is reported on rollback. */ }
        if (previousValid) {
          args.push('--previous-artifact',path.resolve(mysql.previousArtifact));
          report.previousBackend = {sha256:mysql.previousSha256.toLowerCase(),file:redactPath(path.resolve(mysql.previousArtifact),{...env,...values})};
        }
      }
      if (mysqlEdge) args.push('--edge-origin',mysqlEdge.origin,'--edge-http-port',String(mysqlEdge.httpPort),
        '--edge-ca',path.resolve(mysqlEdge.ca),'--edge-backend-port',String(mysqlEdge.backendPort));
      // The helper owns and shuts down its child processes and bounds its own probes.
      const check = execute(report,'node configuration/Test-IsolatedMySql.cjs (fresh disposable database)',process.execPath,args,{timeout:0});
      let evidence;
      try { evidence = JSON.parse(check.stdout); } catch { /* Never emit raw subprocess output. */ }
      const version = typeof evidence?.mysqlVersion === 'string' && /^[\d.]+$/.test(evidence.mysqlVersion) ? evidence.mysqlVersion : null;
      report.versions.mysql = version;
      const directory = typeof evidence?.evidenceDirectory === 'string' ? path.resolve(evidence.evidenceDirectory) : '';
      const knownRoot = path.resolve(mysql.outputDirectory) + path.sep;
      const files = directory.startsWith(knownRoot) ? [redactPath(directory, {...env,...values})] : [];
      for (const [id,summary] of [['mysql','Fresh loopback MySQL connectivity and Flyway compatibility.'],['backup','Dump and restore into a fresh isolated schema.'],['rollback','Previous package compatibility with the restored schema.']]) {
        if (id === 'rollback' && mysql.previousArtifact && !previousValid) {
          add(id,status.fail,'The previous package does not match the independently approved SHA-256. It was not started.');
          continue;
        }
        const outcome = evidence?.checks?.[id];
        add(id,verdicts.has(outcome) && (outcome !== status.pass || check.status === 0 || Object.values(evidence.checks).includes(status.fail)) ? outcome : status.fail,summary,files);
      }
      if (!tls.backendPort) add('spring',evidence?.springReadiness === true ? status.pass : status.untested,
        evidence?.springReadiness === true ? 'The pinned production JAR reached readiness against the fresh isolated MySQL restore.' : 'The isolated MySQL rehearsal did not establish Spring Boot readiness.',files);
      if (mysqlEdge) add('https',evidence?.httpsVerified === true ? status.pass : status.fail,
        evidence?.httpsVerified === true ? 'The disposable rehearsal verified TLS, redirects and API routing through the running candidate.' :
          'The disposable rehearsal did not establish HTTPS trust, hostname and API routing.',files);
    } else {
      add('mysql',input.isolatedMysql ? status.fail : status.blocked,input.isolatedMysql ? 'Pin the candidate JAR to an approved checksum before starting a disposable database.' :
        'Supply local MySQL binaries and the current release JAR to create a disposable database.');
      add('backup',status.untested,'The command did not create a disposable database and perform a fresh backup and restore.');
      add('rollback',status.untested,'Supply the immutable previous package to verify rollback compatibility.');
      if (!tls.backendPort) add('spring',status.blocked,'Supply the direct loopback port or run a fresh disposable MySQL rehearsal with the pinned production JAR.');
      if (mysqlEdge) add('https',status.untested,'The candidate could not start while validating this run input. HTTPS verification was skipped.');
    }

    checkServiceConfiguration(add,values,options,env,validEnv);
    checkSigning(add,report,input.signing || {},env);
    if (options['skip-maven']) add('pin',status.untested,'The operator skipped the offline isolated geocoder fixture tests.');
    else await checkPin(add,report,dir);

    const isolatedProvider = readVersion('backend/src/main/java/com/smartcart/integration/QuickCommerceProvider.java', /@Profile\("(local)"\)/) === 'local' &&
      readVersion('backend/src/main/java/com/smartcart/controller/QuickCommerceController.java', /@Profile\("(local)"\)/) === 'local';
    const disabled = !['true','1','yes','on'].includes(String(env.QUICKCOMMERCE_ENABLED || '').trim().toLowerCase()) &&
      !['true','1','yes','on'].includes(String(env.QUICKCOMMERCE_SHOPPING_ENABLED || '').trim().toLowerCase());
    const finalLedger = await readLedger(ledgerFile);
    const ledgerStable = initialLedger.hash === finalLedger.hash && initialLedger.readable && finalLedger.readable;
    add('retailer',isolatedProvider && disabled && compose.status === 0 && ledgerStable ? status.pass : status.fail,
      'Checked local-profile retailer isolation and paid dispatch flags offline. Provider key in current shell is ' +
        (nonblank(env.QUICKCOMMERCE_API_KEY) ? 'PRESENT' : 'MISSING') + '. ' +
        (ledgerStable ? (finalLedger.hash ? 'The read-only ledger digest was unchanged.' : 'No local ledger file was present.') : 'The ledger read or stability check was inconclusive.') +
        ' No retailer requests or credit transactions were dispatched.',
      ['backend/src/main/resources/application-local.yml','backend/src/main/java/com/smartcart/integration/QuickCommerceProvider.java']);

    report.priorEvidence = await verifyPriorEvidence(input.priorEvidence || [],{...env,...values});
  } catch {
    const preflight = report.checks.find(item=>item.id === 'preflight');
    preflight.status = status.fail;
    preflight.summary = 'A local input could not be processed. The remaining checks were marked NOT_TESTED. Subprocess output and input values were omitted.';
  }
  return finish(report,dir);
}

function checkServiceConfiguration(add, values, options, env, validEnv) {
  const native = Object.fromEntries(Object.entries(env).filter(([key]) =>
    /^(CARTIQ_(NATIVE_PUSH_ENABLED|FIREBASE_ANDROID_FILE|FIREBASE_IOS_FILE|APNS_ENVIRONMENT|EAS_PROJECT_ID)|EXPO_PUBLIC_(FIREBASE_PROJECT_ID|API_URL))$/.test(key)));

  if (options.firebase && validEnv) {
    try {
      const adc = readJson(path.resolve(root,values.FIREBASE_DEV_ADC_FILE));
      const fields = {service_account:['client_email','private_key','token_uri'],authorized_user:['client_id','client_secret','refresh_token'],
        external_account:['audience','token_url','credential_source'],impersonated_service_account:['source_credentials','service_account_impersonation_url']};
      assert(Object.hasOwn(fields,adc?.type) && fields[adc.type].every(field => nonblank(adc[field]) || adc[field] && typeof adc[field] === 'object'));
      add('firebase',status.pass,'Firebase project, public Web fields and ADC document shape passed static checks. No ADC authorization or delivery was attempted.');
    } catch { add('firebase',status.fail,'The supplied Firebase ADC file could not be validated as a supported credential document.'); }
  } else add('firebase',options.firebase && options['env-file'] ? status.fail : status.blocked,'Provide the authorized Firebase development overlay, matching project settings and backend ADC.');

  const nativeApp = readJson(path.join(root,'app.json')).expo;
  const eas = readJson(path.join(root,'eas.json'));
  const validEas = nativeApp.android?.package === 'com.raydonsolutions.cartiq' && nativeApp.ios?.bundleIdentifier === 'com.raydonsolutions.cartiq' &&
    eas.build?.preview?.distribution === 'internal' && eas.build?.production?.android?.buildType === 'app-bundle' &&
    ['preview','production'].every(profile => eas.build[profile]?.env?.CARTIQ_REQUIRE_HTTPS_API === 'true');
  if (!validEas || (nonblank(native.CARTIQ_EAS_PROJECT_ID) && !/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(native.CARTIQ_EAS_PROJECT_ID))) {
    add('eas',status.fail,'The EAS project UUID, signing build profiles or registered application identifiers are invalid.', ['eas.json','app.json']);
  } else if (!nonblank(native.CARTIQ_EAS_PROJECT_ID) || !nonblank(native.EXPO_PUBLIC_API_URL)) {
    add('eas',status.blocked,'Build profiles and application IDs passed static inspection. Supply the linked EAS project ID and private device-reachable HTTPS API.', ['eas.json','configuration/native.env.example']);
  } else {
    try {
      const url = new URL(native.EXPO_PUBLIC_API_URL);
      assert(url.protocol === 'https:' && /\/api\/?$/.test(url.pathname) && !url.username && !url.password && !url.search && !url.hash &&
        !['localhost','127.0.0.1','[::1]'].includes(url.hostname));
      add('eas',status.pass,'Static EAS project UUID, app IDs, HTTPS API shape and build profiles passed. Project ownership and server access require the authorized team.');
    } catch { add('eas',status.fail,'The native API URL is invalid or cannot be used by a physical staging device.'); }
  }

  if (native.CARTIQ_NATIVE_PUSH_ENABLED !== 'true') add('nativePush',status.blocked,'Set the native push flag and supply the authorized Android and iOS client configuration files.', ['configuration/native.env.example']);
  else {
    const saved = process.env;
    try {
      process.env = {...shellEnv(native),EAS_BUILD_PROFILE:'preview'};
      const config = require(path.join(root,'app.config.js'))({config:nativeApp});
      assert(config.extra.nativePushEnabled && config.android.googleServicesFile && config.ios.googleServicesFile);
      if (options.firebase) assert(values.FIREBASE_PROJECT_ID === native.EXPO_PUBLIC_FIREBASE_PROJECT_ID);
      add('nativePush',status.pass,'Both native client files match the Firebase project and application identifiers.');
    } catch { add('nativePush',status.fail,'Native Firebase client configuration, app IDs, HTTPS API or project binding failed.'); }
    finally { process.env = saved; }
  }
  add('pushDelivery',status.blocked,'Requires authorized Firebase development project, correctly signed installations and physical devices. This workflow does not send pushes.');

  let geocoderReady = false;
  try {
    geocoderReady = validateGeocoderEnvironment({...env, ...values});
    if (geocoderReady) {
      add('geocoder',status.pass,'The operator supplied a compatible HTTPS configuration shape. Provider authorization and live Indian address coverage remain external.');
    } else {
      add('geocoder',status.blocked,'Set CARTIQ_GEOCODER_URL to an authorized HTTPS Nominatim-compatible service. No live lookup was made.');
    }
  } catch (error) {
    add('geocoder',status.fail,error.message);
  }
  add('geocoderLive',geocoderReady ? status.untested : status.blocked,'Live PIN coverage requires a separately authorized provider probe. This acceptance command does not make external lookups.');
}

function checkSigning(add, report, signing, env) {
  const states = [];
  if (signing.android && nonblank(env.CARTIQ_ACCEPTANCE_ANDROID_STORE_PASSWORD)) {
    const candidate=signing.android;
    const result = execute(report,'keytool -list (Android signing alias)','keytool',
      ['-list','-v','-keystore',path.resolve(candidate.keystore),'-alias',candidate.alias,'-storepass:env','CARTIQ_ACCEPTANCE_ANDROID_STORE_PASSWORD','-J-Duser.language=en'],
      {env:shellEnv({CARTIQ_ACCEPTANCE_ANDROID_STORE_PASSWORD:env.CARTIQ_ACCEPTANCE_ANDROID_STORE_PASSWORD})});
    const androidStatus = result.error?.code === 'ENOENT' ? status.blocked : result.status === 0 && /Entry type: PrivateKeyEntry/.test(result.stdout || '') ? status.pass : status.fail;
    states.push({platform:'Android',status:androidStatus});
  } else states.push({platform:'Android',status:status.blocked});

  if (signing.ios && process.platform === 'darwin') {
    const profile = execute(report,'security cms -D (Apple provisioning profile)','security',['cms','-D','-i',path.resolve(signing.ios.profile)]);
    const identities = execute(report,'security find-identity (codesigning)','security',['find-identity','-v','-p','codesigning']);
    let iosStatus = status.fail;
    try {
      assert.equal(profile.status,0);
      assert.equal(identities.status,0);
      const data = require('@expo/plist').default.parse(profile.stdout);
      const certificates = data.DeveloperCertificates || [];
      const available = new Set([...identities.stdout.matchAll(/^\s*\d+\)\s+([A-F0-9]{40}) /gm)].map(match=>match[1]));
      const expiry = new Date(data.ExpirationDate).getTime();
      assert(expiry > Date.now() && data.Entitlements?.['application-identifier']?.endsWith('.com.raydonsolutions.cartiq') &&
        certificates.some(certificate=>available.has(createHash('sha1').update(certificate).digest('hex').toUpperCase())));
      iosStatus = status.pass;
    } catch { /* Do not include certificate or provisioning profile contents. */ }
    states.push({platform:'iOS',status:iosStatus});
  } else states.push({platform:'iOS',status:status.blocked});
  const result = states.some(item=>item.status === status.fail) ? status.fail : states.every(item=>item.status === status.pass) ? status.pass : status.blocked;
  add('signing',result,states.map(item=>item.platform+' '+item.status).join(', ')+'. Local Android alias and macOS signing identity checks use no builds. EAS-managed signing requires operator access.');
}

async function checkPin(add, report, directory) {
  const scratch = path.join(directory,'isolated-geocoder-source');
  fs.mkdirSync(scratch,{recursive:true});
  fs.cpSync(path.join(root,'backend','src'),path.join(scratch,'src'),{recursive:true});
  fs.copyFileSync(path.join(root,'backend','pom.xml'),path.join(scratch,'pom.xml'));
  const maven = ['-o','-B','test','-Dspring.profiles.active=test','-DargLine=-Duser.home=.','-Dtest=ShoppingGeocoderTest'];
  const args = process.platform === 'win32' ? ['/d','/c','mvn.cmd',...maven] : maven;
  const result = execute(report,'mvn -o -B test (ShoppingGeocoderTest; isolated test profile)',process.platform === 'win32' ? 'cmd.exe' : 'mvn',args,{cwd:scratch,timeout:300000});
  const counts = result.stdout?.match(/Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)/g);
  const count = counts?.at(-1);
  const mavenBlocked = result.error?.code === 'ENOENT' || /could not be resolved|offline mode|not recognized|not found/i.test(result.stdout || '') && !count;
  if (mavenBlocked) add('pin',status.blocked,'Maven or its cached offline dependencies are unavailable.');
  else add('pin',result.status === 0 && count ? status.pass : status.fail,
    count || 'The isolated PIN adapter fixture suite failed to complete.', ['isolated-geocoder-source/target/surefire-reports/com.smartcart.integration.ShoppingGeocoderTest.txt']);
}

async function checkArtifacts(artifacts, env) {
  const result = [];
  const expected = {backend:readVersion('backend/pom.xml',/smartcart-api<\/artifactId><version>([\d.]+)<\/version>/),
    web:readJson(path.join(root,'app.json')).expo.version, android:readJson(path.join(root,'app.json')).expo.version,
    ios:readJson(path.join(root,'app.json')).expo.version, forecast:readVersion('ai-service/app.py',/version="([\d.]+)"/)};
  for (const entry of artifacts) {
    const {component, file, sha256, version} = settings(entry, ['component','file','sha256','version']);
    assert(Object.hasOwn(expected,component) && version === expected[component] && nonblank(file) && /^[0-9a-f]{64}$/i.test(sha256));
    const actual = await artifactHash(file);
    assert.equal(actual,sha256.toLowerCase());
    if (component === 'backend') assert.equal(jarVersion(file),version);
    result.push({component,file:redactPath(path.resolve(file),env),sourcePath:path.resolve(file),version,sha256:actual,
      versionEvidence:component === 'backend' ? 'JAR manifest and workspace release version' : 'expected workspace release version and artifact digest'});
  }
  return result;
}

async function readLedger(file) {
  try { return {readable:true,hash:await artifactHash(file)}; }
  catch (failure) { return {readable:failure.code === 'ENOENT',hash:null}; }
}

function redactPath(file, env) {
  let cleaned = file;
  for (const [key, value] of Object.entries(env)) {
    if (typeof value === 'string' && value.length >= 6 && /PASSWORD|SECRET|TOKEN|API_KEY|PRIVATE_KEY/i.test(key)) cleaned = cleaned.split(value).join('[redacted]');
  }
  return cleaned;
}

async function verifyPriorEvidence(entries, env) {
  const result = [];
  for (const entry of entries) {
    try {
      const {check,file,sha256} = settings(entry,['check','file','sha256']);
      assert(Object.hasOwn(names,check) && nonblank(file) && /^[0-9a-f]{64}$/i.test(sha256));
      const matched = await artifactHash(file) === sha256.toLowerCase();
      result.push({check,status:matched ? status.pass : status.fail,location:redactPath(path.resolve(file),env),note:'Digest check only. Historical evidence cannot satisfy a current check.'});
    } catch { result.push({status:status.fail,note:'A historical evidence reference could not be validated.'}); }
  }
  return result;
}

function finish(report,dir) {
  const complete = new Set(report.checks.map(item => item.id));
  for (const [id,requirement] of Object.entries(names)) if (!complete.has(id)) report.checks.push({id,requirement,status:status.untested,
    timestamp:new Date().toISOString(),summary:'No current result was recorded for this requirement.',evidence:[]});
  report.checks.sort((a,b) => Object.keys(names).indexOf(a.id)-Object.keys(names).indexOf(b.id));
  report.completedAt = new Date().toISOString();
  report.overall = report.checks.some(check => check.status === status.fail) ? 'FAIL' : report.checks.every(check => check.status === status.pass) ? 'PASS' : 'INCOMPLETE';
  fs.mkdirSync(dir,{recursive:true});
  fs.writeFileSync(path.join(dir,'release-acceptance.json'),JSON.stringify(report,null,2)+'\n',{flag:'wx'});
  const cell = text => String(text ?? '').replace(/[|\r\n<>]/g, ' ');
  const markdown = ['# CartIQ release acceptance','',`**Result** ${report.overall}`,`**Started** ${report.startedAt}`,`**Completed** ${report.completedAt}`,'',
    '| Requirement | Status | Evidence and result |','|---|---|---|',...report.checks.map(check =>
      `| ${cell(check.requirement)} | ${check.status} | ${cell([check.summary,...check.evidence].join(' · '))} |`),'',
    '## Software versions','',...Object.entries(report.versions).map(([key,value]) => `- ${cell(key)} ${cell(value || 'unavailable')}`),'',
    '## Command results','',...report.commands.map(command => `- ${cell(command.command)} · ${command.startedAt} to ${command.finishedAt} · ${command.outcome}` +
      (command.exitCode !== null ? ` ${command.exitCode}` : command.httpStatuses ? ` ${command.httpStatuses.join(', ')}` : '')),'',
    '## Verified artifact pins','',...(report.artifacts || []).map(item => `- ${item.component} ${item.version} · SHA-256 ${item.sha256} · ${item.versionEvidence}`),
    ...(report.previousBackend ? [`- Previous backend · SHA-256 ${report.previousBackend.sha256} · ${cell(report.previousBackend.file)}`] : []),'',
    '## Historical evidence references','',...(report.priorEvidence || []).map(item => `- ${cell(item.check || 'Unverified input')} · ${item.status} · ${cell(item.location)} · ${cell(item.note)}`),''];
  fs.writeFileSync(path.join(dir,'release-acceptance.md'),markdown.join('\n'),{flag:'wx'});
  return {directory:dir,report};
}

if (require.main === module) {
  (async () => {
    const {values} = parseArgs({options:{inputs:{type:'string'},'output-dir':{type:'string'},'env-file':{type:'string'},firebase:{type:'boolean'},'skip-maven':{type:'boolean'}}});
    const {report,directory} = await accept(values);
    console.log(`CartIQ acceptance ${report.overall}. Wrote release-acceptance.json and release-acceptance.md in ${redactPath(directory,process.env)}.`);
    process.exitCode = {PASS:0,FAIL:1,INCOMPLETE:2}[report.overall];
  })().catch(() => {
    console.error('Acceptance could not finish. Check inputs, Node.js compatibility and the writable report directory. Existing reports are not overwritten.');
    process.exitCode = 1;
  });
}

module.exports = {accept,artifactHash,jarVersion};
