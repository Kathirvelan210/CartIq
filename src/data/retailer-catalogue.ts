import {Product} from '../types';

const blinkitProducts = [
  {id:'blinkit-333764', name:'Shudh Chakki Atta', brand:'Aashirvaad', quantity:'5 kg', category:'Pantry', amountBase:5000, art:'bag', color:'#D6A963', description:'Whole wheat atta.', path:'/prn/aashirvaad-shudh-chakki-whole-wheat-atta/prid/333764', image:'73e35060-7c78-4bc0-a230-59c36755f804.jpg'},
  {id:'blinkit-39493', name:'Unpolished Toor Dal', brand:'Tata Sampann', quantity:'1 kg', category:'Pantry', amountBase:1000, art:'bag', color:'#DFB554', description:'Unpolished toor dal, also called arhar dal.', path:'/prn/x/prid/39493', image:'rc-upload-1778578838528-14.jpg'},
  {id:'blinkit-159', name:'Salted Butter', brand:'Amul', quantity:'100 g', category:'Dairy & eggs', amountBase:100, art:'box', color:'#E4C77A', description:'Salted dairy butter.', path:'/prn/amul-butter-100-g/prid/159', image:'rc-upload-1781155438653-138.jpg'},
  {id:'blinkit-105', name:'Vacuum Evaporated Iodised Salt', brand:'Tata Salt', quantity:'1 kg', category:'Pantry', amountBase:1000, art:'bag', color:'#DF914F', description:'Iodised cooking salt.', path:'/prn/tata-salt-vacuum-evaporated-iodised-salt/prid/105', image:'6d3a88e5-5588-4183-84fe-a742d3742ac2.jpg'},
] as const;

export const retailerCatalogue: Product[] = blinkitProducts.map(({path,image,...product}) => ({
  ...product,
  baseUnit:'g',
  offers:[],
  imageUrl:'https://cdn.grofers.com/da/cms-assets/cms/product/'+image,
  retailerListing:{platform:'blinkit',url:'https://blinkit.com'+path},
}));

export function withRetailerCatalogue(products: Product[]): Product[] {
  const existingIds = new Set(products.map(product => product.id));
  return [...products, ...retailerCatalogue.filter(product => !existingIds.has(product.id))];
}
export function canonicalListing(product:Product,products:Product[]):Product|null {
 if(!product.retailerListing)return product;
 const norm=(value:string)=>value.trim().toLowerCase().replace(/\s+/g,' ');
 const matches=products.filter(p=>!p.retailerListing&&norm(p.name)===norm(product.name)&&norm(p.brand)===norm(product.brand)&&norm(p.quantity)===norm(product.quantity));
 return matches.length===1?matches[0]!:null;
}
