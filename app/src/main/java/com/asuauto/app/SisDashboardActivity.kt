package com.asuauto.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray

class SisDashboardActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var resultWebView: WebView
    private lateinit var statusText: TextView
    private lateinit var progressLayout: LinearLayout

    private var phase = "login"
    private var sessionId = ""
    private var categoryIndex = 0
    private var categoryCount = 0

    private val rawTables = HashMap<String, JSONArray>()
    private val planDetails = LinkedHashMap<Int, List<List<String>>>()
    private var categoryNames: MutableList<String> = mutableListOf()

    private fun pageUrl(page: Int) = "https://sis.asu.edu.bh/ords/f?p=2020:$page:$sessionId:::::"

    private val JS_LIB = """
        function nthWithText(txt, n) {
            var all = document.querySelectorAll('a,button,span,li,div,td');
            var matches = [];
            for (var i=0;i<all.length;i++){
                var t = (all[i].textContent||'').trim();
                if (t === txt) matches.push(all[i]);
            }
            return matches[n] || null;
        }
        function countWithText(txt) {
            var all = document.querySelectorAll('a,button,span,li,div,td');
            var c = 0;
            for (var i=0;i<all.length;i++){
                if ((all[i].textContent||'').trim() === txt) c++;
            }
            return c;
        }
        function clickNth(txt, n) {
            var el = nthWithText(txt, n);
            if (el) { el.click(); return true; }
            return false;
        }
        function scrapeAllTables() {
            var tables = document.querySelectorAll('table');
            var result = [];
            for (var ti=0; ti<tables.length; ti++) {
                var t = tables[ti];
                var rows = [];
                var trs = t.querySelectorAll('tr');
                for (var ri=0; ri<trs.length; ri++) {
                    var cells = trs[ri].querySelectorAll('th,td');
                    var rowArr = [];
                    for (var ci=0; ci<cells.length; ci++) {
                        rowArr.push(cells[ci].innerText.trim());
                    }
                    if (rowArr.length > 0) rows.push(rowArr);
                }
                if (rows.length > 0) result.push(rows);
            }
            return JSON.stringify(result);
        }
        function textAfterLabel(label) {
            var all = document.querySelectorAll('body *');
            for (var i=0;i<all.length;i++){
                var t = (all[i].textContent||'').trim();
                if (t === label) {
                    var sib = all[i].previousElementSibling;
                    if (sib) return sib.textContent.trim();
                }
            }
            return '';
        }
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sis_dashboard)

        progressLayout = findViewById(R.id.progressLayout)
        statusText = findViewById(R.id.statusText)
        webView = findViewById(R.id.scrapeWebView)
        resultWebView = findViewById(R.id.resultWebView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.addJavascriptInterface(Bridge(), "AndroidBridge")

        val prefs = getSharedPreferences("asu_prefs", MODE_PRIVATE)
        val user = prefs.getString("username", "") ?: ""
        val pass = prefs.getString("password", "") ?: ""

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
