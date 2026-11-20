import React,{useState} from 'react';
import {Image,Pressable,View,ScrollView} from 'react-native';
import {ChevronRight,LayoutGrid} from 'lucide-react-native';
import {C} from '../theme';
import {Label} from './ui';
import {departmentSearch,type GroceryCategory} from '../services/grocery-categories';
import {departmentPhotos} from './DepartmentGrid';
export function DepartmentRail({select,current,compact}:{select:(q:string)=>void;current:string;compact:boolean}){
 const [expanded,setExpanded]=useState(false);
 return <View style={{width:compact?'100%':218,backgroundColor:C.surface,borderRightWidth:compact?0:1,borderColor:C.line,padding:compact?10:16,gap:10}}>
  <Pressable accessibilityRole={compact?'button':undefined} disabled={!compact} onPress={()=>setExpanded(!expanded)} style={{flexDirection:'row',gap:9,alignItems:'center',padding:5}}>
   <LayoutGrid size={17} color={C.green}/><Label style={{fontWeight:'700'}}>Grocery categories</Label>{compact&&<ChevronRight size={16} color={C.green}/>}
  </Pressable>
  {(!compact||expanded)&&<ScrollView horizontal={compact} contentContainerStyle={{gap:3}}>
   {(Object.keys(departmentSearch) as GroceryCategory[]).map(name=><Pressable key={name} accessibilityRole="button" onPress={()=>{select(departmentSearch[name]);setExpanded(false);}}
    style={{flexDirection:'row',alignItems:'center',gap:10,padding:6,borderRadius:10,backgroundColor:current===departmentSearch[name]?C.mint:'transparent'}}>
    <Image source={departmentPhotos[name]} style={{width:34,height:34,borderRadius:8}}/><Label style={{fontSize:13,fontWeight:current===departmentSearch[name]?'700':'500'}}>{name}</Label>
   </Pressable>)}
  </ScrollView>}
 </View>;
}
