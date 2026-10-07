# AntiGO for Android

This repository contains the Android client foundation. The initial app has no seeded conversations, messages, profile, or AI replies. Backend requests are not implemented.

## Build

Open the project in Android Studio or run `gradlew assembleDebug` and `gradlew testDebugUnitTest` from this directory.

## Release signing

Set `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` in the environment, or provide matching properties in the ignored `local.properties` file. Signing material is not included in this repository.

The API base URL is configured as an HTTPS-only Gradle build value and must be replaced with the service endpoint before network integration.

## Android website distribution

The download page is `index.html` in the repository root, which is also the GitHub Pages source currently selected for this repository. The `Build and publish Android APK` GitHub Actions workflow builds a signed release and attaches it to a GitHub release. The page looks up the latest release and links its `antigo.apk` asset, so the APK is not committed into the source repository.

Before running the workflow, add repository Actions secrets named `FIREBASE_CONFIG_BASE64`, `RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`. Local values are prepared in the ignored `release-signing/github-actions-secrets.txt` file; see `release-signing/SETUP.txt` for how to add them without exposing them in the repository. Choose a new tag such as `v1.0.0` for the first release. For each later release, increment `versionCode` and `versionName` in `app/build.gradle.kts`, and use the matching `v<versionName>` release tag. The app's update prompt compares the installed version name with the latest release tag.

The Android app checks GitHub Releases for a newer version at launch, at most once every 12 hours. If one is available, it offers to open the download page at `https://sharvilmishra.github.io/AntiGO/`; users can then download and confirm installation of the APK.

Keep the signing key and passwords private and backed up. Future APK updates must use the same signing key so Android can install them as updates.

## Firebase Authentication setup

The Android app uses Firebase Authentication for email/password and Google sign-in, account creation, password reset, and sign-out. In Firebase Authentication, enable the Email/Password and Google providers. In Firebase project settings, register the Android app with package `com.sharvil.antigo` and add the SHA-1 signing fingerprints for the debug certificate and the release signing key. Download the refreshed `google-services.json` and put it at `app/google-services.json` for local builds. The Google sign-in button uses the web OAuth client ID generated from that file. For GitHub Actions, store the refreshed file as the `FIREBASE_CONFIG_BASE64` repository secret. The Google services Gradle plugin is applied when the configuration file is present. Without it, the app stays signed out and explains that Firebase is not configured.

The static APK download website does not need Firebase. Firebase is required in the Android app because that is where users sign in. Add Firebase to the website only if the website itself needs user accounts or protected pages.
