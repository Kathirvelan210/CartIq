import React,{useState} from 'react';
import {View} from 'react-native';
import {Button,Heading,Label,ui} from './ui';
import {weeklyShopping,type FeedbackAnalytics,type FeedbackCategory,type FeedbackOutcome} from '../services/weekly-shopping';

const categories:Record<FeedbackCategory,string>={PLAN:'Weekly plan',BASKET:'Basket comparison',BULK_BUY:'Bulk-buy advice',PERSONALIZED:'Personalized suggestions',ASSISTANT:'Assistant'};
const outcomes:Record<FeedbackOutcome,string>={ACCEPTED:'Accepted',REJECTED:'Rejected',MODIFIED:'Modified',NOT_RELEVANT:'Not relevant'};

export function RecommendationFeedbackAnalytics(){
 const [data,setData]=useState<FeedbackAnalytics|null>(null),[loading,setLoading]=useState(false),[error,setError]=useState('');
 async function load(){
  setLoading(true);setError('');
  try{setData(await weeklyShopping.analytics());}catch(e){setError((e as Error).message);}finally{setLoading(false);}
 }
 return <View style={[ui.card,{gap:10,marginTop:16}]}>
  <Heading style={{fontSize:20}}>Recommendation feedback</Heading>
  <Button title={data?'Refresh feedback summary':'Load feedback summary'} secondary loading={loading} onPress={()=>void load()}/>
  {error&&<View accessibilityRole="alert"><Label>{error}</Label></View>}
  {data&&!error&&<>
   <Label muted>Last {data.days} days. Each displayed outcome requires at least {data.minimumDistinctAccounts} different accounts.</Label>
   {data.results.map(row=><Label key={row.category+'-'+row.outcome}>{categories[row.category]} · {outcomes[row.outcome]} · {row.count}</Label>)}
   {!data.results.length&&<Label>No outcome reached the reporting threshold.</Label>}
  </>}
 </View>;
}
