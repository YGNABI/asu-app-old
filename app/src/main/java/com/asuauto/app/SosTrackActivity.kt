package com.asuauto.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SosTrackActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var resultWebView: WebView
    private lateinit var progressLayout: LinearLayout
    private lateinit var statusText: TextView

    private var sosType = "REQUEST"
    private var pendingAction: String? = null // "sendinfo" | "withdraw"

    private val TRACK_JS = """
        (function() {
            function txt(sel){ var e=document.querySelector(sel); return e? e.textContent.trim() : ''; }
            var cat = txt('#P29_DCS_CODE_DISPLAY') || txt('#P29_CCD_CODE_DISPLAY');
            var date = txt('#P29_SUBMIT_DATE_DISPLAY') || txt('#P29_COMPAINT_DATE_DISPLAY');
            var dscp = txt('#P29_DSCP_CLEARLY_DISPLAY') || txt('#P29_COMPLAINT_DSCP_DISPLAY');
            var remarks = txt('#P29_REMARKS_DISPLAY');

            var responses = [];
            document.querySelectorAll('#report_table_R726416070985197091 tbody tr, table[aria-label*="الإستجابات"] tbody tr').forEach(function(tr){
                var cells = []; tr.querySelectorAll('td').forEach(function(td){cells.push(td.textContent.trim());});
                if (cells.length>0) responses.push(cells);
            });

            var noAttach = document.querySelector('.nodatafound') ? true : false;

            function findBtnUrl(label){
                var btns = document.querySelectorAll('button');
                for (var i=0;i<btns.length;i++){
                    if (btns[i].textContent.indexOf(label) !== -1){
                        var onclickAttr = btns[i].getAttribute('onclick') || '';
                        var m = onclickAttr.match(/url=([^&]+)/);
                        if (m) return decodeURIComponent(decodeURIComponent(m[1]));
                    }
                }
                return '';
            }
            var sendInfoUrl = findBtnUrl('إرسال البيانات');
            var attachUrl = findBtnUrl('تحميل المرفقات');
            var withdrawUrl = findBtnUrl('سحب الطلب');

            return JSON.stringify({
                cat: cat, date: date, dscp: dscp, remarks: remarks,
                responses: responses, noAttach: noAttach,
                sendInfoUrl: sendInfoUrl, attachUrl: attachUrl, withdrawUrl: withdrawUrl
            });
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_course_materials)

        sosType = intent.getStringExtra("sosType") ?: "REQUEST"

        progressLayout = findViewById(R.id.progressLayout)
        statusText = findViewById(R.id.statusText)
        webView = findViewById(R.id.scrapeWebView)
        resultWebView = findViewById(R.id.resultWebView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        resultWebView.settings.javaScriptEnabled = true
        resultWebView.addJavascriptInterface(TrackBridge(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                pendingAction = null
                view.evaluateJavascript(TRACK_JS) { json -> renderTrack(json) }
            }
        }

        val startUrl = intent.getStringExtra("trackUrl")
        if (!startUrl.isNullOrBlank()) {
            webView.loadUrl(startUrl)
        } else {
            statusText.text = SosHtmlBuilder.t("تعذر فتح صفحة المتابعة", "Could not open tracking page")
        }
    }

    private fun renderTrack(rawJson: String?) {
        runOnUiThread {
            SosHtmlBuilder.LANG = getSharedPreferences("asu_prefs", MODE_PRIVATE).getString("lang", "ar") ?: "ar"
            val html = SosHtmlBuilder.buildTrack(rawJson ?: "{}")
            resultWebView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            progressLayout.visibility = View.GONE
            resultWebView.visibility = View.VISIBLE
        }
    }

    inner class TrackBridge {
        @JavascriptInterface
        fun sendInfo(url: String, message: String) {
            if (url.isBlank()) return
            runOnUiThread {
                pendingAction = "sendinfo"
                progressLayout.visibility = View.VISIBLE
                resultWebView.visibility = View.GONE
                webView.loadUrl(url)
                webView.postDelayed({
                    webView.evaluateJavascript(
                        "var t=document.querySelector('#RESPONSE_DSCP'); if(t) t.value=${jsStr(message)};", null
                    )
                    webView.evaluateJavascript(
                        "var b=document.querySelector('button[onclick*=\"CREATE\"]'); if(b) b.click();", null
                    )
                }, 800)
            }
        }

        @JavascriptInterface
        fun withdraw(url: String, reason: String) {
            if (url.isBlank()) return
            runOnUiThread {
                pendingAction = "withdraw"
                progressLayout.visibility = View.VISIBLE
                resultWebView.visibility = View.GONE
                webView.loadUrl(url)
                webView.postDelayed({
                    webView.evaluateJavascript(
                        "var t=document.querySelector('#P2_QUIT'); if(t) t.value=${jsStr(reason)};", null
                    )
                    webView.evaluateJavascript(
                        "var b=document.querySelector('button[onclick*=\"Yes_Please\"]'); if(b) b.click();", null
                    )
                }, 800)
            }
        }

        @JavascriptInterface
        fun openAttachUrl(url: String) {
            runOnUiThread {
                Toast.makeText(
                    this@SosTrackActivity,
                    SosHtmlBuilder.t("افتح المرفقات من المتصفح لرفع ملف", "Open attachments in browser to upload a file"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun jsStr(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}
