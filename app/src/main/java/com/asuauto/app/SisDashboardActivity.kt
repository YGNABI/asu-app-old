package com.asuauto.app

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.content.Intent
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SisDashboardActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var resultWebView: WebView
    private lateinit var progressLayout: LinearLayout

    private var phase = "login"
    private var sessionId = ""
    private var planLinks: MutableList<String> = mutableListOf()
    private var planNames: MutableList<String> = mutableListOf()
    private var planIndex = 0

    private val rawTables = HashMap<String, JSONArray>()
    private val CACHE_PREFS = "asu_dashboard_cache"
    private val CACHE_VERSION = 18

    private fun pageUrl(page: Int) = "https://sis.asu.edu.bh/ords/f?p=2020:$page:$sessionId:::::"

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        return activeNetwork != null && activeNetwork.isConnected
    }

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

    private fun logoBase64(): String {
        val bmp = android.graphics.BitmapFactory.decodeResource(resources, R.drawable.asu_logo)
        val baos = java.io.ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, baos)
        return android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
    }

    private var loadingLogoCache: String? = null

    private fun loadingLogoBase64(): String {
        loadingLogoCache?.let { return it }
        val bmp = android.graphics.BitmapFactory.decodeResource(resources, R.drawable.asu_logo_loading)
        val baos = java.io.ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, baos)
        val encoded = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
        loadingLogoCache = encoded
        return encoded
    }

    private fun showAnimatedLoader() {
        progressLayout.visibility = View.GONE
        resultWebView.visibility = View.VISIBLE
        val loaderHtml = """
            <html dir="rtl"><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
            body { background:#f2f3f5; display:flex; flex-direction:column; align-items:center; justify-content:center; height:100vh; margin:0; }
            .loader-container { position:relative; width:200px; height:200px; }
            .loader-bg-logo { width:100%; height:100%; object-fit:contain; opacity:0.18; }
            .loader-water-fill { position:absolute; bottom:0; left:0; width:100%; height:0%; overflow:hidden; transition:height 0.4s ease; }
            .loader-water-fill img { position:absolute; bottom:0; left:0; width:200px; height:200px; object-fit:contain; }
            </style></head><body>
            <div class="loader-container">
                <img src="data:image/png;base64,${loadingLogoBase64()}" class="loader-bg-logo" />
                <div id="waterFillLayer" class="loader-water-fill">
                    <img src="data:image/png;base64,${loadingLogoBase64()}" />
                </div>
            </div>
            <script>
            function setProgress(p){
                document.getElementById('waterFillLayer').style.height = p + '%';
            }
            </script>
            </body></html>
        """.trimIndent()
        resultWebView.loadDataWithBaseURL(null, loaderHtml, "text/html", "utf-8", null)
    }

    private fun updateLoaderProgress(percent: Int) {
        runOnUiThread {
            resultWebView.evaluateJavascript("if(typeof setProgress!=='undefined') setProgress($percent);", null)
        }
    }

    private fun startScraping() {
        phase = "login"
        sessionId = ""
        planIndex = 0
        planLinks.clear()
        planNames.clear()
        rawTables.clear()
        accountPageIndex = 0
        DebugLog.reset()

        showAnimatedLoader()
        updateLoaderProgress(10)

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
        webView = findViewById(R.id.scrapeWebView)
        resultWebView = findViewById(R.id.resultWebView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        resultWebView.settings.javaScriptEnabled = true

        val bridge = Bridge()
        webView.addJavascriptInterface(bridge, "AndroidBridge")
        resultWebView.addJavascriptInterface(bridge, "AndroidBridge")

        val prefs = getSharedPreferences("asu_prefs", MODE_PRIVATE)

        resultWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val urlStr = request?.url?.toString() ?: ""
                if (urlStr.contains("login") || urlStr.contains("signin") || urlStr.contains("redirect")) {
                    return true
                }
                return super.shouldOverrideUrlLoading(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val offlineNow = !isNetworkAvailable()
                val lastUpdateNow = getSharedPreferences("asu_prefs", MODE_PRIVATE).getString("last_update_time", "") ?: ""
                view?.evaluateJavascript(
                    "if(typeof applyOfflineState!=='undefined') applyOfflineState($offlineNow, '${lastUpdateNow.replace("'", "")}');",
                    null
                )
            }
        }

        DashboardHtmlBuilder.isOffline = !isNetworkAvailable()
        DashboardHtmlBuilder.lastUpdate = prefs.getString("last_update_time", "") ?: ""
        DashboardHtmlBuilder.pullLogoBase64 = pullLogoBase64Cached()

        val user = prefs.getString("username", "") ?: ""
        val pass = prefs.getString("password", "") ?: ""

        // هذا الكلاينت لازم يتحدد دائمًا، حتى لو رجّعنا من الكاش تحت -
        // لأن السحب للتحديث (pull-to-refresh) يستخدم نفس الـ webView لاحقًا عبر startScraping()،
        // وبدون WebViewClient هنا، أي إعادة توجيه أثناء تسجيل الدخول تفتح المتصفح الخارجي
        // بدل ما تكمل جوا التطبيق، وتنقطع عملية سحب البيانات (ولذلك تطلع تبويبات فاضية).
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                return false
            }

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
                        updateLoaderProgress(20)
                        
                        DataStore.reset()

                        view.loadUrl(pageUrl(1))
                        phase = "page1"
                    }
                    "page1" -> {
                        view.evaluateJavascript("""
                            (function(){
                                var img = document.querySelector('img[src*="apex_util.get_blob"]');
                                if (img) { 
                                    fetch(img.src)
                                    .then(res => res.blob())
                                    .then(blob => {
                                        var reader = new FileReader();
                                        reader.onloadend = function() { AndroidBridge.txt('studentAvatarFresh', reader.result); }
                                        reader.readAsDataURL(blob);
                                    }).catch(e => {
                                        AndroidBridge.txt('studentAvatarFresh', img.src);
                                    });
                                }
                            })();
                        """.trimIndent(), null)
                        view.evaluateJavascript("AndroidBridge.txt('studentName', getField('Name'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('studentCollege', getField('The College'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('studentGpa', getField('المعدل التراكمي'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('studentWarning', getField('حالة الانذار الاكاديمي'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('advisor', getField('المرشد الاكاديمي'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('gradExpected', getGradExpected());", null)
                        view.evaluateJavascript("AndroidBridge.tbl('registration', scrapeTableById('report_table_currRegId'));", null)
                        view.evaluateJavascript("AndroidBridge.tbl('semesterGrades', scrapeTableById('report_table_termmarks'));", null)
                        view.evaluateJavascript("AndroidBridge.tbl('attendance', scrapeTableByLabel('احصائيات الحضور والغياب'));", null)
                        updateLoaderProgress(30)
                        view.postDelayed({ view.loadUrl(pageUrl(6)) }, 200)
                        phase = "page6"
                    }
                    "page6" -> {
                        view.evaluateJavascript("AndroidBridge.txt('regStart', getField('بداية تاريخ التسجيل'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('regEnd', getField('نهاية تاريخ التسجيل'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('addDropStart', getField('بداية تاريخ السحب و الأضافه'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('addDropEnd', getField('نهاية تاريخ السحب ولأضافه'));", null)
                        view.evaluateJavascript("AndroidBridge.txt('academicStatus', getField('الوضع الأكاديمي'));", null)
                        updateLoaderProgress(45)
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
                        val p = 45 + ((planIndex.toFloat() / planLinks.size) * 20).toInt()
                        updateLoaderProgress(p)
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
                        updateLoaderProgress(75)
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
                        updateLoaderProgress(85)
                        view.postDelayed({ view.loadUrl(pageUrl(7)) }, 200)
                        phase = "account"
                    }
                    "account" -> {
                        view.evaluateJavascript("AndroidBridge.txt('balance', getById('P7_BALANCE'));", null)
                        updateLoaderProgress(95)
                        accountPageIndex = 1
                        scrapeAccountPageAndContinue(view)
                    }
                    "moodleCourses" -> {
                        view.evaluateJavascript("AndroidBridge.tbl('moodleCourses', scrapeMoodleCourses());", null)
                        updateLoaderProgress(100)
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
            resultWebView.loadDataWithBaseURL("https://sis.asu.edu.bh/", cached, "text/html", "utf-8", null)
            return
        }

        startScraping()
    }

    private var accountPageIndex = 0

    private fun scrapeAccountPageAndContinue(view: WebView) {
        view.evaluateJavascript("AndroidBridge.tbl('accountPage$accountPageIndex', scrapeTableByLabel('تفاصيل كشف الحساب '));", null)
        view.evaluateJavascript("clickAccountNext();") { hasNext ->
            if (hasNext == "true" && accountPageIndex < 30) {
                accountPageIndex++
                view.postDelayed({ scrapeAccountPageAndContinue(view) }, 900)
            } else {
                phase = "moodleCourses"
                view.loadUrl("https://elearning.asu.edu.bh/?redirect=0")
            }
        }
    }

    private fun nextPlanDetail(view: WebView) {
        if (planIndex < planLinks.size) {
            phase = "planDetail"
            view.loadUrl("https://sis.asu.edu.bh/ords/" + planLinks[planIndex])
        } else {
            phase = "grades"
            view.loadUrl(pageUrl(8))
        }
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
                runOnUiThread {
                    val cached = getSharedPreferences(CACHE_PREFS, MODE_PRIVATE).getString("html", null)
                    if (cached != null) {
                        progressLayout.visibility = View.GONE
                        resultWebView.visibility = View.VISIBLE
                        resultWebView.loadDataWithBaseURL("https://sis.asu.edu.bh/", cached, "text/html", "utf-8", null)
                    } else {
                        finish()
                    }
                }
            }
        }

        @JavascriptInterface
        fun refresh() {
            runOnUiThread {
                if (isNetworkAvailable()) {
                    startScraping()
                } else {
                    android.widget.Toast.makeText(this@SisDashboardActivity, "تتطلب عملية التحديث اتصالاً بالإنترنت", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun showGpaChart() {
            runOnUiThread {
                try {
                    val dialog = android.app.Dialog(this@SisDashboardActivity)
                    val webViewDialog = WebView(this@SisDashboardActivity).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                    }
                    
                    val chartHtml = """
                        <html><head><meta name="viewport" content="width=device-width,initial-scale=1">
                        <style>body{background:#1e1e1e;color:#fff;font-family:sans-serif;padding:16px;text-align:center;}</style>
                        </head><body>
                        <h3>تطور المعدل التراكمي</h3>
                        <p>عرض تفاصيل المعدلات السابقة</p>
                        </body></html>
                    """.trimIndent()
                    
                    webViewDialog.loadDataWithBaseURL(null, chartHtml, "text/html", "utf-8", null)
                    dialog.setContentView(webViewDialog)
                    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                    
                    val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
                    val height = (resources.displayMetrics.heightPixels * 0.70).toInt()
                    dialog.window?.setLayout(width, height)
                    dialog.show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(this@SisDashboardActivity, "تعذر عرض الرسم البياني", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun showAcademicCalendar() {
            runOnUiThread {
                val calendarDetails = """
                    ■ الفصل الدراسي الأول (2026 / 2027):
                    - الإرشاد والتسجيل: 01 إلى 05-09-2026
                    - بدء الدراسة: 06-09-2026
                    - اختبارات المنتصف: 17 إلى 31-10-2026
                    - الامتحانات النهائية: 08 إلى 26-12-2026

                    ■ الفصل الدراسي الثاني (2026 / 2027):
                    - الإرشاد والتسجيل: 05 إلى 09-01-2027
                    - بدء الدراسة: 10-01-2027
                    - اختبارات المنتصف: 20-02 إلى 06-03-2027
                    - الامتحانات النهائية: 15-04 إلى 29-04-2027

                    ■ الفصل الدراسي الصيفي (2026 / 2027):
                    - التسجيل والإضافة: 06 إلى 08-05-2027
                    - بدء الدراسة: 09-05-2027
                    - اختبارات المنتصف: 29-05 إلى 07-06-2027
                    - الامتحانات النهائية: 27-06 إلى 05-07-2027

                    ■ الفصل الصيفي الممتد (2026 / 2027):
                    - السحب والإضافة: 09 إلى 13-05-2027
                    - اختبارات المنتصف: 08 إلى 14-06-2027
                    - الامتحانات النهائية: 08 إلى 14-08-2027
                    
                    ■ العام الأكاديمي القادم (2027 / 2028):
                    - بدء الدراسة: 05-09-2027
                """.trimIndent()

                android.app.AlertDialog.Builder(this@SisDashboardActivity)
                    .setTitle("التقويم الأكاديمي الشامل")
                    .setMessage(calendarDetails)
                    .setPositiveButton("إغلاق", null)
                    .show()
            }
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
                val intent = Intent(this@SisDashboardActivity, CourseMaterialsActivity::class.java)
                intent.putExtra("moodleId", moodleId)
                intent.putExtra("courseName", courseName)
                startActivity(intent)
            }
        }

        @JavascriptInterface
        fun openCourseAssessments(
            moodleId: String, courseName: String,
            midDate: String, midTime: String, midRoom: String,
            finDate: String, finTime: String, finRoom: String
        ) {
            runOnUiThread {
                if (moodleId.isBlank()) return@runOnUiThread
                val intent = Intent(this@SisDashboardActivity, AssessmentsActivity::class.java)
                intent.putExtra("moodleId", moodleId)
                intent.putExtra("courseName", courseName)
                intent.putExtra("midDate", midDate)
                intent.putExtra("midTime", midTime)
                intent.putExtra("midRoom", midRoom)
                intent.putExtra("finDate", finDate)
                intent.putExtra("finTime", finTime)
                intent.putExtra("finRoom", finRoom)
                startActivity(intent)
            }
        }

        @JavascriptInterface
        fun openSos(sosType: String, specialFlag: Boolean) {
            runOnUiThread {
                val intent = Intent(this@SisDashboardActivity, SosCategoryActivity::class.java)
                intent.putExtra("sosType", sosType)
                intent.putExtra("specialFlag", specialFlag)
                startActivity(intent)
            }
        }

        @JavascriptInterface
        fun openExamExcuses() {
            runOnUiThread {
                val intent = Intent(this@SisDashboardActivity, SosExamExcuseActivity::class.java)
                startActivity(intent)
            }
        }

        @JavascriptInterface
        fun openOutlook(instructorName: String, courseName: String) {
            runOnUiThread {
                try {
                    val emailIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "message/rfc822"
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(instructorName))
                        setPackage("com.microsoft.office.outlook")
                    }
                    if (emailIntent.resolveActivity(packageManager) != null) {
                        startActivity(emailIntent)
                    } else {
                        android.widget.Toast.makeText(
                            this@SisDashboardActivity,
                            "تطبيق Microsoft Outlook غير مثبت على الجهاز",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        this@SisDashboardActivity,
                        "تعذر فتح تطبيق Outlook",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
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

    private var pullLogoCache: String? = null

    private fun pullLogoBase64Cached(): String {
        pullLogoCache?.let { return it }
        return try {
            val stream = java.io.ByteArrayOutputStream()
            val bitmap = android.graphics.BitmapFactory.decodeResource(resources, R.drawable.bar_logo)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, stream)
            val encoded = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
            pullLogoCache = encoded
            encoded
        } catch (e: Exception) {
            ""
        }
    }

    private fun buildAndShowDashboard() {
        DataStore.registration = toList(rawTables["registration"])
        DataStore.semesterGrades = toList(rawTables["semesterGrades"])
        DataStore.attendance = toList(rawTables["attendance"])
        DataStore.transcript = toList(rawTables["transcript"])
        run {
            val accountKeys = rawTables.keys.filter { it.startsWith("accountPage") }
                .sortedBy { it.removePrefix("accountPage").toIntOrNull() ?: 0 }
            val pages = accountKeys.map { toList(rawTables[it]) }
            val header = pages.firstOrNull()?.firstOrNull()
            val seen = LinkedHashSet<String>()
            val dataRows = mutableListOf<List<String>>()
            for (page in pages) {
                for (r in page.drop(1)) {
                    val key = r.joinToString("|")
                    if (seen.add(key)) dataRows.add(r)
                }
            }
            DataStore.account = if (header != null) listOf(header) + dataRows else dataRows
        }
        DataStore.planNames = planNames.toList()
        val details = LinkedHashMap<Int, List<List<String>>>()
        for (i in planLinks.indices) details[i] = toList(rawTables["planDetail_$i"])
        DataStore.planDetails = details

        val prefs = getSharedPreferences("asu_prefs", MODE_PRIVATE)
        val freshAvatarUrl = DataStore.fields["studentAvatarFresh"] ?: ""
        val avatarUrl = if (freshAvatarUrl.isNotBlank()) freshAvatarUrl else (prefs.getString("student_avatar", "") ?: "")
        if (avatarUrl.isNotBlank()) {
            DataStore.fields["studentAvatar"] = avatarUrl
            prefs.edit().putString("student_avatar", avatarUrl).apply()
        }

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

        val currentTime = SimpleDateFormat("yyyy-MM-dd / hh:mm a", Locale.US).format(Date())
        prefs.edit().putString("last_update_time", currentTime).apply()
        DashboardHtmlBuilder.lastUpdate = currentTime
        DashboardHtmlBuilder.isOffline = false

        DashboardHtmlBuilder.LANG = prefs.getString("lang", "ar") ?: "ar"
        DashboardHtmlBuilder.pullLogoBase64 = pullLogoBase64Cached()
        val html = DashboardHtmlBuilder.build()
        getSharedPreferences(CACHE_PREFS, MODE_PRIVATE).edit().putString("html", html).putInt("version", CACHE_VERSION).apply()
        
        resultWebView.loadDataWithBaseURL("https://sis.asu.edu.bh/", html, "text/html", "utf-8", null)
    }

    private var pendingEvent: Triple<String, String, Pair<String, String>>? = null

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            pendingEvent?.let { (title, date, tl) -> insertCalendarEvent(title, date, tl.first, tl.second) }
        }
        pendingEvent = null
    }

    private fun insertCalendarEvent(title: String, dateStr: String, timeStr: String, location: String) {
        try {
            val parts = dateStr.trim().split("-")
            if (parts.size != 3) return
            val day = parts[0].trim().toInt()
            val month = parts[1].trim().toInt()
            val year = parts[2].trim().toInt()
            var hour = 9; var minute = 0
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
                android.widget.Toast.makeText(this, "تعذّر العثور على تقويم في الجهاز", android.widget.Toast.LENGTH_SHORT).show()
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
                android.widget.Toast.makeText(this, "الموعد مضاف مسبقًا إلى التقويم", android.widget.Toast.LENGTH_SHORT).show()
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
            android.widget.Toast.makeText(this, "تمت الإضافة إلى التقويم ✅", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "حدث خطأ أثناء الإضافة إلى التقويم", android.widget.Toast.LENGTH_SHORT).show()
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
