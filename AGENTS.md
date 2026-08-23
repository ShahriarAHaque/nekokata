# Agent notes, nekokata

Offline daily planner (Capacitor WebView + Android widget). **Local-only: no backend, no cloud auth, no analytics.** Do not invent API keys, accounts, or sync.

Product name is **nekokata**.

## Setup

```
npm install
npm run sync:android
```

Open **`android/`** in Android Studio (not the repo root). Node is required at the repo root.

App id: `com.nekokata.app` (`capacitor.config.json`, not `.ts`).

No environment variables are required. See `.env.example`.

## Test

| Command | What it is |
|---|---|
| `npm run test:smoke` | Static smoke checks |
| `npm run test:android:build` | Debug APK assemble |
| SYS-02 in `README.md` | Device regression after UI/nav changes |

After any `www/` edit: `npm run sync:android` before installing on a device.

## Deploy

Debug: Android Studio → Run, or `npm run test:android:build`.
Release: signed bundle/APK in Android Studio. Copy outputs into `releases/` (gitignored). **Never commit a keystore or an APK.** `*.jks` / `*.keystore` / `*.apk` / `*.aab` are gitignored.

Release `minifyEnabled` is **false**. R8/ProGuard is easy to break on Capacitor plugin reflection, the UI is a WebView (`www/`), so shrinking Java does not minify the app. Turn it on only with a keep file that has been SYS-02 tested.

## Product rules

- Light theme only (`html[data-theme="light"]`). Do not add dark mode or a theme toggle. `.cursor/rules/dual-theme-ui.mdc`
- SYS-01: one overlay at a time, await `closeNav()` before screen change. `.cursor/rules/transition-discipline.mdc`
- Category colour is ID: use `--cat-*-fill` for washes, `--cat-*-mark` for dots/icons/strokes, never as text colour.
- Square records the past (`--radius-card`). Pill shows the present (`--shape-pill`).
- HTML-escape any user or calendar string before `innerHTML` (`escapeHtml`).
- Do not put new CSS in `index.html`, use `www/styles/`.

## Docs

- `README.md` is build and SYS-02
- `PRIVACY.md` is local storage, permissions, backup
- `LICENSE` is hobby use only
