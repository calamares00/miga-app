# Baseline profile

`app/src/main/baseline-prof.txt` lists the app code that Android compiles ahead of time when the
app is installed from Google Play (through the `profileinstaller` library for other installs), so
start-up and the main screens do not run interpreted on the first launches. The AndroidX and
Compose libraries bring their own profiles; AGP merges them with this one and R8 rewrites the
names, so the rules use the source class names.

It is written by hand with wildcards (whole packages), because recording one needs a device or an
emulator with the Macrobenchmark library, and neither the build machine nor CI has one (CI minutes
are also limited). It covers:

- start-up: `MigaApp`, `MainActivity`, `StartupGate`, navigation, theme, shared components,
  settings, database, repositories and models;
- the screens used most: books, a book, a recipe, search and the shopping list.

## Checking it

`./gradlew assemblePlayRelease` and then:

- `app/build/intermediates/merged_art_profile/playRelease/mergePlayReleaseArtProfile/baseline-prof.txt`
  contains the `org/calamares/miga` rules;
- the APK has `assets/dexopt/baseline.prof`.

## Recording one instead

On a computer with a device or emulator: add a `:baselineprofile` module with the
`androidx.baselineprofile` plugin and a `BaselineProfileRule` test that opens the app, a book, a
recipe, search and the shopping list, then run `./gradlew :app:generateReleaseBaselineProfile`.
The generated `src/release/generated/baselineProfiles/baseline-prof.txt` would replace these
rules.
