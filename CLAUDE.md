# Posture: repo rules

Two apps, one version:
- Web PWA (index.html, app.js, sw.js, manifest.webmanifest, icons/) served by GitHub Pages
  from `main` at https://van0101.github.io/posture/.
- Android app in `android/` (Java, no AndroidX, Gradle 8.14.3, AGP in android/build.gradle).
  Its versionName/versionCode are read from `APP_VERSION` in app.js; don't set them by hand.
  Beeps are AlarmManager.setAlarmClock alarms handled by PostureService (foreground service),
  played on USAGE_ALARM with transient ducking focus. Sounds.java mirrors SOUNDS/VOICES in app.js:
  change both together.

## Every change that ships to users
1. Bump the version using semver: MAJOR for breaking changes (removed feature, reset settings),
   MINOR for new features, PATCH for fixes, security and wording.
2. Set it in all four places: `APP_VERSION` in app.js, `VERSION` in sw.js (`posture-vX.Y.Z`),
   the `#ver` footer text in index.html, and a new top entry in CHANGELOG.md
   (Keep a Changelog format, with the compare link at the bottom).
3. CI (`.github/workflows/version-check.yml`) fails if these don't match or the version wasn't bumped.
4. Android changes count as shipping changes too (CI includes `android/` in the bump check).
5. Tagging is automatic: `.github/workflows/release.yml` creates the `vX.Y.Z` tag and GitHub Release from the CHANGELOG section.

## Android builds
- `.github/workflows/android.yml` compiles on every push touching android/ or app.js.
- `.github/workflows/release.yml` builds the signed APK and attaches `posture.apk` to the release.
  Signing uses repo secrets `ANDROID_KEYSTORE_BASE64` and `ANDROID_KEYSTORE_PASSWORD` (key alias `posture`).
  Never commit a keystore.
- Build logs aren't readable from Claude sessions; failures are surfaced as a run annotation.

## Conventions
- App name is "Posture". Do not use "DRS" anywhere.
- Keep the Content Security Policy: no inline scripts or event handlers, no new external hosts
  without updating the CSP meta tag in index.html.
- New files the app needs offline must be added to `SHELL` in sw.js.
