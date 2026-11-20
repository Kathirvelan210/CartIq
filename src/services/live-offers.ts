import {request,type RetailerLookup,type RetailerOutcome} from './api';
import type {QuoteEvidence} from './weekly-shopping';

export type OfferScope={locationId:string;lat:number;lon:number;pincode:string|null;retailers:RetailerLookup[];authorizationId?:string};
type Ticket={id:string;retailer:RetailerLookup;operation:'SEARCH'|'ITEM';expiresAt:string;nativeId:string};
type Receipt={authorizationId:string;result:string;billing:string;providerRequestId:string|null;status:number|null;confirmedDebit:number|null;balance:number|null};
export type LiveOffer={retailer:RetailerLookup;nativeId:string;pack:string|null;priceMinor:number|null;mrpMinor:number|null;
 title?:string;brand?:string|null;gtin?:string|null;flavour?:string|null;formulation?:string|null;
 unitPriceMinor:number|null;unit:string|null;available:boolean|null;stockPacks:number|null;variantId:string|null;productId:string|null;
 locationId:string;observedAt:string|null;fetchedAt:string;freshUntil:string;requestId:string|null;source:string;match:'VERIFIED'|'UNRESOLVED'|'DISTINCT';score:number;confidence:string};
export type OfferView={configured:boolean;quotes:QuoteEvidence[];paused:boolean;controlledPilot?:boolean;authorizations?:Ticket[];receipt?:Receipt|null;retailers:{
 retailer:RetailerLookup;status:string;outcome:RetailerOutcome;message:string|null;providerRequestId:string|null;upstreamStatus:number|null;fetchedAt:string|null;offers:LiveOffer[];
}[]};
const path=(id:string)=>'/shopping/retailers/offers/'+encodeURIComponent(id);
const post=(body:unknown,signal?:AbortSignal)=>({method:'POST',body:JSON.stringify(body),signal});
export const liveOffers={
 read:(id:string,area:string,signal?:AbortSignal)=>request<OfferView>(path(id)+'?locationId='+encodeURIComponent(area),{signal}),
 refresh:(id:string,scope:OfferScope,signal?:AbortSignal)=>request<OfferView>(path(id)+'/refresh',post(scope,signal),40000),
 item:(id:string,scope:OfferScope,nativeId:string,signal?:AbortSignal)=>request<OfferView>(path(id)+'/item',post({scope,nativeId},signal),15000),
};
