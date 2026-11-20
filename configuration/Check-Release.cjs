'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {parseEnv, parseArgs} = require('node:util');
const {spawnSync} = require('node:child_process');
const yaml = require('js-yaml');

const project = 'cartiq-staging';
const root = path.resolve(__dirname, '..');
const baseFile = path.join(root, 'compose.yml');
const overlayFile = path.join(__dirname, 'compose.firebase.example.yml');
const isolatedJdbc = 'jdbc:mysql://db:3306/cartiq?connectionTimeZone=UTC&sslMode=REQUIRED';

function sameKeys(object, keys, context) {
  assert.deepEqual(Object.keys(object).sort(), [...keys].sort(), context);
}

function validateCompose(base, overlay) {
  sameKeys(base, ['services', 'networks', 'volumes'], 'Unexpected top-level Compose resource');
  sameKeys(base.services, ['db', 'migrate', 'backend', 'forecast', 'web'], 'Unexpected service');
  const {db, migrate, backend, forecast, web} = base.services;
  for (const [name, service] of Object.entries(base.services)) {
    for (const field of ['privileged', 'devices', 'network_mode', 'pid', 'extra_hosts', 'links', 'external_links', 'container_name', 'extends', 'env_file']) {
      assert(!Object.hasOwn(service, field), `Unsupported isolation-related setting in ${name}`);
    }
    if (service !== web) assert(!service.ports, `Unexpected public port on ${name}`);
    if (service !== db && service !== web) assert(!service.volumes, `Unexpected volume on ${name}`);
  }
  assert.deepEqual(db.volumes, ['cartiq-db:/var/lib/mysql']);
  assert.equal(db.environment.MYSQL_DATABASE, 'cartiq');
  assert.match(db.healthcheck.test.join(' '), /SELECT 1/);
  assert.deepEqual(db.networks, ['private']);
  assert.deepEqual(migrate.networks, ['private']);
  assert.deepEqual(migrate.profiles, ['migration']);
  assert.deepEqual(migrate.command, ['migrate']);
  assert.deepEqual(backend.networks, ['front', 'private']);
  for (const service of [backend, migrate]) {
    assert.equal(service.environment.DB_URL, isolatedJdbc, 'Database must be the isolated Compose service');
    assert.equal(service.depends_on.db.condition, 'service_healthy');
  }
  assert.equal(backend.environment.SPRING_PROFILES_ACTIVE, 'prod');
  assert.match(backend.healthcheck.test.join(' '), /127\.0\.0\.1:8080\/actuator\/health\/readiness/);
  assert.equal(backend.environment.AI_SERVICE_URL, 'http://forecast:8001');
  assert.deepEqual(forecast.networks, ['private']);
  assert.match(forecast.healthcheck.test.join(' '), /127\.0\.0\.1:8001\/health/);
  assert.equal(web.depends_on.backend.condition, 'service_healthy');
  assert.equal(web.networks.front.ipv4_address, '172.30.44.10');
  assert.equal(backend.environment.CARTIQ_TRUSTED_PROXY_REGEX, '172[.]30[.]44[.]10');
  assert.deepEqual(web.ports, [
    '${HTTPS_BIND:-127.0.0.1}:${HTTPS_PORT:-8443}:443',
    '${HTTPS_BIND:-127.0.0.1}:${HTTP_REDIRECT_PORT:-8081}:80',
  ]);
  assert.equal(web.volumes.length, 1);
  assert.equal(web.volumes[0].target, '/etc/cartiq-tls');
  assert.equal(web.volumes[0].read_only, true);
  assert.equal(web.volumes[0].bind.create_host_path, false);
  assert.equal(web.environment.CARTIQ_HTTPS_ORIGIN, '${CARTIQ_HTTPS_ORIGIN:-https://localhost:8443}');
  sameKeys(base.networks, ['front', 'private'], 'Unexpected network');
  assert.deepEqual(base.networks.private, {internal:true});
  assert.deepEqual(base.networks.front, {ipam:{config:[{subnet:'172.30.44.0/24'}]}});
  assert.deepEqual(base.volumes, {'cartiq-db':null}, 'Database volume must be project-owned');
  for (const service of Object.values(base.services)) {
    assert(!Object.keys(service.environment || {}).some(key => /QUICKCOMMERCE|SPRING_DATASOURCE/.test(key)));
  }

  sameKeys(overlay, ['services', 'secrets'], 'Firebase overlay may only configure the backend secret');
  sameKeys(overlay.services, ['backend'], 'Firebase overlay may only affect the backend');
  const firebase = overlay.services.backend;
  sameKeys(firebase, ['secrets', 'environment'], 'Unexpected Firebase override');
  assert.deepEqual(firebase.secrets, ['firebase_adc']);
  sameKeys(overlay.secrets, ['firebase_adc'], 'Unexpected secret resource');
  sameKeys(overlay.secrets.firebase_adc, ['file'], 'Firebase ADC should use a local file');
  assert.equal(overlay.secrets.firebase_adc.file, '${FIREBASE_DEV_ADC_FILE:?Set the path to the approved development ADC file}');
  assert.deepEqual(firebase.environment, {
    FIREBASE_ENABLED:'true',
    FIREBASE_PROJECT_ID:'${FIREBASE_PROJECT_ID:?Set the Firebase development project ID}',
    GOOGLE_APPLICATION_CREDENTIALS:'/run/secrets/firebase_adc',
  });
  const overlaidBackend = {...backend, environment:{...backend.environment, ...firebase.environment}};
  assert.equal(overlaidBackend.environment.DB_URL, isolatedJdbc);
}

function httpsOrigin(value, key) {
  let parsed;
  try { parsed = new URL(value); } catch { /* Report the setting name only. */ }
  if (!parsed || parsed.protocol !== 'https:' || parsed.origin !== value || parsed.username || parsed.password) {
    throw new Error(`Set ${key} to a literal HTTPS origin without a path.`);
  }
  return parsed;
}

function fileExists(file) {
  return fs.statSync(file, {throwIfNoEntry:false})?.isFile() === true;
}

function validateGeocoderEnvironment(values) {
  const base = values.CARTIQ_GEOCODER_URL?.trim();
  if (!base) return false;
  let geocoder;
  try { geocoder = new URL(base); } catch { /* Report the setting name only. */ }
  if (!geocoder || geocoder.protocol !== 'https:' || geocoder.username || geocoder.password || geocoder.search || geocoder.hash) {
    throw new Error('Set CARTIQ_GEOCODER_URL to the authorized HTTPS base URL.');
  }
  const publicService = geocoder.hostname.replace(/\.$/, '') === 'nominatim.openstreetmap.org';
  const contact = values.CARTIQ_GEOCODER_CONTACT?.trim() ?? '';
  if (publicService && (geocoder.port || geocoder.pathname !== '/')) {
    throw new Error('Set CARTIQ_GEOCODER_URL to the public Nominatim HTTPS origin.');
  }
  if ((publicService || contact) && (contact.length > 254 ||
    !/^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+$/.test(contact))) {
    throw new Error('Set CARTIQ_GEOCODER_CONTACT to a monitored operator email address.');
  }
  if (publicService && values.CARTIQ_GEOCODER_PUBLIC_REVERSE_POINT?.trim()) {
    const fields = values.CARTIQ_GEOCODER_PUBLIC_REVERSE_POINT.split(',').map(value => value.trim());
    const [latitude, longitude] = fields.map(Number);
    if (fields.length !== 2 || !fields.every(value => /^[+-]?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)$/.test(value)) ||
        Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
      throw new Error('Set CARTIQ_GEOCODER_PUBLIC_REVERSE_POINT to a valid latitude,longitude pair.');
    }
  }
  return true;
}

function validateEnvironment(values, firebase) {
  const supported = parseEnv(fs.readFileSync(path.join(__dirname, 'production.env.example'), 'utf8'));
  const optionalMigration = ['MIGRATION_DB_USERNAME', 'MIGRATION_DB_PASSWORD', 'MYSQL_VERSION'];
  for (const key of Object.keys(values)) {
    if (!(Object.hasOwn(supported, key) || optionalMigration.includes(key))) {
      throw new Error('Environment file contains a key outside the staging template.');
    }
  }
  for (const key of ['DB_USERNAME', 'DB_PASSWORD', 'MYSQL_ROOT_PASSWORD', 'JWT_SECRET', 'AI_SERVICE_SECRET']) {
    if (!values[key]?.trim()) throw new Error(`Set ${key} in the protected staging environment file.`);
  }
  if (values.DB_USERNAME.trim().toLowerCase() === 'root') throw new Error('Choose a dedicated DB_USERNAME.');
  if (values.DB_PASSWORD === values.MYSQL_ROOT_PASSWORD) throw new Error('Use separate application and MySQL root passwords.');
  if (Boolean(values.MIGRATION_DB_USERNAME) !== Boolean(values.MIGRATION_DB_PASSWORD)) {
    throw new Error('Set both migration identity settings or omit both for disposable Compose.');
  }
  if (Boolean(values.BOOTSTRAP_ADMIN_EMAIL) !== Boolean(values.BOOTSTRAP_ADMIN_PASSWORD)) {
    throw new Error('Set both bootstrap admin settings or omit both.');
  }
  if (Buffer.byteLength(values.JWT_SECRET) < 32 || Buffer.byteLength(values.AI_SERVICE_SECRET) < 32 ||
      values.JWT_SECRET === values.AI_SERVICE_SECRET) throw new Error('Use independent JWT and AI secrets of at least 32 bytes each.');
  if ((values.HTTPS_BIND || '127.0.0.1') !== '127.0.0.1') throw new Error('The staging Compose stack must bind to 127.0.0.1.');
  const origin = httpsOrigin(values.CARTIQ_HTTPS_ORIGIN || 'https://localhost:8443', 'CARTIQ_HTTPS_ORIGIN');
  if (!['localhost', '127.0.0.1'].includes(origin.hostname)) throw new Error('Use a loopback origin for this staging validator.');
  const httpsPort = Number(values.HTTPS_PORT || 8443);
  const httpPort = Number(values.HTTP_REDIRECT_PORT || 8081);
  if (![httpsPort, httpPort].every(port => Number.isInteger(port) && port >= 1024 && port <= 65535)
      || httpsPort === httpPort || httpsPort !== Number(origin.port)) {
    throw new Error('Use different explicit unprivileged ports and match HTTPS_PORT to CARTIQ_HTTPS_ORIGIN.');
  }
  const origins = (values.ALLOWED_ORIGINS || '').split(',').map(value => value.trim());
  for (const value of origins) httpsOrigin(value, 'ALLOWED_ORIGINS');
  if (!origins.includes(origin.origin)) throw new Error('Include CARTIQ_HTTPS_ORIGIN in ALLOWED_ORIGINS.');
  const tls = path.resolve(root, values.CARTIQ_TLS_DIR || './configuration/tls');
  if (!['fullchain.pem', 'privkey.pem'].every(name => fileExists(path.join(tls, name)))) {
    throw new Error('CARTIQ_TLS_DIR must contain fullchain.pem and privkey.pem.');
  }
  for (const flag of ['FIREBASE_ENABLED', 'CARTIQ_PRICE_FORECAST_ENABLED', 'CARTIQ_APPROVED_FORECAST_ENABLED']) {
    if (values[flag] && !['true', 'false'].includes(values[flag])) throw new Error(`Set ${flag} to true or false.`);
  }
  if (!firebase && values.FIREBASE_ENABLED === 'true') throw new Error('Select the development Firebase overlay before enabling Firebase.');
  if (firebase) {
    if (!/^[a-z][a-z0-9-]{4,28}[a-z0-9]$/.test(values.FIREBASE_PROJECT_ID || '')
        || values.FIREBASE_PROJECT_ID !== values.EXPO_PUBLIC_FIREBASE_PROJECT_ID) {
      throw new Error('Set matching server and Web Firebase project IDs.');
    }
    if (!values.FIREBASE_DEV_ADC_FILE || !fileExists(path.resolve(root, values.FIREBASE_DEV_ADC_FILE))) {
      throw new Error('Set FIREBASE_DEV_ADC_FILE to the approved local ADC file.');
    }
    for (const suffix of ['API_KEY', 'MESSAGING_SENDER_ID', 'APP_ID', 'VAPID_KEY']) {
      if (!values['EXPO_PUBLIC_FIREBASE_' + suffix]) throw new Error(`Set EXPO_PUBLIC_FIREBASE_${suffix} for Web push.`);
    }
  }
  validateGeocoderEnvironment(values);
}

function main() {
  const {values:args} = parseArgs({options:{
    'env-file':{type:'string'}, firebase:{type:'boolean'}, 'docker-config':{type:'boolean'},
  }});
  const base = yaml.load(fs.readFileSync(baseFile, 'utf8'));
  const overlay = yaml.load(fs.readFileSync(overlayFile, 'utf8'));
  validateCompose(base, overlay);
  console.log('PASS static Compose base and Firebase combinations');
  if (args['docker-config'] && !args['env-file']) throw new Error('Supply --env-file for Docker Compose validation.');
  if (args.firebase && !args['env-file']) throw new Error('Supply --env-file for Firebase environment validation.');
  if (!args['env-file']) return;
  const file = path.resolve(args['env-file']);
  const env = parseEnv(fs.readFileSync(file, 'utf8'));
  validateEnvironment(env, args.firebase);
  console.log('PASS isolated staging environment presence and shape');
  if (!args['docker-config']) return;
  const flags = ['--context','default','compose','--project-name',project,'--env-file',file,'-f',baseFile];
  if (args.firebase) flags.push('-f',overlayFile);
  // Use the validated literal values and omit host overrides and remote daemon settings.
  const inherited = Object.fromEntries(Object.entries(process.env).filter(([key]) =>
    /^(PATH|PATHEXT|SYSTEMROOT|WINDIR|USERPROFILE|HOME|TEMP|TMP)$/i.test(key)));
  const result = spawnSync('docker', [...flags, '--profile', 'migration', 'config', '--quiet'],
    {cwd: root, env:{...inherited, ...env},stdio:'ignore'});
  if (result.error?.code === 'ENOENT') throw new Error('Docker Engine and Compose v2 are unavailable.');
  if (result.status !== 0) throw new Error('Docker Compose rejected the staging configuration.');
  console.log('PASS Docker Compose config --quiet');
}

if (require.main === module) {
  try { main(); }
  catch (error) {
    console.error('FAIL', error.code === 'ERR_ASSERTION' ? 'Compose isolation or schema check failed.' :
      error.name === 'YAMLException' || error.code === 'ENOENT' ? 'A required input could not be read.' : error.message);
    process.exitCode = 1;
  }
}

module.exports = {validateCompose, validateEnvironment, validateGeocoderEnvironment, httpsOrigin};
