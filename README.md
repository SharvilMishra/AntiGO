# Android client foundation

This repository contains the Android client foundation. The initial app has no seeded conversations, messages, profile, or AI replies. Backend requests are not implemented.

## Build

Open the project in Android Studio or run `gradlew assembleDebug` and `gradlew testDebugUnitTest` from this directory.

## Release signing

Set `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` in the environment, or provide matching properties in the ignored `local.properties` file. Signing material is not included in this repository.

The API base URL is configured as an HTTPS-only Gradle build value and must be replaced with the service endpoint before network integration.

## Android website distribution

The download page is `index.html` in the repository root, which is also the GitHub Pages source currently selected for this repository. Build a release with the signing values configured in the environment or ignored `local.properties`, then run the `publishReleaseApkToWebsite` Gradle task. It copies the signed APK to `downloads/antigo.apk`, which the page links to. Commit and push both files to publish the download on the site.

Keep the signing key and passwords private and backed up. Future APK updates must use the same signing key so Android can install them as updates.

## Firebase Authentication setup

The Android app uses Firebase Authentication with email and password for account creation, sign-in, password reset, and sign-out. In the Firebase console, create/register an Android app with package `com.sharvil.antigo`, enable the Email/Password provider, download its `google-services.json`, and place that file at `app/google-services.json`. The Google services Gradle plugin is applied when this file is present. Without it, the app stays signed out and explains that Firebase is not configured.

The static APK download website does not need Firebase. Firebase is required in the Android app because that is where users sign in. Add Firebase to the website only if the website itself needs user accounts or protected pages.
