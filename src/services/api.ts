import { Platform } from 'react-native';
import * as SecureStore from 'expo-secure-store';
import { AdminData, CatalogueMetadata, ComparisonQuery, ComparisonResult, Location, Product, Session, User } from '../types';
import {ApiError,fetchJson,resolveApiUrl} from './api-client';
import {locationConsentHeaders,type Coordinates,type LocationConsent,type Place,type ShoppingLocation} from './shopping-location';
const configuredUrl = (process.env.EXPO_PUBLIC_API_URL || '').trim();
// Sample inventory is available only to the automated test runtime.
export const DEMO = process.env.NODE_ENV === 'test' && !configuredUrl;
let token: string | null = null;
let sessionRevision = 0;
let storageWrites = Promise.resolve();
let offerPosition:ShoppingLocation|null=null;
export function setOfferPosition(value:ShoppingLocation|null){offerPosition=value;}
const offerPaths=/^\/(?:admin\/retailer-operations\/coverage\?|shopping\/retailers\/offers\/[^/?]+\?|me\/assistant$|me\/shopping-intelligence\/(?:weekly\/(?:preview|assistant(?:\/gemini)?)\?|bulk\/[^/?]+\?|(?:basket|recommendations)\?))/;
const expiryListeners=new Set<()=>void>();
export function onSessionExpired(listener:()=>void){expiryListeners.add(listener);return()=>{expiryListeners.delete(listener);};}
function apiUrl() {
 const webOrigin = Platform.OS === 'web' && typeof location !== 'undefined' ? location.origin : undefined;
 return resolveApiUrl(configuredUrl, __DEV__, webOrigin);
}

function persistToken(value: string | null): Promise<void> {
 token = value;
 if (Platform.OS === 'web') return Promise.resolve();
 const save = () => value ? SecureStore.setItemAsync('cartiq.session', value) : SecureStore.deleteItemAsync('cartiq.session');
 const write = storageWrites.then(save);
 storageWrites = write.catch(() => {});
 return write;
}

export function setToken(value: string | null) {
 sessionRevision++;
 return persistToken(value);
}

let renewal: {revision: number; request: Promise<boolean>} | null = null;
function renew(revision: number): Promise<boolean> {
 if (renewal?.revision === revision) return renewal.request;
 const request = (async () => {
  try {
   const session = await fetchJson<Session>(apiUrl() + '/auth/refresh', {method: 'POST'});
   if (!session?.token) throw new Error('The service returned an invalid session.');
   if (revision !== sessionRevision) return false;
   await persistToken(session.token);
   return true;
  } catch (error) {
   if (error instanceof ApiError && error.status === 401) return false;
   throw error;
  }
 })();
 renewal = {revision, request};
 const clear = () => { if (renewal?.request === request) renewal = null; };
 void request.then(clear, clear);
 return request;
}

export async function restoreToken() {
 const revision = sessionRevision;
 if (Platform.OS === 'web') {
  await renew(revision);
 } else {
  const stored = await SecureStore.getItemAsync('cartiq.session');
  if (revision === sessionRevision) token = stored;
 }
 return revision === sessionRevision ? token : null;
}

export async function request<T>(path: string, options: RequestInit = {}, deadline?:number): Promise<T> {
 const url = apiUrl() + path;
 const auth = path.startsWith('/auth/');
 const sentToken = auth && path !== '/auth/logout' ? null : token;
 if(sentToken&&offerPosition&&offerPaths.test(path)){
  const headers=new Headers(options.headers);
  headers.set('X-CartIQ-Offer-Position',`${offerPosition.latitude},${offerPosition.longitude},${offerPosition.postcode??''}`);
  options={...options,headers};
 }
 const revision = sessionRevision;
 let unauthorized: ApiError;
 try {
  return await fetchJson<T>(url, options, sentToken, deadline);
 } catch (error) {
  if (path.endsWith('/basket/add') || /^\/shopping\/retailers\/private-search\/(?:compare-all\/)?confirm$/.test(path)|| options.signal?.aborted || !(error instanceof ApiError) || error.status !== 401) throw error;
  if (path === '/auth/logout' && sentToken && revision === sessionRevision) {
   return fetchJson<T>(url, options, null, deadline);
  }
  if (auth || !sentToken || revision !== sessionRevision) throw error;
  unauthorized = error;
 }
 if (token !== sentToken || await renew(revision)) {
  if (options.signal?.aborted) throw unauthorized;
  if (revision !== sessionRevision) throw new Error('Your account changed. Please try again.');
  try {
   return await fetchJson<T>(url, options, token, deadline);
  } catch (error) {
   if (path.endsWith('/basket/add') || /^\/shopping\/retailers\/private-search\/(?:compare-all\/)?confirm$/.test(path)|| options.signal?.aborted || !(error instanceof ApiError) || error.status !== 401) throw error;
  }
 }
 if (options.signal?.aborted) throw unauthorized;
 if (revision === sessionRevision) {
  await setToken(null).catch(() => {});
  expiryListeners.forEach(listener => listener());
 }
 throw new Error('Your session has expired. Please sign in again.');
}
export type RetailerLookup = 'blinkit'|'zepto'|'instamart';
export type RetailerProduct = {
 nativeId:string;title:string;brand:string|null;pack:string|null;currency:string;priceMinor:number|null;mrpMinor:number|null;
 available:boolean|null;inventory:number|null;storeId:string|null;providerFetchedAt:string|null;discountPercent:number|null;
 variant?:string|null; deliveryEstimate?:string|null;imageUrl:string|null;productUrl:string|null;normalizedPack:{count:number;each:{amount:number;unit:string}}|null;
 gtin?:string|null;barcode?:string|null;flavour?:string|null;formulation?:string|null;category?:string|null;
};
type ListingIdentity={gtin?:string|null;brand:string|null;name:string;pack:string|null;variant?:string|null;formulation?:string|null};
export type RetailerSearchResult = {retailer:RetailerLookup;creditsRemaining:number|null;products:RetailerProduct[];fetchedAt:string;freshUntil?:string;source?:string;retention?:'TRANSIENT'};
export type RetailerOutcome = 'SUCCESS'|'NO_RESULTS'|'UNAVAILABLE'|'PROVIDER_ERROR'|'TIMEOUT'|'NOT_SUPPORTED'|'NOT_ATTEMPTED';
export type RetailerComparison = {query:string;paused?:boolean;retailers:{retailer:RetailerLookup;status:'success'|'duplicate'|'error'|'not_attempted';outcome?:RetailerOutcome;result:RetailerSearchResult|null;message:string|null}[]};
type RetailerLocation = {lat:number;lon:number;pincode?:string};

export const api={
 products:(location:string)=>request<Product[]>(`/products?locationId=${encodeURIComponent(location)}`),
 productsPage:(location:string,q='',category='',page=0,signal?:AbortSignal,filters:{brand?:string;pack?:string}={})=>request<{items:Product[];page:number;pageSize:number;totalElements:number;totalPages:number;hasNext:boolean;hasPrevious:boolean}>(`/products/page?locationId=${encodeURIComponent(location)}&q=${encodeURIComponent(q)}&category=${encodeURIComponent(category)}&page=${page}&brand=${encodeURIComponent(filters.brand??'')}&pack=${encodeURIComponent(filters.pack??'')}`,{signal}),
 barcode:(location:string,gtin:string,signal?:AbortSignal)=>request<Product>(`/products/lookup/barcode?locationId=${encodeURIComponent(location)}&gtin=${encodeURIComponent(gtin)}`,{signal}),
 product:(location:string,id:string,signal?:AbortSignal)=>request<Product>(`/products/${encodeURIComponent(id)}?locationId=${encodeURIComponent(location)}`,{signal}),
 metadata:(location:string,id:string,signal?:AbortSignal)=>request<CatalogueMetadata>(`/products/${encodeURIComponent(id)}/metadata?locationId=${encodeURIComponent(location)}`,{signal}),
 variants:(location:string,id:string)=>request<Product[]>(`/products/${encodeURIComponent(id)}/variants?locationId=${encodeURIComponent(location)}`),
 compare:(id:string,q:ComparisonQuery)=>request<ComparisonResult>(`/products/${encodeURIComponent(id)}/comparison?locationId=${encodeURIComponent(q.locationId)}&latitude=${q.latitude}&longitude=${q.longitude}&radiusKm=${q.radiusKm}&quantity=${q.quantity}&fulfillment=${q.fulfillment}`),
 retailerCompare:(q:string,location:RetailerLocation,retailers:readonly RetailerLookup[],admin:boolean,signal?:AbortSignal)=>
  request<RetailerComparison>((admin?'/admin/providers/quickcommerce':'/shopping/retailers')+'/compare',
  {method:'POST',body:JSON.stringify({q,...location,retailers}),signal},45_000),
 retailerItem:(retailer:RetailerLookup,itemId:string,location:RetailerLocation,admin:boolean,signal?:AbortSignal)=>{
  const {lat,lon,pincode}=location;
  const pin=pincode?`&pincode=${encodeURIComponent(pincode)}`:'';
  const root=admin?'/admin/providers/quickcommerce':'/shopping/retailers';
  return request<RetailerSearchResult>(`${root}/item?retailer=${retailer}&itemId=${encodeURIComponent(itemId)}&lat=${lat}&lon=${lon}${pin}`,{signal},15_000);
 },
 reviewRetailerMatch:(left:ListingIdentity,right:ListingIdentity,signal?:AbortSignal)=>
  request<{decision:string;confidence:string;score:number;reasons:string[]}>('/admin/catalogue-matching/compare?method=hybrid',
  {method:'POST',body:JSON.stringify({left,right}),signal}),
 selectPosition:(point:ShoppingLocation,signal?:AbortSignal)=>request<{areaId:string|null}>('/me/shopping-position',{method:'PUT',signal,body:JSON.stringify({
  latitude:point.latitude,longitude:point.longitude,accuracy:point.accuracy,observedAt:point.source==='gps'?point.timestamp:Date.now(),source:point.source,postcode:point.postcode
 })}),
 clearPosition:()=>request<void>('/me/shopping-position',{method:'DELETE'}),
 geocodingOptions:(signal?:AbortSignal)=>request<{available:boolean;deviceGpsAllowed:boolean;googleDemo?:boolean}>('/location-lookup/options',{signal}),
 lookupPin:(postcode:string,consent:LocationConsent,signal?:AbortSignal)=>request<Place[]>('/location-lookup/pin',
  {method:'POST',headers:locationConsentHeaders(consent),body:JSON.stringify({postcode}),signal}),
 reverseLocation:(point:Coordinates,consent:LocationConsent,signal?:AbortSignal)=>request<Place>('/location-lookup/reverse',
  {method:'POST',headers:locationConsentHeaders(consent),body:JSON.stringify({latitude:point.latitude,longitude:point.longitude}),signal}),
 verifyPinLocation:(candidate:Place,consent:LocationConsent,signal?:AbortSignal)=>request<Place>('/location-lookup/pin/verify',
  {method:'POST',headers:locationConsentHeaders(consent),body:JSON.stringify(candidate),signal}),
 locations:()=>request<Location[]>('/locations'),
 login:(email:string,password:string)=>request<Session>('/auth/login',{method:'POST',body:JSON.stringify({email,password})}),
 register:(name:string,email:string,password:string,phone?:string)=>request<Session>('/auth/register',{method:'POST',body:JSON.stringify({name,email,password,phone})}),
 demo:()=>request<Session>('/auth/demo',{method:'POST'}),
 me:(signal?:AbortSignal)=>request<User>('/me',{signal}),
 updateProfile:(name:string)=>request<User>('/me',{method:'PATCH',body:JSON.stringify({name})}),
 changePassword:(currentPassword:string,newPassword:string)=>request<void>('/me/password',{method:'POST',body:JSON.stringify({currentPassword,newPassword})}),
 changeEmail:(email:string)=>request<void>('/me/email-change',{method:'POST',body:JSON.stringify({email})}),
 verifyEmail:(code:string)=>request<void>('/me/email-change/confirm',{method:'POST',body:JSON.stringify({code})}),
 wishlist:()=>request<string[]>('/me/wishlist'),
 save:(id:string,saved:boolean)=>request<void>(`/me/wishlist/${encodeURIComponent(id)}`,{method:saved?'PUT':'DELETE'}),
 logout:(deviceToken?:string)=>request<void>('/auth/logout',{method:'POST',body:deviceToken ? JSON.stringify({token:deviceToken}) : undefined}),
 admin:()=>request<AdminData>('/admin/overview'),
 saveProduct:(product:Product)=>request<Product>(`/admin/products/${encodeURIComponent(product.id)}`,{method:'PUT',body:JSON.stringify(product)}),
 deleteProduct:(id:string)=>request<void>(`/admin/products/${encodeURIComponent(id)}`,{method:'DELETE'}),
 users:()=>request<User[]>('/admin/users'),
 setPlatform:(id:string,enabled:boolean)=>request<void>(`/admin/platforms/${id}`,{method:'PATCH',body:JSON.stringify({enabled})})
};

// Bind only for a scoped shopping operation. A manual delivery point has no sensor timestamp.
export async function bindShoppingPosition(point:ShoppingLocation,area:string,signal?:AbortSignal){
 const revision=sessionRevision;
 if(!token||point!==offerPosition)throw new Error('Your shopping location changed. Open this view again.');
 const selected=await api.selectPosition(point,signal);
 if(signal?.aborted||revision!==sessionRevision||point!==offerPosition)
  throw new Error('Your shopping location changed. Open this view again.');
 return selected.areaId??area;
}
