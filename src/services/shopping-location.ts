type AndroidPrecision = 'coarse'|'fine';
export type Coordinates = {latitude:number;longitude:number;accuracy?:number|null;timestamp?:number;androidPrecision?:AndroidPrecision};
export type Place = {
 locality:string|null; city:string|null; district:string|null; state:string|null; postcode:string|null;
 latitude:number|null; longitude:number|null; approximate:boolean; countryCode?:string;
};
export type ShoppingLocation = Coordinates & {label:string; postcode:string|null; source:'gps'|'pin'|'manual'; localityKey?:string};
export type SavedShoppingLocations = {selected:ShoppingLocation|null; places:ShoppingLocation[]};
export type VerifiedLocation = ShoppingLocation & {postcode:string;verified:true;provider?:'google-demo'};
export type DeviceSelection=Coordinates & {label:string;localityKey?:string} & {source:'gps';postcode:null;verified:false;timestamp:number;provider?:undefined};
type ManualSelection=Coordinates & {label:string;localityKey?:string;source:'manual';postcode:string|null;verified:false;provider?:undefined};
export type SelectedLocation=VerifiedLocation|DeviceSelection|ManualSelection;
export function manualSelection(latitude:string,longitude:string,label:string,pin=''):ManualSelection {
 const decimal=/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)$/;
 const point={latitude:Number(latitude.trim()),longitude:Number(longitude.trim())};
 if(!decimal.test(latitude.trim())||!decimal.test(longitude.trim())||!hasCoordinates(point))
  throw new Error('Enter a latitude between −90 and 90 and a longitude between −180 and 180.');
 if(pin.trim()&&!isIndianPin(pin))throw new Error('Enter a six-digit Indian PIN.');
 return {...point,label:label.trim().slice(0,100)||'Manual location',source:'manual',postcode:pin.trim()||null,verified:false};
}
export function deviceSelection(point:Coordinates):DeviceSelection {
 const time=point.timestamp;
 if(!hasCoordinates(point)||typeof time!=='number'||!Number.isFinite(time)||time<Date.now()-900_000||time>Date.now()+5000||
  point.accuracy==null||!Number.isFinite(point.accuracy)||point.accuracy<0)
  throw new Error('The device did not return a recent position with an accuracy estimate. Request your location again.');
 return {...point,timestamp:time,source:'gps',postcode:null,verified:false,label:'Current device location'};
}
export type LocationConsent = 'granted'|'declined'|null;
export type LocationSettings = {selected:SelectedLocation|null;places:SelectedLocation[];consent:LocationConsent;catalogueAreaId?:string};
export const locationConsentVersion='delivery-geocoding-v1';
export const emptyLocationSettings:LocationSettings={selected:null,places:[],consent:null};
export function locationSettingsKey(owner:string){
 return `cartiq.shopping-location.v3.${owner}`;
}

export function readLocationSettings(raw:string|null):LocationSettings {
 try{
  const value=JSON.parse(raw??'null');
  const verified=(entry:unknown):entry is SelectedLocation=>{
   const place=readShoppingLocation(JSON.stringify(entry));
   if(place?.source==='manual')return (entry as ManualSelection).verified===false;
   if(place?.source==='gps'&&place.postcode===null){
    const fix=entry as DeviceSelection;
    return fix.verified===false&&fix.accuracy!=null&&Number.isFinite(fix.accuracy)&&fix.accuracy>=0&&
     Number.isFinite(fix.timestamp)&&fix.timestamp>0&&fix.timestamp<=Date.now()+5000;
   }
   return !!place?.postcode&&(entry as Partial<VerifiedLocation>).verified===true&&
    (entry as Partial<VerifiedLocation>).provider!=='google-demo';
  };
  const places:SelectedLocation[]=Array.isArray(value?.places)?value.places.filter(verified).slice(0,8):[];
  const selected=verified(value?.selected)?places.find(place=>sameShoppingLocation(place,value.selected))??null:null;
  const consent=value?.consentVersion===locationConsentVersion&&(value.consent==='granted'||value.consent==='declined')?value.consent:null;
  return {places,selected,consent,...(typeof value?.catalogueAreaId==='string'?{catalogueAreaId:value.catalogueAreaId}:{})};
 }catch{return emptyLocationSettings;}
}

export function serializeLocationSettings(settings:LocationSettings):string {
 return JSON.stringify({...settings,consentVersion:locationConsentVersion,
  selected:settings.selected?.provider==='google-demo'?null:settings.selected,
  places:settings.places.filter(place=>place.provider!=='google-demo')});
}

export function shoppingLocationLabel(place:SelectedLocation){
 const label=place.postcode&&!place.label.includes(place.postcode)?place.label+' · '+place.postcode:place.label;
 return place.provider==='google-demo'?label+' · Google Maps':label;
}

export function locationConsentHeaders(consent:LocationConsent){
 if(consent!=='granted')throw new Error('Allow delivery geocoding before looking up a location.');
 return {'X-CartIQ-Geocoding-Consent':locationConsentVersion};
}

export function sameShoppingLocation(left:ShoppingLocation,right:ShoppingLocation):boolean {
 return left.latitude===right.latitude&&left.longitude===right.longitude&&left.postcode===right.postcode
  &&left.source===right.source&&left.localityKey===right.localityKey;
}
export function readSavedShoppingLocations(raw:string|null):SavedShoppingLocations {
 try{
  const value=JSON.parse(raw??'null');
  const places:ShoppingLocation[]=Array.isArray(value?.places)?value.places.slice(0,8)
   .map((entry:unknown)=>readShoppingLocation(JSON.stringify(entry))).filter(Boolean):[];
  const selected=readShoppingLocation(JSON.stringify(value?.selected??null));
  return {places,selected:selected?places.find(place=>sameShoppingLocation(place,selected))??null:null};
 }catch{return {places:[],selected:null};}
}

export function isIndianPin(value:string):boolean {
 return /^[1-9][0-9]{5}$/.test(value.trim());
}

export function hasCoordinates<T extends {latitude?:unknown;longitude?:unknown}>(value:T):value is T & Coordinates {
 const {latitude,longitude}=value;
 return typeof latitude==='number'&&typeof longitude==='number'&&Number.isFinite(latitude)&&Number.isFinite(longitude)
  &&Math.abs(latitude)<=90&&Math.abs(longitude)<=180;
}

export function placeLabel(place:Place):string {
 return [...new Set([place.locality,place.city,place.district,place.state,place.postcode].filter(Boolean))].join(', ');
}

export function choosePinPlace(place:Place):ShoppingLocation|null {
 if(!hasCoordinates(place)||!place.postcode||!isIndianPin(place.postcode))return null;
 const label=placeLabel(place);
 return {latitude:place.latitude,longitude:place.longitude,label,postcode:place.postcode,source:'pin',localityKey:label};
}

export function gpsChoice(point:Coordinates,place?:Place,manualLabel?:string):ShoppingLocation {
 return {...point,source:'gps', label:manualLabel?.trim()||(place&&placeLabel(place))||'Current GPS location',
  postcode:place?.postcode&&isIndianPin(place.postcode)?place.postcode:null};
}

function areaText(value:string|null){
 return value?.normalize('NFKC').trim().replace(/\s+/g,' ').toLowerCase()??'';
}

export function googlePinCandidates(places:Place[],input:string){
 const pin=input.trim();
 if(!isIndianPin(pin))return [];
 const matches=places.filter(place=>place.countryCode==='in'&&place.postcode===pin&&place.approximate===true);
 const states=new Set(matches.map(place=>areaText(place.state)).filter(Boolean));
 if(states.size!==1)return [];
 return matches.filter(place=>!!areaText(place.state)&&!!(areaText(place.locality)||areaText(place.city))&&hasCoordinates(place));
}

export function selectGooglePinPlace(places:Place[],selected:Place,pin:string):VerifiedLocation|null {
 if(!googlePinCandidates(places,pin).includes(selected))return null;
 const place=choosePinPlace(selected);
 return place?{...place,postcode:pin.trim(),verified:true,provider:'google-demo'}:null;
}

function sameDeliveryArea(candidate:Place,address:Place,allowPostcodeArea:boolean){
 const locality=areaText(address.locality),city=areaText(address.city),state=areaText(address.state);
 const candidateLocality=areaText(candidate.locality);
 const district=areaText(address.district),candidateDistrict=areaText(candidate.district);
 const postcodeOnly=allowPostcodeArea&&(!candidateLocality||candidateLocality===city);
 return candidate.countryCode==='in'&&candidate.approximate===true&&candidate.postcode===address.postcode&&
  !!city&&!!state&&(postcodeOnly||!!locality&&locality!==city&&candidateLocality===locality)&&areaText(candidate.city)===city&&
  areaText(candidate.state)===state&&(!district||!candidateDistrict||district===candidateDistrict);
}

export function verifyDeliveryLocation(source:'gps'|'pin',point:Coordinates,address:Place,candidates:Place[],expectedPin:string,
 selected?:Place,provider?:'google-demo'):VerifiedLocation {
 if(!hasCoordinates(point)||address.countryCode!=='in'||address.approximate!==false||!isIndianPin(expectedPin)||
  address.postcode!==expectedPin||address.latitude!==point.latitude||address.longitude!==point.longitude){
  throw new Error('The location and PIN could not be matched. Check the PIN or choose another locality.');
 }
 const matching=candidates.filter(candidate=>sameDeliveryArea(candidate,address,provider==='google-demo'));
 const exact=matching.filter(candidate=>areaText(candidate.locality)===areaText(address.locality));
 const matches=source==='gps'&&exact.length?exact:matching;
 if(matches.length!==1)throw new Error('The delivery locality is missing, ambiguous, or does not match this PIN.');
 const match=matches[0]!;
 const chosen=source==='gps'?gpsChoice(point,address):
  match===selected&&match.latitude===point.latitude&&match.longitude===point.longitude?choosePinPlace(match):null;
 if(!chosen)throw new Error('The selected PIN locality could not be verified.');
 return {...chosen,postcode:expectedPin,verified:true,localityKey:placeLabel(match),...(provider?{provider}:{})};
}

// A second, distinct lookup starts after the adapter's cooldown. A provider or rate-limit error stops the flow.
export function waitForGeocoder(signal:AbortSignal):Promise<void>{
 return new Promise((resolve,reject)=>{
  if(signal.aborted){reject(new Error('Location lookup cancelled.'));return;}
  const abort=()=>{clearTimeout(timer);reject(new Error('Location lookup cancelled.'));};
  const timer=setTimeout(()=>{signal.removeEventListener('abort',abort);resolve();},1100);
  signal.addEventListener('abort',abort,{once:true});
 });
}

export function readShoppingLocation(raw:string|null):ShoppingLocation|null {
 if(!raw)return null;
 try{
  const value=JSON.parse(raw) as Partial<ShoppingLocation>|null;
  if(!value||typeof value.label!=='string'||!value.label.trim()||!hasCoordinates(value)||
   (value.source!=='gps'&&value.source!=='pin'&&value.source!=='manual')||
   (value.postcode!==null&&(typeof value.postcode!=='string'||!isIndianPin(value.postcode)))||
   (value.source==='pin'&&!value.postcode))return null;
  return {label:value.label,latitude:value.latitude,longitude:value.longitude,source:value.source,postcode:value.postcode,
   ...(typeof value.localityKey==='string'&&value.localityKey.length<=500?{localityKey:value.localityKey}:{}),
   ...(typeof value.accuracy==='number'&&Number.isFinite(value.accuracy)&&value.accuracy>=0?{accuracy:value.accuracy}:{})};
 }catch{return null;}
}

export function shoppingKey(query:string,location:ShoppingLocation,account:string):string {
 const term=query.normalize('NFKC').trim().toLowerCase().replace(/\s+/g,' ');
 return JSON.stringify([account,term,location.latitude,location.longitude,location.postcode,location.source,location.label]);
}

type BrowserLocation = {
 getCurrentPosition(ok:(position:{coords:Coordinates;timestamp?:number})=>void,fail:(error:{code:number})=>void,
  options:{timeout:number;maximumAge:number;enableHighAccuracy:boolean}):void;
};
const denied='Location permission was denied. Allow location in your settings or enter coordinates manually.';
const unavailable='Your position is unavailable. Check device location services or enter coordinates manually.';
const timeout='Your device timed out before returning a position. Check location services, try again, or enter coordinates manually.';

export async function browserPosition(geo:BrowserLocation|undefined,secure:boolean,
 permission?:()=>Promise<string>,signal?:AbortSignal):Promise<Coordinates> {
 if(!secure)throw new Error('Browser location requires HTTPS or localhost. You can enter coordinates manually.');
 if(!geo)throw new Error(unavailable);
 if(await permission?.().catch(()=>undefined)==='denied')throw new Error(denied);
 if(signal?.aborted)throw new Error('Location lookup cancelled.');
 const acquire=(precise:boolean)=>new Promise<Coordinates>((resolve,reject)=>geo.getCurrentPosition(
  ({coords,timestamp})=>hasCoordinates(coords)?resolve({latitude:coords.latitude,longitude:coords.longitude,accuracy:coords.accuracy,...(timestamp!==undefined?{timestamp}:{})}):reject(new Error(unavailable)),
  error=>reject(Object.assign(new Error(error.code===1?denied:error.code===3?timeout:unavailable),{code:error.code})),
  {timeout:precise?15_000:12_000,maximumAge:precise?0:60_000,enableHighAccuracy:precise}));
 try{return await acquire(true);}
 catch(error){
  if(signal?.aborted||![2,3].includes((error as {code:number}).code))throw error;
  return acquire(false);
 }
}

type ForegroundGrant={granted:boolean;canAskAgain:boolean;android?:{accuracy:'fine'|'coarse'|'none'}};
export async function foregroundGrant(read:()=>Promise<ForegroundGrant>,request:()=>Promise<ForegroundGrant>){
 const current=await read();
 return current.granted||!current.canAskAgain?current:request();
}
export class LocationPermissionError extends Error {
 constructor(readonly canAskAgain:boolean){super(canAskAgain?denied:'Location permission is blocked. Open app settings to allow location, or enter coordinates manually.');}
}
export async function nativePosition(device:{permission:()=>Promise<ForegroundGrant>;
 services:()=>Promise<boolean>;position:()=>Promise<Coordinates>},deadline=12_000):Promise<Coordinates> {
 const permission=await device.permission();
 if(!permission.granted||permission.android?.accuracy==='none')throw new LocationPermissionError(permission.canAskAgain);
 const precision=permission.android?.accuracy;
 if(!await device.services())throw new Error(unavailable);
 let timer:ReturnType<typeof setTimeout>|undefined;
 const expires=new Promise<never>((_,reject)=>{timer=setTimeout(()=>reject(new Error(timeout)),deadline);});
 try{
  const point=await Promise.race([device.position().catch(()=>{throw new Error(unavailable);}),expires]);
  if(!hasCoordinates(point))throw new Error(unavailable);
  return precision?{...point,androidPrecision:precision}:point;
 }finally{clearTimeout(timer);}
}
