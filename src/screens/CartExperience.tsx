import {marketplace} from '../services/marketplace';
import {SelectedCartItem} from '../components/SelectedCartItem';
import {CheckoutPreview} from './CheckoutPreview';
import {Product} from '../types';
import React,{useEffect,useRef,useState} from 'react';
import {LayoutAnimation,Pressable,Text,View} from 'react-native';
import {Minus,Plus,Trash2} from 'lucide-react-native';
import {CartInsights} from './CartInsights';
import {useStore} from '../store';
import {cartLines,groupCart} from '../services/cart';
import {freshness} from '../services/comparison';
import {platforms} from '../data/demo';
import {PlatformId} from '../types';
import {C,money} from '../theme';
import {Button,Empty,Heading,IconButton,Label,ui} from '../components/ui';
import {ProductArt} from '../components/ProductArt';
import {RetailerLink} from '../components/RetailerLink';
import {LivePackOffers} from '../components/LivePackOffers';
export function Cart({browse,openProduct}:{browse:()=>void;openProduct?:(p:Product)=>void}){
 const {liveCart,toast,user,shoppingLocation,cart,products,location,setCartQuantity,clearCart,refresh,loading,error,demo,cartMode,setCartMode,selectedStore,setSelectedStore,editCartItem,cartReady,cartError,refreshCart}=useStore();
 const [preview,setPreview]=useState(false);const [confirmClear,setConfirmClear]=useState(false),[changed,setChanged]=useState(false);
 const [evidencePack,setEvidencePack]=useState<string|null>(null);
 const lines=cartLines(cart,products,location.id,cartMode,selectedStore),groups=groupCart(lines),unpriced=lines.filter(l=>!l.best&&!l.entry.listing);
 const scope=JSON.stringify([user?.id,location.id,shoppingLocation,cart.filter(x=>x.listing).map(x=>[x.id,x.listing,x.quantity])]);
 const [quoteView,setQuoteView]=useState<{scope:string;rows:Awaited<ReturnType<typeof marketplace.cartQuotes>>;error?:string}|null>(null);
 const [revision,reprice]=useState(0),[,clock]=useState(0);
 useEffect(()=>{
  if(!user||!shoppingLocation||!cart.some(line=>line.listing&&line.locationId===location.id))return;
  const control=new AbortController();setQuoteView(null);
  void marketplace.cartQuotes(location.id,shoppingLocation,control.signal).then(rows=>{
   if(!control.signal.aborted)setQuoteView({scope,rows});
  }).catch(e=>{if(!control.signal.aborted)setQuoteView({scope,rows:[],error:e.message});});
  return()=>control.abort();
 },[scope,revision]);
 useEffect(()=>{
  const timers=(quoteView?.rows??[]).map(row=>Date.parse(row.freshUntil)-Date.now()).filter(ms=>ms>0).map(ms=>setTimeout(()=>clock(n=>n+1),ms+10));
  return()=>timers.forEach(clearTimeout);
 },[quoteView]);
 const quotes=quoteView?.scope===scope?quoteView.rows.filter(row=>Date.parse(row.freshUntil)>Date.now()):[];
 const selected=cart.filter(item=>item.locationId===location.id&&item.selection);
 const subtotal=lines.reduce((n,l)=>n+l.total,0),saving=lines.reduce((n,l)=>n+l.saving,0),discount=lines.reduce((n,l)=>n+l.discount,0),premium=lines.reduce((n,l)=>n+l.premium,0);
 const retailerSubtotal=quotes.reduce((total,q)=>total+q.priceMinor*(cart.find(i=>i.id===q.itemId)?.quantity??0),0)/100;
 const missingPrices=unpriced.length+lines.filter(line=>line.entry.listing&&!quotes.some(q=>q.itemId===line.entry.id)).length;
 const pricedSubtotal=groups.length||quotes.length?money(subtotal+retailerSubtotal):'Unknown';
 const signature=JSON.stringify(lines.map(l=>[l.entry.productId,l.best?.platform,l.best?.price]));const key=`${location.id}-${cartMode}-${selectedStore}`,ids=lines.map(l=>l.entry.productId).join(',');const previous=useRef({key,signature,ids});
 useEffect(()=>{if(previous.current.key===key&&previous.current.ids===ids&&previous.current.signature!==signature)setChanged(true);else if(previous.current.key!==key)setChanged(false);previous.current={key,signature,ids};},[signature,key,ids]);
 function quantity(id:string,value:number){LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);setCartQuantity(id,value);}
 if(!cartReady)return <Label muted>Loading your cart…</Label>;
 if(cartError)return <Empty title="Could not load your cart" body={cartError} action="Try again" onPress={()=>{void refreshCart();}}/>;
 const liveSection=<View style={[ui.card,{gap:10,marginBottom:16}]}>
  <Heading>Retailer cart</Heading><Label muted>{shoppingLocation?.label} · {shoppingLocation?.postcode} · Private session cart</Label>
  {liveCart.lines.map(item=><View key={item.id} style={{borderTopWidth:1,borderColor:C.line,paddingVertical:10,gap:4}}>
    <Label style={{fontWeight:'700'}}>{item.title}</Label><Label>{item.pack} · {item.retailer==='instamart'?'Swiggy Instamart':item.retailer==='blinkit'?'Blinkit':'Zepto'} · {item.quantity} pack(s)</Label>
    <Label>{Date.parse(item.freshUntil)>Date.now()?money(item.priceMinor/100)+' each · fetched '+new Date(item.fetchedAt).toLocaleTimeString():'Offer expired · refresh before using this price'}</Label>
    <View style={ui.row}>{[[-1,'Decrease'],[1,'Increase'],[0,'Remove']] .map(([delta,title])=><Pressable accessibilityRole="button" key={title} onPress={()=>void liveCart.quantity(item.id,delta===0?0:item.quantity+Number(delta)).catch(e=>toast(e.message))} style={{padding:10}}><Label>{title}</Label></Pressable>)}</View>
  </View>)}
  <Label muted>Session items expire after 30 minutes of cart inactivity. Fees are unknown.</Label>
 </View>;
 if(!lines.length&&!selected.length&&liveCart.lines.length)return liveSection;
 if(!lines.length&&!selected.length&&liveCart.error)return <Label>{liveCart.error}</Label>;
 if(!lines.length&&!selected.length)return <Empty title="Your smarter basket starts here" body="Add products from search, comparisons or saved finds. Your cart is kept separately for each delivery location." action="Find essentials" onPress={browse}/>;
 return <View><View style={[ui.row,{justifyContent:'space-between'}]}><View><Label style={ui.eyebrow}>GOOD FINDS, ALL TOGETHER</Label><Heading style={{marginTop:7}}>Your cart</Heading></View><IconButton label="Clear cart for this location" onPress={()=>setConfirmClear(true)}><Trash2 size={20} color={C.muted}/></IconButton></View><Label muted style={{marginTop:8,marginBottom:22}}>{location.name} · {lines.reduce((n,l)=>n+l.entry.quantity,0)+selected.reduce((n,item)=>n+item.quantity,0)} items</Label>
 {liveCart.lines.length>0&&liveSection}
 {cart.some(i=>i.listing&&i.locationId===location.id)&&<View style={[ui.card,{gap:8}]}>
  <Heading style={{fontSize:18}}>Selected retailer listings</Heading>
  {cart.filter(i=>i.listing&&i.locationId===location.id).map(i=>{
   const quote=quotes.find(row=>row.itemId===i.id);
   return <View key={i.id} style={{gap:4}}><Label>{products.find(p=>p.id===i.productId)?.name??i.productId} · {i.listing!.retailer} · {i.listing!.nativeId}</Label>
    <Label muted>{quote?money(quote.priceMinor/100)+' each · '+i.quantity+' packs · fetched '+new Date(quote.fetchedAt).toLocaleString():'Price unavailable or expired for this location and quantity.'}</Label>
    <View style={[ui.row,{gap:8}]}>
     <Button secondary title="Decrease packs" onPress={()=>quantity(i.productId,i.quantity-1)}/>
     <Label>{i.quantity}</Label>
     <Button secondary title="Increase packs" disabled={i.quantity>=99} onPress={()=>quantity(i.productId,i.quantity+1)}/>
     <Button secondary title="Remove listing" onPress={()=>quantity(i.productId,0)}/>
    </View>
    <Button secondary title="Review exact pack and refresh options" onPress={()=>setEvidencePack(i.productId)}/></View>;
  })}
  {quotes.length>0&&<Label>Priced retailer items before fees: {money(retailerSubtotal)}</Label>}
  {quoteView?.scope===scope&&!!quoteView.error&&<Label muted>{quoteView.error}</Label>}
  <Button secondary title="Recheck current cart offers" onPress={()=>reprice(n=>n+1)}/>
 </View>}
 <CartInsights/>{confirmClear&&<View style={[ui.card,{gap:12,marginBottom:18}]}><Heading style={{fontSize:18}}>Clear this location’s cart?</Heading><Label muted>Other locations’ carts will be kept.</Label><Button title="Clear these items" onPress={()=>{clearCart();setConfirmClear(false);}}/><Button title="Keep shopping" secondary onPress={()=>setConfirmClear(false)}/></View>}
 {preview&&<CheckoutPreview back={()=>setPreview(false)}/>}{selected.map(item=><SelectedCartItem key={item.productId} item={item} product={products.find(p=>p.id===item.productId)} openProduct={openProduct}/>)}
 {selected.length>0&&<View style={[ui.card,{gap:10,marginBottom:16}]}><Heading style={{fontSize:20}}>Selected-source review</Heading><Label>Recorded item subtotal: {money(selected.reduce((sum,item)=>sum+item.selection!.priceMinor*item.quantity,0)/100)}</Label><Label muted>Combined delivery, handling, minimums and final cart total require a basket quote. Individual comparison fees cannot safely be combined as a confirmed basket total.</Label><Button title="Preview checkout journey" secondary onPress={()=>setPreview(true)}/></View>}
 <View style={[ui.row,{backgroundColor:C.mint,borderRadius:16,padding:5,gap:5,marginBottom:16}]}>{(['best','single'] as const).map(mode=><Pressable key={mode} accessibilityRole="tab" accessibilityState={{selected:cartMode===mode}} onPress={()=>{LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);setCartMode(mode);}} style={{flex:1,paddingVertical:13,borderRadius:12,alignItems:'center',backgroundColor:cartMode===mode?C.paper:'transparent'}}><Label style={{fontWeight:'700',color:C.green}}>{mode==='best'?'Best price':'Single store'}</Label></Pressable>)}</View>
 <Label muted style={{fontSize:12,marginBottom:14}}>{cartMode==='best'?'Lowest fresh item prices. Multiple stores mean separate orders and deliveries.':'Choose one store. Unavailable products stay in your cart and are excluded from that store’s order.'}</Label>
 <View style={[ui.row,{gap:8,marginBottom:22}]}>{(['blinkit','instamart','zepto'] as PlatformId[]).map(id=>{
  const rows=cartLines(cart,products,location.id,'single',id),available=rows.filter(line=>line.best);
  const total=available.length?money(available.reduce((sum,line)=>sum+line.total,0)):'No priced items';
  return <Pressable key={id} accessibilityRole="button" accessibilityLabel={'Select '+platforms[id].name}
   accessibilityState={{selected:cartMode==='single'&&selectedStore===id}} onPress={()=>{setCartMode('single');setSelectedStore(id);}}
   style={[ui.card,{flex:1,minWidth:0,padding:12,borderColor:cartMode==='single'&&selectedStore===id?C.green:C.line}]}>
   <Text style={{fontWeight:'800',fontSize:14,color:platforms[id].color,backgroundColor:platforms[id].background,paddingHorizontal:7,paddingVertical:4,borderRadius:6,alignSelf:'flex-start'}}>{platforms[id].name}</Text>
   <Label style={{fontWeight:'700',marginTop:9}}>{total}</Label>
   <Label muted style={{fontSize:10,lineHeight:15}}>{available.length}/{rows.length} items priced{available.length?' · before fees':''}</Label>
  </Pressable>;
 })}</View>
 {changed&&<View accessibilityRole="alert" style={[ui.card,{backgroundColor:C.orangeLight,marginBottom:18,gap:8}]}><Label style={{fontWeight:'700'}}>Your prices or selected stores changed</Label><Label muted style={{fontSize:12}}>A previous lowest price may no longer be available. Review these options before checkout.</Label><Button secondary title="I’ve reviewed the updates" onPress={()=>setChanged(false)}/></View>}
 {groups.map(group=><View key={group.platform} style={{marginBottom:24}}><View style={[ui.row,{justifyContent:'space-between',marginBottom:12}]}><Text style={{fontSize:20,fontWeight:'800',color:platforms[group.platform].color,backgroundColor:platforms[group.platform].background,paddingHorizontal:10,paddingVertical:5,borderRadius:8}}>{platforms[group.platform].name}</Text><Label style={{fontWeight:'700'}}>{money(group.subtotal)}</Label></View>{group.lines.map(l=><View key={l.entry.productId} style={[ui.card,{marginBottom:10,padding:14}]}><View style={[ui.row,{gap:10}]}>{l.product&&<ProductArt product={l.product} size={58}/>}<View style={{flex:1}}><Label style={{fontWeight:'700'}}>{l.product?.name}</Label><Label muted style={{fontSize:12}}>{l.product?.quantity} · {money(l.best!.price!)} each</Label><Pressable accessibilityRole="button" onPress={()=>{if(l.product)editCartItem(l.product);}}><Label style={{fontSize:11,color:C.green,textDecorationLine:'underline'}}>{l.entry.preferences?.intent==='BULK'?'Planned / bulk':'Normal'} · Edit purchase preferences</Label></Pressable><Label style={{color:C.green,fontSize:11,marginTop:4}}>{l.best!.price===l.lowest?.price?'Lowest available price':`${money(l.premium/l.entry.quantity)} above lowest, per item`}</Label><Label muted style={{fontSize:10}}>{demo?'Sample · ':''}{freshness(l.best!)}</Label></View><IconButton label={`Remove ${l.product?.name}`} onPress={()=>quantity(l.entry.productId,0)}><Trash2 size={17} color={C.muted}/></IconButton></View><View style={[ui.row,{justifyContent:'space-between',marginTop:12}]}><View style={[ui.row,{borderWidth:1,borderColor:C.line,borderRadius:12}]}><IconButton label={`Decrease ${l.product?.name} quantity`} onPress={()=>quantity(l.entry.productId,l.entry.quantity-1)}><Minus size={16} color={C.green}/></IconButton><Label style={{minWidth:22,textAlign:'center',fontWeight:'700'}}>{l.entry.quantity}</Label><IconButton label={`Increase ${l.product?.name} quantity`} onPress={()=>quantity(l.entry.productId,Math.min(99,l.entry.quantity+1))}><Plus size={16} color={C.green}/></IconButton></View><Label style={{fontWeight:'800',fontSize:18}}>{money(l.total)}</Label></View><View style={{marginTop:12}}><RetailerLink platform={group.platform} offer={l.best!} secondary disabled={loading||!!error}/></View></View>)}</View>)}
 {unpriced.length>0&&<View style={[ui.card,{marginBottom:22,backgroundColor:C.orangeLight}]}><Heading style={{fontSize:18}}>Excluded from checkout · {unpriced.length}</Heading><Label muted style={{fontSize:12,marginTop:8}}>No fresh available price{cartMode==='single'?` on ${platforms[selectedStore].name}`:''}. Refresh, choose another store, or remove these items.</Label>{unpriced.map(l=><View key={l.entry.productId} style={[ui.row,{justifyContent:'space-between',marginTop:12}]}><Label style={{flex:1}}>{l.product?.name||l.entry.productId}<Label muted>{l.product?.quantity?' · '+l.product.quantity:''}{l.entry.listing?' · '+l.entry.listing.retailer+' · listing '+l.entry.listing.nativeId+' · price requires revalidation':''}</Label></Label><View style={[ui.row,{gap:4}]}><IconButton label={`Decrease ${l.product?.name||l.entry.productId} quantity`} onPress={()=>quantity(l.entry.productId,l.entry.quantity-1)}><Minus size={16} color={C.green}/></IconButton><Label>{l.entry.quantity}</Label><IconButton label={`Increase ${l.product?.name||l.entry.productId} quantity`} onPress={()=>quantity(l.entry.productId,Math.min(99,l.entry.quantity+1))}><Plus size={16} color={C.green}/></IconButton></View><IconButton label={`Remove ${l.product?.name||l.entry.productId}`} onPress={()=>quantity(l.entry.productId,0)}><Trash2 size={18} color={C.red}/></IconButton></View>)}</View>}
 {!demo&&lines.length>0&&<View style={[ui.card,{gap:8,marginBottom:16}]}>
  <Heading style={{fontSize:18}}>Last retrieved offer evidence</Heading>
  <Label muted>Cart quantities are saved. Retailer prices expire and require confirmation before purchase.</Label>
  {lines.map(line=><Button key={line.entry.productId} title={line.product?.name??line.entry.productId} secondary
   onPress={()=>setEvidencePack(current=>current===line.entry.productId?null:line.entry.productId)}/>)}
  {evidencePack&&lines.some(line=>line.entry.productId===evidencePack)&&<LivePackOffers variant={evidencePack} onChange={()=>reprice(n=>n+1)}/>}
 </View>}
 <View style={[ui.card,{gap:14}]}><Heading style={{fontSize:21}}>Cart summary</Heading>{selected.length>0&&<Label muted>Recorded comparison selections are shown separately above and are not included in the online item totals below.</Label>}<Summary label="Stores / separate orders" value={String(groups.length)}/><Summary label={missingPrices?'Priced items subtotal':'Item subtotal'} value={pricedSubtotal}/><Summary label="MRP discounts already included" value={groups.length?money(discount):'Unknown'}/><Summary label="Delivery & handling" value="Not provided yet"/><Summary label={missingPrices?'Partial items total, before fees':'Estimated items total, before fees'} value={pricedSubtotal}/>{cartMode==='single'&&premium>0?<Label style={{color:C.orange,fontWeight:'600'}}>{money(premium)} above best-price mode for these available items</Label>:saving>0&&<Label style={{color:C.green,fontWeight:'600'}}>Save {money(saving)} versus the highest comparable item prices</Label>}<Label muted style={{fontSize:12}}>Final fees and offers need retailer confirmation. Splitting stores can increase delivery costs. Excluded items are not included in these totals.</Label>{error&&<Label style={{color:C.red}}>{error}</Label>}<Button title="Refresh prices" loading={loading} secondary onPress={()=>{refresh();}}/><Button title="Preview checkout journey" secondary onPress={()=>setPreview(true)}/><Button title="Continue comparing" onPress={browse}/></View></View>;
}
export function Summary({label,value}:{label:string;value:string}){return <View style={[ui.row,{justifyContent:'space-between',gap:12}]}><Label muted style={{flex:1,fontSize:12}}>{label}</Label><Label style={{fontWeight:'700',fontSize:13,textAlign:'right'}}>{value}</Label></View>;}
