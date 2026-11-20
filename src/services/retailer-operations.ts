import {request} from './api';

export type Area={id:string;name:string};
export type Counts={totalProducts:number;approvedProducts:number;productsWithoutMapping:number;unresolvedProducts:number;staleProducts:number;
 missingPriceProducts:number;uncertainStockProducts:number;missingFeeProducts:number;packConflictProducts:number};
export type CoverageRow={productId:string;variantId:string|null;categoryId:string;name:string;brand:string;pack:string|null;retailer:string;nativeId:string|null;
 approved:boolean;status:string;missingFees:boolean;missingPrice:boolean;uncertainStock:boolean;packConflict:boolean;priceMinor:number|null;
 stock:number|null;observedAt:string|null;observationSource:string|null;origin:string;verification:string|null};
export type Event={at:string;requestId:string|null;providerRequestId:string|null;retailer:string;operation:string;outcome:string;upstreamStatus:number|null;
 failedPlatform:boolean;category:string;billing:string};
export type Report={locationId:string;freshnessSeconds:number;calculatedAt:string;counts:Counts;retailers:{id:string;recordedUsableProducts:number;
 usableLiveProductsForCurrentAccount:number;lastSuccessfulRefresh:string|null}[];rows:CoverageRow[];hasNext:boolean;events:Event[];sample:boolean};
export type Identity={brand:string;name:string;gtin:string;pack:string;variant:string;formulation:string};
export type Evidence={sourceProvider:string;sourceRecordId:string;evidenceReference:string;evidenceSha256:string;permissionReference:string;rationale:string};
export type Revision={decision_id:string;revision_no:number;retailer_sku:string;disposition:string;reviewed_at:string;reviewed_by:string;source_provider:string;
 source_record_id:string;evidence_reference:string;evidence_sha256:string;permission_reference:string;rationale:string;
 canonical_brand:string;canonical_title:string;canonical_pack:string;canonical_variant:string;canonical_gtins:string;
 listing_brand:string|null;listing_title:string|null;listing_gtin:string|null;listing_pack:string|null;listing_variant:string|null;listing_formulation:string|null;
 supersedes_decision_id:string|null;blocking_reasons:string};
export type Preview={decisionId:string;assessment:{canonicalPack:string;canonicalGtins:string[];
 canonicalIdentity:Identity;blockingReasons:string[];evidence:{confidence:string;score:number;decision:string;reasons:string[]}}};
type Candidate={variantId:string;platform:string;retailerSku:string;listingIdentity:Identity};
type Decision='APPROVED'|'REJECTED'|'UNRESOLVED';
const root='/admin/retailer-operations';
const mapping='/admin/provider/mapping';
const payload=(method:'POST'|'PUT',body:unknown)=>({method,body:JSON.stringify(body)});
export const operations={
 filters:()=>request<{locations:Area[];categories:Area[]}>(root+'/filters'),
 coverage:(fields:Record<string,string>,signal?:AbortSignal)=>request<Report>(root+'/coverage?'+new URLSearchParams(
  Object.fromEntries(Object.entries(fields).filter(([,v])=>v!==''))),{signal}),
 history:async(variantId:string,platform:string)=>{
  const full:Revision[]=[];
  for(;;){
   const page=await request<Revision[]>(mapping+'/decisions?'+new URLSearchParams({variantId,platform,afterRevision:String(full.at(-1)?.revision_no??0)}));
   full.push(...page);
   if(page.length<100)return full;
  }
 },
 assess:(variantId:string,listingIdentity:Identity)=>request<Preview>(mapping+'/preview',payload('POST',{variantId,listingIdentity})),
 save:(input:Candidate,mappingEvidence:Evidence&{decisionId:string;supersedesDecisionId:string|null},decision:Decision)=>request(
  mapping+(decision==='APPROVED'?'':'/decisions'),payload(decision==='APPROVED'?'PUT':'POST',
   {...input,mappingEvidence,...(decision==='APPROVED'?{}:{disposition:decision})})),
};
