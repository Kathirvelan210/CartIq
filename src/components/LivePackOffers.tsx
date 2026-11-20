import React,{useEffect,useRef,useState} from 'react';
import {Switch,View} from 'react-native';
import {ApiError} from '../services/api-client';
import {bindShoppingPosition} from '../services/api';
import type {RetailerLookup} from '../services/api';
import {liveOffers,type LiveOffer,type OfferView} from '../services/live-offers';
import {retailerIds,retailerMessage} from '../services/live-retailers';
import {useStore} from '../store';
import {money} from '../theme';
import {Button,Heading,Label,ui} from './ui';
import type {QuoteEvidence} from '../services/weekly-shopping';

const names:Record<string,string>={blinkit:'Blinkit',zepto:'Zepto',instamart:'Swiggy Instamart'};
const price=(minor:number|null)=>minor==null?'Price unknown':money(minor/100);

export function useExpiredOffer(freshUntil?:string) {
 const [,tick]=useState(0);
 const deadline=freshUntil==null?Infinity:Date.parse(freshUntil);
 useEffect(()=>{
  const remaining=deadline-Date.now();
  if(!Number.isFinite(remaining)||remaining<=0)return;
  const timer=setTimeout(()=>tick(n=>n+1),remaining+20);
  return()=>clearTimeout(timer);
 },[deadline]);
 return Number.isNaN(deadline)||deadline<=Date.now();
}

export function QuoteOrigin({quote}:{quote:QuoteEvidence}) {
 const stale=useExpiredOffer(quote.freshUntil);
 return <Label muted>{stale?'Expired offer':quote.origin==='LIVE_RESPONSE'?'Live response':quote.origin==='RETRIEVED_OFFER'?'Previously retrieved offer':'Recorded offer'} · {names[quote.retailer]??quote.retailer}
  {quote.source?' · '+quote.source:''}{quote.requestId?' · Request '+quote.requestId:''}
  {' · '}{quote.providerObservedAt?'Source observed '+new Date(quote.providerObservedAt).toLocaleString():
   (quote.origin==='RECORDED'||!quote.origin?'Observed ':'Fetched ')+new Date(quote.observedAt).toLocaleString()}</Label>;
}

export function LivePackOffers({variant,onChange}:{variant:string;onChange?:()=>void}) {
 const {user,location,shoppingLocation,offline}=useStore();
 if(!user||offline||!location.id||!variant)return null;
 return <PackOffers key={JSON.stringify([user.id,variant,location.id,shoppingLocation?.latitude,shoppingLocation?.longitude,shoppingLocation?.postcode])}
  variant={variant} onChange={onChange}/>;
}

function PackOffers({variant,onChange}:{variant:string;onChange?:()=>void}) {
 const {location,shoppingLocation}=useStore();
 const [view,setView]=useState<OfferView|null>(null),[busy,setBusy]=useState(false),[error,setError]=useState('');
 const [chosen,setChosen]=useState<RetailerLookup[]>([]),[attempted,setAttempted]=useState<RetailerLookup[]>([]),[details,setDetails]=useState<string[]>([]);
 const [rows,setRows]=useState<OfferView['retailers']>([]);
 const [lastSuccess,setLastSuccess]=useState<Partial<Record<RetailerLookup,string>>>({});
 const [submitted,setSubmitted]=useState<string[]>([]);
 const [,clock]=useState(0);
 const active=useRef<AbortController|null>(null);
 useEffect(()=>{
  const read=new AbortController();
  void liveOffers.read(variant,location.id,read.signal).then(next=>{if(!read.signal.aborted)setView(next);})
   .catch(e=>{if(!read.signal.aborted&&!(e instanceof ApiError&&[403,404].includes(e.status)))setError('Offer pilot status could not be loaded.');});
  return()=>{read.abort();active.current?.abort();};
 },[variant,location.id]);
 useEffect(()=>{
  if(!view)return;
  const times=[...view.quotes.map(q=>q.freshUntil),...rows.flatMap(r=>r.offers.map(o=>o.freshUntil))]
   .flatMap(at=>at?[Date.parse(at)]:[]).filter(at=>at>Date.now());
  if(!times.length)return;
  const timers=[...new Set(times)].map(at=>setTimeout(()=>clock(t=>t+1),at-Date.now()+20));
  return()=>{timers.forEach(clearTimeout);};
 },[view,rows]);
 async function refresh(retailers:RetailerLookup[],offer?:Pick<LiveOffer,'retailer'|'nativeId'>,authorizationId?:string) {
  if(active.current||!shoppingLocation||!retailers.length||view?.paused)return;
  const controller=new AbortController();active.current=controller;
  setBusy(true);setError('');
  if(authorizationId)setSubmitted(current=>[...current,authorizationId]);
  else if(offer)setDetails(current=>[...current,offer.retailer+'|'+offer.nativeId]);else setAttempted(current=>[...current,...retailers]);
  const scope={locationId:location.id,lat:shoppingLocation.latitude,lon:shoppingLocation.longitude,pincode:shoppingLocation.postcode,retailers,authorizationId};
  try{
   scope.locationId=await bindShoppingPosition(shoppingLocation,location.id,controller.signal);
   const response=offer?await liveOffers.item(variant,scope,offer.nativeId,controller.signal):await liveOffers.refresh(variant,scope,controller.signal);
   if(controller.signal.aborted)return;
   setView(response);
   setLastSuccess(previous=>({...previous,...Object.fromEntries(response.retailers.flatMap(row=>row.fetchedAt?[[row.retailer,row.fetchedAt]]:[]))}));
   setRows(old=>[...old.filter(row=>!response.retailers.some(next=>next.retailer===row.retailer)),...response.retailers]);
   if(response.retailers.some(row=>row.status==='success'))onChange?.();
  }catch(e){if(!controller.signal.aborted){setError(e instanceof Error?e.message:'Refresh failed.');setView(prior=>prior&&({...prior,paused:true}));}}
  finally{active.current=null;if(!controller.signal.aborted)setBusy(false);}
 }
 if(!view&&!error)return null;
 const selected=chosen.filter(retailer=>!attempted.includes(retailer));
 return <View style={[ui.card,{gap:10,marginTop:12}]}>
  <Heading style={{fontSize:19}}>Offers for this exact pack</Heading>
  {view?.quotes.map(q=><View key={q.retailer} style={{gap:4}}>
   <QuoteOrigin quote={q}/>
   {(!q.freshUntil||Date.parse(q.freshUntil)>Date.now())&&<Label>{price(q.priceMinor)} · {price(q.unitPriceMinor)} / {q.unit} · {q.stockPacks==null?'Stock count unknown':q.stockPacks+' packs reported'}</Label>}
  </View>)}
  {!view?.quotes.some(q=>!q.freshUntil||Date.parse(q.freshUntil)>Date.now())&&
   <Label muted>No current offer can support a recommendation for this pack. Availability, price, freshness or identity evidence is missing.</Label>}
  {view?.configured&&view.controlledPilot?<>
   <Label muted>Controlled pilot. Each approved action can use one credit for this pack at {location.name}.</Label>
   {!shoppingLocation&&<Label muted>Confirm the approved shopping location first.</Label>}
   {!view.authorizations?.length&&<Label muted>No active one-use authorization for this account and exact pack.</Label>}
   {view.authorizations?.map(ticket=><View key={ticket.id} style={{gap:5}}>
    <Label muted>Authorization expires {new Date(ticket.expiresAt).toLocaleString()}</Label>
    <Button title={`${ticket.operation==='SEARCH'?'Refresh':'Fetch details for'} ${names[ticket.retailer]} · 1 credit maximum`}
     disabled={!shoppingLocation||busy||view.paused||submitted.includes(ticket.id)||Date.parse(ticket.expiresAt)<=Date.now()}
     onPress={()=>void refresh([ticket.retailer],ticket.operation==='ITEM'?{retailer:ticket.retailer,nativeId:ticket.nativeId}:undefined,ticket.id)}/>
   </View>)}
   {view.receipt&&<Label muted>{view.receipt.billing.toLowerCase().replaceAll('_',' ')} · {view.receipt.confirmedDebit==null?'Debit unconfirmed':view.receipt.confirmedDebit+' credits charged'}
    {view.receipt.balance==null?'':' · '+view.receipt.balance+' credits remaining'}</Label>}
  </>:view?.configured?<>
   <Label muted>Manual pilot refresh for {location.name}. One attempt per retailer and request fingerprint. A timeout or uncertain bill can pause further requests.</Label>
   {!shoppingLocation&&<Label muted>Confirm the approved shopping location first.</Label>}
   <View style={{flexDirection:'row',flexWrap:'wrap',gap:12}}>
    {retailerIds.map(retailer=><View key={retailer} style={[ui.row,{gap:5}]}>
     <Switch value={chosen.includes(retailer)} accessibilityLabel={'Refresh '+names[retailer]} disabled={busy||attempted.includes(retailer)||view.paused}
      onValueChange={value=>setChosen(current=>value?[...current,retailer]:current.filter(item=>item!==retailer))}/>
     <Label>{names[retailer]}</Label>
    </View>)}
   </View>
   <Button title={selected.length?`Refresh selected offers · up to ${selected.length} credit(s)`:'Refresh selected offers'}
    loading={busy} disabled={!shoppingLocation||!selected.length||view.paused} onPress={()=>void refresh(selected)}/>
  </>:<Label muted>The transient offer pilot is unavailable for this area.</Label>}
  {view?.paused&&<Label muted>Further requests are paused. Use the current recorded offers or ask an operator to check the provider outcome.</Label>}
  {error&&<View accessibilityRole="alert"><Label>{error}</Label></View>}
  {rows.map(row=><View key={row.retailer} style={[ui.card,{gap:7}]}>
   <Label>{names[row.retailer]} · {row.outcome==='SUCCESS'?'Refresh succeeded':retailerMessage(row.outcome)}</Label>
   {!!row.message&&<Label muted>{row.message}</Label>}
   {lastSuccess[row.retailer]&&<Label muted>Last successful refresh {new Date(lastSuccess[row.retailer]!).toLocaleString()}</Label>}
   {row.upstreamStatus!=null&&<Label muted>Provider HTTP {row.upstreamStatus}{row.providerRequestId?' · '+row.providerRequestId:''}</Label>}
   {row.offers.slice(0,8).map(offer=>{
    const fresh=Date.parse(offer.freshUntil)>Date.now();
    const eligible=view?.quotes.some(q=>q.retailer===offer.retailer&&q.nativeId===offer.nativeId);
    return <View key={offer.nativeId} style={{gap:4}}>
     {offer.title&&<Label>{offer.title}{offer.brand?' · '+offer.brand:''}</Label>}
     <Label>{offer.pack??'Pack unknown'} · {offer.match==='VERIFIED'?'Verified catalogue match':offer.match==='DISTINCT'?'Distinct pack':'Unresolved match'} · {offer.score}/100</Label>
     {[offer.gtin,offer.flavour,offer.formulation].some(Boolean)&&<Label muted>{[offer.gtin,offer.flavour,offer.formulation].filter(Boolean).join(' · ')}</Label>}
     <Label muted>{fresh?'Live response':'Expired response'} · {offer.nativeId} · {new Date(offer.fetchedAt).toLocaleString()}</Label>
     {fresh&&<Label muted>{price(offer.priceMinor)}{offer.mrpMinor!=null?' · MRP '+price(offer.mrpMinor):''} · {offer.available===null?'Availability unknown':offer.available?'Available':'Unavailable'}</Label>}
     {!view?.controlledPilot&&fresh&&eligible&&<Button secondary title="Refresh verified item details · 1 credit" disabled={busy||view?.paused||details.includes(offer.retailer+'|'+offer.nativeId)}
      onPress={()=>void refresh([offer.retailer],offer)}/>}
    </View>;
   })}
  </View>)}
 </View>;
}
