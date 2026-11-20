import React,{useEffect,useRef,useState} from 'react';
import {Linking,Modal,Platform,ScrollView,View} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {X} from 'lucide-react-native';
import {useStore} from '../store';
import {C} from '../theme';
import {getDevicePosition} from '../services/device-location';
import {deviceSelection,manualSelection,LocationPermissionError,shoppingLocationLabel,type SelectedLocation} from '../services/shopping-location';
import {Button,Field,Heading,IconButton,Label,ui} from './ui';

export function LocationPicker({close}:{close:()=>void}){
 const store=useStore();
 const [manual,setManual]=useState(false),[latitude,setLatitude]=useState(''),[longitude,setLongitude]=useState(''),[name,setName]=useState(''),[pin,setPin]=useState('');
 const [candidate,setCandidate]=useState<SelectedLocation|null>(null),[pending,setPending]=useState<'gps'|'save'|null>(null);
 const [error,setError]=useState(''),[settings,setSettings]=useState(false);
 const sequence=useRef(0),saving=useRef(false),gps=useRef<AbortController|null>(null);
 useEffect(()=>()=>{sequence.current++;gps.current?.abort();},[store.user?.id]);
 function reset(){
  sequence.current++;gps.current?.abort();setCandidate(null);setPending(null);setError('');setSettings(false);
 }
 async function locate(){
  reset();setManual(false);setPending('gps');
  const attempt=sequence.current;
  gps.current=new AbortController();
  try{
   const point=deviceSelection(await getDevicePosition(gps.current.signal));
   if(attempt===sequence.current)setCandidate(point);
  }catch(problem){
   if(attempt===sequence.current){
    setError((problem as Error).message);
    setSettings(problem instanceof LocationPermissionError&&!problem.canAskAgain);
   }
  }finally{if(attempt===sequence.current)setPending(null);}
 }
 async function save(place:SelectedLocation|null){
  if(saving.current)return;
  const attempt=sequence.current;
  saving.current=true;setPending('save');setError('');
  try{
   if(place?.source==='gps'&&(!place.timestamp||Date.now()-place.timestamp>=900000))
    throw new Error('This device fix has expired. Use current location for a fresh reading.');
   await store.chooseShoppingLocation(place);
   if(attempt===sequence.current)close();
  }catch(problem){if(attempt===sequence.current)setError((problem as Error).message);}
  finally{saving.current=false;if(attempt===sequence.current)setPending(null);}
 }
 function saveManual(){
  try{void save(manualSelection(latitude,longitude,name,pin));}
  catch(problem){setError((problem as Error).message);}
 }
 const disabled=!store.user||!store.locationSettingsReady||pending==='save';
 return <Modal visible transparent animationType="slide" onRequestClose={close}>
  <View style={{flex:1,backgroundColor:'#10281F88',justifyContent:'center',alignItems:'center',padding:16}}>
   <SafeAreaView edges={['bottom']} style={{backgroundColor:C.bg,borderRadius:24,padding:20,maxHeight:'92%',width:'100%',maxWidth:500}}>
    <View style={[ui.row,{justifyContent:'space-between',marginBottom:12}]}>
     <Heading style={{fontSize:23,flex:1}}>Shopping location</Heading>
     <IconButton label="Close location selector" onPress={close}><X size={21} color={C.text}/></IconButton>
    </View>
    <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={{gap:12}}>
     <Label muted>{store.user?'Saved on this device for your account. CartIQ uses the coordinates when you request local results.':'Sign in to save your shopping location on this device.'}</Label>
     <Button title="Use current location" disabled={disabled||pending==='gps'} loading={pending==='gps'} onPress={()=>void locate()}/>
     {pending==='gps'&&<Label muted>Waiting for your device. You can enter coordinates manually while you wait.</Label>}
     <Button title={manual?'Close manual entry':'Enter location manually'} secondary disabled={disabled} onPress={()=>{reset();if(!manual){const place=store.savedSelection;setName(place?.label??'');setLatitude(place?String(place.latitude):'');setLongitude(place?String(place.longitude):'');setPin(place?.postcode??'');}setManual(!manual);}}/>
     {manual&&<>
      <Field label="Location name (optional)" value={name} onChangeText={setName} maxLength={100}/>
      <Field label="Delivery PIN (optional)" value={pin} onChangeText={setPin} maxLength={6} keyboardType="number-pad"/>
      <Field label="Latitude" value={latitude} onChangeText={setLatitude} autoCapitalize="none" autoCorrect={false} maxLength={28}/>
      <Field label="Longitude" value={longitude} onChangeText={setLongitude} autoCapitalize="none" autoCorrect={false} maxLength={28}/>
      <Button title="Save manual location" loading={pending==='save'} disabled={disabled||!latitude.trim()||!longitude.trim()} onPress={saveManual}/>
     </>}
     {candidate&&<View style={[ui.card,{gap:8}]}>
      <Heading style={{fontSize:18}}>Confirm device location</Heading>
      <Label>{candidate.latitude.toFixed(6)}, {candidate.longitude.toFixed(6)}</Label>
      <Label muted>Accuracy about {Math.round(candidate.accuracy!)} m · {new Date(candidate.timestamp!).toLocaleTimeString()}</Label>
      {candidate.androidPrecision==='coarse'&&<Label muted>Your device shared an approximate position.</Label>}
      <Button title="Save current location" disabled={disabled} loading={pending==='save'} onPress={()=>void save(candidate)}/>
     </View>}
     {!!error&&<View accessibilityRole="alert"><Label style={{color:C.red}}>{error}</Label></View>}
     {settings&&Platform.OS!=='web'&&<Button title="Open app settings" secondary onPress={()=>void Linking.openSettings().catch(()=>setError('Open device settings and allow location for CartIQ.'))}/>}
     {store.savedShoppingLocations.length>0&&<View style={[ui.card,{gap:8}]}>
      <Heading style={{fontSize:18}}>Saved locations</Heading>
      {store.savedShoppingLocations.map(place=><View key={JSON.stringify([place.source,place.latitude,place.longitude,place.postcode,place.localityKey])} style={{gap:4}}>
       <Label>{shoppingLocationLabel(place)}{place===store.savedSelection?' · Selected':''}</Label>
       <Label muted>{place.latitude.toFixed(6)}, {place.longitude.toFixed(6)} · {place.source==='manual'?'Entered manually':place.source==='pin'?'Approximate PIN location':'Device location'}</Label>
       <View style={[ui.row,{gap:8,flexWrap:'wrap'}]}>
        <Button secondary title={place.source==='gps'&&Date.now()-(place.timestamp??0)>=900000?'Get fresh device location':'Use location'}
         disabled={disabled||pending==='gps'} onPress={()=>place.source==='gps'&&Date.now()-(place.timestamp??0)>=900000?void locate():void save(place)}/>
        <Button secondary title="Remove" disabled={disabled} onPress={()=>store.removeShoppingLocation(place)}/>
       </View>
      </View>)}
     </View>}
    </ScrollView>
   </SafeAreaView>
  </View>
 </Modal>;
}
