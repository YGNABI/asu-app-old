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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
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
    private val CACHE_VERSION = 8

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
        function stillOnLogin() {
            return document.querySelector('input[type="password"]') ? true : false;
        }
        function clickAccountNext() {
            var a = document.querySelector('.t-Report-paginationLink--next');
            if (a) { a.click(); return true; }
            return false;
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
            var wanted = (labelText||'').trim();
            for (var i=0;i<tables.length;i++){
                if ((tables[i].getAttribute('aria-label')||'').trim() === wanted) {
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
        function scrapeMoodleCourses() {
            var out = [];
            var cards = document.querySelectorAll('[data-region="course-content"]');
            for (var i=0;i<cards.length;i++){
                var card = cards[i];
                var id = card.getAttribute('data-course-id');
                if (!id) continue;
                var nameEl = card.querySelector('a.coursename');
                var name = nameEl ? nameEl.textContent.trim() : '';
                if (!name) {
                    var sr = card.querySelector('.sr-only');
                    name = sr ? sr.textContent.trim() : '';
                }
                out.push({ id: id, name: name });
            }
            return JSON.stringify(out);
        }
        function stillOnMoodleLogin() {
            return document.querySelector('input[name="password"]') ? true : false;
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

    private var moodleLoginAttempts = 0

    private fun startScraping() {
        phase = "login"
        sessionId = ""
        planIndex = 0
        planLinks.clear()
        planNames.clear()
        rawTables.clear()
        moodleLoginAttempts = 0
        DataStore.reset()
        DebugLog.reset()

        progressLayout.visibility = View.VISIBLE
        resultWebView.visibility = View.GONE
        setStatus("جاري تسجيل الدخول...")

        webView.loadUrl("https://sis.asu.edu.bh/ords/f?p=101:1")

        webView.postDelayed({
            if (phase != "done" && phase != "failed") {
                phase = "done"
                buildAndShowDashboard()
            }
        }, 60000)
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
                        view.evaluateJavascript("AndroidBridge.checkLogin(stillOnLogin());", null)
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
                        view.evaluateJavascript("clickAccountNext();", null)
                        phase = "accountPage2"
                        view.postDelayed({
                            view.evaluateJavascript("AndroidBridge.tbl('account2', scrapeTableByLabel('تفاصيل كشف الحساب '));", null)
                            setStatus("جاري ربط المواد بموقع التعليم الالكتروني...")
                            phase = "moodleLogin"
                            view.loadUrl("https://elearning.asu.edu.bh/login/index.php")
                        }, 1200)
                    }
                    "moodleLogin" -> {
                        val moodleUser = getSharedPreferences("asu_prefs", MODE_PRIVATE).getString("username", "") ?: ""
                        val moodlePass = getSharedPreferences("asu_prefs", MODE_PRIVATE).getString("password", "") ?: ""
                        view.evaluateJavascript("stillOnMoodleLogin();") { stillOnLogin ->
                            if (stillOnLogin == "true") {
                                if (moodleUser.isBlank() || moodlePass.isBlank()) {
                                    DebugLog.error("ما فيه بيانات دخول محفوظة لموقع التعليم الالكتروني")
                                    phase = "done"
                                    view.postDelayed({ buildAndShowDashboard() }, 300)
                                    return@evaluateJavascript
                                }
                                moodleLoginAttempts++
                                if (moodleLoginAttempts > 3) {
                                    // Capped so a wrong/rejected password (or a login form the
                                    // auto-fill JS can't actually submit) can't loop silently
                                    // until the 60s global timeout swallows the real reason.
                                    DebugLog.error("فشل تسجيل الدخول لموقع التعليم الالكتروني (Moodle) بعد 3 محاولات — تحقق من كلمة السر أو شكل نموذج الدخول")
                                    phase = "done"
                                    view.postDelayed({ buildAndShowDashboard() }, 300)
                                    return@evaluateJavascript
                                }
                                DebugLog.warn("محاولة دخول Moodle رقم $moodleLoginAttempts — لسا بصفحة الدخول")
                                val js = """
                                    (function() {
                                        var u = document.querySelector('input[name="username"]');
                                        var p = document.querySelector('input[name="password"]');
                                        if (u && p) {
                                            u.value = "$moodleUser"; p.value = "$moodlePass";
                                            u.dispatchEvent(new Event('input',{bubbles:true}));
                                            p.dispatchEvent(new Event('input',{bubbles:true}));
                                            u.dispatchEvent(new Event('change',{bubbles:true}));
                                            p.dispatchEvent(new Event('change',{bubbles:true}));
                                            var f = u.closest('form');
                                            var b = document.querySelector('#loginbtn');
                                            setTimeout(function(){
                                                if (b) { b.click(); }
                                                else if (f) { f.submit(); }
                                            }, 500);
                                        }
                                    })();
                                """.trimIndent()
                                view.evaluateJavascript(js, null)
                                // phase stays "moodleLogin" — wait for the redirect and re-check.
                            } else {
                                DebugLog.ok("تسجيل الدخول لـ Moodle نجح (محاولة $moodleLoginAttempts)")
                                phase = "moodleCourses"
                                view.loadUrl("https://elearning.asu.edu.bh/my/")
                            }
                        }
                    }
                    "moodleCourses" -> {
                        view.evaluateJavascript("AndroidBridge.tbl('moodleCourses', scrapeMoodleCourses());", null)
                        phase = "done"
                        view.postDelayed({ buildAndShowDashboard() }, 300)
                    }
                }
            }
        }

        val cachePrefs = getSharedPreferences(CACHE_PREFS, MODE_PRIVATE)
        val cachedVersion = cachePrefs.getInt("version", -1)
        val cached = if (cachedVersion == CACHE_VERSION) cachePrefs.getString("html", null) else null
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
        fun checkLogin(failed: Boolean) {
            if (failed && phase != "done" && phase != "failed") {
                phase = "failed"
                DebugLog.error("فشل تسجيل الدخول لـ SIS (اسم المستخدم/الرقم السري غير صحيح)")
                runOnUiThread {
                    resultWebView.visibility = View.GONE
                    webView.visibility = View.GONE
                    progressLayout.visibility = View.VISIBLE
                    setStatus("اسم المستخدم أو الرقم السري غير صحيح ⚠️")
                }
            }
        }

        @JavascriptInterface
        fun refresh() {
            runOnUiThread { startScraping() }
        }

        @JavascriptInterface
        fun addCalendarEvent(title: String, dateStr: String, timeStr: String, location: String) {
            runOnUiThread {
                pendingEvent = Triple(title, dateStr, Pair(timeStr, location))
                if (ContextCompat.checkSelfPermission(this@SisDashboardActivity, android.Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this@SisDashboardActivity,
                        arrayOf(android.Manifest.permission.WRITE_CALENDAR, android.Manifest.permission.READ_CALENDAR),
                        101
                    )
                } else {
                    insertCalendarEvent(title, dateStr, timeStr, location)
                }
            }
        }

        @JavascriptInterface
        fun openCourseMaterials(moodleId: String, courseName: String) {
            runOnUiThread {
                if (moodleId.isBlank()) return@runOnUiThread
                val intent = android.content.Intent(this@SisDashboardActivity, CourseMaterialsActivity::class.java)
                intent.putExtra("moodleId", moodleId)
                intent.putExtra("courseName", courseName)
                startActivity(intent)
            }
        }

        @JavascriptInterface
        fun logout() {
            runOnUiThread {
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                getSharedPreferences(CACHE_PREFS, MODE_PRIVATE).edit().clear().apply()
                DataStore.reset()
                finish()
            }
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

    private fun runDiagnostics(moodleCourses: List<CourseSummary>) {
        val studentName = DataStore.f("studentName")
        if (studentName.isBlank()) {
            DebugLog.error("ما قدرنا نجيب اسم الطالب من SIS")
        } else {
            DebugLog.ok("بيانات الطالب الأساسية ($studentName)")
        }

        if (DataStore.registration.size <= 1) {
            DebugLog.error("جدول المواد المسجلة (registration) فاضي")
        } else {
            DebugLog.ok("جدول التسجيل — ${DataStore.registration.size - 1} مادة")
        }

        if (DataStore.semesterGrades.size <= 1) {
            DebugLog.warn("جدول درجات الفصل فاضي (طبيعي أول الفصل قبل صدور درجات)")
        } else {
            DebugLog.ok("جدول درجات الفصل — ${DataStore.semesterGrades.size - 1} صف")
        }

        if (DataStore.attendance.size <= 1) {
            DebugLog.warn("جدول الحضور والغياب فاضي")
        } else {
            DebugLog.ok("جدول الحضور والغياب — ${DataStore.attendance.size - 1} صف")
        }

        if (DataStore.transcript.isEmpty()) {
            DebugLog.warn("كشف الدرجات (transcript) فاضي")
        } else {
            DebugLog.ok("كشف الدرجات — ${DataStore.transcript.size} صف")
        }

        if (DataStore.account.size <= 1) {
            DebugLog.warn("كشف الحساب فاضي")
        } else {
            DebugLog.ok("كشف الحساب — ${DataStore.account.size - 1} صف")
        }

        if (moodleCourses.isEmpty()) {
            DebugLog.error("ما انسحبت ولا مادة من Moodle (/my/) — تأكد من الدخول التلقائي لموقع التعليم الالكتروني")
        } else {
            DebugLog.ok("مواد Moodle المسحوبة — ${moodleCourses.size} مادة")
            for (mc in moodleCourses) {
                DebugLog.ok("  Moodle: ${mc.name} (id=${mc.moodleId})")
            }
        }

        val sisCount = (DataStore.registration.size - 1).coerceAtLeast(0)
        val matchedCount = DataStore.moodleCourseMap.size
        if (moodleCourses.isNotEmpty() && sisCount > 0) {
            if (matchedCount == 0) {
                DebugLog.error("ولا مادة انطابقت بين SIS و Moodle — راجع أسماء المواد بالأعلى")
            } else if (matchedCount < sisCount) {
                DebugLog.warn("انطابقت $matchedCount من أصل $sisCount مادة بس")
            } else {
                DebugLog.ok("كل المواد ($matchedCount) انطابقت مع Moodle بنجاح")
            }

            val nameIdx = colIdxForDebug(DataStore.registration.firstOrNull() ?: emptyList(), "اسم المقرر")
            val codeIdx = colIdxForDebug(DataStore.registration.firstOrNull() ?: emptyList(), "رمز")
            if (nameIdx >= 0 && codeIdx >= 0) {
                for (r in 1 until DataStore.registration.size) {
                    val row = DataStore.registration[r]
                    val code = row.getOrNull(codeIdx)?.trim().orEmpty()
                    val name = row.getOrNull(nameIdx)?.trim().orEmpty()
                    if (code.isBlank()) continue
                    val normCode = code.uppercase().replace(Regex("[^A-Z0-9]"), "")
                    val moodleId = DataStore.moodleCourseMap[normCode]
                    if (moodleId != null) {
                        val matchedName = moodleCourses.find { it.moodleId == moodleId }?.name ?: "?"
                        DebugLog.ok("SIS: \"$name\" ($code) ✔ ↔ Moodle: \"$matchedName\"")
                    } else {
                        DebugLog.warn("SIS: \"$name\" ($code) ✘ ما لقى تطابق بـ Moodle")
                    }
                }
            }
        }
    }

    private fun colIdxForDebug(header: List<String>, needle: String): Int {
        for (i in header.indices) if (header[i].replace("\n", " ").contains(needle)) return i
        return -1
    }

    private fun buildAndShowDashboard() {
        DataStore.registration = toList(rawTables["registration"])
        DataStore.semesterGrades = toList(rawTables["semesterGrades"])
        DataStore.attendance = toList(rawTables["attendance"])
        DataStore.transcript = toList(rawTables["transcript"])
        run {
            val acc1 = toList(rawTables["account"])
            val acc2 = toList(rawTables["account2"])
            val header = acc1.firstOrNull()
            val seen = LinkedHashSet<String>()
            val dataRows = mutableListOf<List<String>>()
            for (r in acc1.drop(1) + acc2.drop(1)) {
                val key = r.joinToString("|")
                if (seen.add(key)) dataRows.add(r)
            }
            DataStore.account = if (header != null) listOf(header) + dataRows else dataRows
        }
        DataStore.planNames = planNames.toList()
        val details = LinkedHashMap<Int, List<List<String>>>()
        for (i in planLinks.indices) details[i] = toList(rawTables["planDetail_$i"])
        DataStore.planDetails = details

        val moodleCourses = mutableListOf<CourseSummary>()
        rawTables["moodleCourses"]?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val name = obj.optString("name")
                if (id.isNotBlank() && name.isNotBlank()) {
                    moodleCourses.add(CourseSummary(id, name, ""))
                }
            }
        }
        DataStore.moodleCourses = moodleCourses
        DataStore.moodleCourseMap = MoodleCourseMatcher.match(DataStore.registration, moodleCourses)

        runDiagnostics(moodleCourses)

        DashboardHtmlBuilder.LANG = getSharedPreferences("asu_prefs", MODE_PRIVATE).getString("lang", "ar") ?: "ar"
        val html = DashboardHtmlBuilder.build()
        getSharedPreferences(CACHE_PREFS, MODE_PRIVATE).edit().putString("html", html).putInt("version", CACHE_VERSION).apply()
        resultWebView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        progressLayout.visibility = View.GONE
        resultWebView.visibility = View.VISIBLE
    }

    private var pendingEvent: Triple<String, String, Pair<String, String>>? = null

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            pendingEvent?.let { (title, date, tl) -> insertCalendarEvent(title, date, tl.first, tl.second) }
        } else if (requestCode == 101) {
            toast("نحتاج صلاحية التقويم لإضافة الموعد")
        }
        pendingEvent = null
    }

    private fun insertCalendarEvent(title: String, dateStr: String, timeStr: String, location: String) {
        try {
            val parts = dateStr.trim().split("-")
            if (parts.size != 3) {
                toast("تاريخ الامتحان غير متوفر")
                return
            }
            val day = parts[0].trim().toInt()
            val month = parts[1].trim().toInt()
            val year = parts[2].trim().toInt()
            var hour = 9
            var minute = 0
            if (timeStr.isNotBlank()) {
                val tp = timeStr.trim().split(":")
                hour = tp.getOrNull(0)?.trim()?.toIntOrNull() ?: 9
                minute = tp.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
            }
            val cal = java.util.Calendar.getInstance()
            cal.set(year, month - 1, day, hour, minute, 0)
            val startMillis = cal.timeInMillis
            val endMillis = startMillis + 60 * 60 * 1000

            val calId = primaryCalendarId()
            if (calId == null) {
                toast("ما قدرنا نلقى تقويم بالجهاز")
                return
            }

            val existsCursor = contentResolver.query(
                android.provider.CalendarContract.Events.CONTENT_URI,
                arrayOf(android.provider.CalendarContract.Events._ID),
                "${android.provider.CalendarContract.Events.TITLE} = ? AND ${android.provider.CalendarContract.Events.DTSTART} = ?",
                arrayOf(title, startMillis.toString()),
                null
            )
            val alreadyExists = existsCursor?.use { it.count > 0 } ?: false
            if (alreadyExists) {
                toast("الموعد مضاف مسبقًا بالتقويم")
                return
            }

            val values = android.content.ContentValues().apply {
                put(android.provider.CalendarContract.Events.DTSTART, startMillis)
                put(android.provider.CalendarContract.Events.DTEND, endMillis)
                put(android.provider.CalendarContract.Events.TITLE, title)
                put(android.provider.CalendarContract.Events.EVENT_LOCATION, location)
                put(android.provider.CalendarContract.Events.CALENDAR_ID, calId)
                put(android.provider.CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
            }
            contentResolver.insert(android.provider.CalendarContract.Events.CONTENT_URI, values)
            toast("انضاف للتقويم ✅")
        } catch (e: Exception) {
            toast("صار خطأ بالإضافة للتقويم")
        }
    }

    private fun primaryCalendarId(): Long? {
        val proj = arrayOf(android.provider.CalendarContract.Calendars._ID, android.provider.CalendarContract.Calendars.IS_PRIMARY)
        val cursor = contentResolver.query(android.provider.CalendarContract.Calendars.CONTENT_URI, proj, null, null, null)
        cursor?.use {
            while (it.moveToNext()) {
                if (it.getInt(1) == 1) return it.getLong(0)
            }
            if (it.moveToFirst()) return it.getLong(0)
        }
        return null
    }

    private fun toast(msg: String) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
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
    var moodleCourses: List<CourseSummary> = emptyList()
    // normalized SIS course code -> Moodle course id
    var moodleCourseMap: Map<String, String> = emptyMap()

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
        moodleCourses = emptyList()
        moodleCourseMap = emptyMap()
    }
}
