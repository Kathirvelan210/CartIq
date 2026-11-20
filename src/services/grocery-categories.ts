export const groceryQueries={
 Fruits:['apple','banana','orange','grapes','mango'],
 Vegetables:['tomato','onion','potato','carrot','beans'],
 'Milk and Dairy':['milk','curd','paneer','butter','cheese'],
 'Rice and Grains':['rice','basmati rice','wheat'],
 'Dals and Pulses':['toor dal','moong dal','urad dal','chana dal'],
 'Atta and Flour':['atta','wheat flour','besan'],
 'Cooking Oil':['cooking oil','sunflower oil','mustard oil'],
 Snacks:['chips','namkeen','chocolate'],
 'Biscuits and Bakery':['biscuits','bread','cookies'],
 Beverages:['fruit juice','tea','coffee'],
 'Breakfast and Cereals':['oats','corn flakes','muesli'],
 'Personal Care':['soap','shampoo','toothpaste'],
 'Household Essentials':['laundry detergent','dishwashing liquid','cleaner'],
 'Baby Products':['baby diapers','baby wipes'],
 'Frozen Foods':['frozen peas','frozen vegetables','frozen snacks'],
} as const;
export type GroceryCategory=keyof typeof groceryQueries;

export const departmentSearch:Record<GroceryCategory,string>={
 Fruits:'fruits',Vegetables:'vegetables','Milk and Dairy':'dairy','Rice and Grains':'rice',
 'Dals and Pulses':'dal','Atta and Flour':'flour','Cooking Oil':'cooking oil',Snacks:'snacks',
 'Biscuits and Bakery':'biscuits',Beverages:'beverages','Breakfast and Cereals':'cereals',
 'Personal Care':'personal care','Household Essentials':'household','Baby Products':'baby care','Frozen Foods':'frozen food',
};
