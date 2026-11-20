import React,{useState} from 'react';
import {Image,Linking,Pressable,Text,View,useWindowDimensions} from 'react-native';
import {ArrowUpRight} from 'lucide-react-native';
import {C} from '../theme';
import {Heading,Label} from './ui';
import type {GroceryCategory} from '../services/grocery-categories';
import credits from '../../assets/departments/credits.json';

export const departmentPhotos:Record<GroceryCategory,number>={
 Fruits:require('../../assets/departments/fruit.jpg'), Vegetables:require('../../assets/departments/vegetables.jpg'),
 'Milk and Dairy':require('../../assets/departments/dairy.jpg'),'Rice and Grains':require('../../assets/departments/grains.jpg'),
 'Dals and Pulses':require('../../assets/departments/pulses.png'),'Atta and Flour':require('../../assets/departments/flour.jpg'),
 'Cooking Oil':require('../../assets/departments/oil.png'),Snacks:require('../../assets/departments/snacks.jpg'),
 'Biscuits and Bakery':require('../../assets/departments/biscuits.jpg'),Beverages:require('../../assets/departments/beverages.jpg'),
 'Breakfast and Cereals':require('../../assets/departments/oats.jpg'),'Personal Care':require('../../assets/departments/personal.jpg'),
 'Household Essentials':require('../../assets/departments/household.jpg'),'Baby Products':require('../../assets/departments/baby.jpg'),
 'Frozen Foods':require('../../assets/departments/frozen.jpg'),
};
export function DepartmentGrid({open}:{open:(category:GroceryCategory)=>void}){
 const {width}=useWindowDimensions(),[showCredits,setShowCredits]=useState(false);
 const columns=width>=1150?5:width>=620?3:2;
 const photoHeight=width>=1150?144:width>=620?126:104;
 return <View style={{gap:12,marginTop:12}}>
  <Heading>Shop by department</Heading>
  <View style={{flexDirection:'row',flexWrap:'wrap',marginHorizontal:-5}}>
   {(Object.keys(departmentPhotos) as GroceryCategory[]).map(name=><View key={name} style={{width:(100/columns+'%') as `${number}%`,padding:5}}>
    <Pressable accessibilityRole="button" accessibilityLabel={'Shop '+name} onPress={()=>open(name)}
     style={({pressed})=>({backgroundColor:C.paper,borderColor:C.line,borderWidth:1,borderRadius:16,overflow:'hidden',opacity:pressed?0.78:1})}>
     <View style={{width:'100%',height:photoHeight,backgroundColor:C.surface,overflow:'hidden'}}>
      <Image source={departmentPhotos[name]} accessible={false} resizeMode="cover" style={{position:'absolute',width:'100%',height:'100%'}}/>
     </View>
     <View style={{flexDirection:'row',alignItems:'center',paddingHorizontal:12,height:56,gap:5}}>
      <Text numberOfLines={2} style={{flex:1,fontSize:width>=620?16:14,fontWeight:'700',color:C.text}}>{name}</Text>
      <ArrowUpRight size={16} color={C.green}/>
     </View>
    </Pressable>
   </View>)}
  </View>
  <Pressable accessibilityRole="button" accessibilityState={{expanded:showCredits}} onPress={()=>setShowCredits(!showCredits)}>
   <Label muted style={{fontSize:12}}>Department photo credits</Label>
  </Pressable>
  {showCredits&&credits.map(c=><View key={c.slug}>
   <Text accessibilityRole="link" onPress={()=>void Linking.openURL(c.url)} style={{color:C.green}}>{c.title} · Wikimedia Commons</Text>
   <Label muted>{c.author}</Label>
   <Text accessibilityRole={c.licenseUrl?'link':undefined} onPress={c.licenseUrl?()=>void Linking.openURL(c.licenseUrl):undefined} style={{color:C.muted}}>{c.license}</Text>
  </View>)}
 </View>;
}
