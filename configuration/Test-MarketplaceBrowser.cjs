const http=require('node:http'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {chromium}=require(process.env.CARTIQ_PLAYWRIGHT_PATH || 'playwright-core');
const backend=Number(process.argv[2]),web=path.resolve(process.env.CARTIQ_TEST_WEB),evidence=[],checks=[];
const media={'.html':'text/html','.js':'text/javascript','.css':'text/css','.png':'image/png','.ttf':'font/ttf'};
const server=http.createServer((rq,rs)=>{
 if(rq.url.startsWith('/api/')){
  const headers={...rq.headers,host:'127.0.0.1:'+backend};delete headers.origin;
  const upstream=http.request({host:'127.0.0.1',port:backend,path:rq.url,method:rq.method,headers},r=>{
   rs.writeHead(r.statusCode,r.headers);r.pipe(rs);
  });upstream.on('error',()=>rs.writeHead(502).end());rq.pipe(upstream);return;
 }
 const pathname=new URL(rq.url,'http://127.0.0.1').pathname;
 const file=path.resolve(web,'.'+(pathname==='/'?'/index.html':pathname));
 if(!file.startsWith(web+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){rs.writeHead(404).end();return;}
 rs.setHeader('Content-Type',media[path.extname(file)]??'application/octet-stream');fs.createReadStream(file).pipe(rs);
});
(async()=>{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const origin='http://127.0.0.1:'+server.address().port;
 if(!Number.isInteger(backend)||backend<1||backend>65535)throw new Error('Loopback Spring test port required.');
 const chrome=await chromium.launch({channel:'chrome',headless:true});
 try{
  for(const [width,height] of [[1440,900],[1366,768],[390,844]]){
   const context=await chrome.newContext({viewport:{width,height},serviceWorkers:'block',geolocation:{latitude:12.9784,longitude:77.6408,accuracy:9},permissions:['geolocation']});
   await context.route('**/*',route=>new URL(route.request().url()).origin===origin?route.continue():route.abort());
   const p=await context.newPage();p.setDefaultTimeout(13000);
   p.on('response',r=>{if(r.url().startsWith(origin+'/api/'))evidence.push({width,method:r.request().method(),path:new URL(r.url()).pathname,status:r.status(),id:r.headers()['x-request-id']??null});});
   const button=name=>p.getByRole('button',{name,exact:typeof name==='string'}).filter({visible:true}).first();
   const tab=name=>p.getByRole('tab',{name,exact:true}).first();
   const txt=name=>p.getByText(name,{exact:typeof name==='string'}).filter({visible:true}).first();
   const click=async b=>{await b.click();};
   const label=name=>p.getByLabel(name,{exact:typeof name==='string'}).filter({visible:true}).first();
   const call=async(method,part,action,status)=>{const r=p.waitForResponse(r=>r.url().startsWith(origin+part)&&r.request().method()===method);const [res]=await Promise.all([r,action()]);assert.equal(res.status(),status);return res.status()===204?null:res.json();};
   const login=async email=>{await click(button('Log in'));await label('Email address').fill(email);await label('Password').fill(process.env.PHASE9_FIXTURE_PASSWORD);await call('POST','/api/auth/login',()=>click(button('Sign in')),200);};
   console.log('Chrome viewport',width,height);await p.goto(origin);await login('phase9-shopper@example.invalid');
   await button('Use current location').waitFor();
   if(await button('Allow delivery geocoding').isVisible())await click(button('Allow delivery geocoding'));
   await click(button('Use current location'));await txt(/Device accuracy about 9 m/).waitFor();
   await call('PUT','/api/me/shopping-position',()=>click(button('Save and use this location')),200);
   const categories=['Fruits','Vegetables','Milk and Dairy','Rice and Grains','Dals and Pulses','Atta and Flour','Cooking Oil','Snacks','Biscuits and Bakery','Beverages','Breakfast and Cereals','Personal Care','Household Essentials','Baby Products','Frozen Foods'];
   for(const name of categories)await button(name).waitFor();
   if(width===1440){
    for(const [category,title] of [['Fruits','Fixture apples'],['Vegetables','Fixture tomatoes'],['Milk and Dairy','Amul Taaza'],['Snacks','Fixture chips']]){
     await click(button(category));
     const approved=button('Search Blinkit · up to 1 credit');await approved.waitFor();
     const result=await call('POST','/api/shopping/retailers/marketplace/search',()=>click(approved),200);
     assert.equal(result.outcome,'SUCCESS');await txt(title).waitFor();
     await txt(/Live response · Fetched/).waitFor();
     await p.screenshot({path:path.join(process.env.CARTIQ_BROWSER_EVIDENCE,'1440x900-'+category.replaceAll(' ','-')+'.png')});
    }
   }
   if(width===1440){
    await label('Search groceries').fill('Amul Taaza milk');
    await click(button('Search'));
    const typed=button('Search Blinkit · up to 1 credit');
    const result=await call('POST','/api/shopping/retailers/marketplace/search',()=>click(typed),200);
    assert.equal(result.outcome,'SUCCESS');
    await txt('Amul Taaza').waitFor();
    await p.screenshot({path:path.join(process.env.CARTIQ_BROWSER_EVIDENCE,'1440x900-typed-search.png')});
    await click(button('CartIQ home'));
   }
   await click(button('Milk and Dairy'));
   await txt('Amul Taaza').waitFor();
   await txt(/Previously retrieved · Fetched/).waitFor();
   if(width===1440){
    await call('POST','/api/shopping/retailers/marketplace/cart',()=>click(button('Add to Cart')),200);
    await txt('Exact pack added to your planning cart.').waitFor();

   }
   await p.reload();await button('Log out').waitFor();await txt(/Current device location/).waitFor();await click(button('Milk and Dairy'));
   await txt(/Previously retrieved · Fetched/).waitFor();
   assert.match(await button('Open CartIQ cart').innerText(),/Cart \(1\)/);
   await p.screenshot({path:path.join(process.env.CARTIQ_BROWSER_EVIDENCE,width+'x'+height+'-marketplace.png')});
   if(width===1440){
    const quotes=await call('POST','/api/shopping/retailers/marketplace/cart-quotes',()=>click(button('Open CartIQ cart')),200);
    assert.equal(quotes[0].priceMinor,6000);await txt(/fixture-milk/).first().waitFor();
    await click(button('AI Smart Tools').or(tab('AI Smart Tools')).first());
    await click(tab('Shopping plan'));
    const fromCart=button('Copy exact packs from my cart');await fromCart.waitFor();
    await click(fromCart);
    const plan=await call('POST','/api/me/shopping-intelligence/weekly/preview',()=>click(button('Compare my weekly plan')),200);
    assert.ok(plan.plan.comparison.recommended?.packs?.some(pack=>pack.productId==='milk'));
    await p.screenshot({path:path.join(process.env.CARTIQ_BROWSER_EVIDENCE,'1440x900-plan.png')});
    await call('POST','/api/auth/logout',()=>click(button('Log out')),204);
    await login('phase9-peer@example.invalid');await button('Skip for now').waitFor();await click(button('Skip for now'));await click(button('CartIQ home'));
   await click(button('Milk and Dairy'));await txt('Confirm your delivery location before searching retailers.').waitFor();
    assert.match(await button('Open CartIQ cart').innerText(),/Cart \(0\)/);
   }
   checks.push({width,height,result:'PASS'});
   await context.close();
  }
 }finally{await chrome.close();}
})().catch(e=>{console.error(e.message);process.exitCode=1;}).finally(()=>{
 fs.writeFileSync(path.join(process.env.CARTIQ_BROWSER_EVIDENCE,'browser-evidence.json'),JSON.stringify({checks,evidence},null,2));
 server.closeAllConnections();server.close();
});
