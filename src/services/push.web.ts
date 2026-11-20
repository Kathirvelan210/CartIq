import {initializeApp,getApps} from 'firebase/app';
import {getMessaging,getToken,isSupported} from 'firebase/messaging';
export async function getPushToken(prompt:boolean):Promise<string|null>{
 const config={apiKey:process.env.EXPO_PUBLIC_FIREBASE_API_KEY,projectId:process.env.EXPO_PUBLIC_FIREBASE_PROJECT_ID,messagingSenderId:process.env.EXPO_PUBLIC_FIREBASE_MESSAGING_SENDER_ID,appId:process.env.EXPO_PUBLIC_FIREBASE_APP_ID};
 if(!config.apiKey||!config.projectId||!config.messagingSenderId||!config.appId||!process.env.EXPO_PUBLIC_FIREBASE_VAPID_KEY)throw new Error('Push notifications are not configured for this app yet. In-app notifications remain available.');
 if(!await isSupported())throw new Error('This browser does not support push notifications.');
 if((prompt ? await Notification.requestPermission() : Notification.permission)!=='granted')return null;
 const app=getApps()[0]||initializeApp(config),worker=await navigator.serviceWorker.register('/firebase-messaging-sw.js');await navigator.serviceWorker.ready;
 return getToken(getMessaging(app),{vapidKey:process.env.EXPO_PUBLIC_FIREBASE_VAPID_KEY,serviceWorkerRegistration:worker});
}
export async function listenForPushUpdates(_refresh: () => void, _foreground: () => void) { return () => {}; }
