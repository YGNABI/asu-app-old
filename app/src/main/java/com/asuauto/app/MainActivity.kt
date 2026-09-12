package com.asuauto.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val PREFS = "asu_prefs"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val setupLayout = findViewById<LinearLayout>(R.id.setupLayout)
        val usernameInput = findViewById<EditText>(R.id.usernameInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val saveButton = findViewById<Button>(R.id.saveButton)

        val savedUser = prefs.getString("username", null)
        val savedPass = prefs.getString("password", null)
        if (savedUser != null && savedPass != null) {
            setupLayout.visibility = View.GONE
        }

        saveButton.setOnClickListener {
            prefs.edit()
                .putString("username", usernameInput.text.toString())
                .putString("password", passwordInput.text.toString())
                .apply()
            setupLayout.visibility = View.GONE
        }

        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
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
                view?.evaluateJavascript(js, null)
            }
        }

        findViewById<Button>(R.id.btnSis).setOnClickListener {
            webView.loadUrl("https://sis.asu.edu.bh/ords/f?p=101:1")
        }
        findViewById<Button>(R.id.btnElearning).setOnClickListener {
            webView.loadUrl("https://elearning.asu.edu.bh/login/index.php")
        }
        findViewById<Button>(R.id.btnSos).setOnClickListener {
            webView.loadUrl("https://sos.asu.edu.bh/ords/r/asudss/sos/1")
        }

        findViewById<Button>(R.id.btnSisDashboard).setOnClickListener {
            startActivity(Intent(this, SisDashboardActivity::class.java))
        }
    }
}
