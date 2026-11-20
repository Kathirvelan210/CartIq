import AsyncStorage from '@react-native-async-storage/async-storage';
import {PlatformId} from '../types';
import {CheckoutRequest,PaymentMethod} from './checkout';
import {storageOwner} from './storage-scope';
export type OrderAttempt={key:string;quoteId:string;orderId?:string;platform:PlatformId;request:CheckoutRequest;method:PaymentMethod};
const storageKey=(platform:PlatformId,location:string)=>`cartiq.order-attempt.${storageOwner()}.${platform}.${location}`;
export async function readAttempt(platform:PlatformId,location:string):Promise<OrderAttempt|null>{const raw=await AsyncStorage.getItem(storageKey(platform,location));return raw?JSON.parse(raw):null;}
export async function saveAttempt(attempt:OrderAttempt){const safe={...attempt,request:{locationId:attempt.request.locationId,items:attempt.request.items}};await AsyncStorage.setItem(storageKey(attempt.platform,attempt.request.locationId),JSON.stringify(safe));}
export async function clearAttempt(platform:PlatformId,location:string){await AsyncStorage.removeItem(storageKey(platform,location));}
