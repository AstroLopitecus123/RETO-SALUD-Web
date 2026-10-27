# Local Visual Checks

Run from `Frontend` with installed dependencies:

```powershell
npm run build
npm run test:ui:local
```

The runner uses real Chrome Headless through CDP and the installed `ws` module.
Set `CHROME_PATH` if Chrome is not installed at its default Windows location.
It does not build the app, start `server.js`, start a backend, or access a database.
For mocked authentication, voice, notifications, loading states and payment retry unit tests, run `npm run test:regression`. This focused runner does not require the unavailable Angular Karma builder used by the existing `npm test` configuration.

## Isolation

- A loopback-only HTTP server serves files from `Frontend/browser`, with SPA fallback.
- CDP interception is enabled before navigation. API requests, including CORS preflights, receive synthetic fixtures. Other non-local requests are aborted.
- Chrome uses a dead loopback proxy for external traffic and DNS rules denying external hostname resolution, including background traffic.
- CSP additionally blocks external scripts, styles, images, fonts, frames, and workers. External CDN icons/fonts therefore intentionally do not appear in screenshots.
- Each route starts with clean storage. Protected routes receive fake users and a synthetic JWT with a future expiry. The token is not a real credential.

## Output

Each execution creates `%TEMP%/opencode/ui-visual-<timestamp>/` with PNG captures,
`report.json` (checks, measured rectangles, intercepted requests, browser version,
and build identity), and an isolated Chrome profile. No artifacts are written to
the repository. Chrome and the static server are stopped when execution ends.
The process exits with code 1 if a check fails.

Tests cover doctor edit input containment and overlap, reachable modal actions,
Tab/Shift+Tab/Escape and focus restoration, patient mobile sidebar, application
inputs and submit hit-testing, toast width, and reception's read-only directory.
Pointer interactions use CDP mouse events. The toast-only test uses DOM button
activation to isolate toast layout from the separately checked pointer obstruction;
it does not claim that an obstructed button works with a physical click.

## Observed Build Results

Chrome 155, 2026-10-26: 32 of 32 checks passed against the rebuilt application.

- Doctor edit: passed at 1280x900, 375x812, 320x640, and 667x375, including focus trap, Escape and focus restoration.
- Landscape modal save button: y=290..329 inside a 375px viewport, hit-test passed.
- Application fields: contained and disjoint at widths 1280, 375, 320.
- Toast widths: 420px, 343px, 288px respectively; no internal horizontal overflow.
- Reception route `/recepcion/medicos`: fixture doctor rendered without edit actions or page-level horizontal overflow at all three widths. The mobile table has its own horizontal scrollbar.
- Patient sidebar: opens with focus inside, wraps Tab/Shift+Tab, and closes through Escape or backdrop at 375 and 320.
- Application submit: center hit-test and pointer click pass at 1280, 375 and 320. The accessibility wrapper no longer intercepts the submit button.

Results use synthetic data and blocked external assets. They do not validate real OAuth, Stripe, microphone permissions or backend database concurrency. Rebuild before rerunning after source changes.
