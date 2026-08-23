# releases

Local drop folder for APKs and App Bundles. Binaries in this folder are gitignored.

Copy from Gradle after a build:

- Debug: `android/app/build/outputs/apk/debug/app-debug.apk`
- Signed release: `android/app/build/outputs/apk/release/app-release.apk`

Do not put a keystore here. `*.jks` / `*.keystore` are gitignored at the repo root.