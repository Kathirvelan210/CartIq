import React,{useEffect,useRef,useState} from 'react';
import {Image,Linking,Pressable,View,useWindowDimensions} from 'react-native';
import {CompareAllControls} from './CompareAllControls';
import {PrivateSearchControls} from './PrivateSearchControls';
import {C} from '../theme';
import {api,type RetailerLookup,type RetailerProduct,type RetailerSearchResult,type RetailerComparison as Comparison} from '../services/api';
import {ApiError} from '../services/api-client';
import {comparisonAmount} from '../services/four-source';
import {filterListings,listingReview,listingUnitPrice,matchingPrices,mergeRetailerRows,retailerIds,retailerMessage,unattemptedRetailers} from '../services/live-retailers';
import type {ShoppingLocation} from '../services/shopping-location';
import {Button,Field,Heading,Label,ui} from './ui';

const retailerNames:Record<RetailerLookup,string>={blinkit:'Blinkit',zepto:'Zepto',instamart:'Swiggy Instamart'};
type Props={query:string;location:ShoppingLocation;admin:boolean;privateSearch?:boolean;platforms?:readonly RetailerLookup[]};

function Listing({item,fetchedAt,image=false,expired=false}:{item:RetailerProduct;fetchedAt:string;image?:boolean;expired?:boolean}){
 const availability=item.available==null?'Availability unknown':item.available?'Available':'Unavailable';
 const unitPrice=listingUnitPrice(item);
 return <View style={{gap:4}}>
  {image&&item.imageUrl&&<Image accessibilityLabel={item.title} source={{uri:item.imageUrl}}
   resizeMode="contain" style={{width:100,height:100}}/>}
  <Label style={{fontWeight:'700'}}>{item.title}</Label>
  {(item.brand||item.pack)&&<Label muted>{[item.brand,item.pack].filter(Boolean).join(' · ')}</Label>}
  {expired?<Label muted>Stale offer · price and stock excluded</Label>:<>
   <Label>{item.priceMinor==null?'Price unknown':comparisonAmount(item.priceMinor)} · {availability}</Label>
   {item.variant&&<Label muted>{item.variant}</Label>}
   {item.flavour&&<Label muted>{item.flavour}</Label>}{item.formulation&&<Label muted>{item.formulation}</Label>}
   <Label muted>Delivery fee unknown</Label>
   {unitPrice&&<Label muted>{comparisonAmount(unitPrice.minor)} / {unitPrice.unit}</Label>}
   {item.mrpMinor!=null&&<Label muted>MRP {comparisonAmount(item.mrpMinor)}</Label>}
   {item.discountPercent!=null&&<Label muted>Discount {item.discountPercent}%</Label>}
   {item.inventory!=null&&<Label muted>Stock {item.inventory}</Label>}
  </>}
  {!expired&&item.deliveryEstimate&&<Label>Delivery estimate {item.deliveryEstimate}</Label>}
  {image&&<FetchTime value={fetchedAt}/>}
  {item.providerFetchedAt&&<Label muted>Provider timestamp {item.providerFetchedAt}</Label>}
  {image&&item.productUrl&&<Button title="Open retailer listing" secondary
   onPress={()=>{if(item.productUrl)void Linking.openURL(item.productUrl).catch(()=>{});}}/>}
 </View>;
}

function FetchTime({value}:{value:string}){
 const time=value?new Date(value):null;
 return <Label muted>{time&&Number.isFinite(time.valueOf())?'Last fetched '+time.toLocaleString():'Fetch time unknown'}</Label>;
}

export function RetailerComparison({query,location,admin,platforms=retailerIds,privateSearch=false}:Props){
 const {width}=useWindowDimensions();
 const [result,setResult]=useState<Comparison|null>(null),[detail,setDetail]=useState<RetailerSearchResult|null>(null);
 const [selected,setSelected]=useState<{retailer:RetailerLookup;item:RetailerProduct;fetchedAt:string}|null>(null);
 const [busy,setBusy]=useState<'search'|'detail'|null>(null),[error,setError]=useState('');
 const [single,setSingle]=useState(false);
 const [chosen,setChosen]=useState<readonly RetailerLookup[]>(platforms);
 const [attempted,setAttempted]=useState<RetailerLookup[]>([]);
 const [paused,setPaused]=useState(false);
 const [itemAttempts,setItemAttempts]=useState<string[]>([]);
 const [filter,setFilter]=useState(''),[availableOnly,setAvailableOnly]=useState(false);
 const [sort,setSort]=useState<'relevance'|'low'|'high'>('relevance');
 const [,clock]=useState(0);
 const [reviewOpen,setReviewOpen]=useState(false),[reviewResult,setReviewResult]=useState('');
 const [reviewBusy,setReviewBusy]=useState(false);
 const active=useRef<AbortController|null>(null);
 useEffect(()=>()=>active.current?.abort(),[]);
 const scope={lat:location.latitude,lon:location.longitude,pincode:location.postcode??undefined};

 async function run(kind:'search'|'detail',lookup:(signal:AbortSignal)=>Promise<void>){
  if(active.current)return;
  const controller=new AbortController();
  active.current=controller;setBusy(kind);setError('');
  try{await lookup(controller.signal);}
  catch(e){
   if(!controller.signal.aborted){
    setPaused(true);
    setError(e instanceof ApiError&&e.status===403?'Live shopping is disabled for this account.'
     :e instanceof Error?e.message:'The retailer lookup failed.');
   }
  }finally{if(!controller.signal.aborted){active.current=null;setBusy(null);}}
 }
 const rows=result?.retailers||[];
 useEffect(()=>{
  const now=Date.now();
  const due=(result?.retailers??[]).flatMap(row=>row.result?[Date.parse(row.result.freshUntil??row.result.fetchedAt)+
   (row.result.freshUntil?0:900000)]:[]).filter(at=>at>now);
  const timers=[...new Set(due)].map(at=>setTimeout(()=>clock(n=>n+1),at-now+20));
  return()=>timers.forEach(clearTimeout);
 },[result]);
 const fresh=(row:RetailerSearchResult)=>Date.parse(row.freshUntil??row.fetchedAt)+(row.freshUntil?0:900000)>Date.now();
 const received=rows.flatMap(row=>row.status==='success'&&row.result&&fresh(row.result)
  ?[{retailer:row.retailer,products:row.result.products,fetchedAt:row.result.fetchedAt}]:[]);
 const best=matchingPrices(received).filter(group=>filterListings([group.item],filter,false,'relevance').length>0);
 const reviews=reviewOpen?listingReview(received):[];
 async function review(left:RetailerProduct,right:RetailerProduct){
  const identity=(item:RetailerProduct)=>({brand:item.brand,name:item.title,pack:item.pack,
   gtin:item.gtin&&item.barcode&&item.gtin!==item.barcode?null:item.gtin??item.barcode,
   variant:item.flavour,formulation:item.formulation});
  setReviewBusy(true);setReviewResult('');
  try{
   const result=await api.reviewRetailerMatch(identity(left),identity(right));
   setReviewResult(`Catalogue review · ${result.decision.replace(/_/g,' ')} · ${result.confidence} confidence · ${result.score}/100. No mapping was approved or saved.`);
  }catch(e){setReviewResult(e instanceof Error?e.message:'Catalogue review was unavailable.');}
  finally{setReviewBusy(false);}
 }
 const itemKey=selected?JSON.stringify([selected.retailer,selected.item.nativeId]):'';
 const requested=unattemptedRetailers(chosen,attempted);
 const cost=requested.length===1?'up to 1 credit':`up to ${requested.length} credits`;

 if(privateSearch&&!single)return <View style={{gap:15}}>
  <CompareAllControls query={query} location={location}/>
  <Pressable accessibilityRole="button" onPress={()=>{setResult(null);setSingle(true);}}><Label muted>Search just one retailer</Label></Pressable>
 </View>;
 return <View style={[ui.card,{marginTop:16,gap:12}]}>
  <Heading style={{fontSize:21}}>{privateSearch?'Compare retailer prices':'Live retailer search'}</Heading>
  <Label muted>{query} · {location.label}{location.postcode&&!location.label.includes(location.postcode)?' · '+location.postcode:''}{location.source==='pin'?' · Approximate PIN location':''}</Label>
  {privateSearch?<>
  <Pressable accessibilityRole="button" onPress={()=>{setSingle(!single);setResult(null);setSelected(null);setDetail(null);}}><Label muted>{single?'Close single-retailer search':'Search just one retailer'}</Label></Pressable>
  {single&&<PrivateSearchControls onClear={()=>{setResult(null);setSelected(null);setDetail(null);}} query={query} location={location} onResult={row=>setResult({query,retailers:[row]})}/>}</>:<>
  <View style={[ui.row,{flexWrap:'wrap',gap:8}]}>
   {platforms.map(retailer=>{
    const checked=chosen.includes(retailer),done=attempted.includes(retailer);
    return <Pressable key={retailer} accessibilityRole="checkbox" accessibilityLabel={retailerNames[retailer]}
     accessibilityState={{checked,disabled:!!busy||done||paused}} disabled={!!busy||done||paused}
     onPress={()=>setChosen(current=>current.includes(retailer)?current.filter(value=>value!==retailer):[...current,retailer])}
     style={[ui.pill,{minHeight:44,justifyContent:'center',borderWidth:1,borderColor:checked?C.green:C.line}]}>
     <Label>{checked?'✓ ':''}{retailerNames[retailer]}{done?' · Submitted':''}</Label>
    </Pressable>;
   })}
  </View>
  <Button title={requested.length?`Search selected retailers · ${cost}`:'Search selected retailers'}
   secondary loading={busy==='search'} disabled={!requested.length||!!busy||paused} onPress={()=>{
    void run('search',async signal=>{
     setAttempted(current=>[...current,...requested]);
     const next=await api.retailerCompare(query,scope,requested,admin,signal);
     if(!signal.aborted){
      setPaused(next.paused===true);
      setResult(current=>({...next,retailers:mergeRetailerRows(current?.retailers??[],next.retailers)}));
     }
    });
   }}/>
  </>}
  {paused&&<Label muted>Further retailer requests are paused for this search.</Label>}
  {!paused&&attempted.length===platforms.length&&<Label muted>Choose another product to start a fresh search.</Label>}
  {busy==='search'&&<View accessibilityLiveRegion="polite"><Label>Looking up retailer listings…</Label></View>}
  {!!error&&<View accessibilityRole="alert"><Label>{error}</Label></View>}
  {received.some(row=>row.products.length>0)&&<View style={{gap:8}}>
   <Field label="Filter retailer results" placeholder="Product, brand, category or pack" value={filter} onChangeText={setFilter} maxLength={100}/>
   <View style={[ui.row,{flexWrap:'wrap',gap:8}]}>
    {(['relevance','low','high'] as const).map(mode=><Button key={mode}
     title={mode==='low'?'Price low to high':mode==='high'?'Price high to low':'Retailer order'}
     secondary={mode!==sort} onPress={()=>setSort(mode)}/>)}
    <Button title={availableOnly?'Show all stock states':'Available only'} secondary={!availableOnly}
     onPress={()=>setAvailableOnly(value=>!value)}/>
   </View>
  </View>}
  {received.length>=2&&!best.length&&<Label muted>No comparable in-stock offers with confirmed prices for the same brand, variant and pack.</Label>}
  {best.map(({item,lowest,retailers,offers,saving,savingPercent,score,payable})=>
   <View key={JSON.stringify(offers.map(row=>[row.retailer,row.item.nativeId]))} style={[ui.card,{gap:8,backgroundColor:C.mint,borderColor:C.green}]}>
   {payable&&<Heading style={{fontSize:19}}>Lowest final payable {comparisonAmount(payable.lowest)} · {payable.retailers.map(id=>retailerNames[id]).join(', ')}</Heading>}
   <Label style={{fontWeight:'700',color:C.green}}>Lowest listed item price among returned offers</Label>
   <Heading style={{fontSize:23}}>{comparisonAmount(lowest)} · {retailers.map(id=>retailerNames[id]).join(', ')}</Heading>
   <Label>{item.title} · {item.pack}</Label>
   <Label muted>Listing match {score}/100 · Item prices exclude delivery and fees</Label>
   {saving>0&&<Label>Save {comparisonAmount(saving)} ({savingPercent.toFixed(1)}%) versus the highest available matched price.</Label>}
   {retailerIds.map(retailer=>{
    const offer=offers.find(row=>row.retailer===retailer);
    const status=rows.find(row=>row.retailer===retailer)?.outcome;
    const unit=offer&&listingUnitPrice(offer.item);
    return <View key={retailer} style={{gap:2}}>
     <Label>{retailerNames[retailer]} · {offer
      ?`${comparisonAmount(offer.item.priceMinor)} · ${offer.item.available===true?'Available':offer.item.available===false?'Unavailable':'Availability unknown'}`
      :!status?'No result received yet':status!=='SUCCESS'?retailerMessage(status):'No equivalent listing in this search'}</Label>
     {unit&&<Label muted>{comparisonAmount(unit.minor)} / {unit.unit}</Label>}
     {offer?.fetchedAt&&<FetchTime value={offer.fetchedAt}/>}
    </View>;
   })}
  </View>)}
  {received.filter(row=>row.products.length>0).length>1&&<View style={{gap:8}}>
   <Button title={reviewOpen?'Hide listing match review':'Review possible matches'} secondary
    onPress={()=>{setReviewOpen(value=>!value);setReviewResult('');}}/>
   {reviewOpen&&(reviews.length===0?<Label muted>No uncertain or distinct similar listings were identified.</Label>:<>
    <Label muted>Showing {Math.min(reviews.length,8)} of {reviews.length} similar pairs excluded from price comparisons.</Label>
    {reviews.slice(0,8).map(({left,right,match})=><View key={JSON.stringify([left.retailer,left.item.nativeId,right.retailer,right.item.nativeId])} style={[ui.card,{gap:6}]}>
     <Label style={{fontWeight:'700'}}>{match.decision==='review'?'Needs review':'Distinct products'} · {match.score}/100</Label>
     <Label>{retailerNames[left.retailer]} · {left.item.title} · {left.item.pack??'Unknown pack'}</Label>
     <Label>{retailerNames[right.retailer]} · {right.item.title} · {right.item.pack??'Unknown pack'}</Label>
     <Label muted>{match.reason}</Label>
     {admin&&match.decision==='review'&&<Button title="Check with catalogue reviewer" secondary disabled={reviewBusy}
      onPress={()=>{void review(left.item,right.item);}}/>}
    </View>)}
    {!!reviewResult&&<Label>{reviewResult}</Label>}
   </>)}
  </View>}
  {rows.length>0&&<View style={{flexDirection:width>=950?'row':'column',gap:12}}>
   {rows.map(row=>{
    const status=row.outcome??(row.status==='success'?(row.result?.products.length?'SUCCESS':'NO_RESULTS')
     :row.status==='error'?'PROVIDER_ERROR':'NOT_ATTEMPTED');
    const visible=filterListings(row.result?.products??[],filter,availableOnly,sort);
    return <View key={row.retailer} style={[ui.card,{flex:1,minWidth:0,gap:12}]}>
     <Label style={{fontWeight:'700'}}>{retailerNames[row.retailer]}</Label>
     {row.result&&<>
      <Label>{fresh(row.result)?'Live result':'Expired offer'}</Label>
      <FetchTime value={row.result.fetchedAt}/>
      {row.result.freshUntil&&Date.parse(row.result.freshUntil)<=Date.now()&&<Label muted>Older result. Search again for current retailer data.</Label>}
     </>}
     {row.status==='duplicate'?<Label muted>Already searched at this location. No new request was sent.</Label>:
      status!=='SUCCESS'&&<Label muted>{retailerMessage(status)}{row.message?' '+row.message:''}</Label>}
     {row.result?.products.length&&!visible.length?<Label muted>No listings match the selected filters.</Label>:null}
     {visible.map(item=><View key={item.nativeId} style={{gap:6}}>
      <Listing item={item} image={privateSearch} fetchedAt={row.result!.fetchedAt} expired={!fresh(row.result!)}/>
      <Button title="View listing details" secondary disabled={!!busy||!fresh(row.result!)} onPress={()=>{
       setError('');setDetail(null);setSelected({retailer:row.retailer,item,fetchedAt:row.result!.fetchedAt});
      }}/>
     </View>)}
    </View>;
   })}
  </View>}
  {selected&&<View style={[ui.card,{gap:10}]}>
   <Heading style={{fontSize:18}}>{retailerNames[selected.retailer]} listing</Heading>
   {!detail?.products.length&&<Listing expired={!rows.some(row=>row.retailer===selected.retailer&&row.result&&fresh(row.result))} item={selected.item} fetchedAt={selected.fetchedAt} image/>}
   {detail?.products.map(item=><Listing key={item.nativeId} item={item} fetchedAt={detail.fetchedAt} image/>)}
   {detail&&!detail.products.length&&<Label muted>No additional item details were returned.</Label>}
   {!privateSearch&&<Button title="Fetch retailer item details · 1 credit" secondary loading={busy==='detail'}
    disabled={paused||!!busy||itemAttempts.includes(itemKey)} onPress={()=>{
     void run('detail',async signal=>{
      setItemAttempts(previous=>[...previous,itemKey]);
      const next=await api.retailerItem(selected.retailer,selected.item.nativeId,scope,admin,signal);
      if(!signal.aborted)setDetail(next);
     });
    }}/>}
   <Button title="Close listing details" secondary disabled={!!busy} onPress={()=>{setSelected(null);setDetail(null);}}/>
  </View>}
 </View>;
}
