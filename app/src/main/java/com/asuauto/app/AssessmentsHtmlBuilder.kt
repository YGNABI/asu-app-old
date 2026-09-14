package com.asuauto.app

/**
 * One assignment or research item pulled from a course's Assessment Hub
 * (asupage=assessments), with its due date and instructions filled in
 * from the activity's own Turnitin page.
 */
data class AssessmentItem(
    val name: String,           // "الواجب", "البحث"
    val category: String,       // "Assignments" or "Research & Projects" — from the heading
    val url: String,
    val dueDateRaw: String,     // e.g. "15 Sept 2026 - 18:38" — empty if not found
    val instructionsText: String // plain-text instructions, empty if the instructor left it blank
)

object AssessmentsHtmlBuilder {

    var LANG = "ar"
    private fun t(ar: String, en: String) = if (LANG == "en") en else ar
    private fun esc(s: String) = s.replace("\"", "&quot;").replace("<", "&lt;")

    fun build(courseName: String, items: List<AssessmentItem>, hasExamDates: Boolean = false): String {
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
            .unifiedBtn{display:block;text-align:center;background:#1D9E75;color:#fff;font-size:13px;font-weight:bold;padding:12px;border-radius:10px;margin-bottom:14px}
            .card{background:#fff;border-radius:12px;padding:14px;margin-bottom:12px}
            .catPill{display:inline-block;color:#fff;font-size:10px;padding:2px 9px;border-radius:10px;background:#8e44ad;margin-bottom:8px}
            .itemName{font-size:14px;font-weight:bold;margin-bottom:6px}
            .dueRow{font-size:12px;color:#A32D2D;margin-bottom:8px}
            .dueRowNone{font-size:12px;color:#999;margin-bottom:8px}
            .instructions{font-size:12px;color:#444;line-height:1.6;background:#f7f7f8;border-radius:8px;padding:10px;margin-bottom:10px;white-space:pre-wrap}
            .instructionsNone{font-size:12px;color:#aaa;margin-bottom:10px}
            .addBtn{display:inline-block;background:#2c3e50;color:#fff;font-size:12px;padding:8px 14px;border-radius:8px}
            .empty{text-align:center;color:#aaa;padding:40px 10px}
            </style></head><body>
            <div class="header">${esc(courseName)} — ${t("الواجبات والبحوث", "Assignments & Research")}</div>
            <div class="page">
            """.trimIndent()
        )

        if (items.isNotEmpty() || hasExamDates) {
            sb.append(
                "<div class='unifiedBtn' onclick=\"addAllToCalendar()\">" +
                    t("أضف كل مواعيد هذه المادة للتقويم (الامتحانات + الواجبات + البحوث)", "Add all dates for this course to Calendar (exams + assignments + research)") +
                    "</div>"
            )
        }

        if (items.isEmpty()) {
            sb.append("<div class='empty'>${t("لا توجد واجبات أو بحوث مضافة لهذه المادة", "No assignments or research added for this course")}</div>")
        } else {
            for (item in items) {
                sb.append("<div class='card'>")
                sb.append("<div class='catPill'>${esc(item.category)}</div>")
                sb.append("<div class='itemName'>${esc(item.name)}</div>")
                if (item.dueDateRaw.isNotBlank()) {
                    sb.append("<div class='dueRow'>${t("آخر موعد للتسليم", "Due")}: ${esc(item.dueDateRaw)}</div>")
                } else {
                    sb.append("<div class='dueRowNone'>${t("لا يوجد موعد تسليم محدد", "No due date set")}</div>")
                }
                if (item.instructionsText.isNotBlank()) {
                    sb.append("<div class='instructions'>${esc(item.instructionsText)}</div>")
                } else {
                    sb.append("<div class='instructionsNone'>${t("لم يُضف المدرّس تعليمات لهذا الواجب/البحث", "No instructions added by the instructor")}</div>")
                }
                if (item.dueDateRaw.isNotBlank()) {
                    sb.append(
                        "<div class='addBtn' onclick=\"addToCalendar('${esc(item.name)}','${esc(item.dueDateRaw)}')\">" +
                            t("أضف للتقويم", "Add to Calendar") + "</div>"
                    )
                }
                sb.append("</div>")
            }
        }

        sb.append(
            """
            </div>
            <script>
            function addToCalendar(name,dueDateRaw){
                if(typeof AndroidBridge!=='undefined') AndroidBridge.addAssessmentToCalendar(name,dueDateRaw);
            }
            function addAllToCalendar(){
                if(typeof AndroidBridge!=='undefined') AndroidBridge.addAllToCalendar();
            }
            </script>
            </body></html>
            """.trimIndent()
        )
        return sb.toString()
    }
}
