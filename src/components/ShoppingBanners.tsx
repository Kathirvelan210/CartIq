import React,{useState} from 'react';
import {Image,Linking,Pressable,ScrollView,Text,View,useWindowDimensions} from 'react-native';
import {advertisements,Advertisement} from '../data/advertisements';
function AdvertisementCard({ad,width}:{ad:Advertisement;width:number}){
 const [failed,setFailed]=useState(false),[error,setError]=useState('');
 const enabled=!ad.sample&&!!ad.destinationUrl&&/^https:///i.test(ad.destinationUrl)&&!!ad.imageUrl&&!failed;
 return <View style={{width,height:245,borderRadius:24,backgroundColor:ad.background,overflow:'hidden'}}>
 {ad.imageUrl&&!failed?<Image source={{uri:ad.imageUrl}} accessibilityLabel={ad.title} resizeMode="cover" onError={()=>setFailed(true)} style={{position:'absolute',width:'100%',height:'100%'}}/>:<View style={{padding:24,paddingTop:38,gap:12}}><Text style={{color:ad.textColor,fontWeight:'800',fontSize:27,lineHeight:32,maxWidth:'88%'}}>{ad.title}</Text><Text style={{color:ad.textColor,fontSize:14}}>{failed?'Advertisement image unavailable':ad.subtitle}</Text><Text style={{color:ad.textColor,fontSize:12,opacity:.8}}>Advertiser artwork goes here</Text></View>}
 <View style={{position:'absolute',top:10,right:12,paddingHorizontal:8,paddingVertical:4,borderRadius:6,backgroundColor:'#FFFFFFE8'}}><Text style={{fontSize:10,color:'#344451'}}>{ad.sample?'Ad · Sample placement':'Ad'}</Text></View>
 <Pressable accessibilityRole="button" accessibilityState={{disabled:!enabled}} disabled={!enabled} onPress={()=>{if(enabled)void Linking.openURL(ad.destinationUrl!).catch(()=>setError('Unable to open advertiser link.'));}} style={{position:'absolute',left:24,bottom:22,paddingHorizontal:18,paddingVertical:12,borderRadius:10,backgroundColor:'#FFFFFF'}}><Text style={{fontWeight:'700',fontSize:14,color:'#253748'}}>{ad.buttonLabel}</Text></Pressable>{!!error&&<Text accessibilityRole="alert" style={{position:'absolute',bottom:4,left:24,color:ad.textColor,fontSize:11}}>{error}</Text>}
 </View>;
}
export function ShoppingBanners(){const {width}=useWindowDimensions();return <ScrollView horizontal showsHorizontalScrollIndicator={false} accessibilityLabel="Advertisements" contentContainerStyle={{gap:24,paddingVertical:12}}>{advertisements.map(ad=><AdvertisementCard key={ad.id} ad={ad} width={width<650?290:width>=1500?440:360}/>)}</ScrollView>;}
