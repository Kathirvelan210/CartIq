import {Platform} from 'react-native';
import * as Location from 'expo-location';
import {browserPosition,foregroundGrant,nativePosition} from './shopping-location';

export function getDevicePosition(signal?:AbortSignal){
 if(Platform.OS==='web'){
  const browser=globalThis.navigator;
  return browserPosition(browser?.geolocation,globalThis.isSecureContext!==false,
   browser?.permissions?async()=> (await browser.permissions.query({name:'geolocation'})).state:undefined,signal);
 }
 return nativePosition({
  permission:()=>foregroundGrant(Location.getForegroundPermissionsAsync,Location.requestForegroundPermissionsAsync),
  services:()=>Location.hasServicesEnabledAsync(),
  position:async()=>{
   // Android's fused provider applies the user's permission granularity to the result.
   const {coords,timestamp}=await Location.getCurrentPositionAsync({accuracy:Location.Accuracy.High});
   return {latitude:coords.latitude,longitude:coords.longitude,accuracy:coords.accuracy,timestamp};
  },
 },30_000);
}
