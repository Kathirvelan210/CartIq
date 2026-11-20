import test from 'node:test';
import assert from 'node:assert/strict';
import {groceryQueries} from '../src/services/grocery-categories';

test('all marketplace categories provide unique plain-text query suggestions',()=>{
 assert.equal(Object.keys(groceryQueries).length,15);
 assert.deepEqual(groceryQueries.Fruits,['apple','banana','orange','grapes','mango']);
 assert.equal(groceryQueries.Vegetables[0],'tomato');
 assert.equal(groceryQueries['Milk and Dairy'][0],'milk');
 assert.equal(groceryQueries.Snacks[0],'chips');
 for(const suggestions of Object.values(groceryQueries)){
  assert.ok(suggestions.length>=2);
  assert.equal(new Set<string>(suggestions).size,suggestions.length);
  for(const query of suggestions)assert.match(query,/^[a-z ]+$/);
 }
});

import {claimBrowseTickets,searchAuthorizedRetailers} from '../src/services/department-lookup';
import {departmentSearch} from '../src/services/grocery-categories';

test('a submitted query claims at most one unexpired ticket for each uncached retailer at the selected location',()=>{
 assert.equal(departmentSearch.Fruits,'fruits');
 const now=Date.now(),expiresAt=new Date(now+60000).toISOString();
 const milk={query:'milk',retailer:'blinkit',expiresAt};
 const zepto={...milk,retailer:'zepto'},instamart={...milk,retailer:'instamart'};
 const rows=[instamart,{...milk,query:'dairy'},{...milk,expiresAt:new Date(now).toISOString()},milk,zepto,zepto];
 const intent={scope:'account and coordinates',used:false};
 assert.deepEqual(claimBrowseTickets(intent,'other location','milk',rows,[],now),[]);
 assert.deepEqual(claimBrowseTickets(intent,intent.scope,' Milk ',rows,[],now),[milk,zepto,instamart]);
 assert.deepEqual(claimBrowseTickets(intent,intent.scope,'milk',rows,[],now),[]);
 assert.deepEqual(claimBrowseTickets(undefined,intent.scope,'milk',rows,[],now),[]);
 assert.deepEqual(claimBrowseTickets({...intent,used:false},intent.scope,'milk',rows,[{retailer:'blinkit',freshUntil:expiresAt}],now),[zepto,instamart]);
});

test('retailer calls run in order, preserve settled partial failures and stop on uncertain billing or navigation',async()=>{
 const tickets=['blinkit','zepto','instamart'].map(retailer=>({retailer,query:'milk',expiresAt:new Date(Date.now()+60000).toISOString()}));
 const seen:string[]=[],controller=new AbortController();
 await searchAuthorizedRetailers(tickets,async t=>{seen.push(t.retailer);return t.retailer==='zepto'?'UNCERTAIN':'NO_CHARGE_SETTLED';},controller.signal);
 assert.deepEqual(seen,['blinkit','zepto']);
 seen.length=0;
 await searchAuthorizedRetailers(tickets,async t=>{seen.push(t.retailer);controller.abort();return 'CHARGED_SETTLED';},controller.signal);
 assert.deepEqual(seen,['blinkit']);
});
