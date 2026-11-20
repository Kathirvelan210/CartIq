import React,{useEffect,useState} from 'react';
import {Modal,Pressable,ScrollView,View} from 'react-native';
import {X,ShoppingBasket} from 'lucide-react-native';
import {useStore} from '../store';
import {defaultPreferences} from '../services/cart';
import {C,money} from '../theme';
import {comparison} from '../services/comparison';
import {Button,Field,Heading,IconButton,Label,ui} from './ui';

const purchaseOptions = [
  {intent:'ESSENTIAL', label:'Normal'},
  {intent:'BULK', label:'Planned / bulk'},
] as const;

export function PurchaseSheet() {
  const store = useStore();
  const {pendingProduct:product,pendingEntry:entry} = store;
  const [intent,setIntent] = useState<(typeof purchaseOptions)[number]['intent']>('ESSENTIAL');
  const [quantity,setQuantity] = useState('1');
  const [days,setDays] = useState('30');
  const [error,setError] = useState('');
  const [busy,setBusy] = useState(false);
  const bulk = intent === 'BULK';

  useEffect(() => {
    setIntent(entry?.preferences?.intent === 'BULK' ? 'BULK' : 'ESSENTIAL');
    setQuantity(String(entry?.quantity ?? 1));
    setDays(String(entry?.preferences?.consumptionDays ?? 30));
    setError('');
  }, [product,entry]);

  async function save() {
    setError('');
    const packs = Number(quantity);
    const consumptionDays = Number(days);
    if (!Number.isInteger(packs) || packs < 1 || packs > 99) {
      setError(bulk ? 'Enter 1–99 packs.' : 'Enter a quantity from 1 to 99.');
      return;
    }
    if (bulk && (!Number.isInteger(consumptionDays) || consumptionDays < 1 || consumptionDays > 365)) {
      setError('Enter a consumption period from 1 to 365 days.');
      return;
    }
    setBusy(true);
    try {
      await store.savePurchase(packs,{...defaultPreferences,intent,consumptionDays:bulk ? consumptionDays : null});
    } catch (problem) {
      setError(problem instanceof Error ? problem.message : 'Could not save this purchase.');
    } finally {
      setBusy(false);
    }
  }

  function close() {
    if (!busy) store.closePurchase();
  }

  const best = product ? comparison(product,store.location.id).best : undefined;
  return <Modal visible={!!product} transparent animationType="slide" onRequestClose={close}>
    <View style={{flex:1,backgroundColor:'#10281FCC',justifyContent:'flex-end'}}>
      <Pressable accessibilityLabel="Close purchase preferences" onPress={close} style={{flex:1}}/>
      <View style={{maxHeight:'92%',width:'100%',maxWidth:620,alignSelf:'center',backgroundColor:C.bg,borderTopLeftRadius:28,borderTopRightRadius:28}}>
        <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={{padding:24,paddingBottom:36}}>
          <View style={[ui.row,{justifyContent:'space-between'}]}>
            <ShoppingBasket size={26} color={C.green}/>
            <IconButton label="Close purchase preferences" onPress={close}><X color={C.text}/></IconButton>
          </View>
          <Heading>Make it your kind of purchase.</Heading>
          <Label muted style={{marginTop:8,marginBottom:20}}>{product?.name} · {product?.quantity}{best?.price != null ? ` · from ${money(best.price)}` : ''}</Label>
          <Label style={{fontWeight:'700',marginBottom:10}}>How do you plan to buy it?</Label>
          <View style={[ui.row,{gap:7,marginBottom:18}]}>
            {purchaseOptions.map(option => <Pressable key={option.intent} accessibilityRole="radio"
              accessibilityState={{checked:intent===option.intent}} onPress={() => {setIntent(option.intent);setError('');}}
              style={[ui.card,{flex:1,padding:12,backgroundColor:intent===option.intent?C.mint:C.paper,borderColor:intent===option.intent?C.green:C.line}]}>
              <Label style={{fontSize:12,fontWeight:'700'}}>{option.label}</Label>
            </Pressable>)}
          </View>
          <Field label={bulk ? 'Number of packs' : 'Quantity'} value={quantity} onChangeText={setQuantity} keyboardType="number-pad"/>
          {bulk && <Field label="Expected consumption period (days)" value={days} onChangeText={setDays} keyboardType="number-pad"/>}
          {error !== '' && <View accessibilityRole="alert"><Label style={{color:C.red,marginBottom:14}}>{error}</Label></View>}
          <Button title={entry ? 'Save purchase preferences' : 'Add to planning cart'} onPress={() => {void save();}} loading={busy}/>
        </ScrollView>
      </View>
    </View>
  </Modal>;
}
