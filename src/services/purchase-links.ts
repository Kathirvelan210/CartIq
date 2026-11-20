import {Offer, PlatformId} from '../types';

const retailers = {
  blinkit: {url: 'https://blinkit.com/', hosts: ['blinkit.com', 'www.blinkit.com'], productPath: /^\/prn\/[^/]+\/prid\/\d+\/?$/},
  instamart: {url: 'https://www.swiggy.com/instamart', hosts: ['swiggy.com', 'www.swiggy.com'], productPath: /^\/(?:stores\/)?instamart\/item\/[^/]+\/?$/},
  zepto: {url: 'https://www.zepto.com/', hosts: ['zepto.com', 'www.zepto.com', 'zeptonow.com', 'www.zeptonow.com'], productPath: /^\/pn\/[^/]+\/pvid\/[^/]+\/?$/},
};

const sampleMilkLinks: Partial<Record<PlatformId, string>> = {
  blinkit: 'https://blinkit.com/prn/amul-taaza-homogenised-toned-milk/prid/176',
  zepto: 'https://www.zepto.com/pn/amul-taaza-homogenised-toned-milk/pvid/84eae511-5edb-4a22-875e-5aa94976c2d6',
};

export function purchaseDestination(platform: PlatformId, offer?: Offer, listingUrl?: string) {
  const retailer = retailers[platform];
  const productUrl = listingUrl ?? (offer?.platform !== platform ? undefined
    : offer.verificationStatus === 'VERIFIED' ? offer.purchaseUrl
    : offer.verificationStatus === 'SAMPLE' && offer.productId === 'milk' && offer.variant === '1 litre'
      ? sampleMilkLinks[platform] : undefined);
  if (productUrl) {
    try {
      const url = new URL(productUrl);
      if (url.protocol === 'https:' && !url.username && !url.password && !url.port
        && retailer.hosts.includes(url.hostname) && retailer.productPath.test(url.pathname)) {
        return {url: url.origin + url.pathname, exact: true};
      }
    } catch {
      // An invalid provider link falls back to the official store.
    }
  }
  return {url: retailer.url, exact: false};
}
