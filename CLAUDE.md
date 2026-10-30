# Posture: repo rules

Static PWA (index.html, app.js, sw.js, manifest.webmanifest, icons/) served by GitHub Pages
from `main` at https://van0101.github.io/posture/.

## Every change that ships to users
1. Bump the version using semver: MAJOR for breaking changes (removed feature, reset settings),
   MINOR for new features, PATCH for fixes, security and wording.
2. Set it in all four places: `APP_VERSION` in app.js, `VERSION` in sw.js (`posture-vX.Y.Z`),
   the `#ver` footer text in index.html, and a new top entry in CHANGELOG.md
   (Keep a Changelog format, with the compare link at the bottom).
3. CI (`.github/workflows/version-check.yml`) fails if these don't match or the version wasn't bumped.
4. Tagging is automatic: `.github/workflows/release.yml` creates the `vX.Y.Z` tag and GitHub Release from the CHANGELOG section.

## Conventions
- App name is "Posture". Do not use "DRS" anywhere.
- Keep the Content Security Policy: no inline scripts or event handlers, no new external hosts
  without updating the CSP meta tag in index.html.
- New files the app needs offline must be added to `SHELL` in sw.js.
