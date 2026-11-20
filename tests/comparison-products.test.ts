import test from 'node:test';
import assert from 'node:assert/strict';
import {productGroups,relatedFruitOffers} from '../src/services/comparison-products';
import {matchingPrices,listingUnitPrice} from '../src/services/live-retailers';
import type {RetailerComparison,RetailerLookup,RetailerProduct} from '../src/services/api';
const item:RetailerProduct={nativeId:'test-a',title:'Test toned milk',brand:'Test',currency:'INR',pack:'1 L',priceMinor:7700,mrpMinor:null,
 available:true,inventory:null,storeId:null,providerFetchedAt:null,discountPercent:null,imageUrl:null,productUrl:null,normalizedPack:{count:1,each:{amount:1000,unit:'ml'}}};
const now=Date.now();
function row(retailer:RetailerLookup,products:RetailerProduct[],fresh=true):RetailerComparison['retailers'][number]{
 return {retailer,status:'success',outcome:'SUCCESS',message:null,result:{retailer,products,creditsRemaining:null,fetchedAt:new Date(now-1500).toISOString(),freshUntil:new Date(now+(fresh?5000:-5)).toISOString()}};
}
test('horizontal product row keeps the actual retailer IDs and lists distinct packs independently',()=>{
 const other={...item,nativeId:'test-z'};const smaller={...item,nativeId:'half',pack:'500 ml',normalizedPack:{count:1,each:{amount:500,unit:'ml'}}};
 const groups=productGroups([row('blinkit',[item]),row('zepto',[other,smaller])],now);
 assert.deepEqual(groups.map(g=>g.map(o=>[o.retailer,o.item.nativeId])),[[['blinkit','test-a'],['zepto','test-z']],[['zepto','half']]]);
});
test('ambiguous duplicates remain individual; expired or absent retailers cannot manufacture current offers',()=>{
 const groups=productGroups([row('blinkit',[item,{...item,nativeId:'second'}]),row('zepto',[item],false),
 {retailer:'instamart',status:'error',outcome:'PROVIDER_ERROR',result:null,message:'failure'}],now);
 assert.deepEqual(groups.map(g=>g.length),[1,1,1]);assert.equal(groups[2]?.[0]?.fresh,false);
});

const melon={...item,title:'Watermelon (Sliced) (Dharboosani)',brand:'Freshbury',pack:'200 g',normalizedPack:null};
const figs:RetailerProduct={...item,title:'Fig',brand:'Fruits',pack:'200 - 220 g',normalizedPack:null};
test('fruit aliases, plurals, reordered descriptors and equivalent labelled pack group across stores',()=>{
 const other={...melon,nativeId:'z-melon',title:'Freshbury sliced watermelon',pack:'0.2 kg',priceMinor:4000};
 const groups=productGroups([row('blinkit',[melon]),row('zepto',[other])],now);
 assert.equal(groups.length,1);
 const result=matchingPrices([{retailer:'blinkit',products:[melon]},{retailer:'zepto',products:[other]}])[0];
 assert.equal(result?.lowest,4000);assert.equal(result?.score,90);
 assert.deepEqual(result?.retailers,['zepto']);
});
test('matching weight ranges compare without invented per-kilo estimates; overlapping ranges remain alternatives',()=>{
 const same={...figs,title:'Fresh figs (Anjeer)',brand:null,pack:'0.2 kg – 0.22 kg'};
 const different={...same,nativeId:'other',pack:'200–250 g'};
 const fixed={...same,pack:'220 g'};
 assert.equal(productGroups([row('blinkit',[figs]),row('zepto',[same])],now).length,1);
 for(const item of [fixed,different]){
  const groups=productGroups([row('blinkit',[figs]),row('zepto',[item])],now);
  assert.equal(groups.length,2);
  assert.equal(relatedFruitOffers(groups)[0]?.get('zepto')?.offer.item,item);
  assert.equal(matchingPrices([{retailer:'blinkit',products:[figs]},{retailer:'zepto',products:[item]}]).length,0);
 }
 assert.equal(listingUnitPrice({...figs,normalizedPack:{count:1,each:{amount:220,unit:'g'}}}),null);
});
test('different brands are labelled alternatives; whole, organic and processed fruit never stand in for sliced fruit',()=>{
 for(const title of ['Whole watermelon','Organic sliced watermelon','Watermelon juice','Watermelon sliced seedless']){
  const groups=productGroups([row('blinkit',[melon]),row('zepto',[{...melon,title}])],now);
  assert.equal(groups.length,2);assert.equal(relatedFruitOffers(groups)[0]?.size,0);
 }
 const alt={...melon,brand:'Other Orchard'};
 const groups=productGroups([row('blinkit',[melon]),row('zepto',[alt])],now);
 assert.equal(groups.length,2);
 assert.equal(relatedFruitOffers(groups)[0]?.get('zepto')?.offer.item,alt);
 assert.equal(relatedFruitOffers(productGroups([row('blinkit',[melon]),row('zepto',[alt],false)],now))[0]?.size,0);
});
