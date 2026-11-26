# Publishing on Google Play

Guide to filling in the Play Console with the information that applies to Miga. Keep it up to date
if the network features, the permissions or the AI change (and update `PRIVACY.md` at the same
time).

## Store listing

- Title, short and full description: `fastlane/metadata/android/es-ES/` (short ≤ 80 characters).
- Contact email: miga@calamares.org. Website: https://miga.calamares.org
- Privacy policy: **https://miga.calamares.org/privacy/** (in English,
  https://miga.calamares.org/en/privacy/), published from the `calamares00/miga-web` repository. The
  app also shows it in Settings → About (`app/src/main/assets/docs/privacy-es.md`, same text as
  `PRIVACY.md`). If it changes, copy it to miga-web's `content/` too and rebuild the website.
- Pack catalogue: https://miga.calamares.org/packs/catalog.json (cover and ZIP URLs may be relative
  to it).
- Application ID: `org.calamares.miga` (permanent once published).
- Category: Food & Drink. Suggested tags: recipes, shopping list.
- Graphic assets: 512×512 icon and 1024×500 feature graphic in `fastlane/metadata/android/*/images/`.
  Phone screenshots: 8 per language in `images/phoneScreenshots/`, 1080×1920 (9:16) PNG without
  transparency (valid for featuring the app). They are generated with the real app and pack recipes:
  `StoreShots{Es,En}Test` and `FavoritesShots{Es,En}Test` (each screenshot test in its own
  `./gradlew`, as they share the stored settings) and `tools/playstore/compose.py`, which adds the
  headline, subtitle and phone frame. They are uploaded by hand in Play Console (CI only uploads the
  app and the notes).
- No third-party brands in the listing (except the attribution "Data from Open Food Facts").

## Data safety

Recommended answers:

| Question | Answer |
|---|---|
| Does the app collect or share user data? | **Yes** (only through user actions, see below). |
| Is data encrypted in transit? | **Yes** for the AI, Open Food Facts and the pack catalogue (HTTPS). The user's own server may be `http://` on a home network, chosen by the user; the app warns if it is public and unencrypted. |
| Can users ask for their data to be deleted? | **Yes**: everything is deleted from the app or by uninstalling it; there is no data on the developer's servers. |
| Accounts | The app has no accounts (the account deletion requirement does not apply). |

Data types (mark as **shared**, **not collected by the developer**, **optional** and **at the
user's request**):

- **Photos** (Photos and videos): sent to the chosen AI provider only when importing a recipe from
  a photo or asking it to improve a photo. Purpose: app functionality.
- **Other user-generated content** (recipes, ingredients, text of web pages, dictated text, the
  user's own category, tag and equipment names): sent to the chosen AI provider when using AI
  features (import, polish recipes, content cleanup, ideas...), and to the user's own server if
  they set up sync. Purpose: app functionality.
- **Audio recordings**: no. Audio is processed by Android's speech recognition; the app only gets
  text.
- **Diagnostics / crashes**: no. The crash report stays on the device unless the user shares it
  by hand.
- **Identifiers, location, contacts, financial data, health**: no.

Note: requests to Gemini/Anthropic/OpenAI/OpenRouter and the other providers use the user's own API
key; Google Play still considers them "sharing", so they are declared.

## AI-generated content

- Declare in "App content" that the app generates content with AI.
- The app labels that content "Generated with AI" and lets users report it (**Report** button in the
  editor after importing with AI, health rating, nutrition, substitutions, dish search and bulk
  import). Reports arrive by email at miga@calamares.org (`AiContentReport.SUPPORT_EMAIL`).

## Other questionnaires

- **Content rating (IARC)**: reference/utility app, no violence, sex, gambling or purchases. User
  interaction: no (sync is with the user's own private server).
- **Target audience**: 13+ (not aimed at children; avoids the Families policy rules).
- **Ads**: contains no ads.
- **App access**: no sign-in required. Choose "All functionality is available without special
  access" and add in the instructions:

  > Miga has no accounts. Recipes, books, shopping lists, cooking mode, PDF export and the barcode
  > scanner work without any setup. Optional AI features (Settings › AI) use the user's own API key
  > for Google Gemini, Anthropic, OpenAI or OpenRouter; without a key they are hidden or ask for one.
  > Optional sync (Settings › Sync server) connects to a server the user hosts (miga-server).

  If the reviewer should test sync, add the URL, namespace and token of a demo server.
- **Foreground service** (App content › Foreground service permissions): the app declares
  `FOREGROUND_SERVICE_DATA_SYNC` for `AiWorkService`. Type: *Data sync* / user-initiated network
  transfer. Suggested description: "While an AI task the user started (importing recipes from
  photos, improving recipes) is running, a notification keeps the network request alive if the user
  leaves the app; it stops as soon as the task ends or is cancelled." A short video is needed: start
  "Mejorar varias recetas", leave the app and show the notification until it ends.
- **Health apps declaration**: the app is not a medical app, but it shows an AI-generated health
  rating and nutrition estimate (and the Open Food Facts product score). Choose only the
  nutrition/food category. Both carry the note "Es orientativo y no sustituye el consejo de un
  médico o dietista-nutricionista" and can be turned off in Settings › Artificial intelligence.
- **Sensitive permissions**: microphone (dictation) and camera (barcode scanner), both in the
  foreground and on request.
- **Payments**: the app contains no donations or external payments (not allowed outside Google Play
  Billing except for verified charities).

## Technical requirements

- `targetSdk` 36 and `compileSdk` 37 (minor 2), which the current AndroidX libraries need.
- Updates come only through Google Play: the `play` build uses Play's in-app updates (the update
  installs on Play's own screen without leaving the app).
- No native libraries of our own (the 16 KB page size requirement does not apply).
- Upload the AAB (`bundlePlayRelease`) with Play App Signing; keep a safe copy of the upload key.
- Signing: CI signs the AAB and the release APKs if the GitHub secrets `RELEASE_KEYSTORE_BASE64`,
  `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD` exist. Create the
  upload key once:

  ```bash
  keytool -genkeypair -v -keystore miga-upload.jks -alias miga-upload \
    -keyalg RSA -keysize 4096 -validity 10000
  base64 -w0 miga-upload.jks   # value of RELEASE_KEYSTORE_BASE64
  ```
- Test APKs (`beta-latest`) are no longer published: test versions go only through the Play closed
  beta. Anyone with an old `beta-latest` installed (signed with the debug key) cannot move to the
  Play version without uninstalling: they should make a backup from Settings first.

## Testing before production

1. **Internal testing** (up to 100 testers) with the signed AAB. Check the pre-launch report
   (crashes, freezes, accessibility, screenshots on several devices) and Android vitals (crashes
   and ANRs).
2. **Closed testing**: see the next section.
3. **Production**: staged rollout (for example, 20 % → 100 %) watching crashes and ANRs.

## New (personal) developer account

Before applying for production access, a closed test is needed with at least **12 testers** who
keep the app installed for **14 days in a row**.

## New versions

1. Raise `versionName` in `app/build.gradle.kts` (1.0.1 for fixes, 1.1.0 for new features) and
   write the notes in `fastlane/metadata/android/{es-ES,en-US}/changelogs/<versionCode>.txt` (at
   most 500 characters; these are the notes Play shows).
2. Merge into `main`. CI builds, signs, publishes the `v<versionName>` Release on GitHub (as a
   *pre-release*) and, if the automatic upload is set up (below), uploads the AAB to the **closed
   beta** (the `PLAY_TRACK` track, today `alpha`) with those notes and the deobfuscation file.
3. For the public, see "Closed beta and production".

`versionCode` goes up on every commit, so every merge into `main` carries a higher code than the
previous one, as Play requires.

## Closed beta and production

A single branch: `main`. Every new version goes to the closed beta first, and **the same version**
goes to production (the same AAB the testers tried, without building again):

- **After testing it**: GitHub → Actions → **Publicar en producción** → *Run workflow*. It asks for
  the version (empty = the latest in the beta) and the percentage of users (100 % or a staged
  rollout of 50, 20, 10 or 5 %). To widen a staged rollout, run it again with the same version and
  a higher percentage. At 100 %, the GitHub Release stops being a *pre-release* and becomes the
  latest one, which is what the fdroid build's update notice reads, and the `public-v<version>`
  tag that F-Droid follows is created.
- **Without waiting for the beta**: add the **`env/pro`** label to the pull request before merging
  it. On merge, the version goes to the beta and right away to production, at 100 %.
- No commits or pull requests are needed to publish: the version is already in `main` and in the
  beta.

Production versions go through Google's review (hours to days). To choose the exact moment, turn
on **Managed publishing** in Play Console (Publishing overview → Managed publishing): what is
approved waits until you press **Publish changes**.

The service account needs the **Release to production** permission (step 3 below).

## Automatic upload to Google Play

The workflow's "Upload to Google Play" step uses a Google Cloud service account. It is set up once:

1. At https://console.cloud.google.com create a project (for example "miga-play") and enable the
   **Google Play Android Developer API**
   (https://console.cloud.google.com/apis/library/androidpublisher.googleapis.com).
2. In **IAM & Admin → Service accounts**, create an account (for example `miga-ci`). It needs no
   Google Cloud roles. In its **Keys** tab, **Add key → JSON**: a `.json` file is downloaded. Keep
   it safe: it gives access to publish.
3. In Play Console, **Users and permissions → Invite new users**: enter the service account's email
   (`miga-ci@<project>.iam.gserviceaccount.com`), under **App permissions** add Miga and tick
   **Release to testing tracks** (and **Release to production** to publish to production from CI).
   Send the invitation; it is accepted on its own.
4. In GitHub, https://github.com/calamares00/miga-app/settings/secrets/actions, create the
   `PLAY_SERVICE_ACCOUNT_JSON` secret with the whole content of the `.json`.
5. Optional: in the **Variables** tab of the same page, create `PLAY_TRACK` with the beta track:
   `internal` (default) or `alpha` (closed testing; goes through Google's review). Today it is
   `alpha`. Production is not set here: it goes through «Publicar en producción» or the `env/pro`
   label.

Without that secret, or without the signing key, the step is skipped and only the GitHub Release is
published. An app's first version has to be uploaded by hand from Play Console (done with 1.0.0);
from then on the API can upload the next ones.
