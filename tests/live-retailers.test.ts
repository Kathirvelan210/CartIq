import test from 'node:test';
import assert from 'node:assert/strict';
import {filterListings,listingMatch,listingReview,listingUnitPrice,matchingPrices,mergeRetailerRows,retailerMessage,unattemptedRetailers} from '../src/services/live-retailers';

const milk={title:'Fixture Full Cream Milk',brand:'Fixture',available:true as boolean|null,priceMinor:5000 as number|null,currency:'INR',
 normalizedPack:{count:1,each:{amount:500,unit:'ml'}}};

test('comparison requires a matching brand, full title and pack plus two available known prices',()=>{
 const compare=(other:Partial<typeof milk>={})=>
  matchingPrices([{retailer:'blinkit',products:[milk]},{retailer:'zepto',products:[{...milk,...other}]}]);
 assert.deepEqual(compare({priceMinor:4600}).map(({lowest,retailers})=>({lowest,retailers})),
  [{lowest:4600,retailers:['zepto']}]);
 assert.equal(compare({title:'Fixture Toned Milk'}).length,0);
 assert.equal(compare({brand:'Different brand'}).length,0);
 assert.equal(compare({normalizedPack:{count:2,each:{amount:500,unit:'ml'}}}).length,0);
 assert.equal(compare({available:false}).length,0);
 assert.equal(compare({currency:'AED'}).length,0);
 assert.equal(matchingPrices([{retailer:'blinkit',products:[milk,milk]},
  {retailer:'instamart',products:[{...milk,priceMinor:4500}]}]).length,0);
 assert.equal(matchingPrices([{retailer:'blinkit',products:[milk]},
  {retailer:'instamart',products:[{...milk,available:null,priceMinor:null}]}]).length,0);
});

test('retailer status messages keep empty results distinct from timeout and unsupported location',()=>{
 const messages=['SUCCESS','NO_RESULTS','UNAVAILABLE','PROVIDER_ERROR','TIMEOUT','NOT_SUPPORTED','NOT_ATTEMPTED'].map(retailerMessage);
 assert.equal(new Set(messages).size,7);
 assert.match(messages[1]!,/No products/);
 assert.match(messages[3]!,/failed/);
 assert.match(messages[4]!,/timed out/);
});

test('cost and dispatch selection exclude completed retailers and preserve canonical order',()=>{
 assert.deepEqual(unattemptedRetailers(['instamart','zepto','blinkit','zepto'],['blinkit']),['zepto','instamart']);
 assert.deepEqual(unattemptedRetailers(['blinkit'],['blinkit']),[]);
 assert.deepEqual(unattemptedRetailers(['zepto'],[]),['zepto']);
});

test('results from separate explicit retailer searches retain their own outcome and fetch time',()=>{
 const blinkit={retailer:'blinkit',status:'success',fetchedAt:'2026-01-01T00:00:00Z'} as const;
 const zepto={retailer:'zepto',status:'duplicate',fetchedAt:null} as const;
 const swiggy={retailer:'instamart',status:'empty',fetchedAt:'2026-01-01T00:03:00Z'} as const;
 assert.deepEqual(mergeRetailerRows<typeof blinkit|typeof zepto|typeof swiggy>([blinkit,swiggy],[zepto]),
  [blinkit,zepto,swiggy]);
});

test('valid shared barcodes increase confidence and never override pack or formulation conflicts',()=>{
 const verified={...milk,gtin:'8901234567890',flavour:'Plain',formulation:'Full cream'};
 assert.equal(listingMatch(verified,{...verified,gtin:'08901234567890'}).decision,'confirmed');
 assert.equal(listingMatch(verified,verified).score,100);
 const smaller={...verified,normalizedPack:{count:2,each:{amount:250,unit:'ml'}}};
 assert.match(listingMatch(verified,smaller).reason,/Shared barcode conflicts with pack/);
 assert.equal(listingMatch(verified,{...verified,formulation:'Toned'}).decision,'review');
 assert.equal(listingMatch(verified,{...verified,gtin:undefined,flavour:'Vanilla'}).decision,'distinct');
 const rows=[{retailer:'blinkit',products:[verified]},{retailer:'zepto',products:[smaller]}] as const;
 assert.equal(matchingPrices(rows).length,0);
 assert.equal(listingReview(rows)[0]?.match.decision,'review');
 assert.equal(listingMatch(verified,{...verified,barcode:'8901234567891'}).decision,'review');
});

test('comparison calculates comparable item savings, unit price and preserves the per-retailer fetch time',()=>{
 const rows=[
  {retailer:'blinkit',products:[milk],fetchedAt:'2026-01-01T00:00:00Z'},
  {retailer:'zepto',products:[{...milk,priceMinor:4000}],fetchedAt:'2026-01-01T00:01:00Z'},
  {retailer:'instamart',products:[{...milk,available:false,priceMinor:100}]},
 ] as const;
 const [result]=matchingPrices(rows);
 assert.equal(result?.lowest,4000);
 assert.equal(result?.saving,1000);
 assert.equal(result?.savingPercent,20);
 assert.deepEqual(result?.retailers,['zepto']);
 assert.equal(result?.offers[1]?.fetchedAt,'2026-01-01T00:01:00Z');
 assert.deepEqual(listingUnitPrice(milk),{unit:'litre',minor:10000});
 assert.deepEqual(listingUnitPrice({...milk,normalizedPack:{count:2,each:{amount:500,unit:'g'}}}),{unit:'kg',minor:5000});
 assert.equal(listingUnitPrice({...milk,priceMinor:null}),null);
});

test('searches local retailer metadata, sorts known prices and hides unknown availability only when requested',()=>{
 const available={...milk,category:'Dairy',pack:'500 ml'};
 const unknown={...available,priceMinor:null,available:null};
 const lower={...available,priceMinor:4000};
 const rows=[unknown,available,lower];
 assert.deepEqual(filterListings(rows,'dairy fixture 500',true,'low'),[lower,available]);
 assert.deepEqual(filterListings(rows,'full cream',false,'high'),[available,lower,unknown]);
 assert.deepEqual(filterListings(rows,'rice',false,'relevance'),[]);
});

test('complete mandatory charges may change the payable winner; one unknown charge prevents that ranking',()=>{
 const offers=[{retailer:'blinkit' as const,products:[{...milk,priceMinor:4000,deliveryFeeMinor:1000,mandatoryFeesMinor:200}]},
  {retailer:'zepto' as const,products:[{...milk,priceMinor:4700,deliveryFeeMinor:0,mandatoryFeesMinor:100 as number|null}]}];
 assert.equal(matchingPrices(offers)[0]?.lowest,4000);
 assert.deepEqual(matchingPrices(offers)[0]?.payable,{lowest:4800,retailers:['zepto']});
 offers[1]!.products[0]!.mandatoryFeesMinor=null;
 assert.equal(matchingPrices(offers)[0]?.payable,null);
});

test('offer ranking excludes zero inventory and different explicitly reported variants',()=>{
 const base={...milk,variant:'plain',inventory:2};
 const compare=(other:typeof base)=>matchingPrices([{retailer:'blinkit',products:[base]},{retailer:'zepto',products:[other]}]);
 assert.equal(compare({...base,inventory:0}).length,0);
 assert.equal(compare({...base,variant:'sweetened'}).length,0);
});
