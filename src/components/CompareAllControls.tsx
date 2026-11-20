import {useStore} from '../store';
import {shoppingKey} from '../services/shopping-location';
import {LiveProductList,RetailerMark} from './LiveProductList';
import React,{useEffect,useRef,useState} from 'react';
import {View,Pressable} from 'react-native';
import {api,request,type RetailerLookup,type RetailerComparison} from '../services/api';
import type {ShoppingLocation} from '../services/shopping-location';
import {C} from '../theme';
import {Button,Label,ui} from './ui';

const root='/shopping/retailers/private-search/compare-all';
const names={blinkit:'Blinkit',zepto:'Zepto',instamart:'Swiggy Instamart'};
const ids=['blinkit','zepto','instamart'] as const;
type Row=RetailerComparison['retailers'][number];
type ProgressRow=Pick<Row,'retailer'|'result'|'outcome'|'message'>&{providerRequestId:string|null;upstreamStatus:number|null};
type Quote={id:string|null;expiresAt:string;maximumCredits:number;selection:{query:string;latitude:number;longitude:number;postcode:string|null};
 quote:{balance:number;allowance:number;checkedAt:string;blocker:string|null;legs:{retailer:RetailerLookup;platform:string;duplicate:boolean}[]}};
type CachedRow={jobId:string;expiresAt:string;row:ProgressRow};
type Progress={id:string;rows:ProgressRow[];done:boolean;error:string|null;billing:{settled:boolean;balance:number|null}|null};

export function CompareAllControls({query,location}:{query:string;location:ShoppingLocation}){
 const [preview,setPreview]=useState<Quote|null>(null),[progress,setProgress]=useState<Progress|null>(null);
 const [busy,setBusy]=useState(false),[sent,setSent]=useState(false),[error,setError]=useState('');
 const [showRequests,setShowRequests]=useState(false);
 const [selected,setSelected]=useState<readonly RetailerLookup[]>(ids);
 const account=useStore().user?.id??'';
 const placeKey=shoppingKey(query,location,account);
 const scope=selected.length===ids.length?placeKey:JSON.stringify([placeKey,...selected]);
 const [restoring,setRestoring]=useState(true);
 const [cached,setCached]=useState<CachedRow[]>([]);
 const selection={query,latitude:location.latitude,longitude:location.longitude,postcode:location.postcode,retailers:selected};
 async function readCache(signal:AbortSignal){
  if(!selected.length)return [];
  return request<CachedRow[]>(root+'/cached',{method:'POST',signal,body:JSON.stringify(selection)});
 }
 useEffect(()=>{
  const controller=new AbortController();setRestoring(true);setCached([]);
  void readCache(controller.signal).then(rows=>{if(!controller.signal.aborted)setCached(rows);})
   .catch(()=>{}).finally(()=>{if(!controller.signal.aborted)setRestoring(false);});
  return()=>controller.abort();
 },[scope,account]);
 const [,tick]=useState(0);
 useEffect(()=>{
  const dates=[...cached.map(c=>c.expiresAt),...(progress?.rows??[]).flatMap(r=>r.result?[r.result.freshUntil??new Date(Date.parse(r.result.fetchedAt)+900000).toISOString()]:[])];
  const timers=dates.map(d=>setTimeout(()=>tick(n=>n+1),Math.max(0,Date.parse(d)-Date.now())+10));
  return()=>timers.forEach(clearTimeout);
 },[progress,cached]);
 const currentCache=cached.filter(c=>Date.parse(c.expiresAt)>Date.now()&&selected.includes(c.row.retailer));
 const missing=selected.filter(id=>!currentCache.some(c=>c.row.retailer===id)&&!progress?.rows.some(r=>r.retailer===id&&r.result?.products.length&&Date.parse(r.result.freshUntil??new Date(Date.parse(r.result.fetchedAt)+900000).toISOString())>Date.now()));
 const visibleRows=[...currentCache.map(c=>c.row),...(progress?.rows??[]).filter(row=>!currentCache.some(c=>c.row.retailer===row.retailer))];
 const action=useRef<AbortController|null>(null);
 function toggle(id:RetailerLookup){
  if(action.current||busy||sent&&!progress?.done)return;
  setSelected(current=>ids.filter(retailer=>retailer===id?!current.includes(id):current.includes(retailer)));
  setPreview(null);setProgress(null);setCached([]);setSent(false);setError('');setShowRequests(false);
 }
 useEffect(()=>()=>action.current?.abort(),[]);
 useEffect(()=>{
  if(!preview?.id||sent)return;
  const timer=setTimeout(()=>setPreview(null),Math.max(0,Date.parse(preview.expiresAt)-Date.now()));
  return()=>clearTimeout(timer);
 },[preview,sent]);
 async function run(confirm:boolean){
  if(action.current||!selected.length||restoring||(sent&&!progress?.done)||confirm&&(!preview?.id||sent))return;
  const ctrl=new AbortController();action.current=ctrl;setBusy(true);setError('');
  try{
   if(!confirm){
    const rows=await readCache(ctrl.signal);
    if(ctrl.signal.aborted)return;
    const needed=selected.filter(id=>!rows.some(c=>c.row.retailer===id&&Date.parse(c.expiresAt)>Date.now()));
    setCached(rows);setProgress(null);setSent(false);setPreview(null);
    if(!needed.length)return;
    await api.selectPosition(location);
    if(ctrl.signal.aborted)return;
    const next=await request<Quote>(root+'/prepare',{method:'POST',signal:ctrl.signal,
     body:JSON.stringify({...selection,retailers:needed})});
    if(next.maximumCredits!==needed.length||next.quote.legs.length!==needed.length||
       !needed.every(id=>next.quote.legs.some(leg=>leg.retailer===id)))throw Error('The preview did not match the requested retailers. No searches were sent.');
    setPreview(next);
   }else{
    setSent(true);
    let state=await request<Progress>(root+'/confirm',{method:'POST',signal:ctrl.signal,
     body:JSON.stringify({id:preview!.id,approved:true})});
    const until=Date.now()+90_000;
    while(!ctrl.signal.aborted){
     setProgress(state);
     if(state.done)break;
     if(Date.now()>=until)throw Error('Status check timed out. Verify the ledger before another search.');
     await new Promise(resolve=>setTimeout(resolve,500));
     if(ctrl.signal.aborted)return;
     state=await request<Progress>(root+'/'+state.id,{signal:ctrl.signal});
    }
   }
  }catch(e){if(!ctrl.signal.aborted)setError(confirm?'Submission or status check failed. Do not resubmit. Verify the ledger before another search.':(e as Error).message);}
  finally{action.current=null;if(!ctrl.signal.aborted)setBusy(false);}
 }
 return <View style={{gap:14}}>
  <View style={{flexDirection:'row',flexWrap:'wrap',gap:10}}>
   {ids.map(id=>{
    const leg=preview?.quote.legs.find(row=>row.retailer===id),row=visibleRows.find(r=>r.retailer===id);
    const status=currentCache.some(c=>c.row.retailer===id)?'Previously fetched':row?.result?(row.result.products.length?'Results received':'No matching products'):
     row?row.message:leg?.duplicate?'Previously requested':sent&&!progress?.done?'Search still running':sent?'No result received':'One search';
    const on=selected.includes(id),locked=busy||sent&&!progress?.done;
    return <Pressable key={id} accessibilityRole="switch" accessibilityLabel={'Search '+names[id]} accessibilityState={{checked:on,disabled:locked}} onPress={()=>toggle(id)} disabled={locked}
     style={[ui.card,{flexGrow:1,flexBasis:145,gap:6,padding:14,borderRadius:14,borderColor:on?C.green:C.line,backgroundColor:on?C.mint:C.paper}]}>
     <RetailerMark id={id}/>
     <Label>{on?'On · '+(status??'No result'):'Off · No request'}</Label>
     {showRequests&&row?.upstreamStatus&&<Label muted>HTTP {row.upstreamStatus}</Label>}
     {showRequests&&row?.providerRequestId&&<Label muted>Request {row.providerRequestId}</Label>}
    </Pressable>;
   })}
  </View>
  {!!currentCache.length&&<Label>Previously fetched � {currentCache.map(c=>names[c.row.retailer]).join(', ')} � 0 new credits</Label>}
  {(!preview||progress?.done)&&missing.length>0&&<Button title={progress?.done?'Preview another search':missing.length===3?'Compare all prices':`Compare prices � ${missing.length} ${missing.length===1?'retailer':'retailers'}`} disabled={restoring||sent&&!progress?.done} loading={busy||restoring} onPress={()=>void run(false)}/>}
  {!selected.length&&<Button title="Select a retailer to compare" disabled onPress={()=>{}}/>}
  {preview&&<View style={{backgroundColor:C.surface,borderRadius:14,padding:16,gap:6}}>
   <Label style={{fontWeight:'700'}}>{sent?'Search receipt':'Search preview'} · {preview.selection.query}</Label>
   <Label>{location.label} · {preview.selection.latitude}, {preview.selection.longitude}{preview.selection.postcode?' · PIN '+preview.selection.postcode:''}</Label>
   <Pressable accessibilityRole="button" onPress={()=>setShowRequests(!showRequests)}><Label style={{color:C.green}}>{showRequests?'Hide request details':'View request details'}</Label></Pressable>
   {showRequests&&preview.quote.legs.map(leg=><Label key={leg.retailer} muted>GET /v1/search?q={encodeURIComponent(preview.selection.query)}&amp;platform={leg.platform}&amp;lat={preview.selection.latitude}&amp;lon={preview.selection.longitude}{preview.selection.postcode?'&pincode='+preview.selection.postcode:''}</Label>)}
   {!sent&&preview.quote.legs.some(leg=>leg.duplicate)&&<Label>The previous products are no longer available. Confirm to request fresh prices.</Label>}
   {!sent&&<Label>Maximum {preview.maximumCredits} {preview.maximumCredits===1?'credit':'credits'} · Verified balance {preview.quote.balance} · {preview.quote.allowance} {preview.quote.allowance===1?'request':'requests'} available</Label>}
   {preview.quote.blocker&&<Label style={{color:C.red}}>{preview.quote.blocker} No searches sent.</Label>}
   {!sent&&<Button title={`Confirm ${preview.maximumCredits} ${preview.maximumCredits===1?'search':'searches'} · up to ${preview.maximumCredits} ${preview.maximumCredits===1?'credit':'credits'}`} disabled={!preview.id||!!preview.quote.blocker||busy} onPress={()=>void run(true)}/>}
  </View>}
  {progress?.billing&&<Label>{progress.billing.settled?'Billing settled':'Billing uncertain · further searches blocked'}{progress.billing.balance!=null?' · Provider balance '+progress.billing.balance:''}</Label>}
  <LiveProductList cachedJobs={Object.fromEntries(currentCache.map(c=>[c.row.retailer,c.jobId]))} retailers={selected} rows={visibleRows.filter(r=>selected.includes(r.retailer)).map(r=>({...r,status:r.result?'success':'error'}))} job={progress?.id??null} done={!!progress?.done}/>
  {!!(error||progress?.error)&&<View accessibilityRole="alert"><Label>{error||progress?.error}</Label></View>}
 </View>;
}
