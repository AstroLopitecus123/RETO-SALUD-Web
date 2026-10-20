const express = require('express');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 4200;
const DIST_FOLDER = path.join(__dirname, 'browser');

// ✅ Cabeceras críticas de seguridad y para Google Sign-In
app.use((req, res, next) => {
  res.setHeader('Cross-Origin-Opener-Policy', 'same-origin-allow-popups');
  res.setHeader('Cross-Origin-Embedder-Policy', 'unsafe-none');
  res.setHeader('Strict-Transport-Security', 'max-age=31536000; includeSubDomains; preload');
  res.setHeader('X-Frame-Options', 'SAMEORIGIN');
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self' 'unsafe-inline' https://js.stripe.com https://m.stripe.network https://accounts.google.com https://unpkg.com; style-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com https://unpkg.com https://fonts.googleapis.com; img-src 'self' data: blob: https://*.tile.openstreetmap.org https://unpkg.com https://*.basemaps.cartocdn.com https://res.cloudinary.com https://ui-avatars.com; font-src 'self' https://cdnjs.cloudflare.com https://fonts.gstatic.com; connect-src 'self' https://backend-citas-production-4c29.up.railway.app https://api.stripe.com https://m.stripe.network https://accounts.google.com wss://backend-citas-production-4c29.up.railway.app https://*.agora.io wss://*.agora.io https://*.sd-rtn.com wss://*.sd-rtn.com https://api.deepgram.com wss://api.deepgram.com; frame-src 'self' https://js.stripe.com https://accounts.google.com; worker-src 'self' blob:; object-src 'none'; frame-ancestors 'self';");
  next();
});

// Servir archivos estáticos con caché largo (1 año para assets con hash, sin caché para HTML)
app.use(express.static(DIST_FOLDER, {
  maxAge: '1y',
  setHeaders: (res, filePath) => {
    // index.html y ngsw.json nunca deben cachearse (para que Angular actualice)
    if (filePath.endsWith('.html') || filePath.endsWith('ngsw.json')) {
      res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');
    } else {
      // Forzar caché explícito para Lighthouse (1 año = 31536000 segundos)
      res.setHeader('Cache-Control', 'public, max-age=31536000, immutable');
    }
  }
}));

// Redirigir todo a index.html (SPA fallback)
app.get('*', (req, res) => {
  res.sendFile(path.join(DIST_FOLDER, 'index.html'));
});

app.listen(PORT, () => {
  console.log(`🚀 Frontend server running on port ${PORT}`);
});
