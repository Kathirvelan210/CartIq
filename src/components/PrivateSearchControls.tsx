import {ApiError} from '../services/api-client';
import {useStore} from '../store';
import {shoppingKey} from '../services/shopping-location';
import {browserSearchStorage,comparisonBookmark,saveComparisonBookmark} from '../services/recent-queries';
import React,{useEffect,useRef,useState} from 'react';
import {View} from 'react-native';
import {api,request,type RetailerComparison,type RetailerLookup,type RetailerSearchResult} from '../services/api';
import type {ShoppingLocation} from '../services/shopping-location';
import {Button,Label,ui} from './ui';

const root='/shopping/retailers/private-search';
const names={blinkit:'Blinkit',zepto:'Zepto',instamart:'Swiggy Instamart'};
type Preview={id:string;expiresAt:string;ledgerBalance:number;maximumCredits:number;selection:{query:string;retailer:RetailerLookup;latitude:number;longitude:number;postcode:string|null}};
type Row=RetailerComparison['retailers'][number];
type Receipt={retailer:RetailerLookup;outcome:NonNullable<Row['outcome']>;result:RetailerSearchResult|null;billing:string;upstreamStatus:number|null;providerRequestId:string|null;message:string|null;durationMs:number};

export function PrivateSearchControls({query,location,onResult,onClear}:{query:string;location:ShoppingLocation;onResult:(row:Row)=>void;onClear:()=>void}){
 const [platforms,setPlatforms]=useState<RetailerLookup[]>([]),[retailer,setRetailer]=useState<RetailerLookup>('blinkit');
 const [preview,setPreview]=useState<Preview|null>(null),[error,setError]=useState(''),[receipt,setReceipt]=useState<Receipt|null>(null);
 const [busy,setBusy]=useState(false),[paused,setPaused]=useState(false);
 const account=useStore().user?.id??'';
 const scope=shoppingKey(query,location,account)+'|single|'+retailer;
 const [restored,setRestored]=useState(false);
 const lock=useRef(false),mounted=useRef(true);
 useEffect(()=>{mounted.current=true;const c=new AbortController();
  void request<RetailerLookup[]>(root+'/platforms',{signal:c.signal}).then(ids=>{
   if(c.signal.aborted)return;
   setPlatforms(ids);if(ids[0])setRetailer(ids[0]);else setError('The provider currently lists no supported grocery retailers.');
  }).catch(e=>{if(!c.signal.aborted)setError(e.message);});return()=>{mounted.current=false;c.abort();};
 },[]);
 useEffect(()=>{if(!preview)return;const timer=setTimeout(()=>setPreview(null),Math.max(0,Date.parse(preview.expiresAt)-Date.now()));return()=>clearTimeout(timer);},[preview]);
 async function submit(){
  if(lock.current||paused)return;
  lock.current=true;setBusy(true);setError('');
  try{
   if(!preview){
    await api.selectPosition(location);
    if(!mounted.current)return;
    const saved=comparisonBookmark(browserSearchStorage(),account,scope);
    if(saved){
     let cached:Receipt|null=null;
     try{cached=await request<Receipt>(root+'/receipts/'+saved);}
     catch(e){if(!(e instanceof ApiError && e.status===404))throw e;}
     if(!mounted.current)return;
     if(cached?.result){
      setReceipt(cached);setRestored(true);
      onResult({retailer:cached.retailer,result:cached.result,outcome:cached.outcome,status:'success',message:cached.message});return;
     }
     saveComparisonBookmark(browserSearchStorage(),account,scope,saved,0);
     setReceipt(null);setRestored(false);onClear();setError('Previous results have expired. Preview a new search to check availability.');return;
    }
    setRestored(false);
    const selection={query,retailer,latitude:location.latitude,longitude:location.longitude,postcode:location.postcode};
    const next=await request<Preview>(root+'/prepare',{method:'POST',body:JSON.stringify(selection)});
    if(mounted.current){setReceipt(null);setPreview(next);}
   }else{
    setPreview(null);
    const data=await request<Receipt>(root+'/confirm',{method:'POST',body:JSON.stringify({id:preview.id,approved:true})},45000);
    if(!mounted.current)return;
    if(data.result)saveComparisonBookmark(browserSearchStorage(),account,scope,preview.id,Date.now()+300_000);
    setReceipt(data);setPaused(data.billing==='UNCERTAIN');
    onResult({retailer:data.retailer,result:data.result,outcome:data.outcome,status:data.result?'success':'error',message:data.message});
   }
  }catch(e){if(mounted.current){setError((e as Error).message);if(preview)setPaused(true);}}
  finally{lock.current=false;if(mounted.current)setBusy(false);}
 }
 return <View style={{gap:10}}>
  <View style={[ui.row,{gap:8,flexWrap:'wrap'}]}>{platforms.map(id=><Button key={id} secondary={retailer!==id} disabled={busy||paused} title={names[id]} onPress={()=>{onClear();setRetailer(id);setPreview(null);setRestored(false);setReceipt(null);setError('');}}/>)}</View>
  {preview&&<View style={[ui.card,{gap:4}]}>
   <Label>{names[preview.selection.retailer]} · {preview.selection.query}</Label>
   <Label>Delivery coordinates {preview.selection.latitude}, {preview.selection.longitude}{preview.selection.postcode?' · PIN '+preview.selection.postcode:''}</Label>
   <Label>One request · Maximum 1 credit · Ledger balance {preview.ledgerBalance}</Label>
  </View>}
  <Button title={preview?'Confirm one paid search':comparisonBookmark(browserSearchStorage(),account,scope)?'Reopen previous search':'Preview retailer search'} secondary={!preview} loading={busy} disabled={!platforms.includes(retailer)||paused||busy} onPress={()=>void submit()}/>
  {!!error&&<View accessibilityRole="alert"><Label>{error}</Label></View>}
  {restored&&<Label>Previously fetched · reopened without spending credits</Label>}
  {receipt&&<Label>{names[receipt.retailer]} · {receipt.billing==='CHARGED_SETTLED'?'1 credit charged':receipt.billing==='NO_CHARGE_SETTLED'?'No charge':'Billing uncertain'}{receipt.upstreamStatus?' · Provider HTTP '+receipt.upstreamStatus:''}{receipt.result?' · Balance '+receipt.result.creditsRemaining:''} · {receipt.durationMs} ms{receipt.providerRequestId?' · Request '+receipt.providerRequestId:''}</Label>}
  {paused&&<Label>Further searches are paused. Check the request outcome before continuing.</Label>}
 </View>;
}
