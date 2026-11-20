import {useLiveCart} from './services/live-cart';
import {confirmSelection} from './services/selection';
import React,{createContext,useContext,useEffect,useLayoutEffect,useState,useCallback,useRef} from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {AppState,Platform} from 'react-native';
import * as SecureStore from 'expo-secure-store';
import {api,DEMO,restoreToken,setToken,onSessionExpired,setOfferPosition} from './services/api';
import {demoProducts,locations as sampleLocations,categories as sampleCategories} from './data/demo';
import {Location,Product,User,PlatformId,ComparisonQuery,ComparisonResult,ComparisonOption} from './types';
import {CartEntry,CartMode,PurchasePreferences,defaultPreferences,parseCart,setItemQuantity,clearLocationCart} from './services/cart';
import {comparison} from './services/comparison';
import {CheckoutRequest,OrderStatus,removeConfirmedItems} from './services/checkout';
import {workspace} from './services/workspace';
import {setStorageOwner} from './services/storage-scope';
import {emptyLocationSettings,locationSettingsKey,readLocationSettings,sameShoppingLocation,serializeLocationSettings,
 type LocationConsent,type LocationSettings,type SelectedLocation} from './services/shopping-location';
import {listenForPushUpdates} from './services/push';
import {logoutPushDevice,registerPushDevice,unregisterPushDevice} from './services/push-device';

type Store={cartMode:CartMode;setCartMode:(m:CartMode)=>void;selectedStore:PlatformId;setSelectedStore:(p:PlatformId)=>void;clearCart:()=>void;completeOrder:(o:OrderStatus,r:CheckoutRequest)=>void;cart:CartEntry[];cartReady:boolean;cartError:string;addToCart:(p:Product)=>boolean;addBest:(p:Product,q:ComparisonQuery,r:ComparisonResult,choice?:ComparisonOption)=>Promise<void>;editCartItem:(p:Product)=>void;setCartQuantity:(id:string,n:number)=>void;refreshCart:()=>Promise<void>;pendingProduct:Product|null;pendingEntry:CartEntry|null;closePurchase:()=>void;savePurchase:(n:number,p:PurchasePreferences)=>Promise<void>;products:Product[];locations:Location[];location:Location;categories:string[];loading:boolean;error:string;refresh:()=>Promise<Product[]|null>;chooseLocation:(l:Location)=>void;saved:string[];toggleSave:(id:string)=>Promise<void>;user:User|null;setUser:(u:User|null)=>void;logout:()=>Promise<void>;toast:(s:string)=>void;notice:string;demo:boolean;offline:boolean};
const Context=createContext<Store & {
 liveCart:ReturnType<typeof useLiveCart>; shoppingLocation:SelectedLocation|null; savedSelection:SelectedLocation|null; catalogueSelection:Location|null;
 chooseShoppingLocation:(place:SelectedLocation|null,areaId?:string)=>Promise<void>;
 savedShoppingLocations:SelectedLocation[]; removeShoppingLocation:(place:SelectedLocation)=>void;
 locationSettingsReady:boolean; locationConsent:LocationConsent; setLocationConsent:(consent:'granted'|'declined')=>void;
 enableDevicePush:()=>Promise<void>; disableDevicePush:()=>Promise<void>;
}>(null!);
export function Provider({children}:{children:React.ReactNode}){
 const [products,setProducts]=useState<Product[]>([]),[locations,setLocations]=useState(DEMO?sampleLocations:[]),[location,updateLocation]=useState<Location>(DEMO?sampleLocations[0]!:{id:'',name:'Select location',city:'',postcode:''});
 const [locationBook,setLocationBook]=useState({owner:'guest',value:emptyLocationSettings,ready:true});
 const locationEdit=useRef(0),locationWrites=useRef(Promise.resolve());
 const [categories,setCategories]=useState<string[]>(DEMO?sampleCategories:[]),[sampleData,setSampleData]=useState(DEMO),[saved,setSaved]=useState<string[]>([]),[user,updateUser]=useState<User|null>(null),[loading,setLoading]=useState(true),[error,setError]=useState(''),[notice,setNotice]=useState('');
 const [cartMode,setCartMode]=useState<CartMode>('best'),[selectedStore,setSelectedStore]=useState<PlatformId>('blinkit'),[cart,setCart]=useState<CartEntry[]>([]),[cartReady,setCartReady]=useState(false),[cartError,setCartError]=useState(''),[pendingProduct,setPendingProduct]=useState<Product|null>(null);
 const completedOrders=useRef(new Set<string>()),requestId=useRef(0),timer=useRef<ReturnType<typeof setTimeout>|null>(null),ownerVersion=useRef(0);
 const userId=useRef<string|null>(null),cartMutations=useRef(new Set<string>());
 const signingOut=useRef(false);
 const firstCatalogueArea=useRef<Location|null>(null);
 useEffect(()=>{
  if(DEMO||!user?.id)return;
  const owner=user.id;
  let stopped=false, unlisten:(()=>void)|undefined;
  const current=()=>!stopped&&!signingOut.current&&userId.current===owner;
  const sync=()=>{if(current())void registerPushDevice(owner,false,current).catch(()=>{});};
  sync();
  const foreground=AppState.addEventListener('change',state=>{if(state==='active')sync();});
  void listenForPushUpdates(sync,()=>{if(current())toast('You have a shopping update.');})
   .then(remove=>{if(stopped)remove();else unlisten=remove;}).catch(()=>{});
  return ()=>{stopped=true;foreground.remove();unlisten?.();};
 },[user?.id]);
 const activeLocation=useRef(location.id);activeLocation.current=location.id;
 const owner=user?.id||'guest',cacheKey=`cartiq.cart.v2.${owner}`;
 const locationSettingsReady=locationBook.owner===owner&&locationBook.ready;
 const shoppingLocations=locationSettingsReady?locationBook.value:emptyLocationSettings;
 const [fixClock,advanceFixClock]=useState(0);
 const selectedFix=shoppingLocations.selected;
 useEffect(()=>{
  if(selectedFix?.source!=='gps'||!selectedFix.timestamp)return;
  const remaining=selectedFix.timestamp+900000-Date.now();
  if(remaining<=0)return;
  const timer=setTimeout(()=>advanceFixClock(n=>n+1),remaining+10);
  const foreground=AppState.addEventListener('change',state=>{if(state==='active')advanceFixClock(n=>n+1);});
  return()=>{clearTimeout(timer);foreground.remove();};
 },[selectedFix,fixClock]);
 const shoppingLocation=selectedFix?.source==='gps'&&(!selectedFix.timestamp||Date.now()-selectedFix.timestamp>=900000)?null:selectedFix;
 const liveCart=useLiveCart(user?.id??null,shoppingLocation);
 useLayoutEffect(()=>{setOfferPosition(user?shoppingLocation:null);return()=>setOfferPosition(null);},[shoppingLocation,user]);
 const toast=useCallback((s:string)=>{setNotice(s);if(timer.current)clearTimeout(timer.current);timer.current=setTimeout(()=>setNotice(''),5000);},[]);
 useEffect(()=>{
  if(owner==='guest')return;
  let active=true;
  const key=locationSettingsKey(owner),revision=locationEdit.current;
  const stored=locationWrites.current.then(()=>Platform.OS==='web'?AsyncStorage.getItem(key):SecureStore.getItemAsync(key));
  void stored.catch(()=>null).then(value=>{
   if(active&&revision===locationEdit.current)setLocationBook({owner,value:readLocationSettings(value),ready:true});
  });
  return()=>{active=false;};
 },[owner]);
 const catalogueSelection=locations.find(area=>area.id===shoppingLocations.catalogueAreaId)??null;
 useEffect(()=>{
  if(catalogueSelection){if(catalogueSelection.id!==location.id){setProducts([]);setPendingProduct(null);updateLocation(catalogueSelection);}return;}
  if(!shoppingLocation?.postcode)return;
  const matches=locations.filter(area=>area.postcode===shoppingLocation.postcode);
  if(matches.length===1&&matches[0]!.id!==location.id){
   setProducts([]);setPendingProduct(null);updateLocation(matches[0]!);
  }
 },[shoppingLocation,locations,catalogueSelection,location.id]);
 async function saveLocations(value:LocationSettings){
  if(!locationSettingsReady||(userId.current||'guest')!==owner)throw new Error('Your account changed. Open the location picker again.');
  const revision=++locationEdit.current;
  if(owner!=='guest'){
   const key=locationSettingsKey(owner),data=serializeLocationSettings(value);
   const write=locationWrites.current.then(()=>Platform.OS==='web'?AsyncStorage.setItem(key,data):SecureStore.setItemAsync(key,data));
   locationWrites.current=write.catch(()=>{});
   try{await write;}catch{throw new Error('Could not save your location settings on this device.');}
  }
  if((userId.current||'guest')!==owner||revision!==locationEdit.current)return;
  setLocationBook({owner,value,ready:true});
 }
 function chooseShoppingLocation(place:SelectedLocation|null,areaId?:string){
  const places=place&&place.provider!=='google-demo'?[place,...shoppingLocations.places.filter(saved=>!sameShoppingLocation(saved,place))].slice(0,8):shoppingLocations.places;
  return saveLocations({...shoppingLocations,selected:place,places,catalogueAreaId:areaId});
 }
 function removeShoppingLocation(place:SelectedLocation){
  const selected=selectedFix&&sameShoppingLocation(selectedFix,place)?null:selectedFix;
  void saveLocations({...shoppingLocations,selected,places:shoppingLocations.places.filter(saved=>!sameShoppingLocation(saved,place))}).catch(e=>toast(e.message));
 }
 function setLocationConsent(consent:'granted'|'declined'){
  const selected=consent==='declined'&&selectedFix?.provider==='google-demo'?null:selectedFix;
  void saveLocations({...shoppingLocations,selected,consent}).catch(e=>toast(e.message));
 }
 const setUser=useCallback((u:User|null)=>{
  if((u?.id??null)!==userId.current){
   userId.current=u?.id??null;ownerVersion.current++;setStorageOwner(u?.id||'guest');
   locationEdit.current++;setLocationBook({owner:u?.id||'guest',value:emptyLocationSettings,ready:!u});
   if(firstCatalogueArea.current)updateLocation(firstCatalogueArea.current);
   setCart([]);setSaved([]);setPendingProduct(null);setCartReady(false);setCartError('');
  }
  updateUser(u);
 },[]);
 useEffect(()=>onSessionExpired(()=>{setUser(null);toast('Please sign in again to continue with your private workspace.');}),[setUser,toast]);
 const catalogueReady=useRef(DEMO);
 const refresh=useCallback(async()=>{
  const id=++requestId.current;setLoading(true);setError('');
  try{
   if(!catalogueReady.current){
    const [areas,meta,cats]=await Promise.all([api.locations(),workspace.meta(),workspace.categories()]);
    if(id!==requestId.current)return null;
    if(meta.sample&&!DEMO)setSampleData(true);
    if(meta.sample&&!DEMO)throw new Error('This backend is serving test data. Connect a verified catalogue to browse groceries.');
    setLocations(areas);setSampleData(meta.sample);setCategories(['All',...cats.map(c=>c.name)]);
    if(!areas.length)throw new Error('No delivery locations are configured.');
    firstCatalogueArea.current=areas[0]!;
    catalogueReady.current=true;
    if(!location.id){updateLocation(areas[0]!);return null;}
   }
   const data=DEMO?demoProducts(location.id):await api.products(location.id);
   if(id!==requestId.current)return null;
   setProducts(data);return data;
  }catch(e){
   if(id===requestId.current)setError(e instanceof Error?e.message:'Could not load products.');
   return null;
  }finally{if(id===requestId.current)setLoading(false);}
 },[location.id]);
 useEffect(()=>{void refresh();},[refresh]);
 useEffect(()=>{
  let active=true;
  const revision=ownerVersion.current;
  (async()=>{
   try{
    if(!DEMO&&await restoreToken()){
     const me=await api.me();
     if(active&&revision===ownerVersion.current&&!userId.current)setUser(me);
    }
   }catch(e){if(active&&!userId.current)toast(e instanceof Error?e.message:'Could not connect to your workspace.');}
  })();
  return()=>{active=false;if(timer.current)clearTimeout(timer.current);};
 },[setUser,toast]);
 const [cartReload,reloadCart]=useState(0);
 useEffect(()=>{
  let active=true;const version=ownerVersion.current;
  const current=()=>active&&version===ownerVersion.current;
  setCartReady(false);setCartError('');completedOrders.current=new Set();
  (async()=>{
   let cached:CartEntry[]=[];
   try{
    const raw=await AsyncStorage.getItem(cacheKey);
    if(raw&&current()){
     const parsed=JSON.parse(raw) as {items:CartEntry[];completed:string[]};
     cached=parseCart(JSON.stringify(parsed.items));
     if(Array.isArray(parsed.completed))completedOrders.current=new Set(parsed.completed.filter(v=>typeof v==='string'));
     if(DEMO||!user)setCart(cached);
    }
   }catch{if(current())toast('Could not read the cart saved on this device.');}
   if(!current())return;
   if(!DEMO&&user){
    const loadCart=workspace.cart().then(items=>{
     if(current())setCart(items.map(item=>{
      const local=cached.find(c=>c.id===item.id&&c.quantity===item.quantity);
      return !item.listing&&local?.selection?{...item,selection:local.selection}:item;
     }));
    }).catch(e=>{if(current())setCartError(e instanceof Error?e.message:'Could not load your cart.');})
     .finally(()=>{if(current())setCartReady(true);});
    void api.wishlist().then(items=>{if(current())setSaved(items);})
     .catch(e=>{if(current())toast(e instanceof Error?e.message:'Could not load saved products.');});
    await loadCart;
   }else{
    setCartReady(true);
    if(DEMO){
     try{
      const savedRaw=await AsyncStorage.getItem('cartiq.saved');
      const value:unknown=savedRaw?JSON.parse(savedRaw):null;
      if(current()&&Array.isArray(value)&&value.every(v=>typeof v==='string'))setSaved(value);
     }catch{if(current())toast('Could not load saved products.');}
    }
   }
  })();
  return()=>{active=false;};
 },[owner,cartReload]);
 const writes=useRef(Promise.resolve());
 useEffect(()=>{if(cartReady&&!cartError){const content=JSON.stringify({items:cart.map(({listing,...entry})=>entry),completed:[...completedOrders.current]});writes.current=writes.current.then(()=>AsyncStorage.setItem(cacheKey,content)).catch(()=>toast('Could not save the cart on this device.'));}},[cart,cartReady,cartError,cacheKey]);
 useEffect(()=>{
  if(DEMO||loading||!location.id)return;
  const known=new Set(products.map(p=>p.id));
  const missing=[...new Set([...saved,...cart.filter(i=>i.locationId===location.id).map(i=>i.productId)])].filter(id=>!known.has(id));
  if(!missing.length)return;
  const controller=new AbortController();
  const version=ownerVersion.current;
  void Promise.allSettled(missing.map(id=>api.product(location.id,id,controller.signal))).then(results=>{
   if(controller.signal.aborted||version!==ownerVersion.current)return;
   const found=results.flatMap(result=>result.status==='fulfilled'?[result.value]:[]);
   if(found.length)setProducts(items=>[...items,...found.filter(p=>!items.some(i=>i.id===p.id))]);
  });
  return()=>controller.abort();
 },[cart,loading,location.id,products,saved]);
 async function refreshCart(){if(!DEMO&&user){const version=ownerVersion.current;const items=await workspace.cart();if(version===ownerVersion.current)setCart(old=>items.map(item=>{const match=old.find(i=>i.productId===item.productId&&i.locationId===item.locationId&&i.quantity===item.quantity);return match?.selection?{...item,selection:match.selection}:item;}));}}
 function addToCart(product:Product){
  if(!cartReady||cartError){toast('Open your cart and try loading it again.');return false;}
  if(!location.id||product.retailerListing){toast('Choose a location and a product from the CartIQ catalogue.');return false;}
  if(DEMO&&!comparison(product,location.id).best){toast('No sample price is available for this item.');return false;}
  setPendingProduct(product);return true;
 }
 async function addBest(product:Product,q:ComparisonQuery,shown:ComparisonResult,choice?:ComparisonOption){
  if(!cartReady||cartError)throw new Error('Open your cart and try loading it again.');
  if(DEMO||!(choice||shown.lowestEligible)||shown.quantity!==q.quantity||shown.fulfillment!==q.fulfillment||shown.product.id!==product.id)throw new Error('The comparison changed. Refresh before adding.');
  const version=ownerVersion.current;
  const current=await api.compare(product.id,q);
  if(version!==ownerVersion.current||q.locationId!==activeLocation.current)throw new Error('Your account or location changed. Compare again.');
  const winner=confirmSelection(current,shown,q,product.id,choice);
  const item={productId:product.id,locationId:q.locationId,quantity:q.quantity,preferences:defaultPreferences};
  const savedItem=user?await workspace.saveCart(item):item;
  if(version!==ownerVersion.current)return;
  const selection={source:winner.source,sourceId:winner.sourceId,name:winner.name,priceMinor:winner.priceMinor!,totalMinor:winner.totalMinor!,observedAt:winner.observedAt,quantity:q.quantity,fulfillment:q.fulfillment,query:q,deliveryMinor:winner.deliveryMinor,handlingMinor:winner.handlingMinor,minimumMinor:winner.minimumMinor,stockQuantity:winner.stockQuantity,unitPriceMinor:winner.unitPriceMinor,unitLabel:winner.unitLabel,verificationStatus:winner.verificationStatus};
  if(q.locationId===activeLocation.current)setProducts(items=>items.some(p=>p.id===product.id)?items:[...items,product]);
  setCart(items=>[...items.filter(i=>i.productId!==item.productId||i.locationId!==item.locationId),{...savedItem,selection}]);
  toast(choice?'Selected source saved to your cart.':'Best option added to your CartIQ cart.');
 }
 function editCartItem(product:Product){setPendingProduct(product);}
 async function savePurchase(quantity:number,preferences:PurchasePreferences){
  if(!pendingProduct)return;
  if(!Number.isInteger(quantity)||quantity<1||quantity>99)throw new Error('Choose between 1 and 99 packs.');
  const version=ownerVersion.current, product=pendingProduct;
  const item={productId:product.id,locationId:location.id,quantity,preferences};
  const savedItem=!DEMO&&user?await workspace.saveCart(item):item;
  if(version!==ownerVersion.current)return;
  if(item.locationId===activeLocation.current)setProducts(items=>items.some(p=>p.id===product.id)?items:[...items,product]);
  setCart(items=>[...items.filter(i=>i.productId!==item.productId||i.locationId!==item.locationId),savedItem]);
  setPendingProduct(null);toast('Purchase preferences saved.');
 }
 async function changeQuantity(productId:string,quantity:number){
  if(!Number.isInteger(quantity)||quantity<0||quantity>99)return;
  const entry=cart.find(i=>i.productId===productId&&i.locationId===location.id);
  const key=`${owner}/${location.id}/${productId}`;
  if(!entry||cartMutations.current.has(key))return;
  const version=ownerVersion.current;
  cartMutations.current.add(key);
  try{
   let savedItem:CartEntry|undefined;
   if(!DEMO&&user){
    if(quantity>0)savedItem=await workspace.saveCart({...entry,quantity,preferences:entry.preferences||defaultPreferences});
    else if(entry.id)await workspace.removeCart(entry.id);
   }
   if(version===ownerVersion.current)setCart(items=>savedItem
    ? items.map(i=>i.productId===productId&&i.locationId===entry.locationId?{...savedItem!,selection:i.selection}:i)
    : setItemQuantity(items,productId,entry.locationId,quantity));
  }catch(e){toast(e instanceof Error?e.message:'Could not update your cart.');}
  finally{cartMutations.current.delete(key);}
 }
 async function clear(){const current=location.id,version=ownerVersion.current;try{if(!DEMO&&user)await workspace.clearCart(current);if(version===ownerVersion.current)setCart(items=>clearLocationCart(items,current));}catch(e){toast((e as Error).message);}}
 function completeOrder(order:OrderStatus,request:CheckoutRequest){if(completedOrders.current.has(order.id))return;completedOrders.current.add(order.id);setCart(items=>removeConfirmedItems(items,order,request));}
 const chooseLocation=(next:Location)=>{
  void saveLocations({...shoppingLocations,selected:null,catalogueAreaId:next.id}).catch(e=>toast(e.message));
  if(next.id!==location.id){setProducts([]);setPendingProduct(null);updateLocation(next);}
 };
 const pendingSaves=useRef(new Set<string>());
 async function toggleSave(id:string){if(pendingSaves.current.size)return;if(!DEMO&&!user){toast('Sign in from your profile to save products.');return;}pendingSaves.current.add(id);const version=ownerVersion.current;const next=saved.includes(id)?saved.filter(v=>v!==id):[...saved,id];try{if(DEMO)await AsyncStorage.setItem('cartiq.saved',JSON.stringify(next));else await api.save(id,next.includes(id));if(version===ownerVersion.current){setSaved(next);toast(next.includes(id)?'Added to saved finds':'Removed from saved finds');}}catch(e){toast((e as Error).message);}finally{pendingSaves.current.delete(id);}}
 async function logout(){
  if(signingOut.current)return;
  signingOut.current=true;
  try{
   const owner=userId.current;
   if(!DEMO){if(owner)await logoutPushDevice(owner,api.logout);else await api.logout();}
  }catch(e){signingOut.current=false;toast(e instanceof Error?e.message:'Could not sign out. Try again while connected.');return;}
  const clearSession=setToken(null);setUser(null);
  signingOut.current=false;
  try{await clearSession;await writes.current;await AsyncStorage.removeItem(cacheKey);toast('You are signed out.');}
  catch{toast('Signed out. Some data could not be cleared from this device.');}
 }
 const pendingEntry=pendingProduct?cart.find(i=>i.productId===pendingProduct.id&&i.locationId===location.id)||null:null;
 async function setDevicePush(enabled:boolean){
  const owner=user?.id;
  if(!owner||DEMO||signingOut.current)throw new Error('Sign in to a connected account to manage push notifications.');
  const current=()=>!signingOut.current&&userId.current===owner;
  if(enabled)await registerPushDevice(owner,true,current);
  else await unregisterPushDevice(owner,current);
 }
 return <Context.Provider value={{liveCart,cartMode,setCartMode,selectedStore,setSelectedStore,clearCart:()=>{void clear();},completeOrder,cart,cartReady,cartError,addToCart,addBest,editCartItem,setCartQuantity:(id,n)=>{void changeQuantity(id,n);},refreshCart:cartError?async()=>reloadCart(n=>n+1):refreshCart,pendingProduct,pendingEntry,closePurchase:()=>setPendingProduct(null),savePurchase,products,locations,location,shoppingLocation,savedSelection:selectedFix,catalogueSelection,chooseShoppingLocation,savedShoppingLocations:shoppingLocations.places,removeShoppingLocation,locationSettingsReady,locationConsent:shoppingLocations.consent,setLocationConsent,categories,loading,error,refresh,chooseLocation,saved,toggleSave,user,setUser,logout,enableDevicePush:()=>setDevicePush(true),disableDevicePush:()=>setDevicePush(false),toast,notice,demo:sampleData,offline:DEMO}}>{children}</Context.Provider>;
}
export const useStore=()=>useContext(Context);
