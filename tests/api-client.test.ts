import {strict as assert} from 'node:assert';
import {once} from 'node:events';
import {createServer} from 'node:http';
import {test} from 'node:test';
import {ApiError,fetchJson,resolveApiUrl} from '../src/services/api-client';

test('the API base normalizes the prefix and keeps loopback browser cookies same-site',()=>{
 assert.equal(resolveApiUrl('https://api.example.test/api///',false),'https://api.example.test/api');
 assert.equal(resolveApiUrl('http://127.0.0.1:8080',true,'http://localhost:8092'),'http://localhost:8080/api');
 assert.equal(resolveApiUrl('http://localhost:8080/api',true,'http://127.0.0.1:8092'),'http://127.0.0.1:8080/api');
 assert.equal(resolveApiUrl('http://192.168.1.23:8080',true),'http://192.168.1.23:8080/api');
 assert.throws(()=>resolveApiUrl('http://192.168.1.23:8080',false),/secure server connection/);
 assert.throws(()=>resolveApiUrl('https://user:password@api.example.test',false),/invalid/);
});

test('the API transport sends JSON and bearer auth, preserves errors, and times out while reading',async()=>{
 let received = '';
 const server = createServer(async(req,res)=>{
  if(req.url==='/stall'){
   res.writeHead(200,{'Content-Type':'application/json'});
   res.write('{"unfinished":');
   return;
  }
  res.setHeader('Content-Type','application/json');
  if(req.url==='/reject'){
   res.writeHead(403);
   return res.end(JSON.stringify({message:'A backend validation message.'}));
  }
  for await(const data of req)received+=data.toString();
  res.end(JSON.stringify({
   method:req.method,
   authorization:req.headers.authorization,
   contentType:req.headers['content-type'],
  }));
 });
 server.listen(0,'127.0.0.1');
 await once(server,'listening');
 const address=server.address();
 assert(address&&typeof address!=='string');
 const url=`http://127.0.0.1:${address.port}`;
 try{
  const result=await fetchJson(url+'/cart',{
   method:'PUT',body:JSON.stringify({quantity:2}),
  },'test-token');
  assert.deepEqual(result,{method:'PUT',authorization:'Bearer test-token',contentType:'application/json'});
  assert.deepEqual(JSON.parse(received),{quantity:2});
  await assert.rejects(fetchJson(url+'/reject',{}),error=>error instanceof ApiError&&error.status===403&&error.message==='A backend validation message.');
  await assert.rejects(fetchJson(url+'/stall',{},null,70),/connection timed out/);
  const controller=new AbortController();
  controller.abort();
  await assert.rejects(fetchJson(url+'/stall',{signal:controller.signal}),{name:'AbortError'});
 }finally{
  server.closeAllConnections();
  await new Promise<void>(resolve=>server.close(()=>resolve()));
 }
});
