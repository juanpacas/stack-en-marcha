// Genera la versión instalable y offline (PWA) del curso en ./docs
// Uso: node build-offline.mjs
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { createHash } from 'node:crypto';

const src = readFileSync('stack-en-marcha.html', 'utf8');
mkdirSync('docs', { recursive: true });

const head = `<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="theme-color" content="#2B59E0">
<meta name="mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-title" content="Stack en Marcha">
<link rel="manifest" href="manifest.webmanifest">
<link rel="icon" href="icon-192.png">
<link rel="apple-touch-icon" href="icon-192.png">
<style>
:root{color-scheme:light;padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}
body{margin:0;font:14px system-ui,-apple-system,sans-serif;-webkit-text-size-adjust:100%}
img{max-width:100%}
[hidden]{display:none!important}
</style>
</head>
<body>
`;

const register = `
<script>
if ('serviceWorker' in navigator && (location.protocol === 'https:' || location.hostname === 'localhost' || location.hostname === '127.0.0.1')) {
  window.addEventListener('load', () => navigator.serviceWorker.register('./sw.js').catch(() => {}));
}
</script>
</body>
</html>
`;

const html = head + src + register;
writeFileSync('docs/index.html', html);

const version = createHash('sha1').update(html).digest('hex').slice(0, 10);
const sw = `// Service worker: guarda el curso para usarlo sin internet
const CACHE = 'stack-en-marcha-${version}';
const FONTS = 'stack-en-marcha-fonts';
const CORE = ['./', './index.html', './manifest.webmanifest', './icon-192.png', './icon-512.png'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(CORE)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== CACHE && k !== FONTS).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);

  // Archivos del curso: responde desde la caché y actualiza en segundo plano
  if (url.origin === location.origin) {
    e.respondWith(
      caches.match(req, { ignoreSearch: true }).then(hit => {
        const net = fetch(req).then(res => {
          if (res.ok) { const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); }
          return res;
        }).catch(() => hit || caches.match('./index.html'));
        return hit || net;
      })
    );
    return;
  }

  // Tipografías de Google: se guardan la primera vez que cargan
  if (url.hostname === 'fonts.googleapis.com' || url.hostname === 'fonts.gstatic.com') {
    e.respondWith(
      caches.match(req).then(hit => hit || fetch(req).then(res => {
        const copy = res.clone(); caches.open(FONTS).then(c => c.put(req, copy));
        return res;
      }))
    );
  }
});
`;
writeFileSync('docs/sw.js', sw);

const manifest = {
  name: 'Stack en Marcha',
  short_name: 'Stack',
  description: 'Curso full stack para aprender caminando, también sin internet.',
  lang: 'es',
  start_url: './',
  scope: './',
  display: 'standalone',
  orientation: 'portrait',
  background_color: '#EEF1F5',
  theme_color: '#2B59E0',
  icons: [
    { src: 'icon-192.png', sizes: '192x192', type: 'image/png' },
    { src: 'icon-512.png', sizes: '512x512', type: 'image/png' },
    { src: 'icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
  ],
};
writeFileSync('docs/manifest.webmanifest', JSON.stringify(manifest, null, 2));

for (const f of ['icon-192.png', 'icon-512.png'])
  if (!existsSync('docs/' + f)) console.warn('Falta docs/' + f + ' (ejecuta make-icons.ps1)');

console.log('Listo: docs/ generado (versión ' + version + ')');
