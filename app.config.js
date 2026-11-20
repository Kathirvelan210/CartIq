const fs = require('node:fs');

function requireHttpsApi() {
  let url;
  try { url = new URL(process.env.EXPO_PUBLIC_API_URL); } catch { /* Report only the variable name. */ }
  if (url?.protocol !== 'https:' || url.username || url.password || url.hash || url.search ||
      !/\/api\/?$/.test(url.pathname) || ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)) {
    throw new Error('Set EXPO_PUBLIC_API_URL to the private, device-reachable HTTPS API ending in /api.');
  }
}

module.exports = ({ config }) => {
  const profile = process.env.EAS_BUILD_PROFILE;
  if (profile === 'preview' || profile === 'production' || process.env.CARTIQ_REQUIRE_HTTPS_API === 'true') requireHttpsApi();
  const enabled = process.env.CARTIQ_NATIVE_PUSH_ENABLED === 'true';
  const next = { ...config, extra: { ...config.extra, nativePushEnabled: enabled },
    plugins: [...config.plugins, ['expo-build-properties', { ios: { useFrameworks: 'static', forceStaticLinking: ['RNFBApp', 'RNFBMessaging'] } }]] };
  if (process.env.CARTIQ_EAS_PROJECT_ID) {
    if (!/^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(process.env.CARTIQ_EAS_PROJECT_ID)) {
      throw new Error('Set CARTIQ_EAS_PROJECT_ID to the linked EAS project UUID.');
    }
    next.extra.eas = { ...next.extra.eas, projectId: process.env.CARTIQ_EAS_PROJECT_ID };
  }
  if (!enabled) return next;

  const platform = process.env.EAS_BUILD_PLATFORM;
  const androidFile = process.env.CARTIQ_FIREBASE_ANDROID_FILE;
  const iosFile = process.env.CARTIQ_FIREBASE_IOS_FILE;
  const project = process.env.EXPO_PUBLIC_FIREBASE_PROJECT_ID;
  if (!/^[a-z][a-z0-9-]{4,28}[a-z0-9]$/.test(project || '')) {
    throw new Error('Set EXPO_PUBLIC_FIREBASE_PROJECT_ID to the Firebase project used by the backend.');
  }
  if (platform !== 'ios') {
    let native;
    try { native = JSON.parse(fs.readFileSync(androidFile, 'utf8')); } catch { /* Do not expose file data or paths. */ }
    if (native?.project_info?.project_id !== project || !Array.isArray(native?.client) ||
        !native.client.some(client => client?.client_info?.android_client_info?.package_name === config.android.package)) {
      throw new Error('Set CARTIQ_FIREBASE_ANDROID_FILE to a Google services file for the Android package and project.');
    }
  }
  if (platform !== 'android') {
    let native;
    try { native = require('@expo/plist').default.parse(fs.readFileSync(iosFile, 'utf8')); } catch { /* Do not expose file data or paths. */ }
    if (native?.PROJECT_ID !== project || native?.BUNDLE_ID !== config.ios.bundleIdentifier) {
      throw new Error('Set CARTIQ_FIREBASE_IOS_FILE to a Firebase plist for the iOS bundle ID and project.');
    }
  }
  const apns = process.env.CARTIQ_APNS_ENVIRONMENT || (profile ? 'production' : 'development');
  if (!['production', 'development'].includes(apns) || (profile && apns !== 'production')) {
    throw new Error('Use the production APNs entitlement for signed EAS preview or production builds.');
  }
  return {
    ...next,
    android: { ...config.android, permissions: [...(config.android.permissions || []), 'android.permission.POST_NOTIFICATIONS'],
      ...(androidFile ? { googleServicesFile: androidFile } : {}) },
    ios: { ...config.ios, ...(iosFile ? { googleServicesFile: iosFile } : {}),
      entitlements: { ...config.ios.entitlements, 'aps-environment': apns } },
    plugins: [ ...next.plugins, '@react-native-firebase/app', '@react-native-firebase/messaging',
      ['expo-notifications', { defaultChannel: 'shopping-updates', icon: './assets/notification-icon.png', color: '#174F3D' }]],
  };
};
