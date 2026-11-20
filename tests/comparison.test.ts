import {strict as assert} from 'node:assert';
import {test} from 'node:test';
import {readJsonResponse} from '../src/services/response';
import {validCoordinates,comparisonAmount,comparisonLabel,recommendationText} from '../src/services/four-source';
import type {ComparisonOption,ComparisonResult} from '../src/types';
test('four-source location requires explicit valid coordinates and radius',()=>{
 assert.equal(validCoordinates('','','5'),null);assert.equal(validCoordinates('91','0','5'),null);
 assert.equal(validCoordinates('0','0','0'),null);assert.equal(validCoordinates('0','0','101'),null);
 assert.deepEqual(validCoordinates('12.97','77.64','5'),{latitude:12.97,longitude:77.64,radiusKm:5});
});
test('four-source display preserves unknowns and uses the backend recommendation',()=>{
 const option={source:'instamart',name:'Instamart',sourceId:'instamart',priceMinor:7000,eligible:false,reason:'Unknown charges'} as ComparisonOption;
 const result={options:[option],lowestEligible:null,explanation:'No confirmed total'} as ComparisonResult;
 assert.equal(comparisonAmount(null),'Unknown');assert.equal(comparisonAmount(0),'₹0');
 assert.equal(comparisonLabel(option),'Swiggy Instamart');assert.equal(recommendationText(result),'No eligible option');
 assert.equal(recommendationText({...result,lowestEligible:{...option,eligible:true}}),'Cheapest eligible: Instamart');
});
test('successful empty create and delete responses do not report false failures',async()=>{assert.equal(await readJsonResponse(new Response(null,{status:201})),undefined);assert.equal(await readJsonResponse(new Response(null,{status:204})),undefined);});
test('JSON responses parse correctly and malformed responses report a useful error',async()=>{assert.deepEqual(await readJsonResponse(new Response('{"amountMinor":10000}')),{amountMinor:10000});await assert.rejects(readJsonResponse(new Response('broken-json')),/unreadable response/);});
import {comparison,isFresh} from '../src/services/comparison';
import {demoProducts} from '../src/data/demo';
import {purchaseDestination} from '../src/services/purchase-links';
import {retailerCatalogue,withRetailerCatalogue} from '../src/data/retailer-catalogue';
import {canonicalListing} from '../src/data/retailer-catalogue';
import {productPhoto} from '../src/data/product-photos';
const location='blr-indiranagar';
test('retailer listings need a unique exact catalogue identity before comparison',()=>{
 const listing=retailerCatalogue[0]!;
 const exact={...listing,id:'canonical-variant',retailerListing:undefined};
 assert.equal(canonicalListing(listing,[]),null);
 assert.equal(canonicalListing(listing,[{...exact,quantity:'1 kg'}]),null);
 assert.equal(canonicalListing(listing,[exact]),exact);
 assert.equal(canonicalListing(listing,[exact,{...exact,id:'other'}]),null);
});
test('a saved four-source snapshot is not silently repriced as an online cart line',()=>{
 const p=demoProducts(location)[0]!;
 const entry={productId:p.id,locationId:location,quantity:2,selection:{source:'offline',sourceId:'shop',name:'Shop',priceMinor:100,totalMinor:200,observedAt:null,quantity:2,fulfillment:'pickup' as const}};
 assert.equal(cartLines([entry],[p],location).length,0);
 const changed=setItemQuantity([entry],p.id,location,3);
 assert.equal(changed[0]?.selection?.sourceId,'shop');
 assert.equal(changed[0]?.quantity,3);
 assert.equal(changed[0]?.selection?.quantity,2); // old quote remains visibly stale
 assert.equal(cartLines(changed,[p],location).length,0); // never silently assign an online winner
});
test('retailer photos use the product image and sample photos match the exact pack',()=>{
 const milk=demoProducts(location)[0]!;
 assert.match(productPhoto(milk,true)!,/^https:\/\/cdn\.grofers\.com\//);
 assert.equal(productPhoto({...milk,quantity:'500 ml'},true),undefined);
 assert.equal(productPhoto(milk,false),undefined);
 for(const product of retailerCatalogue) assert.equal(productPhoto(product,false),product.imageUrl);
});
test('public catalogue listings open their exact pages without entering price comparisons',()=>{
 for(const product of retailerCatalogue){
  const listing=product.retailerListing!;
  assert.deepEqual(purchaseDestination(listing.platform,undefined,listing.url),{url:listing.url,exact:true});
  assert.equal(comparison(product,location).best,undefined);
 }
 const serverProduct={...retailerCatalogue[0]!,retailerListing:undefined};
 assert.equal(withRetailerCatalogue([serverProduct]).filter(product=>product.id===serverProduct.id).length,1);
});
test('the best eligible offer routes to its verified retailer product page',()=>{
 const product=demoProducts(location)[0]!;
 product.offers[0]={...product.offers[0]!,verificationStatus:'VERIFIED',purchaseUrl:'https://blinkit.com/prn/example/prid/123?tracking=test'};
 const best=comparison(product,location).best!;
 assert.deepEqual(purchaseDestination(best.platform,best),{url:'https://blinkit.com/prn/example/prid/123',exact:true});
});
test('missing, sample, and off-retailer product links fall back to the selected store',()=>{
 const offer=demoProducts(location)[0]!.offers[0]!;
 for(const purchaseUrl of [undefined,'http://blinkit.com/prn/example/prid/123','https://blinkit.com.example.org/prn/example/prid/123','https://blinkit.com/cn/milk/cid/14/922']){
  assert.deepEqual(purchaseDestination('blinkit',{...offer,verificationStatus:'VERIFIED',purchaseUrl}),{url:'https://blinkit.com/',exact:false});
 }
 assert.equal(purchaseDestination('instamart',offer).url,'https://www.swiggy.com/instamart');
 assert.equal(purchaseDestination('blinkit',{...offer,productId:'oats',verificationStatus:'SAMPLE',purchaseUrl:'https://blinkit.com/prn/example/prid/123'}).exact,false);
 assert.equal(purchaseDestination('blinkit',offer).url,'https://blinkit.com/prn/amul-taaza-homogenised-toned-milk/prid/176');
});
test('unverified observations cannot become the lowest comparable price',()=>{const p=demoProducts(location)[0]!;p.offers[0]={...p.offers[0]!,price:1,verificationStatus:'UNVERIFIED'};assert.equal(comparison(p,location).best?.platform,'instamart');});
test('an offer with missing verification cannot become the lowest comparable price',()=>{const p=demoProducts(location)[0]!;p.offers[0]={...p.offers[0]!,price:1,verificationStatus:undefined};assert.equal(comparison(p,location).best?.platform,'instamart');});
test('corrupt persisted purchase preferences are rejected before rendering',()=>{assert.throws(()=>parseCart(JSON.stringify([{productId:'milk',locationId:location,quantity:1,preferences:{intent:null}}])));});
test('chooses lowest comparable price and computes exact savings',()=>{const p=demoProducts(location)[0]!;const c=comparison(p,location);assert.equal(c.best?.platform,'blinkit');assert.equal(c.saving,4);});
test('excludes stale, mismatched, unavailable and invalid offers',()=>{const p=demoProducts(location)[0]!;for(const patch of [{updatedAt:'2020-01-01'},{variant:'500 ml'},{locationId:'other'},{status:'out_of_stock' as const},{price:-1},{price:NaN},{productId:'other'},{price:null}]){const changed={...p,offers:[{...p.offers[0]!,...patch},p.offers[1]!]};assert.equal(comparison(changed,location).best?.platform,'instamart');}});
test('identifies equal prices without inventing savings',()=>{const p=demoProducts(location)[0]!;p.offers[1]!.price=p.offers[0]!.price;assert.equal(comparison(p,location).tied,true);assert.equal(comparison(p,location).saving,0);});
test('handles total provider failure and partial availability',()=>{const p=demoProducts(location)[0]!;assert.equal(comparison({...p,offers:[]},location).best,undefined);p.offers=p.offers.slice(0,1);assert.equal(comparison(p,location).saving,0);});
test('rejects future and malformed timestamps',()=>{const o=demoProducts(location)[0]!.offers[0]!;assert.equal(isFresh({...o,updatedAt:'invalid'}),false);assert.equal(isFresh({...o,updatedAt:new Date(Date.now()+120000).toISOString()}),false);});

import {addCartItem,cartLines} from '../src/services/cart';
test('cart merges quantities and keeps locations separate',()=>{let entries=addCartItem([],'milk',location);entries=addCartItem(entries,'milk',location);entries=addCartItem(entries,'milk','other');assert.equal(entries.length,2);assert.equal(entries[0]!.quantity,2);assert.equal(addCartItem([{productId:'milk',locationId:location,quantity:99}],'milk',location)[0]!.quantity,99);});
test('cart uses fresh lowest prices and excludes missing prices from totals',()=>{const products=demoProducts(location);const entries=[{productId:'milk',locationId:location,quantity:2},{productId:'missing',locationId:location,quantity:1}];const lines=cartLines(entries,products,location);assert.equal(lines[0]!.best?.platform,'blinkit');assert.equal(lines[0]!.total,136);assert.equal(lines[1]!.total,0);products[0]!.offers[1]!.price=60;assert.equal(cartLines(entries,products,location)[0]!.best?.platform,'instamart');assert.equal(cartLines(entries,products,'other').length,0);products[0]!.offers.forEach(o=>o.updatedAt='2020-01-01');assert.equal(cartLines(entries,products,location)[0]!.total,0);});

import {clearLocationCart,groupCart,parseCart,setItemQuantity} from '../src/services/cart';
import {Address,CheckoutRequest,OrderStatus,RetailerQuote,addressError,assertValidQuote,confirmedOrder,createRetailerService,quoteFingerprint,removeConfirmedItems} from '../src/services/checkout';
import {locations} from '../src/data/demo';
const entries=[{productId:'milk',locationId:location,quantity:2},{productId:'almonds',locationId:location,quantity:1}];
const delivery:Address={id:'test-address',locationId:location,label:'Home',name:'Test Shopper',phone:'9876543210',line1:'12 Test Street',line2:'',city:'Bengaluru',postcode:'560038',isDefault:true};
const checkoutRequest:CheckoutRequest={locationId:location,items:[{productId:'milk',quantity:2}],address:delivery};
function validQuote():RetailerQuote{return {id:'backend-quote-reference',platform:'blinkit',locationId:location,items:[{productId:'milk',quantity:2,unitPrice:68,available:true,updatedAt:new Date().toISOString()}],subtotal:136,discount:6,deliveryCharge:10,handlingCharge:2,estimatedTotal:142,expiresAt:new Date(Date.now()+60000).toISOString(),availabilityConfirmed:true,addressValidated:true};}
const confirmed:OrderStatus={id:'backend-order-reference',platform:'blinkit',locationId:location,items:[{productId:'milk',quantity:2}],status:'confirmed',paymentStatus:'paid',total:142};
test('single-store mode selects the chosen offer and excludes missing stock',()=>{const lines=cartLines(entries,demoProducts(location),location,'single','blinkit');assert.equal(lines[0]!.best?.platform,'blinkit');assert.equal(lines[0]!.total,136);assert.equal(lines[1]!.best,undefined);assert.equal(cartLines(entries,demoProducts(location),location,'single','instamart')[0]!.premium,8);});
test('best-price grouping makes separate store subtotals',()=>{const groups=groupCart(cartLines(entries,demoProducts(location),location));assert.equal(groups.length,2);assert.equal(groups[0]!.platform,'blinkit');assert.equal(groups[0]!.subtotal,136);assert.equal(groups[1]!.subtotal,225);});
test('cart persistence round-trips with strict quantity and duplicate validation',()=>{assert.deepEqual(parseCart(JSON.stringify(entries)),entries);assert.throws(()=>parseCart('[{"productId":"milk","locationId":"x","quantity":-1}]'));assert.throws(()=>parseCart(JSON.stringify([entries[0],entries[0]])));});
test('quantity changes and removal only affect the selected location',()=>{const cart=[...entries,{productId:'milk',locationId:'other',quantity:3}];assert.equal(setItemQuantity(cart,'milk',location,4)[0]!.quantity,4);assert.equal(setItemQuantity(cart,'milk',location,0).length,2);assert.deepEqual(setItemQuantity(cart,'milk',location,1.5),cart);assert.deepEqual(clearLocationCart(cart,location),[cart[2]]);});
test('address requires matching area, city and PIN and a valid recipient',()=>{assert.equal(addressError(delivery,locations[0]!), '');for(const patch of [{postcode:'560034'},{locationId:'elsewhere'},{city:'Mumbai'},{phone:'123'},{name:'A'},{line1:'x'}])assert.notEqual(addressError({...delivery,...patch},locations[0]!), '');});
test('checkout accepts a complete fresh retailer quote',()=>{assert.doesNotThrow(()=>assertValidQuote(validQuote(),checkoutRequest,'blinkit',true));});
test('checkout rejects wrong retailer, location, quantity and unavailable items',()=>{const q=validQuote();for(const changed of [{...q,platform:'instamart' as const},{...q,locationId:'other'},{...q,items:[{...q.items[0]!,quantity:1}]},{...q,items:[{...q.items[0]!,available:false}]}])assert.throws(()=>assertValidQuote(changed,checkoutRequest,'blinkit',true));});
test('checkout rejects stale, expired, unconfirmed and invalid totals',()=>{const q=validQuote();for(const changed of [{...q,expiresAt:'2020-01-01'},{...q,availabilityConfirmed:false},{...q,addressValidated:false},{...q,estimatedTotal:1},{...q,deliveryCharge:-1},{...q,items:[{...q.items[0]!,updatedAt:'2020-01-01'}]}])assert.throws(()=>assertValidQuote(changed,checkoutRequest,'blinkit',true));});
test('unknown fees remain unknown and cannot create a verified total',()=>{const q={...validQuote(),deliveryCharge:null,estimatedTotal:null};assert.doesNotThrow(()=>assertValidQuote(q,checkoutRequest,'blinkit'));assert.throws(()=>assertValidQuote({...q,estimatedTotal:132},checkoutRequest,'blinkit'));});
test('price fingerprints detect price and fee changes but ignore renewal IDs',()=>{const q=validQuote();assert.equal(quoteFingerprint(q),quoteFingerprint({...q,id:'renewed-reference'}));assert.notEqual(quoteFingerprint(q),quoteFingerprint({...q,handlingCharge:5}));});
test('cart removal requires backend-confirmed purchase and matching payment and items',()=>{assert.equal(confirmedOrder(confirmed,'blinkit',checkoutRequest),true);for(const patch of [{status:'pending' as const},{paymentStatus:'pending' as const},{platform:'instamart' as const},{items:[{productId:'milk',quantity:1}]}]){const order={...confirmed,...patch};assert.equal(confirmedOrder(order,'blinkit',checkoutRequest),false);}assert.deepEqual(removeConfirmedItems(entries,{...confirmed,status:'pending'},checkoutRequest),entries);assert.deepEqual(removeConfirmedItems(entries,confirmed,checkoutRequest),[entries[1]]);});
test('confirmed purchase preserves later-added units and other locations',()=>{const cart=[{...entries[0]!,quantity:5},{productId:'milk',locationId:'other',quantity:2}];const remaining=removeConfirmedItems(cart,confirmed,checkoutRequest);assert.equal(remaining[0]!.quantity,3);assert.equal(remaining[1]!.quantity,2);});
test('unsupported retailer blocks ordering without transport or fake confirmation',async()=>{let called=false;const service=createRetailerService('blinkit',async()=>{called=true;throw new Error('should not call');},false);assert.equal((await service.getCapabilities()).ordering,false);await assert.rejects(service.createRetailerOrder('quote','upi','request-key'),/not yet supported/);assert.equal(called,false);});
test('retailer transport propagates network failures and uses idempotency headers',async()=>{let headers:HeadersInit|undefined;const service=createRetailerService('instamart',async<T>(_path:string,options?:RequestInit):Promise<T>=>{headers=options?.headers;throw new Error('network offline');},true);await assert.rejects(service.createRetailerOrder('quote','card','unique-request-key'),/network offline/);assert.equal((headers as Record<string,string>)['Idempotency-Key'],'unique-request-key');});
test('future Zepto ordering remains disabled even with a connected backend',async()=>{const service=createRetailerService('zepto',async()=>{throw new Error('unexpected');},true);assert.equal((await service.getCapabilities()).ordering,false);await assert.rejects(service.validateRetailerCart(checkoutRequest),/not yet supported/);});

import {matchesProductSearch} from '../src/services/product-search';
test('product search matches brand and product words in any order, spaces, accents and packs',()=>{
 const products=withRetailerCatalogue(demoProducts(location));
 const find=(query:string)=>products.filter(p=>matchesProductSearch(p,query)).map(p=>p.id);
 assert.ok(find('  Amul   milk ').includes('milk'));
 assert.ok(find('milk amul').includes('milk'));
 assert.ok(find('nescafe coffee').includes('coffee'));
 assert.ok(find("lays chips").includes('chips'));
 assert.ok(find('aashirvaad atta 5 kg').includes('blinkit-333764'));
 assert.equal(find('   ').length,products.length);
 assert.deepEqual(find('nonexistent grocery'),[]);
});
