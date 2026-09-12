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
    private var planLinks: MutableList<String> = mutableListOf()
    private var planNames: MutableList<String> = mutableListOf()
    private var planIndex = 0

    private val rawTables = HashMap<String, JSONArray>()
    private val CACHE_PREFS = "asu_dashboard_cache"

    private fun pageUrl(page: Int) = "https://sis.asu.edu.bh/ords/f?p=2020:$page:$sessionId:::::"

    private val JS_LIB = """
        function getField(label) {
            var dts = document.querySelectorAll('dt');
            for (var i=0;i<dts.length;i++){
                if ((dts[i].textContent||'').trim() === label) {
                    var dd = dts[i].nextElementSibling;
                    if (dd && dd.tagName === 'DD') return dd.textContent.trim();
                }
            }
            return '';
        }
        function getById(id) {
            var el = document.getElementById(id);
            return el ? el.textContent.trim() : '';
        }
        function getGradExpected() {
            var lbl = document.getElementById('P1_EXCPECTED_GRAD_LABEL');
            if (!lbl) return '';
            var t = lbl.textContent.replace(/\s+/g,' ').trim();
            var idx = t.indexOf('؟');
            return idx >= 0 ? t.substring(idx+1).trim() : t;
        }
        function clickTabByLabel(label) {
            var panel = document.querySelector('[data-label="' + label + '"]');
            if (!panel || !panel.id) return false;
            var tabBtn = document.getElementById(panel.id + '_tab');
            if (tabBtn) { tabBtn.click(); return true; }
            return false;
        }
        function scrapeTableById(id) {
            var t = document.getElementById(id);
            if (!t) return '[]';
            var rows = [];
            var trs = t.querySelectorAll('tr');
            for (var ri=0; ri<trs.length; ri++) {
                var cells = trs[ri].querySelectorAll('th,td');
                var rowArr = [];
                for (var ci=0; ci<cells.length; ci++) rowArr.push(cells[ci].innerText.trim());
                if (rowArr.length > 0) rows.push(rowArr);
            }
            return JSON.stringify(rows);
        }
        function scrapeTableByLabel(labelText) {
            var tables = document.querySelectorAll('table.t-Report-report');
            for (var i=0;i<tables.length;i++){
                if ((tables[i].getAttribute('aria-label')||'').trim() === labelText) {
                    var rows = [];
                    var trs = tables[i].querySelectorAll('tr');
                    for (var ri=0; ri<trs.length; ri++) {
                        var cells = trs[ri].querySelectorAll('th,td');
                        var rowArr = [];
                        for (var ci=0; ci<cells.length; ci++) rowArr.push(cells[ci].innerText.trim());
                        if (rowArr.length > 0) rows.push(rowArr);
                    }
                    return JSON.stringify(rows);
                }
            }
            return '[]';
        }
        function collectPlanLinks() {
            var out = [];
            var rows = document.querySelectorAll('#report_table_R3711472670484039573 tbody tr');
            for (var i=0;i<rows.length;i++){
                var a = rows[i].querySelector('a');
                var cells = rows[i].querySelectorAll('td');
                if (a && cells.length > 0) {
                    out.push({
                        href: a.getAttribute('href'),
                        name: cells[0].innerText.trim(),
                        group: cells.length > 1 ? cells[1].innerText.trim() : '',
                        required: cells.length > 2 ? cells[2].innerText.trim() : '',
                        studied: cells.length > 3 ? cells[3].innerText.trim() : '',
                        passed: cells.length > 4 ? cells[4].innerText.trim() : ''
                    });
                }
            }
            return JSON.stringify(out);
        }
        function scrapePlanDetail() {
            var tbl = document.querySelector('#R3711474874649116488 table.t-Report-report');
            if (!tbl) return '[]';
            var rows = [];
            var trs = tbl.querySelectorAll('tr');
            for (var ri=0; ri<trs.length; ri++) {
                var cells = trs[ri].querySelectorAll('th,td');
                var rowArr = [];
                for (var ci=0; ci<cells.length; ci++) rowArr.push(cells[ci].innerText.trim());
                if (rowArr.length > 0) rows.push(rowArr);
            }
            return JSON.stringify(rows);
        }
        function scrapeTranscript() {
            var tbl = document.getElementById('report_table_R3755411640631850167');
            if (!tbl) return '[]';
            var rows = [];
            var trs = tbl.querySelectorAll('tr');
            for (var ri=0; ri<trs.length; ri++) {
                var tr = trs[ri];
                var brk = tr.querySelector('td.apex_report_break');
                if (brk) { rows.push(['__TERM__', brk.innerText.replace(/\s+/g,' ').trim()]); continue; }
                if (tr.querySelector('th')) continue;
                var cells = tr.querySelectorAll('td');
                var rowArr = [];
                for (var ci=0; ci<cells.length; ci++) rowArr.push(cells[ci].innerText.trim());
                if (rowArr.length > 0) rows.push(rowArr);
            }
            return JSON.stringify(rows);
        }
    """.trimIndent()

    private fun startScraping() {
        phase = "login"
        sessionId = ""
        planIndex = 0
        planLinks.clear()
        planNames.clear()
        rawTables.clear()
        DataStore.reset()

        progressLayout.visibility = View.VISIBLE
        resultWebView.visibility = View.GONE
        setStatus("جاري تسجيل الدخول...")

        webView.loadUrl("https://sis.asu.edu.bh/ords/f?p=101:1")

        webView.postDelayed({
            if (phase != "done") {
                phase = "done"
                buildAndShowDashboard()
            }
        }, 45000)
    }

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
        resultWebView.settings.javaScriptEnabled = true
        val bridge = Bridge()
        webView.addJavascriptInterface(bridge, "AndroidBridge")
        resultWebView.addJavascriptInterface(bridge, "AndroidBridge")

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
                        val js = """
                            (function(){
                                var u = document.querySelector('input[type="text"]');
                                var p = document.querySelector('input[type="password"]');
                                if (u && p) {
                                    u.value = "$user"; p.value = "$pass";
                                    u.dispatchEvent(new Event('input',{bubbles:true}));
                                    p.dispatchEvent(new Event('input',{bubbles:true}));
                                    var b = document.querySelector('button[type="submit"]') || document.querySelector('input[type="submit"]');
                                    if (!b) { var bs = document.querySelectorAll('button'); if (bs.length===1) b = bs[0]; }
                                    if (b) setTimeout(function(){ b.click(); }, 400);
                                }
                            })();
                        """.trimIndent()
                        view.evaluateJavascript(js, null)
                        phase = "afterLogin"
                    }
                    "afterLogin" -> {
                        if (sessionId.isEmpty()) return
                        setStatus("جاري جلب معلومات الطالب...")
                        view.loadUrl(pageUrl(1))
                        phase = "page1"
                    }
                    "page1" -> {
                        view.evaluateJavascript("AndroidBridge.txt('studentName', getField('Name'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('studentCollege', getField('The College'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('studentGpa', getField('المعدل التراكمي'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('studentWarning', getField('حالة الانذار الاكاديمي'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('advisor', getField('المرشد الاكاديمي'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('gradExpected', getGradExpected());", null)
                        view.evaluateJavascript("AndroidBridge.tbl('registration', scrapeTableById('report_table_currRegId'));", null)
                        view.evaluateJavascript("AndroidBridge.tbl('semesterGrades', scrapeTableById('report_table_termmarks'));", null)
                        view.evaluateJavascript("AndroidBridge.tbl('attendance', scrapeTableByLabel('احصائيات الحضور والغياب'));", null)
                        setStatus("جاري جلب مواعيد التسجيل...")
                        view.postDelayed({ view.loadUrl(pageUrl(6)) }, 200)
                        phase = "page6"
                    }
                    "page6" -> {
                        view.evaluateJavascript("AndroidBridge.txt('regStart', getField('بداية تاريخ التسجيل'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('regEnd', getField('نهاية تاريخ التسجيل'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('addDropStart', getField('بداية تاريخ السحب و الأضافه'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('addDropEnd', getField('نهاية تاريخ السحب ولأضافه'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('academicStatus', getField('الوضع الأكاديمي'));", null)
                        setStatus("جاري جلب الخطة الدراسية...")
                        view.postDelayed({ view.loadUrl(pageUrl(3)) }, 200)
                        phase = "planSummary"
                    }
                    "planSummary" -> {
                        view.evaluateJavascript("AndroidBridge.planLinks(collectPlanLinks());", null)
                        phase = "planWait"
                        view.postDelayed({ nextPlanDetail(view) }, 400)
                    }
                    "planDetail" -> {
                        view.evaluateJavascript("AndroidBridge.tbl('planDetail_$planIndex', scrapePlanDetail());", null)
                        planIndex++
                        view.postDelayed({ nextPlanDetail(view) }, 250)
                        phase = "planWait"
                    }
                    "grades" -> {
                        view.evaluateJavascript("AndroidBridge.txt('gpa', getField('المعدل التراكمي'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('totalHours', getField('مجموع الساعات التراكمية'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('passedHours', getField('الساعات التى نجح بها'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('honorList', getField('على لائحة الشرف'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('planHours', getField('ساعات الخطه الدراسية'));", null)
                        view.evaluateJavascript("AndroidBridge.tbl('transcript', scrapeTranscript());", null)
                        setStatus("جاري جلب الأقساط...")
                        view.postDelayed({ view.loadUrl(pageUrl(18)) }, 200)
                        phase = "payment"
                    }
                    "payment" -> {
                        view.evaluateJavascript("AndroidBridge.txt('inst1', getField('قيمة القسط الاول'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('inst1Paid', getField('تم دفع القسط الاول؟'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('inst2', getField('قيمة القسط الثاني'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('inst2Paid', getField('تم دفع القسط الثاني؟'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('inst3', getField('قيمة القسط الثالث'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('inst3Paid', getField('تم دفع القسط الثالث؟'));", null)
                        setStatus("جاري جلب كشف الحساب...")
                        view.postDelayed({ view.loadUrl(pageUrl(7)) }, 200)
                        phase = "account"
                    }
                    "account" -> {
                        view.evaluateJavascript("AndroidBridge.txt('balance', getById('P7_BALANCE'));", null)
                        view.evaluateJavascript("AndroidBridge.tbl('account', scrapeTableByLabel('تفاصيل كشف الحساب '));", null)
                        phase = "done"
                        view.postDelayed({ buildAndShowDashboard() }, 400)
                    }
                }
            }
        }

        val cached = getSharedPreferences(CACHE_PREFS, MODE_PRIVATE).getString("html", null)
        if (cached != null) {
            progressLayout.visibility = View.GONE
            resultWebView.visibility = View.VISIBLE
            resultWebView.loadDataWithBaseURL(null, cached, "text/html", "utf-8", null)
        } else {
            startScraping()
        }
    }

    private fun nextPlanDetail(view: WebView) {
        if (planIndex < planLinks.size) {
            setStatus("جاري جلب الخطة (${planIndex + 1}/${planLinks.size})...")
            phase = "planDetail"
            view.loadUrl("https://sis.asu.edu.bh/ords/" + planLinks[planIndex])
        } else {
            setStatus("جاري جلب كشف الدرجات...")
            phase = "grades"
            view.loadUrl(pageUrl(8))
        }
    }

    private fun setStatus(text: String) {
        runOnUiThread { statusText.text = text }
    }

    inner class Bridge {
        @JavascriptInterface
        fun txt(tag: String, value: String) {
            val v = value.trim().removeSurrounding("\"")
            DataStore.fields[tag] = if (v == "null") "" else v
        }

        @JavascriptInterface
        fun tbl(tag: String, json: String) {
            try {
                rawTables[tag] = JSONArray(json.trim().removeSurrounding("\""))
            } catch (e: Exception) {
                try { rawTables[tag] = JSONArray(json) } catch (e2: Exception) {}
            }
        }

        @JavascriptInterface
        fun planLinks(json: String) {
            try {
                val arr = JSONArray(json.trim().removeSurrounding("\""))
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    planLinks.add(o.optString("href"))
                    planNames.add(o.optString("name"))
                    DataStore.planStats.add(
                        listOf(
                            o.optString("name"),
                            o.optString("required"),
                            o.optString("studied"),
                            o.optString("passed")
                        )
                    )
                }
            } catch (e: Exception) {}
        }

        @JavascriptInterface
        fun refresh() {
            runOnUiThread { startScraping() }
        }
    }

    private fun toList(t: JSONArray?): List<List<String>> {
        if (t == null) return emptyList()
        val rows = mutableListOf<List<String>>()
        for (r in 0 until t.length()) {
            val row = t.optJSONArray(r) ?: continue
            val cells = mutableListOf<String>()
            for (c in 0 until row.length()) cells.add(row.optString(c))
            rows.add(cells)
        }
        return rows
    }

    private fun buildAndShowDashboard() {
        DataStore.registration = toList(rawTables["registration"])
        DataStore.semesterGrades = toList(rawTables["semesterGrades"])
        DataStore.attendance = toList(rawTables["attendance"])
        DataStore.transcript = toList(rawTables["transcript"])
        DataStore.account = toList(rawTables["account"])
        DataStore.planNames = planNames.toList()
        val details = LinkedHashMap<Int, List<List<String>>>()
        for (i in planLinks.indices) details[i] = toList(rawTables["planDetail_$i"])
        DataStore.planDetails = details

        val html = DashboardHtmlBuilder.build()
        getSharedPreferences(CACHE_PREFS, MODE_PRIVATE).edit().putString("html", html).apply()
        resultWebView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        progressLayout.visibility = View.GONE
        resultWebView.visibility = View.VISIBLE
    }
}

object DataStore {
    val fields = HashMap<String, String>()
    var registration: List<List<String>> = emptyList()
    var semesterGrades: List<List<String>> = emptyList()
    var attendance: List<List<String>> = emptyList()
    var transcript: List<List<String>> = emptyList()
    var account: List<List<String>> = emptyList()
    var planDetails: Map<Int, List<List<String>>> = emptyMap()
    var planNames: List<String> = emptyList()
    val planStats = mutableListOf<List<String>>()

    fun f(key: String) = fields[key]?.trim()?.takeIf { it != "-" && it.isNotEmpty() } ?: ""

    fun reset() {
        fields.clear()
        planStats.clear()
        registration = emptyList()
        semesterGrades = emptyList()
        attendance = emptyList()
        transcript = emptyList()
        account = emptyList()
        planDetails = emptyMap()
        planNames = emptyList()
    }
}
