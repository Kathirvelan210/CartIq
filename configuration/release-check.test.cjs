'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const http = require('node:http');
const https = require('node:https');
const {createHash} = require('node:crypto');
const {test} = require('node:test');
const yaml = require('js-yaml');
const {validateCompose, validateEnvironment, validateGeocoderEnvironment} = require('./Check-Release.cjs');
const {run, verifyArtifact} = require('./Test-StagingRelease.cjs');

function readYaml(file) {
  return yaml.load(fs.readFileSync(path.join(__dirname, file), 'utf8'));
}

function fixture(t) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'cartiq-release-check-'));
  const files = ['fullchain.pem', 'privkey.pem', 'fixture-adc.json'];
  t.after(() => {
    for (const file of files) fs.rmSync(path.join(dir,file), {force:true});
    fs.rmdirSync(dir);
  });
  for (const file of files) fs.writeFileSync(path.join(dir, file), 'test placeholder only');
  return {dir, env:{
    DB_USERNAME:'fixture', DB_PASSWORD:'fixture-db', MYSQL_ROOT_PASSWORD:'fixture-root',
    JWT_SECRET:'isolated-test-key'.repeat(4), AI_SERVICE_SECRET:'isolated-ai-key'.repeat(4),
    ALLOWED_ORIGINS:'https://localhost:8443', CARTIQ_TLS_DIR:dir,
  }};
}

test('base and optional Firebase configuration keep a project-owned private database', () => {
  const base = readYaml('../compose.yml');
  const overlay = readYaml('compose.firebase.example.yml');
  validateCompose(base, overlay);
  const shared = structuredClone(base);
  shared.services.backend.environment.DB_URL = 'jdbc:mysql://unapproved.invalid/cartiq';
  assert.throws(() => validateCompose(shared, overlay));
  overlay.services.backend.environment.DB_URL = 'jdbc:mysql://unapproved.invalid/cartiq';
  assert.throws(() => validateCompose(base, overlay));
});

test('staging environment rejects public binds, insecure geocoding and database overrides', t => {
  const {env} = fixture(t);
  validateEnvironment(env);
  assert.throws(() => validateEnvironment({...env, HTTPS_BIND:'0.0.0.0'}), /bind to 127/);
  assert.throws(() => validateEnvironment({...env, CARTIQ_GEOCODER_URL:'http://geocode.invalid'}), /authorized HTTPS/);
  assert.throws(() => validateEnvironment({...env, DB_URL:'jdbc:mysql://unapproved.invalid/cartiq'}), /outside the staging template/);
  assert.throws(() => validateEnvironment({...env, JWT_SECRET:env.AI_SERVICE_SECRET}), /independent/);
});

test('Firebase overlay requires the private ADC path and matching Web project', t => {
  const {dir, env} = fixture(t);
  assert.throws(() => validateEnvironment({...env, FIREBASE_ENABLED:'true'}), /select the development Firebase overlay/i);
  const overlay = {...env, FIREBASE_PROJECT_ID:'cartiq-fixture', FIREBASE_DEV_ADC_FILE:path.join(dir,'fixture-adc.json'),
    EXPO_PUBLIC_FIREBASE_PROJECT_ID:'cartiq-fixture', EXPO_PUBLIC_FIREBASE_API_KEY:'fixture',
    EXPO_PUBLIC_FIREBASE_MESSAGING_SENDER_ID:'fixture', EXPO_PUBLIC_FIREBASE_APP_ID:'fixture', EXPO_PUBLIC_FIREBASE_VAPID_KEY:'fixture'};
  validateEnvironment(overlay, true);
  assert.throws(() => validateEnvironment({...overlay, EXPO_PUBLIC_FIREBASE_PROJECT_ID:'other-fixture'}, true), /matching/);
});

test('public Nominatim requires operator contact and a valid optional reverse point', () => {
  const config = {CARTIQ_GEOCODER_URL:'https://nominatim.openstreetmap.org'};
  assert.throws(() => validateGeocoderEnvironment(config), /CARTIQ_GEOCODER_CONTACT/);
  config.CARTIQ_GEOCODER_CONTACT = 'fixture@example.invalid';
  assert.equal(validateGeocoderEnvironment(config), true);
  assert.throws(() => validateGeocoderEnvironment({...config, CARTIQ_GEOCODER_PUBLIC_REVERSE_POINT:'10, '}), /PUBLIC_REVERSE_POINT/);
  assert.equal(validateGeocoderEnvironment({...config, CARTIQ_GEOCODER_PUBLIC_REVERSE_POINT:'10.942972,76.953417'}), true);
});

test('rejects a nonlocal smoke target and mismatched rollback artifact', async t => {
  const {dir} = fixture(t);
  const artifact = path.join(dir, 'fullchain.pem');
  await assert.rejects(run({origin:'https://unapproved.invalid:8443'}), /loopback/);
  assert.throws(() => verifyArtifact(artifact, 'a'.repeat(64)), /does not match/);
});

const tls = process.env.CARTIQ_SMOKE_TEST_TLS_DIR;
test('checks HTTPS frontend, proxy, private readiness, forecast auth and rollback cart with loopback fixtures',
  {skip:!tls}, async t => {
    const {dir} = fixture(t);
    const endpoint = async server => {
      t.after(() => new Promise(resolve => server.close(resolve)));
      await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
      return server.address().port;
    };
    const reply = (res, code, body) => {
      res.writeHead(code, {'Content-Type':'application/json'});
      res.end(JSON.stringify(body));
    };
    const api = http.createServer((req, res) => {
      if (req.url.startsWith('/actuator/health') || req.url === '/health') reply(res, 200, {status:'UP'});
      else reply(res, req.headers['x-service-token'] === 'fixture-ai' ? 200 : 401, {});
    });
    const privatePort = await endpoint(api);
    const edge = https.createServer({
      cert:fs.readFileSync(path.join(tls,'fullchain.pem')), key:fs.readFileSync(path.join(tls,'privkey.pem')),
    }, (req, res) => {
      if (req.url === '/') {
        res.writeHead(200, {'Content-Type':'text/html','Strict-Transport-Security':'max-age=31536000',
          'X-Content-Type-Options':'nosniff','X-Frame-Options':'DENY','Permissions-Policy':'geolocation=(self)'});
        res.end('<html></html>');
      } else if (req.url === '/api/meta') reply(res, 200, {sample:false,orderingEnabled:false});
      else if (req.url === '/api/categories') reply(res, 200, []);
      else if (req.url === '/api/me/cart' && req.headers.authorization === 'Bearer fixture-user') {
        reply(res, 200, [{productId:'fixture',locationId:'fixture-place',quantity:2}]);
      } else reply(res, 401, {});
    });
    const httpsPort = await endpoint(edge);
    const origin = `https://localhost:${httpsPort}`;
    const redirect = http.createServer((req,res) => { res.writeHead(308,{Location:origin+req.url}); res.end(); });
    const httpPort = await endpoint(redirect);
    const artifact = path.join(dir, 'fullchain.pem');
    const sha256 = createHash('sha256').update(fs.readFileSync(artifact)).digest('hex');
    await run({origin, 'http-port':String(httpPort), 'backend-port':String(privatePort), 'forecast-port':String(privatePort),
      ca:path.join(tls,'ca.pem'),phase:'previous',artifact,sha256,'expected-product':'fixture','expected-location':'fixture-place',
      'expected-quantity':'2'}, {CARTIQ_STAGING_SMOKE_TOKEN:'fixture-user',AI_SERVICE_SECRET:'fixture-ai'});
  });
