# Google Play Setup — Piano Piano

Updated 22 November 2026.

**Piano Piano** is registered in Google Play Console under the personal Lofranco Labs developer account and live on the **Internal testing** track for the owner (`lofranco.francesco@gmail.com`).

- **Package**: `dev.lofrancolabs.pianopiano`
- **Console App ID**: `4976298622681174229`
- **Dashboard**: `https://play.google.com/console/u/0/developers/6260746674436156402/app/4976298622681174229/app-dashboard`
- **Internal Testing Track**: `https://play.google.com/console/u/0/developers/6260746674436156402/app/4976298622681174229/tracks/4701203269518070095`
- **Internal Testing Opt-In URL**: `https://play.google.com/apps/internaltest/4701203269518070095`
- **Default language**: English (United Kingdom), `en-GB`
- **Pricing & audience**: Free app; adults 18 and over
- **Category**: Education
- **Support email**: `lofranco.francesco@gmail.com`
- **Website**: `https://lofrancolabs.dev`
- **Privacy policy**: `https://lofrancolabs.dev/privacy.html`

## Saved Declarations & Testers

All 9 **App content** items are completed (0 needing attention):
- Privacy policy linked to `https://lofrancolabs.dev/privacy.html` (covers on-device-only microphone DSP analysis).
- App access: all functionality available without sign-in or restrictions.
- No ads, no advertising ID, no government affiliation, no financial or health features.
- Data safety: no user data collected or shared.
- IARC content rating completed (PEGI 3 / ESRB Everyone / USK All ages / IARC Generic 3+).
- **Internal testing testers**: `Internal - Owner` list containing solely `lofranco.francesco@gmail.com` (closed testing is not active).

## Store Listing Copy & Artwork

- Store listing copy is maintained in [listing-en-GB.md](play-store/listing-en-GB.md).
- Vector icon and feature graphic sources are in `docs/play-store/` (`app-icon.svg`, `feature-graphic.svg`); rendered PNG uploads (`icon-512.png`, `feature-graphic-1024x500.png`, `screenshot-1-stage.png`, `screenshot-2-playing.png`) are kept locally in ignored `artifacts/play-store/`.
- Main Store Listing descriptions and the 4 PNG assets can be uploaded directly in Play Console under **Grow users → Store presence → Main store listing** when preparing for Closed/Production release (select *"Don't label assets"* per owner preference; Internal Testing is already live without them).

## Automated Releases from `main`

Every push to `main` touching app code runs [`.github/workflows/release.yml`](../.github/workflows/release.yml):
1. Runs `./gradlew :app:testDebugUnitTest :app:lintRelease`.
2. Generates `en-GB` release notes from Git commit subjects since the last `play/*` tag.
3. Builds and signs an optimized App Bundle (`./gradlew :app:bundleRelease`) with `versionCode = git rev-list --count HEAD` and `versionName = 1.0.<count>`.
4. Publishes to Google Play via `./gradlew :app:publishReleaseBundle` targeting `PLAY_TRACK` (`internal` by default) with `PLAY_RELEASE_STATUS` (`completed` by default).
5. Tags the commit as `play/<versionCode>`.

### Repository Secrets & Variables

- Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON` (`publisher@lofranco-labs-ba7769.iam.gserviceaccount.com` with the 6 app-scoped permissions matching Drift).
- Variable: `PLAY_TRACK=internal` (change to `alpha`, `beta`, or `production` when promoting).
- Local upload keystore is kept strictly outside Git at `/Users/fra/keys/pianopiano-upload.jks` and referenced via ignored `keystore.properties`.
