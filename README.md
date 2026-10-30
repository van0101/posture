# Posture

Posture reminder timer: set a session and an interval, and it beeps so you remember to sit up.

- **Android app:** beeps with the app closed, the phone locked, or music playing.
  Download `posture.apk` from the [latest release](https://github.com/van0101/posture/releases/latest).
- **Web app (PWA):** https://van0101.github.io/posture/ beeps while the page is open. Installable on Android and iPhone.

## Install the Android app
1. On the phone, open the [latest release](https://github.com/van0101/posture/releases/latest) and tap `posture.apk`.
2. When Chrome asks, allow it to install unknown apps, then tap Install.
3. Open Posture, tap Start, and allow notifications.

## Publish the web app with GitHub Pages
1. Create a public repo named `posture` on GitHub.
2. Push these files to the `main` branch (root of the repo).
3. Repo > Settings > Pages > Source: "Deploy from a branch" > Branch: `main`, folder `/ (root)` > Save.
4. After about a minute, open https://<your-username>.github.io/posture/

## Install on the phone
- Android (Chrome): open the URL > ⋮ menu > "Install app" (or "Add to Home screen") > Install.
- iPhone (Safari): open the URL > Share > "Add to Home Screen" > Add.

## Security
See SECURITY.md. CodeQL, ZAP and Dependabot run in GitHub Actions.

## Versioning
The app follows [semver](https://semver.org). The current version shows at the bottom of the page.
See [CHANGELOG.md](CHANGELOG.md) for what changed in each release.

## Updating
1. Make the change in index.html or app.js.
2. Bump the version in app.js (`APP_VERSION`), sw.js (`VERSION`) and index.html (`#ver`).
3. Add an entry at the top of CHANGELOG.md.
4. Push. GitHub Actions tags the release and publishes it under Releases.

CI fails if the versions don't match or weren't bumped. Phones get the new release after the app is closed and reopened (sometimes twice).
