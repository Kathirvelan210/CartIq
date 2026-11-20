export type BrowseIntent={scope:string;used:boolean};
const retailers=['blinkit','zepto','instamart'] as const;
type Ticket={retailer:string;query:string;expiresAt:string};
type Recent={retailer:string;freshUntil:string};

export function claimBrowseTickets<T extends Ticket>(intent:BrowseIntent|undefined,scope:string,query:string,
 tickets:T[],recorded:Recent[],now:number):T[]{
 if(!intent||intent.used||intent.scope!==scope)return [];
 intent.used=true;
 const term=query.normalize('NFKC').trim().toLowerCase().replace(/\s+/g,' ');
 const selected:T[]=[];
 for(const retailer of retailers){
  if(recorded.some(row=>row.retailer===retailer&&Date.parse(row.freshUntil)>now))continue;
  const ticket=tickets.find(row=>row.retailer===retailer&&row.query===term&&Date.parse(row.expiresAt)>now);
  if(ticket)selected.push(ticket);
 }
 return selected;
}

export async function searchAuthorizedRetailers<T extends Ticket>(tickets:T[],send:(ticket:T)=>Promise<string|undefined>,signal:AbortSignal){
 for(const ticket of tickets){
  if(signal.aborted||Date.parse(ticket.expiresAt)<=Date.now())break;
  const billing=await send(ticket);
  if(!['CHARGED_SETTLED','NO_CHARGE_SETTLED','NOT_DISPATCHED'].includes(billing??''))break;
 }
}
