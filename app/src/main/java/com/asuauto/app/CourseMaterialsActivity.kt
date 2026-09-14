package com.asuauto.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File

class CourseMaterialsActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var resultWebView: WebView
    private lateinit var progressLayout: LinearLayout
    private lateinit var statusText: TextView

    private var moodleId = ""
    private var courseName = ""
    private var lastWeeks: List<MoodleWeek> = emptyList()
    private var pageLoadFailed = false

    private val EXTRACTION_JS = """
        (function() {
            var out = [];
            var sections = document.querySelectorAll('#format-asu-course-content li.section[data-sectionname]');
            sections.forEach(function(sec) {
                var weekName = sec.getAttribute('data-sectionname') || '';
                var groups = sec.querySelectorAll('.asu-learning-group');
                var cats = [];
                groups.forEach(function(g) {
                    var headingH3 = g.querySelector('.asu-category-heading-list h3');
                    var label = headingH3 ? headingH3.textContent.trim() : '';
                    var activityList = g.querySelector('.asu-category-activity-list');
                    if (!activityList) return;
                    if (activityList.querySelector('.asu-category-empty')) return;
                    var files = [];
                    var items = activityList.querySelectorAll('li.activity.resource');
                    items.forEach(function(li) {
                        var a = li.querySelector('a.aalink');
                        if (!a) return;
                        var nameSpan = li.querySelector('.instancename');
                        var name = '';
                        if (nameSpan && nameSpan.childNodes.length > 0) {
                            name = (nameSpan.childNodes[0].textContent || '').trim();
                        }
                        if (!name) name = (a.textContent || '').trim();
                        var badge = li.querySelector('.activitybadge');
                        var type = badge ? badge.textContent.trim() : '';
                        files.push({ name: name, url: a.href, type: type });
                    });
                    if (files.length > 0) cats.push({ label: label, files: files });
                });
                if (cats.length > 0) out.push({ week: weekName, categories: cats });
            });
            return JSON.stringify(out);
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_course_materials)

        moodleId = intent.getStringExtra("moodleId") ?: ""
        courseName = intent.getStringExtra("courseName") ?: ""

        progressLayout = findViewById(R.id.progressLayout)
        statusText = findViewById(R.id.statusText)
        webView = findViewById(R.id.scrapeWebView)
        resultWebView = findViewById(R.id.resultWebView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        resultWebView.settings.javaScriptEnabled = true
        resultWebView.addJavascriptInterface(DownloadBridge(), "AndroidBridge")

        if (moodleId.isBlank()) {
            statusText.text = "تعذّر العثور على المادة في موقع التعليم الإلكتروني"
            return
        }

        // 1. تحميل وعرض القائمة المحفوظة محلياً فوراً (إن وجدت) لتعمل بدون إنترنت وتظهر بشكل أسرع
        val cachePrefs = getSharedPreferences("asu_materials_cache", MODE_PRIVATE)
        val cachedJson = cachePrefs.getString(moodleId, null)
        if (cachedJson != null) {
            try {
                val cachedWeeks = parseWeeks(cachedJson)
                if (cachedWeeks.isNotEmpty()) {
                    showResult(cachedWeeks, sessionExpired = false)
                }
            } catch (e: Exception) {}
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                pageLoadFailed = true
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                
                // 2. إذا فشل الاتصال (لا يوجد إنترنت)، يتم الاعتماد كلياً على النسخة المحفوظة
                if (pageLoadFailed) {
                    if (cachedJson == null) showResult(emptyList(), sessionExpired = false)
                    return
                }

                view.evaluateJavascript(
                    "document.querySelector('input[name=\"password\"]') ? 'true' : 'false';"
                ) { stillOnLogin ->
                    if (stillOnLogin == "true") {
                        showResult(emptyList(), sessionExpired = true)
                        return@evaluateJavascript
                    }
                    view.evaluateJavascript(EXTRACTION_JS) { rawJson ->
                        try {
                            val unescaped = org.json.JSONTokener(rawJson).nextValue() as String
                            val weeks = parseWeeks(unescaped)
                            if (weeks.isNotEmpty()) {
                                // 3. تحديث القائمة المحفوظة محلياً عند وجود اتصال ونجاح جلب البيانات
                                cachePrefs.edit().putString(moodleId, unescaped).apply()
                            }
                            showResult(weeks, sessionExpired = false)
                        } catch (e: Exception) {
                            if (cachedJson == null) showResult(emptyList(), sessionExpired = false)
                        }
                    }
                }
            }
        }

        webView.loadUrl("https://elearning.asu.edu.bh/course/view.php?id=$moodleId&asupage=weekly")
    }

    private fun parseWeeks(json: String): List<MoodleWeek> {
        val arr = JSONArray(json)
        val weeks = mutableListOf<MoodleWeek>()
        for (i in 0 until arr.length()) {
            val weekObj = arr.optJSONObject(i) ?: continue
            val weekName = weekObj.optString("week")
            val catsArr = weekObj.optJSONArray("categories") ?: continue
            val cats = mutableListOf<MoodleCategory>()
            for (j in 0 until catsArr.length()) {
                val catObj = catsArr.optJSONObject(j) ?: continue
                val label = catObj.optString("label")
                val filesArr = catObj.optJSONArray("files") ?: continue
                val files = mutableListOf<MoodleFile>()
                for (k in 0 until filesArr.length()) {
                    val fileObj = filesArr.optJSONObject(k) ?: continue
                    files.add(
                        MoodleFile(
                            name = fileObj.optString("name"),
                            url = fileObj.optString("url"),
                            type = fileObj.optString("type")
                        )
                    )
                }
                if (files.isNotEmpty()) cats.add(MoodleCategory(label, files))
            }
            if (cats.isNotEmpty()) weeks.add(MoodleWeek(weekName, cats))
        }
        return weeks
    }

    private fun showResult(weeks: List<MoodleWeek>, sessionExpired: Boolean) {
        lastWeeks = weeks
        runOnUiThread {
            CourseMaterialsHtmlBuilder.LANG =
                getSharedPreferences("asu_prefs", MODE_PRIVATE).getString("lang", "ar") ?: "ar"
            val html = if (sessionExpired) {
                "<html dir='rtl'><body style='font-family:sans-serif;padding:20px;text-align:center;color:#888'>" +
                    "انتهت الجلسة، يرجى العودة إلى اللوحة الرئيسية والمحاولة مرة أخرى" +
                    "</body></html>"
            } else {
                CourseMaterialsHtmlBuilder.build(courseName, weeks, downloadedNames = downloadedFileNames())
            }
            resultWebView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            progressLayout.visibility = View.GONE
            resultWebView.visibility = View.VISIBLE
        }
    }

    private fun refreshList() {
        CourseMaterialsHtmlBuilder.LANG = getSharedPreferences("asu_prefs", MODE_PRIVATE).getString("lang", "ar") ?: "ar"
        val html = CourseMaterialsHtmlBuilder.build(courseName, lastWeeks, downloadedNames = downloadedFileNames())
        resultWebView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    inner class DownloadBridge {
        @JavascriptInterface
        fun downloadFile(url: String, name: String, type: String) {
            runOnUiThread { openOrDownload(url, name, type) }
        }
    }

    private fun sanitizeFileName(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()

    private fun localFilesDir(): File {
        val dir = File(filesDir, "moodle_files/$moodleId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun localFileFor(name: String, type: String): File {
        val ext = type.trim().lowercase()
        val fileName = sanitizeFileName(name) + if (ext.isNotBlank() && !name.lowercase().endsWith(".$ext")) ".$ext" else ""
        return File(localFilesDir(), fileName)
    }

    private fun downloadedFileNames(): Set<String> =
        localFilesDir().listFiles()?.map { it.name }?.toSet() ?: emptySet()

    private fun openOrDownload(url: String, name: String, type: String) {
        val file = localFileFor(name, type)
        if (file.exists() && file.length() > 0) {
            openLocalFile(file)
            return
        }
        Toast.makeText(this, "جاري تنزيل: ${file.name}", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val cookie = CookieManager.getInstance().getCookie(url) ?: ""
                val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                    setRequestProperty("Cookie", cookie)
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                }
                connection.connect()
                if (connection.responseCode in 200..299) {
                    val tempFile = File(file.parentFile, "${file.name}.part")
                    connection.inputStream.use { input ->
                        java.io.FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    }
                    tempFile.renameTo(file)
                    runOnUiThread {
                        Toast.makeText(this, "تم التنزيل ✅", Toast.LENGTH_SHORT).show()
                        openLocalFile(file)
                        refreshList()
                    }
                } else {
                    runOnUiThread { Toast.makeText(this, "تعذّر تنزيل الملف (${connection.responseCode})", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "حدث خطأ أثناء التنزيل، تحقق من اتصالك بالإنترنت", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun openLocalFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val mime = guessMime(file.extension)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "تعذّر فتح الملف، تأكد من وجود تطبيق يدعم هذا النوع", Toast.LENGTH_SHORT).show()
        }
    }

    private fun guessMime(ext: String): String = when (ext.lowercase()) {
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "txt" -> "text/plain"
        else -> "*/*"
    }
}
                });
                if (cats.length > 0) out.push({ week: weekName, categories: cats });
            });
            return JSON.stringify(out);
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_course_materials)

        moodleId = intent.getStringExtra("moodleId") ?: ""
        courseName = intent.getStringExtra("courseName") ?: ""

        progressLayout = findViewById(R.id.progressLayout)
        statusText = findViewById(R.id.statusText)
        webView = findViewById(R.id.scrapeWebView)
        resultWebView = findViewById(R.id.resultWebView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        resultWebView.settings.javaScriptEnabled = true
        resultWebView.addJavascriptInterface(DownloadBridge(), "AndroidBridge")

        if (moodleId.isBlank()) {
            statusText.text = "تعذّر العثور على المادة في موقع التعليم الإلكتروني"
            return
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                view.evaluateJavascript(
                    "document.querySelector('input[name=\"password\"]') ? 'true' : 'false';"
                ) { stillOnLogin ->
                    if (stillOnLogin == "true") {
                        showResult(emptyList(), sessionExpired = true)
                        return@evaluateJavascript
                    }
                    view.evaluateJavascript(EXTRACTION_JS) { rawJson ->
                        try {
                            val unescaped = org.json.JSONTokener(rawJson).nextValue() as String
                            showResult(parseWeeks(unescaped), sessionExpired = false)
                        } catch (e: Exception) {
                            showResult(emptyList(), sessionExpired = false)
                        }
                    }
                }
            }
        }

        webView.loadUrl("https://elearning.asu.edu.bh/course/view.php?id=$moodleId&asupage=weekly")
    }

    private fun parseWeeks(json: String): List<MoodleWeek> {
        val arr = JSONArray(json)
        val weeks = mutableListOf<MoodleWeek>()
        for (i in 0 until arr.length()) {
            val weekObj = arr.optJSONObject(i) ?: continue
            val weekName = weekObj.optString("week")
            val catsArr = weekObj.optJSONArray("categories") ?: continue
            val cats = mutableListOf<MoodleCategory>()
            for (j in 0 until catsArr.length()) {
                val catObj = catsArr.optJSONObject(j) ?: continue
                val label = catObj.optString("label")
                val filesArr = catObj.optJSONArray("files") ?: continue
                val files = mutableListOf<MoodleFile>()
                for (k in 0 until filesArr.length()) {
                    val fileObj = filesArr.optJSONObject(k) ?: continue
                    files.add(
                        MoodleFile(
                            name = fileObj.optString("name"),
                            url = fileObj.optString("url"),
                            type = fileObj.optString("type")
                        )
                    )
                }
                if (files.isNotEmpty()) cats.add(MoodleCategory(label, files))
            }
            if (cats.isNotEmpty()) weeks.add(MoodleWeek(weekName, cats))
        }
        return weeks
    }

    private fun showResult(weeks: List<MoodleWeek>, sessionExpired: Boolean) {
        lastWeeks = weeks
        runOnUiThread {
            CourseMaterialsHtmlBuilder.LANG =
                getSharedPreferences("asu_prefs", MODE_PRIVATE).getString("lang", "ar") ?: "ar"
            val html = if (sessionExpired) {
                "<html dir='rtl'><body style='font-family:sans-serif;padding:20px;text-align:center;color:#888'>" +
                    "انتهت الجلسة، يرجى العودة إلى اللوحة الرئيسية والمحاولة مرة أخرى" +
                    "</body></html>"
            } else {
                CourseMaterialsHtmlBuilder.build(courseName, weeks, downloadedNames = downloadedFileNames())
            }
            resultWebView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            progressLayout.visibility = View.GONE
            resultWebView.visibility = View.VISIBLE
        }
    }

    /** Re-renders the same list with fresh "already downloaded" checkmarks, no re-fetch. */
    private fun refreshList() {
        CourseMaterialsHtmlBuilder.LANG = getSharedPreferences("asu_prefs", MODE_PRIVATE).getString("lang", "ar") ?: "ar"
        val html = CourseMaterialsHtmlBuilder.build(courseName, lastWeeks, downloadedNames = downloadedFileNames())
        resultWebView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    inner class DownloadBridge {
        @JavascriptInterface
        fun downloadFile(url: String, name: String, type: String) {
            runOnUiThread { openOrDownload(url, name, type) }
        }
    }

    private fun sanitizeFileName(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()

    private fun localFilesDir(): File {
        val dir = File(filesDir, "moodle_files/$moodleId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun localFileFor(name: String, type: String): File {
        val ext = type.trim().lowercase()
        val fileName = sanitizeFileName(name) + if (ext.isNotBlank() && !name.lowercase().endsWith(".$ext")) ".$ext" else ""
        return File(localFilesDir(), fileName)
    }

    private fun downloadedFileNames(): Set<String> =
        localFilesDir().listFiles()?.map { it.name }?.toSet() ?: emptySet()

    /**
     * First tap on a file: downloads it once into the app's own private
     * storage (not the public Downloads folder — that way we can reliably
     * tell later whether it's already there, and the file only shows up
     * inside this app, matching what was asked for). Every tap after that
     * just opens the already-downloaded copy directly, no network needed —
     * this is what makes it work from a basement classroom or an elevator
     * with no signal, as long as it was opened at least once before with a
     * connection.
     */
    private fun openOrDownload(url: String, name: String, type: String) {
        val file = localFileFor(name, type)
        if (file.exists() && file.length() > 0) {
            openLocalFile(file)
            return
        }
        Toast.makeText(this, "جاري تنزيل: ${file.name}", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val cookie = CookieManager.getInstance().getCookie(url) ?: ""
                val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                    setRequestProperty("Cookie", cookie)
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                }
                connection.connect()
                if (connection.responseCode in 200..299) {
                    val tempFile = File(file.parentFile, "${file.name}.part")
                    connection.inputStream.use { input ->
                        java.io.FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    }
                    tempFile.renameTo(file)
                    runOnUiThread {
                        Toast.makeText(this, "تم التنزيل ✅", Toast.LENGTH_SHORT).show()
                        openLocalFile(file)
                        refreshList()
                    }
                } else {
                    runOnUiThread { Toast.makeText(this, "تعذّر تنزيل الملف (${connection.responseCode})", Toast.LENGTH_SHORT).show() }
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "حدث خطأ أثناء التنزيل", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun openLocalFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val mime = guessMime(file.extension)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "تعذّر فتح الملف — تأكد من وجود تطبيق يدعم هذا النوع", Toast.LENGTH_SHORT).show()
        }
    }

    private fun guessMime(ext: String): String = when (ext.lowercase()) {
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "txt" -> "text/plain"
        else -> "*/*"
    }
}
