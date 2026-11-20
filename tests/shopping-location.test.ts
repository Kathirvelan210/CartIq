import test from 'node:test';
import assert from 'node:assert/strict';
import {foregroundGrant,browserPosition,choosePinPlace,gpsChoice,isIndianPin,nativePosition,readSavedShoppingLocations,readShoppingLocation,sameShoppingLocation,shoppingKey,type Place} from '../src/services/shopping-location';
import {googlePinCandidates,locationConsentHeaders,readLocationSettings,selectGooglePinPlace,serializeLocationSettings,verifyDeliveryLocation} from '../src/services/shopping-location';

const point={latitude:12.9,longitude:77.66,accuracy:15};
const place:Place={...point,locality:'East',city:'Fixture City',district:'Fixture District',state:'Fixture State',
 postcode:'560034',approximate:true};

test('browser permission and fresh position use the actual device coordinates',async()=>{
 const actual=await browserPosition({getCurrentPosition(ok,_fail,options){
  assert.equal(options.maximumAge,0);assert.equal(options.enableHighAccuracy,true);
  ok({coords:point});
 }},true,async()=> 'granted');
 assert.deepEqual(actual,point);
});

test('browser geolocation distinguishes denial, timeout, unavailable and insecure origin',async()=>{
 await assert.rejects(browserPosition(undefined,false),/HTTPS or localhost/);
 const never={getCurrentPosition(){assert.fail('denied permission reached geolocation');}};
 await assert.rejects(browserPosition(never,true,async()=> 'denied'),/permission was denied/);
 for(const [code,error] of [[1,/permission was denied/],[2,/position is unavailable/],[3,/timed out/]] as const){
  await assert.rejects(browserPosition({getCurrentPosition(_ok,fail){fail({code});}},true),error);
 }
});

test('native uses granted GPS and handles denial, disabled services and timeout',async()=>{
 const device={permission:async()=>({granted:true,canAskAgain:true}),services:async()=>true,position:async()=>point};
 assert.deepEqual(await nativePosition(device),point);
 await assert.rejects(nativePosition({...device,permission:async()=>({granted:false,canAskAgain:true})}),/permission was denied/);
 await assert.rejects(nativePosition({...device,permission:async()=>({granted:false,canAskAgain:false})}),/Open app settings/);
 await assert.rejects(nativePosition({...device,services:async()=>false}),/position is unavailable/);
 await assert.rejects(nativePosition({...device,position:()=>new Promise(()=>{})},1),/timed out/);
});

test('an existing coarse grant or blocked permission never prompts for an upgrade',async()=>{
 for(const granted of [true,false]){
  for(const canAskAgain of [true,false]){
   const current={granted,canAskAgain,android:{accuracy:'coarse' as const}};
   let prompts=0;
   await foregroundGrant(async()=>current,async()=>{prompts++;return current;});
   assert.equal(prompts,!granted&&canAskAgain?1:0);
  }
 }
});

test('Android precision preserves sensor accuracy and observation time',async()=>{
 const fix={...point,accuracy:2300,timestamp:Date.now()};
 for(const accuracy of ['coarse','fine'] as const){
  const result=await nativePosition({permission:async()=>({granted:true,canAskAgain:true,android:{accuracy}}),
   services:async()=>true,position:async()=>fix});
  assert.deepEqual(result,{...fix,androidPrecision:accuracy});
 }
});

test('restart preserves the saved place and original fix time even after it expires',async()=>{
 const {deviceSelection}=await import('../src/services/shopping-location');
 const fix=deviceSelection({...point,accuracy:2000,androidPrecision:'coarse',timestamp:Date.now()});
 const settings=serializeLocationSettings({selected:fix,places:[fix],consent:'declined'});
 assert.deepEqual(readLocationSettings(settings).selected,fix);
 const old={...fix,timestamp:Date.now()-901000};
 assert.deepEqual(readLocationSettings(serializeLocationSettings({selected:old,places:[old],consent:'declined'})),
  {selected:old,places:[old],consent:'declined'});
});

test('a browser timeout allows one device fallback and keeps its reported accuracy and time',async()=>{
 const options: boolean[]=[];
 const timestamp=Date.now()-15_000;
 const position=await browserPosition({getCurrentPosition(ok,fail,settings){
  options.push(settings.enableHighAccuracy);
  if(settings.enableHighAccuracy){fail({code:3});return;}
  assert.equal(settings.maximumAge,60_000);ok({coords:point,timestamp});
 }},true);
 assert.deepEqual(options,[true,false]);assert.deepEqual(position,{...point,timestamp});
});

test('manual delivery coordinates survive reload without being labelled a sensor fix',async()=>{
 const {manualSelection}=await import('../src/services/shopping-location');
 const place=manualSelection(' -33.5 ','0','Work');
 assert.deepEqual(readLocationSettings(serializeLocationSettings({selected:place,places:[place],consent:null})).selected,place);
 assert.equal(place.source,'manual');assert.equal(place.verified,false);
 for(const [lat,lon] of [['','0'],['1',' '],['Infinity','0'],['91','0'],['0','-181'],['0xAB','1']] as const)
  assert.throws(()=>manualSelection(lat,lon,'Work'));
});

test('GPS confirmation keeps device coordinates even if the resolved address has another centroid',()=>{
 const chosen=gpsChoice(point,{...place,latitude:13,longitude:77});
 assert.equal(chosen.latitude,point.latitude);assert.equal(chosen.longitude,point.longitude);
 assert.match(chosen.label,/East, Fixture City/);assert.equal(chosen.postcode,'560034');
 assert.equal(gpsChoice(point,undefined,'Home').label,'Home');
});

test('PIN choice validates Indian format, preserves approximation and rejects missing coordinates',()=>{
 for(const pin of ['12345','012345','px0345','1234567'])assert.equal(isIndianPin(pin),false);
 assert.equal(isIndianPin(' 560034 '),true);
 assert.equal(choosePinPlace({...place,latitude:null}),null);
 const east=choosePinPlace(place)!;
 const west=choosePinPlace({...place,locality:'West',longitude:77.61})!;
 assert.equal(east.source,'pin');assert.match(east.label,/East/);assert.match(west.label,/West/);
 assert.deepEqual(readShoppingLocation(JSON.stringify(west)),west);
 assert.notEqual(shoppingKey('milk',east,'user-1'),shoppingKey('milk',west,'user-1'));
 assert.notEqual(shoppingKey('milk',west,'user-1'),shoppingKey('rice',west,'user-1'));
 assert.notEqual(shoppingKey('milk',west,'user-1'),shoppingKey('milk',west,'user-2'));
 assert.equal(shoppingKey('   TOOR   dal ',west,'user-1'),shoppingKey('toor dal',west,'user-1'));
 assert.equal(readShoppingLocation(JSON.stringify({...east,postcode:null})),null);
});

test('PIN confirmation keeps the selected locality even if choices share a centroid',()=>{
 const east=choosePinPlace(place)!;
 const west=choosePinPlace({...place,locality:'West'})!;
 assert.equal(west.latitude,east.latitude);
 assert.equal(west.longitude,east.longitude);
 assert.match(west.label,/West/);
 assert.notEqual(shoppingKey('milk',east,'user-1'),shoppingKey('milk',west,'user-1'));
 assert.equal(choosePinPlace({...place,latitude:91}),null);
 assert.equal(choosePinPlace({...place,longitude:Number.NaN}),null);
 assert.equal(choosePinPlace({...place,postcode:'012345'}),null);
});

test('saved localities remain distinct at the same centroid and preserve a renamed selection',()=>{
 const east=choosePinPlace(place)!;
 const west=choosePinPlace({...place,locality:'West'})!;
 const renamed={...west,label:'My west address'};
 assert.equal(sameShoppingLocation(east,west),false);
 assert.equal(sameShoppingLocation(west,renamed),true);
 assert.deepEqual(readSavedShoppingLocations(JSON.stringify({places:[east,renamed],selected:renamed})),{places:[east,renamed],selected:renamed});
 assert.equal(readSavedShoppingLocations(JSON.stringify({places:[east],selected:west})).selected,null);
});

test('Google current browser position can be verified at PIN area precision',()=>{
 const postal={...place,countryCode:'in',locality:null};
 const actual={...point,latitude:12.91};
 const reverse={...postal,...actual,locality:'East',approximate:false};
 const gps=verifyDeliveryLocation('gps',actual,reverse,[postal],'560034',undefined,'google-demo');
 assert.equal(gps.latitude,actual.latitude);
 assert.equal(gps.postcode,'560034');
 for(const changed of [{postcode:'560035'},{city:'Different city'},{state:'Different state'},{latitude:12.95}]){
  assert.throws(()=>verifyDeliveryLocation('gps',actual,{...reverse,...changed},[postal],'560034',undefined,'google-demo'));
 }
 assert.throws(()=>verifyDeliveryLocation('gps',actual,reverse,[{...postal,locality:'West'}],'560034',undefined,'google-demo'));
});

test('multiple genuine 641008 localities in the same state remain selectable and approximate',()=>{
 const east={...place,countryCode:'in',postcode:'641008',state:'Tamil Nadu'};
 const west={...east,locality:'Fixture West',latitude:10.95};
 const otherPin={...west,postcode:'641009'};
 const places=[east,west,otherPin];
 assert.deepEqual(googlePinCandidates(places,'641008'),[east,west]);
 const selected=selectGooglePinPlace(places,west,' 641008 ');
 assert.equal(selected?.latitude,west.latitude);
 assert.equal(selected?.source,'pin');
 assert.equal(selected?.provider,'google-demo');
 assert.match(selected?.label??'',/Fixture West/);
 assert.equal(selectGooglePinPlace(places,otherPin,'641008'),null);
});

test('Google PIN selection blocks absent locality, missing coordinates, invalid PIN and conflicting state',()=>{
 const named={...place,countryCode:'in',postcode:'641008',state:'Tamil Nadu'};
 const unnamed={...named,locality:null,city:null};
 assert.equal(selectGooglePinPlace([unnamed],unnamed,'641008'),null);
 assert.equal(selectGooglePinPlace([named],named,'000000'),null);
 const foreign={...named,countryCode:'lk'};
 const missingPoint={...named,latitude:null};
 const wrongState={...named,locality:'Other area',state:'Kerala'};
 assert.deepEqual(googlePinCandidates([missingPoint,foreign],'641008'),[]);
 assert.deepEqual(googlePinCandidates([named,wrongState],'641008'),[]);
 assert.equal(selectGooglePinPlace([named],{...named,locality:'Unverified manual entry'},'641008'),null);
 const postalTown={...named,locality:null};
 assert.ok(selectGooglePinPlace([postalTown],postalTown,'641008'));
});

test('Google demo location stays out of storage while other saved places and consent round-trip',()=>{
 const postal={...place,countryCode:'in'};
 const reverse={...postal,approximate:false};
 const ordinary=verifyDeliveryLocation('pin',point,reverse,[postal],'560034',postal);
 const demo=selectGooglePinPlace([postal],postal,'560034')!;
 const data=serializeLocationSettings({selected:demo,places:[demo,ordinary],consent:'granted'});
 assert.doesNotMatch(data,/google-demo/);
 assert.deepEqual(readLocationSettings(data),{selected:null,places:[ordinary],consent:'granted'});
 assert.throws(()=>locationConsentHeaders('declined'),/Allow delivery geocoding/);
 assert.equal(locationConsentHeaders('granted')['X-CartIQ-Geocoding-Consent'],'delivery-geocoding-v1');
});

test('GPS selection preserves the sensor timestamp and accuracy without postal evidence',async()=>{
 const {deviceSelection}=await import('../src/services/shopping-location');
 const timestamp=Date.now()-1000;
 const gps=deviceSelection(await browserPosition({getCurrentPosition(ok){ok({coords:point,timestamp});}},true));
 assert.equal(gps.timestamp,timestamp);assert.equal(gps.accuracy,15);assert.equal(gps.postcode,null);assert.equal(gps.verified,false);
 assert.equal(readLocationSettings(serializeLocationSettings({consent:null,selected:gps,places:[gps]})).selected?.timestamp,timestamp);
 assert.throws(()=>deviceSelection({...point,timestamp:timestamp-1000000}));
 assert.throws(()=>deviceSelection(point));
});
