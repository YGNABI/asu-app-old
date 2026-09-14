package com.asuauto.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CalendarContract
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray

class AssessmentsActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var resultWebView: WebView
    private lateinit var progressLayout: LinearLayout
    private lateinit var statusText: TextView

    private var moodleId = ""
    private var courseName = ""

    // Raw entries found on the Assessment Hub page: name, url, category label.
    // Filled in with due date + instructions one by one afterward.
    private data class RawEntry(val name: String, val url: String, val category: String)
    private val rawEntries = mutableListOf<RawEntry>()
    private val filledItems = mutableListOf<AssessmentItem>()
    private var fetchIndex = 0

    private var pendingEventTitle: String? = null
    private var pendingEventMillis: Long? = null

    // Only "Assignments" and "Research & Projects" categories — quizzes/exams are handled
    // elsewhere (the exam-date calendar buttons already on the course popup), matching what
    // was actually asked for here.
    private val HUB_EXTRACTION_JS = """
        (function() {
            var out = [];
            var wanted = { 'assessment-assessments': true, 'assessment-research': true };
            var groups = document.querySelectorAll('.asu-learning-group');
            groups.forEach(function(g) {
                var cat = g.getAttribute('data-asu-category') || '';
                if (!wanted[cat]) return;
                var headingH3 = g.querySelector('.asu-category-heading-list h3');
                var label = headingH3 ? headingH3.textContent.trim() : cat;
                var items = g.querySelectorAll('.asu-category-activity-list li.activity');
                items.forEach(function(li) {
                    if (li.className.indexOf('turnitintooltwo') === -1) return;
                    var a = li.querySelector('a.aalink');
                    if (!a) return;
                    var nameSpan = li.querySelector('.instancename');
                    var name = '';
                    if (nameSpan && nameSpan.childNodes.length > 0) {
                        name = (nameSpan.childNodes[0].textContent || '').trim();
                    }
                    if (!name) name = (a.textContent || '').trim();
                    out.push({ name: name, url: a.href, category: label });
                });
            });
            return JSON.stringify(out);
        })();
    """.trimIndent()

    private val ITEM_EXTRACTION_JS = """
        (function() {
            var dueDate = '';
            var rows = document.querySelectorAll('.mod_turnitintooltwo_part_details tbody tr');
            for (var i = 0; i < rows.length; i++) {
                var cells = rows[i].querySelectorAll('td.data.cell');
                if (cells.length >= 3) { dueDate = cells[2].textContent.trim(); break; }
            }
            var introEl = document.querySelector('.activity-description#intro');
            var instructions = introEl ? (introEl.innerText || introEl.textContent || '').trim() : '';
            return JSON.stringify({ dueDateRaw: dueDate, instructions: instructions });
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assessments)

        moodleId = intent.getStringExtra("moodleId") ?: ""
        courseName = intent.getStringExtra("courseName") ?: ""

        progressLayout = findViewById(R.id.progressLayout)
        statusText = findViewById(R.id.statusText)
        webView = findViewById(R.id.scrapeWebView)
        resultWebView = findViewById(R.id.resultWebView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        resultWebView.settings.javaScriptEnabled = true
        resultWebView.addJavascriptInterface(AssessmentsBridge(), "AndroidBridge")

        if (moodleId.isBlank()) {
            statusText.text = "ما قدرنا نلقى المادة على موقع التعليم الالكتروني"
            return
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                if (fetchIndex == 0 && rawEntries.isEmpty() && filledItems.isEmpty()) {
                    // First load: the Assessment Hub page itself.
                    view.evaluateJavascript(
                        "document.querySelector('input[name=\"password\"]') ? 'true' : 'false';"
                    ) { stillOnLogin ->
                        if (stillOnLogin == "true") {
                            showResult(emptyList(), sessionExpired = true)
                            return@evaluateJavascript
                        }
                        view.evaluateJavascript(HUB_EXTRACTION_JS) { rawJson ->
                            try {
                                val unescaped = org.json.JSONTokener(rawJson).nextValue() as String
                                val arr = JSONArray(unescaped)
                                for (i in 0 until arr.length()) {
                                    val obj = arr.optJSONObject(i) ?: continue
                                    rawEntries.add(
                                        RawEntry(
                                            name = obj.optString("name"),
                                            url = obj.optString("url"),
                                            category = obj.optString("category")
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                // fall through with an empty list
                            }
                            fetchNextItemOrFinish(view)
                        }
                    }
                } else if (fetchIndex in 1..rawEntries.size) {
                    // We're on the page of item number (fetchIndex - 1).
                    view.evaluateJavascript(ITEM_EXTRACTION_JS) { rawJson ->
                        try {
                            val unescaped = org.json.JSONTokener(rawJson).nextValue() as String
                            val obj = org.json.JSONObject(unescaped)
                            val entry = rawEntries[fetchIndex - 1]
                            filledItems.add(
                                AssessmentItem(
                                    name = entry.name,
                                    category = entry.category,
                                    url = entry.url,
                                    dueDateRaw = obj.optString("dueDateRaw"),
                                    instructionsText = obj.optString("instructions")
                                )
                            )
                        } catch (e: Exception) {
                            val entry = rawEntries[fetchIndex - 1]
                            filledItems.add(AssessmentItem(entry.name, entry.category, entry.url, "", ""))
                        }
                        fetchNextItemOrFinish(view)
                    }
                }
            }
        }

        webView.loadUrl("https://elearning.asu.edu.bh/course/view.php?id=$moodleId&asupage=assessments")
    }

    private fun fetchNextItemOrFinish(view: WebView) {
        if (fetchIndex < rawEntries.size) {
            statusText.text = "جاري جلب الواجب ${fetchIndex + 1} من ${rawEntries.size}..."
            fetchIndex++
            view.loadUrl(rawEntries[fetchIndex - 1].url)
        } else {
            showResult(filledItems, sessionExpired = false)
        }
    }

    private fun showResult(items: List<AssessmentItem>, sessionExpired: Boolean) {
        runOnUiThread {
            AssessmentsHtmlBuilder.LANG =
                getSharedPreferences("asu_prefs", MODE_PRIVATE).getString("lang", "ar") ?: "ar"
            val html = if (sessionExpired) {
                "<html dir='rtl'><body style='font-family:sans-serif;padding:20px;text-align:center;color:#888'>" +
                    "انتهت الجلسة، رجع للوحة الرئيسية وحاول مرة ثانية" +
                    "</body></html>"
            } else {
                AssessmentsHtmlBuilder.build(courseName, items)
            }
            resultWebView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            progressLayout.visibility = View.GONE
            resultWebView.visibility = View.VISIBLE
        }
    }

    inner class AssessmentsBridge {
        @JavascriptInterface
        fun addAssessmentToCalendar(name: String, dueDateRaw: String) {
            runOnUiThread { addToCalendar(name, dueDateRaw) }
        }
    }

    // Moodle/Turnitin renders due dates like "15 Sept 2026 - 18:38".
    private fun parseDueDate(raw: String): Long? {
        try {
            val parts = raw.split(" - ")
            if (parts.size != 2) return null
            val dateParts = parts[0].trim().split(" ")
            if (dateParts.size != 3) return null
            val day = dateParts[0].trim().toIntOrNull() ?: return null
            val monthStr = dateParts[1].trim().lowercase().take(3)
            val year = dateParts[2].trim().toIntOrNull() ?: return null
            val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
            val month = months.indexOf(monthStr)
            if (month < 0) return null
            val timeParts = parts[1].trim().split(":")
            val hour = timeParts.getOrNull(0)?.trim()?.toIntOrNull() ?: 23
            val minute = timeParts.getOrNull(1)?.trim()?.toIntOrNull() ?: 59
            val cal = java.util.Calendar.getInstance()
            cal.set(year, month, day, hour, minute, 0)
            return cal.timeInMillis
        } catch (e: Exception) {
            return null
        }
    }

    private fun addToCalendar(title: String, dueDateRaw: String) {
        val millis = parseDueDate(dueDateRaw)
        if (millis == null) {
            Toast.makeText(this, "ما قدرنا نفهم تاريخ التسليم", Toast.LENGTH_SHORT).show()
            return
        }
        pendingEventTitle = title
        pendingEventMillis = millis
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR),
                102
            )
        } else {
            insertAssessmentEvent(title, millis)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 102 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            val title = pendingEventTitle
            val millis = pendingEventMillis
            if (title != null && millis != null) insertAssessmentEvent(title, millis)
        } else if (requestCode == 102) {
            Toast.makeText(this, "نحتاج صلاحية التقويم لإضافة الموعد", Toast.LENGTH_SHORT).show()
        }
        pendingEventTitle = null
        pendingEventMillis = null
    }

    private fun primaryCalendarId(): Long? {
        val proj = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
        val cursor = contentResolver.query(CalendarContract.Calendars.CONTENT_URI, proj, null, null, null)
        cursor?.use {
            while (it.moveToNext()) {
                if (it.getInt(1) == 1) return it.getLong(0)
            }
            if (it.moveToFirst()) return it.getLong(0)
        }
        return null
    }

    private fun insertAssessmentEvent(title: String, startMillis: Long) {
        try {
            val calId = primaryCalendarId()
            if (calId == null) {
                Toast.makeText(this, "ما قدرنا نلقى تقويم بالجهاز", Toast.LENGTH_SHORT).show()
                return
            }
            val existsCursor = contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events._ID),
                "${CalendarContract.Events.TITLE} = ? AND ${CalendarContract.Events.DTSTART} = ?",
                arrayOf(title, startMillis.toString()),
                null
            )
            val alreadyExists = existsCursor?.use { it.count > 0 } ?: false
            if (alreadyExists) {
                Toast.makeText(this, "الموعد مضاف مسبقًا بالتقويم", Toast.LENGTH_SHORT).show()
                return
            }

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calId)
                put(CalendarContract.Events.TITLE, "تسليم: $title")
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, startMillis + 60 * 60 * 1000)
                put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
            }
            val eventUri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val eventId = eventUri?.lastPathSegment?.toLongOrNull()
            if (eventId != null) {
                // Reminder 7 days before the due date, as agreed.
                val reminderValues = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, 7 * 24 * 60)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
            }
            Toast.makeText(this, "انضاف للتقويم ✅ (تذكير قبل أسبوع)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "صار خطأ بالإضافة للتقويم", Toast.LENGTH_SHORT).show()
        }
    }
}
