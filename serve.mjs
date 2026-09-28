// Servidor local mínimo para probar la versión offline: node serve.mjs → http://localhost:5173
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';

const ROOT = join(process.cwd(), 'docs');
const TYPES = { '.html':'text/html; charset=utf-8', '.js':'text/javascript', '.webmanifest':'application/manifest+json', '.png':'image/png', '.json':'application/json' };

createServer(async (req, res) => {
  let p = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  if (p.endsWith('/')) p += 'index.html';
  const file = normalize(join(ROOT, p));
  if (!file.startsWith(ROOT)) { res.writeHead(403).end(); return; }
  try {
    const data = await readFile(file);
    res.writeHead(200, { 'Content-Type': TYPES[extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-cache' });
    res.end(data);
  } catch { res.writeHead(404).end('No encontrado'); }
}).listen(5173, () => console.log('Sirviendo docs/ en http://localhost:5173'));
