import {Product} from '../types';

function normalize(value:string):string {
 return value.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase()
  .replace(/['’]/g,'').replace(/[^a-z0-9]+/g,' ').trim();
}

/** Match every search word, regardless of field order or punctuation. */
export function matchesProductSearch(product:Product,query:string):boolean {
 const words=normalize(query).split(/\s+/).filter(Boolean);
 const text=normalize(`${product.name} ${product.brand} ${product.category} ${product.quantity} ${product.description}`);
 return words.every(word=>text.includes(word));
}
