import {request} from './api';
export const groups=['MILK','RICE','DAL','COOKING_OIL','SNACKS'] as const;
export type Group=typeof groups[number];
export type Pack={variant:string;mappingDecision:string;group:Group};
export type Candidate={decision_id:string;variant_id:string;retailer_sku:string;name:string;brand:string;pack_label:string;category:string};
type Pilot={id:string;location_id:string;retailer:string;enabled:boolean;starts_at:string;ends_at:string;attempt_limit:number;credit_limit:number;permission_reference:string};
type Attempt={id:string;variant_id:string;authorization_reference:string;operation:string;discovery_query?:string|null;expires_at:string;authorized_at:string;claimed_at:string|null;completed_at:string|null;
 result_code:string|null;billing:string|null;provider_request_id:string|null;upstream_status:number|null;confirmed_debit:number|null;
 credits_remaining:number|null;candidate_count:number|null;verified_count:number|null};
export type Report={pilot:Pilot;packs:{variant_id:string;mapping_decision_id:string;grocery_group:Group;name:string;brand:string;pack_label:string}[];attempts:Attempt[]};
const root='/admin/retailer-pilot';
const post=(method:string,body:object)=>({method,body:JSON.stringify(body)});
export const controlledPilot={
 list:()=>request<{dispatchEnabled:boolean;permissionConfigured:boolean;pilots:Pilot[]}>(root),
 candidates:(area:string,retailer:string)=>request<Candidate[]>(root+'/candidates?'+new URLSearchParams({area,retailer})),
 report:(id:string)=>request<Report>(root+'/'+id),
 create:(body:object)=>request<{id:string}>(root,post('POST',body)),
 enable:(id:string,enabled:boolean)=>request(root+'/'+id+'/enabled',post('PUT',{enabled})),
 authorize:(id:string,body:object)=>request<{id:string}>(root+'/'+id+'/authorize',post('POST',body)),
 authorizeDiscovery:(id:string,body:object)=>request<{id:string}>(root+'/'+id+'/authorize-discovery',post('POST',body)),
};
