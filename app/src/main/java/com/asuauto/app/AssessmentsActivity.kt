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
    private var midDate = ""
    private var midTime = ""
    private var midRoom = ""
    private var finDate = ""
    private var finTime = ""
    private var finRoom = ""

    // Raw entries found on the Assessment Hub page: name, url, category label.
    // Filled in with due date + instructions one by one afterward.
    private data class RawEntry(val name: String, val url: String, val category: String)
    private val rawEntries = mutableListOf<RawEntry>()
    private val filledItems = mutableListOf<AssessmentItem>()
    private var fetchIndex = 0

    private var pendingCalendarAction: (() -> Unit)? = null

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
        midDate = intent.getStringExtra("midDate") ?: ""
        midTime = intent.getStringExtra("midTime") ?: ""
        midRoom = intent.getStringExtra("midRoom") ?: ""
        finDate = intent.getStringExtra("finDate") ?: ""
        finTime = intent.getStringExtra("finTime") ?: ""
        finRoom = intent.getStringExtra("finRoom") ?: ""

        progressLayout = findViewById(R.id.progressLayout)
        statusText = findViewById(R.id.statusText)
        webView = findViewById(R.id.scrapeWebView)
        resultWebView = findViewById(R.id.resultWebView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        resultWebView.settings.javaScriptEnabled = true
        resultWebView.addJavascriptInterface(AssessmentsBridge(), "AndroidBridge")

        if (moodleId.isBlank()) {
            statusText.text = "تعذّر العثور على المادة في موقع التعليم الإلكتروني"
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
                    "انتهت الجلسة، يرجى العودة إلى اللوحة الرئيسية والمحاولة مرة أخرى" +
                    "</body></html>"
            } else {
                AssessmentsHtmlBuilder.build(courseName, items, hasExamDates = midDate.isNotBlank() || finDate.isNotBlank())
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

        @JavascriptInterface
        fun addAllToCalendar() {
            runOnUiThread { addAllForCourse() }
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
            Toast.makeText(this, "تعذّر فهم تاريخ التسليم", Toast.LENGTH_SHORT).show()
            return
        }
        ensureCalendarPermission {
            val result = insertCalendarEventGeneric("تسليم: $title", millis, 7)
            val msg = when (result) {
                InsertResult.ADDED -> "تمت الإضافة إلى التقويم ✅ (تذكير قبل أسبوع)"
                InsertResult.DUPLICATE -> "الموعد مضاف مسبقًا إلى التقويم"
                InsertResult.ERROR -> "حدث خطأ أثناء الإضافة إلى التقويم"
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * The "one button per course" the user asked for: adds the midterm exam,
     * final exam, and every assignment/research due date already loaded on
     * this screen, in a single tap — each with its own correct reminder lead
     * time (10 days for exams, 7 for assignments/research).
     */
    private fun addAllForCourse() {
        ensureCalendarPermission {
            var added = 0
            var dup = 0
            var failed = 0

            fun tally(r: InsertResult) {
                when (r) {
                    InsertResult.ADDED -> added++
                    InsertResult.DUPLICATE -> dup++
                    InsertResult.ERROR -> failed++
                }
            }

            if (midDate.isNotBlank()) {
                parseExamDateTime(midDate, midTime)?.let {
                    tally(insertCalendarEventGeneric("امتحان المنتصف: $courseName", it, 10, midRoom))
                }
            }
            if (finDate.isNotBlank()) {
                parseExamDateTime(finDate, finTime)?.let {
                    tally(insertCalendarEventGeneric("الامتحان النهائي: $courseName", it, 10, finRoom))
                }
            }
            for (item in filledItems) {
                if (item.dueDateRaw.isBlank()) continue
                parseDueDate(item.dueDateRaw)?.let {
                    tally(insertCalendarEventGeneric("تسليم: ${item.name}", it, 7))
                }
            }

            val msg = when {
                added == 0 && dup == 0 && failed == 0 -> "ما فيه مواعيد متوفرة لهذه المادة"
                failed > 0 -> "أُضيف $added موعدًا، و$dup مضاف مسبقًا، وتعذّرت إضافة $failed"
                dup > 0 -> "أُضيف $added موعدًا، و$dup مضاف مسبقًا بالتقويم"
                else -> "أُضيفت جميع مواعيد المادة إلى التقويم ✅ ($added)"
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun ensureCalendarPermission(action: () -> Unit) {
        pendingCalendarAction = action
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR),
                102
            )
        } else {
            action()
            pendingCalendarAction = null
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 102 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            pendingCalendarAction?.invoke()
        } else if (requestCode == 102) {
            Toast.makeText(this, "يلزم منح صلاحية التقويم لإضافة الموعد", Toast.LENGTH_SHORT).show()
        }
        pendingCalendarAction = null
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

    private enum class InsertResult { ADDED, DUPLICATE, ERROR }

    private fun insertCalendarEventGeneric(title: String, startMillis: Long, reminderDays: Int, location: String = ""): InsertResult {
        return try {
            val calId = primaryCalendarId() ?: return InsertResult.ERROR
            val existsCursor = contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events._ID),
                "${CalendarContract.Events.TITLE} = ? AND ${CalendarContract.Events.DTSTART} = ?",
                arrayOf(title, startMillis.toString()),
                null
            )
            val alreadyExists = existsCursor?.use { it.count > 0 } ?: false
            if (alreadyExists) return InsertResult.DUPLICATE

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calId)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, startMillis + 60 * 60 * 1000)
                put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
                if (location.isNotBlank()) put(CalendarContract.Events.EVENT_LOCATION, location)
            }
            val eventUri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val eventId = eventUri?.lastPathSegment?.toLongOrNull()
            if (eventId != null) {
                val reminderValues = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, reminderDays * 24 * 60)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
            }
            InsertResult.ADDED
        } catch (e: Exception) {
            InsertResult.ERROR
        }
    }

    // SIS renders exam dates as "DD-MM-YYYY" with a separate 24h "HH:MM" time —
    // same format already relied on by SisDashboardActivity's own exam calendar buttons.
    private fun parseExamDateTime(dateStr: String, timeStr: String): Long? {
        return try {
            val parts = dateStr.trim().split("-")
            if (parts.size != 3) return null
            val day = parts[0].trim().toIntOrNull() ?: return null
            val month = parts[1].trim().toIntOrNull() ?: return null
            val year = parts[2].trim().toIntOrNull() ?: return null
            var hour = 9
            var minute = 0
            if (timeStr.isNotBlank()) {
                val tp = timeStr.trim().split(":")
                hour = tp.getOrNull(0)?.trim()?.toIntOrNull() ?: 9
                minute = tp.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
            }
            val cal = java.util.Calendar.getInstance()
            cal.set(year, month - 1, day, hour, minute, 0)
            cal.timeInMillis
        } catch (e: Exception) {
            null
        }
    }
}
