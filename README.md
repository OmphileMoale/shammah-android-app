# Shammah Urban Church – Android App

Android mobile app for **Shammah Urban Church**, built for XISD6329 Task 2. It works together with the church website (`shammah-website`) and is developed with Azure DevOps (Boards, Repos, Pipelines).

## Team

|Member|Main responsibilities|
|-|-|
|Omphile Kutlwano Moale|Website updates, database tables and data, REST API|
|Oladele Khashane Lucky Abdul-Azeez Rakgolela|Android screens, register/login, SSO, settings, input validation|
|Lebo Ramoshaba|Azure Pipeline, automated tests, README and documentation, demo video|

## Features

* Register and log in. Passwords are salted and hashed with PBKDF2 (HMAC-SHA256, 65,536 iterations); no plain-text passwords are stored.
* Google / YouTube sign-in screen (see *Cloud and SSO configuration* below).
* User settings that each user can change.
* Church content: prayer requests, donations, events, church groups and journal entries.
* Input validation, so invalid input shows a message instead of crashing the app.
* Works fully offline using a local SQLite database.
* Runs on a physical Android phone (not only an emulator).

## Database

Local SQLite database with 7 tables, each seeded with 10 simulated records:
`users`, `prayer\_requests`, `donations`, `events`, `church\_groups`, `journal\_entries`, `user\_settings`.

**Demo login:** `pastor@church.com` / `Shammah@123` (simulated data for marking only).

## Tech stack

* Kotlin, Android SDK (minSdk 26, targetSdk 36)
* SQLite (`DatabaseHelper`)
* Credential Manager / Google ID (`GoogleSso`)
* Firebase Auth and Realtime Database over HTTPS (`CloudService`, optional)
* Gradle (Kotlin DSL)

## Project structure

```
app/src/main/java/com/example/shammah/MainActivity.kt   App code (UI, database, hashing, cloud, SSO)
app/src/main/AndroidManifest.xml                         App manifest and permissions
app/src/test/                                            Unit tests
app/build.gradle.kts                                     Module build file
azure-pipelines.yml                                      CI pipeline definition
release/                                                 Built debug APK (convenience copy)
```

## How to build and run

1. Install **Android Studio** (latest stable) and open this folder.
2. Let Gradle sync finish.
3. Connect a phone with USB debugging on, or start an emulator.
4. Press **Run**. The app opens on the login screen.

Command line:

```
./gradlew assembleDebug      # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest  # runs unit tests
```

### Install the APK directly

1. Copy the APK from `release/` (or from the pipeline artifact) to your phone.
2. Allow *Install unknown apps* for your file manager or browser.
3. Open the APK and install it.

## Cloud and SSO configuration

`AppConfig` in `MainActivity.kt` holds three values:

|Value|Purpose|
|-|-|
|`FIREBASE\_API\_KEY`|Firebase Web API key|
|`FIREBASE\_DB\_URL`|Realtime Database URL|
|`GOOGLE\_WEB\_CLIENT\_ID`|OAuth web client ID for Google sign-in|

They are placeholders (`YOUR\_...`) in this repository. **Do not commit real keys.** While they are placeholders, the app runs offline against the local SQLite database, and the Google/YouTube sign-in uses an approval form where the user enters an email and name instead of a real Google account chooser. Fill in the values to enable cloud sync and real Google sign-in.

## Azure DevOps

|Service|Use|
|-|-|
|Azure Boards|Epics, features, user stories and sprints (Sprint 1 and Sprint 2) with work assigned to each member|
|Azure Repos|`shammah-android-app` and `shammah-website`, with commits linked to work items|
|Azure Pipelines|Automatic build and unit tests on every push to `main`; the APK is published as a pipeline artifact|

To download the latest APK: **Pipelines → latest run → Artifacts**.

## Testing

Unit tests run in the pipeline with `testDebugUnitTest`. The app was also tested manually on a physical Android phone.

## Known limitations

* Real Google SSO and Firebase sync need the three `AppConfig` values to be filled in.
* The APK in `release/` is a **debug** build, not a signed release build.

## Related repositories

* Website: `shammah-website` (Azure Repos)
* \- GitHub mirror: https://github.com/OmphileMoale/shammah-android-app
* \- Azure Repos: https://dev.azure.com/ST10455873/XISD6329-Shammah-Urban-Church-CMES/\_git/shammah-android-app

