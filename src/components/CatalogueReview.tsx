import React,{useEffect,useState} from 'react';
import {Linking,View} from 'react-native';
import {request} from '../services/api';
import {Button,Field,Heading,Label,ui} from './ui';

type Candidate={id:string;barcode:string;name:string;brand:string;quantity:string;sourceUrl:string;
 status:string;issue:string|null;suggestedCategory:string|null};
type Batch={id:string;expected_rows:number;committed_rows:number;staged_rows:number;correction_rows:number;invalid_rows:number};
type Issue={row_number:number;outcome:string;issue:string};
const path='/admin/catalogue-imports';
const pageSize=20;

export function CatalogueReview({onPublished}: {onPublished:()=>void}){
 const [open,setOpen]=useState(false),[busy,setBusy]=useState(false),[loading,setLoading]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 const [candidates,setCandidates]=useState<Candidate[]>([]),[batches,setBatches]=useState<Batch[]>([]);
 const [issues,setIssues]=useState<Issue[]>([]),[batchId,setBatchId]=useState(''),[page,setPage]=useState(0),[reload,refresh]=useState(0);
 const [selected,select]=useState<Candidate|null>(null);
 const [name,setName]=useState(''),[brand,setBrand]=useState(''),[quantity,setQuantity]=useState(''),[reason,setReason]=useState('');
 const [category,setCategory]=useState(''),[flavour,setFlavour]=useState(''),[parent,setParent]=useState('');
 useEffect(()=>{
  if(!open)return;
  const controller=new AbortController();
  const filters=new URLSearchParams({offset:String(page*pageSize),limit:String(pageSize)});
  if(batchId)filters.set('batchId',batchId);
  setCandidates([]);setIssues([]);setError('');setLoading(true);
  void Promise.all([
   request<Candidate[]>(path+'/review?'+filters.toString(),{signal:controller.signal}),
   request<Batch[]>(path+'/batches',{signal:controller.signal}),
   batchId?request<Issue[]>(path+'/batches/'+encodeURIComponent(batchId)+'/issues',{signal:controller.signal}):Promise.resolve([]),
  ]).then(([review,history,problems])=>{
   if(!controller.signal.aborted){setCandidates(review);setBatches(history);setIssues(problems);}
  }).catch(e=>{if(!controller.signal.aborted)setError(e.message);})
    .finally(()=>{if(!controller.signal.aborted)setLoading(false);});
  return ()=>controller.abort();
 },[open,page,batchId,reload]);
 function edit(candidate:Candidate){
  select(candidate);setName(candidate.name);setBrand(candidate.brand);setQuantity(candidate.quantity);
  setCategory(candidate.suggestedCategory||'');setFlavour('');setReason('');setParent('');setNotice('');
 }
 async function submit(action:'correct'|'approve'|'reject'|'publish'){
  if(!selected)return;
  setBusy(true);setError('');
  try{
   const endpoint=path+'/candidates/'+encodeURIComponent(selected.id);
   const body=action==='correct'?{name,brand,quantity,reason}:action==='approve'?
    {categoryId:category.trim(),variantName:flavour.trim(),existingProductId:parent.trim()||null}:undefined;
   const result=await request<{status:string;productId?:string}|undefined>(endpoint+(action==='correct'?'':'/'+action),
    {method:action==='correct'?'PUT':'POST',body:body?JSON.stringify(body):undefined});
   setNotice(result?.productId?result.status+' · Product '+result.productId:result?.status||
    (action==='correct'?'Correction saved.':'Candidate rejected.'));
   select(null);refresh(n=>n+1);
   if(action==='publish')onPublished();
  }catch(e){setError((e as Error).message);}
  finally{setBusy(false);}
 }
 const pending=selected&&['PENDING_REVIEW','NEEDS_CORRECTION'].includes(selected.status);
 return <View style={{gap:10,marginVertical:20}}>
  <Heading style={{fontSize:20}}>Catalogue imports</Heading>
  <Button title={open?'Hide import review':'Open import review'} secondary onPress={()=>setOpen(value=>!value)}/>
  {open&&<>
   <Button title="Refresh import review" secondary onPress={()=>refresh(n=>n+1)}/>
   {!!error&&<View accessibilityRole="alert"><Label>{error}</Label></View>}
   {loading&&<Label muted>Loading import review…</Label>}
   {!!notice&&<Label>{notice}</Label>}
   {batches.map(batch=><Button key={batch.id} secondary onPress={()=>{setPage(0);select(null);setBatchId(batch.id);}}
    title={batch.id.slice(0,8)+' · '+batch.committed_rows+'/'+batch.expected_rows+' processed · '+batch.invalid_rows+' invalid'}/>)}
   {!!batchId&&<Button title="Show all candidates" secondary onPress={()=>{setPage(0);select(null);setBatchId('');}}/>}
   {issues.map(issue=><Label key={issue.row_number} muted>Row {issue.row_number} · {issue.issue}</Label>)}
   {selected&&<View style={[ui.card,{gap:8}]}>
    <Heading style={{fontSize:17}}>{selected.name||'Unnamed product'}</Heading>
    <Label>{selected.status} · GTIN {selected.barcode}</Label>
    <Label muted>{selected.brand} · {selected.quantity}</Label>
    {!!selected.issue&&<Label>{selected.issue}</Label>}
    <Button title="Open Open Food Facts record" secondary onPress={()=>{void Linking.openURL(selected.sourceUrl).catch(()=>setError('Could not open the source record.'));}}/>
    {pending&&<>
     <Field label="Product name" value={name} onChangeText={setName}/>
     <Field label="Brand" value={brand} onChangeText={setBrand}/>
     <Field label="Pack size" value={quantity} onChangeText={setQuantity}/>
     <Field label="Correction reason" value={reason} onChangeText={setReason}/>
     <Button title="Save correction" disabled={busy||!reason.trim()} onPress={()=>void submit('correct')}/>
     <Field label="Category ID" value={category} onChangeText={setCategory}/>
     <Field label="Confirmed flavour or variant" value={flavour} onChangeText={setFlavour}/>
     <Field label="Existing inactive product ID (optional)" value={parent} onChangeText={setParent}/>
     <Button title="Approve as inactive" disabled={busy||!category.trim()||!flavour.trim()} onPress={()=>void submit('approve')}/>
     <Button title="Reject candidate" secondary disabled={busy} onPress={()=>void submit('reject')}/>
    </>}
    {selected.status==='IMPORTED'&&<Button title="Publish approved product and packs" disabled={busy} onPress={()=>void submit('publish')}/>}
   </View>}
   {candidates.map(candidate=><Button key={candidate.id} secondary
    title={(candidate.name||candidate.barcode)+' · '+candidate.status} onPress={()=>edit(candidate)}/>)}
   {!candidates.length&&!error&&!loading&&<Label muted>No candidates on this page.</Label>}
   <View style={[ui.row,{gap:8}]}>
    <Button title="Previous" secondary disabled={page===0} onPress={()=>setPage(p=>p-1)}/>
    <Button title="Next" secondary disabled={candidates.length<pageSize} onPress={()=>setPage(p=>p+1)}/>
   </View>
  </>}
 </View>;
}
