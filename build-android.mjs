// Copia el curso dentro de la app Android, con las tipografías incluidas para que funcione sin internet.
// Uso: node build-android.mjs   (luego: gradle -p android assembleDebug)
import { readFileSync, writeFileSync, mkdirSync, copyFileSync, existsSync } from 'node:fs';

const ASSETS = 'android/app/src/main/assets';
const RES = 'android/app/src/main/res';
mkdirSync(ASSETS + '/fonts', { recursive: true });

let src = readFileSync('stack-en-marcha.html', 'utf8');

// 1. Descarga las tipografías de Google (solo alfabeto latino) y las guarda en la app
const linkRe = /<link rel="stylesheet" href="(https:\/\/fonts\.googleapis\.com[^"]+)">/;
const m = src.match(linkRe);
let fontCss = '';
if (m) {
  const cssUrl = m[1].replace(/&amp;/g, '&');
  const ua = 'Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36';
  const css = await (await fetch(cssUrl, { headers: { 'User-Agent': ua } })).text();
  const blocks = css.split(/(?=\/\* [a-z-]+ \*\/)/).filter(b => /^\/\* latin(-ext)? \*\//.test(b));
  let n = 0;
  for (let block of blocks) {
    const url = block.match(/url\((https:[^)]+)\)/)[1];
    const name = `f${n++}.woff2`;
    const file = `${ASSETS}/fonts/${name}`;
    if (!existsSync(file)) writeFileSync(file, Buffer.from(await (await fetch(url)).arrayBuffer()));
    fontCss += block.replace(url, `fonts/${name}`);
  }
  src = src.replace(/<link rel="preconnect"[^>]*>\n?/, '').replace(linkRe, `<style>\n${fontCss}</style>`);
  console.log(`Tipografías incluidas: ${n} archivos`);
}

// 2. Página completa
const html = `<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
:root{color-scheme:light}
body{margin:0;font:14px system-ui,sans-serif;-webkit-text-size-adjust:100%}
img{max-width:100%}
[hidden]{display:none!important}
</style>
</head>
<body>
${src}
</body>
</html>
`;
writeFileSync(`${ASSETS}/index.html`, html);

// 3. Ícono de la app
for (const [dir, file] of [['mipmap-xxxhdpi', 'docs/icon-192.png']]) {
  mkdirSync(`${RES}/${dir}`, { recursive: true });
  copyFileSync(file, `${RES}/${dir}/ic_launcher.png`);
}
console.log('Listo: curso copiado a ' + ASSETS);
