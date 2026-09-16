package com.asuauto.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray

class SosCategoryActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var resultWebView: WebView
    private lateinit var progressLayout: LinearLayout
    private lateinit var statusText: TextView

    private var sosType = "REQUEST"
    private var specialFlag = false
    private var loginAttempted = false

    private val LIST_URLS = mapOf(
        "REQUEST" to "https://sos.asu.edu.bh/ords/r/asudss/sos/4",
        "SUGGESTION" to "https://sos.asu.edu.bh/ords/r/asudss/sos/50",
        "COMPLAINT" to "https://sos.asu.edu.bh/ords/r/asudss/sos/8"
    )

    private val LOGIN_CHECK_JS = """
        document.querySelector('#P101_USERNAME') ? 'true' : 'false';
    """.trimIndent()

    private val CARDS_JS = """
        (function() {
            var out = [];
            var cards = document.querySelectorAll('.t-Card-wrap, .t-Cards-item, a.t-Card');
            if (cards.length > 0) {
                cards.forEach(function(a) {
                    var title = a.querySelector('.t-Card-title');
                    var link = a.tagName === 'A' ? a.href : (a.querySelector('a') ? a.querySelector('a').href : '');
                    if (title && link) {
                        out.push({ label: title.textContent.trim(), href: link });
                    }
                });
            } else {
                document.querySelectorAll('.t-MediaList-item a, table.t-Report-report a').forEach(function(a) {
                    if (a.textContent.trim()) {
                        out.push({ label: a.textContent.trim(), href: a.href });
                    }
                });
            }
            return JSON.stringify(out);
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_course_materials)

        sosType = intent.getStringExtra("sosType") ?: "REQUEST"
        specialFlag = intent.getBooleanExtra("specialFlag", false)

        progressLayout = findViewById(R.id.progressLayout)
        statusText = findViewById(R.id.statusText)
        webView = findViewById(R.id.scrapeWebView)
        resultWebView = findViewById(R.id.resultWebView)

        statusText.text = when (sosType) {
            "REQUEST" -> "جاري جلب الطلبات..."
            "SUGGESTION" -> "جاري جلب الاقتراحات..."
            else -> "جاري جلب الشكاوى..."
        }

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        resultWebView.settings.javaScriptEnabled = true
        resultWebView.addJavascriptInterface(SosBridge(), "AndroidBridge")

        var baseUrl = LIST_URLS[sosType] ?: LIST_URLS["REQUEST"]!!
        if (specialFlag) {
            val flagParam = when (sosType) {
                "REQUEST" -> "p4_speical=Y"
                "SUGGESTION" -> "p50_speical=Y"
                "COMPLAINT" -> "p8_speical=Y"
                else -> ""
            }
            baseUrl = "$baseUrl?$flagParam"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)

                view.evaluateJavascript(LOGIN_CHECK_JS) { isLoginPage ->
                    val isLogin = isLoginPage?.replace("\"", "") == "true"
                    
                    if (isLogin) {
                        if (loginAttempted) {
                            runOnUiThread {
                                Toast.makeText(
                                    this@SosCategoryActivity,
                                    SosHtmlBuilder.t(
                                        "تعذر تسجيل الدخول لنظام خدمات الطلاب",
                                        "Could not log in to Student Services"
                                    ),
                                    Toast.LENGTH_LONG
                                ).show()
                                finish()
                            }
                            return@evaluateJavascript
                        }
                        loginAttempted = true
                        val prefs = getSharedPreferences("asu_prefs", MODE_PRIVATE)
                        val user = prefs.getString("username", "") ?: ""
                        val pass = prefs.getString("password", "") ?: ""
                        
                        val loginJs = """
                            (function() {
                                var u = document.querySelector('#P101_USERNAME');
                                var p = document.querySelector('#P101_PASSWORD');
                                if (!u || !p) return;
                                u.value = "$user";
                                p.value = "$pass";
                                u.dispatchEvent(new Event('input', {bubbles:true}));
                                p.dispatchEvent(new Event('input', {bubbles:true}));
                                
                                var btn = document.querySelector('button[type="submit"], input[type="submit"], .t-Button');
                                if(btn) { 
                                    setTimeout(function(){ btn.click(); }, 400); 
                                }
                            })();
                        """.trimIndent()
                        view.evaluateJavascript(loginJs, null)
                    } else {
                        view.evaluateJavascript(CARDS_JS) { rawJson ->
                            val cards = parseCards(rawJson)
                            showCategories(cards)
                        }
                    }
                }
            }
        }
        webView.loadUrl(baseUrl)
    }

    private fun parseCards(rawJson: String?): List<SosCard> {
        if (rawJson.isNullOrBlank() || rawJson == "null") return emptyList()
        return try {
            var unescaped = rawJson
            if (rawJson.startsWith("\"") && rawJson.endsWith("\"")) {
                unescaped = org.json.JSONTokener(rawJson).nextValue() as String
            }
            val arr = JSONArray(unescaped)
            val list = mutableListOf<SosCard>()
            val seenLabels = mutableSetOf<String>()
            
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val label = o.optString("label").trim()
                val href = o.optString("href")
                
                if (label.isNotBlank() && !seenLabels.contains(label)) {
                    seenLabels.add(label)
                    list.add(SosCard(label, href))
                }
            }
            list
        } catch (e: Exception) { emptyList() }
    }

    private fun showCategories(cards: List<SosCard>) {
        runOnUiThread {
            SosHtmlBuilder.LANG = getSharedPreferences("asu_prefs", MODE_PRIVATE).getString("lang", "ar") ?: "ar"
            val title = when (sosType) {
                "REQUEST" -> SosHtmlBuilder.t("طلب", "Request")
                "SUGGESTION" -> SosHtmlBuilder.t("اقتراح", "Suggestion")
                else -> SosHtmlBuilder.t("شكوى", "Complaint")
            }
            val html = SosHtmlBuilder.buildCategoryList(title, cards)
            resultWebView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            
            progressLayout.visibility = View.GONE
            resultWebView.visibility = View.VISIBLE
        }
    }

    inner class SosBridge {
        @JavascriptInterface
        fun openCategory(href: String) {
            runOnUiThread {
                val intent = Intent(this@SosCategoryActivity, SosCreateActivity::class.java)
                intent.putExtra("sosType", sosType)
                intent.putExtra("createUrl", href)
                startActivity(intent)
            }
        }
    }
}
