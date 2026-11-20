import React,{useRef,useState} from 'react';
import {Image,Linking,Pressable,View,useWindowDimensions} from 'react-native';
import {Check,Package,ShoppingBag} from 'lucide-react-native';
import type {RetailerComparison,RetailerLookup} from '../services/api';
import {comparisonAmount} from '../services/four-source';
import {listingUnitPrice,retailerMessage,matchingPrices} from '../services/live-retailers';
import {useStore} from '../store';
import {Button,Heading,Label,ui} from './ui';
import {C} from '../theme';
type Row=RetailerComparison['retailers'][number];
import {productGroups,relatedFruitOffers,type Offer} from '../services/comparison-products';
const names={blinkit:'Blinkit',zepto:'Zepto',instamart:'Swiggy Instamart'};
const colors={blinkit:['#fff186','#244600'],zepto:['#f4e4ff','#602399'],instamart:['#fff0dc','#a34000']};
const logos={blinkit:'blinkit.webp',zepto:'zepto.webp',instamart:'swiggy.png'};
export function RetailerMark({id}:{id:RetailerLookup}){
 const [failed,setFailed]=useState(false);
 return <View style={{flexDirection:'row',alignItems:'center',gap:4,alignSelf:'flex-start',backgroundColor:colors[id][0],paddingHorizontal:8,paddingVertical:2,borderRadius:5}}>
  {!failed&&<Image accessible={false} resizeMode="contain" onError={()=>setFailed(true)} source={{uri:'https://quickcommerceapi.com/platforms/'+logos[id]}} style={{width:20,height:20,borderRadius:4}}/>}<Label style={{fontSize:12,fontWeight:'800',color:colors[id][1]}}>{names[id]}</Label></View>;
}
function canBuy(o:Offer){return o.fresh&&o.item.currency==='INR'&&o.item.available===true&&o.item.inventory!==0&&o.item.priceMinor!=null;}
function Card({offers,related,rows,job,done,retailers,cachedJobs}:{cachedJobs:Partial<Record<RetailerLookup,string>>;retailers:readonly RetailerLookup[];offers:Offer[];related:ReturnType<typeof relatedFruitOffers>[number];rows:Row[];job:string|null;done:boolean}){
 const {liveCart,toast}=useStore(),wide=useWindowDimensions().width>=1110;
 const [choice,setChoice]=useState<RetailerLookup|null>(null),[busy,setBusy]=useState(false),[added,setAdded]=useState(''),[badImage,setBadImage]=useState('');
 const guard=useRef(false);
 const eligible=offers.filter(canBuy).sort((a,b)=>a.item.priceMinor!-b.item.priceMinor!);
 const selected=offers.find(o=>o.retailer===choice)??eligible[0]??offers[0]!;
 const best=matchingPrices(offers.filter(o=>o.fresh).map(o=>({retailer:o.retailer,products:[o.item]})))[0];
 const sourceJob=cachedJobs[selected.retailer]??job;
 const {item}=selected,unit=listingUnitPrice(item);
 async function add(){
  if(!sourceJob||!canBuy(selected)||guard.current)return;
  guard.current=true;setBusy(true);
  try{await liveCart.add(sourceJob,selected.retailer,item.nativeId);setAdded(selected.retailer);toast(names[selected.retailer]+' listing added to your cart.');}
  catch(e){toast((e as Error).message);}finally{guard.current=false;setBusy(false);}
 }
 return <View style={[ui.card,{borderRadius:16,gap:16,padding:18}]}>
  <View style={{flexDirection:wide?'row':'column',alignItems:wide?'center':'stretch',gap:16}}>
   <View style={{flexDirection:'row',alignItems:'center',gap:14,flex:wide?1.15:undefined,minWidth:0}}>
    <View style={{width:76,height:86,alignItems:'center',justifyContent:'center',backgroundColor:C.surface,borderRadius:10}}>
     {item.imageUrl&&item.imageUrl!==badImage?<Image accessibilityLabel={item.title} source={{uri:item.imageUrl}} onError={()=>setBadImage(item.imageUrl!)} resizeMode="contain" style={{width:70,height:76}}/>:<Package size={26} color={C.muted}/>}
    </View>
    <View style={{flex:1,gap:3}}>{item.brand&&<Label muted style={{fontSize:12}}>{item.brand}</Label>}<Heading style={{fontSize:17,lineHeight:22}}>{item.title}</Heading>
     <Label>{[item.pack||'Pack unknown',item.variant,item.flavour,item.formulation].filter(Boolean).join(' · ')}</Label>
     {item.productUrl&&<Pressable accessibilityRole="link" onPress={()=>void Linking.openURL(item.productUrl!).catch(()=>toast('Retailer link could not open.'))}><Label style={{color:C.green}}>Retailer listing ↗</Label></Pressable>}
    </View>
   </View>
   <View style={{flexDirection:'row',flex:wide?1.6:undefined,flexWrap:'wrap',gap:6}}>
    {retailers.map(id=>{
     const offer=offers.find(o=>o.retailer===id),row=rows.find(r=>r.retailer===id);
     const selectedHere=id===selected.retailer,alternative=related.get(id);
     return <Pressable key={id} accessibilityRole="radio" accessibilityLabel={'Select '+names[id]+' for '+item.title} accessibilityState={{selected:selectedHere,disabled:!offer}}
      disabled={!offer} onPress={()=>setChoice(id)} style={{flexGrow:1,flexBasis:95,borderWidth:1,borderRadius:10,padding:8,gap:5,borderColor:selectedHere?C.green:C.line,backgroundColor:selectedHere?C.mint:C.paper}}>
      <RetailerMark id={id}/>
      {offer?<><Label style={{fontSize:18,fontWeight:'800'}}>{offer.fresh?comparisonAmount(offer.item.priceMinor):'Expired'}</Label>
       <Label muted style={{fontSize:12}}>{!offer.fresh?'Previous offer':offer.item.available==null?'Stock unknown':offer.item.available&&offer.item.inventory!==0?'In stock':'Out of stock'}</Label>
       {offer.item.deliveryEstimate&&offer.fresh&&<Label muted>{offer.item.deliveryEstimate}</Label>}</>:
       row?.outcome==='SUCCESS'&&alternative?<><Label style={{fontWeight:'700'}}>Similar fruit</Label>
        <Label>{[alternative.offer.item.brand,alternative.offer.item.title,alternative.offer.item.pack].filter(Boolean).join(' · ')}</Label>
        <Label>{alternative.offer.item.currency==='INR'?comparisonAmount(alternative.offer.item.priceMinor):'Price unknown'}</Label>
        <Label muted style={{fontSize:12}}>Separate listing · excluded from lowest price</Label></>:
       <Label muted style={{fontSize:12}}>{row?.outcome&&row.outcome!=='SUCCESS'?retailerMessage(row.outcome):row?'No matching pack returned':done?'No result':'Searching…'}</Label>}
     </Pressable>;
    })}
   </View>
  </View>
  <View style={{borderTopWidth:1,borderColor:C.line,paddingTop:12,flexDirection:'row',alignItems:'center',justifyContent:'space-between',flexWrap:'wrap',gap:9}}>
   <View style={{gap:4,flexGrow:1}}>
    {best&&<View style={[ui.row,{gap:5,alignSelf:'flex-start',backgroundColor:C.mint,borderRadius:6,paddingHorizontal:8}]}><Check size={14} color={C.green}/><Label style={{fontWeight:'700',color:C.green}}>Lowest listed {comparisonAmount(best.lowest)} · {best.retailers.map(r=>names[r]).join(', ')}</Label></View>}
    {best&&<Label muted style={{fontSize:12}}>Rule-based match · {best.score}/100 · matching product and pack</Label>}
    <Label muted style={{fontSize:12}}>{names[selected.retailer]} · Fetched {new Date(selected.at).toLocaleTimeString()}{selected.fresh&&unit?' · '+comparisonAmount(unit.minor)+' / '+unit.unit:''} · Fees unknown</Label>
    {selected.fresh&&item.mrpMinor!=null&&<Label muted style={{fontSize:12}}>MRP {comparisonAmount(item.mrpMinor)}{item.discountPercent!=null?' · '+item.discountPercent+'% off':''}</Label>}
   </View>
   <Button title={added===selected.retailer?'Add another pack':'Add to Cart'} onPress={()=>void add()} disabled={!sourceJob||!canBuy(selected)||busy} loading={busy}/>
  </View>
 </View>;
}
export function LiveProductList({rows,job,done,retailers,cachedJobs}:{cachedJobs:Partial<Record<RetailerLookup,string>>;rows:Row[];job:string|null;done:boolean;retailers:readonly RetailerLookup[]}){
 const all=productGroups(rows,Date.now());
 const related=relatedFruitOffers(all);
 const groups=all.map((offers,i)=>({offers,related:related[i]!}));
 return <View style={{gap:12}}>
  <View style={[ui.row,{gap:8}]}><ShoppingBag size={20} color={C.green}/><Heading style={{fontSize:20}}>Product comparison</Heading>{groups.length>0&&<Label muted>{groups.length} packs</Label>}</View>
  {!all.length&&<Label muted>{!job?'Confirm a search to see retailer offers for your saved location.':!done?'Waiting for retailer responses…':rows.some(r=>r.result)&&rows.every(r=>r.outcome==='NO_RESULTS')?'No matching products returned.':'No products available from the returned responses. Check the retailer statuses above.'}</Label>}
  {groups.map(({offers,related})=><Card key={offers[0]!.retailer+'|'+offers[0]!.item.nativeId} cachedJobs={cachedJobs} retailers={retailers} offers={offers} related={related} rows={rows} job={job} done={done}/>)}
 </View>;
}
