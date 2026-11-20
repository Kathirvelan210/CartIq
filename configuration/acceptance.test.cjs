'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const os = require('node:os');
const {accept,artifactHash} = require('./Accept-Release.cjs');

test('acceptance produces both reports, redacts input, and keeps prior evidence separate', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(),'cartiq-acceptance-test-'));
  const fixture = path.join(dir,'web-bundle.zip');
  const evidence = path.join(dir,'historic.txt');
  const config = path.join(dir,'input.json');
  const results = path.join(dir,'report');
  const envFile = path.join(dir,'invalid.env');
  const secret = 'release-secret-fixture-0000-should-omit';
  fs.writeFileSync(fixture,'artifact fixture');
  fs.writeFileSync(evidence,'synthetic history '+secret);
  fs.writeFileSync(envFile,'JWT_SECRET='+secret+'\n');
  fs.writeFileSync(config,JSON.stringify({
    artifacts:[{component:'web',file:fixture,sha256:await artifactHash(fixture),version:'1.0.0'}],
    priorEvidence:[{check:'https',file:evidence,sha256:await artifactHash(evidence)}],
  }));
  const {report} = await accept({'output-dir':results,'inputs':config,'env-file':envFile,'skip-maven':true},{QUICKCOMMERCE_API_KEY:secret});
  assert.equal(report.overall,'FAIL');
  assert.equal(report.checks.find(check=>check.id === 'env').status,'FAIL');
  assert.equal(report.checks.find(check=>check.id === 'https').status,'BLOCKED_EXTERNAL');
  assert.equal(report.checks.find(check=>check.id === 'artifacts').status,'PASS');
  assert.equal(report.priorEvidence[0].status,'PASS');
  for (const command of report.commands) assert(command.startedAt && command.finishedAt);
  for (const ext of ['json','md']) {
    const contents=fs.readFileSync(path.join(results,'release-acceptance.'+ext),'utf8');
    assert(!contents.includes(secret));
    assert.doesNotMatch(contents,/Response body:|Authorization:|JWT_SECRET=/i);
  }
});

test('known external HTTPS origin fails without dispatch and tampered artifacts are rejected', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(),'cartiq-acceptance-reject-'));
  const fixture = path.join(dir,'unsafe-web.zip');
  const input = path.join(dir,'input.json');
  fs.writeFileSync(fixture,'tampered');
  fs.writeFileSync(input,JSON.stringify({https:{origin:'https://example.invalid:18443',httpPort:18081,ca:'unused.pem'},
    artifacts:[{component:'web',file:fixture,sha256:'a'.repeat(64),version:'1.0.0'}]}));
  const {report} = await accept({'output-dir':path.join(dir,'report'),inputs:input,'skip-maven':true},{});
  assert.equal(report.checks.find(check=>check.id === 'https').status,'FAIL');
  assert.equal(report.checks.find(check=>check.id === 'artifacts').status,'FAIL');
  assert.equal(report.checks.find(check=>check.id === 'mysql').status,'BLOCKED_EXTERNAL');
  assert.equal(report.overall,'FAIL');
});

test('invalid input still emits both reports with a status for every requirement', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(),'cartiq-acceptance-input-'));
  const malformed = path.join(dir,'input.json');
  fs.writeFileSync(malformed,'{"unrecognized":"fixture"}');
  const {report} = await accept({'output-dir':path.join(dir,'report'),inputs:malformed},{});
  assert.equal(report.overall,'FAIL');
  assert.equal(report.checks[0].status,'FAIL');
  assert(report.checks.length >= 13 && report.checks.slice(1).every(check=>check.status === 'NOT_TESTED'));
  assert.equal(report.commands.length,0);
});

test('private forecast probe verifies health and authentication independently of the edge', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(),'cartiq-acceptance-forecast-'));
  const secret = 'private-forecast-fixture-value-should-omit';
  const server = http.createServer((request,response) => {
    if (request.url === '/health') response.statusCode = 200;
    else if (request.url === '/models/status') response.statusCode = request.headers['x-service-token'] === secret ? 200 : 401;
    else response.statusCode = 404;
    response.end(JSON.stringify({status:'ok',livePriceProvider:false,fixture:secret}));
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  try {
    const file = path.join(dir,'input.json');
    fs.writeFileSync(file,JSON.stringify({https:{forecastPort:server.address().port}}));
    const {report} = await accept({inputs:file,'output-dir':path.join(dir,'report'),'skip-maven':true},{AI_SERVICE_SECRET:secret});
    assert.equal(report.checks.find(check=>check.id === 'forecast').status,'PASS');
    assert.equal(report.checks.find(check=>check.id === 'https').status,'BLOCKED_EXTERNAL');
    assert.deepEqual(report.commands.find(command=>command.command.startsWith('GET loopback /health')).httpStatuses,[200,401,200]);
    assert(!JSON.stringify(report).includes(secret));
  } finally { await new Promise(resolve=>server.close(resolve)); }
});
