# Security

Posture is a static web page plus an Android app. Neither has a backend or login, and no data leaves the phone.

## Checks in this repo
| Check | What it catches | Where to see results |
|---|---|---|
| CodeQL (`.github/workflows/codeql.yml`) | Risky code patterns in JS, Android Java and the workflows | Security > Code scanning |
| ZAP baseline (`.github/workflows/zap-baseline.yml`) | Header and config issues on the live site | Actions > ZAP run > Artifacts |
| Version check (`.github/workflows/version-check.yml`) | Releases that skip the version bump or changelog entry | Actions |
| Dependabot (`.github/dependabot.yml`) | Outdated GitHub Actions | Pull requests |
| Secret scanning (GitHub setting) | API keys or tokens committed by mistake | Security > Secret scanning |

## Hardening in the app
- Content Security Policy (meta tag): scripts only from this site, no inline scripts, no plugins, no form posts.
- `referrer` set to `no-referrer`.
- No `eval`, no user text written into the page as HTML, no cookies, no storage of personal data.

## Android permissions
| Permission | Why |
|---|---|
| `POST_NOTIFICATIONS` | Countdown notification with a Stop button |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keeps the session running with the app closed |
| `USE_EXACT_ALARM` (`SCHEDULE_EXACT_ALARM` on Android 12) | Beeps fire on time in deep sleep |
| `WAKE_LOCK` | Keeps the CPU awake for the length of one beep |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Optional "Allow in background" shortcut |

No internet permission: the Android app can't send data anywhere.
The APK is signed with a key stored only in GitHub Actions secrets.

## Reporting
Open a private advisory: Security > Advisories > Report a vulnerability.
