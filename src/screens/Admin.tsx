import React,{useEffect,useState} from 'react';
import {Pressable,Switch,Text,View} from 'react-native';
import {Activity,Box,Layers,Users} from 'lucide-react-native';
import {C} from '../theme';
import {useStore} from '../store';
import {api} from '../services/api';
import {demoAdmin,platforms} from '../data/demo';
import {AdminData,Product,User} from '../types';
import {Back,Button,Empty,Field,Heading,Label,ui} from '../components/ui';
import {CatalogueReview} from '../components/CatalogueReview';
import {PriceDatasetCoverage} from '../components/PriceDatasetCoverage';
import {RetailerOperations} from '../components/RetailerOperations';
import {ControlledPilotAdmin} from '../components/ControlledPilotAdmin';
import {RecommendationFeedbackAnalytics} from '../components/RecommendationFeedbackAnalytics';
export function Admin({back}:{back:()=>void}) {const {offline:demo,products:demoProducts,location,refresh,toast,user}=useStore();const [data,setData]=useState<AdminData|null>(null),[users,setUsers]=useState<User[]>([]),[error,setError]=useState(''),[editing,setEditing]=useState<Product|null>(null),[name,setName]=useState(''),[brand,setBrand]=useState(''),[quantity,setQuantity]=useState(''),[category,setCategory]=useState(''),[description,setDescription]=useState(''),[confirmDelete,setConfirmDelete]=useState(false),[busy,setBusy]=useState(false);
 const [page,setPage]=useState(0),[reload,reloadProducts]=useState(0),[query,setQuery]=useState(''),[search,setSearch]=useState('');
 const [listing,setListing]=useState<{items:Product[];hasNext:boolean}>();
 const products=demo?demoProducts:listing?.items??[];
 useEffect(()=>{if(demo){setData(demoAdmin);return;}if(user?.role==='admin')Promise.all([api.admin(),api.users()]).then(([a,u])=>{setData(a);setUsers(u);}).catch(e=>setError(e.message));},[]);
 useEffect(()=>{
  if(demo||user?.role!=='admin'||!location.id)return;
  const controller=new AbortController();
  void api.productsPage(location.id,search,'',page,controller.signal)
   .then(value=>{if(!controller.signal.aborted)setListing(value);})
   .catch(e=>{if(!controller.signal.aborted)setError(e.message);});
  return ()=>controller.abort();
 },[demo,location.id,page,reload,search,user?.role]);
 if(!demo&&user?.role!=='admin')return <Empty title="Administrator access required" body="Sign in with an authorized administrator account." action="Go back" onPress={back}/>;
 function edit(p:Product){setEditing(p);setName(p.name);setBrand(p.brand);setQuantity(p.quantity);setCategory(p.category);setDescription(p.description);setConfirmDelete(false);}
 async function save(){if(!name.trim()||!brand.trim()||!quantity.trim()||!category.trim()){toast('Name, brand, quantity and category are required.');return;}if(demo){toast('Connect the backend to manage catalogue records.');return;}setBusy(true);try{await api.saveProduct({...editing!,name:name.trim(),brand:brand.trim(),quantity:quantity.trim(),category:category.trim(),description});setEditing(null);reloadProducts(n=>n+1);await refresh();toast('Product saved.');}catch(e){toast((e as Error).message);}finally{setBusy(false);}}
 async function remove(){setBusy(true);try{await api.deleteProduct(editing!.id);setEditing(null);reloadProducts(n=>n+1);await refresh();toast('Product removed from catalogue.');}catch(e){toast((e as Error).message);}finally{setBusy(false);}}
 return <View><Back onPress={back} title="Operations overview"/><Label style={ui.eyebrow}>CARTIQ · ADMINISTRATION</Label><Heading style={{marginTop:8}}>Keep the good finds flowing.</Heading><Label muted style={{marginTop:8,marginBottom:24}}>{demo?'Preview dashboard · sample records, no live integrations':'Catalogue, customers and integration health'}</Label>{error&&<Empty title="Unable to load operations" body={error}/>}
 <View style={{flexDirection:'row',flexWrap:'wrap',gap:12}}>{[{label:'Registered users',value:data?.users,icon:Users},{label:'Products',value:data?.products,icon:Box},{label:'Categories',value:data?.categories,icon:Layers},{label:'Supported stores',value:data?.platforms.filter(p=>p.enabled).length,icon:Activity}].map(({label,value,icon:Icon})=><View key={label} style={[ui.card,{width:'47%',flexGrow:1}]}><Icon size={20} color={C.green}/><Heading style={{marginTop:12}}>{value??'—'}</Heading><Label muted style={{fontSize:12}}>{label}</Label></View>)}</View><Heading style={{fontSize:20,marginTop:28,marginBottom:14}}>Platform connections</Heading>{data?.platforms.map(p=><View key={p.id} style={[ui.card,{marginBottom:10}]}><View style={[ui.row,{justifyContent:'space-between'}]}><Text style={{fontSize:18,fontWeight:'800',color:platforms[p.id].color}}>{platforms[p.id].name}</Text><Switch accessibilityLabel={`Enable ${platforms[p.id].name}`} disabled={busy} value={p.enabled} trackColor={{true:C.green}} onValueChange={async enabled=>{if(demo){toast('Integration controls require your backend.');return;}setBusy(true);try{await api.setPlatform(p.id,enabled);setData(await api.admin());toast('Integration updated.');}catch(e){toast((e as Error).message);}finally{setBusy(false);}}}/></View><Label muted style={{fontSize:12}}>{p.status}</Label><Label muted style={{fontSize:11,marginTop:4}}>Last sync: {p.syncedAt?new Date(p.syncedAt).toLocaleString():'No synchronization recorded'}</Label></View>)}
 {!demo&&<><RecommendationFeedbackAnalytics key={'feedback:'+user?.id}/><RetailerOperations key={'retailers:'+user?.id} location={location.id}/><ControlledPilotAdmin area={location.id} adminId={user?.id??''}/><PriceDatasetCoverage key={'prices:'+user?.id}/><CatalogueReview onPublished={()=>{reloadProducts(n=>n+1);void refresh();void api.admin().then(setData).catch(e=>setError(e.message));}}/></>}
 <View style={[ui.row,{justifyContent:'space-between',marginTop:28,marginBottom:14}]}><Heading style={{fontSize:20}}>Product catalogue</Heading><Pressable accessibilityRole="button" onPress={()=>edit({id:`product-${Date.now()}`,name:'',brand:'',quantity:'',category:'',description:'',art:'box',color:'#78A38A',offers:[]})} style={ui.pill}><Label style={{color:C.green,fontWeight:'700',fontSize:12}}>+ Add product</Label></Pressable></View>
 {!demo&&<View style={{gap:8,marginBottom:14}}><Field label="Search active catalogue" value={query} onChangeText={setQuery} maxLength={100}/>
  <Button title="Search catalogue" secondary onPress={()=>{setPage(0);setSearch(query.trim());reloadProducts(n=>n+1);}}/>
  <View style={[ui.row,{gap:8}]}><Button title="Previous catalogue page" secondary disabled={page===0} onPress={()=>setPage(n=>n-1)}/>
   <Button title="Next catalogue page" secondary disabled={!listing?.hasNext} onPress={()=>setPage(n=>n+1)}/></View>
 </View>}
 {editing&&<View style={[ui.card,{marginBottom:20}]}><Heading style={{fontSize:19,marginBottom:20}}>Product editor</Heading><Field label="Product name" value={name} onChangeText={setName}/><Field label="Brand" value={brand} onChangeText={setBrand}/><Field label="Quantity / variant" value={quantity} onChangeText={setQuantity}/><Field label="Category" value={category} onChangeText={setCategory}/><Field label="Description" value={description} onChangeText={setDescription} multiline/><Button title="Save product" onPress={save} loading={busy}/><View style={{marginTop:10}}><Button title="Cancel" secondary onPress={()=>setEditing(null)}/></View>{products.some(p=>p.id===editing.id)&&<Pressable accessibilityRole="button" onPress={()=>demo?toast('Deletion requires a connected backend.'):setConfirmDelete(true)} style={{padding:15,alignItems:'center'}}><Label style={{color:C.red}}>Delete product</Label></Pressable>}{confirmDelete&&<View style={{gap:12}}><Label>Remove this product from the active catalogue? Existing shopping and price history will be kept.</Label><Button title="Confirm removal" onPress={remove} loading={busy}/></View>}</View>}
 {products.map(p=><Pressable key={p.id} accessibilityRole="button" onPress={()=>edit(p)} style={[ui.card,{marginBottom:8,padding:14}]}><Label style={{fontWeight:'700'}}>{p.name}</Label><Label muted style={{fontSize:11}}>{p.brand} · {p.quantity} · {p.category}</Label><Label muted style={{fontSize:10,marginTop:4}}>{p.offers.length} price records · Tap to edit</Label></Pressable>)}<Heading style={{fontSize:20,marginTop:28,marginBottom:14}}>Registered customers</Heading>{users.length?users.map(u=><View key={u.id} style={[ui.card,{marginBottom:8}]}><Label>{u.name}</Label><Label muted>{u.email}</Label></View>):<Label muted>{demo?'No real customer data in preview mode.':'No registered customers returned.'}</Label>}
 </View>;
}
