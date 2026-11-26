# Miga

**Your recipes. Your books. Your kitchen.**

Miga is a recipe and shopping list app for Android, with no accounts and no
ads. Your data stays on the phone; the internet is only used for the optional
features you choose (the cooking assistant with your own key, Open Food Facts,
the recipe pack catalogue and sync with your own server).

Website: **https://miga.calamares.org** · Sync server:
[calamares00/miga-server](https://github.com/calamares00/miga-server) · Website code:
[calamares00/miga-web](https://github.com/calamares00/miga-web)

## Features

- **Recipe books**: keep your recipes in separate books (for example, one per
  family member), each with its own cover.
- **Complete recipes**: ingredients with quantity and unit, numbered steps,
  photo, equipment, difficulty, tags and sub-recipe groups (sauces,
  sides...).
- **Categories by kind of dish, search and filters**, including allergen
  filters.
- **Cooking mode**: large step-by-step screen with timers.
- **Ingredient autocomplete** from what you have already written.
- **Export/import**: a recipe or a whole book as text, PDF or JSON/ZIP (with
  photos), and a full backup of the library.
- **Cooking assistant** (optional): imports recipes from photos, websites,
  posts and videos, polishes them and suggests ideas, with the user's own key
  for Gemini, Claude, OpenAI, OpenRouter, DeepSeek, Mistral, Groq or a custom
  server. Without a key it makes no network calls.
- Optional **biometric lock** (fingerprint, face or device PIN).
- Light, dark or system **theme**, with several colour themes.
- **Shopping list** with product scanner (Open Food Facts), templates,
  several lists and order by supermarket.
- Optional **sync** with your own server (miga-server).

Two builds of the same app differ only in how they learn about a new version:
`play` (the Google Play AAB, with Play's in-app updates) and `fdroid` (the
GitHub and F-Droid APKs, without Google libraries, which look at the latest
stable GitHub Release). Publishing guides: [Google Play](docs/google-play.md)
and [F-Droid](docs/f-droid.md).

## Tech stack

- Kotlin + [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3)
- [Room](https://developer.android.com/training/data-storage/room) for local storage
- [DataStore Preferences](https://developer.android.com/topic/libraries/architecture/datastore) for settings
- [Navigation Compose](https://developer.android.com/jetpack/compose/navigation)
- [Coil](https://coil-kt.github.io/coil/) for loading images
- `kotlinx.serialization` for JSON export/import
- `androidx.biometric` for the fingerprint/face lock

## Privacy

Miga has no accounts, no server of its own, no analytics and no ads. See
[PRIVACY.md](PRIVACY.md) for the details of which data the app handles and the
network connections it makes (all optional).

## Building locally

```bash
./gradlew :app:assembleFdroidDebug   # or assemblePlayDebug
```

### Tests

```bash
./gradlew :app:testPlayDebugUnitTest
```

Unit tests (JVM, no emulator) of the domain logic and, with Robolectric, of
the screens.

Debug APKs are signed with each machine's own debug key: there is no key in
the repository. Test versions reach testers through the Google Play closed
beta.

## CI/CD

`.github/workflows/android-build.yml` first works out what changed (job
«Changes»): the app (code, resources, Gradle), only the workflows, or only
docs and the store listing. Then, in parallel:

| Job | When | What |
|---|---|---|
| Tests | the app or the workflows changed | Unit tests (`play`) and compilation of `fdroid` |
| Lint | the app changed | Lint of each flavor, one runner each |
| Build | the app changed | R8 release APK (`fdroid`) and AAB (`play`) as checks; on `main` with a new version, the signed build |
| Publish | new version on `main`, after the three above | GitHub Release (*pre-release*) with the APKs per architecture (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`, universal), the AAB and `SHA256SUMS.txt`, and upload to the Google Play closed beta |

A pull request only checks that an app change raises the version and has its
release notes; the push of its branch runs everything else. The public gets a
version with the «Publicar en producción» workflow, or right away with the
`env/pro` label (see [docs/google-play.md](docs/google-play.md)).

### Release signing

The release build is signed with a keystore that does **not** live in the
repository, but in 4 GitHub Actions secrets (*Settings → Secrets and
variables → Actions*):

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_KEYSTORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

Without them, the release build is unsigned and the GitHub Release is marked
as a *pre-release*.

### CI security

- Actions are pinned to their commit (`uses: action@<sha> # vX.Y.Z`); Dependabot updates them.
- Each job has only the permissions it needs, and checkouts do not keep credentials except where
  tags are pushed.
- `codeql.yml`: security analysis of the code (Security › Code scanning).
- `dependencies.yml`: submits the Gradle dependencies to GitHub's dependency graph (Dependabot
  alerts) and, on pull requests, fails when a new dependency has known vulnerabilities.
- Every published APK and AAB has a provenance attestation, checked with
  `gh attestation verify miga-v<version>-universal-release.apk -R calamares00/miga-app`.

## Distribution

- `fastlane/metadata/android/{es-ES,en-US}/` holds the title, descriptions,
  images and changelogs for Google Play and F-Droid.
- Every GitHub Release includes APKs ready to install directly or through
  [Obtainium](https://github.com/ImranR98/Obtainium), and the `.aab` uploaded
  to Play Console.
- `SHA256SUMS.txt`, in every Release, has the SHA-256 of each file. To check a
  download, put it in the same folder and run
  `sha256sum -c SHA256SUMS.txt --ignore-missing`.
