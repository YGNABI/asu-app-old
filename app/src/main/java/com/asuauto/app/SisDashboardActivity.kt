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
                view.evaluateJavascript(JS_LIB, null)

                if (sessionId.isEmpty() && url != null) {
                    val m = Regex("""p=2020:\d+:(\d+)""").find(url)
                    if (m != null) sessionId = m.groupValues[1]
                }

                when (phase) {
                    "login" -> {
                        setStatus("جاري تسجيل الدخول...")
                        val js = """
                            (function(){
                                var userField = document.querySelector('input[type="text"]');
                                var passField = document.querySelector('input[type="password"]');
                                if (userField && passField && !passField.value) {
                                    userField.value = "$user";
                                    passField.value = "$pass";
                                    userField.dispatchEvent(new Event('input', {bubbles:true}));
                                    passField.dispatchEvent(new Event('input', {bubbles:true}));
                                    var btn = document.querySelector('button[type="submit"]') || document.querySelector('input[type="submit"]');
                                    if (btn) setTimeout(function(){ btn.click(); }, 300);
                                }
                            })();
                        """.trimIndent()
                        view.evaluateJavascript(js, null)
                        phase = "afterLogin"
                    }
                    "afterLogin" -> {
                        if (sessionId.isEmpty()) return
                        setStatus("جاري جلب مواعيد التسجيل...")
                        view.loadUrl(pageUrl(6))
                        phase = "regTimes"
                    }
                    "regTimes" -> {
                        view.evaluateJavascript("AndroidBridge.onScraped('regTimes', scrapeAllTables());", null)
                        setStatus("جاري جلب المواد المسجلة...")
                        view.postDelayed({ view.loadUrl(pageUrl(1)) }, 300)
                        phase = "registrationBase"
                    }
                    "registrationBase" -> {
                        view.evaluateJavascript("AndroidBridge.onScraped('studentBasic', scrapeAllTables());", null)
                        view.postDelayed({
                            view.evaluateJavascript("clickNth('المواد المسجلة للفصل الحالي', 0);", null)
                            view.postDelayed({
                                view.evaluateJavascript("AndroidBridge.onScraped('registration', scrapeAllTables());", null)
                                view.evaluateJavascript("clickNth('علامات الفصل', 0);", null)
                                view.postDelayed({
                                    view.evaluateJavascript("AndroidBridge.onScraped('semesterGrades', scrapeAllTables());", null)
                                    view.evaluateJavascript("clickNth('احصائيات الحضور والغياب', 0);", null)
                                    view.postDelayed({
                                        view.evaluateJavascript("AndroidBridge.onScraped('attendance', scrapeAllTables());", null)
                                        setStatus("جاري جلب الخطة الدراسية...")
                                        view.loadUrl(pageUrl(3))
                                    }, 900)
                                }, 900)
                            }, 900)
                        }, 300)
                        phase = "planSummary"
                    }
                    "planSummary" -> {
                        view.evaluateJavascript(
                            "AndroidBridge.onScraped('planSummaryTable', scrapeAllTables()); AndroidBridge.onCount('planCategories', countWithText('تفاصيل')); clickNth('تفاصيل', 0);",
                            null
                        )
                        phase = "planDetail"
                    }
                    "planDetail" -> {
                        setStatus("جاري جلب الخطة الدراسية (${categoryIndex + 1}/$categoryCount)...")
                        view.evaluateJavascript(
                            "AndroidBridge.onScraped('planDetail_$categoryIndex', scrapeAllTables());", null
                        )
                        categoryIndex++
                        if (categoryIndex < categoryCount) {
                            view.postDelayed({
                                view.loadUrl(pageUrl(3))
                            }, 300)
                            phase = "planSummaryReturn"
                        } else {
                            setStatus("جاري جلب كشف الدرجات...")
                            view.postDelayed({ view.loadUrl(pageUrl(8)) }, 300)
                            phase = "grades"
                        }
                    }
                    "planSummaryReturn" -> {
                        view.postDelayed({
                            view.evaluateJavascript("clickNth('تفاصيل', $categoryIndex);", null)
                        }, 300)
                        phase = "planDetail"
                    }
                    "grades" -> {
                        view.evaluateJavascript("AndroidBridge.onScraped('grades', scrapeAllTables());", null)
                        setStatus("جاري جلب الأقساط...")
                        view.postDelayed({ view.loadUrl(pageUrl(18)) }, 300)
                        phase = "payment"
                    }
                    "payment" -> {
                        view.evaluateJavascript("AndroidBridge.onScraped('payment', scrapeAllTables());", null)
                        setStatus("جاري جلب كشف حساب الطالب...")
                        view.postDelayed({ view.loadUrl(pageUrl(7)) }, 300)
                        phase = "account"
                    }
                    "account" -> {
                        view.evaluateJavascript("AndroidBridge.onScraped('account', scrapeAllTables());", null)
                        view.evaluateJavascript(
                            "AndroidBridge.onText('accountBalance', textAfterLabel('الرصيد المطلوب'));", null
                        )
                        phase = "done"
                    }
                }
            }
        }

        webView.loadUrl("https://sis.asu.edu.bh/ords/f?p=101:1")
    }

    private fun setStatus(text: String) {
        runOnUiThread { statusText.text = text }
    }

    inner class Bridge {
        @JavascriptInterface
        fun onCount(tag: String, count: Int) {
            if (tag == "planCategories") categoryCount = count
        }

        @JavascriptInterface
        fun onText(tag: String, value: String) {
            if (tag == "accountBalance") {
                DataStore.accountBalance = value
                runOnUiThread { buildAndShowDashboard() }
            }
        }

        @JavascriptInterface
        fun onScraped(tag: String, json: String) {
            try {
                val tables = JSONArray(json)
                rawTables[tag] = tables
                if (tag.startsWith("planDetail_")) {
                    val idx = tag.removePrefix("planDetail_").toInt()
                    planDetails[idx] = pickTableWithHeaderContaining(tables, "المتطلب السابق")
                }
                if (tag == "planSummaryTable") {
                    val summary = pickTableWithHeaderContaining(tables, "عنصر الخطة")
                    val names = mutableListOf<String>()
                    for (r in 1 until summary.size) {
                        val row = summary[r]
                        if (row.isNotEmpty()) names.add(row.last())
                    }
                    categoryNames = names
                }
            } catch (e: Exception) {
            }
        }
    }

    private fun pickLargestTable(tables: JSONArray): MutableList<List<String>> {
        var best: MutableList<List<String>> = mutableListOf()
        for (i in 0 until tables.length()) {
            val t = tables.getJSONArray(i)
            if (t.length() > best.size) best = jsonTableToList(t)
        }
        return best
    }

    private fun pickTableWithHeaderContaining(tables: JSONArray, needle: String): List<List<String>> {
        for (i in 0 until tables.length()) {
            val t = tables.getJSONArray(i)
            if (t.length() == 0) continue
            val header = t.getJSONArray(0)
            for (c in 0 until header.length()) {
                if (header.getString(c).contains(needle)) return jsonTableToList(t)
            }
        }
        return emptyList()
    }

    private fun jsonTableToList(t: JSONArray): MutableList<List<String>> {
        val rows = mutableListOf<List<String>>()
        for (r in 0 until t.length()) {
            val row = t.getJSONArray(r)
            val cells = mutableListOf<String>()
            for (c in 0 until row.length()) cells.add(row.getString(c))
            rows.add(cells)
        }
        return rows
    }

    private fun buildAndShowDashboard() {
        DataStore.studentBasic = rawTables["studentBasic"]?.let { pickLargestTable(it) } ?: mutableListOf()
        DataStore.regTimes = rawTables["regTimes"]?.let { pickLargestTable(it) } ?: mutableListOf()
        DataStore.registration = rawTables["registration"]?.let { pickLargestTable(it) } ?: mutableListOf()
        DataStore.semesterGrades = rawTables["semesterGrades"]?.let { pickLargestTable(it) } ?: mutableListOf()
        DataStore.attendance = rawTables["attendance"]?.let { pickLargestTable(it) } ?: mutableListOf()
        DataStore.planDetails = planDetails
        DataStore.categoryNames = categoryNames
        DataStore.grades = rawTables["grades"]?.let { pickLargestTable(it) } ?: mutableListOf()
        DataStore.gradesSummary = rawTables["grades"]?.let {
            pickTableWithHeaderContaining(it, "المعدل التراكمي")
        } ?: emptyList()
        DataStore.payment = rawTables["payment"]?.let { pickLargestTable(it) } ?: mutableListOf()
        DataStore.account = rawTables["account"]?.let { pickLargestTable(it) } ?: mutableListOf()

        val html = DashboardHtmlBuilder.build()
        resultWebView.settings.javaScriptEnabled = true
        resultWebView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        progressLayout.visibility = View.GONE
        webView.visibility = View.GONE
        resultWebView.visibility = View.VISIBLE
    }
}

object DataStore {
    var studentBasic: List<List<String>> = emptyList()
    var regTimes: List<List<String>> = emptyList()
    var registration: List<List<String>> = emptyList()
    var semesterGrades: List<List<String>> = emptyList()
    var attendance: List<List<String>> = emptyList()
    var planDetails: Map<Int, List<List<String>>> = emptyMap()
    var categoryNames: List<String> = emptyList()
    var grades: List<List<String>> = emptyList()
    var gradesSummary: List<List<String>> = emptyList()
    var payment: List<List<String>> = emptyList()
    var account: List<List<String>> = emptyList()
    var accountBalance: String = ""
}
