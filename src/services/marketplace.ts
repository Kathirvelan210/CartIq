import {bindShoppingPosition,request,type RetailerLookup,type RetailerProduct} from './api';
import type {ShoppingLocation} from './shopping-location';

export type MarketplaceTicket={id:string;variant:string;retailer:RetailerLookup;query:string;expiresAt:string};
export type MarketplaceListing={listing:RetailerProduct;evidence:{match:string;variantId:string|null;observedAt:string|null;freshUntil:string;unitPriceMinor:number|null;unit:string|null}};
export type MarketplaceRow={retailer:RetailerLookup;requestId:string;fetchedAt:string;freshUntil:string;listings:MarketplaceListing[]};
type View={configured:boolean;locationId?:string|null;authorizations:MarketplaceTicket[];recorded:MarketplaceRow[]};
export type MarketplaceResult={marketplace:View;retailer:RetailerLookup;outcome:string;billing:string;message:string|null;requestId:string|null;debit:number|null};
const root='/shopping/retailers/marketplace';
function position(locationId:string,place:ShoppingLocation){
 return {locationId,lat:place.latitude,lon:place.longitude,pincode:place.postcode??undefined};
}
export const marketplace={
 read(locationId:string,place:ShoppingLocation,query:string,signal?:AbortSignal){
  const {lat,lon,pincode}=position(locationId,place);
  const params=new URLSearchParams({locationId,lat:String(lat),lon:String(lon),query});
  if(pincode)params.set('pincode',pincode);
  return request<View>(root+'?'+params,{signal});
 },
 async search(locationId:string,place:ShoppingLocation,query:string,ticket:MarketplaceTicket,signal:AbortSignal){
  locationId=await bindShoppingPosition(place,locationId,signal);
  return request<MarketplaceResult>(root+'/search',{method:'POST',signal,body:JSON.stringify({location:position(locationId,place),
   query,variant:ticket.variant,retailer:ticket.retailer,authorizationId:ticket.id})},45_000);
 },
 async cartQuotes(locationId:string,place:ShoppingLocation,signal:AbortSignal){
  locationId=await bindShoppingPosition(place,locationId,signal);
  return request<{itemId:string;priceMinor:number;fetchedAt:string;freshUntil:string}[]>(root+'/cart-quotes',
   {method:'POST',body:JSON.stringify(position(locationId,place)),signal});
 },
 async add(locationId:string,place:ShoppingLocation,query:string,retailer:RetailerLookup,variant:string,nativeId:string,replace=false){
  locationId=await bindShoppingPosition(place,locationId);
  return request(root+'/cart',{method:'POST',body:JSON.stringify({location:position(locationId,place),query,variant,retailer,nativeId,replace})});
 },
};
