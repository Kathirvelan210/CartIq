import React,{useState} from 'react';
import {Switch,View} from 'react-native';
import {controlledPilot,groups,type Candidate,type Group,type Pack,type Report} from '../services/controlled-pilot';
import {Button,Field,Heading,Label,ui} from './ui';
import {ApiError} from '../services/api-client';

const text=(value:string)=>value.toLowerCase().replaceAll('_',' ');
const retailers=['blinkit','zepto','instamart'];
const date=(value:string)=>new Date(value).toLocaleString();
function iso(value:string){
 if(!/Z$|[+-]\d\d:\d\d$/.test(value)||!Number.isFinite(Date.parse(value)))throw new Error('Use an ISO timestamp with a timezone.');
 return new Date(value).toISOString();
}
function consent(value:boolean,onChange:(v:boolean)=>void,label:string){
 return <View style={[ui.row,{gap:8}]}><Switch accessibilityLabel={label} value={value} onValueChange={onChange}/>
  <Label>{label}</Label></View>;
}
export function ControlledPilotAdmin({area,adminId}:{area:string;adminId:string}) {
 const [data,setData]=useState<Awaited<ReturnType<typeof controlledPilot.list>>|null>(null);
 const [candidates,setCandidates]=useState<Candidate[]>([]),[packs,setPacks]=useState<Pack[]>([]),[group,setGroup]=useState<Group>('MILK');
 const [retailer,setRetailer]=useState('blinkit'),[start,setStart]=useState(''),[end,setEnd]=useState('');
 const [attempts,setAttempts]=useState('5'),[credits,setCredits]=useState('5'),[rights,setRights]=useState(''),[display,setDisplay]=useState(false),[audit,setAudit]=useState(false);
 const [report,setReport]=useState<Report|null>(null),[variant,setVariant]=useState(''),[account,setAccount]=useState(adminId);
 const [operation,setOperation]=useState<'SEARCH'|'ITEM'>('SEARCH'),[expiry,setExpiry]=useState(''),[reference,setReference]=useState(''),[authorized,setAuthorized]=useState(false);
 const [query,setQuery]=useState(''),[queryRights,setQueryRights]=useState(false);
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[message,setMessage]=useState('');
 async function run(action:()=>Promise<void>){
  if(busy)return;setBusy(true);setError('');setMessage('');
  try{await action();}catch(e){setError(e instanceof ApiError&&e.status===404?'The controlled pilot is available on the local backend.':(e as Error).message);}
  finally{setBusy(false);}
 }
 const selected=report?.pilot;
 async function load(id:string){setReport(await controlledPilot.report(id));}
 return <View style={[ui.card,{gap:10,marginVertical:12}]}>
  <Heading style={{fontSize:20}}>Controlled retailer pilot</Heading>
  <Button title="Open pilot controls" secondary loading={busy&&!data} onPress={()=>void run(async()=>setData(await controlledPilot.list()))}/>
  {data&&<>
   <Label muted>{data.dispatchEnabled?'Server dispatch is enabled. Each request still needs a one-use authorization.':'Server dispatch is disabled.'} {data.permissionConfigured?'A permission reference is configured.':'Written permission reference is missing.'}</Label>
   {data.pilots.map(p=><Button key={p.id} title={`${p.retailer} · ${p.location_id} · ${p.enabled?'enabled':'disabled'} · ${date(p.starts_at)}`}
    secondary onPress={()=>void run(async()=>{await load(p.id);setVariant('');setAuthorized(false);})}/>)}
   <Heading style={{fontSize:17}}>Prepare a disabled pilot</Heading>
   <Label>Selected catalogue area {area||'unavailable'}</Label>
   <View style={{flexDirection:'row',flexWrap:'wrap',gap:6}}>{retailers.map(r=><Button key={r} title={r} secondary={retailer!==r} onPress={()=>{setRetailer(r);setCandidates([]);setPacks([]);}}/>)}</View>
   <Button title="Load approved exact-pack candidates" secondary disabled={!area||busy} onPress={()=>void run(async()=>{
    const rows=await controlledPilot.candidates(area,retailer);setCandidates(rows);
    if(!rows.length)setMessage('No current approved mappings match this retailer, area and permission scope.');
   })}/>
   <Label muted>Assign a coverage group before adding a pack. Missing groups stay visible.</Label>
   <View style={{flexDirection:'row',flexWrap:'wrap',gap:6}}>{groups.map(g=><Button key={g} title={text(g)} secondary={g!==group} onPress={()=>setGroup(g)}/>)}</View>
   {candidates.map(c=><Button key={c.decision_id} title={`${c.brand} ${c.name} · ${c.pack_label}`} secondary
    disabled={packs.some(p=>p.variant===c.variant_id)||packs.length>=10} onPress={()=>setPacks(p=>[...p,{variant:c.variant_id,mappingDecision:c.decision_id,group}])}/>)}
   {packs.map(p=><Button key={p.variant} title={`Remove ${candidates.find(c=>c.variant_id===p.variant)?.name??p.variant} · ${text(p.group)}`} secondary
    onPress={()=>setPacks(rows=>rows.filter(x=>x.variant!==p.variant))}/>)}
   <Label muted>Missing groups {groups.filter(g=>!packs.some(p=>p.group===g)).map(text).join(', ')||'none'}</Label>
   <Field label="Pilot starts at (ISO timezone)" value={start} onChangeText={setStart}/>
   <Field label="Pilot ends at (ISO timezone)" value={end} onChangeText={setEnd}/>
   <Field label="Maximum attempts" value={attempts} onChangeText={setAttempts} keyboardType="number-pad"/>
   <Field label="Maximum credits" value={credits} onChangeText={setCredits} keyboardType="number-pad"/>
   <Field label="Written permission reference" value={rights} onChangeText={setRights} maxLength={200}/>
   {consent(display,setDisplay,'Transient catalogue join and pilot display are authorized')}
   {consent(audit,setAudit,'Operational evidence and billing metadata may be retained')}
   <Button title="Save disabled pilot" disabled={busy||!display||!audit||!packs.length} onPress={()=>void run(async()=>{
    const saved=await controlledPilot.create({location:area,retailer,startsAt:iso(start),endsAt:iso(end),maxAttempts:Number(attempts),
     maxCredits:Number(credits),permissionReference:rights.trim(),displayAndTemporaryJoinApproved:display,operationalMetadataApproved:audit,packs});
    setData(await controlledPilot.list());await load(saved.id);setMessage('Pilot saved disabled.');
   })}/>
  </>}
  {selected&&report&&<>
   <Heading style={{fontSize:17}}>Pilot evidence</Heading>
   <Label>{selected.retailer} · {selected.location_id} · {selected.enabled?'Enabled':'Disabled'} · {date(selected.starts_at)} to {date(selected.ends_at)}</Label>
   <Label muted>Limit {selected.attempt_limit} attempts and {selected.credit_limit} credits · Max 1 credit per action · Permission {selected.permission_reference}</Label>
   <Button title={selected.enabled?'Stop this pilot':'Enable selected pilot'} disabled={busy||(!selected.enabled&&!data?.dispatchEnabled)} secondary
    onPress={()=>void run(async()=>{await controlledPilot.enable(selected.id,!selected.enabled);await load(selected.id);setData(await controlledPilot.list());})}/>
   {report.packs.map(p=><Button key={p.variant_id} title={`${p.name} · ${p.pack_label} · ${text(p.grocery_group)}`} secondary={p.variant_id!==variant}
    onPress={()=>{setVariant(p.variant_id);setAuthorized(false);}}/>)}
   {selected.enabled&&<>
    <Field label="Authorized shopper account ID" value={account} onChangeText={v=>{setAccount(v);setAuthorized(false);}} maxLength={36}/>
    <View style={{flexDirection:'row',gap:8}}>{(['SEARCH','ITEM'] as const).map(op=><Button key={op} title={text(op)} secondary={op!==operation}
     onPress={()=>{setOperation(op);setAuthorized(false);}}/>)}</View>
    <Field label="Request authorization expiry (ISO timezone)" value={expiry} onChangeText={v=>{setExpiry(v);setAuthorized(false);}}/>
    <Field label="Separate live request authorization reference" value={reference} onChangeText={v=>{setReference(v);setAuthorized(false);}} maxLength={200}/>
    {operation==='SEARCH'&&<>
     <Field label="Marketplace query (optional)" value={query} maxLength={150} onChangeText={v=>{setQuery(v);setQueryRights(false);setAuthorized(false);}}/>
     {!!query.trim()&&consent(queryRights,setQueryRights,'Written permission covers private display of all listings returned by this query')}
    </>}
    {consent(authorized,setAuthorized,'This one exact paid request has separate explicit authorization')}
    <Button title="Issue one-use authorization" disabled={busy||!authorized||!variant||!reference.trim()||(operation==='SEARCH'&&!!query.trim()&&!queryRights)} onPress={()=>void run(async()=>{
     const approval={accountId:account.trim(),variant,operation,expiresAt:iso(expiry),authorizationReference:reference.trim(),separatelyAuthorized:authorized};
     if(operation==='SEARCH'&&query.trim())await controlledPilot.authorizeDiscovery(selected.id,{approval,query:query.trim(),discoveryDisplayApproved:queryRights});
     else await controlledPilot.authorize(selected.id,approval);
     setAuthorized(false);setReference('');setQueryRights(false);setQuery('');await load(selected.id);setMessage('One-use authorization recorded. No request was dispatched.');
    })}/>
   </>}
   <Button title="Reload pilot evidence" secondary disabled={busy} onPress={()=>void run(()=>load(selected.id))}/>
   {!report.attempts.length&&<Label muted>No request authorizations have been recorded.</Label>}
   {report.attempts.map(a=><View key={a.id} style={[ui.card,{gap:4}]}>
    <Label>{text(a.operation)} · {a.discovery_query??a.variant_id} · {a.result_code??(a.claimed_at?'Claimed, outcome pending':'Authorized only')}</Label>
    <Label muted>{a.authorization_reference} · {date(a.authorized_at)}{a.completed_at?' · Completed '+date(a.completed_at):''}</Label>
    <Label muted>{a.billing?text(a.billing):'No settlement'} · {a.confirmed_debit==null?'Debit unconfirmed':a.confirmed_debit+' credits charged'}
     {a.credits_remaining==null?'':' · '+a.credits_remaining+' credits remaining'}</Label>
    <Label muted>{a.provider_request_id??'Provider ID unavailable'}{a.upstream_status==null?'':' · HTTP '+a.upstream_status}
     {a.verified_count==null?'':' · '+a.verified_count+' verified of '+a.candidate_count+' candidates'}</Label>
   </View>)}
  </>}
  {message&&<Label>{message}</Label>}
  {error&&<View accessibilityRole="alert"><Label>{error}</Label></View>}
 </View>;
}
