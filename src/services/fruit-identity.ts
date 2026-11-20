type FruitListing={title:string;brand:string|null;pack?:string|null;category?:string|null};

const aliases:Record<string,string>={
 fig:'fig',figs:'fig',anjeer:'fig',watermelon:'watermelon',dharboosani:'watermelon',tarbooz:'watermelon',
 banana:'banana',bananas:'banana',apple:'apple',apples:'apple',orange:'orange',oranges:'orange',
 mango:'mango',mangoes:'mango',grape:'grape',grapes:'grape',guava:'guava',guavas:'guava',
 pineapple:'pineapple',papaya:'papaya',pomegranate:'pomegranate',kiwi:'kiwi',kiwis:'kiwi',
 strawberry:'strawberry',strawberries:'strawberry',pear:'pear',pears:'pear',muskmelon:'muskmelon',
};
const descriptors=new Set('sliced slices diced cubes whole cut peeled seedless seeded organic ripe raw green red black yellow white indian imported small medium large royal gala fuji shimla alphonso robusta yelakki cavendish'.split(' '));
const words=(s:string)=>s.normalize('NFKC').toLowerCase().match(/[\p{L}\p{M}\p{N}]+/gu)??[];
const identityCache=new WeakMap<FruitListing,ReturnType<typeof parseFruit>>();
function parseFruit(item:FruitListing){
 if(/\b(dry|dried|frozen|candied|juices?|preserves?|beverages?|powders?|canned)\b/i.test(item.category??''))return null;
 let title=words(item.title).join(' ');
 const brand=words(item.brand??'').join(' ');
 if(brand&&!Object.prototype.hasOwnProperty.call(aliases,brand)&&title.startsWith(brand+' '))title=title.slice(brand.length+1);
 const pack=words(item.pack??'').join(' ');
 if(pack&&title.endsWith(' '+pack))title=title.slice(0,-pack.length-1);
 const names=new Set<string>(),details=new Set<string>();
 for(const token of title.split(' ')){
  if(Object.prototype.hasOwnProperty.call(aliases,token))names.add(aliases[token]!);
  else if(descriptors.has(token))details.add(token==='slices'?'sliced':token);
  else if(token!=='fresh')return null;
 }
 if(names.size!==1)return null;
 return {title:[...names,...Array.from(details).sort()].join(' '),
  brand:['','fruit','fruits','fresh fruits','fresh fruit','fruits vegetables'].includes(brand)?'':brand};
}
export function fruitIdentity(item:FruitListing){
 if(!identityCache.has(item))identityCache.set(item,parseFruit(item));
 return identityCache.get(item)??null;
}

/** A weight range is its own pack: never substitute its midpoint or upper bound. */
export function fruitWeight(text:string|null|undefined){
 const match=text?.trim().toLowerCase().match(/^(\d+(?:\.\d+)?)\s*(kg|g)?\s*(?:[-–—]|to)\s*(\d+(?:\.\d+)?)\s*(kg|g)$/);
 if(!match)return null;
 const low=Number(match[1])*((match[2]??match[4])==='kg'?1000:1);
 const high=Number(match[3])*(match[4]==='kg'?1000:1);
 return low>0&&high>=low?JSON.stringify(['range-g',low,high]):null;
}
