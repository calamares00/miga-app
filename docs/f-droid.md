# Publishing on F-Droid

F-Droid builds the **`fdroid`** flavor, which has no proprietary dependencies (no Google Play
Services, Firebase, analytics or Google libraries); the `play` flavor only adds Play's in-app
updates. F-Droid builds the app on its servers from a git tag of this repository.

## Before submitting it

- A public version must exist, with its `public-v<version>` tag (created by the «Publicar en
  producción» workflow, or by the `env/pro` label, when the version reaches production).
- Its Release must be **signed** with the upload key (GitHub secrets set up) and include
  `miga-v<version>-universal-release.apk`.
- Screenshots in `fastlane/metadata/android/<language>/images/phoneScreenshots/` (the same as for
  Play). F-Droid reads the title, descriptions, icon, screenshots and changelogs from `fastlane/`.
- SHA-256 fingerprint of the signing certificate, without colons and in lower case:

  ```bash
  keytool -list -v -keystore miga-upload.jks -alias miga-upload \
    | grep 'SHA256:' | sed 's/.*SHA256: //; s/://g' | tr 'A-F' 'a-f'
  ```

## Submission

1. Create an account at https://gitlab.com and fork https://gitlab.com/fdroid/fdroiddata.
2. In the fork, create a branch and the file `metadata/org.calamares.miga.yml` with the content
   below (replacing the fingerprint and the commit).
3. Open a merge request against `fdroid/fdroiddata` titled `New app: Miga` and fill in the
   template's checklist. The merge request's pipelines (lint and build) must pass.
4. Answer the reviewers. It usually takes days to weeks. Once merged, the app appears on F-Droid
   after the next build cycle (one or two more days).

Alternative without GitLab or YAML: open a request at https://gitlab.com/fdroid/rfp/-/issues. It is
slower, because it depends on a volunteer packaging it.

## Metadata (`metadata/org.calamares.miga.yml`)

```yaml
Categories:
  - Diet
License: GPL-3.0-only
AuthorName: Calamares
AuthorEmail: miga@calamares.org
WebSite: https://miga.calamares.org
SourceCode: https://github.com/calamares00/miga-app
IssueTracker: https://github.com/calamares00/miga-app/issues
Changelog: https://github.com/calamares00/miga-app/releases

AutoName: Miga

RepoType: git
Repo: https://github.com/calamares00/miga-app.git
Binaries: https://github.com/calamares00/miga-app/releases/download/v%v/miga-v%v-universal-release.apk

Builds:
  - versionName: 1.1.0
    versionCode: <versionCode of the public-v1.1.0 tag>
    commit: <full hash of the public-v1.1.0 tag's commit>
    subdir: app
    gradle:
      - fdroid
    output: build/outputs/apk/fdroid/release/app-fdroid-universal-release-unsigned.apk

AllowedAPKSigningKeys: <SHA-256 fingerprint of the certificate>

AutoUpdateMode: Version
UpdateCheckMode: Tags ^public-v
CurrentVersion: 1.1.0
CurrentVersionCode: <same versionCode>
```

- `versionCode` and `CurrentVersionCode`: the `versionCode` in `app/build.gradle.kts` at the tag.
- `Categories`: the reviewer may ask for another one; F-Droid revises its list of categories often.
- `gradle: fdroid`: the flavor without Google libraries. It announces new versions with the latest
  stable GitHub Release, unless an F-Droid client installed it, which updates it on its own.
- `output`: with the ABI splits, Gradle builds several APKs; F-Droid only uses the universal one.
- `UpdateCheckMode: Tags ^public-v`: F-Droid only takes public versions. Every closed beta version
  also has its `v<version>` tag, so publishing to production adds `public-v<version>`.
- `Binaries` + `AllowedAPKSigningKeys` ask for a **reproducible build**: F-Droid builds, compares
  its APK with the Release's and, if they are identical, publishes ours with our signature. So
  whoever installs from F-Droid, GitHub or Obtainium can move from one to another without
  uninstalling. If they do not match, the reviewer will say so: either the difference is fixed or
  those two lines are removed and F-Droid signs with its own key (then F-Droid installs cannot be
  updated with APKs from another source, nor the other way round).
- With `AutoUpdateMode: Version` and `UpdateCheckMode: Tags ^public-v`, every new public version is
  built on its own; no further merge request is needed.

## Anti-features

F-Droid will probably mark **NonFreeNet**: the cooking assistant uses non-free services (Gemini,
Anthropic, OpenAI, OpenRouter and others) with the user's own key, and the scanner looks up Open
Food Facts (which is open). It is common and does not prevent publishing. If the reviewer asks for
it, add to the YAML:

```yaml
AntiFeatures:
  NonFreeNet:
    en-US: Optional cooking assistant using Gemini, Anthropic, OpenAI or OpenRouter with the user's own API key.
```

## New versions

Nothing to do on F-Droid: when a version reaches production, the `public-v<version>` tag is created
and F-Droid picks it up. Versions that stay in the closed beta never reach F-Droid.
