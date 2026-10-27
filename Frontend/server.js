const express = require('express');
const path = require('path');
const compression = require('compression');

const app = express();
const PORT = process.env.PORT || 4200;
const DIST_FOLDER = path.join(__dirname, 'browser');

app.use(compression());

app.use((req, res, next) => {
  res.setHeader('Cross-Origin-Opener-Policy', 'same-origin-allow-popups');
  res.setHeader('Cross-Origin-Embedder-Policy', 'unsafe-none');
  res.setHeader('Strict-Transport-Security', 'max-age=31536000');
  res.setHeader('X-Frame-Options', 'SAMEORIGIN');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self' 'unsafe-inline' https://js.stripe.com https://m.stripe.network https://accounts.google.com https://unpkg.com https://www.google.com/recaptcha/ https://www.gstatic.com/recaptcha/ https://www.recaptcha.net; style-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com https://unpkg.com https://fonts.googleapis.com https://www.gstatic.com; img-src 'self' data: blob: https://*.tile.openstreetmap.org https://unpkg.com https://*.basemaps.cartocdn.com https://res.cloudinary.com https://ui-avatars.com https://www.google.com https://www.gstatic.com; font-src 'self' https://cdnjs.cloudflare.com https://fonts.gstatic.com; connect-src 'self' https://backend-citas-production-4c29.up.railway.app https://api.stripe.com https://m.stripe.network https://accounts.google.com wss://backend-citas-production-4c29.up.railway.app https://*.agora.io wss://*.agora.io https://*.sd-rtn.com wss://*.sd-rtn.com https://api.deepgram.com wss://api.deepgram.com https://www.google.com/recaptcha/ https://www.recaptcha.net; frame-src 'self' https://js.stripe.com https://accounts.google.com https://www.google.com https://www.google.com/recaptcha/ https://recaptcha.google.com; worker-src 'self' blob:; object-src 'none'; frame-ancestors 'self';");
  next();
});

app.use(express.static(DIST_FOLDER, {
  setHeaders: (res, filePath) => {
    const baseName = path.basename(filePath);
    if (filePath.endsWith('.html') || baseName === 'ngsw.json') {
      res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');
    } else if (baseName === 'manifest.json' || baseName === 'manifest.webmanifest' || baseName === 'favicon.ico') {
      res.setHeader('Cache-Control', 'public, max-age=3600, must-revalidate');
    } else if (/(chunk|main|polyfills|scripts|styles)-[A-Z0-9]+\.(js|css)$/i.test(baseName) || /[.-][a-f0-9]{8,}\.(js|css)$/i.test(baseName)) {
      res.setHeader('Cache-Control', 'public, max-age=31536000, immutable');
    } else {
      res.setHeader('Cache-Control', 'public, max-age=86400, stale-while-revalidate=604800');
    }
  }
}));

app.get('/login', (req, res) => {
  res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');
  res.setHeader('Link', '</assets/images/FondoLogin.webp>; rel=preload; as=image; fetchpriority=high');
  res.sendFile(path.join(DIST_FOLDER, 'index.html'));
});

app.get('*', (req, res) => {
  res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');
  res.sendFile(path.join(DIST_FOLDER, 'index.html'));
});

app.listen(PORT, () => {
  console.log(`Server running on port ${PORT}`);
});
