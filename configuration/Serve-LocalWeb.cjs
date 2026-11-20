// Serve an exported Expo client and the existing local Spring API on the same origin.
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const root = fs.realpathSync(process.argv[2]);
const port = Number(process.argv[3] || 8092);
if (!fs.statSync(path.join(root, 'index.html')).isFile()) throw Error('Expo index.html is required');
const hosts = new Set(['localhost:' + port, '127.0.0.1:' + port]);
const mime = {'.html':'text/html', '.js':'text/javascript', '.json':'application/json', '.css':'text/css',
  '.png':'image/png', '.svg':'image/svg+xml', '.ico':'image/x-icon', '.jpg':'image/jpeg', '.webp':'image/webp',
  '.ttf':'font/ttf', '.woff2':'font/woff2'};
http.createServer((req, res) => {
  const fail = status => { res.writeHead(status, {'Cache-Control':'no-store'}); res.end(); };
  if (!hosts.has(req.headers.host)) return fail(403);
  if (!req.url.startsWith('/') || req.url.startsWith('//')) return fail(400);
  let pathname;
  try { pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname); }
  catch { return fail(400); }
  if (pathname.startsWith('/api/')) {
    if (req.headers.origin && req.headers.origin !== 'http://' + req.headers.host) return fail(403);
    const headers = {host:'127.0.0.1:8080'};
    for (const key of ['accept','content-type','content-length','authorization','cookie','accept-language','x-request-id','x-cartiq-geocoding-consent','x-cartiq-offer-position']) {
      if (req.headers[key] !== undefined) headers[key] = req.headers[key];
    }
    const upstream = http.request({hostname:'127.0.0.1',port:8080,path:req.url,method:req.method,headers}, reply => {
      res.writeHead(reply.statusCode, reply.headers);
      reply.on('error', () => res.destroy());
      reply.pipe(res);
    });
    res.on('close', () => upstream.destroy());
    upstream.on('error', () => { if (!res.headersSent && !res.destroyed) fail(502); });
    req.pipe(upstream);
    return;
  }
  if (!['GET','HEAD'].includes(req.method)) return fail(405);
  if (/[\\:\x00]/.test(pathname)) return fail(400);
  const name = path.resolve(root, '.' + pathname);
  if (name !== root && !name.startsWith(root + path.sep)) return fail(403);
  const file = path.extname(name) ? name : path.join(root, 'index.html');
  fs.stat(file, (error, stat) => {
    if (error || !stat.isFile()) return fail(404);
    res.writeHead(200, {'Content-Type':mime[path.extname(file)] || 'application/octet-stream',
      'Content-Length':stat.size, 'Cache-Control':'no-cache', 'X-Content-Type-Options':'nosniff'});
    if (req.method === 'HEAD') return res.end();
    fs.createReadStream(file).on('error', () => res.destroy()).pipe(res);
  });
}).listen(port, '127.0.0.1', () => console.log('CartIQ Web listening on http://localhost:' + port));
