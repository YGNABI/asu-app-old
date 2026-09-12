package com.asuauto.app

object DashboardHtmlBuilder {

    private fun esc(s: String) = s.replace("\"", "&quot;").replace("<", "&lt;")

    private fun colIndex(header: List<String>, contains: String): Int {
        for (i in header.indices) if (header[i].contains(contains)) return i
        return -1
    }

    private fun colIndexAny(header: List<String>, candidates: List<String>): Int {
        for (needle in candidates) {
            val idx = colIndex(header, needle)
            if (idx >= 0) return idx
        }
        return -1
    }

    private fun kvAny(rows: List<List<String>>, candidates: List<String>): String {
        for (label in candidates) {
            val v = kv(rows, label)
            if (v.isNotBlank()) return v
        }
        return ""
    }

    private fun kv(rows: List<List<String>>, label: String): String {
        for (row in rows) {
            if (row.size >= 2) {
                for (i in row.indices) {
                    if (row[i].trim() == label) {
                        val other = if (i == 0) row.getOrNull(1) else row.getOrNull(0)
                        return other?.trim() ?: ""
                    }
                }
            }
        }
        return ""
    }

    private fun normCode(s: String) = s.uppercase().replace(Regex("[^A-Z0-9]"), "")

    private fun to12h(time: String): String {
        val parts = time.trim().split(":")
        if (parts.size < 2) return time
        val h = parts[0].toIntOrNull() ?: return time
        val m = parts[1]
        val period = if (h < 12) "ص" else "م"
        var h12 = h % 12
        if (h12 == 0) h12 = 12
        return "$h12:$m $period"
    }

    private fun daysToArabic(code: String): String {
        val map = mapOf('U' to "الأحد", 'M' to "الاثنين", 'T' to "الثلاثاء", 'W' to "الأربعاء", 'H' to "الخميس", 'F' to "الجمعة", 'S' to "السبت")
        return code.trim().map { map[it] ?: it.toString() }.joinToString("، ")
    }

    private fun attendanceColor(pct: Double): Triple<String, String, Boolean> {
        return when {
            pct <= 10.0 -> Triple("#EAF3DE", "#3B6D11", false)
            pct <= 15.0 -> Triple("#FAEEDA", "#854F0B", false)
            pct <= 25.0 -> Triple("#FCEBEB", "#A32D2D", false)
            else -> Triple("#1a1a1a", "#ffffff", true)
        }
    }

    private fun gradeColor(g: Double): String = when {
        g >= 90 -> "#1D9E75"
        g >= 80 -> "#0F6E56"
        g >= 70 -> "#378ADD"
        g >= 60 -> "#BA7517"
        g >= 50 -> "#E24B4A"
        else -> "#1a1a1a"
    }

    private fun warningColor(status: String): Triple<String, String, String> {
        val s = status.trim()
        return when {
            s.isEmpty() || s == "-" -> Triple("#EAF3DE", "#3B6D11", "لا يوجد")
            s.contains("اول") || s.contains("أول") -> Triple("#FAEEDA", "#854F0B", s)
            s.contains("ثاني") -> Triple("#FCEBEB", "#A32D2D", s)
            s.contains("ثالث") -> Triple("#1a1a1a", "#ffffff", s)
            else -> Triple("#EAF3DE", "#3B6D11", s)
        }
    }

    data class CourseInfo(
        var code: String = "",
        var nameAr: String = "",
        var nameEn: String = "",
        var section: String = "",
        var days: String = "",
        var timeFrom: String = "",
        var timeTo: String = "",
        var room: String = "",
        var instructor: String = "",
        var midtermDate: String = "",
        var midtermTime: String = "",
        var midtermRoom: String = "",
        var finalDate: String = "",
        var finalTime: String = "",
        var finalRoom: String = "",
        var attendancePct: Double? = null,
        var midtermGrade: String = "",
        var otherWorkGrade: String = "",
        var finalGrade: String = "",
        var gradeStatus: String = ""
    )

    private fun buildCourseMap(): LinkedHashMap<String, CourseInfo> {
        val map = LinkedHashMap<String, CourseInfo>()

        val reg = DataStore.registration
        if (reg.isNotEmpty()) {
            val h = reg[0]
            val codeIdx = colIndex(h, "رمز")
            val nameIdx = colIndex(h, "اسم المقرر")
            val secIdx = colIndex(h, "الشعبه").takeIf { it >= 0 } ?: colIndex(h, "الشعبة")
            val daysIdx = colIndex(h, "الايام")
            val fromIdx = colIndex(h,
