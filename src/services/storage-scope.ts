let owner='guest';
export function setStorageOwner(value:string){owner=value.replace(/[^a-zA-Z0-9-]/g,'');}
export function storageOwner(){return owner;}
