import {ComparisonOption,ComparisonResult} from '../types';

export function validCoordinates(latitude:string,longitude:string,radius:string) {
 if(!latitude.trim()||!longitude.trim()||!radius.trim())return null;
 const lat=Number(latitude),lon=Number(longitude),km=Number(radius);
 return Number.isFinite(lat)&&Math.abs(lat)<=90&&Number.isFinite(lon)&&Math.abs(lon)<=180&&Number.isFinite(km)&&km>0&&km<=100?{latitude:lat,longitude:lon,radiusKm:km}:null;
}
export function comparisonAmount(value:number|null) {
 return value===null?'Unknown':'₹'+(value/100).toLocaleString('en-IN',{maximumFractionDigits:2});
}
export function comparisonLabel(option:ComparisonOption) {
 if(option.source==='offline')return option.name+' · Nearby grocery store';
 return ({blinkit:'Blinkit',instamart:'Swiggy Instamart',zepto:'Zepto'} as const)[option.source];
}
export function recommendationText(result:ComparisonResult) {
 return result.lowestEligible?'Cheapest eligible: '+result.lowestEligible.name:'No eligible option';
}
