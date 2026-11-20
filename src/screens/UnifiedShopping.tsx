import React,{useEffect,useState} from 'react';
import {ScrollView,Switch,View} from 'react-native';
import {Button,Empty,Field,Label,ui} from '../components/ui';
import {useStore} from '../store';
import {money} from '../theme';
import {LivePackOffers,QuoteOrigin,useExpiredOffer} from '../components/LivePackOffers';
import {Card,LoadState,ProductPicker,useLoad} from './SmartHub';
import {weeklyShopping,type AssistantReply,type FeedbackCategory,type FeedbackOutcome,
 type PlanBasket,type PlanPreview,type PlanWorkspace,type PreviewResponse,type QuoteEvidence,type WeeklyDraft} from '../services/weekly-shopping';
import {includePacks,integerField,planExpiry,weeklyBudget} from '../services/weekly-shopping-input';

const paise=(n:number)=>money(n/100);
const names:Record<string,string>={blinkit:'Blinkit',zepto:'Zepto',swiggy:'Swiggy Instamart',instamart:'Swiggy Instamart'};
const outcomes:Record<FeedbackOutcome,string>={ACCEPTED:'Accepted',REJECTED:'Rejected',MODIFIED:'Modified',NOT_RELEVANT:'Not relevant'};
const budgets:Record<string,string>={NO_BUDGET:'Set a budget to check spending.',WITHIN_BUDGET:'The complete evaluated total fits the effective budget.',
 OVER_BUDGET:'The evaluated complete total exceeds the effective budget.',PARTIAL_OVER_BUDGET:'The priced items alone exceed the effective budget.',
 BUDGET_UNKNOWN:'Budget fit is unknown because the total is incomplete.',SEARCH_LIMIT:'The evaluated total exceeds the budget. The bounded search may have missed a cheaper basket.'};
const confirmedDays=(s:string)=>s.trim()?integerField(s,365,'confirmed use-by days'):null;
function Alert({message}:{message:string}){return message?<View accessibilityRole="alert"><Label>{message}</Label></View>:null;}

export function UnifiedShopping({openPantry,openBulk,openPreferences}:{openPantry:()=>void;openBulk:()=>void;openPreferences:()=>void}) {
 const {location,offline}=useStore();
 const load=useLoad(()=>offline||!location.id?Promise.resolve(null):weeklyShopping.load(location.id),[location.id,offline]);
 if(offline||!location.id)return <Empty title="Choose a catalogue location" body="Sign in online and select a supported area to plan with your pantry and recorded offers."/>;
 return <><LoadState {...load} retry={load.reload}/>{load.data&&!load.busy&&!load.error&&
  <ShoppingPlan initial={load.data} reload={load.reload} openPantry={openPantry} openBulk={openBulk} openPreferences={openPreferences}/>}</>;
}

function ShoppingPlan({initial,reload,openPantry,openBulk,openPreferences}:{
 initial:PlanWorkspace;reload:()=>void;openPantry:()=>void;openBulk:()=>void;openPreferences:()=>void
}) {
 const {location,products,shoppingLocation}=useStore();
 const [draft,setDraft]=useState(initial.draft),[revision,setRevision]=useState(initial.revision);
 const [budget,setBudget]=useState(initial.draft.budgetMinor==null?'':String(initial.draft.budgetMinor/100));
 const [day,setDay]=useState('1'),[servings,setServings]=useState(String(Math.min(initial.householdSize??2,20)));
 const [pack,setPack]=useState(''),[bulkPack,setBulkPack]=useState(''),[expiry,setExpiry]=useState('');
 const [preview,setPreview]=useState<PreviewResponse|null>(null),[task,setTask]=useState(''),[status,setStatus]=useState(''),[error,setError]=useState('');
 const [prompt,setPrompt]=useState(''),[dialogue,setDialogue]=useState<{question:string;reply:AssistantReply}[]>([]);
 const [confirmClear,setConfirmClear]=useState(false),[confirmFeedback,setConfirmFeedback]=useState(false);
 const [confirmed,setConfirmed]=useState<Record<string,string>>(Object.fromEntries(Object.entries(initial.draft.onHand).map(([key,n])=>[key,String(n)])));
 const visibleRecipes=initial.recipes.filter(r=>draft.diet==='ANY'||draft.diet===r.recipe.diet);
 const productMap=new Map(products.map(p=>[p.id,p]));
 const [quantities,setQuantities]=useState<Record<string,string>>(Object.fromEntries(initial.draft.items.map(line=>[line.productId,String(line.quantity)])));
 const [pricedKey,setPricedKey]=useState('');
 const [expired,setExpired]=useState(false);
 const position=JSON.stringify([shoppingLocation?.latitude,shoppingLocation?.longitude,shoppingLocation?.postcode]);
 const activePosition=React.useRef(position);
 const performing=React.useRef(false);
 activePosition.current=position;
 const currentKey=JSON.stringify([draft,budget,quantities,confirmed,position]);
 const current=preview&&!expired&&pricedKey===currentKey;
 useEffect(()=>{setDialogue([]);},[position]);
 useEffect(()=>{
  if(!preview)return;
  const {singleRetailers,split,recommended}=preview.plan.comparison;
  const quotes=[...singleRetailers,...(split?[split]:[]),recommended].flatMap(basket=>basket.packs.map(p=>p.quote));
  const timestamps=quotes.map(q=>q.observedAt);
  const remaining=planExpiry(timestamps,preview.plan.checkedAt)-Date.now();
  const until=Math.min(remaining,...quotes.flatMap(q=>q.freshUntil?[Date.parse(q.freshUntil)-Date.now()]:[]));
  if(!Number.isFinite(until)||until<=0){setExpired(true);return;}
  const timeout=setTimeout(()=>setExpired(true),until);
  return()=>clearTimeout(timeout);
 },[preview]);

 function payload():WeeklyDraft {
  const onHand:Record<string,number>={};
  for(const [key,raw] of Object.entries(confirmed)){
   const value=raw.trim();if(!value)continue;
   if(!/^\d{1,6}(\.\d{1,3})?$/.test(value)||Number(value)>200000)throw new Error('Use an on-hand amount between 0 and 200,000 in the ingredient unit.');
   onHand[key]=Number(value);
  }
  return {...draft,budgetMinor:weeklyBudget(budget),items:draft.items.map(line=>({...line,quantity:integerField(quantities[line.productId]??String(line.quantity),99,'pack quantity')})),onHand};
 }
 async function run(label:string,action:()=>Promise<void>) {
  if(performing.current)return;
  performing.current=true;
  setStatus('');setError('');setTask(label);
  try{await action();}catch(e){setError((e as Error).message);}finally{performing.current=false;setTask('');}
 }
 function addMeal(recipeId:string) {
  try{
   if(draft.meals.length>=14)throw new Error('A plan can include up to 14 meals.');
   const meal={recipeId,day:integerField(day,7,'day'),servings:integerField(servings,20,'servings')};
   setDraft(d=>({...d,meals:[...d.meals,meal]}));setError('');
  }catch(e){setError((e as Error).message);}
 }
 function ask(question=prompt,chosenPack=bulkPack,knownDays=expiry,gemini=false) {
  if(!question.trim())return;
  void run(gemini?'gemini':'ask',async()=>{
   const context=payload();
   const reply=await weeklyShopping.ask(location.id,question.trim(),context,chosenPack||null,confirmedDays(knownDays),gemini);
   if(position!==activePosition.current)return;
   setDialogue(rows=>[...rows.slice(-7),{question:question.trim(),reply}]);setPrompt('');
  });
 }
 const doPreview=()=>void run('preview',async()=>{
  const response=await weeklyShopping.preview(location.id,payload());
  setExpired(false);setPreview(response);setPricedKey(currentKey);
 });
 const doSave=()=>void run('save',async()=>{
  const result=await weeklyShopping.save(location.id,payload(),revision);
  setRevision(result.revision);setStatus('Weekly plan saved for this location.');
 });
 return <>
  <Card title="AI Smart Shopping">
   <Label muted>{initial.sample?'Sample workspace · ':''}{location.name}. Build a seven-day list from meals, pantry stock and exact extra packs.</Label>
   <Label muted>{initial.householdSize==null?'Set your household size in preferences.':`Household: ${initial.householdSize}. Serving counts can be edited for each meal.`}</Label>
   <View style={{flexDirection:'row',flexWrap:'wrap',gap:8}}>
    <Button title="Household preferences" secondary onPress={openPreferences}/><Button title="Open pantry" secondary onPress={openPantry}/>
   </View>
  </Card>
  <Card title="Weekly meals">
   <Field label="Weekly budget in ₹ (optional)" value={budget} onChangeText={setBudget} keyboardType="decimal-pad"/>
   <Label>Diet for this plan</Label>
   <View style={{flexDirection:'row',flexWrap:'wrap',gap:6}}>{([['ANY','Any'],['VEGETARIAN','Vegetarian'],['NON_VEGETARIAN','Non-vegetarian']] as const).map(([diet,label])=>
    <Button key={diet} title={label} secondary={draft.diet!==diet} onPress={()=>setDraft(d=>({...d,diet}))}/>)}</View>
   <View style={{flexDirection:'row',flexWrap:'wrap',gap:10}}>
    <View style={{flex:1,minWidth:135}}><Field label="Meal day (1–7)" value={day} onChangeText={setDay} keyboardType="number-pad"/></View>
    <View style={{flex:1,minWidth:135}}><Field label="Servings" value={servings} onChangeText={setServings} keyboardType="number-pad"/></View>
   </View>
   <ScrollView horizontal contentContainerStyle={{gap:8}}>{visibleRecipes.map(r=>
    <Button key={r.id} title={`Add ${r.recipe.name}`} secondary onPress={()=>addMeal(r.id)}/>)}</ScrollView>
   {!draft.meals.length&&<Label muted>No meals added yet.</Label>}
   {draft.meals.map((meal,index)=><View key={`${meal.recipeId}-${index}`} style={[ui.card,{gap:8}]}>
    <Label>Day {meal.day} · {initial.recipes.find(r=>r.id===meal.recipeId)?.recipe.name??'Curated recipe'} · {meal.servings} servings</Label>
    <View style={{flexDirection:'row',flexWrap:'wrap',gap:8}}>
     <Button title={`Apply day and servings to meal ${index+1}`} secondary onPress={()=>{
      try{const next={...meal,day:integerField(day,7,'day'),servings:integerField(servings,20,'servings')};
       setDraft(d=>({...d,meals:d.meals.map((m,i)=>i===index?next:m)}));setError('');
      }catch(e){setError((e as Error).message);}
     }}/>
     <Button title={`Remove meal ${index+1}`} secondary onPress={()=>setDraft(d=>({...d,meals:d.meals.filter((_,i)=>i!==index)}))}/>
    </View>
   </View>)}
  </Card>
  <Card title="Editable shopping list">
   <Label muted>Extras are added to what the meals need. Review any packs already included through your recipes.</Label>
   <Button title="Copy exact packs from my cart" secondary disabled={!initial.cart.length} onPress={()=>setDraft(d=>({...d,items:includePacks(d.items,initial.cart)}))}/>
   <ProductPicker value={pack} onChange={setPack}/>
   <Button title="Add this exact pack" secondary disabled={!pack||draft.items.some(line=>line.productId===pack)||draft.items.length>=30}
    onPress={()=>setDraft(d=>({...d,items:includePacks(d.items,[{productId:pack,quantity:1}])}))}/>
   {draft.items.map(line=><View key={line.productId} style={{gap:6}}>
    <Field label={`Packs of ${productMap.get(line.productId)?.name??line.productId} · ${productMap.get(line.productId)?.quantity??'saved pack'}`}
     keyboardType="number-pad" value={quantities[line.productId]??String(line.quantity)}
     onChangeText={value=>setQuantities(q=>({...q,[line.productId]:value}))}/>
    <Button title={`Remove ${productMap.get(line.productId)?.name??line.productId} (${productMap.get(line.productId)?.quantity??'saved pack'})`}
     secondary onPress={()=>{setDraft(d=>({...d,items:d.items.filter(i=>i.productId!==line.productId)}));setQuantities(q=>Object.fromEntries(Object.entries(q).filter(([id])=>id!==line.productId)));}}/>
   </View>)}
   <View style={{flexDirection:'row',flexWrap:'wrap',gap:8}}>
    <Button title="Compare my weekly plan" loading={task==='preview'} disabled={!!task} onPress={doPreview}/>
    <Button title="Save weekly plan" secondary loading={task==='save'} disabled={!!task} onPress={doSave}/>
    <Button title="Reload saved plan" secondary disabled={!!task} onPress={reload}/>
   </View>
   {revision>0&&<View style={{gap:8}}><Label>Saved revision {revision}</Label>
    <View style={[ui.row,{gap:8}]}><Switch value={confirmClear} onValueChange={setConfirmClear} accessibilityLabel="Confirm deletion of saved weekly plan"/><Label>Confirm plan deletion</Label></View>
    <Button title="Delete saved plan" secondary disabled={!confirmClear||!!task} onPress={()=>void run('clear',async()=>{await weeklyShopping.clear(location.id);reload();})}/>
   </View>}
   {status&&<View accessibilityLiveRegion="polite"><Label>{status}</Label></View>}
   <Alert message={error}/>
  </Card>
  {pack&&<LivePackOffers variant={pack} onChange={doPreview}/>}
  {preview&&<Card title="Pantry-aware ingredients">
   {preview.plan.ingredients.length===0&&<Label muted>There are no recipe ingredients in this preview.</Label>}
   {preview.plan.ingredients.map(i=><View key={i.key} style={{gap:5}}>
    <Label>{i.name} · Need {i.required} {i.unit}</Label>
    <Label muted>Pantry applied {i.pantry} {i.unit} · Still needed {i.missing} {i.unit}</Label>
    <Field label={`Confirm on hand for ${i.name} (${i.unit})`} value={confirmed[i.key]??''}
     onChangeText={v=>setConfirmed(a=>({...a,[i.key]:v}))} keyboardType="decimal-pad"/>
   </View>)}
   {current?<Feedback reference={preview.planFeedbackId} category="PLAN"/>:<Label muted>{expired?'Recorded offers expired. Recheck the plan when ready.':'Comparison is out of date. Recheck the plan after editing.'}</Label>}
  </Card>}
  {current&&<Card title="Retailer basket comparison">
   <PlanEvidence plan={preview.plan} details/>
   <Feedback reference={preview.basketFeedbackId} category="BASKET"/>
  </Card>}
  <Card title="Bulk-buy check">
   <Label muted>Choose your normal pack. Household consumption and storage come from your saved bulk-buy limits.</Label>
   <ProductPicker value={bulkPack} onChange={setBulkPack}/>
   <Field label="Confirmed use-by days (optional)" value={expiry} onChangeText={setExpiry} keyboardType="number-pad"/>
   <View style={{flexDirection:'row',flexWrap:'wrap',gap:8}}>
    <Button title="Check this pack for bulk buying" disabled={!bulkPack||!!task} onPress={()=>ask('Is this pack worth buying in bulk?')}/>
    <Button title="Set consumption and storage" secondary onPress={openBulk}/>
   </View>
  </Card>
  <Card title="Shopping assistant">
   <Field label="Ask about your grocery plan" value={prompt} onChangeText={setPrompt} maxLength={1000}/>
   <Label muted>Ask with Gemini sends your question to Gemini to choose a CartIQ tool. Prices, pantry details and basket calculations come from your account.</Label>
   <Button title="Ask with Gemini" secondary loading={task==='gemini'} disabled={!!task||prompt.trim().length<3} onPress={()=>ask(prompt,bulkPack,expiry,true)}/>
   <Button title="Ask CartIQ" loading={task==='ask'} disabled={!!task||!prompt.trim()} onPress={()=>ask()}/>
   <View style={{flexDirection:'row',flexWrap:'wrap',gap:7}}>
    {['What can I cook with my pantry?','Compare my basket','Suggest cheaper alternatives'].map(question=>
     <Button key={question} title={question} secondary disabled={!!task} onPress={()=>ask(question,'','')}/>)}
   </View>
   <Alert message={error}/>
   {dialogue.map(({question,reply})=><View key={reply.feedbackId} style={[ui.card,{gap:8}]}>
    <Label>{question}</Label><Reply answer={reply} addPack={id=>setDraft(d=>({...d,items:includePacks(d.items,[{productId:id,quantity:1}])}))}/>
   </View>)}
   <View style={[ui.row,{gap:8}]}><Switch value={confirmFeedback} onValueChange={setConfirmFeedback} accessibilityLabel="Confirm deletion of my feedback"/><Label>Confirm feedback deletion</Label></View>
   <Button secondary title="Delete my recommendation feedback" disabled={!confirmFeedback||!!task}
    onPress={()=>void run('feedback',async()=>{await weeklyShopping.clearFeedback();setConfirmFeedback(false);setStatus('Your feedback was deleted.');})}/>
   {task===''&&status==='Your feedback was deleted.'&&<Label>{status}</Label>}
  </Card>
 </>;
}

function Quote({q}:{q:QuoteEvidence}){
 if(useExpiredOffer(q.freshUntil))return <QuoteOrigin quote={q}/>;
 return <><QuoteOrigin quote={q}/><Label muted>{paise(q.priceMinor)} per pack · {paise(q.unitPriceMinor)} / {q.unit} · {q.stockPacks==null?'Available at observation, count unknown':`${q.stockPacks} packs reported`} · {q.source}</Label></>;
}
function Basket({option,details=false}:{option:PlanBasket;details?:boolean}){
 return <View style={[ui.card,{gap:6}]}>
  <Label>{option.label} · {option.totalMinor==null?'Complete total unavailable':`Complete total ${paise(option.totalMinor)}`}</Label>
  <Label muted>{option.totalMinor==null?'Partial priced items':'Items'} {paise(option.pricedItemsMinor)} · {option.feesMinor==null?'Fees excluded':`Fees ${paise(option.feesMinor)}`}</Label>
  <Label muted>{option.reason}</Label>
  {option.missing.map((line,i)=><Label key={i}>Missing: {line}</Label>)}
  {details&&option.packs.map((p,i)=><View key={i}><Label>{p.count} × {p.name} · {p.pack}</Label><Quote q={p.quote}/></View>)}
 </View>;
}
function PlanEvidence({plan,details=false}:{plan:PlanPreview;details?:boolean}){
 const {singleRetailers,split,recommended}=plan.comparison;
 const expiry=Math.min(Date.parse(plan.checkedAt)+900000,...[...singleRetailers,...(split?[split]:[]),recommended].flatMap(basket=>
  basket.packs.map(pack=>Date.parse(pack.quote.freshUntil??pack.quote.observedAt)+(pack.quote.freshUntil?0:900000))));
 const expired=useExpiredOffer(Number.isFinite(expiry)?new Date(expiry).toISOString():'invalid');
 if(expired)return <Label muted>Offer evidence expired. Compare the plan again to see current totals.</Label>;
 return <>
  <Label muted>Rule-based · Checked {new Date(plan.checkedAt).toLocaleString()}{plan.sample?' · Sample workspace':''}</Label>
  {plan.budgetMinor!=null&&<Label>Effective budget {paise(plan.budgetMinor)}</Label>}
  <Label>{budgets[plan.budgetStatus]}</Label>
  {!plan.comparison.searchComplete&&<Label muted>Search limit reached. This is the best evaluated result.</Label>}
  <Basket option={plan.comparison.recommended} details/>
  {details&&plan.comparison.singleRetailers.filter(item=>item.label!==plan.comparison.recommended.label).map(item=><Basket key={item.label} option={item}/>)}
  {details&&plan.comparison.split&&plan.comparison.recommended.label!==plan.comparison.split.label&&<Basket option={plan.comparison.split}/>}
  {details&&plan.notes.map(note=><Label muted key={note}>{note}</Label>)}
 </>;
}
function Reply({answer,addPack}:{answer:AssistantReply;addPack:(id:string)=>void}){
 const {bulk,suggestions}=answer;
 return <>
  <Label>{answer.text}</Label><Label muted>{answer.method==='GEMINI_ROUTED'?'Gemini interpreted your question · CartIQ calculated the answer':answer.method==='MODEL_ASSISTED'?'Model-assisted':'Rule-based'}{answer.sample?' · Sample workspace':''}</Label>
  {answer.timing&&<Label muted>Forecast for {answer.products.find(p=>p.variantId===answer.timing?.variantId)?.pack??'the selected pack'} · {names[answer.timing.retailer]??answer.timing.retailer} · {paise(answer.timing.lowMinor)}–{paise(answer.timing.highMinor)} on {answer.timing.date} · Model {answer.timing.modelVersion}</Label>}
  {answer.plan&&<PlanEvidence plan={answer.plan}/>}
  {answer.products.map(p=><View key={p.variantId} style={{gap:6}}>
   <Label>{p.title} · {p.pack}</Label>{p.offers.length?p.offers.map(q=><Quote key={q.retailer} q={q}/>):<Label muted>No fresh eligible offers for this exact pack.</Label>}
   <Button secondary title={`Add ${p.title} · ${p.pack} to plan`} onPress={()=>addPack(p.variantId)}/>
  </View>)}
  {answer.pantry.map(p=><Label key={p.productId}>{p.name} · {p.amountBase} {p.baseUnit}</Label>)}
  {bulk&&<View style={{gap:6}}>
   <Label>{bulk.name} · {bulk.action.toLowerCase().replaceAll('_',' ')}</Label>
   <Label muted>Pantry {bulk.pantry} {bulk.unit} · {bulk.required==null?'Planned need unknown':`Planned purchase ${bulk.required} ${bulk.unit}`}</Label>
   {bulk.budgetMinor!=null&&<Label muted>Effective budget {paise(bulk.budgetMinor)}</Label>}
   {bulk.normal&&<><Label>Usual pack quote</Label><Quote q={bulk.normal.quote}/></>}
   {bulk.recommended&&<><Label>{bulk.recommended.packCount} × {bulk.recommended.pack} · {paise(bulk.recommended.subtotalMinor)} before fees</Label><Quote q={bulk.recommended.quote}/></>}
  </View>}
  {suggestions&&<>
   {suggestions.budgetMinor!=null&&<Label muted>Effective limit {paise(suggestions.budgetMinor)}</Label>}
   {suggestions.recipes.map(r=><Label key={r.id}>{r.name} · {r.missingIngredients} ingredients to check or buy · {r.estimatedSubtotalMinor==null?'Complete subtotal unavailable':`${paise(r.estimatedSubtotalMinor)} estimated before fees`}</Label>)}
   {suggestions.pantryGaps.map(g=><Label key={g.name}>{g.name} · {g.reason}</Label>)}
   {suggestions.products.map(s=><View key={s.product.id} style={{gap:6}}>
    <Label>{s.product.name} · {s.product.quantity}</Label><Label muted>{s.reason}</Label>
    {s.quote?<Quote q={s.quote}/>:<Label muted>Fresh price or availability unknown.</Label>}
    {s.alternative&&<><Label>Same quantity for {paise(s.alternative.subtotalMinor)} before fees · {s.alternative.packCount} × {s.alternative.product.quantity}</Label><Quote q={s.alternative.quote}/></>}
    <Button title={`Add ${s.product.name} · ${s.product.quantity} to plan`} secondary onPress={()=>addPack(s.product.id)}/>
   </View>)}
  </>}
  <Feedback reference={answer.feedbackId} category={bulk?'BULK_BUY':suggestions?'PERSONALIZED':'ASSISTANT'}/>
 </>;
}
function Feedback({reference,category}:{reference:string;category:FeedbackCategory}){
 const [sent,setSent]=useState<FeedbackOutcome|null>(null),[busy,setBusy]=useState(false),[error,setError]=useState('');
 useEffect(()=>{setSent(null);setError('');},[reference]);
 async function send(outcome:FeedbackOutcome){
  setBusy(true);setError('');
  try{await weeklyShopping.rate(reference,category,outcome);setSent(outcome);}
  catch(e){setError((e as Error).message);}finally{setBusy(false);}
 }
 return <View style={{gap:6}}>
  <Label muted>{sent?`Saved feedback: ${outcomes[sent]}`:'Was this recommendation useful? Optional.'}</Label>
  <View style={{flexDirection:'row',flexWrap:'wrap',gap:6}}>{Object.entries(outcomes).map(([id,title])=>
   <Button key={id} title={title} secondary={sent!==id} disabled={busy} onPress={()=>void send(id as FeedbackOutcome)}/>)}</View>
  <Alert message={error}/>
 </View>;
}
