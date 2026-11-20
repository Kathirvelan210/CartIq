import {useEffect,useRef,useState} from 'react';
import {api,request,type RetailerLookup} from './api';
import type {ShoppingLocation} from './shopping-location';

export type LiveCartLine={id:string;retailer:RetailerLookup;nativeId:string;title:string;brand:string|null;pack:string|null;
 priceMinor:number;imageUrl:string|null;stock:number|null;quantity:number;fetchedAt:string;freshUntil:string};
const root='/shopping/retailers/private-search/compare-all';
export function useLiveCart(owner:string|null,place:ShoppingLocation|null){
 const scope=JSON.stringify([owner,place?.latitude,place?.longitude,place?.postcode]);
 const [state,setState]=useState({scope:'',lines:[] as LiveCartLine[],error:''});
 const generation=useRef(0),active=useRef(scope),busy=useRef(false);active.current=scope;
 const point=place&&{latitude:place.latitude,longitude:place.longitude,postcode:place.postcode};
 useEffect(()=>{
  const revision=++generation.current;
  if(!owner||!place)return;
  const c=new AbortController();
  void api.selectPosition(place,c.signal).then(()=>request<LiveCartLine[]>(root+'/basket',{method:'POST',signal:c.signal,body:JSON.stringify(point)}))
   .then(lines=>{if(!c.signal.aborted&&revision===generation.current)setState({scope,lines,error:''});})
   .catch(e=>{if(!c.signal.aborted&&revision===generation.current)setState({scope,lines:[],error:e.message});});
  return()=>c.abort();
 },[scope]);
 async function change(path:string,body:object){
  if(busy.current||!owner||!place)throw Error('A cart update is pending or the delivery location changed.');
  busy.current=true;++generation.current;
  try{
   await api.selectPosition(place);
   if(active.current!==scope)throw Error('Delivery location changed.');
   const lines=await request<LiveCartLine[]>(root+path,{method:'POST',body:JSON.stringify(body)});
   if(active.current===scope)setState({scope,lines,error:''});
  }finally{busy.current=false;}
 }
 return {lines:state.scope===scope?state.lines:[],error:state.scope===scope?state.error:'',
  add:(job:string,retailer:RetailerLookup,nativeId:string)=>change('/'+encodeURIComponent(job)+'/basket/add',{retailer,nativeId}),
  quantity:(lineId:string,quantity:number)=>change('/basket/quantity',{place:point,lineId,quantity})};
}
