export type PlatformId = 'blinkit' | 'instamart' | 'zepto';
export type Availability = 'available' | 'out_of_stock' | 'unavailable' | 'no_data';
export type Offer = {platform: PlatformId; price: number | null; mrp?: number; status: Availability; updatedAt: string; productId: string; variant: string; locationId: string; purchaseUrl?: string;source?:string;verificationStatus?:string;priceMinor?:number;unitPriceMinor?:number;unitLabel?:string};
export type Product = {id:string; name:string; brand:string; quantity:string; category:string; description:string; color:string; art:'carton'|'bag'|'bottle'|'box'|'jar'; offers:Offer[];canonicalProductId?:string;amountBase?:number;baseUnit?:string;imageUrl?:string|null;retailerListing?:{platform:PlatformId;url:string}};
export type Location = {id:string; name:string; city:string; postcode:string};
export type CatalogueMetadata = {gtins:string[];sources:{
 source:string;sourceUrl:string;collectedAt:string;verificationStatus:string;attribution:string;dataLicense:string;
 imageLicense:string|null;ingredients:string|null;nutritionBasis:'100g'|'100ml';nutritionPer100:Record<string,number>;
}[]};
export type ComparisonOption = {source:'blinkit'|'instamart'|'zepto'|'offline';name:string;variantId:string;priceMinor:number|null;unitPriceMinor:number|null;unitLabel:string;availability:string;stockQuantity:number|null;distanceKm:number|null;deliveryMinor:number|null;handlingMinor:number|null;minimumMinor:number|null;totalMinor:number|null;dataSource:string|null;observedAt:string|null;verificationStatus:string|null;eligible:boolean;reason:string;sourceId:string};
export type ComparisonResult = {product:Product;quantity:number;fulfillment:'pickup'|'delivery';options:ComparisonOption[];lowestEligible:ComparisonOption|null;savingsMinor:number|null;explanation:string};
export type ComparisonQuery = {locationId:string;latitude:number;longitude:number;radiusKm:number;quantity:number;fulfillment:'pickup'|'delivery'};
export type User = {id:string; name:string; email:string; role:'customer'|'admin';sample?:boolean};
export type Session = {token:string; user:User};
export type AdminData = {users:number; products:number; categories:number; platforms:{id:PlatformId; enabled:boolean; status:string; syncedAt:string|null}[]};
