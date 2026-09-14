package com.asuauto.app

/**
 * One downloadable file extracted from a Moodle weekly-content category.
 */
data class MoodleFile(
    val name: String,
    val url: String,
    val type: String // "PDF", "DOCX", etc — from the Moodle activity badge
)

data class MoodleCategory(
    val label: String,       // "Slides", "Additional Materials"...
    val files: List<MoodleFile>
)

data class MoodleWeek(
    val week: String,        // "Week 1", "Course Home"...
    val categories: List<MoodleCategory>
)

/**
 * Renders the weekly-materials list as HTML, reusing the same look
 * (card/list/row classes) as DashboardHtmlBuilder so it feels like part
 * of the same app instead of a raw Moodle page.
 */
object CourseMaterialsHtmlBuilder {

    var LANG = "ar"
    private fun t(ar: String, en: String) = if (LANG == "en") en else ar
    private fun esc(s: String) = s.replace("\"", "&quot;").replace("<", "&lt;")

    private fun sanitizeFileName(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()

    private fun expectedFileName(name: String, type: String): String {
        val ext = type.trim().lowercase()
        return sanitizeFileName(name) + if (ext.isNotBlank() && !name.lowercase().endsWith(".$ext")) ".$ext" else ""
    }

    fun build(courseName: String, weeks: List<MoodleWeek>, downloadedNames: Set<String> = emptySet()): String {
        val dir = if (LANG == "en") "ltr" else "rtl"
        val sb = StringBuilder()
        sb.append(
            """
            <html dir="$dir"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
            body{font-family:sans-serif;background:#f2f3f5;margin:0}
            .header{position:sticky;top:0;background:#2c3e50;color:#fff;padding:14px;font-size:15px;font-weight:bold}
            .page{padding:12px}
            .weekTitle{background:#e3e8ee;border-radius:8px;padding:9px 12px;margin:14px 0 8px;font-size:13px;font-weight:bold}
            .catTitle{font-size:12px;color:#666;margin:10px 0 6px}
            .list{background:#fff;border-radius:12px;overflow:hidden}
            .row{display:flex;justify-content:space-between;align-items:center;padding:12px;border-bottom:1px solid #f0f0f0;cursor:pointer}
            .row:last-child{border-bottom:none}
            .rowTitle{font-size:13px}
            .rowMeta{display:flex;align-items:center;gap:6px;flex-shrink:0;margin-inline-start:8px}
            .pill{color:#fff;font-size:10px;padding:2px 9px;border-radius:10px;background:#3498db}
            .savedTick{color:#1D9E75;font-size:14px}
            .empty{text-align:center;color:#aaa;padding:40px 10px}
            </style></head><body>
            <div class="header">${esc(courseName)}</div>
            <div class="page">
            """.trimIndent()
        )

        if (weeks.isEmpty()) {
            sb.append("<div class='empty'>${t("لا توجد مواد تعليمية مرفوعة لهذه المادة", "No course materials uploaded for this course")}</div>")
        } else {
            for (week in weeks) {
                sb.append("<div class='weekTitle'>${esc(week.week)}</div>")
                for (cat in week.categories) {
                    if (cat.label.isNotBlank()) sb.append("<div class='catTitle'>${esc(cat.label)}</div>")
                    sb.append("<div class='list'>")
                    for (f in cat.files) {
                        val saved = expectedFileName(f.name, f.type) in downloadedNames
                        sb.append("<div class='row' onclick=\"downloadFile('${esc(f.url)}','${esc(f.name)}','${esc(f.type)}')\">")
                        sb.append("<div class='rowTitle'>${esc(f.name)}</div>")
                        sb.append("<div class='rowMeta'>")
                        if (saved) sb.append("<span class='savedTick' title='${t("محفوظ على الجهاز", "Saved on device")}'>&#10004;</span>")
                        if (f.type.isNotBlank()) sb.append("<span class='pill'>${esc(f.type)}</span>")
                        sb.append("</div></div>")
                    }
                    sb.append("</div>")
                }
            }
        }

        sb.append(
            """
            </div>
            <script>
            function downloadFile(url,name,type){
                if(typeof AndroidBridge!=='undefined') AndroidBridge.downloadFile(url,name,type);
            }
            </script>
            </body></html>
            """.trimIndent()
        )
        return sb.toString()
    }
}
