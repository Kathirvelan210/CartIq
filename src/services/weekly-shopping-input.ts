export type GroceryItem={productId:string;quantity:number};

export function integerField(raw:string,max:number,label:string) {
 const value=raw.trim();
 if(!/^\d+$/.test(value)||Number(value)<1||Number(value)>max)throw new Error(`Enter ${label} from 1 to ${max}.`);
 return Number(value);
}
export function weeklyBudget(raw:string) {
 const value=raw.trim();
 if(!value)return null;
 if(!/^\d{1,7}(\.\d{1,2})?$/.test(value)||Number(value)>1000000)throw new Error('Enter a weekly budget up to ₹10,00,000.');
 return Math.round(Number(value)*100);
}
export function includePacks(existing:GroceryItem[],incoming:GroceryItem[]) {
 const keys=new Set(existing.map(line=>line.productId));
 return [...existing,...incoming.filter(line=>{if(keys.has(line.productId))return false;keys.add(line.productId);return true;})].slice(0,30);
}
export function planExpiry(timestamps:string[],checkedAt:string) {
 return Math.min(Date.parse(checkedAt),...timestamps.map(at=>Date.parse(at)))+15*60*1000;
}
