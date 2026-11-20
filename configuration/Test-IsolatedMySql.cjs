'use strict';

const assert = require('node:assert/strict');
const {createHash, createHmac, randomBytes} = require('node:crypto');
const {spawn, spawnSync} = require('node:child_process');
const fs = require('node:fs');
const http = require('node:http');
const net = require('node:net');
const path = require('node:path');
const {parseArgs} = require('node:util');
const {setTimeout: delay} = require('node:timers/promises');

const source = 'cartiq_acceptance';
const restored = 'cartiq_restored';
const fixture = 'acceptance-' + randomBytes(8).toString('hex');
const windows = process.platform === 'win32';
const childEnv = Object.fromEntries(Object.entries(process.env).filter(([name]) =>
  /^(path|pathext|systemroot|windir|home|userprofile|temp|tmp|java_home|ld_library_path)$/i.test(name)));

function run(exe, args, options = {}) {
  const {label, ...settings} = options;
  const result = spawnSync(exe, args, {env: childEnv, windowsHide: true, encoding:'utf8', timeout:90000, ...settings});
  if (result.status !== 0) throw new Error(label || 'An isolated command failed.');
  return result.stdout?.trim();
}

function start(exe, args, options) {
  const child = spawn(exe, args, {env:childEnv, windowsHide:true, stdio:'ignore', ...options});
  child.on('error', () => {});
  return child;
}

async function stop(child) {
  if (!child?.pid || child.exitCode !== null || child.signalCode !== null) return;
  const exited = new Promise(resolve => child.once('exit', resolve));
  child.kill();
  await Promise.race([exited, delay(5000, undefined, {ref:false})]);
  if (child.exitCode === null && child.signalCode === null) child.kill('SIGKILL');
}

async function freePort(requested = 0) {
  const server = net.createServer();
  await new Promise((resolve, reject) => server.once('error', reject).listen(requested, '127.0.0.1', resolve));
  const {port} = server.address();
  await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
  return port;
}

function get(port, route, headers) {
  return new Promise((resolve, reject) => {
    const request = http.get({hostname:'127.0.0.1', port, path:route, timeout:5000, headers}, response => {
      let buffer = '';
      response.setEncoding('utf8');
      response.on('data', chunk => {
        if (buffer.length + chunk.length > 65536) request.destroy();
        else buffer += chunk;
      });
      response.on('error', reject);
      response.on('end', () => {
        let data = null;
        try { data = JSON.parse(buffer); } catch { /* Ignore HTML and other unexpected bodies. */ }
        resolve({status:response.statusCode,data});
      });
    });
    request.once('error', reject);
    request.once('timeout', () => request.destroy(new Error('Loopback health deadline exceeded.')));
  });
}

function artifact(file) {
  const resolved = path.resolve(file);
  assert(fs.statSync(resolved).isFile() && resolved.endsWith('.jar'));
  return resolved;
}

const secret = () => randomBytes(32).toString('hex');
const hash = file => createHash('sha256').update(fs.readFileSync(file)).digest('hex');
function fixtureToken(key) {
  const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const now = Math.floor(Date.now()/1000);
  const unsigned = encode({alg:'HS256'})+'.'+encode({iss:'smartcart-ai',sub:fixture,iat:now,exp:now+60,scope:'admin',version:0});
  return unsigned+'.'+createHmac('sha256',key).update(unsigned).digest('base64url');
}

function javaBinary() {
  if (!windows) return 'java';
  const probe = spawnSync('java', ['-XshowSettings:properties','-version'], {env:childEnv, encoding:'utf8', windowsHide:true, timeout:15000});
  const home = probe.stderr?.match(/^\s*java\.home = (.+)$/m)?.[1]?.trim();
  if (!home) throw new Error('A local Java runtime is unavailable.');
  return path.join(home, 'bin', 'java.exe');
}

async function exercise(args, report) {
  const candidate = artifact(args.artifact);
  const java = javaBinary();
  const previous = args['previous-artifact'] ? artifact(args['previous-artifact']) : null;
  const edge = Object.fromEntries(['origin','http-port','ca','backend-port'].filter(key=>args['edge-'+key]).map(key=>[key,args['edge-'+key]]));
  if (Object.keys(edge).length) {
    assert(Object.keys(edge).length === 4 && /^\d{4,5}$/.test(edge['backend-port']) && Number(edge['backend-port']) >= 1024 && Number(edge['backend-port']) <= 65535);
  }
  const bin = path.resolve(args['mysql-bin']);
  const parent = path.resolve(args['output-dir']);
  fs.mkdirSync(parent, {recursive:true, mode:0o700});
  const scratch = fs.mkdtempSync(path.join(parent, 'isolated-mysql-'));
  report.evidenceDirectory = scratch;
  report.candidateSha256 = hash(candidate);
  if (previous) {
    report.previousSha256 = hash(previous);
    assert(report.previousSha256 !== report.candidateSha256, 'Rollback needs two distinct immutable packages.');
  }
  const tool = name => path.join(bin, name + (windows ? '.exe' : ''));
  const mysqld = tool('mysqld');
  const version = run(mysqld, ['--no-defaults','--version'], {label:'MySQL 8 server binary is unavailable.'}).match(/\bVer (8\.\d+\.\d+)\b/)?.[1];
  assert(version, 'Supply a MySQL 8 toolchain.');
  report.mysqlVersion = version;
  const directory = path.join(scratch, 'data');
  const init = path.join(scratch, 'initialize.sql');
  const socket = path.join(scratch, 'mysql.sock');
  const rootPassword = secret(), runtimePassword = secret();
  const port = await freePort();
  const clientFlags = ['--no-defaults','--protocol=TCP','--host=127.0.0.1',`--port=${port}`,'--user=root','--ssl-mode=REQUIRED'];
  const mysqlEnv = {...childEnv, MYSQL_PWD:rootPassword, MYSQL_TEST_LOGIN_FILE:path.join(scratch,'unused-mylogin.cnf')};
  const client = (sql, database) => run(tool('mysql'), [...clientFlags, '--connect-timeout=2', '--batch', '--skip-column-names', ...(database ? [database] : [])],
    {env:mysqlEnv,cwd:scratch,input:sql,label:'A disposable MySQL query failed.'});
  const jdbc = database => `jdbc:mysql://127.0.0.1:${port}/${database}?connectionTimeZone=UTC&sslMode=REQUIRED`;
  const flyway = (operation, jar, database) => run(java, ['-Duser.home=.', '-Dloader.main=com.smartcart.config.DatabaseMigration','-cp',jar,
    'org.springframework.boot.loader.launch.PropertiesLauncher',operation], {cwd:scratch,
      env:{...childEnv, DB_URL:jdbc(database), DB_USERNAME:'root', DB_PASSWORD:rootPassword},
      label:`Isolated Flyway ${operation} failed.`});
  const versions = database => client('SELECT version FROM flyway_schema_history WHERE success=TRUE AND type=\'SQL\' ORDER BY installed_rank;', database);
  let mysql, api, owned = false, stage = 'mysql';

  async function verifyRuntime(jar, probeEdge = false) {
    const apiPort = await freePort(probeEdge && edge['backend-port'] ? Number(edge['backend-port']) : 0);
    let boot = '';
    const jwtKey=secret();
    api = start(java, ['-Duser.home=.', '-jar',jar,'--server.address=127.0.0.1',`--server.port=${apiPort}`], {cwd:scratch,stdio:['ignore','pipe','pipe'],env:{...childEnv,
      SPRING_PROFILES_ACTIVE:'prod', DB_URL:jdbc(restored), DB_USERNAME:'cartiq_accept', DB_PASSWORD:runtimePassword,
      JWT_SECRET:jwtKey, ALLOWED_ORIGINS:'https://localhost:8443', FIREBASE_ENABLED:'false', CARTIQ_GEOCODER_URL:'',
      QUICKCOMMERCE_ENABLED:'false', QUICKCOMMERCE_SHOPPING_ENABLED:'false'}});
    const collect = bytes => { boot = (boot + bytes.toString()).slice(-96*1024); };
    api.stdout.on('data',collect);
    api.stderr.on('data',collect);
    let observed;
    try {
      const until = Date.now() + 65000;
      while (Date.now() < until && api.pid && api.exitCode === null && api.signalCode === null) {
        const health = await get(apiPort, '/actuator/health/readiness').catch(() => null);
        observed = health?.status || observed;
        if (health?.status === 200 && health.data?.status === 'UP') {
          const categories = await get(apiPort, '/api/categories');
          if (categories.status === 200 && Array.isArray(categories.data) && categories.data.some(row=>row.id === fixture)) {
            if(args['retailer-operations']&&jar===candidate) {
              const route='/api/admin/retailer-operations/coverage?locationId='+fixture+'&category='+fixture+'&retailer=blinkit';
              const denied=await get(apiPort,route).catch(()=>null);
              const coverage=await get(apiPort,route,{Authorization:'Bearer '+fixtureToken(jwtKey)}).catch(()=>null);
              if(denied?.status!==401||coverage?.status!==200||coverage.data?.counts?.totalProducts!==1
                ||coverage.data?.counts?.productsWithoutMapping!==1||coverage.data?.retailers?.[0]?.recordedUsableProducts!==0) {
                report.retailerOperations={status:coverage?.status??null,anonymousStatus:denied?.status??null,verified:false};
                throw new Error('The isolated retailer coverage endpoint did not return its expected fixture.');
              }
              report.retailerOperations={status:200,anonymousStatus:401,verified:true};
            }
            if (probeEdge && edge.origin) {
              // The smoke helper is strictly loopback and prints only fixed PASS lines.
              const result = spawnSync(process.execPath, [path.join(__dirname,'Test-StagingRelease.cjs'),
                ...Object.entries(edge).flatMap(([key,value])=>['--'+key,value])],
                {env:childEnv, stdio:'ignore', windowsHide:true, timeout:30000});
              report.httpsVerified = result.status === 0;
            }
            return;
          }
          throw new Error('The runtime did not expose the restored fixture.');
        }
        await delay(350);
      }
      const types = [...new Set(boot.match(/\b[A-Za-z][A-Za-z0-9]*(?:Exception|Error)\b/g) || [])];
      report.runtimeDiagnostic = {httpStatus:observed || null,exitCode:api.exitCode,
        started:/Started SmartCartApplication/.test(boot), bound:/Tomcat started on port/.test(boot), errors:types.slice(0,10)};
      throw new Error('The package did not become ready with the disposable database.' +
        (types.length ? ` Failure classes: ${types.join(', ')}.` : ''));
    } finally { await stop(api); api = null; }
  }

  try {
    run(mysqld, ['--no-defaults','--initialize-insecure',`--datadir=${directory}`],
      {cwd:scratch,label:'Could not initialize a fresh MySQL data directory.'});
    fs.writeFileSync(init, `ALTER USER 'root'@'localhost' IDENTIFIED BY '${rootPassword}';\n`, {mode:0o600, flag:'wx'});
    mysql = start(mysqld, ['--no-defaults', ...(windows ? ['--console','--no-monitor'] : [`--socket=${socket}`]),
      '--bind-address=127.0.0.1',`--port=${port}`,`--datadir=${directory}`,'--mysqlx=OFF','--skip-log-bin',`--init-file=${init}`], {cwd:scratch});
    for (let i=0; i<60 && mysql.pid && mysql.exitCode === null; i++) {
      let observed;
      try { observed = client('SELECT @@datadir;'); } catch { /* The new server can still be starting. */ }
      const canonical = value => windows ? path.resolve(value).toLowerCase() : path.resolve(value);
      if (observed && canonical(observed) === canonical(directory)) { owned = true; break; }
      await delay(250);
    }
    assert(owned, 'The new MySQL server could not be authenticated and tied to its scratch directory.');
    fs.unlinkSync(init);

    client(`CREATE DATABASE ${source}; CREATE DATABASE ${restored};
      CREATE USER 'cartiq_accept'@'localhost' IDENTIFIED BY '${runtimePassword}'; GRANT SELECT, INSERT, UPDATE, DELETE ON ${restored}.* TO 'cartiq_accept'@'localhost';`);
    if (previous) flyway('migrate', previous, source);
    else flyway('migrate', candidate, source);
    const priorRevisions = previous ? versions(source).split(/\r?\n/) : [];
    client(`INSERT INTO product_categories(id,name) VALUES ('${fixture}','Release acceptance fixture');`, source);
    if (previous) {
      client(`INSERT INTO users(id,name,email,password_hash) VALUES ('${fixture}','Acceptance fixture','fixture@acceptance.invalid','unusable');
        INSERT INTO notifications(id,user_id,title,body,kind,dedupe_key,sample,delivery_status) VALUES
        ('${fixture}','${fixture}','Acceptance fixture','Acceptance fixture','TARGET','${fixture}',FALSE,'PUSH_PENDING');`, source);
      flyway('migrate', candidate, source);
    }
    flyway('validate', candidate, source);
    const revisions = versions(source);
    assert(revisions, 'The source database has no validated Flyway history.');
    report.flywayVersions = revisions.split(/\r?\n/);
    if (report.flywayVersions.includes('14')) {
      const [observations,retentionGrants,forecastPermissions]=client(`SELECT
        (SELECT COUNT(*) FROM approved_price_observations),
        (SELECT COUNT(*) FROM price_retention_grants),
        (SELECT COUNT(*) FROM price_forecast_permissions);`,source).split('\t').map(Number);
      report.forecastSchemaCounts={observations,retentionGrants,forecastPermissions};
    }
    if (report.flywayVersions.includes('15')) {
      const [authorizations,batches,decisions]=client(`SELECT
        (SELECT COUNT(*) FROM price_source_authorizations),
        (SELECT COUNT(*) FROM price_intake_batches),
        (SELECT COUNT(*) FROM price_intake_decisions);`,source).split('\t').map(Number);
      report.historicalIntakeCounts={authorizations,batches,decisions};
    }
    const hasReceipts = previous && report.flywayVersions.includes('12');
    if (hasReceipts) {
      if (!priorRevisions.includes('12')) {
        assert(client(`SELECT delivery_status FROM notifications WHERE id='${fixture}';`, source) === 'IN_APP',
          'The pre-receipt pending event did not safely fall back in-app.');
      }
      client(`INSERT INTO notification_deliveries(notification_id,device_id,status,attempt_count,provider_message_id)
        VALUES ('${fixture}','acceptance-device','SENT',1,'synthetic-local-test-receipt');`, source);
    }
    const hasWeeklyPlans = report.flywayVersions.includes('17');
    if (hasWeeklyPlans) {
      if (!previous) client(`INSERT INTO users(id,name,email,password_hash)
        VALUES ('${fixture}','Acceptance fixture','fixture@acceptance.invalid','unusable');`, source);
      client(`INSERT INTO delivery_locations(id,name,city,postcode)
        VALUES ('${fixture}','Fixture area','Fixture city','641018');
        INSERT INTO weekly_shopping_plans(user_id,location_id,plan_input,revision)
        VALUES ('${fixture}','${fixture}','{"meals":[],"items":[],"budgetMinor":null,"diet":"ANY","onHand":{}}',1);
        INSERT INTO recommendation_feedback(user_id,reference_id,category,outcome)
        VALUES ('${fixture}','${fixture}','PLAN','MODIFIED');`, source);
    }
    if(args['retailer-operations']){
      if(!hasWeeklyPlans)throw new Error('Retailer operations requires the current normalized schema.');
      client(`UPDATE users SET role='admin' WHERE id='${fixture}';
        INSERT INTO products(id,category_id,name,brand,variant_name,description,color,art,active)
        VALUES ('${fixture}','${fixture}','Fixture rice','Fixture','Standard','Synthetic','#fff','bag',TRUE);
        INSERT INTO product_variants(id,product_id,pack_label,amount_base,base_unit)
        VALUES ('${fixture}','${fixture}','500 g',500,'g');`,source);
    }
    const hasControlledPilot=report.flywayVersions.includes('18');
    if(hasControlledPilot&&hasWeeklyPlans){
      client(`INSERT INTO grocery_platforms(id,name,fee_source)
        SELECT 'blinkit','Blinkit','UNKNOWN' WHERE NOT EXISTS (SELECT 1 FROM grocery_platforms WHERE id='blinkit');
        INSERT INTO retailer_pilots(id,location_id,retailer,starts_at,ends_at,enabled,attempt_limit,credit_limit,permission_reference,created_by,created_at)
        VALUES ('${fixture}','${fixture}','blinkit',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + INTERVAL 1 DAY,FALSE,1,1,'synthetic-rehearsal','${fixture}',CURRENT_TIMESTAMP);`,source);
    }
    report.checks.mysql = 'PASS';

    stage = 'backup';
    const dump = path.join(scratch, 'backup.sql');
    const fd = fs.openSync(dump, 'wx', 0o600);
    try {
      run(tool('mysqldump'), [...clientFlags,'--single-transaction','--no-tablespaces','--set-gtid-purged=OFF',source],
        {env:mysqlEnv,cwd:scratch,stdio:['ignore',fd,'pipe'],label:'The disposable backup failed.'});
    } finally { fs.closeSync(fd); }
    const input = fs.openSync(dump, 'r');
    try {
      run(tool('mysql'), [...clientFlags,restored], {env:mysqlEnv,cwd:scratch,stdio:[input,'pipe','pipe'], label:'The disposable restore failed.'});
    } finally { fs.closeSync(input); }
    flyway('validate', candidate, restored);
    assert(versions(restored) === revisions, 'Restored Flyway revisions differ.');
    assert(client(`SELECT COUNT(*) FROM product_categories WHERE id='${fixture}';`, restored) === '1', 'The backup fixture was not restored.');
    const restoredReceipt = () => client(`SELECT COUNT(*) FROM notification_deliveries WHERE notification_id='${fixture}' AND status='SENT' AND attempt_count=1;`, restored);
    if (hasReceipts) assert(restoredReceipt() === '1', 'The backup receipt was not restored.');
    const restoredShopping = () => client(`SELECT
      (SELECT COUNT(*) FROM weekly_shopping_plans WHERE user_id='${fixture}' AND location_id='${fixture}' AND revision=1 AND JSON_VALID(plan_input)),
      (SELECT COUNT(*) FROM recommendation_feedback WHERE user_id='${fixture}' AND reference_id='${fixture}' AND category='PLAN' AND outcome='MODIFIED');`, restored);
    if (hasWeeklyPlans) {
      assert(restoredShopping() === '1\t1', 'The backup plan and feedback were not restored.');
      report.weeklyShoppingFixturesRestored = true;
    }
    const restoredPilot = () => client(`SELECT COUNT(*) FROM retailer_pilots WHERE id='${fixture}' AND enabled=FALSE AND attempt_limit=1 AND credit_limit=1;`,restored);
    if(hasControlledPilot&&hasWeeklyPlans){
      assert(restoredPilot()==='1','The disabled pilot fixture was not restored.');
      report.disabledPilotRestored=true;
    }
    report.backupSha256 = hash(dump);
    await verifyRuntime(candidate,true);
    report.springReadiness = true;
    report.checks.backup = 'PASS';

    if (previous) {
      stage = 'rollback';
      await verifyRuntime(previous);
      await verifyRuntime(candidate);
      assert(versions(restored) === revisions, 'Package switch altered Flyway history.');
      if (hasReceipts) assert(restoredReceipt() === '1', 'Package switch altered the delivery receipt.');
      if (hasWeeklyPlans) assert(restoredShopping() === '1\t1', 'Package switch altered the plan or feedback.');
      if(hasControlledPilot&&hasWeeklyPlans)assert(restoredPilot()==='1','Package switch altered the disabled pilot fixture.');
      report.checks.rollback = 'PASS';
    }
  } catch (error) {
    report.checks[stage] = 'FAIL';
    report.failureStage = stage;
    report.detail = error.code === 'ERR_ASSERTION' || error.code ? 'An isolation or readiness check failed.' : error.message;
  } finally {
    fs.rmSync(init,{force:true});
    await stop(api);
    if (owned) {
      try {
        run(tool('mysqladmin'), [...clientFlags,'shutdown'], {env:mysqlEnv,cwd:scratch,timeout:7000});
        if (mysql.exitCode === null) await Promise.race([new Promise(resolve=>mysql.once('exit',resolve)), delay(5000,undefined,{ref:false})]);
      } catch { /* Fall back to the tracked child PID. */ }
    }
    await stop(mysql);
  }
}

(async () => {
  const report = {checks:{mysql:'NOT_TESTED',backup:'NOT_TESTED',rollback:'NOT_TESTED'}};
  try {
    const {values:args} = parseArgs({options:{...Object.fromEntries(['mysql-bin','artifact','previous-artifact','output-dir',
      'edge-origin','edge-http-port','edge-ca','edge-backend-port'].map(key=>[key,{type:'string'}])),
      'retailer-operations':{type:'boolean'}}});
    if (!args['mysql-bin'] || !args.artifact || !args['output-dir']) throw new Error();
    await exercise(args, report);
  } catch {
    report.checks.mysql = 'FAIL';
    report.detail = 'Provide the MySQL 8 bin directory, candidate JAR, disposable output directory and optional previous JAR.';
  }
  const json = JSON.stringify(report);
  if (report.evidenceDirectory) fs.writeFileSync(path.join(report.evidenceDirectory,'rehearsal.json'),json+'\n',{mode:0o600});
  console.log(json);
  if (Object.values(report.checks).includes('FAIL')) process.exitCode = 1;
})();
