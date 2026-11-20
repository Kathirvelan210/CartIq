import * as SecureStore from 'expo-secure-store';
import {Platform} from 'react-native';
import {getPushToken} from './push';
import {workspace} from './workspace';

const storageKey = 'cartiq.push-registration.v1';
type Registration = {owner:string; token:string};
let tail = Promise.resolve<unknown>(undefined);

function serialize<T>(run:() => Promise<T>): Promise<T> {
 const work = tail.then(run);
 tail = work.catch(() => {});
 return work;
}

async function registration(owner:string):Promise<Registration|null> {
 const raw = Platform.OS === 'web' ? localStorage.getItem(storageKey) : await SecureStore.getItemAsync(storageKey);
 if (!raw) return null;
 try {
  const saved:Registration = JSON.parse(raw);
  return saved?.owner === owner && typeof saved.token === 'string' ? saved : null;
 } catch { return null; }
}

async function remember(value:Registration|null) {
 if (Platform.OS === 'web') {
  if (value) localStorage.setItem(storageKey, JSON.stringify(value));
  else localStorage.removeItem(storageKey);
 } else if (value) await SecureStore.setItemAsync(storageKey, JSON.stringify(value));
 else await SecureStore.deleteItemAsync(storageKey);
}

export function registerPushDevice(owner:string, prompt:boolean, current:() => boolean) {
 return serialize(async () => {
  if (!current()) return;
  const previous = await registration(owner);
  if (!prompt && !previous) return;
  const token = await getPushToken(prompt);
  if (!current()) return;
  if (!token) {
   if (previous) {
    await workspace.unregisterPush(previous.token);
    await remember(null);
   }
   if (prompt) throw new Error('Notification permission was not granted. You can change it in your device settings.');
   return;
  }
  await workspace.registerPush(token, previous?.token);
  try { await remember({owner, token}); }
  catch (error) {
   await workspace.unregisterPush(token).catch(() => {});
   throw error;
  }
 });
}

export function unregisterPushDevice(owner:string, current:() => boolean) {
 return serialize(async () => {
  const saved = await registration(owner);
  if (!saved || !current()) return;
  await workspace.unregisterPush(saved.token);
  await remember(null);
 });
}

export function logoutPushDevice(owner:string, logout:(token?:string) => Promise<void>) {
 return serialize(async () => {
  const saved = await registration(owner).catch(() => null);
  await logout(saved?.token);
  if (saved) await remember(null).catch(() => {});
 });
}
