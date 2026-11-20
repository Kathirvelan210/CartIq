import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const resolveConfig = require(path.resolve('app.config.js'));
const {expo:config} = require(path.resolve('app.json'));
const keys = ['EAS_BUILD_PROFILE','EAS_BUILD_PLATFORM','EXPO_PUBLIC_API_URL','CARTIQ_NATIVE_PUSH_ENABLED',
 'CARTIQ_FIREBASE_ANDROID_FILE','CARTIQ_FIREBASE_IOS_FILE','EXPO_PUBLIC_FIREBASE_PROJECT_ID',
 'CARTIQ_APNS_ENVIRONMENT','CARTIQ_EAS_PROJECT_ID','CARTIQ_REQUIRE_HTTPS_API'];

function runWithEnv(values:Record<string,string>, check:() => void) {
 const previous = Object.fromEntries(keys.map(key=>[key,process.env[key]]));
 try {
  for(const key of keys) delete process.env[key];
  Object.assign(process.env, values);
  check();
 } finally {
  for(const [key,value] of Object.entries(previous)) {
   if(value===undefined)delete process.env[key];else process.env[key]=value;
  }
 }
}

test('native push defaults off and the native assets exist',()=>runWithEnv({},()=>{
 const result=resolveConfig({config});
 assert.equal(result.extra.nativePushEnabled,false);
 assert.equal(result.android.package,result.ios.bundleIdentifier);
 for(const asset of [result.icon,result.android.adaptiveIcon.foregroundImage,'./assets/splash.png']) {
  assert.equal(fs.readFileSync(asset).subarray(1,4).toString(),'PNG');
 }
}));

test('EAS preview and release require an HTTPS API without credentials',()=>{
 for(const api of ['/api','http://staging.invalid/api','https://localhost/api','https://u:p@staging.invalid/api']) {
  runWithEnv({EAS_BUILD_PROFILE:'preview',EXPO_PUBLIC_API_URL:api},()=>assert.throws(()=>resolveConfig({config}),/EXPO_PUBLIC_API_URL/));
 }
 runWithEnv({EAS_BUILD_PROFILE:'production',EXPO_PUBLIC_API_URL:'https://staging.invalid/api'},()=>assert.equal(resolveConfig({config}).extra.nativePushEnabled,false));
});

test('push build validates the project and package identity before configuring APK and IPA',()=>{
 const folder=fs.mkdtempSync(path.join(os.tmpdir(),'cartiq-native-config-'));
 try {
  const androidFile=path.join(folder,'services.json'), iosFile=path.join(folder,'services.plist');
  fs.writeFileSync(androidFile, JSON.stringify({project_info:{project_id:'cartiq-fixture'},client:[{client_info:{android_client_info:{package_name:config.android.package}}}]}));
  fs.writeFileSync(iosFile,`<plist version="1.0"><dict><key>PROJECT_ID</key><string>cartiq-fixture</string><key>BUNDLE_ID</key><string>${config.ios.bundleIdentifier}</string></dict></plist>`);
  const vars={EAS_BUILD_PROFILE:'preview',EXPO_PUBLIC_API_URL:'https://staging.invalid/api',CARTIQ_NATIVE_PUSH_ENABLED:'true',
   EXPO_PUBLIC_FIREBASE_PROJECT_ID:'cartiq-fixture',CARTIQ_FIREBASE_ANDROID_FILE:androidFile,CARTIQ_FIREBASE_IOS_FILE:iosFile};
  runWithEnv(vars,()=>{
   const result=resolveConfig({config});
   assert.equal(result.extra.nativePushEnabled,true);
   assert.equal(result.ios.entitlements['aps-environment'],'production');
   assert.ok(result.plugins.includes('@react-native-firebase/messaging'));
  });
  runWithEnv({...vars,EXPO_PUBLIC_FIREBASE_PROJECT_ID:'wrong-project'},()=>assert.throws(()=>resolveConfig({config}),/ANDROID_FILE/));
  runWithEnv({...vars,EAS_BUILD_PLATFORM:'ios',EXPO_PUBLIC_FIREBASE_PROJECT_ID:'wrong-project'},()=>assert.throws(()=>resolveConfig({config}),/IOS_FILE/));
  runWithEnv({...vars,CARTIQ_APNS_ENVIRONMENT:'development'},()=>assert.throws(()=>resolveConfig({config}),/APNs/));
 } finally {fs.rmSync(folder,{recursive:true});}
});
