package com.asuauto.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray

class CourseMaterialsActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var resultWebView: WebView
    private lateinit var progressLayout: LinearLayout
    private lateinit var statusText: TextView

    private var moodleId = ""
    private var courseName = ""

    // Extraction JS: walks each weekly section, skips categories whose
    // activity list only contains the "asu-category-empty" placeholder
    // (real HTML confirmed this is how Moodle marks a category with no
    // files uploaded), and returns name/url/type for every real file.
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
            statusText.text = "ما قدرنا نلقى المادة على موقع التعليم الالكتروني"
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
        runOnUiThread {
            CourseMaterialsHtmlBuilder.LANG =
                getSharedPreferences("asu_prefs", MODE_PRIVATE).getString("lang", "ar") ?: "ar"
            val html = if (sessionExpired) {
                "<html dir='rtl'><body style='font-family:sans-serif;padding:20px;text-align:center;color:#888'>" +
                    "انتهت الجلسة، رجع للوحة الرئيسية وحاول مرة ثانية" +
                    "</body></html>"
            } else {
                CourseMaterialsHtmlBuilder.build(courseName, weeks)
            }
            resultWebView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            progressLayout.visibility = View.GONE
            resultWebView.visibility = View.VISIBLE
        }
    }

    inner class DownloadBridge {
        @JavascriptInterface
        fun downloadFile(url: String, name: String, type: String) {
            runOnUiThread { startDownload(url, name, type) }
        }
    }

    private fun startDownload(url: String, name: String, type: String) {
        try {
            val cookie = CookieManager.getInstance().getCookie(url) ?: ""
            val extension = type.trim().lowercase().ifBlank { "" }
            val fileName = if (extension.isNotBlank() && !name.lowercase().endsWith(".$extension")) {
                "$name.$extension"
            } else {
                name
            }

            val request = DownloadManager.Request(Uri.parse(url))
                .addRequestHeader("Cookie", cookie)
                .setTitle(fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            android.widget.Toast.makeText(this, "جاري تنزيل: $fileName", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "صار خطأ بالتنزيل", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
