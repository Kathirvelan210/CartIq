import type {RetailerComparison,RetailerLookup,RetailerProduct} from './api';
import {listingMatch} from './live-retailers';
import {fruitIdentity} from './fruit-identity';
type Row=RetailerComparison['retailers'][number];
export type Offer={retailer:RetailerLookup;item:RetailerProduct;at:string;fresh:boolean};
export function productGroups(rows:Row[],now:number){
 const groups:Offer[][]=[];
 for(const row of rows){
  const data=row.result;if(!data)continue;
  const fresh=(data.freshUntil?Date.parse(data.freshUntil):Date.parse(data.fetchedAt)+900000)>now;
  for(const item of data.products){
   const offer={retailer:row.retailer,item,at:data.fetchedAt,fresh};
   const candidates=groups.filter(g=>g.every(o=>listingMatch(o.item,item).decision==='confirmed'));
   // A duplicated SKU candidate on either side cannot become a cross-retailer winner.
   const ambiguous=data.products.some(other=>item!==other&&listingMatch(item,other).decision==='confirmed');
   if(!ambiguous&&candidates.length===1&&!candidates[0]!.some(o=>o.retailer===row.retailer))candidates[0]!.push(offer);
   else groups.push([offer]);
  }
 }
 return groups;
}

// Alternatives retain their own pack, listing and price. They never enter a group's price ranking.
export function relatedFruitOffers(groups:Offer[][]){
 const byFruit=new Map<string,Offer[]>();
 for(const group of groups)for(const offer of group){
  const fruit=fruitIdentity(offer.item);
  if(!fruit||!offer.fresh)continue;
  const bucket=byFruit.get(fruit.title)??[];
  bucket.push(offer);byFruit.set(fruit.title,bucket);
 }
 return groups.map(group=>{
  const alternatives=new Map<RetailerLookup,{offer:Offer;score:number}>();
  const base=group[0]!;
  const fruit=fruitIdentity(base.item);
  if(!fruit)return alternatives;
  for(const offer of byFruit.get(fruit.title)??[]){
   if(group.some(member=>member.retailer===offer.retailer))continue;
   if((['variant','flavour','formulation'] as const).some(key=>
    (base.item[key]??'').trim().toLowerCase()!==(offer.item[key]??'').trim().toLowerCase()))continue;
   const match=listingMatch(base.item,offer.item);
   if(match.decision==='confirmed'||match.reason.includes('barcode'))continue;
   if(!alternatives.has(offer.retailer)||match.score>alternatives.get(offer.retailer)!.score)
    alternatives.set(offer.retailer,{offer,score:match.score});
  }
  return alternatives;
 });
}
