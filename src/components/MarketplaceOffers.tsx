import React,{useEffect,useRef,useState} from 'react';
import {Image,View} from 'react-native';
import {ApiError} from '../services/api-client';
import {api} from '../services/api';
import type {Product} from '../types';
import {comparableListings,freshMarketplaceRows} from '../services/marketplace-comparison';
import {comparisonAmount} from '../services/four-source';
import {marketplace,type MarketplaceListing,type MarketplaceResult,type MarketplaceRow,type MarketplaceTicket} from '../services/marketplace';
import {retailerMessage} from '../services/live-retailers';
import {useStore} from '../store';
import {claimBrowseTickets,searchAuthorizedRetailers,type BrowseIntent} from '../services/department-lookup';
import {C} from '../theme';
import {shoppingKey,shoppingLocationLabel} from '../services/shopping-location';
import {Button,Heading,Label,ui} from './ui';

const retailerNames={blinkit:'Blinkit',zepto:'Zepto',instamart:'Swiggy Instamart'};
function ListingImage({image,title}:{image:string|null;title:string}){
 const [failed,setFailed]=useState<string|null>(null);
 return image&&failed!==image?<Image source={{uri:image}} accessibilityLabel={title} onError={()=>setFailed(image)}
  resizeMode="contain" style={{width:84,height:84}}/>:null;
}
export function MarketplaceOffers({query,intent,openProduct,openLocation}:{query:string;intent?:BrowseIntent;openProduct:(product:Product)=>void;openLocation:()=>void}){
 const {user,location,shoppingLocation,chooseShoppingLocation,cart,cartReady,refreshCart,toast}=useStore();
 const [view,setView]=useState<Awaited<ReturnType<typeof marketplace.read>>|null>(null);
 const [outcomes,setOutcomes]=useState<Partial<Record<MarketplaceRow['retailer'],MarketplaceResult>>>({});
 const [details,setDetails]=useState<string|null>(null),[error,setError]=useState(''),[revision,setRevision]=useState(0);
 const [retailer,setRetailer]=useState<MarketplaceRow['retailer']|null>(null),[sort,setSort]=useState(false);
 const [loading,setLoading]=useState(false),[working,setWorking]=useState(false),[,tick]=useState(0);
 const operation=useRef<AbortController|null>(null),cartAction=useRef(false);
 useEffect(()=>{
  if(!user||!shoppingLocation||!location.id||!query)return;
  const controller=new AbortController();
  setLoading(true);setError('');
  void marketplace.read(location.id,shoppingLocation,query,controller.signal).then(async data=>{
   if(controller.signal.aborted)return;
   setView(data);
   const tickets=claimBrowseTickets(intent,shoppingKey(query,shoppingLocation,user.id),query,data.authorizations,data.recorded,Date.now());
   if(data.configured)await searchAuthorizedRetailers(tickets,ticket=>search(ticket,data.locationId??location.id),controller.signal);
   if(!controller.signal.aborted&&data.locationId&&data.locationId!==location.id)
    await chooseShoppingLocation(shoppingLocation,data.locationId);
  }).catch(problem=>{
   if(controller.signal.aborted)return;
   if(intent)intent.used=true;
   setView(null);
   setError(problem instanceof ApiError&&problem.status===403?'Live pricing is disabled on this server.':
    problem instanceof ApiError&&problem.status===404?'This server has no live marketplace endpoint.':(problem as Error).message);
  }).finally(()=>{if(!controller.signal.aborted)setLoading(false);});
  return()=>controller.abort();
 },[user,location.id,shoppingLocation,query,revision,intent]);
 useEffect(()=>()=>operation.current?.abort(),[]);
 useEffect(()=>{
  const now=Date.now();
  const times=view?.recorded.flatMap(row=>[row.freshUntil,...row.listings.map(x=>x.evidence.freshUntil)])??[];
  const timers=[...new Set(times.map(Date.parse).filter(at=>at>now))].map(at=>setTimeout(()=>tick(n=>n+1),at-now+20));
  return()=>timers.forEach(clearTimeout);
 },[view]);
 async function search(ticket:MarketplaceTicket,area=location.id){
  if(operation.current||!shoppingLocation)return;
  const controller=new AbortController();operation.current=controller;
  setWorking(true);setError('');
  try{
   const result=await marketplace.search(area,shoppingLocation,query,ticket,controller.signal);
   if(!controller.signal.aborted){setView(result.marketplace);setOutcomes(old=>({...old,[result.retailer]:result}));return result.billing;}
  }catch(problem){
   if(!controller.signal.aborted){setError((problem as Error).message);setView(old=>old&&({...old,authorizations:[]}));}
  }finally{if(!controller.signal.aborted){operation.current=null;setWorking(false);}}
 }
 async function add(row:MarketplaceRow,item:MarketplaceListing){
  const variant=item.evidence.variantId;
  if(cartAction.current||!shoppingLocation||!variant||!cartReady)return;
  cartAction.current=true;setWorking(true);setError('');
  try{
   const prior=cart.find(entry=>entry.locationId===location.id&&entry.productId===variant)?.listing;
   const replace=!!prior&&(prior.retailer!==row.retailer||prior.nativeId!==item.listing.nativeId);
   await marketplace.add(location.id,shoppingLocation,query,row.retailer,variant,item.listing.nativeId,replace);
   await refreshCart();toast('Exact pack added to your planning cart.');
  }catch(problem){setError((problem as Error).message);}
  finally{cartAction.current=false;setWorking(false);}
 }
 const genuineFreshRows=freshMarketplaceRows(view?.recorded??[],Date.now());
 const comparisons=new Map(comparableListings(genuineFreshRows,Date.now()).map(group=>[group.variantId,group]));
 const paused=Object.values(outcomes).some(row=>row?.billing==='UNCERTAIN');
 const tickets=view?.authorizations.filter(ticket=>Date.parse(ticket.expiresAt)>Date.now()&&
  !genuineFreshRows.some(row=>row.retailer===ticket.retailer))??[];
 return <View style={[ui.card,{gap:12,marginVertical:12}]}>
  <Heading style={{fontSize:22}}>Retailer prices</Heading>
  <Label muted>{query}{shoppingLocation?' · '+shoppingLocationLabel(shoppingLocation):''}</Label>
  {!user?<Label>Sign in to check live retailer prices.</Label>:!shoppingLocation?<Button title="Choose a delivery location for live prices" secondary onPress={openLocation}/>:null}
  {(working||loading)&&<Label>{working?'Fetching retailer results…':'Checking retailer prices…'}</Label>}
  {view&&!view.configured&&<Label>Retailer search is not enabled for this delivery area. No retailer was queried.</Label>}
  {view?.configured&&<>
   <View style={{flexDirection:'row',flexWrap:'wrap',gap:6}}>
    <Button title="All retailers" secondary={retailer!==null} onPress={()=>setRetailer(null)}/>
    {Object.entries(retailerNames).map(([id,name])=><Button key={id} title={name}
     secondary={retailer!==id} onPress={()=>setRetailer(id as MarketplaceRow['retailer'])}/>)}
    <Button title={sort?'Retailer order':'Sort known prices low to high'} secondary onPress={()=>setSort(value=>!value)}/>
   </View>
   <Button title="Check for authorized refresh" secondary disabled={working||loading} onPress={()=>{
    setOutcomes(current=>Object.fromEntries(Object.entries(current).filter(([,row])=>row?.outcome!=='SUCCESS')));
    setRevision(n=>n+1);
   }}/>
   {!loading&&!working&&Object.entries(retailerNames).filter(([id])=>(!retailer||id===retailer)&&
    !genuineFreshRows.some(row=>row.retailer===id)&&!tickets.some(ticket=>ticket.retailer===id)&&!(id in outcomes))
    .map(([id,name])=><Label key={id} muted>{name} · Search authorization unavailable for this location and query.</Label>)}
   {tickets.filter(ticket=>!retailer||retailer===ticket.retailer).map(ticket=><Button key={ticket.id}
    title={`Search ${retailerNames[ticket.retailer]} · up to 1 credit`} disabled={working||loading||paused}
    onPress={()=>void search(ticket)}/>)}
  </>}
  {!!error&&<View accessibilityRole="alert"><Label>{error}</Label></View>}
  {Object.values(outcomes).filter(row=>row&&(!retailer||row.retailer===retailer)).map(row=><Label key={row!.retailer}>
   {retailerNames[row!.retailer]} · {row!.outcome==='CACHED'?'Previously retrieved':row!.outcome==='SUCCESS'?'Results received':retailerMessage(row!.outcome)||'Lookup failed'}{row!.message?' · '+row!.message:''}
  </Label>)}
  {genuineFreshRows.filter(row=>!retailer||row.retailer===retailer).map(row=>{
   const expired=Date.parse(row.freshUntil)<=Date.now();
   const origin=expired?'Expired result':outcomes[row.retailer]?.outcome==='SUCCESS'?'Live response':'Previously retrieved';
   const listings=sort?[...row.listings].sort((a,b)=>(a.listing.priceMinor??Infinity)-(b.listing.priceMinor??Infinity)):row.listings;
   return <View key={row.retailer} style={[ui.card,{gap:10}]}>
    <Heading style={{fontSize:18}}>{retailerNames[row.retailer]}</Heading>
    <Label muted>{origin} · Fetched {new Date(row.fetchedAt).toLocaleString()} · QuickCommerceAPI</Label>
    {!listings.length&&<Label>No current listings for this search.</Label>}
    {listings.map(item=>{
     const {listing,evidence}=item,key=row.retailer+':'+listing.nativeId;
     const stale=expired||Date.parse(evidence.freshUntil)<=Date.now();
     const match=!stale&&evidence.match==='VERIFIED'&&evidence.variantId?comparisons.get(evidence.variantId):undefined;
     const cartEligible=!stale&&!!evidence.variantId&&listing.priceMinor!=null&&listing.available===true&&(listing.inventory??1)>0;
     return <View key={key} style={[ui.card,{gap:6}]}>
      <ListingImage image={listing.imageUrl} title={listing.title}/>
      <Label style={{fontWeight:'700',fontSize:18}}>{listing.title}</Label>
      <Label muted>{[listing.brand,listing.pack].filter(Boolean).join(' · ')||'Brand and pack unknown'}</Label>
      <Label style={{fontWeight:'700',fontSize:17,color:C.green}}>{stale?'Expired offer · refresh required':`${comparisonAmount(listing.priceMinor)} · ${listing.available==null?'Stock unknown':listing.available?'Available':'Unavailable'}`}</Label>
      {match&&<View style={{backgroundColor:C.mint,borderRadius:12,padding:12,gap:5}}>
       <Label style={{fontWeight:'700'}}>Same pack across retailers</Label>
       {match.prices.map(quote=><Label key={quote.retailer}>{retailerNames[quote.retailer as MarketplaceRow['retailer']]} · {comparisonAmount(quote.price)}{quote.price===match.prices[0]?.price?' · Lowest item price':''} · {new Date(quote.fetchedAt).toLocaleTimeString()}</Label>)}
      </View>}
      {!stale&&listing.inventory!=null&&<Label>Stock {listing.inventory}</Label>}
      {!stale&&evidence.unitPriceMinor!=null&&<Label>{comparisonAmount(evidence.unitPriceMinor)} / {evidence.unit}</Label>}
      {!stale&&listing.mrpMinor!=null&&<Label>MRP {comparisonAmount(listing.mrpMinor)}</Label>}
      {!stale&&listing.discountPercent!=null&&<Label>Discount {listing.discountPercent}%</Label>}
      <Label muted>{evidence.match==='VERIFIED'?'Approved exact-pack match':'Listing is outside the approved comparison pack scope'}</Label>
      <Button title={details===key?'Hide listing details':'View listing details'} secondary onPress={()=>setDetails(details===key?null:key)}/>
      {details===key&&<Label muted>Brand {listing.brand??'unknown'} · Pack {listing.pack??'unknown'} · MRP {comparisonAmount(listing.mrpMinor)} · Discount {listing.discountPercent==null?'not provided':listing.discountPercent+'%'} · Retailer listing {listing.nativeId} · Request {row.requestId||'unknown'}{evidence.observedAt?' · Observed '+new Date(evidence.observedAt).toLocaleString():''}</Label>}
      {details===key&&evidence.variantId&&<Button title="Open exact pack and item details" secondary onPress={()=>{
       void api.product(location.id,evidence.variantId!).then(openProduct).catch(problem=>setError(problem.message));
      }}/>}
      {cartEligible&&<Button title={cart.some(entry=>entry.locationId===location.id&&entry.productId===evidence.variantId&&entry.listing&&(entry.listing.retailer!==row.retailer||entry.listing.nativeId!==listing.nativeId))?"Switch cart to this listing":"Add to Cart"} disabled={working||!cartReady} onPress={()=>void add(row,item)}/>}
     </View>;
    })}
   </View>;
  })}
 </View>;
}
