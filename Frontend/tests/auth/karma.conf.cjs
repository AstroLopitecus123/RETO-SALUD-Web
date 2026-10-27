const path = require('node:path');
const os = require('node:os');
const { buildSync } = require('esbuild');

// Regression tests without the unavailable Angular Karma builder or an app build.
module.exports = config => {
  const root = path.resolve(__dirname, '../..');
  const output = path.join(os.tmpdir(), 'opencode', 'docgosi-auth-tests.js');
  buildSync({
    absWorkingDir: root,
    stdin: {
      contents: [
        "import '@angular/compiler';",
        "import './src/app/components/google-callback/google-auth-security.spec';",
        "import './src/app/components/mi-perfil/mi-perfil-auth.spec';",
        "import './src/app/services/voice-accessibility.service.spec';",
        "import './src/app/components/notificaciones/notificaciones.spec';",
        "import './src/app/components/recepcion-dashboard/recepcion-dashboard.spec';",
        "import './src/app/components/historial-medico/historial-medico.spec';",
        "import './src/app/components/mis-pagos/mis-pagos.spec';",
        "import './src/app/components/pagar-tarjeta/pagar-tarjeta.spec';"
      ].join('\n'),
      resolveDir: root,
      loader: 'ts'
    },
    bundle: true,
    outfile: output,
    platform: 'browser',
    format: 'iife',
    tsconfigRaw: { compilerOptions: { experimentalDecorators: true, target: 'ES2022' } }
  });
  config.set({
    frameworks: ['jasmine'],
    plugins: ['karma-jasmine', 'karma-chrome-launcher'],
    files: [output],
    browsers: ['ChromeHeadless'],
    reporters: ['dots'],
    singleRun: true,
    client: { jasmine: { random: false } }
  });
};
