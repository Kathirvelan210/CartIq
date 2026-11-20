import {ComparisonOption,ComparisonQuery,ComparisonResult} from '../types';
// A reviewed choice must survive a fresh backend comparison unchanged. Never pick a replacement here.
export function confirmSelection(current:ComparisonResult,shown:ComparisonResult,query:ComparisonQuery,variantId:string,choice?:ComparisonOption):ComparisonOption {
 const previous=choice||shown.lowestEligible;
 if(!previous||shown.product.id!==variantId||current.product.id!==variantId||shown.quantity!==query.quantity||current.quantity!==query.quantity||shown.fulfillment!==query.fulfillment||current.fulfillment!==query.fulfillment)throw new Error('The comparison changed. Review this pack and quantity again.');
 const reviewed=shown.options.find(o=>o.source===previous.source&&o.sourceId===previous.sourceId);
 const candidate=choice?current.options.find(o=>o.source===previous.source&&o.sourceId===previous.sourceId):current.lowestEligible;
 if(!reviewed?.eligible||!candidate?.eligible||candidate.variantId!==variantId||candidate.source!==previous.source||candidate.sourceId!==previous.sourceId||candidate.priceMinor===null||candidate.totalMinor===null||candidate.priceMinor!==previous.priceMinor||candidate.totalMinor!==previous.totalMinor)throw new Error('Your selected source, price or availability changed. Review the comparison; your cart has not been replaced.');
 return candidate;
}
