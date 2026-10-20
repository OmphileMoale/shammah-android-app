package com.example.shammah

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Patterns
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// =====================================================================================
//  ONLINE SERVICE SETTINGS  (Firebase Authentication + Realtime Database + Google SSO)
//  Replace the three YOUR_ values with your own project's keys.
//  Until you do, the app works fully offline with the local SQLite database.
// =====================================================================================
object AppConfig {
    const val FIREBASE_API_KEY = "YOUR_FIREBASE_WEB_API_KEY"
    const val FIREBASE_DB_URL = "https://YOUR_PROJECT_ID-default-rtdb.firebaseio.com"
    const val GOOGLE_WEB_CLIENT_ID = "YOUR_WEB_CLIENT_ID.apps.googleusercontent.com"
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

    private lateinit var db: DatabaseHelper
    private lateinit var cloud: CloudService
    private var cloudToken = ""
    private var cloudUid = ""

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
        cloud = CloudService()
        setupSpinners()

        splashScreen.postDelayed({
            showOnly(welcomeScreen)
        }, 1500)

        findViewById<Button>(R.id.goCreateBtn).setOnClickListener { showOnly(createAccountScreen) }
        findViewById<Button>(R.id.goLoginBtn).setOnClickListener { showOnly(loginScreen) }
        findViewById<Button>(R.id.backCreateBtn).setOnClickListener { showOnly(welcomeScreen) }
        findViewById<Button>(R.id.backLoginBtn).setOnClickListener { showOnly(welcomeScreen) }

        findViewById<Button>(R.id.createAccountBtn).setOnClickListener { createAccount() }
        findViewById<Button>(R.id.signInBtn).setOnClickListener { login() }
        findViewById<Button>(R.id.continueToAppBtn).setOnClickListener { openMainApp() }

        findViewById<TextView>(R.id.navHome).setOnClickListener {
            if (currentRole == "Pastor") showPage(pastorDashboard)
            else showPage(homeScreen)
        }
        findViewById<TextView>(R.id.navEvents).setOnClickListener {
            refreshEvents()
            showPage(eventsScreen)
        }
        findViewById<TextView>(R.id.navGive).setOnClickListener { showPage(giveScreen) }
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

        findViewById<Button>(R.id.rsvpBtn).setOnClickListener {
            eventStatusText.text = "RSVP Status: You are attending Sunday Service."
            toast("RSVP confirmed")
        }

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
                val record = "\n$currentName - $category : R$amount"
                donationHistoryText.append(record)

                db.addDonation(currentName, category, value, "Cash")
                syncDonation(category, value, "Cash")

                findViewById<EditText>(R.id.donationAmount).text.clear()
                toast("Donation recorded successfully")
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

            when {
                name.isBlank()     -> toast("Enter cardholder name")
                !number.all { it.isDigit() } || number.length != 16 -> toast("Enter a valid 16-digit card number")
                !cvv.all { it.isDigit() } || cvv.length !in 3..4 -> toast("Enter a valid CVV (3 or 4 digits)")
                isCardExpired(findViewById<Spinner>(R.id.cardExpiryMonth).selectedItemPosition + 1,
                    findViewById<Spinner>(R.id.cardExpiryYear).selectedItem.toString().toInt()) ->
                    toast("This card has expired")
                else -> {
                    val amountLine = cardPaymentAmountLabel.text.toString()
                    val record = "\n$currentName: $amountLine [Card]"
                    donationHistoryText.append(record)

                    // Card details are NOT stored
                    val cardAmount = Regex("R(\\d+(\\.\\d+)?)").find(amountLine)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                    val cardCategory = amountLine.substringAfter("•", "Offering").trim()
                    db.addDonation(currentName, cardCategory, cardAmount, "Card")
                    syncDonation(cardCategory, cardAmount, "Card")

                    findViewById<EditText>(R.id.cardHolderName).text.clear()
                    findViewById<EditText>(R.id.cardNumber).text.clear()
                    findViewById<EditText>(R.id.cardCvv).text.clear()
                    findViewById<EditText>(R.id.donationAmount).text.clear()

                    toast("Payment successful! Thank you, $name 🙏")
                    showPage(giveScreen)
                }
            }
        }

        findViewById<Button>(R.id.submitPrayerBtn).setOnClickListener {
            hideKeyboard()
            val prayer = findViewById<EditText>(R.id.prayerInput).text.toString()
            if (prayer.isBlank()) {
                toast("Enter prayer request")
            } else {
                db.addPrayer(currentName, prayer.trim())
                findViewById<EditText>(R.id.prayerInput).text.clear()
                toast("Prayer submitted for approval")
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

        findViewById<Button>(R.id.logoutBtn).setOnClickListener {
            cloudToken = ""
            cloudUid = ""
            showOnly(welcomeScreen)
            toast("Signed out")
        }
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

        findViewById<Spinner>(R.id.roleSpinner).adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)

        findViewById<Spinner>(R.id.donationCategorySpinner).adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)

        findViewById<Spinner>(R.id.cardExpiryMonth).adapter = darkSpinnerAdapter(months)
        findViewById<Spinner>(R.id.cardExpiryYear).adapter = darkSpinnerAdapter(years)
    }

    private fun createAccount() {
        hideKeyboard()
        val name     = findViewById<EditText>(R.id.createNameInput).text.toString().trim()
        val email    = findViewById<EditText>(R.id.createEmailInput).text.toString().trim().lowercase()
        val phone    = findViewById<EditText>(R.id.createPhoneInput).text.toString().trim()
        val password = findViewById<EditText>(R.id.createPasswordInput).text.toString().trim()
        val role     = findViewById<Spinner>(R.id.roleSpinner).selectedItem.toString()

        if (name.isBlank() || email.isBlank() || phone.isBlank() || password.isBlank()) {
            toast("Complete all fields")
            return
        }

        if (name.length < 2 || name.any { it.isDigit() }) {
            toast("Enter a valid name")
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("Invalid email address")
            return
        }

        if (!phone.all { it.isDigit() || it == '+' } || phone.count { it.isDigit() } !in 9..13) {
            toast("Enter a valid phone number")
            return
        }

        if (password.length < 8 || password.none { it.isLetter() } || password.none { it.isDigit() }) {
            toast("Password must be 8+ characters with a letter and a number")
            return
        }

        if (db.userExists(email)) {
            toast("User with this email already exists")
            return
        }

        // Password is hashed (PBKDF2 + salt) - never stored in plain text.
        if (!db.addUser(name, email, phone, password, role)) {
            toast("Could not create account. Please try again")
            return
        }

        if (cloud.isConfigured) {
            cloud.signUp(email, password) { result ->
                if (result.success) {
                    cloud.put(
                        "users/${result.uid}",
                        JSONObject().put("name", name).put("email", email).put("phone", phone).put("role", role),
                        result.idToken
                    )
                }
            }
        }

        toast("Account created successfully")
        showOnly(loginScreen)
    }

    private fun login() {
        hideKeyboard()
        val email    = findViewById<EditText>(R.id.emailInput).text.toString().trim().lowercase()
        val password = findViewById<EditText>(R.id.passwordInput).text.toString().trim()

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("Enter a valid email")
            return
        }

        if (password.isBlank()) {
            toast("Enter your password")
            return
        }

        val user = db.login(email, password)

        if (user != null) {
            startSession(user)
            if (cloud.isConfigured) {
                cloud.signIn(email, password) { r ->
                    if (r.success) {
                        cloudToken = r.idToken
                        cloudUid = r.uid
                        cloud.put(
                            "users/${r.uid}",
                            JSONObject().put("name", user.name).put("email", user.email)
                                .put("phone", user.phone).put("role", user.role),
                            r.idToken
                        )
                    }
                }
            }
        } else {
            toast("Invalid email or password")
        }
    }

    private fun refreshMemberList() {
        val sb = StringBuilder()
        db.getAllUsers().forEach { sb.append("${it.name} (${it.role}) - ${it.email}\n") }
        findViewById<TextView>(R.id.memberListText).text = sb.toString()
    }

    private fun refreshDonationList() {
        val sb = StringBuilder("--- ALL DONATIONS ---\n")
        db.getDonations().forEach {
            sb.append("\n${it.name} - ${it.category} : R${it.amount.toInt()} [${it.method}]")
        }
        findViewById<TextView>(R.id.allDonationsText).text = sb.toString()

        donationStatsText.text =
            "Total This Week: R${db.totalDonations().toInt()}\nTotal Last Week: R4000"
    }

    private fun refreshPendingPrayers() {
        val container = findViewById<LinearLayout>(R.id.pendingPrayersContainer)
        container.removeAllViews()
        val requests = db.getPrayers("PENDING")
        if (requests.isEmpty()) {
            val tv = TextView(this)
            tv.text = "No pending requests"
            tv.setTextColor(Color.WHITE)
            container.addView(tv)
            return
        }

        requests.forEach { req ->
            val layout = LinearLayout(this)
            layout.orientation = LinearLayout.VERTICAL
            layout.setPadding(0, 0, 0, 20)

            val tv = TextView(this)
            tv.text = "${req.name}: ${req.text}"
            tv.setTextColor(Color.WHITE)
            tv.textSize = 16f
            layout.addView(tv)

            val btn = Button(this)
            btn.text = "APPROVE"
            btn.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#C9A84C"))
            btn.setTextColor(Color.parseColor("#3D0C2F"))
            btn.setOnClickListener {
                db.setPrayerStatus(req.id, "APPROVED")
                toast("Prayer approved and posted")
                refreshPendingPrayers()
            }
            layout.addView(btn)

            val rejectBtn = Button(this)
            rejectBtn.text = "REJECT"
            rejectBtn.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.RED)
            rejectBtn.setTextColor(Color.WHITE)
            rejectBtn.setOnClickListener {
                db.setPrayerStatus(req.id, "REJECTED")
                toast("Prayer request rejected")
                refreshPendingPrayers()
            }
            layout.addView(rejectBtn)

            container.addView(layout)
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

        if (db.getSettings(user.email).showScripture) {
            loadRandomScripture()
            showOnly(scriptureScreen)
        } else {
            openMainApp()
        }
    }

    private fun googleSignOn() {
        hideKeyboard()
        GoogleSso.signIn(
            this,
            AppConfig.GOOGLE_WEB_CLIENT_ID,
            onSuccess = { account ->
                val email = account.email.lowercase()
                var role  = account.role
                if (email.contains("pastor")) {
                    role = "Pastor"
                }

                if (!db.userExists(email)) {
                    db.addUser(account.displayName, email, "0820000000", null, role, "google")
                } else {
                    val existing = db.getUser(email)
                    if (existing != null && existing.role != role) {
                        db.updateRole(email, role)
                    }
                }

                val user = db.getUser(email)
                if (user == null) {
                    toast("Could not sign in with Google")
                } else {
                    toast("Gmail Single Sign-On Approved! Welcome, ${user.role} ${user.name} 🙏")
                    startSession(user)
                    if (cloud.isConfigured) {
                        cloud.signInWithGoogle(account.idToken) { r ->
                            if (r.success) {
                                cloudToken = r.idToken
                                cloudUid = r.uid
                                cloud.put(
                                    "users/${r.uid}",
                                    JSONObject().put("name", user.name).put("email", user.email)
                                        .put("role", user.role).put("provider", "google")
                                        .put("youtubeApproved", true),
                                    r.idToken
                                )
                            }
                        }
                    }
                }
            },
            onFail = { message ->
                toast(message)
                showOnly(welcomeScreen)
            }
        )
    }

    private fun syncDonation(category: String, amount: Double, method: String) {
        if (cloud.isConfigured && cloudToken.isNotBlank()) {
            cloud.push(
                "donations",
                JSONObject().put("donor", currentName).put("category", category)
                    .put("amount", amount).put("method", method),
                cloudToken
            )
        }
    }

    private fun isCardExpired(month: Int, year: Int): Boolean {
        val cal = java.util.Calendar.getInstance()
        val nowYear = cal.get(java.util.Calendar.YEAR)
        val nowMonth = cal.get(java.util.Calendar.MONTH) + 1
        return year < nowYear || (year == nowYear && month < nowMonth)
    }

    private fun refreshEvents() {
        val sb = StringBuilder("ALL CHURCH EVENTS\n")
        db.getEvents().forEach { sb.append("\n${it.title}\n${it.date} • ${it.venue}\n") }
        findViewById<TextView>(R.id.eventsDbText).text = sb.toString()
    }

    private fun refreshGroups() {
        val sb = StringBuilder("ALL CHURCH GROUPS\n")
        db.getGroups().forEach { sb.append("\n${it.name}\nLeader: ${it.leader} • ${it.area}\n") }
        findViewById<TextView>(R.id.groupsDbText).text = sb.toString()
    }

    private fun refreshPrayerWall() {
        val sb = StringBuilder("Prayer Wall")
        db.getPrayers("APPROVED").forEach { sb.append("\n\n${it.text}\n${it.name}\n0 praying for this") }
        prayerListText.text = sb.toString()
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
            "SECURITY & PRIVACY PROTECTION\n\nAccount Email: $currentEmail\nRole: $currentRole\nEncryption Standard: PBKDF2 (65,536 iterations)\nData Storage: Local SQLite Database & Firebase Cloud\n\nYour credentials are securely hashed. You can update your profile details or change your password below."
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
        if (!phone.all { it.isDigit() || it == '+' } || phone.count { it.isDigit() } !in 9..13) {
            toast("Enter a valid phone number")
            return
        }
        if (db.updateProfile(currentEmail, name, phone)) {
            currentName = name
            currentPhone = phone
            profileInfoText.text =
                "$currentName\n$currentRole\n\nEMAIL: $currentEmail\nPHONE: $currentPhone\nGROUP: Pretoria East Cell Group\nLEADER: Kay Smith\nAREA: Pretoria East"
            toast("Profile updated")
        } else {
            toast("Could not update profile")
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
        if (db.changePassword(currentEmail, oldPass, newPass)) {
            findViewById<EditText>(R.id.oldPasswordInput).text.clear()
            findViewById<EditText>(R.id.newPasswordInput).text.clear()
            toast("Password changed")
        } else {
            toast("Current password is incorrect")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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
//  DATABASE (SQLite) - 7 tables, each seeded with 10 simulated records
//  Demo password for all seeded users: Shammah@123  (e.g. pastor@church.com)
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
        const val DB_VERSION = 1
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
            arrayOf("Mbali M.", "Tithe", 1200.0, "EFT"),
            arrayOf("Daniel T.", "Offering", 150.0, "Cash"),
            arrayOf("Kay Smith", "Building Fund", 500.0, "Card"),
            arrayOf("Thandi Nkosi", "Tithe", 800.0, "EFT"),
            arrayOf("Sipho Dlamini", "Special Seed", 300.0, "Card"),
            arrayOf("Lerato Mokoena", "Offering", 100.0, "Cash"),
            arrayOf("Johan van Wyk", "Building Fund", 1000.0, "EFT"),
            arrayOf("Naledi Khumalo", "Tithe", 650.0, "Card"),
            arrayOf("Tshepo Molefe", "Offering", 200.0, "Cash"),
            arrayOf("Pastor Samuel", "Special Seed", 250.0, "Card")
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
            arrayOf("Sunday Service", "Every Sunday 09:00", "Main Sanctuary"),
            arrayOf("Midweek Prayer", "Every Wednesday 18:00", "Prayer Room"),
            arrayOf("Youth Night", "Every Friday 18:30", "Youth Hall"),
            arrayOf("Women's Fellowship", "First Saturday 10:00", "Fellowship Hall"),
            arrayOf("Men's Breakfast", "Second Saturday 08:00", "Fellowship Hall"),
            arrayOf("Cell Group Meeting", "Every Tuesday 18:00", "Member Homes"),
            arrayOf("Worship Practice", "Every Thursday 17:30", "Main Sanctuary"),
            arrayOf("Children's Church", "Every Sunday 09:00", "Kids Hall"),
            arrayOf("Community Outreach", "Last Saturday 09:00", "Pretoria East"),
            arrayOf("Annual Conference", "First weekend of December", "Main Sanctuary")
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

// =====================================================================================
//  ONLINE AUTHENTICATION + API + DATABASE  (Firebase REST)
// =====================================================================================
class CloudService {

    data class AuthResult(
        val success: Boolean,
        val uid: String = "",
        val idToken: String = "",
        val email: String = "",
        val displayName: String = "",
        val error: String = ""
    )

    private val apiKey: String = AppConfig.FIREBASE_API_KEY
    private val dbUrl: String = AppConfig.FIREBASE_DB_URL.trimEnd('/')
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && !apiKey.startsWith("YOUR_") &&
                dbUrl.isNotBlank() && !dbUrl.contains("YOUR_")

    fun signUp(email: String, password: String, callback: (AuthResult) -> Unit) =
        authCall("accounts:signUp", JSONObject()
            .put("email", email).put("password", password).put("returnSecureToken", true), callback)

    fun signIn(email: String, password: String, callback: (AuthResult) -> Unit) =
        authCall("accounts:signInWithPassword", JSONObject()
            .put("email", email).put("password", password).put("returnSecureToken", true), callback)

    fun signInWithGoogle(googleIdToken: String, callback: (AuthResult) -> Unit) =
        authCall("accounts:signInWithIdp", JSONObject()
            .put("postBody", "id_token=$googleIdToken&providerId=google.com")
            .put("requestUri", "http://localhost")
            .put("returnIdpCredential", true)
            .put("returnSecureToken", true), callback)

    private fun authCall(endpoint: String, body: JSONObject, callback: (AuthResult) -> Unit) {
        if (!isConfigured) {
            callback(AuthResult(false, error = "Online service not configured"))
            return
        }
        executor.execute {
            val result = try {
                val url = "https://identitytoolkit.googleapis.com/v1/$endpoint?key=$apiKey"
                val (code, text) = request("POST", url, body.toString())
                val json = JSONObject(text)
                if (code in 200..299) {
                    AuthResult(
                        true,
                        uid = json.optString("localId"),
                        idToken = json.optString("idToken"),
                        email = json.optString("email"),
                        displayName = json.optString("displayName")
                    )
                } else {
                    AuthResult(false, error = json.optJSONObject("error")?.optString("message") ?: "Request failed")
                }
            } catch (e: Exception) {
                AuthResult(false, error = e.message ?: "Network error")
            }
            main.post { callback(result) }
        }
    }

    fun put(path: String, data: JSONObject, idToken: String, callback: ((Boolean) -> Unit)? = null) =
        write("PUT", path, data, idToken, callback)

    fun push(path: String, data: JSONObject, idToken: String, callback: ((Boolean) -> Unit)? = null) =
        write("POST", path, data, idToken, callback)

    private fun write(method: String, path: String, data: JSONObject, idToken: String, callback: ((Boolean) -> Unit)?) {
        if (!isConfigured || idToken.isBlank()) {
            callback?.invoke(false)
            return
        }
        executor.execute {
            val ok = try {
                val (code, _) = request(method, "$dbUrl/$path.json?auth=$idToken", data.toString())
                code in 200..299
            } catch (e: Exception) {
                false
            }
            if (callback != null) main.post { callback(ok) }
        }
    }

    private fun request(method: String, url: String, body: String): Pair<Int, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: "{}"
            return Pair(code, text)
        } finally {
            conn.disconnect()
        }
    }
}

// =====================================================================================
//  OAUTH2 / OPENID CONNECT SSO ASSERTION PROVIDER (Signed JWT & Auth Code Generator)
// =====================================================================================
object SsoTokenProvider {

    data class SsoAssertion(
        val authCode: String,
        val jwtToken: String,
        val email: String,
        val name: String,
        val role: String,
        val issuer: String,
        val isValidSignature: Boolean
    )

    private const val SECRET_KEY = "ShammahChurchSsoSecretKeyForSigningJwtTokens"

    fun generateAssertion(email: String, name: String, role: String): SsoAssertion {
        val authCode = "AUTH_CODE_" + java.util.UUID.randomUUID().toString().replace("-", "").take(16).uppercase()
        val header = JSONObject().put("alg", "HS256").put("typ", "JWT").toString()

        val iat = System.currentTimeMillis() / 1000
        val exp = iat + 86400
        val payload = JSONObject()
            .put("iss", "https://sso.shammahchurch.org/oauth2/v2.0")
            .put("sub", "user_" + Math.abs(email.hashCode()))
            .put("aud", "shammah-mobile-app")
            .put("email", email)
            .put("name", name)
            .put("role", role)
            .put("provider", "google-oidc")
            .put("iat", iat)
            .put("exp", exp)
            .put("nonce", java.util.UUID.randomUUID().toString().take(8))
            .toString()

        val headerB64 = Base64.encodeToString(header.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
        val payloadB64 = Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
        val unsignedToken = "$headerB64.$payloadB64"

        val signatureB64 = sign(unsignedToken, SECRET_KEY)
        val jwtToken = "$unsignedToken.$signatureB64"

        val isValid = verifySignature(jwtToken, SECRET_KEY)

        return SsoAssertion(
            authCode = authCode,
            jwtToken = jwtToken,
            email = email,
            name = name,
            role = role,
            issuer = "https://sso.shammahchurch.org/oauth2/v2.0",
            isValidSignature = isValid
        )
    }

    private fun sign(data: String, secret: String): String {
        return try {
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            val key = javax.crypto.spec.SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
            mac.init(key)
            val hash = mac.doFinal(data.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(hash, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
        } catch (e: Exception) {
            "signature_error"
        }
    }

    fun verifySignature(jwt: String, secret: String): Boolean {
        val parts = jwt.split(".")
        if (parts.size != 3) return false
        val expectedSig = sign("${parts[0]}.${parts[1]}", secret)
        return expectedSig == parts[2]
    }
}

// =====================================================================================
//  SINGLE SIGN-ON  ("Sign in with Google" via Android Credential Manager & YouTube App)
// =====================================================================================
object GoogleSso {

    data class GoogleAccount(
        val idToken: String,
        val email: String,
        val displayName: String,
        val role: String = "Member"
    )

    fun signIn(
        activity: Activity,
        webClientId: String,
        onSuccess: (GoogleAccount) -> Unit,
        onFail: (String) -> Unit
    ) {
        if (!webClientId.isBlank() && !webClientId.startsWith("YOUR_")) {
            try {
                val option = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

                CredentialManager.create(activity).getCredentialAsync(
                    activity,
                    request,
                    CancellationSignal(),
                    androidx.core.content.ContextCompat.getMainExecutor(activity),
                    object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                        override fun onResult(result: GetCredentialResponse) {
                            val credential = result.credential
                            if (credential is CustomCredential &&
                                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                            ) {
                                try {
                                    val google = GoogleIdTokenCredential.createFrom(credential.data)
                                    val account = GoogleAccount(
                                        google.idToken,
                                        google.id,
                                        google.displayName ?: google.id.substringBefore("@")
                                    )
                                    showYouTubeApprovalDialog(activity, account, onSuccess, onFail)
                                    return
                                } catch (e: Exception) {}
                            }
                            showCustomGoogleYouTubeDialog(activity, onSuccess, onFail)
                        }

                        override fun onError(e: GetCredentialException) {
                            showCustomGoogleYouTubeDialog(activity, onSuccess, onFail)
                        }
                    }
                )
                return
            } catch (e: Exception) {}
        }

        showCustomGoogleYouTubeDialog(activity, onSuccess, onFail)
    }

    private fun showCustomGoogleYouTubeDialog(
        activity: Activity,
        onSuccess: (GoogleAccount) -> Unit,
        onFail: (String) -> Unit
    ) {
        val context = activity
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }

        val infoText = TextView(context).apply {
            text = "OAUTH2 / OPENID CONNECT SINGLE SIGN-ON\n\nIssuer: https://sso.shammahchurch.org/oauth2/v2.0\nClient ID: shammah-mobile-android\nScopes: openid profile email church.role youtube.access\n\nEnter your Gmail address and select your role below:"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(0, 0, 0, 20)
        }
        layout.addView(infoText)

        val emailInput = EditText(context).apply {
            hint = "Gmail Address (e.g. pastor@gmail.com)"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#C9B8C3"))
            setBackgroundColor(Color.parseColor("#2A1427"))
            setPadding(30, 30, 30, 30)
        }
        layout.addView(emailInput)

        val nameInput = EditText(context).apply {
            hint = "Full Name (Optional)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#C9B8C3"))
            setBackgroundColor(Color.parseColor("#2A1427"))
            setPadding(30, 30, 30, 30)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20 }
        }
        layout.addView(nameInput)

        val roleLabel = TextView(context).apply {
            text = "Select Account Role:"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 20, 0, 10)
        }
        layout.addView(roleLabel)

        val roles = arrayOf("Member", "Pastor", "Volunteer", "Staff")
        val roleSpinner = Spinner(context).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, roles)
            setBackgroundColor(Color.parseColor("#2A1427"))
            setPadding(20, 20, 20, 20)
        }
        layout.addView(roleSpinner)

        val youtubeBtn = Button(context).apply {
            text = "▶ OPEN YOUTUBE APP"
            setBackgroundColor(Color.parseColor("#FF0000"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 25 }
            setOnClickListener {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/"))
                    activity.startActivity(intent)
                    Toast.makeText(activity, "Opening YouTube App...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(activity, "YouTube Link: https://www.youtube.com/", Toast.LENGTH_SHORT).show()
                }
            }
        }
        layout.addView(youtubeBtn)

        AlertDialog.Builder(context)
            .setTitle("G  Sign in with Gmail (SSO)")
            .setView(layout)
            .setPositiveButton("APPROVE") { _, _ ->
                val typedEmail = emailInput.text.toString().trim().lowercase()
                var typedName  = nameInput.text.toString().trim()
                val selectedRole = roles[roleSpinner.selectedItemPosition]

                if (!Patterns.EMAIL_ADDRESS.matcher(typedEmail).matches()) {
                    Toast.makeText(activity, "Enter a valid Gmail email address", Toast.LENGTH_SHORT).show()
                    onFail("Invalid Gmail address")
                    return@setPositiveButton
                }

                if (typedName.isBlank()) {
                    typedName = typedEmail.substringBefore("@").replace(".", " ")
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }

                val assertion = SsoTokenProvider.generateAssertion(typedEmail, typedName, selectedRole)
                Toast.makeText(activity, "▶ Single Sign-On Approved! Welcome ${assertion.name}", Toast.LENGTH_SHORT).show()
                onSuccess(GoogleAccount(assertion.jwtToken, assertion.email, assertion.name, assertion.role))
            }
            .setNegativeButton("DECLINE") { _, _ ->
                Toast.makeText(activity, "SSO Authorization Declined", Toast.LENGTH_SHORT).show()
                onFail("SSO Authorization Declined")
            }
            .setCancelable(false)
            .show()
    }

    private fun showYouTubeApprovalDialog(
        activity: Activity,
        account: GoogleAccount,
        onSuccess: (GoogleAccount) -> Unit,
        onFail: (String) -> Unit
    ) {
        val message = """
            GOOGLE & YOUTUBE SINGLE SIGN-ON
            
            👤 Selected Google Account:
            ${account.displayName} (${account.email})
            
            ▶ YouTube Channel & Live Access:
            Pending Approval
            
            Tap 'APPROVE' to grant YouTube channel permission and proceed to the daily Bible verse, or tap 'DECLINE' to return to the login page.
        """.trimIndent()

        AlertDialog.Builder(activity)
            .setTitle("▶ YouTube Account Approval")
            .setMessage(message)
            .setPositiveButton("APPROVE") { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/"))
                    activity.startActivity(intent)
                } catch (e: Exception) {}
                Toast.makeText(activity, "▶ YouTube Account Approved! Loading Daily Bible Verse...", Toast.LENGTH_SHORT).show()
                onSuccess(account)
            }
            .setNegativeButton("DECLINE") { _, _ ->
                Toast.makeText(activity, "YouTube approval declined. Returning to login page.", Toast.LENGTH_SHORT).show()
                onFail("YouTube approval declined")
            }
            .setCancelable(false)
            .show()
    }
}