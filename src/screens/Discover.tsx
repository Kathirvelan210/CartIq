import {GroceryLandingIntro} from '../components/GroceryLandingIntro';
import React,{useEffect,useRef,useState} from 'react';
import {Pressable,ScrollView,Text,TextInput,View,useWindowDimensions} from 'react-native';
import {ArrowRight,ArrowUpRight,Check,ChevronDown,MapPin,Search,SlidersHorizontal,Sparkles,Tag,TrendingDown,X} from 'lucide-react-native';
import {C,money} from '../theme';
import {useStore} from '../store';
import {Product} from '../types';
import {withRetailerCatalogue} from '../data/retailer-catalogue';
import {api} from '../services/api';
import {ApiError} from '../services/api-client';
import {comparison} from '../services/comparison';
import {matchesProductSearch} from '../services/product-search';
import {ProductCard} from '../components/ProductCard';
import {ProductArt} from '../components/ProductArt';
import {RetailerComparison} from '../components/RetailerComparison';
import {DepartmentGrid} from '../components/DepartmentGrid';
import type {BrowseIntent} from '../services/department-lookup';
import {departmentSearch,groceryQueries,type GroceryCategory} from '../services/grocery-categories';
import {Button,Empty,Field,Heading,IconButton,Label,ui} from '../components/ui';
import {shoppingKey,shoppingLocationLabel} from '../services/shopping-location';
export function Discover({openProduct,openLocation,openSmart,recent,onResultsOpen,onBrowseChange,onDepartments,submitted,searchOnly=false,savedOnly=false,initialQuery='',initialCategory='All'}:{openProduct:(p:Product)=>void;openLocation:()=>void;openSmart:(tool:'Price trends'|'Cart'|'Overview'|'Recipes')=>void;recent:Product[];onResultsOpen?:()=>void;onDepartments:()=>void;submitted?:BrowseIntent;onBrowseChange?:(browse:boolean)=>void;searchOnly?:boolean;savedOnly?:boolean;initialQuery?:string;initialCategory?:string}) {
 const store=useStore(),{categories}=store,{width}=useWindowDimensions();const [query,setQuery]=useState(initialQuery),[category,setCategory]=useState(initialCategory),[sort,setSort]=useState<'recommended'|'price'|'savings'>('recommended'),[filters,setFilters]=useState(false),[stockOnly,setStockOnly]=useState(false);
 const selectedPoint=store.shoppingLocation;
 const coordinates=React.useMemo(()=>selectedPoint?{latitude:selectedPoint.latitude,longitude:selectedPoint.longitude,radiusKm:5}:null,[selectedPoint]);
 const [brand,setBrand]=useState(''),[pack,setPack]=useState('');
 const [retailCategory,setRetailCategory]=useState<GroceryCategory|null>(null),[retailTerm,setRetailTerm]=useState('');
 const retailOnly=!savedOnly&&!store.demo;
 const [catalogueFilters,setCatalogueFilters]=useState({brand:'',pack:''});
 const [barcode,setBarcode]=useState(''),[barcodeBusy,setBarcodeBusy]=useState(false),[barcodeError,setBarcodeError]=useState('');
 const barcodeRequest=useRef<AbortController|null>(null);
 const [intent,setIntent]=useState<BrowseIntent|undefined>(submitted);
 function browseRetailer(term:string,topic:GroceryCategory|null){
  onResultsOpen?.();
  setPage(0);setCategory('All');setRetailCategory(topic);setRetailTerm(topic?term:'');setQuery(topic?'':term);
  setFilters(false);setCatalogueFilters({brand:'',pack:''});setStockOnly(false);
  setIntent(store.shoppingLocation&&store.user?{scope:shoppingKey(term,store.shoppingLocation,store.user.id),used:false}:undefined);
 }
 const browse=!searchOnly&&!savedOnly&&!query&&!retailTerm&&category==='All'&&!catalogueFilters.brand&&!catalogueFilters.pack;
 const connectedList=!store.offline&&!savedOnly&&!browse&&!retailOnly;
 const [page,setPage]=useState(0),[retry,setRetry]=useState(0);
 const [listing,setListing]=useState<{
  key:string; items:Product[]; totalElements:number; hasNext:boolean; error:string; invalidFilters?:boolean;
 }|null>(null);
 const locationId=store.location.id, term=query.trim()||retailTerm, selectedCategory=category==='All'?'':category;
 const retailerQuery=term||selectedCategory;
 const listingKey=JSON.stringify([locationId,term,selectedCategory,page,retry,catalogueFilters]);
 useEffect(()=>{
  if(!connectedList||!locationId)return;
  const controller=new AbortController();
  const timer=setTimeout(()=>{
   void api.productsPage(locationId,term,selectedCategory,page,controller.signal,catalogueFilters)
    .then(result=>{if(!controller.signal.aborted)setListing({key:listingKey,...result,error:''});})
    .catch(e=>{if(!controller.signal.aborted)setListing({key:listingKey,items:[],totalElements:0,hasNext:false,error:e.message,invalidFilters:e instanceof ApiError&&e.status===400});});
  },term?250:0);
  return()=>{clearTimeout(timer);controller.abort();};
 },[connectedList,locationId,term,selectedCategory,page,listingKey,catalogueFilters]);
 useEffect(()=>()=>barcodeRequest.current?.abort(),[locationId]);
 function updateQuery(value:string){setIntent(undefined);setPage(0);setQuery(value);if(value.trim()){setRetailCategory(null);setRetailTerm('');}}
 function selectCategory(value:string){setPage(0);setCategory(value);}
 function clearFilters(){
  updateQuery('');selectCategory('All');setStockOnly(false);
  setRetailCategory(null);setRetailTerm('');
  setBrand('');setPack('');setCatalogueFilters({brand:'',pack:''});
 }
 async function findBarcode(){
  barcodeRequest.current?.abort();
  const controller=new AbortController();
  barcodeRequest.current=controller;
  setBarcodeBusy(true);setBarcodeError('');
  try{
   const product=await api.barcode(locationId,barcode.trim(),controller.signal);
   if(!controller.signal.aborted)openProduct(product);
  }catch(e){
   if(!controller.signal.aborted)setBarcodeError((e as Error).message);
  }finally{
   if(!controller.signal.aborted)setBarcodeBusy(false);
  }
 }
 const currentListing=listing?.key===listingKey?listing:null;
 const catalogue=store.offline?withRetailerCatalogue(store.products):store.products;
 const candidates=connectedList?currentListing?.items??[]:catalogue.filter(p=>
  (!savedOnly||store.saved.includes(p.id))&&(category==='All'||p.category===category)&&matchesProductSearch(p,query));
 const list=candidates.filter(p=>!stockOnly||comparison(p,locationId).best);
 if(sort==='price')list.sort((a,b)=>(comparison(a,store.location.id).best?.price??Infinity)-(comparison(b,store.location.id).best?.price??Infinity));
 if(sort==='savings')list.sort((a,b)=>comparison(b,store.location.id).saving-comparison(a,store.location.id).saving);
 const listingError=currentListing?.error||((!connectedList||!locationId)?store.error:'');
 const listingLoading=connectedList&&locationId?!currentListing:store.loading;
 const retryListing=()=>{if(connectedList&&locationId)setRetry(n=>n+1);else void store.refresh();};
 const cols=1, categoryNames=categories.filter(name=>name!=='All'), tileColumns=width>=1500?6:width>=1100?4:width>=650?3:2;
 useEffect(()=>{onBrowseChange?.(browse);},[browse,onBrowseChange]);
 return <View>{browse&&<><GroceryLandingIntro location={store.savedSelection?shoppingLocationLabel(store.savedSelection)+(store.shoppingLocation?'':' · refresh GPS'):store.catalogueSelection?store.catalogueSelection.name+' · '+store.catalogueSelection.postcode:'Choose shopping location'} openLocation={openLocation}/></>} {!browse&&<View style={[ui.row,{justifyContent:'space-between',marginBottom:24}]}><View style={{flex:1}}><Heading style={{fontSize:width<400?25:30}}>{savedOnly?'Saved products':retailCategory??(query||'Search groceries')}</Heading></View>{!savedOnly&&<IconButton label="Choose delivery location" onPress={openLocation} style={{backgroundColor:C.mint}}><MapPin size={20} color={C.green}/></IconButton>}</View>}
 {!savedOnly&&!store.demo&&browse&&<>
  <Label muted>Browse Blinkit, Zepto and Swiggy Instamart at your shopping location.</Label>
  <DepartmentGrid open={topic=>browseRetailer(departmentSearch[topic],topic)}/>
 </>}
 {retailCategory&&<View style={{flexDirection:'row',flexWrap:'wrap',gap:6,marginBottom:10}}>
  {[departmentSearch[retailCategory],...groceryQueries[retailCategory].filter(t=>t!==departmentSearch[retailCategory!])].map(term=>
   <Pressable key={term} accessibilityRole="button" onPress={()=>browseRetailer(term,retailCategory)} style={[ui.pill,{backgroundColor:term===retailTerm?C.green:C.mint}]}>
    <Label style={{color:term===retailTerm?C.onGreen:C.green}}>{term===departmentSearch[retailCategory]?'All '+retailCategory.toLowerCase():term}</Label>
   </Pressable>)}
 </View>}
 {!savedOnly&&!store.demo&&!!retailerQuery&&(!store.user?<Label>Sign in to search retailers.</Label>:!store.shoppingLocation?<Button title="Choose a delivery location" onPress={openLocation}/>:<RetailerComparison key={shoppingKey(retailerQuery,store.shoppingLocation,store.user.id)} query={retailerQuery} location={store.shoppingLocation} admin={false} privateSearch/>)}
 {!browse&&retailOnly&&<Pressable accessibilityRole="button" onPress={onDepartments} style={{alignSelf:'flex-start',paddingVertical:10}}><Label style={{color:C.green,fontWeight:'700'}}>All departments</Label></Pressable>}
 {!retailerQuery&&searchOnly&&retailOnly&&<Label muted>Enter a product in the search bar to compare retailer listings at your selected location.</Label>}
 {!retailOnly&&!savedOnly&&!browse&&<><Pressable accessibilityRole="button" onPress={openLocation} style={[ui.row,{gap:6,marginTop:-12,marginBottom:22}]}><MapPin size={13} color={C.green}/><Label style={{fontSize:12}}>Shopping location · <Text style={{fontWeight:'700'}}>{store.savedSelection?shoppingLocationLabel(store.savedSelection):'Choose location'}</Text></Label><ChevronDown size={12} color={C.green}/></Pressable><View style={[ui.row,{gap:10}]}><View style={[ui.row,{flex:1,backgroundColor:C.paper,borderWidth:1,borderColor:C.line,borderRadius:15,paddingHorizontal:15,height:54,gap:10}]}><Search size={20} color={C.muted}/><TextInput accessibilityLabel="Search products, brands or categories" placeholder="Search milk, coffee, a little of everything…" placeholderTextColor={C.muted} value={query} onChangeText={text=>{updateQuery(text);selectCategory('All');}} maxLength={100} returnKeyType="search" style={{flex:1,color:C.text,fontSize:13,height:'100%',minWidth:0}}/>{query.length>0&&<IconButton label="Clear search" onPress={()=>updateQuery('')}><X size={16} color={C.muted}/></IconButton>}</View><IconButton label="Filter and sort products" onPress={()=>setFilters(!filters)} style={{backgroundColor:filters?C.green:C.paper,borderWidth:1,borderColor:C.line,width:54,height:54}}><SlidersHorizontal size={19} color={filters?'white':C.green}/></IconButton></View></>}
 {filters&&<View style={[ui.card,{marginTop:12,gap:12}]}><Label style={{fontWeight:'700'}}>Sort & filter</Label><View style={[ui.row,{flexWrap:'wrap',gap:8}]}>{(['recommended','price','savings'] as const).map(s=><Pressable key={s} accessibilityRole="button" accessibilityState={{selected:sort===s}} onPress={()=>setSort(s)} style={[ui.pill,{backgroundColor:sort===s?C.green:C.mint}]}><Label style={{fontSize:12,color:sort===s?C.onGreen:C.green}}>{s==='recommended'?'Featured':s==='price'?'Lowest price':'Biggest difference'}</Label></Pressable>)}</View><Pressable accessibilityRole="checkbox" accessibilityState={{checked:stockOnly}} onPress={()=>setStockOnly(!stockOnly)} style={[ui.row,{gap:8,minHeight:44}]}><View style={{width:22,height:22,borderWidth:1,borderColor:C.green,borderRadius:6,backgroundColor:stockOnly?C.green:'white'}}>{stockOnly&&<Check size={20} color="white"/>}</View><Label>Available products only</Label></Pressable>{connectedList&&<>
  <Label muted>Price sorting and availability filters apply to the current page.</Label>
  <Field label="Exact brand name" value={brand} onChangeText={setBrand} maxLength={100}/>
  <Field label="Pack size" value={pack} onChangeText={setPack} maxLength={60} placeholder="For example, 500 g or 1 litre"/>
  <Button title="Apply catalogue filters" onPress={()=>{setPage(0);setCatalogueFilters({brand:brand.trim(),pack:pack.trim()});}}/>
  <View style={ui.divider}/>
  <Field label="Product barcode" value={barcode} onChangeText={value=>{setBarcode(value);setBarcodeError('');}} maxLength={14} keyboardType="number-pad" editable={!barcodeBusy}/>
  <Button title="Find by barcode" secondary disabled={!barcode.trim()||!locationId} loading={barcodeBusy} onPress={()=>{void findBarcode();}}/>
  {!!barcodeError&&<View accessibilityRole="alert"><Label style={{color:C.red}}>{barcodeError}</Label></View>}
 </>}</View>}
 {!retailOnly&&<>
 {store.demo&&browse&&<View style={{marginTop:28,gap:20}}><Heading style={{fontSize:26}}>Catalogue categories</Heading>{store.loading&&<Label muted>Loading categories…</Label>}{store.error&&<Empty title="Could not load the catalogue" body={store.error} action="Try again" onPress={store.refresh}/>}{!store.loading&&!store.error&&categoryNames.length===0&&<Label muted>No categories are available yet.</Label>}<View style={{flexDirection:'row',flexWrap:'wrap',marginHorizontal:-8}}>{categoryNames.map(name=>{const examples=catalogue.filter(p=>p.category===name).slice(0,2);return <View key={name} style={{width:(100/tileColumns)+'%' as `${number}%`,padding:8}}><Pressable accessibilityRole="button" accessibilityLabel={'Browse '+name} onPress={()=>{selectCategory(name);setFilters(false);}} style={{backgroundColor:C.paper,borderWidth:1,borderColor:'#DCE4EF',borderRadius:18,padding:14,gap:14}}><View style={{height:width<650?125:180,backgroundColor:'#F6F8FA',borderRadius:14,alignItems:'center',justifyContent:'center',overflow:'hidden'}}><View style={{position:'absolute',width:'85%',height:'85%',backgroundColor:'#EAF3E6',borderRadius:70}}/><View style={{flexDirection:'row',alignItems:'center',justifyContent:'center'}}>{examples.map((product,i)=><View key={product.id} style={{marginLeft:i?-25:0,transform:[{rotate:i?'7deg':'-5deg'}]}}><ProductArt product={product} size={width<650?72:108}/></View>)}</View></View><Text style={{color:C.text,fontSize:width<650?14:17,fontWeight:'600',minHeight:40}}>{name}</Text></Pressable></View>;})}</View></View>}
 {!savedOnly&&!browse&&<ScrollView horizontal showsHorizontalScrollIndicator={false} style={{marginTop:24,marginBottom:6}} contentContainerStyle={{gap:8}}>{categories.map(c=><Pressable key={c} accessibilityRole="button" accessibilityState={{selected:category===c}} onPress={()=>selectCategory(c)} style={{paddingVertical:11,paddingHorizontal:17,borderRadius:24,backgroundColor:category===c?C.green:'white',borderWidth:1,borderColor:category===c?C.green:C.line}}><Text style={{fontSize:12,fontWeight:'600',color:category===c?C.onGreen:C.muted}}>{c}</Text></Pressable>)}</ScrollView>}
 {!searchOnly&&!savedOnly&&!query&&recent.length>0&&<View style={{marginTop:24,gap:10}}><Heading style={{fontSize:20}}>Recently viewed</Heading><ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{gap:10}}>{recent.map(p=><Pressable key={p.id} accessibilityRole="button" onPress={()=>openProduct(p)} style={[ui.card,{width:150,padding:12}]}><ProductArt product={p} size={48}/><Label style={{fontWeight:'700'}}>{p.name}</Label><Label muted style={{fontSize:11}}>{p.brand} · {p.quantity}</Label></Pressable>)}</ScrollView></View>}
 {!browse&&<>

 {!!retailerQuery&&!savedOnly&&<Label muted style={{marginTop:18}}>CartIQ catalogue · {store.location.name}</Label>}
 <View style={[ui.row,{justifyContent:'space-between',marginTop:20,marginBottom:16}]}><Heading style={{fontSize:20}}>{savedOnly?'Saved products':'Catalogue products'}</Heading><Label muted style={{fontSize:11}}>{stockOnly?list.length:currentListing?.totalElements??list.length} items</Label></View>
 {listingLoading?<View style={{gap:12}}>{[0,1].map(i=><View key={i} style={{height:160,backgroundColor:C.mint,borderRadius:18}}/>)}<Label muted>Finding your essentials…</Label></View>:listingError?<Empty title={currentListing?.invalidFilters?'Check catalogue filters':'Catalogue unavailable'} body={listingError} action={currentListing?.invalidFilters?'Clear filters':'Try again'} onPress={currentListing?.invalidFilters?clearFilters:retryListing}/>:list.length===0?<Empty title={savedOnly?'Your good finds belong here':'No matches just yet'} body={savedOnly?'Tap the heart on any product to keep it close for your next shop.':'Try another product or clear your filters.'} action={savedOnly?undefined:'Clear filters'} onPress={clearFilters}/>:<View style={{flexDirection:'row',flexWrap:'wrap',gap:12}}>{list.map(p=><View key={p.id} style={{width:`${100/cols}%`,flexGrow:0}}><ProductCard moveFromSaved={savedOnly} product={p} onPrices={savedOnly?undefined:item=>browseRetailer([item.brand,item.name,item.quantity].filter(Boolean).join(' '),null)} coordinates={coordinates} onPress={()=>openProduct(p)}/></View>)}</View>}
 {connectedList&&currentListing&&!listingError&&(page>0||currentListing.hasNext)&&<View style={[ui.row,{gap:10,marginTop:16}]}><Button title="Previous page" secondary disabled={page===0} onPress={()=>setPage(n=>n-1)}/><Label muted>Page {page+1}</Label><Button title="Next page" secondary disabled={!currentListing.hasNext} onPress={()=>setPage(n=>n+1)}/></View>}
 </>}
 </>}
 {browse&&<View style={{marginTop:24,gap:12}}><Heading style={{fontSize:20}}>Smart tools for your next shop</Heading><Label muted>Explore price trends, basket planning, recommendations and recipe lists. Results depend on available data.</Label><View style={{flexDirection:'row',flexWrap:'wrap',gap:10}}>{([{title:'Price prediction',page:'Price trends',detail:'Recorded history and experimental forecasts.'},{title:'Smart cart optimizer',page:'Cart',detail:'Review your basket and available planning tools.'},{title:'Personal recommendations',page:'Overview',detail:'Suggestions from your shopping workspace.'},{title:'Recipe planner',page:'Recipes',detail:'Plan servings, ingredients and pantry needs.'}] as const).map(({title,page,detail})=><Pressable key={title} accessibilityRole="button" onPress={()=>openSmart(page)} style={[ui.card,{flexGrow:1,minWidth:150,padding:14}]}><Label style={{fontWeight:'700'}}>{title}</Label><Label muted style={{fontSize:11}}>{detail}</Label><Label style={{fontSize:11,color:C.green,marginTop:6}}>Explore →</Label></Pressable>)}</View></View>}
 <View style={{paddingVertical:28,alignItems:'center',gap:5}}><View style={[ui.row,{gap:5}]}><Sparkles size={12} color={C.green}/><Label style={{fontSize:11,color:C.green,fontWeight:'600'}}>A little smarter, every shop.</Label></View><Label muted style={{fontSize:10,textAlign:'center'}}>Eligible totals include known fulfillment charges. CartIQ does not place retailer orders yet.</Label></View></View>;
}


