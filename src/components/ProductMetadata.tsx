import React, {useEffect, useState} from 'react';
import {Linking, Pressable, View} from 'react-native';
import {api} from '../services/api';
import {CatalogueMetadata} from '../types';
import {C} from '../theme';
import {Button, Heading, Label, ui} from './ui';

const nutrients: Record<string,[string,string]> = {
 'energy-kcal':['Energy','kcal'], 'energy-kj':['Energy','kJ'], fat:['Fat','g'],
 'saturated-fat':['Saturated fat','g'], carbohydrates:['Carbohydrates','g'], sugars:['Sugars','g'],
 fiber:['Fibre','g'], proteins:['Protein','g'], salt:['Salt','g'], sodium:['Sodium','g'],
};

export function ProductMetadata({id,location}: {id:string;location:string}) {
 const [data,setData]=useState<CatalogueMetadata>();
 const [error,setError]=useState('');
 const [retry,setRetry]=useState(0);
 useEffect(()=>{
  const controller=new AbortController();
  setData(undefined);setError('');
  void api.metadata(location,id,controller.signal)
   .then(value=>{if(!controller.signal.aborted)setData(value);})
   .catch(e=>{if(!controller.signal.aborted)setError(e.message);});
  return ()=>controller.abort();
 },[id,location,retry]);
 if(error)return <View style={{marginTop:20,gap:6}}>
  <Label muted>Product label information could not be loaded.</Label>
  <Button title="Retry product label" secondary onPress={()=>setRetry(n=>n+1)}/>
 </View>;
 if(!data)return <Label muted style={{marginTop:20}}>Loading product label…</Label>;
 if(!data.gtins.length&&!data.sources.length)return null;
 return <View style={[ui.card,{marginTop:20,gap:10}]}>
  <Heading style={{fontSize:20}}>Product label</Heading>
  {data.gtins.length>0&&<Label>GTIN {data.gtins.join(', ')}</Label>}
  {data.sources.map(source=><View key={source.sourceUrl} style={{gap:6}}>
   <Label muted>Community-contributed label data. Check the pack for current ingredients and allergens.</Label>
   {source.ingredients&&<><Label style={{fontWeight:'700'}}>Ingredients</Label><Label>{source.ingredients}</Label></>}
   {Object.keys(source.nutritionPer100).length>0&&<Label style={{fontWeight:'700'}}>Nutrition per {source.nutritionBasis==='100ml'?'100 ml':'100 g'}</Label>}
   {Object.entries(source.nutritionPer100).map(([key,value])=>{
    const nutrient=nutrients[key];
    return nutrient?<Label key={key}>{nutrient[0]} · {value} {nutrient[1]}</Label>:null;
   })}
   <Pressable accessibilityRole="link" onPress={()=>{void Linking.openURL(source.sourceUrl).catch(()=>setError('Could not open source.'));}}>
    <Label style={{color:C.green}}>{source.attribution}</Label>
   </Pressable>
   <Label muted>Data {source.dataLicense}{source.imageLicense?' · Image '+source.imageLicense:''}</Label>
  </View>)}
 </View>;
}
