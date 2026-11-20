// Run against the isolated Phase 10.4 fixture pair and same-origin Expo Web export.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const http = require('node:http');
const os = require('node:os');
const path = require('node:path');
const {randomUUID} = require('node:crypto');
const {chromium} = require(path.join(os.homedir(), '.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright-core'));
const scratch = path.resolve(process.argv[2]);
const web = path.resolve(process.argv[3] || path.join(scratch, 'web'));
const cases = [
  ['success', 200, null, 200], ['auth', 503, 'GEMINI_AUTH', 401],
  ['model', 503, 'GEMINI_MODEL_OR_ENDPOINT', 404], ['payload', 502, 'GEMINI_INVALID_REQUEST', 400],
  ['quota', 429, 'GEMINI_QUOTA', 429], ['server', 502, 'GEMINI_SERVER', 503],
  ['connection', 502, 'GEMINI_CONNECTION', null], ['timeout', 504, 'GEMINI_TIMEOUT', null],
  ['malformed', 502, 'GEMINI_INVALID_OUTPUT', 200]
];
const server = http.createServer((req, res) => {
  if (req.url.startsWith('/api/')) {
    const headers = {...req.headers, host:'127.0.0.1:18201'};
    delete headers.origin;
    const upstream = http.request({hostname:'127.0.0.1',port:18201,path:req.url,method:req.method,headers},
      r => {res.writeHead(r.statusCode,r.headers); r.pipe(res);});
    upstream.on('error', () => {res.writeHead(503); res.end();});
    req.pipe(upstream);
    return;
  }
  const file = path.resolve(web,'.' + (req.url === '/' ? '/index.html' : new URL(req.url,'http://localhost').pathname));
  if (!file.startsWith(web + path.sep) || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
    res.writeHead(404); res.end(); return;
  }
  res.setHeader('Content-Type',({'.js':'text/javascript','.html':'text/html','.ttf':'font/ttf','.png':'image/png'})[path.extname(file)] || 'application/octet-stream');
  fs.createReadStream(file).pipe(res);
});

async function run() {
  // Fail closed if the test-only fixture is absent or stale.
  assert.ok(fs.readFileSync(path.join(scratch,'mock-ai.log'),'utf8').includes('Uvicorn running on http://127.0.0.1:18202'));
  assert.ok(!fs.existsSync(path.join(scratch,'events.jsonl')));
  await new Promise(resolve => server.listen(0,'127.0.0.1',resolve));
  const origin = 'http://127.0.0.1:' + server.address().port;
  const browser = await chromium.launch({channel:'chrome'});
  const rows = [];
  try {
    for (const [scenario,expected,classification,upstream] of cases) {
      const ctx = await browser.newContext({serviceWorkers:'block',viewport:{width:1440,height:900}});
      try {
        await ctx.route('**/*', route => new URL(route.request().url()).origin === origin ? route.continue() : route.abort());
        const email = randomUUID()+'@fixture.invalid', password = randomUUID()+'Ab2';
        const registered = await fetch(origin+'/api/auth/register',{method:'POST',headers:{'content-type':'application/json'},
          body:JSON.stringify({name:'Fixture shopper',email,password})});
        assert.equal(registered.status,201);
        const page = await ctx.newPage();
        page.setDefaultTimeout(15000);
        const button = name => page.getByRole('button',{name,exact:true}).filter({visible:true}).first();
        await page.goto(origin);
        await button('Log in').click();
        await page.getByLabel('Email address',{exact:true}).fill(email);
        await page.getByLabel('Password',{exact:true}).fill(password);
        await button('Sign in').click();
        await button('Skip for now').click();
        await button('AI Smart Tools').or(page.getByRole('tab',{name:'AI Smart Tools',exact:true})).first().click();
        await page.getByRole('tab',{name:'Recipes',exact:true}).first().click();
        await page.getByLabel('What would you like to cook?',{exact:true}).fill('Offline recipe diagnostic '+scenario);
        let submissions = 0;
        const endpoint = '/api/me/recipe-intelligence/generate';
        page.on('request',r => {if (new URL(r.url()).pathname === endpoint) submissions++;});
        const wait = page.waitForResponse(r => new URL(r.url()).pathname === endpoint);
        await button('Generate recipe draft').click();
        const reply = await wait;
        const data = await reply.json(), headers = await reply.allHeaders();
        assert.equal(reply.status(),expected);
        const id = headers['x-request-id'];
        assert.match(id,/^[\da-f-]{36}$/);
        const message = classification ? data.message : data.recipe.substitutions[0];
        if (classification) {
          assert.equal(data.code,classification);
        } else assert.equal(data.kind,'GENERATED');
        assert.equal(headers['x-gemini-http-status'], upstream === null ? undefined : String(upstream));
        assert.equal(headers['x-gemini-request-id'], upstream === null ? undefined : 'fixture-'+scenario);
        await page.getByText(message,{exact:true}).first().waitFor();
        assert.ok(!(await page.locator('body').innerText()).includes('private-fixture-detail'));
        const events = fs.readFileSync(path.join(scratch,'events.jsonl'),'utf8').trim().split('\n').map(JSON.parse).filter(e=>e.case===scenario);
        const dispatches = events.filter(e=>e.stage==='dispatch').length, receipts = events.filter(e=>e.stage==='received').length;
        assert.equal(submissions,1); assert.equal(dispatches,1); assert.equal(receipts,scenario==='connection'?0:1);
        const log = fs.readFileSync(path.join(scratch,'mock-ai.log'),'utf8').split('\n').filter(l=>l.includes('gemini_result requestId='+id+' '));
        assert.equal(log.length,1);
        assert.ok(log[0].includes('outcome='+(classification||'SUCCESS')+' providerHttp='+(upstream??'None')+' providerRequestId='+(upstream===null?'None':'fixture-'+scenario)+' '));
        rows.push({scenario,cartiqRequestId:id,cartiqHttp:reply.status(),classification:classification||'SUCCESS',upstreamHttp:upstream,
          fixtureRequestId:upstream===null?null:'fixture-'+scenario,publicProviderId:headers['x-gemini-request-id']||null,visibleText:message,submissions,dispatches,receipts});
        console.log(scenario+' passed');
      } finally {await ctx.close();}
    }
  } finally {
    fs.writeFileSync(path.join(scratch,'browser-result.json'),JSON.stringify(rows,null,2)+'\n');
    await browser.close();
    server.close();
  }
}
run().catch(error => {console.error(error.name, String(error.message).slice(0,450));server.close();process.exitCode=1;});
