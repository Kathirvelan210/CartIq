/* Firebase supplies a browser PushSubscription; only public notification content is handled here. */
self.addEventListener('push',event=>{
 let payload={};try{payload=event.data.json();}catch{return;}
 const notification=payload.notification||{},data=payload.data||{};
 event.waitUntil(self.registration.showNotification(notification.title||'CartIQ update',{body:notification.body||'Open your notification centre for details.',tag:data.notificationId||payload.fcmMessageId||'cartiq-update',data:{url:self.location.origin}}));
});
self.addEventListener('notificationclick',event=>{event.notification.close();event.waitUntil(clients.matchAll({type:'window',includeUncontrolled:true}).then(windows=>{const existing=windows.find(w=>new URL(w.url).origin===self.location.origin);return existing?existing.focus():clients.openWindow(self.location.origin);}));});
