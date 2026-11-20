import {request} from './api';
import type {CartEntry} from './cart';

export type MealType='BREAKFAST'|'LUNCH'|'DINNER'|'SNACK';
export type Diet='VEGETARIAN'|'NON_VEGETARIAN';
export type Difficulty='EASY'|'MEDIUM'|'HARD';
export type Retailer='blinkit'|'zepto';
export type BasketRetailer=Retailer|'best';
export type RecipeEntry={id:string;kind:'CURATED'|'GENERATED';source:string|null;recipe:{name:string;description:string;
 cuisine:string;meal:MealType;diet:Diet;servings:number;prepMinutes:number;cookMinutes:number;difficulty:Difficulty;
 ingredients:{name:string;quantity:number;unit:string}[];steps:string[];substitutions:string[]}};
export type Meal={recipeId:string;day:number;servings:number;alreadyHave?:number[]};
export type RecipeFilter={q:string;cuisine:string;meal:string;diet:string;difficulty:string;maxMinutes:string;budgetMinor?:number;locationId:string};
export type MealRequest={locationId:string;meals:Meal[];budgetMinor?:number;onHand:Record<string,number>;substitutions:Record<string,string>};
export type PackRef={variantId:string;productName:string;brand:string;label:string};
export type RecipePlan={meals:Meal[];sample:boolean;budgetStatus:'NO_BUDGET'|'OVER_BUDGET'|'WITHIN_ESTIMATED_SUBTOTAL'|'INSUFFICIENT_PRICE_DATA';
 ingredients:{key:string;name:string;unit:string;required:number;pantry:number;missing:number;
  substitutions:{id:string;name:string;ratio:number}[];cataloguePacks:PackRef[];
  choices:{retailer:Retailer;subtotalMinor:number;packs:(PackRef&{count:number;eachMinor:number;observedAt:string})[]}[]}[];
 baskets:{retailer:BasketRetailer;complete:boolean;missingIngredients:number;pricedSubtotalMinor:number;estimatedSubtotalMinor:number|null}[]};

const path='/me/recipe-intelligence';
const generationDeadlineMs=115_000;
const post=(body:unknown)=>({method:'POST',body:JSON.stringify(body)});
export const recipeIntelligence={
 browse:(filters:RecipeFilter)=>request<RecipeEntry[]>(path+'?'+Object.entries(filters)
  .filter(([,value])=>value!==undefined&&value!=='').map(([key,value])=>encodeURIComponent(key)+'='+encodeURIComponent(String(value))).join('&')),
 week:(prompt:string,usePantry:boolean,diet:string)=>request<RecipeEntry[]>(path+'/week',post({prompt,usePantry,diet:diet||null}),generationDeadlineMs),
 generate:(prompt:string,usePantry:boolean)=>request<RecipeEntry>(path+'/generate',post({prompt,usePantry}),generationDeadlineMs),
 plan:(plan:MealRequest)=>request<RecipePlan>(path+'/plan',post(plan)),
 cart:(plan:MealRequest,retailer:BasketRetailer)=>request<CartEntry[]>(path+'/cart',post({plan,retailer})),
};
