import React,{useState} from 'react';
import {Image,View,Text} from 'react-native';
import {Product} from '../types';
import {productPhoto} from '../data/product-photos';
import {useStore} from '../store';
export function ProductArt({product:p,size=110}:{product:Product;size?:number}) {
 const {demo} = useStore();
 const uri = productPhoto(p,demo);
 const [failedUri,setFailedUri] = useState<string>();
 if (uri && failedUri !== uri) {
  return <Image
   accessibilityLabel={`${p.brand} ${p.name}, ${p.quantity}`}
   accessible
   source={{uri}}
   resizeMode="contain"
   onError={() => setFailedUri(uri)}
   style={{width:size+18,height:size+18}}
  />;
 }
 if(!demo)return <View accessibilityLabel="Product image unavailable" style={{width:size+18,height:size+18,justifyContent:'center',alignItems:'center'}}>
  <Text style={{fontSize:12,textAlign:'center',color:'#56645e'}}>Image unavailable</Text>
 </View>;
 const bottle=p.art==='bottle',jar=p.art==='jar',box=p.art==='box',bag=p.art==='bag';
 return <View accessible accessibilityLabel={`${p.brand} ${p.name}, illustrated packaging`} style={{height:size+20,width:size+24,alignItems:'center',justifyContent:'center'}}><View style={{position:'absolute',bottom:1,width:size*.7,height:9,borderRadius:100,backgroundColor:'#233E2610',transform:[{scaleX:1.2}]}}/>{(bottle||jar)&&<View style={{width:size*(bottle?.24:.45),height:8,backgroundColor:p.color,borderTopLeftRadius:4,borderTopRightRadius:4}}/>}<View style={{width:size*(bottle?.48:jar?.65:box?.95:.67),height:size*(box?.62:.94),backgroundColor:p.color,borderRadius:bag?10:bottle?18:jar?10:4,borderTopLeftRadius:bag?5:bottle?20:4,borderTopRightRadius:bag?5:bottle?20:4,transform:[{rotate:bag?'-6deg':box?'4deg':'-3deg'}],overflow:'hidden',borderWidth:1,borderColor:'#0000000B',alignItems:'center',justifyContent:'center'}}>{p.art==='carton'&&<View style={{position:'absolute',top:0,left:0,right:0,height:14,backgroundColor:'#FFFFFF55'}}/>}<View style={{backgroundColor:'#FFFEF2',width:'100%',paddingVertical:size*.08,paddingHorizontal:3,alignItems:'center'}}><Text numberOfLines={1} style={{fontSize:size*.12,color:p.color,fontWeight:'900',fontStyle:'italic'}}>{p.brand}</Text><Text numberOfLines={2} style={{fontSize:size*.077,fontWeight:'700',color:'#314133',textAlign:'center',marginTop:4}}>{p.name.replace(p.brand,'').trim()}</Text></View><View style={{marginTop:size*.08,width:size*.22,height:size*.22,borderRadius:size,backgroundColor:'#FFFFFF55',alignItems:'center',justifyContent:'center'}}><Text style={{fontSize:size*.12,color:'white'}}>✦</Text></View><Text style={{fontSize:size*.065,color:'white',marginTop:5}}>{p.quantity}</Text></View></View>;
}
