import {request} from './api';
import type {Meal,RecipeEntry,RecipePlan} from './recipe-intelligence';
import type {GroceryItem} from './weekly-shopping-input';

export type WeeklyDraft={meals:Meal[];items:GroceryItem[];budgetMinor:number|null;diet:'ANY'|'VEGETARIAN'|'NON_VEGETARIAN';onHand:Record<string,number>};
export type PlanWorkspace={draft:WeeklyDraft;revision:number;recipes:RecipeEntry[];cart:GroceryItem[];householdSize:number|null;sample:boolean};
export type QuoteEvidence={retailer:string;priceMinor:number;unitPriceMinor:number;unit:string;stockPacks:number|null;observedAt:string;source:string;
 origin?:'LIVE_RESPONSE'|'RETRIEVED_OFFER'|'RECORDED';nativeId?:string|null;requestId?:string|null;freshUntil?:string;providerObservedAt?:string|null};
type PlannedPack={productId:string;name:string;pack:string;count:number;quote:QuoteEvidence};
export type PlanBasket={label:string;totalMinor:number|null;pricedItemsMinor:number;feesMinor:number|null;missing:string[];reason:string;packs:PlannedPack[]};
export type PlanPreview={ingredients:RecipePlan['ingredients'];comparison:{singleRetailers:PlanBasket[];split:PlanBasket|null;recommended:PlanBasket;searchComplete:boolean};
 budgetMinor:number|null;budgetStatus:string;method:string;checkedAt:string;notes:string[];sample:boolean};
export type PreviewResponse={plan:PlanPreview;planFeedbackId:string;basketFeedbackId:string};
export type FeedbackCategory='PLAN'|'BASKET'|'BULK_BUY'|'PERSONALIZED'|'ASSISTANT';
export type FeedbackOutcome='ACCEPTED'|'REJECTED'|'MODIFIED'|'NOT_RELEVANT';
export type FeedbackAnalytics={days:number;minimumDistinctAccounts:number;results:{category:FeedbackCategory;outcome:FeedbackOutcome;count:number}[]};
export type AssistantReply={
 intent:string;method:string;text:string;sample:boolean;feedbackId:string;plan:PlanPreview|null;
 products:{variantId:string;title:string;pack:string;offers:QuoteEvidence[]}[];
 pantry:{productId:string;name:string;amountBase:number;baseUnit:string}[];
 timing:null|{variantId:string;retailer:string;lowMinor:number;highMinor:number;date:string;modelVersion:string;advice:{action:string;reason:string}};
 bulk:null|{name:string;action:string;reason:string;unit:string;pantry:number;required:number|null;budgetMinor:number|null;
  normal:{quote:QuoteEvidence}|null;recommended:{pack:string;packCount:number;subtotalMinor:number;quote:QuoteEvidence}|null};
 suggestions:null|{budgetMinor:number|null;products:{product:{id:string;name:string;quantity:string};reason:string;quote:QuoteEvidence|null;
  alternative:{product:{name:string;quantity:string};quote:QuoteEvidence;packCount:number;subtotalMinor:number}|null}[];
  recipes:{id:string;name:string;missingIngredients:number;estimatedSubtotalMinor:number|null}[];pantryGaps:{name:string;reason:string}[]};
};
const root='/me/shopping-intelligence';
const endpoint=(locationId:string,path='')=>root+'/weekly'+path+'?locationId='+encodeURIComponent(locationId);
const data=(method:'PUT'|'POST',body:unknown)=>({method,body:JSON.stringify(body)});
export const weeklyShopping={
 load:(locationId:string,signal?:AbortSignal)=>request<PlanWorkspace>(endpoint(locationId),{signal}),
 preview:(locationId:string,draft:WeeklyDraft)=>request<PreviewResponse>(endpoint(locationId,'/preview'),data('POST',draft)),
 save:(locationId:string,draft:WeeklyDraft,revision:number)=>request<{revision:number}>(endpoint(locationId),data('PUT',{draft,revision})),
 clear:(locationId:string)=>request<void>(endpoint(locationId),{method:'DELETE'}),
 ask:(locationId:string,message:string,draft:WeeklyDraft|null,variantId:string|null,daysBeforeExpiry:number|null,gemini=false)=>
  request<AssistantReply>(endpoint(locationId,gemini?'/assistant/gemini':'/assistant'),data('POST',{message,draft,variantId,daysBeforeExpiry})),
 rate:(reference:string,category:FeedbackCategory,outcome:FeedbackOutcome)=>request(root+'/feedback/'+encodeURIComponent(reference),data('PUT',{category,outcome})),
 clearFeedback:()=>request<void>(root+'/feedback',{method:'DELETE'}),
 analytics:()=>request<FeedbackAnalytics>('/admin/shopping-intelligence/feedback'),
};
