export type Advertisement={id:string;title:string;subtitle:string;background:string;textColor:string;imageUrl?:string;destinationUrl?:string;buttonLabel:string;sample:boolean};
// Replace sample placements with approved advertiser artwork and HTTPS destinations.
// Do not enable advertiser campaigns without permission to publish their assets and claims.
export const advertisements:Advertisement[]=[
 {id:'grocery',title:'Your brand. Their everyday essentials.',subtitle:'Grocery campaign placement',background:'#3C50B8',textColor:'#FFFFFF',buttonLabel:'Advertisement space',sample:true},
 {id:'household',title:'Put your products in the spotlight.',subtitle:'Household campaign placement',background:'#00ACA3',textColor:'#FFFFFF',buttonLabel:'Advertisement space',sample:true},
 {id:'pet',title:'A space for pet care brands.',subtitle:'Pet care campaign placement',background:'#FFCF30',textColor:'#25312D',buttonLabel:'Advertisement space',sample:true},
 {id:'family',title:'Reach everyday shoppers.',subtitle:'Family essentials campaign placement',background:'#D8E5F2',textColor:'#253748',buttonLabel:'Advertisement space',sample:true},
];
