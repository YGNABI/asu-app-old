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

class SosCreateActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var resultWebView: WebView
    private lateinit var progressLayout: LinearLayout
    private lateinit var statusText: TextView

    private var sosType = "REQUEST"
    private var pendingSubmit = false

    private fun fieldPrefix() = when (sosType) {
        "REQUEST" -> "P5"
        "SUGGESTION" -> "P51"
        else -> "P9"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_course_materials)

        sosType = intent.getStringExtra("sosType") ?: "REQUEST"
        val createUrl = intent.getStringExtra("createUrl") ?: return

        progressLayout = findViewById(R.id.progressLayout)
        statusText = findViewById(R.id.statusText)
        webView = findViewById(R.id.scrapeWebView)
        resultWebView = findViewById(R.id.resultWebView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        resultWebView.settings.javaScriptEnabled = true
        resultWebView.addJavascriptInterface(CreateBridge(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)

                if (pendingSubmit) {
                    pendingSubmit = false
                    val landedUrl = url ?: ""
                    if (landedUrl.contains("/sos/29")) {
                        runOnUiThread {
                            val trackIntent = Intent(this@SosCreateActivity, SosTrackActivity::class.java)
                            trackIntent.putExtra("sosType", sosType)
                            trackIntent.putExtra("trackUrl", landedUrl)
                            startActivity(trackIntent)
                            finish()
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(
                                this@SosCreateActivity,
                                SosHtmlBuilder.t(
                                    "تأكد من تعبئة الحقول المطلوبة",
                                    "Please fill all required fields"
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                            progressLayout.visibility = View.GONE
                            resultWebView.visibility = View.VISIBLE
                        }
                    }
                    return
                }
                showForm()
            }
        }
        webView.loadUrl(createUrl)
    }

    private fun showForm() {
        runOnUiThread {
            SosHtmlBuilder.LANG = getSharedPreferences("asu_prefs", MODE_PRIVATE).getString("lang", "ar") ?: "ar"
            val html = SosHtmlBuilder.buildCreateForm(sosType)
            resultWebView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            progressLayout.visibility = View.GONE
            resultWebView.visibility = View.VISIBLE
        }
    }

    inner class CreateBridge {
        @JavascriptInterface
        fun submitForm(contactNo: String, dscp: String, remarks: String, witnesses: String) {
            val p = fieldPrefix()
            val js = StringBuilder()
            if (sosType == "COMPLAINT") {
                js.append(
                    """
                    (function(){
                        var c = document.querySelector('#${p}_CONTACT_NO'); if(c){c.value=${jsStr(contactNo)};}
                        var w = document.querySelector('#${p}_WITNESSES'); if(w){w.value=${jsStr(witnesses)};}
                        var d = document.querySelector('#${p}_COMPLAINT_DSCP'); if(d){d.value=${jsStr(dscp)};}
                        var r = document.querySelector('#${p}_REMARKS'); if(r){r.value=${jsStr(remarks)};}
                    })();
                    """.trimIndent()
                )
            } else {
                js.append(
                    """
                    (function(){
                        var c = document.querySelector('#${p}_CONTACT_NO'); if(c){c.value=${jsStr(contactNo)};}
                        var d = document.querySelector('#${p}_DSCP_CLEARLY'); if(d){d.value=${jsStr(dscp)};}
                        var r = document.querySelector('#${p}_REMARKS'); if(r){r.value=${jsStr(remarks)};}
                    })();
                    """.trimIndent()
                )
            }
            runOnUiThread {
                webView.evaluateJavascript(js.toString(), null)
                webView.evaluateJavascript(
                    "var b=document.querySelector('button[onclick*=\"CREATE\"]'); if(b) b.click();", null
                )
                pendingSubmit = true
                progressLayout.visibility = View.VISIBLE
                resultWebView.visibility = View.GONE
            }
        }
    }

    private fun jsStr(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}
