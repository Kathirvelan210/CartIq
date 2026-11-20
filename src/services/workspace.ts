import {request} from './api';
import {Product,PlatformId} from '../types';
import {CartEntry,PurchasePreferences} from './cart';

export type Decision={productId:string;action:'BUY_NOW'|'COMPARE_ANOTHER_STORE'|'CHOOSE_ALTERNATIVE_PRODUCT'|'WAIT_AND_MONITOR';reason:string;priceMinor:number|null;platform:PlatformId|null;sample:boolean;alternatives:string[]};
export type Assignment={productId:string;platform:PlatformId;quantity:number;unitPriceMinor:number};
export type BasketOption={label:string;feasible:boolean;subtotalMinor:number|null;feesMinor:number|null;totalMinor:number|null;reason:string;assignments:Assignment[]};
export type Optimization={singleStores:BasketOption[];recommended:BasketOption;savingsMinor:number|null;searchComplete:boolean;sample:boolean;explanation:string};
export type Budget={month:string;limitMinor:number|null;spentMinor:number;remainingMinor:number|null;categories:{category:string;amountMinor:number}[]};
export type Purchase={id:string;platform:PlatformId;totalMinor:number;savingsMinor:number;purchasedAt:string;sample:boolean;itemCount:number};
export type Watch={id:string;productId:string;name:string;quantity:string;locationId:string;targetMinor:number;currentMinor:number|null;platform:PlatformId|null;enabled:boolean;sample:boolean};
export type Notice={id:string;title:string;body:string;kind:string;sample:boolean;read:boolean;createdAt:string;deliveryStatus:string};
export type Recommendation={product:Product;reason:string};
export type MonthSpend={month:string;amountMinor:number};
export type Overview={budget:Budget;recentPurchases:Purchase[];recommendations:Recommendation[];trend:MonthSpend[];watchCount:number;unreadNotifications:number;dueCount:number};
export type Settings={notificationsEnabled:boolean;priceIncreaseAlerts:boolean;replenishmentAlerts:boolean;preferredBrands:string};
export type Replenishment={id:string;productId:string;name:string;cycleDays:number;lastPurchasedAt:string;estimatedNextAt:string;due:boolean;explanation:string};
export type Recipe={id:string;name:string;servings:number;instructions:string;source:string;sample:boolean;color:string;ingredientCount:number;pantryMatches:number};
export type Ingredient={productId:string;name:string;requiredBase:number;pantryBase:number;missingBase:number;baseUnit:string;suggestedVariantId:string|null;packs:number;estimatedMinor:number|null};
export type RecipePlan={recipeId:string;servings:number;ingredients:Ingredient[];estimatedMinor:number;complete:boolean;sample:boolean};
export type Pantry={productId:string;name:string;amountBase:number;baseUnit:string};
export type PriceAnalytics={productId:string;history:{observedAt:string;platform:PlatformId;priceMinor:number}[];minMinor:number|null;maxMinor:number|null;averageMinor:number|null;sample:boolean;forecast:{estimateMinor:number|null;lowMinor:number|null;highMinor:number|null;confidence:number;method:string;caveat:string}};
type ApprovedStats={observations:number;priced:number;minimumMinor:number|null;maximumMinor:number|null;averageMinor:number|null};
export type ApprovedPriceHistory={variantId:string;locationId:string;retailers:{retailer:PlatformId;listingId:string;stats:ApprovedStats;lastObservedAt:string;freshness:'FRESH'|'STALE';priceChangeMinor:number|null;readings:{provider:string;observedAt:string;priceMinor:number|null}[]}[]};
export type PricePrediction={status:'INSUFFICIENT_DATA'|'FORECAST';productId:string;variantId:string;pack:string;retailer:PlatformId;locationId:string;
 latestObservation:{observedAt:string;priceMinor:number|null;stock:'AVAILABLE'|'OUT_OF_STOCK'|'UNKNOWN'}|null;observedChangeMinor:number|null;
 freshness:'UNKNOWN'|'FRESH'|'STALE';horizonDays:number;forecastDate:string|null;predictedMinor:number|null;lowMinor:number|null;highMinor:number|null;
 empiricalCoverage:number|null;modelVersion:string|null;trainedAt:string|null;warning:string;advice:{action:string;reason:string}};
export type AssistantAnswer={text:string;engine:string;products:Product[];sources:string[];sample:boolean};
const json=(method:string,body:unknown)=>({method,body:JSON.stringify(body)});
async function saveCart(item:{productId:string;locationId:string;quantity:number;preferences:PurchasePreferences}) {
 const {productId,locationId,quantity,preferences}=item;
 const save=(values:PurchasePreferences)=>request<CartEntry>('/me/cart',json('PUT',{productId,locationId,quantity,preferences:values}));
 try {
  return await save(preferences);
 } catch (error) {
  if (preferences.intent!=='BULK' || preferences.maxUsefulPacks!==null || preferences.consumptionDays===null
   || !(error instanceof Error) || error.message!=='Bulk purchases need a useful pack count and consumption period.') {
   throw error;
  }
  return save({...preferences,maxUsefulPacks:quantity});
 }
}
export const workspace={
 meta:()=>request<{sample:boolean;orderingEnabled:boolean}>('/meta'),
 categories:()=>request<{id:string;name:string}[]>('/categories'),
 cart:()=>request<CartEntry[]>('/me/cart'),
 saveCart,
 removeCart:(id:string)=>request<void>('/me/cart/'+encodeURIComponent(id),{method:'DELETE'}),
 clearCart:(locationId:string)=>request<void>('/me/cart?locationId='+encodeURIComponent(locationId),{method:'DELETE'}),
 decisions:(locationId:string)=>request<Decision[]>('/me/decisions?locationId='+encodeURIComponent(locationId)),
 optimize:(locationId:string,items:{productId:string;quantity:number}[])=>request<Optimization>('/cart/optimize',json('POST',{locationId,items})),
 overview:(locationId:string)=>request<Overview>('/me/overview?locationId='+encodeURIComponent(locationId)),
 watches:()=>request<Watch[]>('/me/watchlist'),
 watch:(productId:string,locationId:string,targetMinor:number)=>request<Watch>('/me/watchlist',json('PUT',{productId,locationId,targetMinor})),
 unwatch:(id:string)=>request<void>('/me/watchlist/'+encodeURIComponent(id),{method:'DELETE'}),
 checkWatches:()=>request<void>('/me/watchlist/check',{method:'POST'}),
 notifications:()=>request<Notice[]>('/me/notifications'),
 readNotice:(id:string)=>request<void>('/me/notifications/'+encodeURIComponent(id)+'/read',{method:'PATCH'}),
 settings:()=>request<Settings>('/me/preferences'),
 saveSettings:(settings:Settings)=>request<Settings>('/me/preferences',json('PUT',settings)),
 budget:(month?:string)=>request<Budget>('/me/budget'+(month?'?month='+encodeURIComponent(month):'')),
 saveBudget:(month:string,limitMinor:number)=>request<Budget>('/me/budget',json('PUT',{month,limitMinor})),
 expense:(categoryId:string,amountMinor:number,note:string)=>request<void>('/me/expenses',json('POST',{categoryId,amountMinor,note})),
 history:()=>request<Purchase[]>('/me/history'),
 samplePurchase:(requestKey:string,locationId:string,platform:PlatformId,items:{productId:string;quantity:number}[])=>request<Purchase>('/me/history/sample',json('POST',{requestKey,locationId,platform,items})),
 trend:()=>request<MonthSpend[]>('/me/spending-trend'),
 replenishment:()=>request<Replenishment[]>('/me/replenishment'),
 saveCycle:(productId:string,cycleDays:number)=>request<void>('/me/replenishment',json('PUT',{productId,cycleDays})),
 restocked:(id:string)=>request<void>('/me/replenishment/'+encodeURIComponent(id)+'/restocked',{method:'POST'}),
 removeCycle:(id:string)=>request<void>('/me/replenishment/'+encodeURIComponent(id),{method:'DELETE'}),
 recipes:(q='')=>request<Recipe[]>('/recipes?q='+encodeURIComponent(q)),
 recipePlan:(id:string,servings:number,locationId:string,ownedProductIds:string[])=>request<RecipePlan>('/recipes/'+encodeURIComponent(id)+'/plan',json('POST',{servings,locationId,ownedProductIds})),
 recipeCart:(id:string,servings:number,locationId:string,ownedProductIds:string[],selectedVariantIds:Record<string,string>)=>request<CartEntry[]>('/recipes/'+encodeURIComponent(id)+'/cart',json('POST',{plan:{servings,locationId,ownedProductIds},selectedVariantIds})),
 pantry:()=>request<Pantry[]>('/me/pantry'),
 savePantry:(productId:string,amountBase:number,baseUnit:string)=>request<void>('/me/pantry',json('PUT',{productId,amountBase,baseUnit})),
 prices:(id:string,locationId:string)=>request<PriceAnalytics>('/products/'+encodeURIComponent(id)+'/analytics?locationId='+encodeURIComponent(locationId)),
 approvedPrices:(id:string,locationId:string)=>request<ApprovedPriceHistory>('/grocery/variants/'+encodeURIComponent(id)+'/price-history?locationId='+encodeURIComponent(locationId)),
 forecast:(id:string,locationId:string,retailer:PlatformId)=>request<PricePrediction>('/me/price-intelligence/'+encodeURIComponent(id)+'?locationId='+encodeURIComponent(locationId)+'&retailer='+encodeURIComponent(retailer)),
 assistant:(message:string,locationId:string)=>request<AssistantAnswer>('/me/assistant',json('POST',{message,locationId})),
 matchIngredient:(query:string)=>request<{productId:string;name:string;score:number;requiresConfirmation:boolean}[]>('/me/ingredients/match?query='+encodeURIComponent(query)),
 registerPush:(token:string,previousToken?:string)=>request<void>('/me/device-tokens',json('POST',{token,previousToken})),
 unregisterPush:(token:string)=>request<void>('/me/device-tokens',json('DELETE',{token}))
};
