# Publishing on F-Droid

[F-Droid](https://f-droid.org) is the catalogue of free and open-source Android
apps. It builds apps from source itself, so a listing there is also a strong
trust signal. Tethertone qualifies: MIT licensed, no proprietary libraries
(CameraX and ZXing are Apache-2.0), no ads, no tracking.

## What is already in this repository

The store listing F-Droid displays is read from
[`fastlane/metadata/android/en-US/`](../fastlane/metadata/android/en-US):

| File | Shown as |
|---|---|
| `title.txt` | App name |
| `short_description.txt` | One-line summary (80 characters max) |
| `full_description.txt` | Description (limited HTML allowed) |
| `changelogs/<versionCode>.txt` | “What's new” for that release |
| `images/icon.png` | Store icon (512×512) |

Add `images/phoneScreenshots/1.png`, `2.png`, … when real screenshots exist.
Listings with screenshots are installed noticeably more.

Every release: add `changelogs/<versionCode>.txt`. The versionCode is
`major × 10000 + minor × 100 + patch` (0.2.0 → 200), computed from `VERSION`.

## Submitting (the maintainer does this once)

1. Fork https://gitlab.com/fdroid/fdroiddata and create
   `metadata/dev.tethertone.app.yml` with the draft below.
2. Test it locally if you can (`fdroid build -v -l dev.tethertone.app`); the
   F-Droid docs describe the build server setup.
3. Open a merge request. Reviewers often suggest small changes; follow them.

## Draft `metadata/dev.tethertone.app.yml`

```yaml
Categories:
  - Connectivity
  - Multimedia
License: MIT
AuthorName: Tethertone contributors
WebSite: https://aimensayoud.github.io/tethertone-website/
SourceCode: https://github.com/AimenSayoud/tethertone
IssueTracker: https://github.com/AimenSayoud/tethertone/issues
Changelog: https://github.com/AimenSayoud/tethertone/blob/main/CHANGELOG.md

AutoName: Tethertone

RepoType: git
Repo: https://github.com/AimenSayoud/tethertone.git

Builds:
  - versionName: 0.2.0
    versionCode: 200
    commit: v0.2.0
    subdir: android/androidApp
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 0.2.0
CurrentVersionCode: 200
```

### Things reviewers may raise

- **Version detection.** `versionName` and `versionCode` are computed in
  `androidApp/build.gradle.kts` from the `VERSION` file, not written as
  literals. If F-Droid's update checker can't read them, the simplest fix is
  `UpdateCheckData` pointing at `VERSION`, or writing the two values into
  `gradle.properties` at release time.
- **Toolchain.** The build uses AGP 9 and Kotlin 2.4 with JDK 17. If the build
  server lags behind, the reviewers will say which versions they support.
- **Signing.** F-Droid signs with its own key, so an F-Droid install and a
  GitHub-release install can't update each other. That is normal; the website
  can mention it once the listing is live.
