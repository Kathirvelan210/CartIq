'use strict';

const assert = require('node:assert/strict');
const {createHash} = require('node:crypto');
const fs = require('node:fs');
const http = require('node:http');
const https = require('node:https');
const {parseArgs} = require('node:util');
const {httpsOrigin} = require('./Check-Release.cjs');

function port(value) {
  const number = Number(value);
  if (!Number.isInteger(number) || number < 1024 || number > 65535) throw new Error('Use an explicit unprivileged staging port.');
  return number;
}

function request(client, options, body) {
  return new Promise((resolve, reject) => {
    const req = client.request({...options, hostname:'127.0.0.1', agent:false, timeout:8000}, res => {
      let length = 0;
      const chunks = [];
      res.on('data', chunk => {
        length += chunk.length;
        if (length > 1024 * 1024) req.destroy(new Error('Smoke response exceeded the body limit.'));
        else chunks.push(chunk);
      });
      res.on('error', reject);
      res.on('end', () => resolve({status:res.statusCode, headers:res.headers, body:Buffer.concat(chunks).toString('utf8')}));
    });
    req.on('error', reject);
    req.on('timeout', () => req.destroy(new Error('A local smoke probe timed out.')));
    req.end(body);
  });
}

function status(response, expected, label) {
  assert(response.status === expected, `${label} returned ${response.status}, expected ${expected}.`);
}

function json(response, label) {
  try { return JSON.parse(response.body); } catch { throw new Error(`${label} did not return JSON.`); }
}

function verifyArtifact(file, expected) {
  assert(/^[a-f0-9]{64}$/i.test(expected), 'Supply the approved SHA-256 for the artifact.');
  const actual = createHash('sha256').update(fs.readFileSync(file)).digest('hex');
  assert(actual === expected.toLowerCase(), 'The supplied package does not match its approved SHA-256.');
}

async function run(args, env = process.env) {
  const origin = httpsOrigin(args.origin || 'https://localhost:8443', '--origin');
  if (!['127.0.0.1', 'localhost'].includes(origin.hostname)) throw new Error('Smoke probes require a literal loopback origin.');
  const tlsPort = port(origin.port);
  const httpPort = port(args['http-port'] || 8081);
  if (httpPort === tlsPort || !args.ca) throw new Error('Supply a private staging CA file and distinct HTTP and HTTPS ports.');
  const ca = fs.readFileSync(args.ca);
  const {phase} = args;
  if (phase) {
    if (!['candidate', 'previous', 'roll-forward'].includes(phase)
        || !args.artifact || !args.sha256 || !args['expected-product'] || !args['expected-location']
        || !/^[1-9][0-9]*$/.test(args['expected-quantity'] || '') || !env.CARTIQ_STAGING_SMOKE_TOKEN
        || !args['backend-port']) throw new Error('Rollback verification needs a phase, approved artifact hash, backend port, and disposable account token and cart fixture.');
    verifyArtifact(args.artifact, args.sha256);
  }

  const secure = (path, options = {}, body) => request(https, {
    port:tlsPort, path, ca, rejectUnauthorized:true,
    ...(origin.hostname === 'localhost' ? {servername:'localhost'} : {}),
    ...options,
    headers:{Host:origin.host, ...options.headers},
  }, body);
  const privateHttp = (number, path, options) => request(http, {...options, port:port(number), path});

  const redirect = await privateHttp(httpPort, '/api/meta?probe=1', {headers:{Host:'untrusted.invalid'}});
  status(redirect, 308, 'HTTP redirect');
  assert(redirect.headers.location === origin.origin + '/api/meta?probe=1', 'Redirect canonical origin, path or query differs.');
  const home = await secure('/');
  status(home, 200, 'Expo index');
  assert(/text\/html/i.test(home.headers['content-type'] || '') && /<html\b/i.test(home.body), 'Expected a Web HTML index.');
  assert(/max-age=[1-9][0-9]*/i.test(home.headers['strict-transport-security'] || ''), 'Expected HSTS on HTTPS.');
  assert(home.headers['x-content-type-options'] === 'nosniff' && home.headers['x-frame-options'] === 'DENY'
    && home.headers['permissions-policy']?.includes('geolocation=(self)'), 'Expected security headers.');
  const meta = await secure('/api/meta');
  status(meta, 200, 'API metadata');
  const metadata = json(meta, 'API metadata');
  assert(metadata.sample === false && metadata.orderingEnabled === false, 'Expected sample and ordering features disabled.');
  const categories = await secure('/api/categories');
  status(categories, 200, 'Catalogue categories');
  assert(Array.isArray(json(categories, 'Catalogue categories')), 'Expected a category array.');
  status(await secure('/api/me/cart'), 401, 'Anonymous cart');
  console.log('PASS trusted HTTPS, fixed 308, edge headers, Web index, production API and anonymous cart boundary');

  if (args['backend-health'] && args['backend-health'] !== 'legacy') throw new Error('The optional backend health mode is legacy.');
  if (args['backend-port']) {
    const healthPath = args['backend-health'] === 'legacy' ? '/actuator/health' : '/actuator/health/readiness';
    const health = await privateHttp(args['backend-port'], healthPath);
    status(health, 200, 'Backend readiness');
    assert(json(health, 'Backend readiness').status === 'UP', 'Expected backend readiness UP.');
    console.log('PASS direct loopback backend readiness');
  }
  if (args['forecast-port']) {
    const health = await privateHttp(args['forecast-port'], '/health');
    status(health, 200, 'Forecast health');
    status(await privateHttp(args['forecast-port'], '/models/status'), 401, 'Anonymous forecast model status');
    if (env.AI_SERVICE_SECRET) {
      status(await privateHttp(args['forecast-port'], '/models/status', {headers:{'X-Service-Token':env.AI_SERVICE_SECRET}}),
        200, 'Authenticated forecast model status');
    }
    console.log('PASS loopback forecast health and authentication boundary');
  }

  if (!phase) return;
  const authenticated = {headers:{Authorization:'Bearer ' + env.CARTIQ_STAGING_SMOKE_TOKEN}};
  const cart = await secure('/api/me/cart', authenticated);
  status(cart, 200, 'Disposable rollback cart read');
  assert(json(cart, 'Disposable rollback cart').some(item => item.productId === args['expected-product']
    && item.locationId === args['expected-location'] && item.quantity === Number(args['expected-quantity'])),
  'Expected the preserved disposable cart fixture.');
  console.log(`PASS ${phase} artifact hash and disposable account cart fixture`);
}

if (require.main === module) {
  const stringKeys = ['origin','http-port','ca','backend-port','backend-health','forecast-port','phase','artifact','sha256',
    'expected-product','expected-location','expected-quantity'];
  (async () => {
    const {values} = parseArgs({options:Object.fromEntries(stringKeys.map(key => [key,{type:'string'}]))});
    await run(values);
  })().catch(error => {
    // Never emit response bodies, configuration values, request credentials or raw network errors.
    console.error('FAIL', error.code ? [ 'ERR_ASSERTION' ].includes(error.code) ? error.message :
      'A local input, TLS or connection check failed (' + error.code + ').' : error.message);
    process.exitCode = 1;
  });
}

module.exports = {run, verifyArtifact};
