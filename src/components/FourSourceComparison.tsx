import React,{useEffect,useState} from 'react';
import {Pressable,View} from 'react-native';
import {ComparisonOption,ComparisonResult,Product} from '../types';
import {api,DEMO} from '../services/api';
import {C} from '../theme';
import {Button,Field,Heading,Label,ui} from './ui';
import {comparisonAmount,comparisonLabel,recommendationText,validCoordinates} from '../services/four-source';
import {useStore} from '../store';
import {RetailerComparison} from './RetailerComparison';
import {shoppingKey} from '../services/shopping-location';

type Props={product:Product;locationId:string;locationName:string;offline:boolean;addToCart:(product:Product)=>void};
export function FourSourceComparison({product,locationId,locationName,offline,addToCart}:Props){
 const [variants,setVariants]=useState<Product[]>([product]),[selected,setSelected]=useState(product);
 const [latitude,setLatitude]=useState(''),[longitude,setLongitude]=useState(''),[radius,setRadius]=useState('5');
 const [location,setLocation]=useState<{latitude:number;longitude:number;radiusKm:number}|null>(null);
 const [quantity,setQuantity]=useState(1),[fulfillment,setFulfillment]=useState<'pickup'|'delivery'>('delivery');
 const [result,setResult]=useState<ComparisonResult|null>(null),[error,setError]=useState(''),[inputError,setInputError]=useState(''),[busy,setBusy]=useState(false),[retry,setRetry]=useState(0),[variantError,setVariantError]=useState('');
 const {addBest,user,shoppingLocation}=useStore(),[adding,setAdding]=useState(false);
 const retailerQuery=[selected.brand,selected.name,selected.quantity].filter(Boolean).join(' ').slice(0,150);
 useEffect(()=>{setSelected(product);setVariants([product]);setResult(null);setVariantError('');if(DEMO||offline)return;let active=true;
  api.variants(locationId,product.id).then(items=>{if(active&&items.length)setVariants(items);}).catch(()=>{if(active)setVariantError('Pack sizes could not be loaded. The current pack is still available.');});
  return()=>{active=false;};
 },[product.id,locationId,offline]);
 useEffect(()=>{
  setLatitude(shoppingLocation?String(shoppingLocation.latitude):'');
  setLongitude(shoppingLocation?String(shoppingLocation.longitude):'');setRadius('5');
  setLocation(shoppingLocation?{latitude:shoppingLocation.latitude,longitude:shoppingLocation.longitude,radiusKm:5}:null);
  setResult(null);
 },[locationId,shoppingLocation]);
 useEffect(()=>{if(!location||DEMO||offline){setResult(null);setError('');setBusy(false);return;}let active=true;
  setBusy(true);setResult(null);setError('');
  const timer=setTimeout(()=>{api.compare(selected.id,{locationId,...location,quantity,fulfillment}).then(value=>{if(active)setResult(value);}).catch(e=>{if(active)setError(e instanceof Error?e.message:'Could not load the comparison.');}).finally(()=>{if(active)setBusy(false);});},300);
  return()=>{active=false;clearTimeout(timer);};
 },[selected.id,locationId,location,quantity,fulfillment,retry,offline]);
 function applyLocation(){const value=validCoordinates(latitude,longitude,radius);if(!value){setInputError('Enter valid latitude, longitude and a radius greater than 0 and at most 100 km.');return;}setInputError('');setLocation(old=>old&&old.latitude===value.latitude&&old.longitude===value.longitude&&old.radiusKm===value.radiusKm?old:value);}
 const winner=result?.lowestEligible;
 return <View style={{marginTop:28,gap:12}}>
  <Heading style={{fontSize:20}}>Compare your options</Heading>
  <Label muted>Compare the selected pack in {locationName}. Enter coordinates to include nearby shops. Online and store prices depend on available, verified data.</Label>
  {DEMO||offline?<><Label muted>Four-source comparison requires a connection to the CartIQ backend. Sample prices are not live.</Label>{product.offers.map((offer,index)=><View key={offer.platform+'-'+index} style={[ui.card,{gap:4}]}><Label style={{fontWeight:'700'}}>{offer.platform==='instamart'?'Swiggy Instamart':offer.platform==='blinkit'?'Blinkit':'Zepto'}</Label><Label>Sample item price: {offer.price==null?'Unknown':comparisonAmount(Math.round(offer.price*100))}</Label><Label muted>Sample only · {offer.status.replace(/_/g,' ')} · No confirmed total</Label></View>)}</>:<>
   <Label style={{fontWeight:'700'}}>Choose a pack</Label>
   <View style={[ui.row,{flexWrap:'wrap',gap:8}]}>{variants.map(v=><Pressable key={v.id} accessibilityRole="radio" accessibilityState={{selected:selected.id===v.id}} onPress={()=>setSelected(v)} style={[ui.pill,{borderWidth:1,borderColor:selected.id===v.id?C.green:C.line}]}><Label>{v.quantity}</Label></Pressable>)}</View>
   {!!variantError&&<Label muted>{variantError}</Label>}
   <Field label="Latitude" value={latitude} onChangeText={setLatitude} keyboardType="default" placeholder="For example, 12.97"/>
   <Field label="Longitude" value={longitude} onChangeText={setLongitude} keyboardType="default" placeholder="For example, 77.64"/>
   <Field label="Nearby radius (km)" value={radius} onChangeText={setRadius} keyboardType="numeric"/>
   {location&&<Label muted>Using {location.latitude}, {location.longitude} within {location.radiusKm} km. Apply changes to update the comparison.</Label>}
   {!!inputError&&<Label style={{color:C.red}}>{inputError}</Label>}
   <Button title="Use these coordinates" secondary onPress={applyLocation}/>
   <View style={[ui.row,{gap:10,flexWrap:'wrap'}]}><Label style={{fontWeight:'700'}}>Packs: {quantity}</Label><Button title="−" secondary disabled={quantity<=1} onPress={()=>setQuantity(q=>Math.max(1,q-1))}/><Button title="+" secondary disabled={quantity>=99} onPress={()=>setQuantity(q=>Math.min(99,q+1))}/></View>
   <View style={[ui.row,{gap:10}]}><Button title={fulfillment==='pickup'?'✓ Pickup':'Pickup'} secondary={fulfillment!=='pickup'} onPress={()=>setFulfillment('pickup')}/><Button title={fulfillment==='delivery'?'✓ Delivery':'Delivery'} secondary={fulfillment!=='delivery'} onPress={()=>setFulfillment('delivery')}/></View>
   {!location&&<Label muted>Enter your coordinates and select “Use these coordinates” to compare.</Label>}
   {busy&&<Label>Loading comparison…</Label>}
   {!!error&&<View style={{gap:8}}><Label style={{color:C.red}}>{error}</Label><Button title="Retry comparison" secondary onPress={()=>setRetry(n=>n+1)}/></View>}
   {result&&<><View style={[ui.card,{borderColor:winner?C.green:C.line}]}><Label style={{fontWeight:'700',color:winner?C.green:C.text}}>{recommendationText(result)}</Label><Label muted>{result.explanation}</Label>{winner&&<Label>Total for {result.quantity} pack(s): {comparisonAmount(winner.totalMinor)}</Label>}</View>
    {result.options.length===0?<Label muted>No comparison options were returned for this location.</Label>:result.options.map((option:ComparisonOption)=><View key={option.source+':'+option.sourceId} style={[ui.card,{gap:5,borderColor:winner?.source===option.source&&winner?.sourceId===option.sourceId?C.green:C.line}]}>
     <Label style={{fontWeight:'700',fontSize:16}}>{comparisonLabel(option)}</Label><Label>{result.product.brand} · {result.product.name} · {result.product.quantity}</Label>
     <Label>Pack price: {comparisonAmount(option.priceMinor)}{option.unitPriceMinor!==null?' · '+comparisonAmount(option.unitPriceMinor)+' / '+option.unitLabel:''}</Label>
     <Label>Availability: {option.availability.replace(/_/g,' ')} · Stock: {option.stockQuantity===null?'Unknown':option.stockQuantity}</Label>
     {option.distanceKm!==null&&<Label>Distance: {option.distanceKm} km</Label>}
     <Label>Fulfillment: {result.fulfillment} · Charge: {comparisonAmount(option.deliveryMinor)} · Handling: {comparisonAmount(option.handlingMinor)}</Label>
     <Label>Minimum order: {comparisonAmount(option.minimumMinor)} · Total: {comparisonAmount(option.totalMinor)}</Label>
     <Label style={{color:option.eligible?C.green:C.red,fontWeight:'700'}}>{option.eligible?'Eligible':'Not eligible'} · {option.reason}</Label>
     <Label muted>Source: {option.dataSource||'Unavailable'} · Verification: {option.verificationStatus||'Unknown'} · Observed: {option.observedAt?new Date(option.observedAt).toLocaleString():'Unknown'}</Label>
    </View>)}
    {winner&&<Button title="Add Best Option to Cart" loading={adding} onPress={()=>{if(!location||!result)return;setAdding(true);setError('');void addBest(selected,{locationId,...location,quantity,fulfillment},result).catch(e=>{setResult(null);setError(e instanceof Error?e.message:'Could not add this option.');}).finally(()=>setAdding(false));}}/>}
   </>}
   {user&&shoppingLocation&&<RetailerComparison key={shoppingKey(retailerQuery,shoppingLocation,user.id)}
    query={retailerQuery} location={shoppingLocation} admin={user.role==='admin'} privateSearch/>}
  </>}
 </View>;
}

