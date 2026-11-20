import React,{useEffect,useState} from 'react';
import {View} from 'react-native';
import {request} from '../services/api';
import {Button,Heading,Label,ui} from './ui';
import {C} from '../theme';

const root='/admin/price-acquisition';
type Series={variant_id:string;retailer:string;location_id:string;retained_observations:number};
type Overview={coverage:{observations:number;products:number;retailers:number;locations:number;first:string|null;last:string|null;trainingAllowed:number;inferenceAllowed:number};
 legacySimulated:number;withoutTrainingApproval:number;intakeCounts:{disposition:string;total:number}[];series:Series[];sample:boolean};
type Source={grant_id:string;source_identity:string;provider:string;retailer:string;location_id:string;retain_until:string;permission_status:string;
 analytics_allowed:boolean;training_allowed:boolean;serve_allowed:boolean;display_allowed:boolean};
type Detail={eligibleRows:number;eligibleDays:number;first:string|null;last:string|null;validationTargets:number;testTargets:number;missingDates:string[];readiness:string};
const title=(key:string)=>key.toLowerCase().replaceAll('_',' ');

export function PriceDatasetCoverage(){
 const [open,setOpen]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState('');
 const [data,setData]=useState<Overview|null>(null),[sources,setSources]=useState<Source[]>([]);
 const [selected,select]=useState<Series|null>(null),[detail,setDetail]=useState<Detail|null>(null);
 useEffect(()=>{
  if(!open)return;
  const controller=new AbortController();setBusy(true);setError('');select(null);setDetail(null);
  Promise.all([request<Overview>(root+'/coverage',{signal:controller.signal}),request<Source[]>(root+'/sources',{signal:controller.signal})])
   .then(([report,registry])=>{if(!controller.signal.aborted){setData(report);setSources(registry);}})
   .catch(e=>{if(!controller.signal.aborted)setError(e.message);}).finally(()=>{if(!controller.signal.aborted)setBusy(false);});
  return ()=>controller.abort();
 },[open]);
 useEffect(()=>{
  if(!open||!selected)return;
  const controller=new AbortController();setDetail(null);setError('');
  const query=new URLSearchParams({variantId:selected.variant_id,retailer:selected.retailer,locationId:selected.location_id});
  request<Detail>(root+'/coverage/series?'+query,{signal:controller.signal}).then(result=>{if(!controller.signal.aborted)setDetail(result);})
   .catch(e=>{if(!controller.signal.aborted)setError(e.message);});
  return ()=>controller.abort();
 },[open,selected]);
 return <View style={[ui.card,{gap:10,marginVertical:12}]}><Heading style={{fontSize:20}}>Historical dataset coverage</Heading>
  <Button title={open?'Close dataset coverage':'Review dataset coverage'} secondary onPress={()=>{setData(null);setSources([]);setOpen(x=>!x);}}/>
  {open&&busy&&<Label muted>Checking retained observations and permissions…</Label>}
  {open&&error!==''&&<Label style={{color:C.red}}>{error}</Label>}
  {open&&data&&<>
   {data.sample&&<Label muted>Sample environment. Sample rows are excluded from genuine coverage.</Label>}
   <Label>{data.coverage.observations} approved observations · {data.coverage.products} products · {data.coverage.retailers} retailers · {data.coverage.locations} areas</Label>
   <Label muted>{data.coverage.first?`${data.coverage.first.slice(0,10)} to ${data.coverage.last?.slice(0,10)} UTC`:'No historical date range available'}</Label>
   <Label>{data.coverage.trainingAllowed} rows with training approval · {data.coverage.inferenceAllowed} rows with forecast-serving approval</Label>
   <Label muted>{data.legacySimulated} simulated legacy rows excluded · {data.withoutTrainingApproval} retained rows lack training approval</Label>
   {data.intakeCounts.length>0&&<Label muted>Intake decisions · {data.intakeCounts.map(row=>`${title(row.disposition)} ${row.total}`).join(' · ')}</Label>}
   <Heading style={{fontSize:17}}>Source permissions</Heading>
   {sources.length===0&&<Label muted>No registered source authorizations.</Label>}
   {sources.map(row=><Label key={row.grant_id}>{row.provider} · {row.source_identity} · {row.retailer} / {row.location_id} · {title(row.permission_status)} · retention to {row.retain_until.slice(0,10)} · analytics {row.analytics_allowed?'allowed':'restricted'} · train {row.training_allowed?'allowed':'restricted'} · serve {row.serve_allowed?'allowed':'restricted'} · display {row.display_allowed?'allowed':'restricted'}</Label>)}
   <Heading style={{fontSize:17}}>Exact series</Heading>
   {data.series.length===0&&<Label muted>No retained price series to evaluate.</Label>}
   {data.series.map(series=><Button key={JSON.stringify(series)} title={`${series.variant_id} · ${series.retailer} · ${series.location_id}`} secondary onPress={()=>select(series)}/>)}
   {selected&&detail&&<><Label>{detail.eligibleDays} eligible days · {title(detail.readiness)}</Label>
    <Label muted>{detail.first?`${detail.first} to ${detail.last} UTC`:'No eligible date range'} · {detail.validationTargets} validation targets · {detail.testTargets} test targets</Label>
    <Label muted>{detail.missingDates.length} missing days{detail.missingDates.length>0?`: ${detail.missingDates.slice(0,12).join(', ')}${detail.missingDates.length>12?'…':''}`:''}</Label></>}
  </>}
 </View>;
}
