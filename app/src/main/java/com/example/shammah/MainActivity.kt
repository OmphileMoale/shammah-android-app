package com.example.shammah

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.util.Patterns
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.text.InputFilter
import android.text.InputType
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// =====================================================================================
//  SERVER SETTINGS  —  the app and the website share ONE MySQL database (shammah_db)
//  through the website's api/ folder. See SETUP_SHARED_DATABASE.md.
//
//  API_BASE_URL is where XAMPP is running:
//    • Android Emulator on the same PC:  http://10.0.2.2/shammah-urban-church/api/
//    • Real phone on the same Wi-Fi:     http://<your PC's IPv4>/shammah-urban-church/api/
//                                        (Windows: run "ipconfig", e.g. 192.168.1.50)
//    • Hosted website:                   https://your-domain.co.za/api/
// =====================================================================================
object AppConfig {
    // Your PC on the home Wi-Fi (from ipconfig). A phone on the same Wi-Fi reaches XAMPP here.
    const val API_BASE_URL = "http://192.168.68.101/shammah-urban-church/api/"
    // If the saved address doesn't answer, the app tries these in turn and keeps the one that works.
    val FALLBACK_URLS = listOf(
        "http://192.168.68.101/shammah-urban-church/api/",   // PC on the same Wi-Fi
        "http://10.0.2.2/shammah-urban-church/api/"          // Android emulator on the PC
    )
    // Google Cloud console > Credentials > OAuth "Web client ID" (also put it in the website's
    // includes/notify_config.php). Leave as is to use the "type your Gmail + code" sign-in instead.
    const val GOOGLE_WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"
    // Used to turn local numbers like 0821234567 into +27821234567
    const val DEFAULT_COUNTRY_CODE = "27"
}

// =====================================================================================
//  MAIN ACTIVITY
// =====================================================================================
class MainActivity : Activity() {

    private lateinit var splashScreen: View
    private lateinit var welcomeScreen: View
    private lateinit var createAccountScreen: View
    private lateinit var loginScreen: View
    private lateinit var scriptureScreen: View
    private lateinit var mainApp: View

    private lateinit var homeScreen: View
    private lateinit var eventsScreen: View
    private lateinit var giveScreen: View
    private lateinit var groupsScreen: View
    private lateinit var profileScreen: View
    private lateinit var prayerScreen: View
    private lateinit var journalScreen: View
    private lateinit var cardPaymentScreen: View
    private lateinit var pastorDashboard: View
    private lateinit var manageMembersScreen: View
    private lateinit var viewDonationsScreen: View
    private lateinit var approvePrayersScreen: View
    private lateinit var settingsScreen: View
    private lateinit var notificationsSettingScreen: View
    private lateinit var appearanceSettingScreen: View
    private lateinit var securitySettingScreen: View
    private lateinit var helpScreen: View

    private lateinit var db: DatabaseHelper      // on-phone SQLite: journal, settings, cached profile

    private lateinit var welcomeNameText: TextView
    private lateinit var profileInfoText: TextView
    private lateinit var eventStatusText: TextView
    private lateinit var donationHistoryText: TextView
    private lateinit var prayerListText: TextView
    private lateinit var journalListText: TextView
    private lateinit var scriptureText: TextView
    private lateinit var cardPaymentAmountLabel: TextView
    private lateinit var donationStatsText: TextView

    private var currentName = "Member"
    private var currentEmail = ""
    private var currentPhone = ""
    private var currentRole = "Member"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        db = DatabaseHelper(this)
        ServerConfig.load(this)      // server address saved in the app (real phone), else AppConfig
        setupSpinners()

        // Long-press the Sign In button to change the server address at any time
        findViewById<Button>(R.id.signInBtn).setOnLongClickListener {
            showServerDialog()
            true
        }

        splashScreen.postDelayed({
            tryRestoreSession()      // stays signed in between app launches
        }, 1500)

        findViewById<Button>(R.id.goCreateBtn).setOnClickListener { showOnly(createAccountScreen) }
        findViewById<Button>(R.id.goLoginBtn).setOnClickListener { showOnly(loginScreen) }
        findViewById<Button>(R.id.backCreateBtn).setOnClickListener { showOnly(welcomeScreen) }
        findViewById<Button>(R.id.backLoginBtn).setOnClickListener { showOnly(welcomeScreen) }

        findViewById<Button>(R.id.createAccountBtn).setOnClickListener { createAccount() }
        findViewById<Button>(R.id.signInBtn).setOnClickListener { login() }
        findViewById<TextView>(R.id.forgotPasswordBtn).setOnClickListener { forgotPassword() }
        findViewById<Button>(R.id.continueToAppBtn).setOnClickListener { openMainApp() }

        findViewById<TextView>(R.id.navHome).setOnClickListener {
            if (currentRole == "Pastor") showPage(pastorDashboard)
            else showPage(homeScreen)
        }
        findViewById<TextView>(R.id.navEvents).setOnClickListener {
            refreshEvents()
            showPage(eventsScreen)
        }
        findViewById<TextView>(R.id.navGive).setOnClickListener {
            refreshMyDonations()
            showPage(giveScreen)
        }
        findViewById<TextView>(R.id.navGroups).setOnClickListener {
            refreshGroups()
            showPage(groupsScreen)
        }
        findViewById<TextView>(R.id.navProfile).setOnClickListener { showPage(profileScreen) }

        findViewById<Button>(R.id.openPrayerBtn).setOnClickListener {
            refreshPrayerWall()
            showPage(prayerScreen)
        }
        findViewById<Button>(R.id.openJournalBtn).setOnClickListener {
            refreshJournal()
            showPage(journalScreen)
        }

        // Pastor Dashboard Buttons
        findViewById<Button>(R.id.manageMembersBtn).setOnClickListener {
            refreshMemberList()
            showPage(manageMembersScreen)
        }
        findViewById<Button>(R.id.viewDonationsBtn).setOnClickListener {
            refreshDonationList()
            showPage(viewDonationsScreen)
        }
        findViewById<Button>(R.id.approvePrayersBtn).setOnClickListener {
            refreshPendingPrayers()
            showPage(approvePrayersScreen)
        }

        findViewById<Button>(R.id.backFromMembersBtn).setOnClickListener { showPage(pastorDashboard) }
        findViewById<Button>(R.id.backFromDonationsBtn).setOnClickListener { showPage(pastorDashboard) }
        findViewById<Button>(R.id.backFromApproveBtn).setOnClickListener { showPage(pastorDashboard) }

        findViewById<Button>(R.id.liveStreamBtn).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/@ShammahUrbanChurch"))
            startActivity(intent)
        }

        findViewById<Button>(R.id.podcastBtn).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/show/shammahurbanchurch"))
            startActivity(intent)
            toast("Opening Sermon Podcast...")
        }

        findViewById<Button>(R.id.rsvpBtn).setOnClickListener { chooseEventToRsvp() }

        findViewById<Button>(R.id.joinGroupBtn).setOnClickListener {
            toast("Joined church group successfully")
        }

        findViewById<Button>(R.id.donateBtn).setOnClickListener {
            hideKeyboard()
            val amount = findViewById<EditText>(R.id.donationAmount).text.toString()
            val category = findViewById<Spinner>(R.id.donationCategorySpinner).selectedItem.toString()
            val value = amount.trim().toDoubleOrNull()
            if (amount.isBlank()) {
                toast("Enter donation amount")
            } else if (value == null || value <= 0.0 || value > 1000000.0) {
                toast("Enter a valid amount between R1 and R1,000,000")
            } else {
                // Saved in the website's DONATION table (shows in the website portal too)
                recordDonation(category, value, "Cash") { ok ->
                    if (ok) findViewById<EditText>(R.id.donationAmount).text.clear()
                }
            }
        }

        findViewById<Button>(R.id.copyBankBtn).setOnClickListener {
            val eftDetails = """
                Standard Bank
                Account Name: Shammah Urban Church
                Account Number: 123456789
                Branch Code: 051001
                Reference: $currentName Donation
            """.trimIndent()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("EFT Banking Details", eftDetails)
            clipboard.setPrimaryClip(clip)
            toast("EFT banking details copied")
        }

        findViewById<Button>(R.id.payOnlineBtn).setOnClickListener {
            val amount = findViewById<EditText>(R.id.donationAmount).text.toString()
            val category = findViewById<Spinner>(R.id.donationCategorySpinner).selectedItem.toString()
            val payValue = amount.trim().toDoubleOrNull()
            if (amount.isBlank()) {
                toast("Please enter an amount first")
            } else if (payValue == null || payValue <= 0.0 || payValue > 1000000.0) {
                toast("Enter a valid amount between R1 and R1,000,000")
            } else {
                cardPaymentAmountLabel.text = "Amount: R$amount  •  $category"
                showPage(cardPaymentScreen)
            }
        }

        findViewById<Button>(R.id.backFromCardBtn).setOnClickListener {
            showPage(giveScreen)
        }

        findViewById<Button>(R.id.confirmCardPayBtn).setOnClickListener {
            hideKeyboard()
            val name   = findViewById<EditText>(R.id.cardHolderName).text.toString().trim()
            val number = findViewById<EditText>(R.id.cardNumber).text.toString().trim()
            val cvv    = findViewById<EditText>(R.id.cardCvv).text.toString().trim()
            val month  = findViewById<Spinner>(R.id.cardExpiryMonth).selectedItemPosition + 1
            val year   = findViewById<Spinner>(R.id.cardExpiryYear).selectedItem.toString().toInt()
            val expired = isCardExpired(month, year)

            val validation = CardValidator.validateCard(number, name, cvv, expired)
            // Only the result is logged; the card number, CVV and name are never written to the log.
            Log.d("ShammahValidation", "Card check: valid=${validation.isValid}, type=${validation.cardType}")

            if (!validation.isValid) {
                toast(validation.errorMessage)
            } else {
                val amountLine = cardPaymentAmountLabel.text.toString()
                val cardAmount = Regex("R(\\d+(\\.\\d+)?)").find(amountLine)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val cardCategory = amountLine.substringAfter("•", "Offering").trim()

                recordDonation(cardCategory, cardAmount, "${validation.cardType} Card") { ok ->
                    if (ok) {
                        toast("✅ ${validation.cardType} payment of R${"%.2f".format(cardAmount)} confirmed!")
                        findViewById<EditText>(R.id.cardHolderName).text.clear()
                        findViewById<EditText>(R.id.cardNumber).text.clear()
                        findViewById<EditText>(R.id.cardCvv).text.clear()
                        findViewById<EditText>(R.id.donationAmount).text.clear()
                        showPage(giveScreen)
                    }
                }
            }
        }

        findViewById<Button>(R.id.submitPrayerBtn).setOnClickListener {
            hideKeyboard()
            val prayer = findViewById<EditText>(R.id.prayerInput).text.toString()
            if (prayer.isBlank()) {
                toast("Enter prayer request")
            } else {
                // Goes to the website's PRAYER_REQUEST table for a pastor to approve
                ApiClient.post("prayers.php", JSONObject().put("message", prayer.trim())) { r ->
                    toast(r.msg)
                    if (r.ok) findViewById<EditText>(R.id.prayerInput).text.clear()
                }
            }
        }

        findViewById<Button>(R.id.saveJournalBtn).setOnClickListener {
            hideKeyboard()
            val entry = findViewById<EditText>(R.id.journalInput).text.toString()
            if (entry.isBlank()) {
                toast("Write something first")
            } else {
                db.addJournal(currentEmail, entry.trim())
                journalListText.append("\n\nToday's Reflection:\n${entry.trim()}")
                findViewById<EditText>(R.id.journalInput).text.clear()
                toast("Journal saved")
            }
        }

        // ---- Single sign-on (Google)
        findViewById<Button>(R.id.googleSignInBtn).setOnClickListener { googleSignOn() }
        findViewById<Button>(R.id.googleSignUpBtn).setOnClickListener { googleSignOn() }

        // ---- Settings and Help Navigation
        findViewById<Button>(R.id.openSettingsBtn).setOnClickListener {
            showPage(settingsScreen)
        }
        findViewById<Button>(R.id.openHelpBtn).setOnClickListener {
            showPage(helpScreen)
        }

        // Settings Sub-Screen Category Buttons
        findViewById<Button>(R.id.openNotificationsSettingBtn).setOnClickListener {
            loadSettingsScreen()
            showPage(notificationsSettingScreen)
        }
        findViewById<Button>(R.id.openAppearanceSettingBtn).setOnClickListener {
            loadSettingsScreen()
            showPage(appearanceSettingScreen)
        }
        findViewById<Button>(R.id.openSecuritySettingBtn).setOnClickListener {
            loadSettingsScreen()
            showPage(securitySettingScreen)
        }
        findViewById<Button>(R.id.openHelpSupportSettingBtn).setOnClickListener {
            showPage(helpScreen)
        }

        // Back buttons
        findViewById<Button>(R.id.backFromSettingsBtn).setOnClickListener { showPage(profileScreen) }
        findViewById<Button>(R.id.backFromNotificationsBtn).setOnClickListener { showPage(settingsScreen) }
        findViewById<Button>(R.id.backFromAppearanceBtn).setOnClickListener { showPage(settingsScreen) }
        findViewById<Button>(R.id.backFromSecurityBtn).setOnClickListener { showPage(settingsScreen) }
        findViewById<Button>(R.id.backFromHelpBtn).setOnClickListener { showPage(settingsScreen) }

        // Settings action buttons
        findViewById<Button>(R.id.saveNotificationsBtn).setOnClickListener { saveNotificationsSettings() }
        findViewById<Button>(R.id.saveAppearanceBtn).setOnClickListener { saveAppearanceSettings() }
        findViewById<Button>(R.id.saveProfileBtn).setOnClickListener { saveProfile() }
        findViewById<Button>(R.id.changePasswordBtn).setOnClickListener { changePassword() }
        findViewById<Button>(R.id.contactSupportBtn).setOnClickListener {
            showChurchSupportDialog()
        }

        findViewById<Button>(R.id.logoutBtn).setOnClickListener { signOut() }
    }

    private fun bindViews() {
        splashScreen        = findViewById(R.id.splashScreen)
        welcomeScreen       = findViewById(R.id.welcomeScreen)
        createAccountScreen = findViewById(R.id.createAccountScreen)
        loginScreen         = findViewById(R.id.loginScreen)
        scriptureScreen     = findViewById(R.id.scriptureScreen)
        mainApp             = findViewById(R.id.mainApp)

        homeScreen          = findViewById(R.id.homeScreen)
        eventsScreen        = findViewById(R.id.eventsScreen)
        giveScreen          = findViewById(R.id.giveScreen)
        groupsScreen        = findViewById(R.id.groupsScreen)
        profileScreen       = findViewById(R.id.profileScreen)
        prayerScreen        = findViewById(R.id.prayerScreen)
        journalScreen       = findViewById(R.id.journalScreen)
        cardPaymentScreen   = findViewById(R.id.cardPaymentScreen)
        pastorDashboard     = findViewById(R.id.pastorDashboard)
        manageMembersScreen = findViewById(R.id.manageMembersScreen)
        viewDonationsScreen = findViewById(R.id.viewDonationsScreen)
        approvePrayersScreen = findViewById(R.id.approvePrayersScreen)
        settingsScreen             = findViewById(R.id.settingsScreen)
        notificationsSettingScreen = findViewById(R.id.notificationsSettingScreen)
        appearanceSettingScreen    = findViewById(R.id.appearanceSettingScreen)
        securitySettingScreen      = findViewById(R.id.securitySettingScreen)
        helpScreen                 = findViewById(R.id.helpScreen)

        welcomeNameText        = findViewById(R.id.welcomeNameText)
        profileInfoText        = findViewById(R.id.profileInfoText)
        eventStatusText        = findViewById(R.id.eventStatusText)
        donationHistoryText    = findViewById(R.id.donationHistoryText)
        prayerListText         = findViewById(R.id.prayerListText)
        journalListText        = findViewById(R.id.journalListText)
        scriptureText          = findViewById(R.id.scriptureText)
        cardPaymentAmountLabel = findViewById(R.id.cardPaymentAmountLabel)
        donationStatsText      = findViewById(R.id.donationStatsText)
    }

    private fun darkSpinnerAdapter(items: Array<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = TextView(context)
                tv.text = getItem(position)
                tv.setTextColor(Color.BLACK)
                tv.textSize = 15f
                tv.setPadding(16, 16, 16, 16)
                return tv
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = TextView(context)
                tv.text = getItem(position)
                tv.setTextColor(Color.WHITE)
                tv.setBackgroundColor(Color.parseColor("#2A1427"))
                tv.textSize = 15f
                tv.setPadding(24, 24, 24, 24)
                return tv
            }
        }
    }

    private fun setupSpinners() {
        val roles      = arrayOf("Member", "Volunteer", "Staff", "Pastor")
        val categories = arrayOf("Tithe", "Offering", "Building Fund", "Special Seed")
        val months     = arrayOf(
            "01 - Jan", "02 - Feb", "03 - Mar", "04 - Apr",
            "05 - May", "06 - Jun", "07 - Jul", "08 - Aug",
            "09 - Sep", "10 - Oct", "11 - Nov", "12 - Dec"
        )
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val years = Array(10) { i -> (currentYear + i).toString() }

        // Roles (Member / Pastor / Admin) are set by the church admin on the website,
        // so new accounts can't pick one here.
        findViewById<Spinner>(R.id.roleSpinner).adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)
        findViewById<Spinner>(R.id.roleSpinner).visibility = View.GONE

        findViewById<Spinner>(R.id.donationCategorySpinner).adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)

        findViewById<Spinner>(R.id.cardExpiryMonth).adapter = darkSpinnerAdapter(months)
        findViewById<Spinner>(R.id.cardExpiryYear).adapter = darkSpinnerAdapter(years)
    }

    // =================================================================================
    //  ACCOUNT CREATION / SIGN-IN  (shared with the website through api/*.php)
    //  Sign in with your EMAIL or PHONE NUMBER + password. The church server then sends
    //  a 6-digit code to your email AND your phone; typing it in finishes signing in.
    //  The same account, password and lockout rules apply on the website.
    // =================================================================================
    private fun setAuthBusy(busy: Boolean) {
        listOf(R.id.createAccountBtn, R.id.signInBtn, R.id.googleSignInBtn, R.id.googleSignUpBtn, R.id.forgotPasswordBtn)
            .forEach { findViewById<View>(it).isEnabled = !busy }
    }

    private fun createAccount() {
        hideKeyboard()
        val name     = findViewById<EditText>(R.id.createNameInput).text.toString().trim()
        val email    = findViewById<EditText>(R.id.createEmailInput).text.toString().trim().lowercase()
        val phone    = findViewById<EditText>(R.id.createPhoneInput).text.toString().trim()
        val password = findViewById<EditText>(R.id.createPasswordInput).text.toString().trim()

        if (name.isBlank() || email.isBlank() || phone.isBlank() || password.isBlank()) {
            Log.w("ShammahValidation", "Registration rejected: a required field is empty")
            toast("Complete all fields")
            return
        }
        if (name.length < 2 || name.any { it.isDigit() }) {
            Log.w("ShammahValidation", "Registration rejected: invalid name")
            toast("Enter a valid name")
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Log.w("ShammahValidation", "Registration rejected: invalid email format")
            toast("Invalid email address")
            return
        }
        val phoneE164 = PhoneFormat.toE164(phone)
        if (phoneE164 == null) {
            Log.w("ShammahValidation", "Registration rejected: phone number could not be converted to +27 format")
            toast("Enter a valid phone number, e.g. 0821234567 or +27821234567")
            return
        }
        if (password.length < 8 || password.none { it.isLetter() } || password.none { it.isDigit() }) {
            Log.w("ShammahValidation", "Registration rejected: password does not meet the rules")
            toast("Password must be 8+ characters with a letter and a number")
            return
        }

        setAuthBusy(true)
        toast("Creating your account...")
        val body = JSONObject().put("name", name).put("email", email).put("phone", phoneE164).put("password", password)
        ApiClient.post("register.php", body) { r ->
            setAuthBusy(false)
            if (!r.ok) {
                failed(r)
                return@post
            }
            showCodeDialog(r.json, "Verify your account") { token, user ->
                findViewById<EditText>(R.id.createPasswordInput).text.clear()
                toast("Account created ✅ Use it on the website too.")
                signedIn(token, user)
            }
        }
    }

    private fun login() {
        hideKeyboard()
        val login    = findViewById<EditText>(R.id.emailInput).text.toString().trim().lowercase()
        val password = findViewById<EditText>(R.id.passwordInput).text.toString().trim()

        if (!Patterns.EMAIL_ADDRESS.matcher(login).matches() && PhoneFormat.toE164(login) == null) {
            Log.w("ShammahValidation", "Login rejected: not a valid email or phone number")
            toast("Enter your email address or phone number")
            return
        }
        if (password.isBlank()) {
            Log.w("ShammahValidation", "Login rejected: password is empty")
            toast("Enter your password")
            return
        }

        setAuthBusy(true)
        ApiClient.post("login.php", JSONObject().put("login", login).put("password", password)) { r ->
            setAuthBusy(false)
            if (!r.ok) {
                failed(r)
                return@post
            }
            showCodeDialog(r.json, "Enter your code") { token, user ->
                findViewById<EditText>(R.id.passwordInput).text.clear()
                signedIn(token, user)
            }
        }
    }

    private fun forgotPassword() {
        hideKeyboard()
        val login = findViewById<EditText>(R.id.emailInput).text.toString().trim().lowercase()
        if (!Patterns.EMAIL_ADDRESS.matcher(login).matches() && PhoneFormat.toE164(login) == null) {
            toast("Type your email address or phone number above first")
            return
        }
        setAuthBusy(true)
        ApiClient.post("forgot_password.php", JSONObject().put("login", login)) { r ->
            setAuthBusy(false)
            if (!r.ok) failed(r) else showResetDialog(r.json)
        }
    }

    private fun styledInput(hintText: String, type: Int): EditText = EditText(this).apply {
        hint = hintText
        inputType = type
        setTextColor(Color.WHITE)
        setHintTextColor(Color.parseColor("#C9B8C3"))
        setBackgroundColor(Color.parseColor("#2A1427"))
        setPadding(30, 30, 30, 30)
    }

    private fun dialogBox(vararg children: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(50, 30, 50, 10)
        children.forEachIndexed { i, child ->
            addView(child, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { if (i > 0) topMargin = 20 })
        }
    }

    private fun codeMessage(reply: JSONObject): String {
        val msg = reply.optString("msg").ifBlank { "We sent a 6-digit code to your email and phone." }
        return if (reply.has("dev_code")) "$msg\n\n(Test mode: email/SMS are not set up on the server yet.)" else msg
    }

    /** Asks for the 6-digit code sent to email + phone, then returns the sign-in token. */
    private fun showCodeDialog(reply: JSONObject, title: String, onSignedIn: (String, JSONObject) -> Unit) {
        val ticket = reply.optString("ticket")
        val codeInput = styledInput("6-digit code", InputType.TYPE_CLASS_NUMBER).apply {
            filters = arrayOf(InputFilter.LengthFilter(6))
        }
        val d = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(codeMessage(reply))
            .setView(dialogBox(codeInput))
            .setPositiveButton("VERIFY", null)
            .setNeutralButton("RESEND", null)
            .setNegativeButton("CANCEL", null)
            .setCancelable(false)
            .create()
        d.setOnShowListener {
            val verify = d.getButton(DialogInterface.BUTTON_POSITIVE)
            verify.setOnClickListener {
                val code = codeInput.text.toString().trim()
                if (code.length != 6) {
                    toast("Enter the 6-digit code from your email or SMS")
                    return@setOnClickListener
                }
                verify.isEnabled = false
                ApiClient.post("verify_code.php", JSONObject().put("ticket", ticket).put("code", code)
                    .put("device", "Android ${android.os.Build.MODEL}")) { r ->
                    verify.isEnabled = true
                    if (r.ok) {
                        Log.i("ShammahAuth", "Two-step code accepted by the server")
                        d.dismiss()
                        onSignedIn(r.json.optString("token"), r.json.optJSONObject("user") ?: JSONObject())
                    } else {
                        Log.w("ShammahAuth", "Two-step code rejected (HTTP ${r.status})")
                        toast(r.msg)
                        codeInput.selectAll()
                    }
                }
            }
            d.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                ApiClient.post("resend_code.php", JSONObject().put("ticket", ticket)) { r ->
                    if (r.ok) d.setMessage(codeMessage(r.json))
                    toast(if (r.ok) "New code sent" else r.msg)
                }
            }
            d.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener { d.dismiss() }
        }
        d.show()
    }

    /** Code + new password. The new password works on the website as well. */
    private fun showResetDialog(reply: JSONObject) {
        val ticket = reply.optString("ticket")
        val codeInput = styledInput("6-digit code", InputType.TYPE_CLASS_NUMBER).apply {
            filters = arrayOf(InputFilter.LengthFilter(6))
        }
        val passInput = styledInput("New password (8+ chars, letter + number)",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val d = AlertDialog.Builder(this)
            .setTitle("Reset your password")
            .setMessage(codeMessage(reply))
            .setView(dialogBox(codeInput, passInput))
            .setPositiveButton("RESET", null)
            .setNeutralButton("RESEND", null)
            .setNegativeButton("CANCEL", null)
            .setCancelable(false)
            .create()
        d.setOnShowListener {
            val ok = d.getButton(DialogInterface.BUTTON_POSITIVE)
            ok.setOnClickListener {
                val code = codeInput.text.toString().trim()
                val pass = passInput.text.toString()
                when {
                    code.length != 6 -> toast("Enter the 6-digit code")
                    pass.length < 8 || pass.none { it.isLetter() } || pass.none { it.isDigit() } ->
                        toast("Password must be 8+ characters with a letter and a number")
                    else -> {
                        ok.isEnabled = false
                        ApiClient.post("reset_password.php", JSONObject().put("ticket", ticket).put("code", code)
                            .put("new_password", pass).put("device", "Android ${android.os.Build.MODEL}")) { r ->
                            ok.isEnabled = true
                            if (r.ok) {
                                d.dismiss()
                                toast("Password changed ✅")
                                signedIn(r.json.optString("token"), r.json.optJSONObject("user") ?: JSONObject())
                            } else {
                                toast(r.msg)
                            }
                        }
                    }
                }
            }
            d.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                ApiClient.post("resend_code.php", JSONObject().put("ticket", ticket)) { r ->
                    if (r.ok) d.setMessage(codeMessage(r.json))
                    toast(if (r.ok) "New code sent" else r.msg)
                }
            }
            d.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener { d.dismiss() }
        }
        d.show()
    }

    private fun askForPhone(prefill: String, onPhone: (String) -> Unit, onCancel: () -> Unit) {
        val input = styledInput("Phone number, e.g. 0821234567", InputType.TYPE_CLASS_PHONE)
        if (prefill.isNotBlank()) input.setText(prefill)
        val dialog = AlertDialog.Builder(this)
            .setTitle("Add your phone number")
            .setMessage("Your sign-in codes will also be sent to this number by SMS.")
            .setView(dialogBox(input))
            .setPositiveButton("SAVE", null)
            .setNegativeButton("LATER") { _, _ -> onCancel() }
            .setCancelable(false)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val e164 = PhoneFormat.toE164(input.text.toString())
                if (e164 == null) {
                    toast("Enter a valid phone number")
                } else {
                    dialog.dismiss()
                    onPhone(e164)
                }
            }
        }
        dialog.show()
    }

    /** Saves the token so the app stays signed in, then opens the app. */
    private fun signedIn(token: String, userJson: JSONObject) {
        ApiClient.token = token
        SessionStore.save(this, token, userJson)
        val user = userFromJson(userJson)
        Log.i("ShammahAuth", "Signed in successfully (role: ${user.role})")
        if (PhoneFormat.toE164(user.phone) == null) {
            askForPhone("", onPhone = { phone ->
                ApiClient.post("me.php", JSONObject().put("name", user.name).put("phone", phone)) { r ->
                    toast(if (r.ok) "Phone number saved" else r.msg)
                    startSession(r.json.optJSONObject("user")?.let { userFromJson(it) } ?: user)
                }
            }, onCancel = { startSession(user) })
        } else {
            startSession(user)
        }
    }

    /** On app start: if a token was saved, check it with the server and skip the login screen. */
    private fun tryRestoreSession() {
        val saved = SessionStore.token(this)
        if (saved.isBlank()) {
            showOnly(welcomeScreen)
            return
        }
        ApiClient.token = saved
        Log.i("ShammahAuth", "Saved session found; checking it with the server")
        ApiClient.get("me.php") { r ->
            if (r.ok) {
                startSession(userFromJson(r.json.optJSONObject("user") ?: JSONObject()))
            } else {
                if (r.status == 401) {
                    Log.w("ShammahAuth", "Saved session has expired; the member must sign in again")
                    SessionStore.clear(this)
                    ApiClient.token = ""
                }
                showOnly(welcomeScreen)
                if (r.status != 401) failed(r)
            }
        }
    }

    /** Shows the server's message, or asks for the server address if it couldn't be reached. */
    private fun failed(r: ApiClient.Result) {
        // No pop-up "can't reach server" dialog. A short note only; long-press SIGN IN sets the address.
        toast(if (r.status == 0) "No connection to the church right now. Please check your internet and try again." else r.msg)
    }

    /** Lets a real phone point at the PC running XAMPP, e.g. 192.168.1.50 (run ipconfig on the PC). */
    private fun showServerDialog(problem: String = "") {
        val input = styledInput("e.g. shammah-xyz.ngrok-free.app", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        input.setText(ApiClient.baseUrl)
        AlertDialog.Builder(this)
            .setTitle("Church server address")
            .setMessage((if (problem.isNotBlank()) "$problem\n\n" else "") +
                "Phone anywhere (tunnel): your ngrok address, e.g. shammah-xyz.ngrok-free.app " +
                "(START_SHAMMAH_ONLINE.bat must be running on the PC).\n" +
                "Same Wi-Fi: your PC's IPv4 address (run ipconfig), e.g. 192.168.1.50.\n" +
                "Emulator: 10.0.2.2\nXAMPP Apache and MySQL must be running.")
            .setView(dialogBox(input))
            .setPositiveButton("SAVE & TEST") { _, _ ->
                val url = ServerConfig.save(this, input.text.toString())
                ApiClient.get("index.php") { r ->
                    if (r.ok) toast("Connected ✅ ${r.json.optInt("members")} members in the church database")
                    else failed(r)
                }
                toast("Testing $url ...")
            }
            .setNeutralButton("USE EMULATOR") { _, _ ->
                ServerConfig.save(this, "10.0.2.2")
                toast("Server set to the emulator address")
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun refreshMemberList() {
        val view = findViewById<TextView>(R.id.memberListText)
        showCachedThenFresh("members.php", "members", view) { json ->
            val sb = StringBuilder()
            val list = json.optJSONArray("members")
            for (i in 0 until (list?.length() ?: 0)) {
                val m = list!!.getJSONObject(i)
                val status = if (m.optBoolean("active", true)) "" else " [inactive]"
                sb.append("${m.optString("name")} (${m.optString("role").replaceFirstChar { it.uppercase() }})$status\n")
                sb.append("   ${m.optString("email")} • ${m.optString("phone")}\n")
            }
            view.text = if (sb.isEmpty()) "No members to show yet." else sb.toString()
        }
    }

    private fun refreshDonationList() {
        val view = findViewById<TextView>(R.id.allDonationsText)
        showCachedThenFresh("donations.php?all=1", "donations_all", view) { json ->
            val sb = StringBuilder("--- ALL DONATIONS ---\n")
            val list = json.optJSONArray("donations")
            for (i in 0 until (list?.length() ?: 0)) {
                val d = list!!.getJSONObject(i)
                sb.append("\n${d.optString("date")}  ${d.optString("donor")} - ${d.optString("category")} : " +
                    "R${"%.2f".format(d.optDouble("amount"))} [${d.optString("method")}]")
            }
            if ((list?.length() ?: 0) == 0) sb.append("\nNo donations recorded yet.")
            view.text = sb.toString()
            donationStatsText.text =
                "Last 7 days: R${"%.2f".format(json.optDouble("last_7_days", 0.0))}\n" +
                "All time: R${"%.2f".format(json.optDouble("total", 0.0))}"
        }
    }

    private fun refreshMyDonations() {
        val sb = StringBuilder("⛪ MY CHURCH CONTRIBUTION HISTORY\n")
        val localDonations = db.getDonations()
        var totalAmount = 0.0

        localDonations.forEach {
            totalAmount += it.amount
            val formatted = "R${"%.2f".format(it.amount)}"
            sb.append("\n• ${it.category} : $formatted [${it.method}]")
        }

        if (localDonations.isEmpty()) {
            sb.append("\nNo contributions recorded yet. Your giving supports church ministry and community outreach.")
        } else {
            sb.append("\n\n------------------------------\nTOTAL CONTRIBUTIONS: R${"%.2f".format(totalAmount)}")
        }

        donationHistoryText.text = sb.toString()

        ApiClient.get("donations.php") { r ->
            if (!r.ok) return@get
            val list = r.json.optJSONArray("donations")
            if (list != null && list.length() > 0) {
                val apiSb = StringBuilder("⛪ MY CHURCH CONTRIBUTION HISTORY\n")
                var apiTotal = 0.0
                for (i in 0 until list.length()) {
                    val d = list.getJSONObject(i)
                    val amt = d.optDouble("amount")
                    apiTotal += amt
                    apiSb.append("\n• ${d.optString("date")}  ${d.optString("category")} : " +
                        "R${"%.2f".format(amt)} [${d.optString("method")}]")
                }
                apiSb.append("\n\n------------------------------\nTOTAL CONTRIBUTIONS: R${"%.2f".format(apiTotal)}")
                donationHistoryText.text = apiSb.toString()
            }
        }
    }

    private fun recordDonation(category: String, amount: Double, method: String, onDone: (Boolean) -> Unit) {
        Log.i("ShammahGive", "Donation submitted: $category, R$amount via $method")
        ApiClient.post("donations.php",
            JSONObject().put("amount", amount).put("category", category).put("method", method)) { r ->
            toast(r.msg)
            if (r.ok) refreshMyDonations()
            onDone(r.ok)
        }
    }

    private fun refreshPendingPrayers() {
        val container = findViewById<LinearLayout>(R.id.pendingPrayersContainer)
        container.removeAllViews()
        ApiClient.get("prayers.php?status=pending") { r ->
            container.removeAllViews()
            val list = r.json.optJSONArray("prayers")
            if (!r.ok || list == null || list.length() == 0) {
                val tv = TextView(this)
                tv.text = if (r.ok || r.status == 0) "No pending requests" else r.msg
                tv.setTextColor(Color.WHITE)
                container.addView(tv)
                return@get
            }

            for (i in 0 until list.length()) {
                val req = list.getJSONObject(i)
                val layout = LinearLayout(this)
                layout.orientation = LinearLayout.VERTICAL
                layout.setPadding(0, 0, 0, 20)

                val tv = TextView(this)
                tv.text = "${req.optString("name")}: ${req.optString("text")}"
                tv.setTextColor(Color.WHITE)
                tv.textSize = 16f
                layout.addView(tv)

                val btn = Button(this)
                btn.text = "APPROVE"
                btn.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#C9A84C"))
                btn.setTextColor(Color.parseColor("#3D0C2F"))
                btn.setOnClickListener { setPrayerStatus(req.optLong("id"), "approved") }
                layout.addView(btn)

                val rejectBtn = Button(this)
                rejectBtn.text = "REJECT"
                rejectBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.RED)
                rejectBtn.setTextColor(Color.WHITE)
                rejectBtn.setOnClickListener { setPrayerStatus(req.optLong("id"), "rejected") }
                layout.addView(rejectBtn)

                container.addView(layout)
            }
        }
    }

    private fun setPrayerStatus(id: Long, status: String) {
        Log.i("ShammahPrayer", "Pastor set prayer request $id to $status")
        ApiClient.post("prayer_status.php", JSONObject().put("id", id).put("status", status)) { r ->
            toast(if (r.ok && status == "approved") "Prayer approved and posted" else r.msg)
            refreshPendingPrayers()
        }
    }

    private fun loadRandomScripture() {
        val scriptures = listOf(
            "Jeremiah 29:11\n\nFor I know the plans I have for you.",
            "Philippians 4:13\n\nI can do all things through Christ.",
            "Psalm 23:1\n\nThe Lord is my shepherd.",
            "Isaiah 41:10\n\nFear not, for I am with you.",
            "Romans 8:28\n\nAll things work together for good.",
            "Proverbs 3:5\n\nTrust in the Lord with all your heart.",
            "John 3:16\n\nFor God so loved the world.",
            "Joshua 1:9\n\nBe strong and courageous.",
            "Matthew 19:26\n\nWith God all things are possible.",
            "Psalm 46:5\n\nGod is within her, she will not fall."
        )
        scriptureText.text = scriptures.random()

        Executors.newSingleThreadExecutor().execute {
            try {
                val references = listOf("jeremiah+29:11", "philippians+4:13", "psalm+23:1", "isaiah+41:10", "romans+8:28", "proverbs+3:5", "john+3:16", "joshua+1:9", "matthew+19:26")
                val ref = references.random()
                val conn = URL("https://bible-api.com/$ref").openConnection() as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                if (conn.responseCode in 200..299) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(text)
                    val reference = json.optString("reference")
                    val passage = json.optString("text").trim()
                    if (reference.isNotBlank() && passage.isNotBlank()) {
                        Handler(Looper.getMainLooper()).post {
                            scriptureText.text = "$reference (Live Bible REST API)\n\n$passage"
                        }
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private fun openMainApp() {
        hideKeyboard()
        welcomeNameText.text =
            "Shammah Urban Church\nPretoria East\n\nWelcome back, $currentName\nRole: $currentRole"
        profileInfoText.text =
            "$currentName\n$currentRole\n\nEMAIL: $currentEmail\nPHONE: $currentPhone\nGROUP: Pretoria East Cell Group\nLEADER: Kay Smith\nAREA: Pretoria East"

        findViewById<TextView>(R.id.pastorWelcomeText).text =
            "Welcome, Pastor $currentName\nManage your congregation and services here."

        val navHome = findViewById<TextView>(R.id.navHome)
        val navEvents = findViewById<TextView>(R.id.navEvents)
        val navGive = findViewById<TextView>(R.id.navGive)
        val navGroups = findViewById<TextView>(R.id.navGroups)
        val navProfile = findViewById<TextView>(R.id.navProfile)
        val navBar = navHome.parent as LinearLayout

        navHome.visibility = View.VISIBLE
        navEvents.visibility = View.VISIBLE
        navGive.visibility = View.VISIBLE
        navGroups.visibility = View.VISIBLE
        navProfile.visibility = View.VISIBLE
        navBar.weightSum = 5f

        applyTextSize(db.getSettings(currentEmail).textSize)
        showOnly(mainApp)

        when (currentRole) {
            "Pastor" -> {
                showPage(pastorDashboard)
            }
            "Volunteer" -> {
                navHome.visibility = View.GONE
                navGroups.visibility = View.GONE
                navBar.weightSum = 3f
                showPage(eventsScreen)
            }
            else -> {
                showPage(homeScreen)
            }
        }
    }

    private fun showOnly(screen: View) {
        hideKeyboard()
        splashScreen.visibility        = View.GONE
        welcomeScreen.visibility       = View.GONE
        createAccountScreen.visibility = View.GONE
        loginScreen.visibility         = View.GONE
        scriptureScreen.visibility     = View.GONE
        mainApp.visibility             = View.GONE
        screen.visibility              = View.VISIBLE
    }

    private fun showPage(page: View) {
        hideKeyboard()
        homeScreen.visibility                  = View.GONE
        eventsScreen.visibility                = View.GONE
        giveScreen.visibility                  = View.GONE
        groupsScreen.visibility                = View.GONE
        profileScreen.visibility               = View.GONE
        prayerScreen.visibility                = View.GONE
        journalScreen.visibility               = View.GONE
        cardPaymentScreen.visibility           = View.GONE
        pastorDashboard.visibility             = View.GONE
        manageMembersScreen.visibility         = View.GONE
        viewDonationsScreen.visibility         = View.GONE
        approvePrayersScreen.visibility        = View.GONE
        settingsScreen.visibility              = View.GONE
        notificationsSettingScreen.visibility = View.GONE
        appearanceSettingScreen.visibility    = View.GONE
        securitySettingScreen.visibility      = View.GONE
        helpScreen.visibility                  = View.GONE
        page.visibility                        = View.VISIBLE
    }

    private fun hideKeyboard() {
        val inputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val focusedView = currentFocus
        if (focusedView != null) {
            inputMethodManager.hideSoftInputFromWindow(focusedView.windowToken, 0)
            focusedView.clearFocus()
        }
    }

    private fun startSession(user: User) {
        currentName  = user.name
        currentEmail = user.email
        currentPhone = user.phone
        currentRole  = user.role
        Log.i("ShammahApp", "Session started (role: $currentRole)")
        db.cacheUser(user)          // local copy so journal + settings (SQLite) can link to it
        prefetchLists()             // fill the offline copies in the background

        if (db.getSettings(user.email).showScripture) {
            loadRandomScripture()
            showOnly(scriptureScreen)
        } else {
            openMainApp()
        }
    }

    private fun googleSignOn() {
        hideKeyboard()
        setAuthBusy(true)
        GoogleSso.signIn(
            this,
            onSuccess = { account ->
                // Real Google sign-in: the server checks the Google token.
                // Typed Gmail address: the server sends a code to that email + phone.
                Log.i("ShammahAuth", if (account.idToken.isNotBlank()) "Google sign-in: using a Google ID token" else "Google sign-in: no ID token, using the Gmail + code fallback")
                val (path, body) = if (account.idToken.isNotBlank())
                    "google_login.php" to JSONObject().put("id_token", account.idToken)
                else
                    "code_login.php" to JSONObject().put("login", account.email)
                ApiClient.post(path, body) { r ->
                    setAuthBusy(false)
                    if (!r.ok) {
                        GoogleSso.signOut(this)
                        failed(r)
                        return@post
                    }
                    showCodeDialog(r.json, "Enter your code") { token, user -> signedIn(token, user) }
                }
            },
            onFail = { message ->
                Log.w("ShammahAuth", "Google sign-in failed: $message")
                setAuthBusy(false)
                toast(message)
            }
        )
    }

    private fun signOut() {
        Log.i("ShammahAuth", "Member tapped sign out")
        ApiClient.post("logout.php", JSONObject()) { }
        ApiClient.token = ""
        SessionStore.clear(this)
        GoogleSso.signOut(this)
        showOnly(welcomeScreen)
        toast("Signed out")
    }

    private fun isCardExpired(month: Int, year: Int): Boolean {
        val cal = java.util.Calendar.getInstance()
        val nowYear = cal.get(java.util.Calendar.YEAR)
        val nowMonth = cal.get(java.util.Calendar.MONTH) + 1
        return year < nowYear || (year == nowYear && month < nowMonth)
    }

    /** Same EVENT table as the website's Events page. */
    private var loadedEvents: List<JSONObject> = emptyList()

    private fun refreshEvents() {
        val view = findViewById<TextView>(R.id.eventsDbText)
        showCachedThenFresh("events.php", "events", view, onFresh = { sendPendingRsvps() }) { json ->
            renderEvents(json)
        }
    }

    private fun renderEvents(json: JSONObject) {
        val view = findViewById<TextView>(R.id.eventsDbText)
        val pending = DataCache.pendingRsvps()
        val list = json.optJSONArray("events")
        loadedEvents = (0 until (list?.length() ?: 0)).map { list!!.getJSONObject(it) }
        val sb = StringBuilder("ALL CHURCH EVENTS\n")
        loadedEvents.forEach {
            val going = when {
                it.optBoolean("my_rsvp") -> "  ✅ You're attending"
                it.optString("title") in pending -> "  ✅ You're attending (will sync)"
                else -> ""
            }
            sb.append("\n${it.optString("title")}\n${it.optString("date")} ${it.optString("time")} • " +
                "${it.optString("venue")}\n${it.optInt("going")} of ${it.optInt("capacity")} attending$going\n")
        }
        if (loadedEvents.isEmpty()) sb.append("\nNo upcoming events right now — check back soon.")
        view.text = sb.toString()
        val mine = loadedEvents.filter { it.optBoolean("my_rsvp") || it.optString("title") in pending }
            .map { it.optString("title") }
        eventStatusText.text = if (mine.isEmpty()) "RSVP Status: No RSVPs yet."
                               else "RSVP Status: You are attending ${mine.joinToString(", ")}."
    }

    /** RSVPs made while the phone was offline are sent as soon as the server answers again. */
    private fun sendPendingRsvps() {
        val pending = DataCache.pendingRsvps()
        if (pending.isEmpty()) return
        Log.i("ShammahRsvp", "Syncing ${pending.size} queued RSVP(s) with the server")
        pending.toList().forEach { title ->
            val ev = loadedEvents.firstOrNull { it.optString("title") == title && it.optInt("id") > 0 }
            if (ev == null || ev.optBoolean("my_rsvp")) {
                pending.remove(title); DataCache.savePendingRsvps(pending)
                return@forEach
            }
            ApiClient.post("rsvp.php", JSONObject().put("event_id", ev.optInt("id")).put("cancel", false)) { r ->
                if (r.status != 0) {
                    Log.i("ShammahRsvp", "Queued RSVP for $title reached the server (ok=${r.ok})")
                    val left = DataCache.pendingRsvps(); left.remove(title); DataCache.savePendingRsvps(left)
                    if (r.ok) refreshEvents()
                }
            }
        }
    }

    private fun chooseEventToRsvp() {
        if (loadedEvents.isEmpty()) renderEvents(DataCache.get("events") ?: BuiltInData.events())
        if (loadedEvents.isEmpty()) {
            toast("There are no events to RSVP to right now.")
            return
        }
        val pending = DataCache.pendingRsvps()
        val titles = loadedEvents.map {
            val mine = it.optBoolean("my_rsvp") || it.optString("title") in pending
            (if (mine) "✅ " else "") + "${it.optString("title")} – ${it.optString("date")}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("RSVP to an event")
            .setItems(titles) { _, which ->
                val ev = loadedEvents[which]
                val title = ev.optString("title")
                val date = ev.optString("date")
                val queued = DataCache.pendingRsvps()

                if (title in queued) {
                    Log.i("ShammahRsvp", "Queued RSVP cancelled: $title")
                    queued.remove(title)
                    DataCache.savePendingRsvps(queued)
                    toast("RSVP cancelled for $title")
                    renderEvents(DataCache.get("events") ?: BuiltInData.events())
                    return@setItems
                }

                Log.i("ShammahRsvp", "RSVP queued and sent: $title")
                queued.add(title)
                DataCache.savePendingRsvps(queued)

                val isSundayEvent = date.contains("Sunday", ignoreCase = true) || title.contains("Sunday", ignoreCase = true)

                if (isSundayEvent) {
                    val message = "RSVP Confirmed for $title on $date! ✉️ Confirmation email sent to $currentEmail."
                    toast(message)
                    eventStatusText.text = "RSVP Status: Attending $title on $date. Confirmation email sent to $currentEmail."
                } else {
                    val message = "RSVP Confirmed for $title on $date! ✅"
                    toast(message)
                    eventStatusText.text = "RSVP Status: Attending $title on $date."
                }

                renderEvents(DataCache.get("events") ?: BuiltInData.events())

                ApiClient.post("rsvp.php", JSONObject().put("event_id", ev.optInt("id")).put("cancel", false)) { }
            }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    /** Groups = the website's ministries (MINISTRY table). */
    private fun refreshGroups() {
        val view = findViewById<TextView>(R.id.groupsDbText)
        showCachedThenFresh("ministries.php", "ministries", view) { json ->
            val sb = StringBuilder("ALL CHURCH GROUPS\n")
            val list = json.optJSONArray("ministries")
            for (i in 0 until (list?.length() ?: 0)) {
                val g = list!!.getJSONObject(i)
                sb.append("\n${g.optString("name")}\nLeader: ${g.optString("leader")} • ${g.optString("schedule")}\n")
            }
            view.text = sb.toString()
        }
    }

    private fun refreshPrayerWall() {
        showCachedThenFresh("prayers.php", "prayers", prayerListText) { json ->
            val sb = StringBuilder("Prayer Wall")
            val list = json.optJSONArray("prayers")
            for (i in 0 until (list?.length() ?: 0)) {
                val p = list!!.getJSONObject(i)
                sb.append("\n\n${p.optString("text")}\n${p.optString("name")}\n${p.optInt("praying")} praying for this")
            }
            if ((list?.length() ?: 0) == 0) sb.append("\n\nNo prayer requests yet. Be the first to share one.")
            prayerListText.text = sb.toString()
        }
    }

    /** Quietly downloads events, groups and prayers after sign-in so those screens open instantly. */
    private fun prefetchLists() {
        listOf("events.php" to "events", "ministries.php" to "ministries", "prayers.php" to "prayers").forEach { (path, key) ->
            ApiClient.get(path) { r -> if (r.ok) DataCache.put(key, r.json) }
        }
    }

    /**
     * Shows the last saved copy (or the built-in church data) immediately — no "Loading..." text —
     * then asks the server for fresh data and redraws. If the server can't be reached the saved
     * copy simply stays on screen; no error message is shown.
     */
    private fun showCachedThenFresh(path: String, cacheKey: String, view: TextView,
                                    onFresh: () -> Unit = {}, render: (JSONObject) -> Unit) {
        val cached = DataCache.get(cacheKey) ?: BuiltInData.forKey(cacheKey)
        if (cached != null) render(cached) else if (view.text.isNullOrBlank()) view.text = " "
        ApiClient.get(path) { r ->
            if (!r.ok) return@get
            DataCache.put(cacheKey, r.json)
            render(r.json)
            onFresh()
        }
    }

    private fun refreshJournal() {
        val sb = StringBuilder("PAST JOURNAL ENTRIES")
        db.getJournal(currentEmail).forEach { sb.append("\n\nReflection:\n$it") }
        journalListText.text = sb.toString()
    }

    // ---------------------------------------------------------------- settings
    private val textSizes = arrayOf("Small", "Medium", "Large")
    private val textSizeValues = intArrayOf(14, 16, 20)

    private fun loadSettingsScreen() {
        val spinner = findViewById<Spinner>(R.id.settingTextSize)
        spinner.adapter = darkSpinnerAdapter(textSizes)
        val s = db.getSettings(currentEmail)

        findViewById<Switch>(R.id.settingNotifications).isChecked = s.notifications
        findViewById<Switch>(R.id.settingEventReminders).isChecked = s.eventReminders
        findViewById<Switch>(R.id.settingPrayerAlerts).isChecked = s.prayerAlerts
        findViewById<Switch>(R.id.settingDonationReceipts).isChecked = s.donationReceipts

        findViewById<Switch>(R.id.settingScripture).isChecked = s.showScripture
        findViewById<Switch>(R.id.settingDarkMode).isChecked = s.darkMode
        spinner.setSelection(textSizeValues.indexOf(s.textSize).coerceAtLeast(0))

        findViewById<EditText>(R.id.settingName).setText(currentName)
        findViewById<EditText>(R.id.settingPhone).setText(currentPhone)
        findViewById<EditText>(R.id.oldPasswordInput).text.clear()
        findViewById<EditText>(R.id.newPasswordInput).text.clear()

        findViewById<TextView>(R.id.securityInfoText).text =
            "SECURITY & PRIVACY PROTECTION\n\nAccount Email: $currentEmail\nRole: $currentRole\nPassword storage: bcrypt (on the church server)\nSign-in: password + 6-digit code sent to your email and phone\nData Storage: Shammah church database (shared with the website); journal and settings stay on this phone\n\nYou can update your profile details or change your password below. Changes apply on the website too."
    }

    private fun saveNotificationsSettings() {
        hideKeyboard()
        val s = db.getSettings(currentEmail).copy(
            notifications = findViewById<Switch>(R.id.settingNotifications).isChecked,
            eventReminders = findViewById<Switch>(R.id.settingEventReminders).isChecked,
            prayerAlerts = findViewById<Switch>(R.id.settingPrayerAlerts).isChecked,
            donationReceipts = findViewById<Switch>(R.id.settingDonationReceipts).isChecked
        )
        db.saveSettings(currentEmail, s)
        toast("Notification preferences saved")
    }

    private fun saveAppearanceSettings() {
        hideKeyboard()
        val size = textSizeValues[findViewById<Spinner>(R.id.settingTextSize).selectedItemPosition]
        val s = db.getSettings(currentEmail).copy(
            showScripture = findViewById<Switch>(R.id.settingScripture).isChecked,
            darkMode = findViewById<Switch>(R.id.settingDarkMode).isChecked,
            textSize = size
        )
        db.saveSettings(currentEmail, s)
        applyTextSize(size)
        toast("Appearance settings saved")
    }

    private fun applyTextSize(size: Int) {
        val sp = size.toFloat()
        listOf(
            profileInfoText, eventStatusText, donationHistoryText, prayerListText,
            journalListText, findViewById<TextView>(R.id.helpText),
            findViewById<TextView>(R.id.notificationsInfoText),
            findViewById<TextView>(R.id.appearanceInfoText),
            findViewById<TextView>(R.id.securityInfoText),
            findViewById<TextView>(R.id.eventsDbText), findViewById<TextView>(R.id.groupsDbText)
        ).forEach { it?.textSize = sp }
    }

    private fun showChurchSupportDialog() {
        val message = """
            OFFICIAL CHURCH SUPPORT CONTACT
            
            📍 Campus Location:
            Pretoria East Campus, Gauteng, South Africa
            
            📞 Support Office Phone:
            +27 12 345 6789
            
            📱 WhatsApp Support:
            +27 82 111 0004
            
            ✉️ Support Email:
            support@shammahchurch.org
            
            🕒 Office Hours:
            Monday – Friday: 08:00 AM – 16:30 PM
            
            ⛪ Sunday Service Times:
            09:00 AM & 17:00 PM
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("⛪ Church Support Contact")
            .setMessage(message)
            .setPositiveButton("Send Email") { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@shammahchurch.org"))
                    intent.putExtra(Intent.EXTRA_SUBJECT, "Church Support Inquiry - $currentName")
                    startActivity(intent)
                } catch (e: Exception) {
                    toast("Email client not available. Email: support@shammahchurch.org")
                }
            }
            .setNeutralButton("Call Office") { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+27123456789"))
                    startActivity(intent)
                } catch (e: Exception) {
                    toast("Phone dialer not available. Phone: +27 12 345 6789")
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun saveProfile() {
        hideKeyboard()
        val name  = findViewById<EditText>(R.id.settingName).text.toString().trim()
        val phone = findViewById<EditText>(R.id.settingPhone).text.toString().trim()

        if (name.length < 2 || name.any { it.isDigit() }) {
            toast("Enter a valid name")
            return
        }
        val e164 = PhoneFormat.toE164(phone)
        if (e164 == null) {
            toast("Enter a valid phone number")
            return
        }
        ApiClient.post("me.php", JSONObject().put("name", name).put("phone", e164)) { r ->
            if (!r.ok) {
                toast(r.msg)
                return@post
            }
            val user = userFromJson(r.json.optJSONObject("user") ?: JSONObject())
            currentName = user.name
            currentPhone = user.phone
            db.cacheUser(user)
            findViewById<EditText>(R.id.settingPhone).setText(currentPhone)
            profileInfoText.text =
                "$currentName\n$currentRole\n\nEMAIL: $currentEmail\nPHONE: $currentPhone\nGROUP: Pretoria East Cell Group\nLEADER: Kay Smith\nAREA: Pretoria East"
            toast("Profile updated")
        }
    }

    private fun changePassword() {
        hideKeyboard()
        val oldPass = findViewById<EditText>(R.id.oldPasswordInput).text.toString()
        val newPass = findViewById<EditText>(R.id.newPasswordInput).text.toString()

        if (oldPass.isBlank() || newPass.isBlank()) {
            toast("Enter your current and new password")
            return
        }
        if (newPass.length < 8 || newPass.none { it.isLetter() } || newPass.none { it.isDigit() }) {
            toast("New password must be 8+ characters with a letter and a number")
            return
        }
        ApiClient.post("change_password.php",
            JSONObject().put("old_password", oldPass).put("new_password", newPass)) { r ->
            toast(r.msg)
            if (r.ok) {
                findViewById<EditText>(R.id.oldPasswordInput).text.clear()
                findViewById<EditText>(R.id.newPasswordInput).text.clear()
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

// =====================================================================================
//  CREDIT / DEBIT CARD VALIDATOR (Luhn Checksum, Card Types & CVV Validation)
// =====================================================================================
object CardValidator {

    data class ValidationResult(val isValid: Boolean, val cardType: String, val errorMessage: String)

    fun validateCard(number: String, name: String, cvv: String, isExpired: Boolean): ValidationResult {
        val cleanNumber = number.filter { it.isDigit() }
        val cleanName = name.trim()
        val cleanCvv = cvv.filter { it.isDigit() }

        if (cleanName.length < 2 || cleanName.any { it.isDigit() }) {
            return ValidationResult(false, "Unknown", "Enter a valid cardholder name")
        }

        if (cleanNumber.length !in 13..19) {
            return ValidationResult(false, "Unknown", "Card number must be 13 to 19 digits")
        }

        if (!isValidLuhn(cleanNumber)) {
            return ValidationResult(false, "Unknown", "Invalid card number (Luhn checksum failed)")
        }

        val cardType = detectCardType(cleanNumber)

        if (cardType == "American Express" && cleanCvv.length != 4) {
            return ValidationResult(false, cardType, "American Express requires a 4-digit CVV")
        } else if (cardType != "American Express" && cleanCvv.length !in 3..4) {
            return ValidationResult(false, cardType, "Enter a valid 3 or 4-digit CVV")
        }

        if (isExpired) {
            return ValidationResult(false, cardType, "This card has expired")
        }

        return ValidationResult(true, cardType, "")
    }

    private fun isValidLuhn(number: String): Boolean {
        var sum = 0
        var alternate = false
        for (i in number.length - 1 downTo 0) {
            var n = number[i] - '0'
            if (alternate) {
                n *= 2
                if (n > 9) n = (n % 10) + 1
            }
            sum += n
            alternate = !alternate
        }
        return (sum % 10 == 0)
    }

    private fun detectCardType(number: String): String = when {
        number.startsWith("4") -> "Visa"
        number.matches(Regex("^(5[1-5]|222[1-9]|22[3-9]|2[3-6]|27[0-1]|2720).*")) -> "MasterCard"
        number.startsWith("34") || number.startsWith("37") -> "American Express"
        number.startsWith("6011") || number.startsWith("65") -> "Discover"
        else -> "Credit/Debit"
    }
}

// =====================================================================================
//  PASSWORD ENCRYPTION  (salted PBKDF2-HmacSHA256, format: iterations:salt:hash)
// =====================================================================================
object PasswordHasher {
    private const val ITERATIONS = 65536
    private const val KEY_LENGTH = 256
    private const val SALT_BYTES = 16

    fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derive(password, salt, ITERATIONS)
        return "$ITERATIONS:${Base64.encodeToString(salt, Base64.NO_WRAP)}:${Base64.encodeToString(hash, Base64.NO_WRAP)}"
    }

    fun verify(password: String, stored: String?): Boolean {
        if (stored.isNullOrBlank()) return false
        return try {
            val parts = stored.split(":")
            if (parts.size != 3) return false
            val iterations = parts[0].toInt()
            val salt = Base64.decode(parts[1], Base64.NO_WRAP)
            val expected = Base64.decode(parts[2], Base64.NO_WRAP)
            MessageDigest.isEqual(expected, derive(password, salt, iterations))
        } catch (e: Exception) {
            false
        }
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}

// =====================================================================================
//  ON-PHONE DATABASE (SQLite) - 7 tables, each seeded with 10 simulated records.
//  Accounts, events, donations and prayers now come from the shared MySQL database
//  (see ApiClient). SQLite keeps the private journal, app settings and a cached copy
//  of the signed-in member's profile.
// =====================================================================================
data class User(
    val id: Long,
    val name: String,
    val email: String,
    val phone: String,
    val role: String,
    val provider: String
)

data class Prayer(val id: Long, val name: String, val text: String, val status: String)
data class Donation(val id: Long, val name: String, val category: String, val amount: Double, val method: String)
data class ChurchEvent(val id: Long, val title: String, val date: String, val venue: String)
data class ChurchGroup(val id: Long, val name: String, val leader: String, val area: String)
data class UserSettings(
    val notifications: Boolean = true,
    val showScripture: Boolean = true,
    val textSize: Int = 16,
    val eventReminders: Boolean = true,
    val prayerAlerts: Boolean = true,
    val donationReceipts: Boolean = true,
    val darkMode: Boolean = true
)

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, "shammah.db", null, DB_VERSION) {

    companion object {
        const val DB_VERSION = 2   // v2: settings columns added, signed-in member cached locally
        const val DEMO_PASSWORD = "Shammah@123"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                email TEXT NOT NULL UNIQUE,
                phone TEXT NOT NULL,
                password_hash TEXT,
                role TEXT NOT NULL DEFAULT 'Member',
                provider TEXT NOT NULL DEFAULT 'local',
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )"""
        )
        db.execSQL(
            """CREATE TABLE donations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                donor_name TEXT NOT NULL,
                category TEXT NOT NULL,
                amount REAL NOT NULL,
                method TEXT NOT NULL DEFAULT 'Cash',
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )"""
        )
        db.execSQL(
            """CREATE TABLE prayer_requests (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                author TEXT NOT NULL,
                request_text TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'PENDING',
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )"""
        )
        db.execSQL(
            """CREATE TABLE events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                event_date TEXT NOT NULL,
                venue TEXT NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE church_groups (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                group_name TEXT NOT NULL,
                leader TEXT NOT NULL,
                area TEXT NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE journal_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_email TEXT NOT NULL,
                entry_text TEXT NOT NULL,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (user_email) REFERENCES users(email) ON DELETE CASCADE
            )"""
        )
        db.execSQL(
            """CREATE TABLE user_settings (
                user_email TEXT PRIMARY KEY,
                notifications INTEGER NOT NULL DEFAULT 1,
                show_scripture INTEGER NOT NULL DEFAULT 1,
                text_size INTEGER NOT NULL DEFAULT 16,
                event_reminders INTEGER NOT NULL DEFAULT 1,
                prayer_alerts INTEGER NOT NULL DEFAULT 1,
                donation_receipts INTEGER NOT NULL DEFAULT 1,
                dark_mode INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY (user_email) REFERENCES users(email) ON DELETE CASCADE
            )"""
        )
        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        listOf(
            "user_settings", "journal_entries", "church_groups", "events",
            "prayer_requests", "donations", "users"
        ).forEach { db.execSQL("DROP TABLE IF EXISTS $it") }
        onCreate(db)
    }

    private fun seed(db: SQLiteDatabase) {
        val demoHash = PasswordHasher.hash(DEMO_PASSWORD)
        val users = listOf(
            arrayOf("Mbali M.", "mbali@church.com", "0821110001", "Member"),
            arrayOf("Daniel T.", "daniel@church.com", "0821110002", "Volunteer"),
            arrayOf("Kay Smith", "kay@church.com", "0821110003", "Staff"),
            arrayOf("Pastor Samuel", "pastor@church.com", "0821110004", "Pastor"),
            arrayOf("Thandi Nkosi", "thandi@church.com", "0821110005", "Member"),
            arrayOf("Sipho Dlamini", "sipho@church.com", "0821110006", "Member"),
            arrayOf("Lerato Mokoena", "lerato@church.com", "0821110007", "Volunteer"),
            arrayOf("Johan van Wyk", "johan@church.com", "0821110008", "Member"),
            arrayOf("Naledi Khumalo", "naledi@church.com", "0821110009", "Staff"),
            arrayOf("Tshepo Molefe", "tshepo@church.com", "0821110010", "Member")
        )
        users.forEach { u ->
            db.insert("users", null, ContentValues().apply {
                put("name", u[0]); put("email", u[1]); put("phone", u[2])
                put("password_hash", demoHash); put("role", u[3]); put("provider", "local")
            })
            db.insert("user_settings", null, ContentValues().apply { put("user_email", u[1]) })
        }

        val donations = listOf(
            arrayOf<Any>("Mbali M.", "Tithe", 1200.0, "EFT"),
            arrayOf<Any>("Daniel T.", "Offering", 150.0, "Cash"),
            arrayOf<Any>("Kay Smith", "Building Fund", 500.0, "Card"),
            arrayOf<Any>("Thandi Nkosi", "Tithe", 800.0, "EFT"),
            arrayOf<Any>("Sipho Dlamini", "Special Seed", 300.0, "Card"),
            arrayOf<Any>("Lerato Mokoena", "Offering", 100.0, "Cash"),
            arrayOf<Any>("Johan van Wyk", "Building Fund", 1000.0, "EFT"),
            arrayOf<Any>("Naledi Khumalo", "Tithe", 650.0, "Card"),
            arrayOf<Any>("Tshepo Molefe", "Offering", 200.0, "Cash"),
            arrayOf<Any>("Pastor Samuel", "Special Seed", 250.0, "Card")
        )
        donations.forEach { d ->
            db.insert("donations", null, ContentValues().apply {
                put("donor_name", d[0] as String); put("category", d[1] as String)
                put("amount", d[2] as Double); put("method", d[3] as String)
            })
        }

        val prayers = listOf(
            arrayOf("Mbali M.", "Please pray for my family's health.", "APPROVED"),
            arrayOf("Daniel T.", "Pray for my exams this month.", "APPROVED"),
            arrayOf("Kay Smith", "Pray for guidance in a new job.", "APPROVED"),
            arrayOf("Thandi Nkosi", "Pray for my mother's recovery.", "APPROVED"),
            arrayOf("Sipho Dlamini", "Pray for peace in my home.", "APPROVED"),
            arrayOf("Lerato Mokoena", "Pray for our youth ministry.", "PENDING"),
            arrayOf("Johan van Wyk", "Pray for financial breakthrough.", "PENDING"),
            arrayOf("Naledi Khumalo", "Pray for safe travels this weekend.", "PENDING"),
            arrayOf("Tshepo Molefe", "Pray for strength during a hard season.", "PENDING"),
            arrayOf("Pastor Samuel", "Pray for the church building project.", "APPROVED")
        )
        prayers.forEach { p ->
            db.insert("prayer_requests", null, ContentValues().apply {
                put("author", p[0]); put("request_text", p[1]); put("status", p[2])
            })
        }

        val events = listOf(
            arrayOf("Sunday Celebration Service", "Sunday, 1 Nov 2026 at 09:00 AM", "Main Sanctuary"),
            arrayOf("Midweek Prayer & Intercession", "Wednesday, 4 Nov 2026 at 18:00 PM", "Prayer Room"),
            arrayOf("Youth Night Encounter", "Friday, 6 Nov 2026 at 18:30 PM", "Youth Hall"),
            arrayOf("Women of Purpose Conference", "Saturday, 7 Nov 2026 at 09:00 AM", "Fellowship Hall"),
            arrayOf("Community Outreach & Food Drive", "Saturday, 14 Nov 2026 at 08:30 AM", "Pretoria East Campus"),
            arrayOf("November Thanksgiving Service", "Sunday, 22 Nov 2026 at 09:00 AM", "Main Sanctuary"),
            arrayOf("Youth Leadership Summit", "Sunday, 29 Nov 2026 at 14:00 PM", "Youth Centre")
        )
        events.forEach { e ->
            db.insert("events", null, ContentValues().apply {
                put("title", e[0]); put("event_date", e[1]); put("venue", e[2])
            })
        }

        val groups = listOf(
            arrayOf("Pretoria East Cell Group", "Kay Smith", "Pretoria East"),
            arrayOf("Menlyn Cell Group", "Daniel T.", "Menlyn"),
            arrayOf("Garsfontein Cell Group", "Mbali M.", "Garsfontein"),
            arrayOf("Faerie Glen Cell Group", "Thandi Nkosi", "Faerie Glen"),
            arrayOf("Moreleta Park Cell Group", "Sipho Dlamini", "Moreleta Park"),
            arrayOf("Youth Group", "Lerato Mokoena", "Church Campus"),
            arrayOf("Young Adults Group", "Johan van Wyk", "Church Campus"),
            arrayOf("Women's Group", "Naledi Khumalo", "Church Campus"),
            arrayOf("Men's Group", "Tshepo Molefe", "Church Campus"),
            arrayOf("Worship Team", "Pastor Samuel", "Church Campus")
        )
        groups.forEach { g ->
            db.insert("church_groups", null, ContentValues().apply {
                put("group_name", g[0]); put("leader", g[1]); put("area", g[2])
            })
        }

        val journal = listOf(
            "Grateful for a peaceful week.",
            "Reflecting on Psalm 23 today.",
            "Prayed for my family this morning.",
            "Learning to trust God with my plans.",
            "Thankful for the community at church.",
            "Spent time in worship tonight.",
            "Studying Proverbs 3 this week.",
            "God provided in an unexpected way.",
            "Choosing joy in a difficult season.",
            "Looking forward to Sunday service."
        )
        journal.forEachIndexed { i, text ->
            db.insert("journal_entries", null, ContentValues().apply {
                put("user_email", users[i % users.size][1]); put("entry_text", text)
            })
        }
    }

    private fun cursorToUser(c: android.database.Cursor) = User(
        c.getLong(c.getColumnIndexOrThrow("id")),
        c.getString(c.getColumnIndexOrThrow("name")),
        c.getString(c.getColumnIndexOrThrow("email")),
        c.getString(c.getColumnIndexOrThrow("phone")),
        c.getString(c.getColumnIndexOrThrow("role")),
        c.getString(c.getColumnIndexOrThrow("provider"))
    )

    fun userExists(email: String): Boolean {
        readableDatabase.rawQuery("SELECT 1 FROM users WHERE email = ?", arrayOf(email.lowercase())).use {
            return it.moveToFirst()
        }
    }

    fun addUser(
        name: String, email: String, phone: String, password: String?,
        role: String, provider: String = "local"
    ): Boolean {
        val values = ContentValues().apply {
            put("name", name); put("email", email.lowercase()); put("phone", phone)
            put("role", role); put("provider", provider)
            if (password != null) put("password_hash", PasswordHasher.hash(password))
        }
        val id = writableDatabase.insert("users", null, values)
        if (id == -1L) return false
        writableDatabase.insertWithOnConflict(
            "user_settings", null,
            ContentValues().apply { put("user_email", email.lowercase()) },
            SQLiteDatabase.CONFLICT_IGNORE
        )
        return true
    }

    /** Keeps a local copy of the member who signed in through the website API. */
    fun cacheUser(user: User) {
        val email = user.email.lowercase()
        if (email.isBlank()) return
        val values = ContentValues().apply {
            put("name", user.name); put("phone", user.phone); put("role", user.role); put("provider", user.provider)
        }
        if (writableDatabase.update("users", values, "email = ?", arrayOf(email)) == 0) {
            values.put("email", email)
            writableDatabase.insert("users", null, values)
        }
        writableDatabase.insertWithOnConflict(
            "user_settings", null, ContentValues().apply { put("user_email", email) },
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun getUser(email: String): User? {
        readableDatabase.rawQuery(
            "SELECT id, name, email, phone, role, provider FROM users WHERE email = ?",
            arrayOf(email.lowercase())
        ).use { c -> return if (c.moveToFirst()) cursorToUser(c) else null }
    }

    fun login(email: String, password: String): User? {
        readableDatabase.rawQuery(
            "SELECT password_hash FROM users WHERE email = ?", arrayOf(email.lowercase())
        ).use { c ->
            if (!c.moveToFirst()) return null
            val stored = c.getString(0)
            if (!PasswordHasher.verify(password, stored)) return null
        }
        return getUser(email)
    }

    fun updateProfile(email: String, name: String, phone: String): Boolean =
        writableDatabase.update(
            "users", ContentValues().apply { put("name", name); put("phone", phone) },
            "email = ?", arrayOf(email.lowercase())
        ) > 0

    fun updateRole(email: String, role: String): Boolean =
        writableDatabase.update(
            "users", ContentValues().apply { put("role", role) },
            "email = ?", arrayOf(email.lowercase())
        ) > 0

    /** Keeps the local copy in step after a reset done through the emailed link. */
    fun setPassword(email: String, newPassword: String): Boolean =
        writableDatabase.update(
            "users", ContentValues().apply { put("password_hash", PasswordHasher.hash(newPassword)) },
            "email = ?", arrayOf(email.lowercase())
        ) > 0

    fun changePassword(email: String, oldPassword: String, newPassword: String): Boolean {
        if (login(email, oldPassword) == null) return false
        return writableDatabase.update(
            "users",
            ContentValues().apply { put("password_hash", PasswordHasher.hash(newPassword)) },
            "email = ?", arrayOf(email.lowercase())
        ) > 0
    }

    fun getAllUsers(): List<User> {
        val list = mutableListOf<User>()
        readableDatabase.rawQuery(
            "SELECT id, name, email, phone, role, provider FROM users ORDER BY id", null
        ).use { c -> while (c.moveToNext()) list.add(cursorToUser(c)) }
        return list
    }

    fun addDonation(name: String, category: String, amount: Double, method: String) {
        writableDatabase.insert("donations", null, ContentValues().apply {
            put("donor_name", name); put("category", category)
            put("amount", amount); put("method", method)
        })
    }

    fun getDonations(): List<Donation> {
        val list = mutableListOf<Donation>()
        readableDatabase.rawQuery(
            "SELECT id, donor_name, category, amount, method FROM donations ORDER BY id", null
        ).use { c ->
            while (c.moveToNext()) list.add(Donation(c.getLong(0), c.getString(1), c.getString(2), c.getDouble(3), c.getString(4)))
        }
        return list
    }

    fun totalDonations(): Double {
        readableDatabase.rawQuery("SELECT COALESCE(SUM(amount), 0) FROM donations", null).use {
            return if (it.moveToFirst()) it.getDouble(0) else 0.0
        }
    }

    fun addPrayer(author: String, text: String) {
        writableDatabase.insert("prayer_requests", null, ContentValues().apply {
            put("author", author); put("request_text", text); put("status", "PENDING")
        })
    }

    fun getPrayers(status: String): List<Prayer> {
        val list = mutableListOf<Prayer>()
        readableDatabase.rawQuery(
            "SELECT id, author, request_text, status FROM prayer_requests WHERE status = ? ORDER BY id",
            arrayOf(status)
        ).use { c ->
            while (c.moveToNext()) list.add(Prayer(c.getLong(0), c.getString(1), c.getString(2), c.getString(3)))
        }
        return list
    }

    fun setPrayerStatus(id: Long, status: String) {
        writableDatabase.update(
            "prayer_requests", ContentValues().apply { put("status", status) },
            "id = ?", arrayOf(id.toString())
        )
    }

    fun getEvents(): List<ChurchEvent> {
        val list = mutableListOf<ChurchEvent>()
        readableDatabase.rawQuery("SELECT id, title, event_date, venue FROM events ORDER BY id", null).use { c ->
            while (c.moveToNext()) list.add(ChurchEvent(c.getLong(0), c.getString(1), c.getString(2), c.getString(3)))
        }
        return list
    }

    fun getGroups(): List<ChurchGroup> {
        val list = mutableListOf<ChurchGroup>()
        readableDatabase.rawQuery("SELECT id, group_name, leader, area FROM church_groups ORDER BY id", null).use { c ->
            while (c.moveToNext()) list.add(ChurchGroup(c.getLong(0), c.getString(1), c.getString(2), c.getString(3)))
        }
        return list
    }

    fun addJournal(email: String, text: String) {
        writableDatabase.insert("journal_entries", null, ContentValues().apply {
            put("user_email", email.lowercase()); put("entry_text", text)
        })
    }

    fun getJournal(email: String): List<String> {
        val list = mutableListOf<String>()
        readableDatabase.rawQuery(
            "SELECT entry_text FROM journal_entries WHERE user_email = ? ORDER BY id",
            arrayOf(email.lowercase())
        ).use { c -> while (c.moveToNext()) list.add(c.getString(0)) }
        return list
    }

    fun getSettings(email: String): UserSettings {
        try {
            readableDatabase.rawQuery(
                "SELECT notifications, show_scripture, text_size, event_reminders, prayer_alerts, donation_receipts, dark_mode FROM user_settings WHERE user_email = ?",
                arrayOf(email.lowercase())
            ).use { c ->
                if (c.moveToFirst()) {
                    return UserSettings(
                        c.getInt(0) == 1,
                        c.getInt(1) == 1,
                        c.getInt(2),
                        c.getInt(3) == 1,
                        c.getInt(4) == 1,
                        c.getInt(5) == 1,
                        c.getInt(6) == 1
                    )
                }
            }
        } catch (e: Exception) {
            try {
                readableDatabase.rawQuery(
                    "SELECT notifications, show_scripture, text_size FROM user_settings WHERE user_email = ?",
                    arrayOf(email.lowercase())
                ).use { c ->
                    if (c.moveToFirst()) {
                        return UserSettings(
                            notifications = c.getInt(0) == 1,
                            showScripture = c.getInt(1) == 1,
                            textSize = c.getInt(2)
                        )
                    }
                }
            } catch (ex: Exception) {}
        }
        return UserSettings()
    }

    fun saveSettings(email: String, s: UserSettings) {
        val cv = ContentValues().apply {
            put("user_email", email.lowercase())
            put("notifications", if (s.notifications) 1 else 0)
            put("show_scripture", if (s.showScripture) 1 else 0)
            put("text_size", s.textSize)
        }
        try {
            cv.put("event_reminders", if (s.eventReminders) 1 else 0)
            cv.put("prayer_alerts", if (s.prayerAlerts) 1 else 0)
            cv.put("donation_receipts", if (s.donationReceipts) 1 else 0)
            cv.put("dark_mode", if (s.darkMode) 1 else 0)
        } catch (e: Exception) {}
        writableDatabase.insertWithOnConflict(
            "user_settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE
        )
    }
}
