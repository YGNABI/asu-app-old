package com.asuauto.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray

class SosExamExcuseActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var resultWebView: WebView
    private lateinit var progressLayout: LinearLayout
    private lateinit var statusText: TextView

    private val LIST_URL = "https://sos.asu.edu.bh/ords/r/asudss/sos/66"

    private val SCRAPE_JS = """
        (function() {
            var out = [];
            var table = document.querySelector('table.t-Report-report');
            if (!table) return JSON.stringify(out);
            var rows = table.querySelectorAll('tbody tr');
            rows.forEach(function(tr){
                var cells = tr.querySelectorAll('td');
                if (cells.length < 8) return;
                var link = cells[5].querySelector('a');
                out.push({
                    ref: cells[0].textContent.trim(),
                    date: cells[1].textContent.trim(),
                    course: cells[2].textContent.trim(),
                    reason: cells[3].textContent.trim(),
                    courseDesc: cells[4].textContent.trim(),
                    attachUrl: link ? link.href : '',
                    term: cells[6].textContent.trim(),
                    status: cells[7].textContent.trim()
                });
            });
            return JSON.stringify(out);
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_course_materials)

        progressLayout = findViewById(R.id.progressLayout)
        statusText = findViewById(R.id.statusText)
        webView = findViewById(R.id.scrapeWebView)
        resultWebView = findViewById(R.id.resultWebView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        resultWebView.settings.javaScriptEnabled = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                view.evaluateJavascript(SCRAPE_JS) { rawJson ->
                    val rows = parseRows(rawJson)
                    showResult(rows)
                }
            }
        }
        webView.loadUrl(LIST_URL)
    }

    private fun parseRows(rawJson: String?): List<JSONObjectLite> {
        if (rawJson.isNullOrBlank()) return emptyList()
        return try {
            val unescaped = org.json.JSONTokener(rawJson).nextValue() as String
            val arr = JSONArray(unescaped)
            val list = mutableListOf<JSONObjectLite>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                list.add(
                    JSONObjectLite(
                        ref = o.optString("ref"),
                        date = o.optString("date"),
                        course = o.optString("course"),
                        reason = o.optString("reason"),
                        courseDesc = o.optString("courseDesc"),
                        attachUrl = o.optString("attachUrl"),
                        term = o.optString("term"),
                        status = o.optString("status")
                    )
                )
            }
            list
        } catch (e: Exception) { emptyList() }
    }

    private fun showResult(rows: List<JSONObjectLite>) {
        runOnUiThread {
            val lang = getSharedPreferences("asu_prefs", MODE_PRIVATE).getString("lang", "ar") ?: "ar"
            val html = buildHtml(lang, rows)
            resultWebView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            progressLayout.visibility = View.GONE
            resultWebView.visibility = View.VISIBLE
        }
    }

    private data class JSONObjectLite(
        val ref: String, val date: String, val course: String, val reason: String,
        val courseDesc: String, val attachUrl: String, val term: String, val status: String
    )

    private fun esc(s: String) = s.replace("\"", "&quot;").replace("<", "&lt;")

    private fun buildHtml(lang: String, rows: List<JSONObjectLite>): String {
        val ar = lang != "en"
        fun t(a: String, e: String) = if (ar) a else e
        val dir = if (ar) "rtl" else "ltr"
        val sb = StringBuilder()
        sb.append(
            """
            <html dir="$dir"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
            body{font-family:sans-serif;background:#f2f3f5;margin:0}
            .header{position:sticky;top:0;background:#2c3e50;color:#fff;padding:14px;font-size:15px;font-weight:bold}
            .page{padding:12px}
            .card{background:#fff;border-radius:12px;padding:14px;margin-bottom:12px}
            .refRow{display:flex;justify-content:space-between;align-items:center;margin-bottom:8px}
            .ref{font-size:12px;color:#999}
            .status{font-size:11px;color:#fff;background:#7f8c8d;padding:3px 10px;border-radius:10px}
            .course{font-size:14px;font-weight:bold;margin-bottom:4px}
            .desc{font-size:12px;color:#444;margin-bottom:6px}
            .reason{font-size:12px;color:#666;background:#f7f7f8;border-radius:8px;padding:8px;margin-bottom:8px}
            .metaRow{display:flex;justify-content:space-between;font-size:11px;color:#888}
            .attachLink{display:inline-block;margin-top:8px;font-size:12px;color:#2c7be5}
            .empty{text-align:center;color:#aaa;padding:40px 10px}
            .noteBanner{background:#fff3cd;color:#856404;font-size:12px;padding:10px 12px;margin:0 0 12px}
            </style></head><body>
            <div class="header">${t("أعذار امتحانات غير المكتمل", "Incomplete Exam Excuses")}</div>
            <div class="page">
            <div class="noteBanner">${t(
                "عرض السجل التاريخي فقط. تقديم عذر جديد يتوفر عند فتح باب التقديم قبل/بعد الامتحانات النهائية.",
                "History view only. Submitting a new excuse becomes available when the submission window opens before/after final exams."
            )}</div>
            """.trimIndent()
        )

        if (rows.isEmpty()) {
            sb.append("<div class='empty'>${t("لا توجد سجلات", "No records")}</div>")
        } else {
            for (r in rows) {
                sb.append("<div class='card'>")
                sb.append("<div class='refRow'><span class='ref'>#${esc(r.ref)}</span><span class='status'>${esc(r.status)}</span></div>")
                sb.append("<div class='course'>${esc(r.course)} — ${esc(r.courseDesc)}</div>")
                if (r.reason.isNotBlank()) sb.append("<div class='reason'>${esc(r.reason)}</div>")
                sb.append("<div class='metaRow'><span>${esc(r.date)}</span><span>${esc(r.term)}</span></div>")
                if (r.attachUrl.isNotBlank()) {
                    sb.append("<a class='attachLink' href=\"${esc(r.attachUrl)}\" target=\"_blank\">${t("عرض المرفق", "View Attachment")}</a>")
                }
                sb.append("</div>")
            }
        }

        sb.append("</div></body></html>")
        return sb.toString()
    }
}
