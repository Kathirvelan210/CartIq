import test from 'node:test';
import assert from 'node:assert/strict';
import {comparableListings,freshMarketplaceRows} from '../src/services/marketplace-comparison';
test('compares only fresh in-stock verified listings for the same exact pack across retailers',()=>{
 const now=Date.now(),freshUntil=new Date(now+60000).toISOString();
 const item={listing:{title:'Fixture',pack:'1 l',priceMinor:5000,available:true},evidence:{match:'VERIFIED',variantId:'litre',freshUntil}};
 const row={retailer:'blinkit',freshUntil,fetchedAt:new Date(now).toISOString(),listings:[item]};
 for(const other of [{...item,evidence:{...item.evidence,variantId:'half-litre'}},
  {...item,evidence:{...item.evidence,match:'UNRESOLVED'}},{...item,listing:{...item.listing,available:false}},
  {...item,evidence:{...item.evidence,freshUntil:new Date(now-1).toISOString()}}])
  assert.deepEqual(comparableListings([row,{...row,retailer:'zepto',listings:[other]}],now),[]);
 assert.equal(comparableListings([row,{...row,retailer:'zepto',listings:[{...item,listing:{...item.listing,priceMinor:4900}}]}],now)[0]?.prices[0]?.retailer,'zepto');
});

test('retailer search hides expired results and individual expired listings without losing unpriced fresh products',()=>{
 const now=Date.now(),future=new Date(now+60000).toISOString(),expired=new Date(now).toISOString();
 const listing={title:'Milk',pack:'1 litre',available:null,priceMinor:null};
 const item={listing,evidence:{match:'UNRESOLVED',variantId:null,freshUntil:future}};
 const row={retailer:'blinkit',freshUntil:future,fetchedAt:expired,listings:[item,{...item,evidence:{...item.evidence,freshUntil:expired}}]};
 assert.deepEqual(freshMarketplaceRows([row,{...row,retailer:'zepto',freshUntil:expired}],now),[{...row,listings:[item]}]);
});
