package com.example.shammah

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

// =====================================================================================
//  SHARED DATABASE CONNECTION
//  The app no longer keeps its own accounts. Every sign-in, event, donation and prayer
//  goes to the website's api/ folder, which reads and writes the SAME MySQL database
//  (shammah_db) as the website. An account made in the app works on the website and
//  the other way round.
//
//  Each call runs on a background thread and the answer comes back on the main thread:
//      ApiClient.post("login.php", JSONObject().put("login", email).put("password", pw)) { r ->
//          if (r.ok) ... else toast(r.msg)
//      }
//
//  Logging: every request is logged under the tag "ShammahApi" (method, path, attempt and HTTP
//  status). Passwords, codes and the sign-in token are NEVER written to the log.
//  View it in Android Studio: Logcat -> filter "Shammah".
//
//  References:
//  Android Developers. Log (android.util). https://developer.android.com/reference/android/util/Log
//  Android Developers. HttpURLConnection. https://developer.android.com/reference/java/net/HttpURLConnection
// =====================================================================================
object ApiClient {

    private const val TAG = "ShammahApi"

    class Result(val ok: Boolean, val status: Int, val json: JSONObject, val msg: String)

    /** Sign-in token from verify_code.php. Sent with every call. */
    var token: String = ""

    /** Server address. Starts as AppConfig.API_BASE_URL; can be changed in the app (ServerConfig). */
    var baseUrl: String = AppConfig.API_BASE_URL

    private val executor = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    fun get(path: String, callback: (Result) -> Unit) = call("GET", path, null, callback)

    fun post(path: String, body: JSONObject, callback: (Result) -> Unit) = call("POST", path, body, callback)

    private fun call(method: String, path: String, body: JSONObject?, callback: (Result) -> Unit) {
        executor.execute {
            val url = baseUrl.trimEnd('/') + "/" + path
            var result: Result? = null
            // GETs are tried up to 3 times (phones often drop the first request after waking up).
            // POSTs are sent once, so a donation or RSVP can never be recorded twice.
            for (attempt in 1..3) {
                Log.d(TAG, "$method $path (attempt $attempt of 3)")
                result = try {
                    request(method, url, body)
                } catch (e: Exception) {
                    // Network errors (no Wi-Fi, server off, timeout) end up here.
                    Log.w(TAG, "$method $path attempt $attempt failed: ${e.javaClass.simpleName}")
                    Result(false, 0, JSONObject(),
                        "No connection to the church right now. Please check your internet and try again.")
                }
                if (result.status != 0 || method != "GET") break
                if (attempt < 3) Thread.sleep(1200L * attempt)
            }
            // Saved address didn't answer: try the PC's other addresses and keep the one that works.
            if (result!!.status == 0) {
                Log.w(TAG, "Saved server address did not answer; trying the fallback addresses")
                for (alt in AppConfig.FALLBACK_URLS + listOfNotNull(ServerConfig.tunnelUrl())) {
                    if (alt.trimEnd('/') == baseUrl.trimEnd('/')) continue
                    val probe = try { request("GET", alt.trimEnd('/') + "/index.php", null, 4000) } catch (e: Exception) { null }
                    if (probe != null && probe.ok) {
                        Log.i(TAG, "Switched to fallback server address")
                        baseUrl = alt
                        ServerConfig.remember(alt)
                        result = try {
                            request(method, alt.trimEnd('/') + "/" + path, body)
                        } catch (e: Exception) { result }
                        break
                    }
                }
            }
            val done = result!!
            Log.i(TAG, "$method $path finished: HTTP ${done.status}, ok=${done.ok}")
            main.post { callback(done) }
        }
    }

    private fun request(method: String, url: String, body: JSONObject?, connectMs: Int = 10000): Result {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = connectMs
            conn.readTimeout = 20000           // sending email + SMS can take a few seconds
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("ngrok-skip-browser-warning", "1") // free ngrok tunnel: skip its warning page
            if (token.isNotBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("X-Auth-Token", token)   // some Apache setups drop Authorization
            }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                Log.w(TAG, "Reply was not JSON (HTTP $code)")
                if (text.contains("ERR_NGROK") || text.contains("ngrok", ignoreCase = true)) {
                    // The tunnel address answered but the PC side is off.
                    return Result(false, 0, JSONObject(),
                        "The church PC is offline. On the PC: start Apache and MySQL in XAMPP, " +
                        "then run START_SHAMMAH_ONLINE.bat.")
                }
                JSONObject().put("ok", false).put("msg", "The server sent an unexpected reply (HTTP $code).")
            }
            return Result(json.optBoolean("ok", false), code, json, json.optString("msg", ""))
        } finally {
            conn.disconnect()
        }
    }
}

// =====================================================================================
//  SERVER ADDRESS (so a real phone can point at your PC without rebuilding the app)
//  e.g. http://192.168.1.50/shammah-urban-church/api/
// =====================================================================================
object ServerConfig {
    private const val PREFS = "shammah_server"

    fun load(context: Context) {
        appContext = context.applicationContext
        DataCache.init(context)
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("url", "").orEmpty()
        ApiClient.baseUrl = saved.ifBlank { AppConfig.API_BASE_URL }
    }

    private var appContext: Context? = null

    /** The ngrok address the user typed once, kept so it can be tried again later. */
    fun tunnelUrl(): String? = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        ?.getString("tunnel", null)

    /** Saves an address that just worked (used by the automatic fallback). */
    fun remember(url: String) {
        Log.i("ShammahServer", "Saving the server address that just worked")
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString("url", url)?.apply()
    }

    /** Accepts "192.168.1.50", "abc.ngrok-free.app" or a full URL and turns it into the api/ address. */
    fun save(context: Context, input: String): String {
        var url = input.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            val host = url.substringBefore('/').substringBefore(':')
            val local = host == "localhost" || host.all { it.isDigit() || it == '.' }
            // Internet names (like an ngrok address) use https; PC/emulator addresses use http.
            url = if (local) "http://$url" else "https://$url"
        }
        url = url.trimEnd('/')
        if (!url.endsWith("/api")) {
            url = if (url.substringAfter("://").contains("/")) "$url/api" else "$url/shammah-urban-church/api"
        }
        url += "/"
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("url", url)
        if (url.startsWith("https://")) edit.putString("tunnel", url)   // remember the ngrok address too
        edit.apply()
        ApiClient.baseUrl = url
        return url
    }
}

// =====================================================================================
//  REMEMBER WHO IS SIGNED IN (so the app doesn't ask for a code every time it opens)
// =====================================================================================
object SessionStore {
    private const val PREFS = "shammah_session"

    fun save(context: Context, token: String, user: JSONObject) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("token", token).putString("user", user.toString()).apply()
    }

    fun token(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("token", "").orEmpty()

    fun clear(context: Context) {
        Log.i("ShammahSession", "Signed out: stored session cleared")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

/** The signed-in member, as returned by the website API. */
fun userFromJson(u: JSONObject) = User(
    id = u.optLong("id"),
    name = u.optString("name"),
    email = u.optString("email"),
    phone = u.optString("phone"),
    role = u.optString("app_role", "Member"),   // admin + pastor -> "Pastor" dashboard
    provider = "website"
)

// =====================================================================================
//  OFFLINE COPY of the last lists the server sent (events, groups, prayers ...).
//  Screens show this straight away, so there is never a "Loading..." wait or an error
//  screen; the server's fresh answer replaces it a moment later.
// =====================================================================================
object DataCache {
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("shammah_cache", Context.MODE_PRIVATE)
    }

    fun get(key: String): JSONObject? =
        prefs?.getString(key, null)?.let { try { JSONObject(it) } catch (e: Exception) { null } }

    fun put(key: String, json: JSONObject) {
        prefs?.edit()?.putString(key, json.toString())?.apply()
    }

    /** Titles of events the member RSVP'd to while offline; sent when the server is back. */
    fun pendingRsvps(): MutableSet<String> = prefs?.getStringSet("pending_rsvp", emptySet())?.toMutableSet() ?: mutableSetOf()

    fun savePendingRsvps(titles: Set<String>) {
        Log.i("ShammahCache", "Offline RSVP queue now holds ${titles.size} event(s)")
        prefs?.edit()?.putStringSet("pending_rsvp", titles)?.apply()
    }
}
