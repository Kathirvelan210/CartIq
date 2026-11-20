import {Location,PlatformId} from '../types';
import {CartEntry} from './cart';
export type Address={id:string;locationId:string;label:string;name:string;phone:string;line1:string;line2:string;city:string;postcode:string;isDefault:boolean};
export type PaymentMethod='upi'|'card'|'netbanking'|'cod';
export type CheckoutItem={productId:string;quantity:number};
export type CheckoutRequest={locationId:string;items:CheckoutItem[];address?:Address};
export type RetailerCapabilities={ordering:boolean;paymentMethods:PaymentMethod[]};
export type RetailerQuote={id:string;platform:PlatformId;locationId:string;items:{productId:string;quantity:number;unitPrice:number;available:boolean;updatedAt:string}[];subtotal:number;discount:number;deliveryCharge:number|null;handlingCharge:number|null;estimatedTotal:number|null;expiresAt:string;availabilityConfirmed:boolean;addressValidated:boolean;deliveryEstimate?:string};
export type OrderStatus={id:string;platform:PlatformId;locationId:string;items:CheckoutItem[];status:'pending'|'confirmed'|'completed'|'cancelled'|'failed';paymentStatus:'pending'|'paid'|'failed'|'cod_due';total:number};
export type PaymentIntent={id:string;orderId:string;gateway:string;clientToken:string};
export interface RetailerService {
 getCapabilities():Promise<RetailerCapabilities>;
 getRetailerCart(request:CheckoutRequest):Promise<RetailerQuote>;
 validateRetailerCart(request:CheckoutRequest):Promise<RetailerQuote>;
 createRetailerOrder(quoteId:string,method:PaymentMethod,idempotencyKey:string):Promise<{id:string}>;
 initiatePayment(orderId:string,method:PaymentMethod,idempotencyKey:string):Promise<PaymentIntent>;
 getOrderStatus(orderId:string):Promise<OrderStatus>;
 getOrderByKey(key:string):Promise<OrderStatus|null>;
 cancelOrder(orderId:string):Promise<OrderStatus>;
}
export const paymentLabels:Record<PaymentMethod,string>={upi:'UPI',card:'Credit / debit card',netbanking:'Net banking',cod:'Cash on delivery'};
export function addressError(a:Address,location:Location){if(a.locationId!==location.id||a.postcode!==location.postcode||a.city.trim().toLowerCase()!==location.city.trim().toLowerCase())return 'Choose an address in the selected delivery area and PIN code.';if(a.name.trim().length<2||a.name.length>80)return 'Enter a recipient name (2–80 characters).';if(!/^[6-9]\d{9}$/.test(a.phone.replace(/\s/g,'')))return 'Enter a valid 10-digit Indian mobile number.';if(a.line1.trim().length<5||a.line1.length>150||a.line2.length>150||a.label.length>30)return 'Enter a valid house number and street (5–150 characters).';return '';}
export function quoteFingerprint(q:RetailerQuote){return JSON.stringify([q.platform,q.locationId,q.items.map(i=>[i.productId,i.quantity,i.unitPrice,i.available]).sort(),q.subtotal,q.discount,q.deliveryCharge,q.handlingCharge,q.estimatedTotal]);}
export function assertValidQuote(q:RetailerQuote,r:CheckoutRequest,platform:PlatformId,requireAddress=false){
 const finite=(n:unknown):n is number=>typeof n==='number'&&Number.isFinite(n)&&n>=0;
 if(!q||!q.id||q.platform!==platform||q.locationId!==r.locationId||!Array.isArray(q.items)||q.items.length!==r.items.length||new Set(q.items.map(i=>i.productId)).size!==q.items.length)throw new Error('The retailer returned an invalid cart. Please refresh.');
 if(!Number.isFinite(Date.parse(q.expiresAt))||Date.parse(q.expiresAt)<=Date.now())throw new Error('Your retailer quote has expired. Refresh before continuing.');
 if(!q.availabilityConfirmed||requireAddress&&!q.addressValidated)throw new Error('Retailer availability or delivery address is not confirmed.');
 for(const item of r.items){const line=q.items.find(i=>i.productId===item.productId);const time=line?Date.parse(line.updatedAt):NaN;if(!line||line.quantity!==item.quantity||!line.available||!finite(line.unitPrice)||!Number.isFinite(time)||Date.now()-time>15*60000||time>Date.now()+60000)throw new Error('An item is unavailable or its price is stale. Return to your cart and refresh.');}
 if(!finite(q.subtotal)||!finite(q.discount)||q.discount>q.subtotal||Math.abs(q.items.reduce((n,i)=>n+i.unitPrice*i.quantity,0)-q.subtotal)>.01)throw new Error('Retailer pricing could not be verified.');
 for(const charge of [q.deliveryCharge,q.handlingCharge])if(charge!==null&&!finite(charge))throw new Error('Invalid retailer fees.');
 const expected=q.subtotal-q.discount+(q.deliveryCharge??0)+(q.handlingCharge??0);
 if(q.estimatedTotal!==null&&(!finite(q.estimatedTotal)||q.deliveryCharge===null||q.handlingCharge===null||Math.abs(q.estimatedTotal-expected)>.01))throw new Error('The retailer total could not be verified.');
}
export function confirmedOrder(order:OrderStatus,platform:PlatformId,request:CheckoutRequest){return !!order&&typeof order.id==='string'&&order.id.length>0&&order.platform===platform&&order.locationId===request.locationId&&['confirmed','completed'].includes(order.status)&&['paid','cod_due'].includes(order.paymentStatus)&&Number.isFinite(order.total)&&order.total>=0&&Array.isArray(order.items)&&order.items.length===request.items.length&&new Set(order.items.map(i=>i.productId)).size===order.items.length&&request.items.every(i=>order.items.some(o=>o.productId===i.productId&&o.quantity===i.quantity));}
export function removeConfirmedItems(cart:CartEntry[],order:OrderStatus,request:CheckoutRequest){if(!confirmedOrder(order,order.platform,request))return cart;return cart.flatMap(e=>{if(e.locationId!==request.locationId)return [e];const bought=request.items.find(i=>i.productId===e.productId);if(!bought)return [e];const remaining=e.quantity-bought.quantity;return remaining>0?[{...e,quantity:remaining}]:[];});}
export type Transport=<T>(path:string,options?:RequestInit)=>Promise<T>;
export function createRetailerService(platform:PlatformId,transport:Transport,connected:boolean):RetailerService {
 const base=`/retailers/${platform}`;
 const requireConnected=()=>{if(!connected||platform==='zepto')throw new Error('Direct checkout is not yet supported by this retailer. Your cart has been kept.');};
 const post=async<T>(path:string,body:unknown,key?:string):Promise<T>=>{requireConnected();return transport<T>(base+path,{method:'POST',body:JSON.stringify(body),headers:key?{'Idempotency-Key':key}:{}});};
 return {
 getCapabilities:()=>!connected||platform==='zepto'?Promise.resolve({ordering:false,paymentMethods:[]}):transport<RetailerCapabilities>(base+'/capabilities'),
 getRetailerCart:r=>post('/cart',r), validateRetailerCart:r=>post('/cart/validate',r),
 createRetailerOrder:(quoteId,method,key)=>post('/orders',{quoteId,paymentMethod:method},key),
 initiatePayment:(orderId,method,key)=>post('/payments',{orderId,method},key),
 getOrderStatus:async id=>{requireConnected();return transport<OrderStatus>(base+'/orders/'+encodeURIComponent(id));},
 getOrderByKey:async key=>{requireConnected();return transport<OrderStatus|null>(base+'/orders/by-request/'+encodeURIComponent(key));},
 cancelOrder:id=>post('/orders/'+encodeURIComponent(id)+'/cancel',{})
 };
}
