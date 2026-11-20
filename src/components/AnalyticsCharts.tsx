import React from 'react';
import {View} from 'react-native';
import {Label} from './ui';
import {C,money} from '../theme';
import {Budget,MonthSpend,PriceAnalytics} from '../services/workspace';
export function SpendingChart({data}:{data:MonthSpend[]}){const max=Math.max(1,...data.map(d=>d.amountMinor));return <View style={{gap:12}}>{data.map(d=><View key={d.month}><Label muted>{d.month} · {money(d.amountMinor/100)}</Label><View style={{height:10,backgroundColor:C.mint,borderRadius:5,marginTop:5}}><View style={{height:10,width:`${d.amountMinor/max*100}%`,backgroundColor:C.green,borderRadius:5}}/></View></View>)}</View>;}
export function CategoryChart({data}:{data:Budget['categories']}){return <View style={{gap:10}}>{data.map(d=><Label key={d.category}>{d.category} · {money(d.amountMinor/100)}</Label>)}</View>;}
export function PriceChart({data}:{data:PriceAnalytics['history']}){return <View style={{gap:8}}>{data.slice(-12).map((d,i)=><Label key={i} muted>{new Date(d.observedAt).toLocaleDateString('en-IN')} · {d.platform} · {money(d.priceMinor/100)}</Label>)}</View>;}
