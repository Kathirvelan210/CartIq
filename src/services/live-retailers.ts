import {fruitIdentity,fruitWeight} from './fruit-identity';
export type RetailerId = 'blinkit'|'zepto'|'instamart';
export const retailerIds:readonly RetailerId[]=['blinkit','zepto','instamart'];

export function unattemptedRetailers(selected:readonly RetailerId[],attempted:readonly RetailerId[]):RetailerId[] {
 return retailerIds.filter(retailer=>selected.includes(retailer)&&!attempted.includes(retailer));
}

export function mergeRetailerRows<T extends {retailer:RetailerId}>(current:readonly T[],incoming:readonly T[]):T[] {
 const rows=new Map([...current,...incoming].map(row=>[row.retailer,row] as const));
 return retailerIds.flatMap(retailer=>rows.has(retailer)?[rows.get(retailer)!]:[]);
}
export type PriceCandidate = {title:string;brand:string|null;available:boolean|null;priceMinor:number|null;currency:string;
 variant?:string|null; inventory?:number|null; mandatoryFeesMinor?:number|null; deliveryFeeMinor?:number|null; gtin?:string|null;barcode?:string|null;flavour?:string|null;formulation?:string|null;category?:string|null;pack?:string|null;
 normalizedPack:{count:number;each:{amount:number;unit:string}}|null};

const clean=(value?:string|null)=>(value??'').normalize('NFKC').trim().toLowerCase().replace(/\s+/g,' ');

function packKey(item:PriceCandidate):string|null {
 const pack=item.normalizedPack;
 if(fruitIdentity(item)){
  const range=fruitWeight(item.pack);
  if(range)return JSON.stringify([range,pack?.count??1]);
  if(!pack){
   const exact=clean(item.pack).match(/^(\d+(?:\.\d+)?)\s*(kg|g|pc|pcs|pieces?)$/);
   if(exact&&Number(exact[1])>0)return JSON.stringify([1,Number(exact[1])*(exact[2]==='kg'?1000:1),/^(?:kg|g)$/.test(exact[2]!)?'g':'piece']);
  }
 }
 if(!pack||!Number.isSafeInteger(pack.count)||pack.count<1||!Number.isFinite(pack.each.amount)||pack.each.amount<=0)return null;
 return JSON.stringify([pack.count,pack.each.amount,pack.each.unit]);
}

function barcodeEvidence(item:PriceCandidate) {
 const inputs=[item.gtin,item.barcode].filter((value):value is string=>!!value?.trim()).map(value=>value.trim());
 const normalized=inputs.map(value=>{
  if(!/^(?:\d{8}|\d{12}|\d{13}|\d{14})$/.test(value))return null;
  const check=[...value.slice(0,-1)].reverse().reduce((sum,digit,i)=>sum+Number(digit)*(i%2===0?3:1),0);
  const gtin=value.padStart(14,'0');
  return /^0+$|^0200/.test(gtin)||(10-check%10)%10!==Number(value[value.length-1])?null:gtin;
 });
 return {gtin:normalized[0]??null,conflict:normalized.some(value=>value===null||value!==normalized[0])};
}

export function listingMatch(left:PriceCandidate,right:PriceCandidate) {
 const leftCode=barcodeEvidence(left),rightCode=barcodeEvidence(right);
 const shared=!!leftCode.gtin&&leftCode.gtin===rightCode.gtin;
 const fruit=fruitIdentity(left),otherFruit=fruitIdentity(right);
 const produce=!!fruit&&!!otherFruit;
 const brand=produce?fruit.brand:clean(left.brand),otherBrand=produce?otherFruit.brand:clean(right.brand);
 const title=produce?fruit.title:clean(left.title),otherTitle=produce?otherFruit.title:clean(right.title);
 const pack=packKey(left),otherPack=packKey(right);
 const fields=[
  [brand,otherBrand,'brand'],[pack,otherPack,'pack'],
  [clean(left.variant),clean(right.variant),'variant'],[clean(left.flavour),clean(right.flavour),'flavour'],[clean(left.formulation),clean(right.formulation),'formulation'],
 ] as const;
 const conflicts=fields.filter(([a,b])=>a&&b&&a!==b).map(([, ,label])=>label);
 const partialVariant=fields.slice(2).some(([a,b])=>Boolean(a)!==Boolean(b));
 const score=(title&&title===otherTitle?35:0)+(brand===otherBrand?(brand?25:produce?20:0):0)
  +(pack&&pack===otherPack?30:0)+(shared?10:0);
 if(Boolean(fruit)!==Boolean(otherFruit))return {decision:'review' as const,score,reason:'Fruit identity or preparation differs'};
 if(leftCode.conflict||rightCode.conflict)return {decision:'review' as const,score,reason:'Conflicting or invalid barcode evidence'};
 if(conflicts.length)return {decision:shared?'review' as const:'distinct' as const,score,
  reason:shared?'Shared barcode conflicts with '+conflicts.join(', '):'Different '+conflicts.join(', ')};
 if(leftCode.gtin&&rightCode.gtin&&!shared)return {decision:'distinct' as const,score,reason:'Different verified-format barcodes'};
 if((brand||produce)&&brand===otherBrand&&title&&title===otherTitle&&pack&&pack===otherPack&&!partialVariant){
  return {decision:'confirmed' as const,score,reason:produce?'Same fruit, preparation and labelled pack':shared?'Matching barcode, title, brand and pack':'Matching title, brand and pack'};
 }
 return {decision:'review' as const,score,reason:partialVariant?'Flavour or formulation needs confirmation':'Incomplete or differing listing identity'};
}

export function listingUnitPrice(item:PriceCandidate) {
 const pack=item.normalizedPack;
 if(!packKey(item)||!pack||fruitWeight(item.pack)||!knownPrice(item))return null;
 const unit=({g:'kg',ml:'litre',piece:'piece'} as Record<string,string>)[pack.each.unit];
 if(!unit)return null;
 return {unit,minor:Math.round(item.priceMinor!*(unit==='piece'?1:1000)/(pack.count*pack.each.amount))};
}

const knownPrice=(item:PriceCandidate)=>item.currency==='INR'&&item.priceMinor!==null
 &&Number.isSafeInteger(item.priceMinor)&&item.priceMinor>=0;

export function filterListings<T extends PriceCandidate>(products:readonly T[],query:string,availableOnly:boolean,
 sort:'relevance'|'low'|'high'):T[] {
 const words=clean(query).split(' ').filter(Boolean);
 const visible=products.filter(item=>(!availableOnly||item.available===true)
  &&words.every(word=>clean([item.title,item.brand,item.category,item.pack,item.flavour,item.formulation].filter(Boolean).join(' ')).includes(word)));
 if(sort==='relevance')return visible;
 return visible.sort((a,b)=>{
  if(!knownPrice(a)||!knownPrice(b))return Number(knownPrice(b))-Number(knownPrice(a));
  return (sort==='low'?1:-1)*(a.priceMinor!-b.priceMinor!);
 });
}

type RetailerItems<T>={retailer:RetailerId;products:readonly T[];fetchedAt?:string};
type ListingRow<T>={retailer:RetailerId;item:T;fetchedAt?:string};

export function listingReview<T extends PriceCandidate>(retailers:readonly RetailerItems<T>[]) {
 const decisions:{left:ListingRow<T>;right:ListingRow<T>;match:ReturnType<typeof listingMatch>}[]=[];
 const words=(title:string)=>new Set(clean(title).split(/[^\p{L}\p{N}]+/u).filter(word=>word.length>2));
 for(let i=0;i<retailers.length;i++)for(const left of retailers[i]!.products){
  const leftWords=words(left.title),leftCode=barcodeEvidence(left).gtin;
  for(const retailer of retailers.slice(i+1))for(const right of retailer.products){
   const overlap=[...words(right.title)].filter(word=>leftWords.has(word)).length;
   const sameBrand=!!clean(left.brand)&&clean(left.brand)===clean(right.brand);
   if(!(leftCode&&leftCode===barcodeEvidence(right).gtin)&&!(sameBrand&&overlap>=2))continue;
   const match=listingMatch(left,right);
   if(match.decision==='confirmed')continue;
   decisions.push({left:{retailer:retailers[i]!.retailer,item:left},right:{retailer:retailer.retailer,item:right},match});
  }
 }
 return decisions.sort((a,b)=>b.match.score-a.match.score);
}

export function matchingPrices<T extends PriceCandidate>(
 retailers:readonly RetailerItems<T>[],
) {
 const groups=new Map<string,ListingRow<T>[]>();
 for(const row of retailers){
  for(const item of row.products){
   const pack=packKey(item);
   const fruit=fruitIdentity(item);
   if(!pack||(!fruit&&!clean(item.brand))||!clean(item.title))continue;
   const key=JSON.stringify([fruit?'fruit':'listing',fruit?.brand??clean(item.brand),fruit?.title??clean(item.title),pack,clean(item.variant),clean(item.flavour),clean(item.formulation)]);
   const offers=groups.get(key)||[];
   offers.push({retailer:row.retailer,item,fetchedAt:row.fetchedAt});groups.set(key,offers);
  }
 }
 return [...groups.values()].flatMap(rows=>{
  if(new Set(rows.map(row=>row.retailer)).size!==rows.length)return [];
  if(rows.some((row,i)=>rows.slice(i+1).some(other=>listingMatch(row.item,other.item).decision!=='confirmed')))return [];
  const eligible=rows.filter(({item})=>item.available===true&&item.inventory!==0&&knownPrice(item));
  if(eligible.length<2)return [];
  const lowest=Math.min(...eligible.map(row=>row.item.priceMinor!));
  const highest=Math.max(...eligible.map(row=>row.item.priceMinor!));
  const score=Math.min(...eligible.flatMap((row,i)=>eligible.slice(i+1).map(other=>listingMatch(row.item,other.item).score)));
  const totals=eligible.flatMap(({retailer,item})=>{
   const delivery=item.deliveryFeeMinor,fees=item.mandatoryFeesMinor;
   if(delivery==null||fees==null||![delivery,fees].every(n=>Number.isSafeInteger(n)&&n>=0))return [];
   return [{retailer,total:item.priceMinor!+delivery+fees}];
  });
  const minimum=Math.min(...totals.map(row=>row.total));
  const payable=totals.length===eligible.length?{lowest:minimum,retailers:totals.filter(row=>row.total===minimum).map(row=>row.retailer)}:null;
  return [{payable,item:eligible[0]!.item,lowest,retailers:eligible.filter(row=>row.item.priceMinor===lowest).map(row=>row.retailer),
   offers:rows,saving:highest-lowest,savingPercent:highest>0?100*(highest-lowest)/highest:0,score}];
 });
}

export function retailerMessage(status:string):string {
 switch(status){
  case 'NO_RESULTS':return 'No products matched this search.';
  case 'UNAVAILABLE':return 'The retailer or item is unavailable at this location.';
  case 'TIMEOUT':return 'The retailer request timed out.';
  case 'NOT_SUPPORTED':return 'The provider did not support this lookup.';
  case 'PROVIDER_ERROR':return 'The retailer lookup failed.';
  case 'NOT_ATTEMPTED':return 'No request was sent to this retailer.';
  default:return '';
 }
}
