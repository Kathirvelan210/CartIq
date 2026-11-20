import React from 'react';
import {Pressable,View,useWindowDimensions} from 'react-native';
import {ChevronDown,MapPin} from 'lucide-react-native';
import {C} from '../theme';
import {Heading,Label,ui} from './ui';

export function GroceryLandingIntro({location,openLocation}:{location:string;openLocation:()=>void}){
 const narrow=useWindowDimensions().width<650;
 return <View style={{flexDirection:'row',alignItems:'center',justifyContent:'space-between',flexWrap:'wrap',gap:8,marginBottom:10}}>
  <Heading style={{fontSize:narrow?24:30}}>Shop groceries</Heading>
  <Pressable accessibilityRole="button" accessibilityLabel="Choose comparison location" onPress={openLocation}
   style={[ui.row,{gap:7,backgroundColor:C.mint,paddingHorizontal:14,paddingVertical:9,borderRadius:20,maxWidth:'100%'}]}>
   <MapPin size={17} color={C.green}/><Label style={{fontWeight:'700',flexShrink:1}}>{location}</Label><ChevronDown size={14} color={C.green}/>
  </Pressable>
 </View>;
}
