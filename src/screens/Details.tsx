import React,{useEffect,useState} from 'react';
import {Text,View} from 'react-native';
import {Heart} from 'lucide-react-native';
import {Product} from '../types';
import {C} from '../theme';
import {useStore} from '../store';
import {ProductArt} from '../components/ProductArt';
import {Back,Button,Empty,Heading,IconButton,Label,ui} from '../components/ui';
import {api} from '../services/api';
import {ProductIntelligence} from '../components/ProductIntelligence';
import {LivePackOffers} from '../components/LivePackOffers';
import {ProductMetadata} from '../components/ProductMetadata';
import {FourSourceComparison} from '../components/FourSourceComparison';
import {canonicalListing} from '../data/retailer-catalogue';
import {productPhoto} from '../data/product-photos';

export function Details({product,back}:{product:Product;back:()=>void}) {
 const {location,saved,toggleSave,demo,offline,addToCart,products}=useStore();
 const [retry,setRetry]=useState(0);
 const [detail,setDetail]=useState<{key:string; product?:Product; error?:string}|null>(null);
 const key=JSON.stringify([location.id,product.id,retry]);
 useEffect(()=>{
  if(offline||!location.id||product.retailerListing)return;
  const controller=new AbortController();
  void api.product(location.id,product.id,controller.signal)
   .then(value=>{if(!controller.signal.aborted)setDetail({key,product:value});})
   .catch(error=>{if(!controller.signal.aborted)setDetail({key,error:error.message});});
  return()=>controller.abort();
 },[offline,location.id,product.id,product.retailerListing,key]);
 const p=offline||product.retailerListing?product:detail?.key===key?detail.product:null;
 if(!p){
  const error=detail?.key===key?detail.error:null;
  return <View><Back onPress={back} title="A closer look"/>
   {error?<Empty title="Could not load product details" body={error} action="Try again" onPress={()=>setRetry(n=>n+1)}/>
    :<Label muted>{location.id?'Loading product details…':'Choose a delivery location to view this product.'}</Label>}
  </View>;
 }
 const canonical=canonicalListing(p,products);
 return <View><Back onPress={back} title="A closer look"/><View style={{backgroundColor:C.surface,borderRadius:28,minHeight:260,alignItems:'center',justifyContent:'center'}}><ProductArt product={p} size={195}/>{!p.retailerListing&&<IconButton label="Save product" onPress={()=>toggleSave(p.id)} style={{position:'absolute',right:16,top:16,backgroundColor:C.paper,borderRadius:50}}><Heart size={21} color={C.green} fill={saved.includes(p.id)?C.green:'transparent'}/></IconButton>}{!productPhoto(p,demo)&&<Text style={{position:'absolute',bottom:13,color:C.muted,fontSize:10}}>Illustrative packaging</Text>}</View>
 <Label style={[ui.eyebrow,{marginTop:25}]}>{p.brand.toUpperCase()} · {p.category.toUpperCase()}</Label><Heading style={{fontSize:29,marginTop:7}}>{p.name}</Heading><Label muted style={{marginTop:5}}>{p.quantity}</Label><Label style={{marginTop:15,lineHeight:23}}>{p.description}</Label>
 {!offline&&!p.retailerListing&&<ProductMetadata key={key} id={p.id} location={location.id}/>}
 {!canonical?<View style={{marginTop:22}}><Heading style={{fontSize:20}}>Compare your options</Heading><Label muted>Sample retailer listing. Comparison is pending a verified match for this pack.</Label></View>:<>
 <View style={{marginTop:20}}><Button secondary title="Add to planning cart" onPress={()=>{addToCart(canonical);}}/></View>
 <FourSourceComparison product={canonical} locationId={location.id} locationName={location.name} offline={offline} addToCart={addToCart}/>
 <LivePackOffers variant={canonical.id}/>
 <ProductIntelligence product={canonical}/></>}</View>;
}
