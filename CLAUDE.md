# Miga app

Android recipe and shopping list app (`org.calamares.miga`): Kotlin, Jetpack Compose, Room.

## Conventions

- Commits, pull requests, code comments and technical docs are written in **English**. App texts
  exist in Spanish and English (`res/values-es/strings.xml` and `res/values/strings.xml`), read
  through `L10n.str`.
- In the app, the AI features are called the "cooking assistant" (asistente de cocina), not "AI".
  Its guardrails keep it on cooking topics only.
- Every merge to `main` with a new `versionName` publishes a release: signed APKs, AAB and
  `SHA256SUMS.txt` on GitHub, plus an upload to Google Play (track from the `PLAY_TRACK` repo
  variable). So any change to `main` needs:
  - `versionName` bumped and `versionCode` + 1 in `app/build.gradle.kts`;
  - release notes `<versionCode>.txt` in `app/src/main/assets/changelogs` (es),
    `app/src/main/assets/changelogs-en` and `fastlane/metadata/android/{es-ES,en-US}/changelogs`
    (max 500 characters each). Older notes stay.
- The app cannot be built in Claude Code cloud sessions (Maven is blocked): rely on CI, which also
  builds the release AAB on every branch.
- Units go through `UnitInflection` (singular/plural). `calamares00/miga-server` keeps a copy for
  its PDFs: keep both in step.
- Publishing guides: `docs/google-play.md` and `docs/f-droid.md`.
