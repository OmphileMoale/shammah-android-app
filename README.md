# Shammah Urban Church - Android App

Android mobile app for **Shammah Urban Church**, built for XISD6329 (Task 3 - final submission). The app and the church website (`shammah-website`) share **one MySQL database** through a PHP REST API, so a member can register on the website and log in to the app with the same email and password.

## Purpose

The church needed one place where members can stay connected during the week: see upcoming events and RSVP, join church groups, give, send prayer requests, read the daily scripture and keep a private journal. Pastors get a dashboard to manage members, review donations and approve prayer requests.

## Team

| Member | Main responsibilities |
|---|---|
| Omphile Kutlwano Moale | Website updates, database tables and data, REST API |
| Oladele Khashane Lucky Abdul-Azeez Rakgolela | Android screens, register/login, SSO, settings, input validation |
| Lebo Ramoshaba | Azure Pipeline, automated tests, README and documentation, demo video |

## How it works

```
Android app (Kotlin)  <--HTTPS/HTTP + JSON-->  PHP REST API (website /api)  <-->  MySQL (shammah_db)
        |                                              |
  SQLite on phone                               Same accounts as the website
  (journal, settings,                           6-digit codes by email / SMS
   cached profile)
```

- `ApiClient.kt` sends every request to the church server.
- If the saved server address does not answer, the app tries its fallback addresses and keeps the one that works.
- The last lists the server sent are cached on the phone, so screens open instantly and still work offline.

## Features

- **Accounts:** register and log in with the same credentials as the website. Passwords are never stored in plain text (the server hashes them).
- **Two-step sign-in:** a 6-digit code is sent by the server to the member's email (and by SMS where configured).
- **Sign in with Google (SSO):** uses Android Credential Manager. Until a Google web client ID is added, the app uses the "enter your Gmail + code" sign-in instead (see Known limitations).
- **Roles:** Member and Pastor. Pastors see a dashboard with *Manage members*, *View donations* and *Approve prayers*.
- **Events:** list of events with RSVP, including RSVPs made while offline.
- **Church groups (ministries)** and **prayer requests** (approved by a pastor before they appear).
- **Giving:** offering, tithe and other categories, with card-payment validation (simulated, no real payment is taken).
- **Daily scripture** fetched live from bible-api.com.
- **Private journal** and **settings** (notifications, appearance, security) stored on the phone.
- **Input validation and friendly errors:** invalid input shows a message instead of crashing, and connection problems explain what to do.

### Screens

Splash, Welcome, Create account, Login, Scripture, Home, Events, Give, Card payment, Groups, Prayer, Journal, Profile, Settings (Notifications, Appearance, Security), Help, and the Pastor screens (Manage members, View donations, Approve prayers).

## Design considerations

- **One database, two front ends.** The app does not keep its own user table. The website API is the single source of truth, so changes show up on both.
- **Offline first.** `DataCache` stores the last events, groups and prayers. `BuiltInData` supplies starter content on a first launch with no connection.
- **No duplicate records.** GET requests are retried up to 3 times because phones often drop the first request after waking up. POST requests (RSVP, donation, prayer) are sent **once**, so a donation can never be recorded twice.
- **Card safety.** The card number, CVV and name are checked on the phone (Luhn checksum, card type, CVV length, expiry) and then cleared. Only the card type and amount are sent to the server.
- **Clear error messages.** For example, "The church PC is offline. Start Apache and MySQL in XAMPP" instead of a raw network error.
- **Phone numbers** are converted to international format (for example `0821234567` becomes `+27821234567`) so SMS codes can be delivered.

## Tech stack

- Kotlin, Android SDK (minSdk 26, targetSdk 36), Gradle (Kotlin DSL)
- Plain `HttpURLConnection` + JSON for the REST API (`ApiClient`)
- SQLite on the phone (journal, settings, cached profile)
- Credential Manager / Google ID for Google sign-in (`GoogleSso`)
- PHP + MySQL website and API (separate repo)

## Project structure

```
app/src/main/java/com/example/shammah/
    MainActivity.kt     Screens, navigation, validation, card validator, local database
    ApiClient.kt        REST client, server address handling, offline cache (DataCache)
    AuthServices.kt     Phone number formatting and Google sign-in
    BuiltInData.kt      Starter events and ministries for offline use
app/src/main/res/layout/activity_main.xml   All screens
app/src/main/AndroidManifest.xml            Permissions (INTERNET, ACCESS_NETWORK_STATE)
app/src/test/                               Unit tests
azure-pipelines.yml                         CI pipeline definition
release/                                    Built APK (convenience copy)
SETUP_SHARED_DATABASE.md                    How the app and website share the database
START_SHAMMAH_ONLINE.bat                    Opens an ngrok tunnel so a phone can reach the PC
```

## How to build and run

1. Install **Android Studio** (latest stable) and open this folder, then let Gradle sync.
2. On the PC, start **Apache and MySQL** in XAMPP and make sure the church website is running (see `SETUP_SHARED_DATABASE.md`).
3. Tell the app where the server is. Edit `AppConfig.API_BASE_URL` in `MainActivity.kt`, or long-press **SIGN IN** in the app and type the address, then press **Save & test**:
   - Emulator on the same PC: `http://10.0.2.2/shammah-urban-church/api/`
   - Phone on the same Wi-Fi: `http://<PC IPv4 address>/shammah-urban-church/api/`
   - Phone on any network: run `START_SHAMMAH_ONLINE.bat` and use the ngrok address it shows
4. Connect a phone with USB debugging, or start an emulator, and press **Run**.

Command line:

```
./gradlew assembleDebug      # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest  # runs unit tests
```

### Install the APK directly

1. Copy the APK from `release/` (or from the pipeline artifact) to the phone.
2. Allow *Install unknown apps* for your file manager or browser, then open the APK.

## Azure DevOps

| Service | Use |
|---|---|
| Azure Boards | Epics, features, user stories and sprints with work assigned to each member |
| Azure Repos | `shammah-android-app` and `shammah-website`, with commits linked to work items |
| Azure Pipelines | Automatic build and unit tests on every push to `main`; the APK is published as a pipeline artifact |

To download the latest APK: **Pipelines -> latest run -> Artifacts**.

## Testing

- The build and unit tests run in Azure Pipelines on every push to `main`. The latest run reports 15 tests passed and 0 failed.
- **15 unit tests** in `app/src/test`: `CardValidatorTest` (10 tests: card types, Luhn checksum, CVV length, cardholder name and expiry) and `PhoneFormatTest` (5 tests: local to international numbers, spaces and brackets, invalid input).
- Run them locally with `./gradlew testDebugUnitTest`.
- The app was tested manually on a physical Android phone against the live shared database.

## Logging

The app writes log messages with Android's `Log` class so the flow of the code can be followed in Android Studio (**Logcat**, filter by `Shammah`).

| Tag | What it logs |
|---|---|
| `ShammahApi` | Each request (method, endpoint, attempt, HTTP status), retries and fallback server switches |
| `ShammahAuth` | Two-step code result, Google or Gmail-and-code sign-in path, session restore and sign-out |
| `ShammahValidation` | Why registration, login or a card payment was rejected |
| `ShammahGive`, `ShammahPrayer` | Donations submitted and prayer requests approved by a pastor |
| `ShammahRsvp`, `ShammahCache` | Offline RSVP queue and its sync |
| `ShammahServer`, `ShammahSession`, `ShammahApp` | Saved server address, stored session and session start |

Passwords, codes, tokens and card details are never written to the log.

## Release notes

### Version 1.0 - final submission

Changes since the prototype. Features marked **(innovative)** are the new ideas we added.

- Replaced the offline-only SQLite prototype with the **shared MySQL database** through the website's REST API. Accounts work on both website and app.
- **(innovative)** Two-step sign-in with a 6-digit code sent by the server by email and SMS.
- **(innovative)** Offline-first cache: lists open instantly, and **RSVPs made offline are queued and synced** when the server is reachable again.
- **(innovative)** Automatic reconnect: retries, fallback server addresses and an ngrok tunnel so a phone can reach the PC from any network.
- **(innovative)** Card validation with Luhn checksum, card type detection, CVV and expiry checks. Card numbers are never sent to the server.
- Daily scripture fetched live from a public REST API (bible-api.com).
- Pastor dashboard: manage members, view all donations, approve prayer requests.
- Friendly error messages and input validation on all forms.
- Phone numbers converted to international format for SMS.
- Added `BuiltInData.kt` starter content and removed the Firebase-based cloud code.
- Added logging across sign-in, validation, giving, prayers and the offline queue.
- Added 15 automated unit tests that run in the Azure pipeline, replacing the default placeholder test.

## Known limitations

- Real Google sign-in needs `AppConfig.GOOGLE_WEB_CLIENT_ID` to be set. While it is the placeholder, the app uses the "Gmail + code" sign-in.
- Card payments are validated but **simulated**; no payment gateway is connected.
- Biometric login is **not** implemented.
- The APK in `release/` is a **debug** build.
- The server address defaults to a home-network PC address and must be changed for other networks (see How to build and run).

## Related repositories

- GitHub mirror: https://github.com/OmphileMoale/shammah-android-app
- Azure Repos (app): https://dev.azure.com/ST10455873/XISD6329-Shammah-Urban-Church-CMES/_git/shammah-android-app
- Azure Repos (website): https://dev.azure.com/ST10455873/XISD6329-Shammah-Urban-Church-CMES/_git/shammah-website
-## Demo video

Watch the demo video here: [Shammah Android App Demo](https://drive.google.com/file/d/1oU9cDYekwwAL7JiSn-5AlPDCG9MWJLLy/view?usp=drivesdk)
