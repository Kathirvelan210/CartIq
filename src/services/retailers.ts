import {PlatformId} from '../types';
import {DEMO,request} from './api';
import {createRetailerService,PaymentIntent,PaymentMethod} from './checkout';
export const retailerService=(id:PlatformId)=>createRetailerService(id,request,!DEMO);
// Register only an authorized gateway SDK bridge. It must present the gateway's
// own secure UI; payment credentials must never be collected by CartIQ fields.
export type PaymentBridge={methods:PaymentMethod[];present:(intent:PaymentIntent)=>Promise<void>};
let bridge:PaymentBridge|null=null;
export function registerPaymentBridge(value:PaymentBridge){bridge=value;}
export const paymentBridge=()=>bridge;
