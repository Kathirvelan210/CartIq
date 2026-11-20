import { Location, Product, PlatformId, AdminData } from '../types';
export const locations:Location[]=[{id:'blr-indiranagar',name:'Indiranagar',city:'Bengaluru',postcode:'560038'},{id:'blr-koramangala',name:'Koramangala',city:'Bengaluru',postcode:'560034'},{id:'mum-bandra',name:'Bandra West',city:'Mumbai',postcode:'400050'}];
export const categories=['All','Dairy & eggs','Fruits & vegetables','Snacks','Pantry','Beverages','Personal care'];
export const platforms:Record<PlatformId,{name:string;color:string;background:string;short:string}>={blinkit:{name:'Blinkit',color:'#36501D',background:'#F6E45E',short:'b'},instamart:{name:'Instamart',color:'#B4481A',background:'#FFE9DD',short:'i'},zepto:{name:'Zepto',color:'#6836A2',background:'#EFE4FB',short:'z'}};
const rows = [
 ['milk','Amul Taaza','Amul','1 litre','Dairy & eggs','#78B5DA','carton',68,72,'Toned milk, ready for your morning coffee and everyday cooking.'],
 ['eggs','Farm Fresh Eggs','Fresho','6 pieces','Dairy & eggs','#D5B994','box',54,62,'A pack of six fresh eggs. Store refrigerated.'],
 ['oats','Rolled Oats','Quaker','1 kg','Pantry','#C1483E','bag',179,199,'Wholegrain rolled oats for wholesome breakfasts.'],
 ['coffee','Classic Coffee','Nescafé','100 g','Beverages','#70432C','jar',345,369,'Instant coffee with a rich aroma and full-bodied flavour.'],
 ['chips','Magic Masala Chips',"Lay’s",'90 g','Snacks','#4565AF','bag',45,50,'Crispy potato chips seasoned with a classic masala blend.'],
 ['oil','Sunlite Refined Oil','Fortune','1 litre','Pantry','#E6B44C','bottle',139,155,'Refined sunflower oil for your everyday kitchen.'],
 ['curd','Fresh Curd','Milky Mist','400 g','Dairy & eggs','#69A4A4','jar',38,42,'Thick, creamy curd made from pasteurised milk.'],
 ['almonds','California Almonds','Happilo','200 g','Pantry','#9C8056','bag',189,225,'Whole almonds for a nutritious snack.'],
 ['juice','Real Orange Juice','Real','1 litre','Beverages','#E99A3D','carton',109,120,'Orange fruit beverage. Serve chilled.'],
 ['soap','Gentle Beauty Bar','Dove','100 g','Personal care','#91A1BF','box',58,65,'A gentle cleansing bar for everyday care.'],
 ['bananas','Yelakki Bananas','Fresh harvest','500 g','Fruits & vegetables','#DFC45C','bag',42,49,'Sweet, small bananas, selected for everyday snacking.'],
 ['tomatoes','Fresh Tomatoes','Fresh harvest','500 g','Fruits & vegetables','#C66B53','bag',24,29,'Fresh tomatoes for salads, sauces and everyday cooking.']
] as const;
export function demoProducts(locationId:string):Product[] {return rows.map((r,i)=>({id:r[0],name:r[1],brand:r[2],quantity:r[3],category:r[4],color:r[5],art:r[6],description:r[9],offers:(['blinkit','instamart'] as PlatformId[]).map((platform,j)=>({platform,price:locationId==='blr-indiranagar'?(j===0?r[7]:r[8]):(j===0?r[7]+3:r[8]-2),mrp:Math.ceil(r[8]*1.15),status:i===7&&j===0?'out_of_stock':'available',updatedAt:new Date(Date.now()-(j+1)*60000).toISOString(),verificationStatus:'SAMPLE',productId:r[0],variant:r[3],locationId}))}));}
export const demoAdmin:AdminData={users:0,products:12,categories:6,platforms:[{id:'blinkit',enabled:true,status:'Sample data',syncedAt:null},{id:'instamart',enabled:true,status:'Sample data',syncedAt:null},{id:'zepto',enabled:false,status:'Planned integration',syncedAt:null}]};
