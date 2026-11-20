import {Product} from '../types';

const imageBase = 'https://cdn.grofers.com/da/cms-assets/cms/product/';
const samplePackPhotos: ReadonlyArray<{
  id: string;
  brand: string;
  quantity: string;
  image: string;
}> = [
  {id:'milk', brand:'Amul', quantity:'1 litre', image:'eca2572f-da3c-4ce0-95e1-7fa67bf1e8cf.jpg'},
  {id:'juice', brand:'Real', quantity:'1 litre', image:'large_images/40e09b64-4393-44bc-adb8-54462e2e42c2.jpg'},
  {id:'chips', brand:'Lay’s', quantity:'90 g', image:'rc-upload-1778729709715-696.jpg'},
  {id:'almonds', brand:'Happilo', quantity:'200 g', image:'56cd0474-4d6f-4897-9683-c48eb1989cd8.jpg'},
];

export function productPhoto(product: Product, sample: boolean): string | undefined {
  if (product.imageUrl) return product.imageUrl;
  if (!sample) return undefined;
  const pack = samplePackPhotos.find(photo => photo.id === product.id
    && photo.brand === product.brand && photo.quantity === product.quantity);
  return pack ? imageBase+pack.image : undefined;
}
