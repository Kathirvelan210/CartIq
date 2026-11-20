import React,{useEffect,useState} from 'react';
import {ActivityIndicator,ScrollView,Switch,View} from 'react-native';
import {Button,Field,Heading,Label,ui} from './ui';
import {C,money} from '../theme';
import {operations,type Area,type CoverageRow,type Evidence,type Identity,type Preview,type Report,type Revision} from '../services/retailer-operations';

const retailers:Record<string,string>={blinkit:'Blinkit',zepto:'Zepto',instamart:'Swiggy Instamart'};
const states=['USABLE','NO_MAPPING','UNRESOLVED','PACK_CONFLICT','NO_OBSERVATION','STALE','MISSING_PRICE','UNKNOWN_STOCK','UNAVAILABLE','MISSING_FEES','DISABLED'];
const labels:Record<string,string>={UNAVAILABLE:'Unavailable offer',STALE:'Stale offer',NO_OBSERVATION:'No recorded offer',NO_MAPPING:'No approved mapping',
 PACK_CONFLICT:'Pack conflict',UNRESOLVED:'Unresolved product match',USABLE:'Usable recorded offer',UNKNOWN_STOCK:'Stock unconfirmed',
 PLATFORM_FAILED_UNSPECIFIED:'Provider reported platform failure, cause unconfirmed',ADAPTER_RESPONSE:'CartIQ response validation or transport',
 AUTH_OR_ACCOUNT:'Provider authentication or account',RETAILER_UPSTREAM:'Retailer upstream error',UNAVAILABLE_AREA_OR_ITEM:'Area or item unavailable'};
const label=(value:string)=>labels[value]??value.toLowerCase().replaceAll('_',' ');
const time=(value:string|null)=>value?new Date(value).toLocaleString():'Unavailable';
const blankIdentity:Identity={brand:'',name:'',gtin:'',pack:'',variant:'',formulation:''};
const blankEvidence:Evidence={sourceProvider:'QUICKCOMMERCEAPI',sourceRecordId:'',evidenceReference:'',evidenceSha256:'',permissionReference:'',rationale:''};

function Choices({name,options,value,choose}:{name:string;options:Area[];value:string;choose:(id:string)=>void}) {
 return <View style={{gap:5}}><Label>{name}</Label><ScrollView horizontal contentContainerStyle={{gap:6}}>
  {options.map(option=><Button key={option.id} title={option.name} secondary={option.id!==value} onPress={()=>choose(option.id)}/>)}
 </ScrollView></View>;
}
export function RetailerOperations({location}:{location:string}) {
 const [open,setOpen]=useState(false);
 return <View style={[ui.card,{gap:10,marginVertical:12}]}>
  <Heading style={{fontSize:20}}>Retailer coverage and data quality</Heading>
  <Button title={open?'Close retailer operations':'Open retailer operations'} secondary onPress={()=>setOpen(v=>!v)}/>
  {open&&<Operations key={location} initialLocation={location}/>}
 </View>;
}
function Operations({initialLocation}:{initialLocation:string}) {
 const [filters,setFilters]=useState<{locations:Area[];categories:Area[]}|null>(null),[report,setReport]=useState<Report|null>(null);
 const [scope,setScope]=useState({locationId:initialLocation,retailer:'',category:'',status:'',page:'0'});
 const [cycle,setCycle]=useState(0),[busy,setBusy]=useState(true),[error,setError]=useState('');
 const [candidate,setCandidate]=useState<CoverageRow|null>(null);
 useEffect(()=>{
  let active=true;
  operations.filters().then(f=>{if(!active)return;setFilters(f);
   if(!initialLocation&&f.locations[0])setScope(s=>({...s,locationId:f.locations[0]!.id}));
  }).catch(e=>{if(active)setError(e.message);});
  return()=>{active=false;};
 },[initialLocation,cycle]);
 useEffect(()=>{
  if(!scope.locationId)return;
  const abort=new AbortController();
  setBusy(true);setError('');setReport(null);
  operations.coverage(scope,abort.signal).then(r=>{if(!abort.signal.aborted)setReport(r);})
   .catch(e=>{if(!abort.signal.aborted)setError(e.message);}).finally(()=>{if(!abort.signal.aborted)setBusy(false);});
  return()=>abort.abort();
 },[scope,cycle]);
 function choose(key:'locationId'|'retailer'|'category'|'status',value:string){setCandidate(null);setScope(s=>({...s,[key]:value,page:'0'}));}
 return <>
  <Button title="Refresh coverage report" secondary onPress={()=>setCycle(n=>n+1)}/>
  {filters&&<>
   <Choices name="Delivery area" options={filters.locations} value={scope.locationId} choose={id=>choose('locationId',id)}/>
   <Choices name="Retailer" options={[{id:'',name:'All retailers'},...Object.entries(retailers).map(([id,name])=>({id,name}))]} value={scope.retailer} choose={id=>choose('retailer',id)}/>
   <Choices name="Category" options={[{id:'',name:'All categories'},...filters.categories]} value={scope.category} choose={id=>choose('category',id)}/>
   <Choices name="Offer status" options={[{id:'',name:'All offer statuses'},...states.map(id=>({id,name:label(id)}))]} value={scope.status} choose={id=>choose('status',id)}/>
  </>}
  {busy&&<ActivityIndicator color={C.green}/>}
  {error&&<View accessibilityRole="alert"><Label>{error}</Label></View>}
  {report&&<ReportView report={report} select={setCandidate}/>}
  {report&&<View style={{flexDirection:'row',flexWrap:'wrap',gap:8}}>
   <Button title="Previous coverage page" secondary disabled={scope.page==='0'} onPress={()=>setScope(s=>({...s,page:String(Number(s.page)-1)}))}/>
   <Button title="Next coverage page" secondary disabled={!report.hasNext} onPress={()=>setScope(s=>({...s,page:String(Number(s.page)+1)}))}/>
  </View>}
  {candidate?.variantId&&<MappingEditor key={candidate.variantId+':'+candidate.retailer} row={candidate} variantId={candidate.variantId}
   close={()=>setCandidate(null)} changed={()=>setCycle(n=>n+1)}/>}
 </>;
}
function ReportView({report,select}:{report:Report;select:(row:CoverageRow)=>void}) {
 const c=report.counts;
 const counts:[string,number][]=[['Active catalogue products',c.totalProducts],['Products with approved mappings',c.approvedProducts],
  ['Products without approved mappings',c.productsWithoutMapping],['Unresolved mapping conflicts',c.unresolvedProducts],['Stale offers',c.staleProducts],
  ['Missing prices',c.missingPriceProducts],['Uncertain stock',c.uncertainStockProducts],['Missing verified fees',c.missingFeeProducts],['Pack conflicts',c.packConflictProducts]];
 return <>
  <Label muted>Calculated {time(report.calculatedAt)} · Freshness window {report.freshnessSeconds / 60} minutes</Label>
  <Label muted>{report.sample?'Sample workspace. Synthetic observations are excluded. ':''}Counts show distinct products in this category and retailer scope. Offer status filters the rows below.</Label>
  <View style={{flexDirection:'row',flexWrap:'wrap',gap:10}}>
   {counts.map(([key,value])=><View key={key} style={[ui.card,{flexGrow:1,minWidth:146}]}><Heading style={{fontSize:20}}>{value}</Heading><Label muted>{key}</Label></View>)}
  </View>
  {report.retailers.map(r=><View key={r.id}>
   <Label>{retailers[r.id]} · {r.recordedUsableProducts} usable recorded products · {r.usableLiveProductsForCurrentAccount} live for this account and confirmed location</Label>
   <Label muted>Last successful provider refresh in this process: {time(r.lastSuccessfulRefresh)}</Label>
  </View>)}
  <Heading style={{fontSize:17}}>Offer evidence</Heading>
  {!report.rows.length&&<Label muted>No products match these filters.</Label>}
  {report.rows.map(r=><View key={JSON.stringify([r.productId,r.variantId,r.retailer])} style={[ui.card,{gap:6}]}>
   <Label>{r.name} · {r.brand} · {r.pack??'No active pack'} · {retailers[r.retailer]}</Label>
   <Label>{label(r.status)}{r.approved?' · Approved mapping':''}</Label>
   <Label muted>{r.observedAt?`${r.origin==='APPROVED_OBSERVATION'?'Retained approved observation':'Recorded observation'} · ${r.observationSource??'Source unknown'} · ${time(r.observedAt)}`:'Observation unavailable'}</Label>
   <Label muted>{r.priceMinor==null?'Price missing':money(r.priceMinor/100)} · {r.stock==null?'Exact stock unknown':r.stock+' packs recorded'} · {r.missingFees?'Verified fees missing':'Verified fee fields present'}</Label>
   {r.variantId&&<Button title={`Review mapping for ${r.name} · ${retailers[r.retailer]}`} secondary onPress={()=>select(r)}/>}
  </View>)}
  <Heading style={{fontSize:17}}>Recent retailer requests</Heading>
  <Label muted>Process-wide sanitized events. They are filtered by retailer and exclude account or delivery location.</Label>
  {!report.events.length&&<Label muted>No retailer request diagnostics in this process.</Label>}
  {report.events.slice(0,15).map((e,index)=><View key={index}>
   <Label>{retailers[e.retailer]} · {label(e.category)} · {e.upstreamStatus==null?'Provider HTTP unknown':'Provider HTTP '+e.upstreamStatus} · {time(e.at)}</Label>
   <Label muted>{e.operation} · {label(e.outcome)} · {label(e.billing)}{e.failedPlatform?' · Platform failed':''}
    {e.requestId?' · CartIQ '+e.requestId:''}{e.providerRequestId?' · Provider '+e.providerRequestId:''}</Label>
  </View>)}
 </>;
}

const identityFields:[keyof Identity,string,number][]=[['name','Retailer title',150],['brand','Retailer brand',100],['gtin','Retailer GTIN',14],
 ['pack','Pack count, size and unit',60],['variant','Flavour or variant',100],['formulation','Formulation',150]];
const evidenceFields:[keyof Evidence,string,number][]=[['sourceProvider','Source provider',80],['sourceRecordId','Source record ID',150],
 ['evidenceReference','Reviewed evidence reference',500],['evidenceSha256','Evidence SHA-256',64],['permissionReference','Permission reference',200],['rationale','Reason for decision',1000]];
function MappingEditor({row,variantId,close,changed}:{row:CoverageRow;variantId:string;close:()=>void;changed:()=>void}) {
 const [identity,setIdentity]=useState(blankIdentity),[evidence,setEvidence]=useState(blankEvidence),[sku,setSku]=useState(row.nativeId??'');
 const [preview,setPreview]=useState<Preview|null>(null),[history,setHistory]=useState<Revision[]>([]),[loaded,setLoaded]=useState(false);
 const [confirmed,setConfirmed]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState(''),[status,setStatus]=useState('');
 const [historyOpen,setHistoryOpen]=useState(false);
 useEffect(()=>{
  let active=true;
  operations.history(variantId,row.retailer).then(r=>{if(active){setHistory(r);setLoaded(true);}})
   .catch(e=>{if(active)setError(e.message);});
  return()=>{active=false;};
 },[variantId,row.retailer]);
 function changeIdentity(key:keyof Identity,value:string){setIdentity(i=>({...i,[key]:value}));setPreview(null);setConfirmed(false);}
 async function review(){
  setBusy(true);setError('');setStatus('');setPreview(null);
  try{setPreview(await operations.assess(variantId,identity));}catch(e){setError((e as Error).message);}finally{setBusy(false);}
 }
 async function decide(action:'APPROVED'|'REJECTED'|'UNRESOLVED'){
  if(!preview||!confirmed||!loaded)return;
  setBusy(true);setError('');setStatus('');
  const retailerSku=sku.trim();
  const prior=history.filter(r=>r.retailer_sku===retailerSku).at(-1);
  try{
   await operations.save({variantId,platform:row.retailer,retailerSku,listingIdentity:identity},
    {...evidence,decisionId:preview.decisionId,supersedesDecisionId:prior?.decision_id??null},action);
   setPreview(null);setConfirmed(false);setStatus(`Saved ${label(action)} decision.`);changed();
   setHistory(await operations.history(variantId,row.retailer));
  }catch(e){setError((e as Error).message);}finally{setBusy(false);}
 }
 function reuse(r:Revision){
  setSku(r.retailer_sku);
  setIdentity({brand:r.listing_brand??'',name:r.listing_title??'',gtin:r.listing_gtin??'',pack:r.listing_pack??'',
   variant:r.listing_variant??'',formulation:r.listing_formulation??''});
  setEvidence({sourceProvider:r.source_provider,sourceRecordId:'',evidenceReference:'',evidenceSha256:'',permissionReference:r.permission_reference,rationale:''});
  setPreview(null);setConfirmed(false);
 }
 const valid=!!preview&&confirmed&&loaded&&!busy&&!!sku.trim()&&Object.values(evidence).every(value=>value.trim()!=='');
 return <View style={[ui.card,{gap:8}]}>
  <Heading style={{fontSize:18}}>Review {row.name} · {retailers[row.retailer]}</Heading>
  <Label muted>Canonical {row.brand} · {row.name} · {row.pack}. Use an authorized evidence record for every decision.</Label>
  <Field label="Retailer listing ID" value={sku} maxLength={150} onChangeText={value=>{setSku(value);setConfirmed(false);}}/>
  {identityFields.map(([key,title,max])=><Field key={key} label={title} value={identity[key]} maxLength={max} onChangeText={v=>changeIdentity(key,v)}/>)}
  <Button title="Check identity evidence" secondary disabled={busy} onPress={()=>void review()}/>
  {preview&&<View style={{gap:6}}>
   <Label>{label(preview.assessment.evidence.decision)} · {preview.assessment.evidence.confidence} · {preview.assessment.evidence.score}/100</Label>
   <Label muted>Canonical GTINs {preview.assessment.canonicalGtins.join(', ')||'unknown'} · Variant {preview.assessment.canonicalIdentity?.variant||'unknown'} · Formulation {preview.assessment.canonicalIdentity?.formulation||'unknown'}</Label>
   <Label muted>{preview.assessment.evidence.reasons.map(label).join(' · ')}</Label>
   <Label>{preview.assessment.blockingReasons.length?'Approval blocked: '+preview.assessment.blockingReasons.map(label).join(', '):'Evidence check passed. A reviewer must still confirm approval.'}</Label>
  </View>}
  {evidenceFields.map(([key,title,max])=><Field key={key} label={title} value={evidence[key]} maxLength={max}
   onChangeText={value=>{setEvidence(e=>({...e,[key]:value}));setConfirmed(false);}}/>)}
  <View style={[ui.row,{gap:8}]}><Switch value={confirmed} onValueChange={setConfirmed} accessibilityLabel="Confirm human review of listing and permission"/>
   <Label>I reviewed the listing evidence and its permissions.</Label></View>
  <View style={{flexDirection:'row',flexWrap:'wrap',gap:8}}>
   <Button title={history.length?'Approve new mapping revision':'Approve retailer mapping'} disabled={!valid||!!preview?.assessment.blockingReasons.length} onPress={()=>void decide('APPROVED')}/>
   <Button title="Reject candidate" secondary disabled={!valid} onPress={()=>void decide('REJECTED')}/>
   <Button title="Keep unresolved" secondary disabled={!valid} onPress={()=>void decide('UNRESOLVED')}/>
  </View>
  {status&&<Label>{status}</Label>}{error&&<View accessibilityRole="alert"><Label>{error}</Label></View>}
  <Button title={historyOpen?'Hide mapping history':'View complete mapping history'} secondary disabled={!loaded} onPress={()=>setHistoryOpen(v=>!v)}/>
  {historyOpen&&(!history.length?<Label muted>No prior mapping decisions.</Label>:history.slice().reverse().map(r=><View key={r.decision_id} style={[ui.card,{gap:5}]}>
   <Label>Revision {r.revision_no} · {label(r.disposition)} · {r.retailer_sku}</Label>
   <Label muted>{time(r.reviewed_at)} · Reviewer {r.reviewed_by}{r.supersedes_decision_id?' · Supersedes '+r.supersedes_decision_id:''}</Label>
   <Label>{[r.listing_title,r.listing_brand,r.listing_gtin,r.listing_pack,r.listing_variant,r.listing_formulation].filter(Boolean).join(' · ')||'Listing fields unavailable'}</Label>
   <Label muted>Canonical {r.canonical_title} · {r.canonical_brand} · {r.canonical_pack} · {r.canonical_variant} · {r.canonical_gtins||'GTIN unknown'}</Label>
   <Label muted>{r.source_provider} · {r.source_record_id} · Evidence {r.evidence_reference} · {r.evidence_sha256} · Permission {r.permission_reference}</Label>
   <Label muted>{r.rationale}{r.blocking_reasons?' · Blockers '+r.blocking_reasons:''}</Label>
   <Button title={`Correct or supersede revision ${r.revision_no}`} secondary onPress={()=>reuse(r)}/>
  </View>))}
  <Button title="Close mapping review" secondary onPress={close}/>
 </View>;
}
