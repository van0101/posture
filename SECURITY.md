# Security

Posture is a static page: no backend, no login, no data leaves the phone.

## Checks in this repo
| Check | What it catches | Where to see results |
|---|---|---|
| CodeQL (`.github/workflows/codeql.yml`) | Risky code patterns in JS and in the workflows | Security > Code scanning |
| ZAP baseline (`.github/workflows/zap-baseline.yml`) | Header and config issues on the live site | Actions > ZAP run > Artifacts |
| Version check (`.github/workflows/version-check.yml`) | Releases that skip the version bump or changelog entry | Actions |
| Dependabot (`.github/dependabot.yml`) | Outdated GitHub Actions | Pull requests |
| Secret scanning (GitHub setting) | API keys or tokens committed by mistake | Security > Secret scanning |

## Hardening in the app
- Content Security Policy (meta tag): scripts only from this site, no inline scripts, no plugins, no form posts.
- `referrer` set to `no-referrer`.
- No `eval`, no user text written into the page as HTML, no cookies, no storage of personal data.

## Reporting
Open a private advisory: Security > Advisories > Report a vulnerability.
