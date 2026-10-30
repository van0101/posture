# Posture

Posture reminder timer. Installable on Android and iPhone as a PWA.

## Publish with GitHub Pages
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
4. Push, then tag: `git tag vX.Y.Z && git push origin vX.Y.Z`.

CI fails if the versions don't match or weren't bumped. Phones get the new release after the app is closed and reopened (sometimes twice).
