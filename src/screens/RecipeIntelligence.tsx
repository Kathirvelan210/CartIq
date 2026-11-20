import React,{useState} from 'react';
import {ChefHat,Check,SlidersHorizontal} from 'lucide-react-native';
import {recipeRequest} from '../services/recipe-preferences';
import {scheduleWeek} from '../services/weekly-recipes';
import {Linking,Pressable,Switch,View} from 'react-native';
import {Button,Empty,Field,Heading,Label,ui} from '../components/ui';
import {RetailerComparison} from '../components/RetailerComparison';
import {recipeIntelligence,type BasketRetailer,type Meal,type MealRequest,type PackRef,type RecipeEntry,type RecipeFilter,type RecipePlan} from '../services/recipe-intelligence';
import {useStore} from '../store';
import {C,money} from '../theme';

const platforms=['blinkit','zepto'] as const;
const retailerName={blinkit:'Blinkit',zepto:'Zepto',best:'Lowest item subtotal'};
const budgets={NO_BUDGET:'',OVER_BUDGET:'The priced ingredient subtotal exceeds your budget.',
 WITHIN_ESTIMATED_SUBTOTAL:'The estimated ingredient subtotal is within your budget before fees.',
 INSUFFICIENT_PRICE_DATA:'Some prices are missing. Budget fit is unknown.'};
const human=(value:string)=>value.toLowerCase().replaceAll('_',' ').replace(/^[a-z]/,v=>v.toUpperCase());

function Section({title,children}:{title:string;children:React.ReactNode}){
 return <View style={[ui.card,{gap:12,marginBottom:16}]}><Heading style={{fontSize:20}}>{title}</Heading>{children}</View>;
}
function Choices({label,values,selected,select}:{label:string;values:readonly string[];selected:string;select:(value:string)=>void}){
 return <View style={{gap:5}}><Label muted>{label}</Label><View style={{flexDirection:'row',flexWrap:'wrap',gap:8}}>{values.map(value=>
  <Pressable key={value} accessibilityRole="radio" accessibilityState={{checked:value===selected}} accessibilityLabel={`${label} ${value?human(value):'Any'}`} onPress={()=>select(value)}
    style={[ui.pill,{backgroundColor:value===selected?C.green:C.mint,minHeight:42,justifyContent:'center'}]}><Label style={{color:value===selected?C.onGreen:C.text}}>{value?human(value):'Any'}</Label></Pressable>)}</View></View>;
}

function budgetMinor(text:string){
 if(!text.trim())return undefined;
 if(!/^\d{1,7}(?:\.\d{1,2})?$/.test(text.trim())||Number(text)>1000000)throw new Error('Enter a budget in rupees, up to ₹10,00,000.');
 return Math.round(Number(text)*100);
}
function integer(text:string,limit:number,label:string){
 const value=Number(text);
 if(!Number.isInteger(value)||value<1||value>limit)throw new Error(`Choose ${label} from 1 to ${limit}.`);
 return value;
}

export function RecipeIntelligence(){
 const {user,location,offline}=useStore();
 if(offline)return <Empty title="Connect to CartIQ to plan recipes" body="The recipe planner uses your catalogue and pantry account."/>;
 return <RecipeWorkspace key={JSON.stringify([user?.id,location.id])}/>;
}

function RecipeWorkspace(){
 const store=useStore();
 const [filters,filter]=useState<RecipeFilter>({q:'',cuisine:'',meal:'',diet:'',difficulty:'',maxMinutes:'',locationId:store.location.id});
 const [discoveryBudget,setDiscoveryBudget]=useState(''),[budget,setBudget]=useState('');
 const [selected,select]=useState<RecipeEntry|null>(null);
 const [preferences,setPreferences]=useState(false),[checked,setChecked]=useState<number[]>([]);
 const [people,setPeople]=useState('2'),[onlyNeeded,setOnlyNeeded]=useState(false);
 const [prompt,setPrompt]=useState(''),[usePantry,setUsePantry]=useState(false);
 const [meals,setMeals]=useState<Meal[]>([]),[entries,setEntries]=useState<Record<string,RecipeEntry>>({});
 const suggestedMeals=React.useRef(new Set<Meal>());
 const [weekReview,setWeekReview]=useState<{alternatives:RecipeEntry[];servings:number}|null>(null);
 const suggested=meals.filter(meal=>suggestedMeals.current.has(meal));
 const manualCount=meals.length-suggested.length;
 const [day,setDay]=useState('1'),[servings,setServings]=useState('2');
 const [amounts,setAmounts]=useState<Record<string,string>>({}),[substitutions,setSubstitutions]=useState<Record<string,string>>({});
 const [output,setOutput]=useState<{request:MealRequest;plan:RecipePlan}|null>(null),[live,setLive]=useState<PackRef|null>(null);
 const [busy,setBusy]=useState(''),[error,setError]=useState('');
 function change(key:keyof RecipeFilter,value:string){filter(prev=>({...prev,[key]:value}));}
 const planKey=JSON.stringify([meals,budget,amounts,substitutions]);
 const [pricedKey,setPricedKey]=useState('');
 const [cartAdded,setCartAdded]=useState(false);
 const stale=pricedKey!==planKey;
 const plan=output?.plan;

 function requestText(){
  return recipeRequest({idea:prompt,cuisine:filters.cuisine,meal:filters.meal,diet:filters.diet,difficulty:filters.difficulty,
   minutes:filters.maxMinutes,budget:discoveryBudget,servings:people});
 }
 const hasRequest=Boolean(prompt.trim()||filters.cuisine||filters.meal||filters.diet||filters.difficulty||filters.maxMinutes||discoveryBudget);
 const performing=React.useRef(false);
 async function perform(name:string,action:()=>Promise<void>){
  if(performing.current)return;
  performing.current=true;
  setBusy(name);setError('');
  try{await action();}catch(e){setError(e instanceof Error?e.message:'The recipe request failed.');}
  finally{performing.current=false;setBusy('');}
 }
 function choose(entry:RecipeEntry){select(entry);setChecked([]);setOnlyNeeded(false);setServings(String(entry.recipe.servings));}
 function updateWeek(ranked:RecipeEntry[],count:number){
  const week=scheduleWeek(ranked);
  const next=week.map((entry,index)=>({recipeId:entry.id,day:index+1,servings:count}));
  const old=suggestedMeals.current;
  suggestedMeals.current=new Set(next);
  setEntries(previous=>({...previous,...Object.fromEntries(week.map(entry=>[entry.id,entry]))}));
  setMeals(previous=>[...previous.filter(meal=>!old.has(meal)),...next].sort((a,b)=>a.day-b.day));
 }
 async function suggestWeek(){
  const text=requestText();
  const eligible=await recipeIntelligence.browse({...filters,budgetMinor:budgetMinor(discoveryBudget)});
  if(!eligible.length)throw new Error('No catalogue recipes match these filters. Try a wider selection or generate an individual recipe.');
  const instruction='Plan a varied week. Use each suitable recipe before repeating it.';
  const week=await recipeIntelligence.week(text.length+instruction.length+1<=600?text+'\n'+instruction:text,usePantry,filters.diet);
  const eligibleIds=new Set(eligible.map(entry=>entry.id));
  const matches=week.filter(entry=>eligibleIds.has(entry.id));
  const picked=new Set(matches.map(entry=>entry.id));
  updateWeek(matches,Number(people));
  setWeekReview({alternatives:eligible.filter(entry=>!picked.has(entry.id)),servings:Number(people)});
 }
 function addMeal(){
  if(!selected)return;
  try{
   if(meals.length>=14)throw new Error('A plan can contain up to 14 recipes.');
   const meal={recipeId:selected.id,day:integer(day,7,'a day'),servings:integer(servings,20,'servings'),alreadyHave:Number(servings)===selected.recipe.servings?[...checked]:[]};
   setEntries(current=>({...current,[selected.id]:selected}));setMeals(current=>[...current,meal]);setError('');
  }catch(e){setError((e as Error).message);}
 }
 async function calculate(){
  if(!store.location.id)throw new Error('Choose a catalogue delivery area first.');
  const onHand:Record<string,number>={};
  for(const [key,raw] of Object.entries(amounts))if(raw.trim()){
   if(!/^\d{1,6}(?:\.\d{1,3})?$/.test(raw.trim())||Number(raw)>200000)throw new Error('Enter pantry quantities between 0 and 200000 in the listed unit.');
   onHand[key]=Number(raw);
  }
  const request={locationId:store.location.id,meals,budgetMinor:budgetMinor(budget),onHand,substitutions};
  const result=await recipeIntelligence.plan(request);
  setOutput({request,plan:result});setPricedKey(planKey);setLive(null);setCartAdded(false);
 }
 async function addCart(retailer:BasketRetailer){
  if(!output||stale)return;
  await recipeIntelligence.cart(output.request,retailer);
  setCartAdded(true);await store.refreshCart();store.toast('Missing ingredients added to your cart.');
 }

 return <View>
  {!!error&&<View accessibilityRole="alert" style={[ui.card,{marginBottom:12}]}><Label style={{color:C.red}}>{error}</Label></View>}
  {!selected&&<View style={[ui.card,{padding:0,overflow:'hidden',marginBottom:16}]}>
   <View style={{backgroundColor:C.hero,padding:20,gap:7}}>
    <View style={[ui.row,{gap:10}]}><ChefHat color="#D9F080" size={28}/><Heading style={{fontSize:25,color:'#fff'}}>Recipe assistant</Heading></View>
    <Label style={{color:'#e4f1e5'}}>Choose a dish for measured ingredients and step-by-step cooking instructions.</Label>
   </View>
   <View style={{padding:18,gap:12}}>
    <Field label="What would you like to cook?" value={prompt} onChangeText={setPrompt} maxLength={430}
     placeholder="A dish, ingredients, or a meal idea" multiline/>
    <View style={[ui.row,{justifyContent:'space-between',flexWrap:'wrap',gap:10}]}>
     <Label style={{fontWeight:'700'}}>Servings</Label>
     <View style={[ui.row,{gap:12}]}>
      <Button secondary title="−" disabled={Number(people)<=1} onPress={()=>setPeople(String(Number(people)-1))}/>
      <Label>{people}</Label>
      <Button secondary title="+" disabled={Number(people)>=20} onPress={()=>setPeople(String(Number(people)+1))}/>
     </View>
    </View>
    <Pressable accessibilityRole="button" accessibilityState={{expanded:preferences}} onPress={()=>setPreferences(!preferences)}
     style={[ui.row,{gap:8,minHeight:44}]}><SlidersHorizontal size={18} color={C.green}/><Label>{preferences?'Hide cooking preferences':'Cooking preferences'}</Label></Pressable>
    {preferences&&<View style={{gap:12}}>
   <Choices label="Cuisine" values={['','South Indian','North Indian','Indian']} selected={filters.cuisine} select={v=>change('cuisine',v)}/>
   <Choices label="Meal" values={['','BREAKFAST','LUNCH','DINNER','SNACK']} selected={filters.meal} select={v=>change('meal',v)}/>
   <Choices label="Diet" values={['','VEGETARIAN','NON_VEGETARIAN']} selected={filters.diet} select={v=>change('diet',v)}/>
   <Choices label="Difficulty" values={['','EASY','MEDIUM','HARD']} selected={filters.difficulty} select={v=>change('difficulty',v)}/>
   <View style={{flexDirection:'row',gap:10,flexWrap:'wrap'}}><View style={{flex:1,minWidth:150}}><Field label="Maximum minutes" value={filters.maxMinutes} onChangeText={t=>change('maxMinutes',t)} keyboardType="number-pad"/></View>
   <View style={{flex:1,minWidth:150}}><Field label="Recipe budget (₹)" value={discoveryBudget} onChangeText={setDiscoveryBudget} keyboardType="decimal-pad"/></View></View>
    </View>}
    <View style={[ui.row,{justifyContent:'space-between',gap:12}]}><Label style={{flex:1}}>Share pantry ingredient names with AI</Label><Switch accessibilityLabel="Include pantry ingredient names" value={usePantry} onValueChange={setUsePantry}/></View>
    {busy==='generate'&&<View accessibilityLiveRegion="polite"><Label>Preparing your ingredients and cooking steps. This can take over a minute.</Label></View>}
    <Button title="Generate recipe" loading={busy==='generate'} disabled={!!busy||!hasRequest} onPress={()=>void perform('generate',async()=>{
     const text=requestText();select(null);const entry=await recipeIntelligence.generate(text,usePantry);choose(entry);if(discoveryBudget)setBudget(discoveryBudget);
    })}/>

   </View>
  </View>}
  {selected&&<Section title={selected.recipe.name}>
   <Pressable accessibilityRole="button" onPress={()=>{select(null);setError('');}} style={{alignSelf:'flex-start',paddingVertical:6}}><Label style={{color:C.green}}>Change recipe preferences</Label></Pressable>
   <Label muted>{selected.kind==='CURATED'?'Curated adaptation':'AI-generated draft'} · {selected.recipe.cuisine} · {human(selected.recipe.diet)} · {human(selected.recipe.difficulty)}</Label>
   <Label>{selected.recipe.description}</Label><Label muted>{selected.recipe.servings} base servings · {selected.recipe.prepMinutes} min prep · {selected.recipe.cookMinutes} min cooking</Label>
   <View style={[ui.row,{justifyContent:'space-between',flexWrap:'wrap',gap:8}]}>
    <Heading style={{fontSize:18}}>Ingredients for {selected.recipe.servings}</Heading>
    <Label muted>{selected.recipe.ingredients.length-checked.length} to buy</Label>
   </View>
   <Label muted>Tick the ingredients you already have in the quantities shown.</Label>
   <Pressable accessibilityRole="button" onPress={()=>setOnlyNeeded(!onlyNeeded)} style={{alignSelf:'flex-start',paddingVertical:7}}>
    <Label style={{color:C.green}}>{onlyNeeded?'Show all ingredients':'Show shopping list'}</Label></Pressable>
   {selected.recipe.ingredients.map((part,i)=>{
    const owned=checked.includes(i);
    if(onlyNeeded&&owned)return null;
    return <Pressable key={i} accessibilityRole="checkbox" accessibilityLabel={`Already have ${part.name}, ${part.quantity} ${part.unit}`}
     accessibilityState={{checked:owned}} onPress={()=>setChecked(current=>current.includes(i)?current.filter(n=>n!==i):[...current,i])}
     style={[ui.row,{gap:10,minHeight:46,borderBottomWidth:1,borderColor:C.line}]}>
     <View style={{width:22,height:22,borderRadius:6,borderWidth:1,borderColor:owned?C.green:C.muted,backgroundColor:owned?C.green:C.paper,alignItems:'center',justifyContent:'center'}}>{owned&&<Check size={16} color={C.onGreen}/>}</View>
     <Label style={{flex:1,textDecorationLine:owned?'line-through':'none'}}>{part.name}</Label>
     <Label style={{fontWeight:'700'}}>{part.quantity} {part.unit}</Label>
    </Pressable>;
   })}
   {onlyNeeded&&checked.length===selected.recipe.ingredients.length&&<Label>All ingredients are on hand.</Label>}
   <Heading style={{fontSize:18,marginTop:8}}>How to cook</Heading>
   {selected.recipe.steps.map((step,i)=><View key={i} style={{flexDirection:'row',alignItems:'flex-start',gap:12}}>
    <View style={{minWidth:28,height:28,borderRadius:14,backgroundColor:C.mint,alignItems:'center',justifyContent:'center'}}><Label style={{fontWeight:'700'}}>{i+1}</Label></View>
    <Label style={{flex:1,lineHeight:23}}>{step}</Label>
   </View>)}
   {selected.recipe.substitutions.length>0&&<><Heading style={{fontSize:17}}>Suggested substitutions</Heading>{selected.recipe.substitutions.map((item,i)=><Label key={i}>{item}</Label>)}</>}
   {selected.source&&<Button title="Open source recipe" secondary onPress={()=>{if(selected.source)void Linking.openURL(selected.source).catch(()=>{});}}/>}
   {selected.kind==='GENERATED'&&<Label muted>Review the draft, ingredients and cooking steps before use. The draft is available for about 30 minutes.</Label>}
   <View style={{flexDirection:'row',gap:10,flexWrap:'wrap'}}><View style={{flex:1,minWidth:120}}><Field label="Meal day (1–7)" value={day} onChangeText={setDay} keyboardType="number-pad"/></View>
   <View style={{flex:1,minWidth:120}}><Field label="Plan servings (1–20)" value={servings} onChangeText={v=>{setServings(v);setChecked([]);}} keyboardType="number-pad"/></View></View>
   {Number(servings)!==selected.recipe.servings&&<Label muted>For a different serving count, enter your on-hand amounts in the meal plan.</Label>}
   <Button title="Add to meal plan" disabled={!!busy} onPress={addMeal}/>
  </Section>}
  <Section title="Meal plan and shopping list">
   <Button title={suggested.length?'Replace suggested meals with AI':'Suggest seven meals with AI'} secondary loading={busy==='week'} disabled={!!busy||!hasRequest||manualCount>7} onPress={()=>void perform('week',suggestWeek)}/>
   {weekReview&&<View style={{gap:8}} accessibilityLiveRegion="polite">
    <Label>{suggested.length===1?'One dish suggested. Choose another to build a varied week.':
     suggested.length===0?'No suggested meals in your plan.':
     `${new Set(suggested.map(meal=>meal.recipeId)).size} different dishes across ${suggested.length} suggested meals.`}</Label>
    {weekReview.alternatives.length>0&&manualCount<=7&&<>
     <Label muted>Other catalogue recipes matching your filters. Choose a dish to include it in the rotation.</Label>
     {weekReview.alternatives.map(entry=><Button key={entry.id} secondary disabled={!!busy} title={`Include ${entry.recipe.name}`} onPress={()=>{
      updateWeek([...suggested.map(meal=>entries[meal.recipeId]).filter((item):item is RecipeEntry=>!!item),entry],weekReview.servings);
      setWeekReview({...weekReview,alternatives:weekReview.alternatives.filter(other=>other.id!==entry.id)});
     }}/>)}
    </>}
   </View>}
   {meals.length>0&&<>
   {meals.map((meal,index)=><View key={index} style={[ui.row,{gap:12,flexWrap:'wrap',justifyContent:'space-between'}]}>
    <Pressable style={{flex:1}} accessibilityRole="button" onPress={()=>{const entry=entries[meal.recipeId];if(entry)choose(entry);}}><Label>Day {meal.day} · {entries[meal.recipeId]?.recipe.name} · {meal.servings} servings</Label></Pressable>
    <Button title={`Remove meal ${index+1}`} secondary disabled={!!busy} onPress={()=>setMeals(current=>current.filter((_,i)=>i!==index))}/></View>)}
   <Field label="Meal plan budget (₹)" value={budget} onChangeText={setBudget} keyboardType="decimal-pad"/>
   <Button title="Check pantry and compare grocery packs" loading={busy==='plan'} disabled={!!busy||meals.length===0} onPress={()=>void perform('plan',calculate)}/>
   {plan&&<>
    {stale&&<Label muted>Recalculate after changes to update the shopping list and estimates.</Label>}
    {plan.ingredients.map(row=><View key={row.key} style={{gap:8,borderTopWidth:1,borderTopColor:C.line,paddingTop:12}}>
     <Label style={{fontWeight:'700'}}>{row.name}</Label><Label muted>Need {row.required} {row.unit} · Pantry {row.pantry} · Missing {row.missing}</Label>
     <Field label={`On hand for ${row.name} (${row.unit})`} placeholder="Use saved pantry" value={amounts[row.key]??''} keyboardType="decimal-pad" onChangeText={v=>setAmounts(current=>({...current,[row.key]:v}))}/>
     {row.substitutions.length>0&&<Choices label={`Substitute for ${row.key.split('/')[0]}`} values={['',...row.substitutions.map(s=>s.name)]}
      selected={row.substitutions.find(s=>s.id===substitutions[row.key])?.name??''} select={name=>setSubstitutions(current=>{
       const next={...current},match=row.substitutions.find(s=>s.name===name);if(match)next[row.key]=match.id;else delete next[row.key];return next;
      })}/>}
     {row.missing>0&&row.choices.length===0&&<Label muted>{row.cataloguePacks.length?'No recorded price for a compatible pack in this area.':'No reviewed catalogue pack in this unit.'}</Label>}
     {row.choices.map(choice=><View key={choice.retailer} style={{gap:4}}><Label>{retailerName[choice.retailer]} · {money(choice.subtotalMinor/100)}</Label>
      {choice.packs.map(pack=><Label key={pack.variantId} muted>{pack.count} × {pack.brand} {pack.productName} · {pack.label} · {money(pack.eachMinor/100)} each · {new Date(pack.observedAt).toLocaleString()}</Label>)}</View>)}
     {!stale&&store.shoppingLocation&&row.cataloguePacks.map(pack=><Button key={pack.variantId} secondary title={`Open live search for ${pack.productName} ${pack.label}`} onPress={()=>setLive(pack)}/>)}
    </View>)}
    {plan.baskets.map(basket=><View key={basket.retailer} style={{gap:7}}><Label>{retailerName[basket.retailer]} · {basket.estimatedSubtotalMinor===null?`Partial ${money(basket.pricedSubtotalMinor/100)} · ${basket.missingIngredients} missing prices or packs`:money(basket.estimatedSubtotalMinor/100)}</Label>
     <Button title={`Add ${retailerName[basket.retailer]} packs to cart`} secondary disabled={!!busy||stale||cartAdded||!basket.complete||!plan.ingredients.some(i=>i.missing>0)} onPress={()=>void perform('cart',()=>addCart(basket.retailer))}/></View>)}
    {cartAdded&&<Label muted>Packs were added to your cart. Recalculate to prepare another shopping list.</Label>}
    <Label muted>{plan.sample?'Illustrative sample estimates. ':'Recorded price estimates. '}Totals cover grocery packs before fees. The cart is recalculated when you add packs.</Label>
    {!!budgets[plan.budgetStatus]&&<Label>{budgets[plan.budgetStatus]}</Label>}
   </>}
   </>}
  </Section>
  {live&&store.shoppingLocation&&!stale&&<Section title="Separate live price check"><Label muted>Review live results against the selected catalogue brand and pack. These results stay separate from your plan.</Label>
   <RetailerComparison key={live.variantId} query={[live.brand,live.productName,live.label].join(' ').slice(0,100)} location={store.shoppingLocation} admin={store.user?.role==='admin'} platforms={platforms}/>
   <Button title="Close live check" secondary onPress={()=>setLive(null)}/></Section>}

 </View>;
}
