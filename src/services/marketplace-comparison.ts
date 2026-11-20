type Listing={listing:{title:string;pack:string|null;priceMinor:number|null;available:boolean|null;inventory?:number|null};
 evidence:{match:string;variantId:string|null;freshUntil:string}};
type Result={retailer:string;freshUntil:string;fetchedAt:string;listings:Listing[]};
type Price={retailer:string;price:number;fetchedAt:string};
export function freshMarketplaceRows<T extends Result>(rows:T[],now:number){
 return rows.filter(row=>Date.parse(row.freshUntil)>now).map(row=>({
  ...row,listings:row.listings.filter(item=>Date.parse(item.evidence.freshUntil)>now) as T['listings'],
 }));
}
export function comparableListings(rows:Result[],now:number){
 const packs=new Map<string,{title:string;pack:string|null;prices:Map<string,Price>}>();
 for(const row of rows){
  if(!(Date.parse(row.freshUntil)>now))continue;
  for(const {listing,evidence} of row.listings){
   if(evidence.match!=='VERIFIED'||!evidence.variantId||!(Date.parse(evidence.freshUntil)>now)||
    listing.priceMinor==null||listing.available!==true||(listing.inventory!=null&&listing.inventory<=0))continue;
   const group=packs.get(evidence.variantId)??{title:listing.title,pack:listing.pack,prices:new Map<string,Price>()};
   const previous=group.prices.get(row.retailer);
   if(!previous||previous.price>listing.priceMinor)
    group.prices.set(row.retailer,{retailer:row.retailer,price:listing.priceMinor,fetchedAt:row.fetchedAt});
   packs.set(evidence.variantId,group);
  }
 }
 return [...packs.entries()].filter(([,group])=>group.prices.size>=2).map(([variantId,group])=>({
  variantId,title:group.title,pack:group.pack,prices:[...group.prices.values()].sort((a,b)=>a.price-b.price)
 }));
}
