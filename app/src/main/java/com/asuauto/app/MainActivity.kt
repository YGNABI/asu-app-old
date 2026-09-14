package com.asuauto.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val PREFS = "asu_prefs"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val setupLayout = findViewById<LinearLayout>(R.id.setupLayout)
        val buttonsLayout = findViewById<LinearLayout>(R.id.buttonsLayout)
        val btnSisDashboard = findViewById<Button>(R.id.btnSisDashboard)
        val usernameInput = findViewById<EditText>(R.id.usernameInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val togglePasswordVisibility = findViewById<ImageView>(R.id.togglePasswordVisibility)
        val biometricCheckbox = findViewById<CheckBox>(R.id.biometricCheckbox)
        val saveButton = findViewById<Button>(R.id.saveButton)
        val logoutTop = findViewById<TextView>(R.id.logoutTop)
        val langToggle = findViewById<TextView>(R.id.langToggle)
        val appTitle = findViewById<TextView>(R.id.appTitle)

        fun applyLangText() {
            val lang = prefs.getString("lang", "ar") ?: "ar"
            if (lang == "en") {
                langToggle.text = "AR"
                appTitle.text = "Applied Science University"
                usernameInput.hint = "University ID"
                passwordInput.hint = "Password"
                biometricCheckbox.text = "Enable fingerprint login"
                saveButton.text = "Save"
                btnSisDashboard.text = "Smart SIS Dashboard"
                logoutTop.text = "Logout"
            } else {
                langToggle.text = "EN"
                appTitle.text = "جامعة العلوم التطبيقية"
                usernameInput.hint = "الرقم الجامعي"
                passwordInput.hint = "كلمة السر"
                biometricCheckbox.text = "تفعيل الدخول بالبصمة"
                saveButton.text = "حفظ البيانات"
                btnSisDashboard.text = "لوحة SIS الذكية"
                logoutTop.text = "خروج"
            }
        }
        applyLangText()

        langToggle.setOnClickListener {
            val cur = prefs.getString("lang", "ar") ?: "ar"
            prefs.edit().putString("lang", if (cur == "ar") "en" else "ar").apply()
            getSharedPreferences("asu_dashboard_cache", MODE_PRIVATE).edit().clear().apply()
            applyLangText()
        }

        fun showLoggedInState() {
            setupLayout.visibility = View.GONE
            btnSisDashboard.visibility = View.VISIBLE
            buttonsLayout.visibility = View.VISIBLE
            logoutTop.visibility = View.VISIBLE
        }

        fun showSetupState() {
            setupLayout.visibility = View.VISIBLE
            btnSisDashboard.visibility = View.GONE
            buttonsLayout.visibility = View.GONE
            logoutTop.visibility = View.GONE
        }

        val savedUser = prefs.getString("username", null)
        val savedPass = prefs.getString("password", null)
        val biometricEnabled = prefs.getBoolean("biometric_enabled", false)

        if (savedUser != null && savedPass != null) {
            if (biometricEnabled && canUseBiometric()) {
                showSetupState()
                btnSisDashboard.visibility = View.GONE
                buttonsLayout.visibility = View.GONE
                promptBiometric(
                    onSuccess = { showLoggedInState() },
                    onFail = { /* stay on setup/login screen */ }
                )
            } else {
                showLoggedInState()
            }
        } else {
            showSetupState()
        }

        logoutTop.setOnClickListener {
            prefs.edit().remove("username").remove("password").remove("biometric_enabled").remove("gpa_history").apply()
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
            getSharedPreferences("asu_dashboard_cache", MODE_PRIVATE).edit().clear().apply()
            usernameInput.setText("")
            passwordInput.setText("")
            biometricCheckbox.isChecked = false
            showSetupState()
        }

        saveButton.setOnClickListener {
            prefs.edit()
                .putString("username", usernameInput.text.toString())
                .putString("password", passwordInput.text.toString())
                .putBoolean("biometric_enabled", biometricCheckbox.isChecked && canUseBiometric())
                .apply()
            showLoggedInState()
        }

        var passwordVisible = false
        togglePasswordVisibility.setOnClickListener {
            passwordVisible = !passwordVisible
            if (passwordVisible) {
                passwordInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                togglePasswordVisibility.setImageResource(android.R.drawable.ic_secure)
            } else {
                passwordInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                togglePasswordVisibility.setImageResource(android.R.drawable.ic_menu_view)
            }
            passwordInput.setSelection(passwordInput.text.length)
        }

        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        var pendingSisDashboardLaunch = false

        fun stillOnAnyLoginPage(view: WebView, done: (Boolean) -> Unit) {
            view.evaluateJavascript(
                "document.querySelector('input[type=\"password\"]') ? 'true' : 'false';"
            ) { result -> done(result == "true") }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (view == null) return
                val user = prefs.getString("username", "") ?: ""
                val pass = prefs.getString("password", "") ?: ""
                if (user.isEmpty() || pass.isEmpty()) return

                val js = """
                    (function() {
                        var userField = document.querySelector('input[name="username"]') || document.querySelector('input[type="text"]');
                        var passField = document.querySelector('input[name="password"]') || document.querySelector('input[type="password"]');
                        if (!userField || !passField) return;
                        if (passField.value) return;
                        userField.value = "$user";
                        passField.value = "$pass";
                        userField.dispatchEvent(new Event('input', {bubbles:true}));
                        passField.dispatchEvent(new Event('input', {bubbles:true}));
                        var btn = document.querySelector('#loginbtn') || document.querySelector('button[type="submit"]') || document.querySelector('input[type="submit"]');
                        if (btn) { setTimeout(function(){ btn.click(); }, 300); }
                    })();
                """.trimIndent()
                view.evaluateJavascript(js, null)

                // If this page load was the elearning warm-up we kicked off before opening
                // the SIS dashboard, wait to see whether we actually landed on the logged-in
                // dashboard (no password field) before launching the activity — this is what
                // replaces SisDashboardActivity's own separate (and unreliable — it was
                // tripping Moodle's login-attempt throttling) login attempt. One login, one
                // place, done before the dashboard ever opens.
                if (pendingSisDashboardLaunch) {
                    stillOnAnyLoginPage(view) { stillOnLogin ->
                        if (!stillOnLogin) {
                            pendingSisDashboardLaunch = false
                            startActivity(Intent(this@MainActivity, SisDashboardActivity::class.java))
                        }
                        // If still on the login page, just wait for the next onPageFinished
                        // (the submit above will trigger it) — no manual retry loop here.
                    }
                }
            }
        }

        findViewById<Button>(R.id.btnSos).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://sos.asu.edu.bh/ords/r/asudss/sos/1"))
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnSisDashboard).setOnClickListener {
            pendingSisDashboardLaunch = true
            android.widget.Toast.makeText(this, "جاري تجهيز موقع التعليم الالكتروني...", android.widget.Toast.LENGTH_SHORT).show()
            webView.loadUrl("https://elearning.asu.edu.bh/?redirect=0")
        }
    }

    private fun canUseBiometric(): Boolean {
        val manager = BiometricManager.from(this)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun promptBiometric(onSuccess: () -> Unit, onFail: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFail()
            }
            override fun onAuthenticationFailed() {
                // allow retry, do nothing
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("تسجيل الدخول بالبصمة")
            .setSubtitle("استخدم بصمتك لفتح التطبيق")
            .setNegativeButtonText("إلغاء")
            .build()
        prompt.authenticate(info)
    }
}
