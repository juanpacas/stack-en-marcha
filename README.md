# Stack en Marcha

Curso full stack para aprender desde el celular, incluso caminando y sin internet.

**Abrir e instalar:** https://juanpacas.github.io/stack-en-marcha/

- 15 módulos y 54 microlecciones: TypeScript, React, Next.js, Node.js, PostgreSQL, nube, IA, React Native, Python, Go, Power Automate y agentes de IA.
- Modo caminata: las lecciones se leen en voz alta, con preguntas y pausas para pensar.
- Simulador de entrevistas con 26 preguntas reales, en modo manos libres o por escrito.
- Retos para completar código con el pulgar, tarjetas de repaso y quiz relámpago.
- Funciona sin internet después de abrirlo una vez (PWA).

## Instalar en el celular

1. Abre el enlace con conexión a internet.
2. Android (Chrome): menú ⋮ → **Instalar app** o **Agregar a la pantalla principal**.
   iPhone (Safari): botón Compartir → **Agregar a inicio**.
3. Desde ese momento abre sin internet. Solo la evaluación de entrevistas con IA necesita conexión.

## Desarrollo

`stack-en-marcha.html` es el código fuente. Después de editarlo:

```bash
node build-offline.mjs
```

Eso genera la app instalable en `docs/`. Los íconos se regeneran con `make-icons.ps1`, y `node serve.mjs` la sirve en http://localhost:5173 para probarla.
