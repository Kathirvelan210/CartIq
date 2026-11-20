import React,{useEffect,useState} from 'react';
import {ScrollView,View} from 'react-native';
import {Button,Empty,Field,Label} from '../components/ui';
import {request} from '../services/api';
import {useStore} from '../store';
import {money} from '../theme';
import {Product} from '../types';
import {LivePackOffers,QuoteOrigin,useExpiredOffer} from '../components/LivePackOffers';
import type {QuoteEvidence} from '../services/weekly-shopping';
import {Card,LoadState,ProductPicker,useLoad} from './SmartHub';

export type ShoppingPage='Bulk buy'|'For you'|'Shopping insights';
type Diet='ANY'|'VEGETARIAN'|'NON_VEGETARIAN';
type Household={householdSize:number;diet:Diet;preferredCuisine:string|null;shoppingBudgetMinor:number|null};
type Limits={perPersonDaily:number;storageCapacity:number;planningDays:number;budgetMinor:number|null};
type Quote=QuoteEvidence;
type Purchase={variantId:string;pack:string;packCount:number;amount:number;quote:Quote;subtotalMinor:number};
type Advice={action:string;reason:string;unit:string;pantry:number;required:number|null;maxPurchase:number|null;budgetMinor:number|null;normal:Purchase|null;recommended:Purchase|null;sample:boolean};
type Suggestions={products:{product:Product;reason:string;quote:Quote|null;alternative:{product:Product;quote:Quote;packCount:number;subtotalMinor:number}|null}[];recipes:{id:string;name:string;cuisine:string;missingIngredients:number;estimatedSubtotalMinor:number|null}[];pantryGaps:{name:string;reason:string}[];budgetMinor:number|null;sample:boolean};
type BasketOption={label:string;feasible:boolean;subtotalMinor:number|null;feesMinor:number|null;totalMinor:number|null;reason:string;assignments:{productId:string;platform:string;quantity:number}[]};
type Basket={comparison:{singleStores:BasketOption[];recommended:BasketOption;searchComplete:boolean;explanation:string}|null;budgetMinor:number|null;status:string;sample:boolean};
const root='/me/shopping-intelligence';
const put=(body:object)=>({method:'PUT',body:JSON.stringify(body)});
const paise=(amount:number)=>money(amount/100);
const retailerNames:Record<string,string>={blinkit:'Blinkit',zepto:'Zepto',swiggy:'Swiggy Instamart',instamart:'Swiggy Instamart'};
function number(value:string,label:string,min:number,max:number,precision:number) {
  const str=value.trim();
  if(!/^\d+(\.\d+)?$/.test(str)||Number(str)<min||Number(str)>max||(str.split('.')[1]?.length??0)>precision)
    throw new Error(`Enter ${label} between ${min} and ${max}.`);
  return Number(str);
}
function budget(value:string) {
  return value.trim()===''?null:Math.round(number(value,'a budget in rupees',0,10000000,2)*100);
}
function withLocation(location:string,limit?:number|null) {
  return `?locationId=${encodeURIComponent(location)}${limit==null?'':`&budgetMinor=${limit}`}`;
}
function QuoteText({quote}:{quote:Quote}) {
  if(useExpiredOffer(quote.freshUntil))return <QuoteOrigin quote={quote}/>;
  return <><QuoteOrigin quote={quote}/><Label>{paise(quote.priceMinor)} per pack · {paise(quote.unitPriceMinor)} / {quote.unit}</Label>
    <Label muted>{quote.stockPacks==null?'Exact stock unknown':`${quote.stockPacks} packs reported in stock`}</Label></>;
}
export function ShoppingIntelligence({page,openProduct,openRecipes,openBudget}:{page:ShoppingPage;openProduct:(product:Product)=>void;openRecipes:()=>void;openBudget:()=>void}) {
  const store=useStore();
  if(store.offline)return <Empty title="Connect to your shopping workspace" body="These recommendations need your account data and the catalogue." />;
  return <>
    {page!=='Shopping insights'&&<HouseholdSettings />}
    {page==='Bulk buy'?<Bulk />:page==='For you'?<ForYou openProduct={openProduct} openRecipes={openRecipes}/>:<Insights openBudget={openBudget}/>}
  </>;
}
function HouseholdSettings() {
  const load=useLoad(()=>request<{household:Household|null}>(root+'/preferences'));
  const [size,setSize]=useState(''),[diet,setDiet]=useState<Diet>('ANY'),[cuisine,setCuisine]=useState(''),[limit,setLimit]=useState('');
  const [saving,setSaving]=useState(false),[message,setMessage]=useState(''),[error,setError]=useState('');
  const home=load.data?.household;
  useEffect(()=>{
    if(!home)return;
    setSize(String(home.householdSize));setDiet(home.diet);setCuisine(home.preferredCuisine??'');
    setLimit(home.shoppingBudgetMinor==null?'':String(home.shoppingBudgetMinor/100));
  },[home]);
  async function save() {
    setSaving(true);setError('');setMessage('');
    try {
      await request(root+'/preferences',put({householdSize:number(size,'a household size',1,30,0),diet,preferredCuisine:cuisine.trim()||null,shoppingBudgetMinor:budget(limit)}));
      setMessage('Preferences saved. Refresh recommendations to apply them.');
    } catch(e) {setError((e as Error).message);} finally {setSaving(false);}
  }
  return <Card title="Household preferences"><LoadState {...load} retry={load.reload}/>
    {!load.busy&&!load.error&&<>
      <Field label="Household size" value={size} onChangeText={setSize} keyboardType="number-pad"/>
      <Field label="Preferred cuisine (optional)" value={cuisine} onChangeText={setCuisine} maxLength={100}/>
      <Label>Dietary preference</Label>
      <ScrollView horizontal contentContainerStyle={{gap:8}}>{([
        ['ANY','Any'],['VEGETARIAN','Vegetarian'],['NON_VEGETARIAN','Non-vegetarian'],
      ] as const).map(([item,label])=>
        <Button key={item} title={label} secondary={diet!==item} onPress={()=>setDiet(item)}/>)}</ScrollView>
      <Field label="Shopping budget in ₹ (optional)" value={limit} onChangeText={setLimit} keyboardType="decimal-pad"/>
      <Button title="Save household preferences" onPress={()=>void save()} loading={saving}/>
      {message&&<View accessibilityLiveRegion="polite"><Label>{message}</Label></View>}
      {error&&<View accessibilityRole="alert"><Label>{error}</Label></View>}
    </>}
  </Card>;
}
const actions:Record<string,string>={BUY_NORMAL_QUANTITY:'Buy normal quantity',BUY_LARGER_PACK:'Buy a larger pack',BUY_MULTIPLE_PACKS:'Buy multiple packs',DO_NOT_BUY_EXTRA:'Do not buy extra',INSUFFICIENT_INFORMATION:'Insufficient information'};
function Bulk() {
  const {products}=useStore(),[id,setId]=useState('');
  const product=products.find(p=>p.id===id);
  return <><Card title="Choose your usual pack"><ProductPicker value={id} onChange={setId}/>
    {!product&&<Label muted>Choose the pack you normally buy to compare compatible quantities.</Label>}</Card>
    {product&&<><LivePackOffers variant={product.id}/><BulkPlan key={product.id} product={product}/></>}
  </>;
}
function BulkPlan({product}:{product:Product}) {
  const {location}=useStore(),endpoint=root+'/bulk/'+encodeURIComponent(product.id),query=withLocation(location.id);
  const load=useLoad(()=>request<{limits:Limits|null}>(endpoint+'/limits'+query),[endpoint,query]);
  const [daily,setDaily]=useState(''),[space,setSpace]=useState(''),[days,setDays]=useState('7'),[limit,setLimit]=useState(''),[expiry,setExpiry]=useState('');
  const [result,setResult]=useState<Advice|null>(null),[error,setError]=useState(''),[busy,setBusy]=useState(false);
  const rules=load.data?.limits;
  useEffect(()=>{if(rules){setDaily(String(rules.perPersonDaily));setSpace(String(rules.storageCapacity));setDays(String(rules.planningDays));setLimit(rules.budgetMinor==null?'':String(rules.budgetMinor/100));}},[rules]);
  async function advise() {
    setBusy(true);setError('');setResult(null);
    try {
      const useBy=expiry.trim()===''?'':`&daysBeforeExpiry=${number(expiry,'remaining use-by days',1,365,0)}`;
      await request(endpoint+'/limits'+query,put({perPersonDaily:number(daily,'daily use per person',0.001,9999999.999,3),storageCapacity:number(space,'total storage capacity',0,9999999.999,3),
        planningDays:number(days,'planning days',1,90,0),budgetMinor:budget(limit)}));
      setResult(await request<Advice>(endpoint+query+useBy));
    } catch(e) {setError((e as Error).message);} finally {setBusy(false);}
  }
  const unit=product.baseUnit??'base units';
  return <>
    <Card title={`${product.name} · ${product.quantity}`}><LoadState {...load} retry={load.reload}/>
      {!load.busy&&!load.error&&<>
        <Field label={`Daily use per person (${unit})`} value={daily} onChangeText={setDaily} keyboardType="decimal-pad"/>
        <Field label={`Total storage including pantry stock (${unit})`} value={space} onChangeText={setSpace} keyboardType="decimal-pad"/>
        <Field label="Planning days" value={days} onChangeText={setDays} keyboardType="number-pad"/>
        <Field label="Item budget in ₹ (optional)" value={limit} onChangeText={setLimit} keyboardType="decimal-pad"/>
        <Field label="Confirmed days before expiry (optional)" value={expiry} onChangeText={setExpiry} keyboardType="number-pad"/>
        <Label muted>Enter use-by days only when you can confirm them for the packs you are considering.</Label>
        <Button title="Save limits and check packs" loading={busy} onPress={()=>void advise()}/>
        {error&&<View accessibilityRole="alert"><Label>{error}</Label></View>}
      </>}
    </Card>
    {result&&<Card title={actions[result.action]??result.action}>
      {result.sample&&<Label>Sample workspace</Label>}
      <Label>{result.reason}</Label>
      <Label muted>Pantry: {result.pantry} {result.unit}{result.required==null?'':` · Planned purchase: ${result.required} ${result.unit}`}</Label>
      {result.budgetMinor!=null&&<Label muted>Effective item limit: {paise(result.budgetMinor)}</Label>}
      {result.normal&&<><Label>Usual pack · {result.normal.pack}</Label><QuoteText quote={result.normal.quote}/></>}
      {result.recommended&&<><Label>{result.recommended.packCount} × {result.recommended.pack} · {paise(result.recommended.subtotalMinor)} before fees</Label>
        <QuoteText quote={result.recommended.quote}/></>}
    </Card>}
  </>;
}
function ForYou({openProduct,openRecipes}:{openProduct:(product:Product)=>void;openRecipes:()=>void}) {
  const {location}=useStore();
  const load=useLoad(()=>request<Suggestions>(root+'/recommendations'+withLocation(location.id)),[location.id]);
  const data=load.data;
  return <><LoadState {...load} retry={load.reload}/>
    {data&&!load.busy&&!load.error&&<>
      <Card title="Products for your routine">
        {data.sample&&<Label>Sample workspace</Label>}
        {data.budgetMinor!=null&&<Label muted>Effective limit: {paise(data.budgetMinor)}. Product prices exclude fees.</Label>}
        <Button title="Refresh recommendations" secondary onPress={load.reload}/>
        {data.products.length===0&&<Label muted>No matching products have usable offers or saved activity here.</Label>}
        {data.products.map(item=><View key={item.product.id} style={{gap:7}}>
          <Label>{item.product.name} · {item.product.quantity}</Label><Label muted>{item.reason}</Label>
          {item.quote?<QuoteText quote={item.quote}/>:<Label muted>Fresh price or availability unknown.</Label>}
          <Button title="View product" secondary onPress={()=>openProduct(item.product)}/>
          {item.alternative&&<><Label>Same total quantity · {item.alternative.packCount} × {item.alternative.product.quantity} · {paise(item.alternative.subtotalMinor)} before fees</Label>
            <QuoteText quote={item.alternative.quote}/><Button title="View alternative" secondary onPress={()=>openProduct(item.alternative!.product)}/></>}
        </View>)}
      </Card>
      <Card title="Pantry essentials">
        {data.pantryGaps.length===0&&<Label muted>No pantry gaps identified from your saved plans and reminders.</Label>}
        {data.pantryGaps.map(gap=><Label key={gap.name}>{gap.name} · {gap.reason}</Label>)}
      </Card>
      <Card title="Recipes for your pantry">
        {data.recipes.length===0&&<Label muted>No curated recipes match these preferences.</Label>}
        {data.recipes.map(recipe=><View key={recipe.id} style={{gap:6}}>
          <Label>{recipe.name} · {recipe.cuisine}</Label><Label muted>{recipe.missingIngredients} ingredients to check or buy</Label>
          <Label muted>{recipe.estimatedSubtotalMinor==null?'Complete ingredient subtotal unavailable':`Estimated ingredients: ${paise(recipe.estimatedSubtotalMinor)} before fees`}</Label>
        </View>)}
        <Button title="Open recipe planner" secondary onPress={openRecipes}/>
      </Card>
    </>}
  </>;
}
const basketStatus:Record<string,string>={EMPTY:'Add items to your cart at this location to compare baskets.',TOO_MANY_LINES:'Compare up to 30 cart lines at a time.',INCOMPLETE:'A complete basket cost is unavailable.',BUDGET_NOT_SET:'Set a spending limit to check this basket.',WITHIN_BUDGET:'The complete evaluated basket fits your budget.',OVER_BUDGET:'The cheapest evaluated complete basket exceeds your budget.',LIMIT_REACHED:'The best evaluated basket exceeds your budget. The search ended before checking every option.'};
function Insights({openBudget}:{openBudget:()=>void}) {
  const {location,products}=useStore(),[limit,setLimit]=useState(''),[saved,setSaved]=useState<number|null>(null),[error,setError]=useState('');
  const load=useLoad(()=>request<Basket>(root+'/basket'+withLocation(location.id,saved)),[location.id,saved]);
  const data=load.data;
  function apply() {
    setError('');
    try{const value=budget(limit);if(value===saved)load.reload();else setSaved(value);}catch(e){setError((e as Error).message);}
  }
  return <>
    <Card title="Basket and budget">
      <Field label="One-time basket budget in ₹" value={limit} onChangeText={setLimit} keyboardType="decimal-pad"/>
      <Button title="Check my cart" onPress={apply}/>
      <Button title="Open monthly budget settings" secondary onPress={openBudget}/>
      {error&&<View accessibilityRole="alert"><Label>{error}</Label></View>}
      <LoadState {...load} retry={load.reload}/>
      {data&&!load.error&&!load.busy&&<>
        {data.sample&&<Label>Sample workspace</Label>}
        <Label>{basketStatus[data.status]??data.status}</Label>
        {data.budgetMinor!=null&&<Label muted>Effective limit: {paise(data.budgetMinor)}</Label>}
        {data.comparison&&<><Label muted>{data.comparison.explanation}</Label>
          <BasketResult option={data.comparison.recommended} products={products}/>
        </>}
      </>}
    </Card>
    {data?.comparison&&!load.error&&!load.busy&&<Card title="Retailer breakdown">
      {data.comparison.singleStores.map(option=><BasketResult key={option.label} option={option}/>)}
    </Card>}
  </>;
}
function BasketResult({option,products}:{option:BasketOption;products?:Product[]}) {
  return <View style={{gap:6}}>
    <Label>{option.label} · {option.totalMinor==null?'Complete total unavailable':paise(option.totalMinor)}</Label>
    {option.subtotalMinor!=null&&<Label muted>Items: {paise(option.subtotalMinor)} · Fees: {option.feesMinor==null?'unknown':paise(option.feesMinor)}</Label>}
    <Label muted>{option.reason}</Label>
    {option.feasible&&products&&option.assignments.map(line=><Label key={line.productId} muted>
      {line.quantity} × {products.find(p=>p.id===line.productId)?.name??line.productId} · {retailerNames[line.platform]??line.platform}
    </Label>)}
  </View>;
}
