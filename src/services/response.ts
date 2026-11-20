export async function readJsonResponse<T>(response:Response):Promise<T>{
 const content=await response.text();
 if(!content.trim())return undefined as T;
 try{return JSON.parse(content) as T;}catch{throw new Error('The service returned an unreadable response. Refresh your information before trying again.');}
}
