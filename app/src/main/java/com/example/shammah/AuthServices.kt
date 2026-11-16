package com.example.shammah

import android.app.Activity
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

// =====================================================================================
//  SIGN-IN HELPERS
//  Codes are now sent by the church server (website api/), not Firebase:
//    - the 6-digit code goes to the member's EMAIL (Gmail SMTP) and PHONE (BulkSMS SMS)
//    - the same account and password work on the website
//  See SETUP_SHARED_DATABASE.md.
// =====================================================================================

// =====================================================================================
//  PHONE NUMBERS  (SMS needs international E.164 format, e.g. +27821234567)
// =====================================================================================
object PhoneFormat {

    /** 0821234567 -> +27821234567, 0027... -> +27..., +27... unchanged. Null if invalid. */
    fun toE164(raw: String, defaultCountryCode: String = AppConfig.DEFAULT_COUNTRY_CODE): String? {
        val cleaned = raw.trim().replace(Regex("[\\s()\\-]"), "")
        val e164 = when {
            cleaned.startsWith("+") -> cleaned
            cleaned.startsWith("00") -> "+" + cleaned.drop(2)
            cleaned.startsWith("0") -> "+$defaultCountryCode" + cleaned.drop(1)
            else -> "+$cleaned"
        }
        return if (Regex("^\\+[1-9]\\d{7,14}$").matches(e164)) e164 else null
    }
}

// =====================================================================================
//  SINGLE SIGN-ON  ("Sign in with Google" via Android Credential Manager & YouTube)
// =====================================================================================
object GoogleSso {

    /**
     * idToken is a real Google ID token (checked by the server in api/google_login.php),
     * or blank when the person typed their Gmail address in the fallback dialog — then the
     * server signs them in with a code sent to that email and their phone (api/code_login.php).
     * The role is never chosen here: it always comes from the church database.
     */
    data class GoogleAccount(
        val idToken: String,
        val email: String,
        val displayName: String
    )

    fun signIn(
        activity: Activity,
        onSuccess: (GoogleAccount) -> Unit,
        onFail: (String) -> Unit
    ) {
        val webClientId = AppConfig.GOOGLE_WEB_CLIENT_ID
        if (webClientId.isNotBlank() && !webClientId.startsWith("YOUR_")) {
            try {
                val option = GetSignInWithGoogleOption.Builder(webClientId).build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

                CredentialManager.create(activity).getCredentialAsync(
                    activity,
                    request,
                    CancellationSignal(),
                    ContextCompat.getMainExecutor(activity),
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
                                        google.id.lowercase(),
                                        google.displayName ?: google.id.substringBefore("@")
                                    )
                                    onSuccess(account)
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
        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }

        val infoText = android.widget.TextView(context).apply {
            text = "Type the Gmail address on your Shammah account.\n\nWe'll send a 6-digit code to that email and to your phone. Tap 'SEND CODE' to continue, or 'CANCEL' to go back."
            setTextColor(android.graphics.Color.WHITE)
            textSize = 14f
            setPadding(0, 0, 0, 20)
        }
        layout.addView(infoText)

        val emailInput = android.widget.EditText(context).apply {
            hint = "Gmail Address (e.g. pastor@gmail.com)"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.parseColor("#C9B8C3"))
            setBackgroundColor(android.graphics.Color.parseColor("#2A1427"))
            setPadding(30, 30, 30, 30)
        }
        layout.addView(emailInput)

        val nameInput = android.widget.EditText(context).apply {
            visibility = android.view.View.GONE        // the name comes from the church database
            hint = "Full Name (Optional)"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.parseColor("#C9B8C3"))
            setBackgroundColor(android.graphics.Color.parseColor("#2A1427"))
            setPadding(30, 30, 30, 30)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20 }
        }
        layout.addView(nameInput)

        val youtubeBtn = android.widget.Button(context).apply {
            text = "▶ OPEN YOUTUBE APP"
            setBackgroundColor(android.graphics.Color.parseColor("#FF0000"))
            setTextColor(android.graphics.Color.WHITE)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 25 }
            setOnClickListener {
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/"))
                    activity.startActivity(intent)
                    android.widget.Toast.makeText(activity, "Opening YouTube App...", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(activity, "YouTube Link: https://www.youtube.com/", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        layout.addView(youtubeBtn)

        android.app.AlertDialog.Builder(context)
            .setTitle("G  Sign in with Gmail (SSO)")
            .setView(layout)
            .setPositiveButton("SEND CODE") { _, _ ->
                val typedEmail = emailInput.text.toString().trim().lowercase()
                var typedName  = nameInput.text.toString().trim()

                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(typedEmail).matches()) {
                    android.widget.Toast.makeText(activity, "Enter a valid Gmail email address", android.widget.Toast.LENGTH_SHORT).show()
                    onFail("Invalid Gmail address")
                    return@setPositiveButton
                }

                if (typedName.isBlank()) {
                    typedName = typedEmail.substringBefore("@").replace(".", " ")
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }

                onSuccess(GoogleAccount("", typedEmail, typedName))
            }
            .setNegativeButton("CANCEL") { _, _ ->
                onFail("Sign in cancelled")
            }
            .setCancelable(false)
            .show()
    }

    fun signOut(activity: Activity) {
        try {
            CredentialManager.create(activity).clearCredentialStateAsync(
                ClearCredentialStateRequest(),
                CancellationSignal(),
                ContextCompat.getMainExecutor(activity),
                object : CredentialManagerCallback<Void?, ClearCredentialException> {
                    override fun onResult(result: Void?) {}
                    override fun onError(e: ClearCredentialException) {}
                }
            )
        } catch (e: Exception) {}
    }
}
