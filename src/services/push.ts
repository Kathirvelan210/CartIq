import Constants from 'expo-constants';
import {Platform} from 'react-native';

async function device() {
 if (!Constants.expoConfig?.extra?.nativePushEnabled || Constants.executionEnvironment === 'storeClient') {
  throw new Error('Push requires a Firebase-enabled CartIQ build. In-app notifications remain available.');
 }
 const [notifications, firebase] = await Promise.all([import('expo-notifications'), import('@react-native-firebase/messaging')]);
 return {notifications, firebase, messaging:firebase.getMessaging()};
}

export async function getPushToken(prompt: boolean): Promise<string|null> {
 const {notifications, firebase, messaging} = await device();
 if (Platform.OS === 'android') {
  await notifications.setNotificationChannelAsync('shopping-updates', {
   name:'Shopping updates', importance:notifications.AndroidImportance.DEFAULT,
  });
 }
 const permissions = prompt ? await notifications.requestPermissionsAsync() : await notifications.getPermissionsAsync();
 if (!permissions.granted && permissions.ios?.status !== notifications.IosAuthorizationStatus.PROVISIONAL) return null;
 if (Platform.OS === 'ios' && !messaging.isDeviceRegisteredForRemoteMessages) {
  await firebase.registerDeviceForRemoteMessages(messaging);
 }
 const token = await firebase.getToken(messaging);
 if (!token) throw new Error('This device could not register for push. Try again in a signed build.');
 return token;
}

export async function listenForPushUpdates(refresh: () => void, foreground: () => void) {
 const {firebase, messaging} = await device();
 const removeRefresh = firebase.onTokenRefresh(messaging, refresh);
 const removeForeground = firebase.onMessage(messaging, message => {
  if (typeof message.data?.notificationId === 'string') foreground();
 });
 return () => { removeRefresh(); removeForeground(); };
}
