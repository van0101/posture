# Changelog

All notable changes to Posture are listed here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the app uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html):

- **MAJOR** (2.0.0): a change that breaks how people use the app, such as removing a feature or resetting saved settings.
- **MINOR** (1.2.0): a new feature that keeps everything else working.
- **PATCH** (1.1.1): bug fixes, security fixes, wording changes.

## [Unreleased]

## [1.1.0] - 2026-10-30

### Added
- Beep sound choice: Short pips, Double blip (the original sound), Two long.
- Pitch choice for any beep: Bright or Bass note.
- Tapping a sound or pitch plays a preview. The choice is remembered on this device.
- Version number in the page footer, linked to this changelog.
- `CHANGELOG.md`, semantic versioning, and a CI check that fails when app files change without a version bump.

### Changed
- Renamed the app from "Posture DRS" to "Posture". The installed app's name updates the next time the phone refreshes it.
- Both sound modes now play the same generated sounds, so "Over music" and "Screen off" sound identical.
- The session-end chime follows the chosen pitch.

## [1.0.1] - 2026-10-30

### Security
- Content Security Policy: scripts load only from this site, no inline scripts, no plugins, no form posts.
- Scripts moved from the page into `app.js` so inline scripts can be blocked.
- `no-referrer` policy, so the page's URL is not sent to other sites.
- CodeQL static analysis, OWASP ZAP baseline scan of the live site, and Dependabot for GitHub Actions.

## [1.0.0] - 2026-10-30

### Added
- Posture reminder timer: session length, beep interval, double beep at start and at every interval.
- "Over music" mode (beeps over other audio) and "Screen off" mode (looping track that keeps going with the phone locked).
- Installable app (PWA) with offline support and home-screen icons.

[Unreleased]: https://github.com/van0101/posture/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/van0101/posture/compare/v1.0.1...v1.1.0
[1.0.1]: https://github.com/van0101/posture/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/van0101/posture/releases/tag/v1.0.0
