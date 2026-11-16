# One database for the Shammah app and website

The Android app and the website now use **the same MySQL database (`shammah_db`)**.
The app doesn't keep its own accounts any more. It talks to the website's `api/` folder,
which reads and writes the same tables as the website.

- An account made in the app works on the website, and an account made on the website works in the app.
- You can sign in with your **email address or your phone number**, plus your password.
- After the password, a **6-digit code is sent to your email and to your phone by SMS**.
  Typing it in finishes signing in (app and website).
- "Forgot password" sends a code to your email and phone. The new password works everywhere.
- 3 wrong passwords lock the account for 15 minutes, on both the app and the website.
- Events/RSVPs, donations, prayer requests (and pastor approval), groups (ministries) and the
  member list all come from the shared database. The private journal and app settings stay on the phone.

```
 Android app ──HTTP/JSON──►  website/api/*.php ──►  MySQL shammah_db  ◄── website pages
                                   │
                                   ├─► Gmail SMTP  ─► code in the member's email
                                   └─► BulkSMS     ─► code by SMS to the member's phone
```

---

## 1. Website (XAMPP)

1. Copy the `shammah-urban-church` folder to `C:\xampp\htdocs\shammah-urban-church\`.
2. Start **Apache** and **MySQL** in XAMPP.
3. phpMyAdmin → database `shammah_db` → **Import** `db_setup.sql` (if not done before),
   then **Import** `db_update_shared_login.sql`.
   (If you forget the second file, the site creates the same tables the first time someone signs in.)
4. `includes/db.php`: check `DB_PORT`. XAMPP's default is **3306**. The file currently says 3307.
5. Open `http://localhost/shammah-urban-church/api/`. You should see `"database":"connected"`.

## 2. Turn on real email and SMS codes

Open `includes/notify_config.php`:

| Setting | What to put |
|---|---|
| `SMTP_USER` | The church Gmail address, e.g. `shammahchurch@gmail.com` |
| `SMTP_PASS` | A Gmail **App password**: Google Account → Security → 2-Step Verification (turn on) → App passwords |
| `BULKSMS_TOKEN_ID` / `BULKSMS_SECRET` | From bulksms.com → Settings → API Tokens. Buy a few credits first; each SMS costs a few cents |
| `OTP_SHOW_CODE_IF_UNSENT` | `true` while testing. If neither email nor SMS could be sent, the code is shown on screen. Set to `false` for the real church |

Until these are filled in, the codes are written to `logs/otp_dev.log` and shown on screen (test mode).
That way you can demo the login without spending SMS credit.

> Keep real passwords out of Azure Repos: commit `notify_config.php` with blank values.

## 3. Android app

Open `MainActivity.kt` → `AppConfig.API_BASE_URL`:

| Where the app runs | Value |
|---|---|
| Android Emulator on the same PC as XAMPP | `http://10.0.2.2/shammah-urban-church/api/` (already set) |
| A real phone on the same Wi-Fi | `http://<PC IPv4>/shammah-urban-church/api/`. Run `ipconfig` on the PC, e.g. `http://192.168.1.50/...` |
| Hosted website | `https://your-domain/api/` |

**No rebuild needed for a real phone:** if the app can't reach the server, it asks for the
**Church server address**. Type your PC's IPv4 address (e.g. `192.168.1.50`) and tap **SAVE & TEST**.
It should say "Connected ✅ 12 members". You can open this box any time by **long-pressing the Sign In button**.

For a real phone: the phone and PC must be on the same Wi-Fi, and Apache must be allowed through
Windows Firewall (Windows asks the first time Apache starts; tick *Private networks*).
You can also open `http://<PC IP>/shammah-urban-church/api/` in the phone's browser. It should show `connected`.

Then **Sync Gradle** (Firebase was removed from `app/build.gradle.kts`) and run the app.
The phone's local SQLite database goes up to version 2, so old demo data on the phone is reset once.

## 4. Try it

| Test | Expected |
|---|---|
| App → Create Account | Code dialog → enter code → signed in. Then log in on the **website** with the same email or phone and password |
| Website → Register | Code box → enter code → member portal. Then sign in to the **app** with the same details |
| Sign in with phone `071 234 5678` / `Member@2025` | Works on both (Thabo, demo member) |
| `pastor@shammah.co.za` / `Admin@2025` in the app | Opens the Pastor dashboard: members, donations and prayers from the website database |
| App → Give | Donation shows in the website's member portal and admin dashboard |
| App → Prayer request | Shows as *pending* in the website's admin dashboard. Approve it there or in the app |

## Google sign-in (optional)

The "Sign in with Google" button works in two ways:
- **Set up:** put the same **Web client ID** in `AppConfig.GOOGLE_WEB_CLIENT_ID` (app) and `GOOGLE_WEB_CLIENT_ID`
  in `includes/notify_config.php` (website). The server checks the Google token with Google.
- **Not set up:** the person types the Gmail address on their account. A code is sent to that email and their phone.

Either way, the **role (member / pastor / admin) always comes from the database**. The app can no longer choose
"Pastor" on sign-up, because with one shared database that would also give website admin access.
The church admin changes roles in phpMyAdmin (`MEMBER.role`).

## API reference (website `api/` folder)

| Endpoint | Purpose |
|---|---|
| `POST register.php` `{name,email,phone,password}` | Create account → sends code |
| `POST login.php` `{login,password}` | Email or phone + password → sends code |
| `POST verify_code.php` `{ticket,code}` | → `{token,user}` |
| `POST resend_code.php` `{ticket}` | New code (60 s wait) |
| `POST forgot_password.php` `{login}` → `POST reset_password.php` `{ticket,code,new_password}` | Reset password |
| `POST code_login.php` `{login}` / `POST google_login.php` `{id_token}` | Gmail / Google sign-in |
| `GET/POST me.php` · `POST change_password.php` · `POST logout.php` | Profile and password |
| `GET events.php` · `POST rsvp.php` | Events + RSVP |
| `GET/POST donations.php` (`?all=1` for pastor/admin) | Giving |
| `GET/POST prayers.php` (`?status=pending` for pastor/admin) · `POST prayer_status.php` | Prayer wall |
| `GET ministries.php` · `GET members.php` (pastor/admin) | Groups, member directory |

Signed-in calls send `Authorization: Bearer <token>` (the app also sends `X-Auth-Token`).
Codes, tokens and passwords are stored only as hashes. Codes expire after 10 minutes and allow 5 tries.
