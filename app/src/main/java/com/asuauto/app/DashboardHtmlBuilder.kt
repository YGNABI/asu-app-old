package com.asuauto.app

object DashboardHtmlBuilder {

    private fun esc(s: String) = s.replace("\"", "&quot;").replace("<", "&lt;")

    private fun colIndex(header: List<String>, contains: String): Int {
        for (i in header.indices) if (header[i].contains(contains)) return i
        return -1
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
            val fromIdx = colIndex(h, "من")
            val toIdx = colIndex(h, "الى")
            val roomIdx = colIndex(h, "القاعة")
            val teacherIdx = colIndex(h, "اسم المدرس")
            val midDateIdx = colIndex(h, "تاريخ امتحان المنتصف")
            val midTimeIdx = colIndex(h, "وقت امتحان المنتصف")
            val midRoomIdx = colIndex(h, "قاعة امتحان المنتصف")
            val finDateIdx = colIndex(h, "تاريخ الامتحان النهائي")
            val finTimeIdx = colIndex(h, "وقت الامتحان النهائي")
            val finRoomIdx = colIndex(h, "قاعة الامتحان النهائي")
            for (r in 1 until reg.size) {
                val row = reg[r]
                fun cell(i: Int) = if (i in row.indices) row[i] else ""
                val code = cell(codeIdx)
                if (code.isBlank()) continue
                val info = CourseInfo(
                    code = code,
                    nameEn = cell(nameIdx),
                    section = cell(secIdx),
                    days = daysToArabic(cell(daysIdx)),
                    timeFrom = to12h(cell(fromIdx)),
                    timeTo = to12h(cell(toIdx)),
                    room = cell(roomIdx),
                    instructor = cell(teacherIdx),
                    midtermDate = cell(midDateIdx),
                    midtermTime = to12h(cell(midTimeIdx)),
                    midtermRoom = cell(midRoomIdx),
                    finalDate = cell(finDateIdx),
                    finalTime = to12h(cell(finTimeIdx)),
                    finalRoom = cell(finRoomIdx)
                )
                map[normCode(code)] = info
            }
        }

        val att = DataStore.attendance
        if (att.isNotEmpty()) {
            val h = att[0]
            val codeIdx = colIndex(h, "رمز")
            val pctIdx = colIndex(h, "الغياب")
            val nameIdx = colIndex(h, "اسم المقرر")
            for (r in 1 until att.size) {
                val row = att[r]
                fun cell(i: Int) = if (i in row.indices) row[i] else ""
                val code = cell(codeIdx)
                if (code.isBlank()) continue
                val key = normCode(code)
                val info = map.getOrPut(key) { CourseInfo(code = code) }
                if (info.nameAr.isBlank()) info.nameAr = cell(nameIdx)
                val pctStr = cell(pctIdx).replace("%", "").trim()
                info.attendancePct = pctStr.toDoubleOrNull()
            }
        }

        val sg = DataStore.semesterGrades
        if (sg.isNotEmpty()) {
            val h = sg[0]
            val codeIdx = colIndex(h, "رمز")
            val nameIdx = colIndex(h, "اسم المقرر")
            val statusIdx = colIndex(h, "حالة العلامة")
            val finalIdx = colIndex(h, "النهائي")
            val otherIdx = colIndex(h, "أعمال اخرى").takeIf { it >= 0 } ?: colIndex(h, "أعمال أخرى")
            val midIdx = colIndex(h, "المنتصف")
            for (r in 1 until sg.size) {
                val row = sg[r]
                fun cell(i: Int) = if (i in row.indices) row[i] else ""
                val code = cell(codeIdx)
                if (code.isBlank()) continue
                val key = normCode(code)
                val info = map.getOrPut(key) { CourseInfo(code = code) }
                if (info.nameAr.isBlank()) info.nameAr = cell(nameIdx)
                info.gradeStatus = cell(statusIdx)
                info.finalGrade = cell(finalIdx)
                info.otherWorkGrade = cell(otherIdx)
                info.midtermGrade = cell(midIdx)
            }
        }

        return map
    }

    fun build(): String {
        val sb = StringBuilder()
        sb.append(
            """
            <html dir="rtl" lang="ar"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
                body { font-family: sans-serif; background:#f4f4f4; margin:0; padding:0; }
                .tabs { display:flex; position:sticky; top:0; background:#2c3e50; z-index:20; }
                .tab { flex:1; text-align:center; padding:12px 2px; color:#fff; cursor:pointer; font-size:12px; }
                .tab.active { background:#34495e; border-bottom:3px solid #3498db; }
                .page { display:none; padding:12px; }
                .page.active { display:block; }
                .card { background:#fff; border-radius:8px; padding:12px; margin-bottom:10px; box-shadow:0 1px 3px rgba(0,0,0,0.15); }
                .card.green { border-right:6px solid #27ae60; }
                .card.red { border-right:6px solid #e74c3c; }
                .card.blue { border-right:6px solid #3498db; }
                .card-title { font-weight:bold; font-size:15px; margin-bottom:4px; }
                .card-sub { font-size:13px; color:#555; }
                .filters { display:flex; flex-wrap:wrap; gap:6px; margin-bottom:10px; }
                .chip { background:#eee; border-radius:16px; padding:6px 12px; font-size:13px; cursor:pointer; }
                .chip.active { background:#3498db; color:#fff; }
                .sem-header { background:#2c3e50; color:#fff; padding:8px; border-radius:6px; margin:14px 0 8px; font-size:13px; }
                .cat-header { background:#e9edf1; padding:8px 10px; border-radius:6px; margin:14px 0 8px; font-size:13px; }
                .row-kv { display:flex; justify-content:space-between; font-size:13px; padding:2px 0; border-bottom:1px dashed #eee; }
                .empty { text-align:center; color:#999; padding:30px; }
                .stu-card { background:#fff; border-radius:12px; padding:14px; margin-bottom:10px; }
                .avatar { width:46px; height:46px; border-radius:50%; display:flex; align-items:center; justify-content:center; font-weight:bold; font-size:15px; }
                .kv-grid { display:grid; grid-template-columns:1fr 1fr; gap:6px 12px; margin-top:8px; }
                .kv-item .lbl { font-size:10px; color:#999; }
                .kv-item .val { font-size:13px; }
                .course-row { display:flex; justify-content:space-between; align-items:center; padding:12px; border-bottom:1px solid #eee; background:#fff; cursor:pointer; }
                .course-row:last-child { border-bottom:none; }
                .att-circle { width:34px; height:34px; border-radius:50%; display:flex; align-items:center; justify-content:center; font-size:11px; font-weight:bold; flex-shrink:0; }
                .overlay { display:none; position:fixed; inset:0; background:rgba(0,0,0,0.5); z-index:100; align-items:center; justify-content:center; }
                .overlay.show { display:flex; }
                .modal { background:#fff; border-radius:14px; padding:16px; width:85%; max-width:340px; }
                .modal-close { float:left; font-size:18px; cursor:pointer; }
                .badge-gold { background:#caa23e; color:#fff; font-size:11px; padding:3px 10px; border-radius:12px; }
            </style></head><body>
            <div class="tabs">
                <div class="tab active" onclick="showPage('home')">الرئيسية</div>
                <div class="tab" onclick="showPage('plan')">الخطة الدراسية</div>
                <div class="tab" onclick="showPage('grades')">كشف الدرجات</div>
                <div class="tab" onclick="showPage('account')">كشف الحساب</div>
            </div>
            """.trimIndent()
        )

        sb.append("<div id='home' class='page active'>").append(buildHomeSection()).append("</div>")
        sb.append("<div id='plan' class='page'>").append(buildPlanSection()).append("</div>")
        sb.append("<div id='grades' class='page'>").append(buildGradesSection()).append("</div>")
        sb.append("<div id='account' class='page'>").append(buildAccountSection()).append("</div>")

        sb.append(buildModalAndScript())
        sb.append("</body></html>")
        return sb.toString()
    }

    private fun buildHomeSection(): String {
        val sb = StringBuilder()
        val basic = DataStore.studentBasic
        val regTimes = DataStore.regTimes

        val name = kv(basic, "Name").ifBlank { "الطالب" }
        val college = kv(basic, "The College")
        val gpa = kv(basic, "المعدل التراكمي")
        val warningStatus = kv(basic, "حالة الانذار الاكاديمي")
        val (bg, fg, _) = warningColor(warningStatus)
        val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }

        val gradExpected = kv(regTimes, "متوقع تخرجه")
        val regStart = kv(regTimes, "بداية تاريخ التسجيل")
        val regEnd = kv(regTimes, "نهاية تاريخ التسجيل")
        val addDropStart = kv(regTimes, "بداية تاريخ السحب و الأضافة").ifBlank { kv(regTimes, "بداية تاريخ السحب والأضافة") }
        val addDropEnd = kv(regTimes, "نهاية تاريخ السحب والأضافة").ifBlank { kv(regTimes, "نهاية تاريخ السحب و الأضافة") }

        sb.append("<div class='stu-card'>")
        sb.append("<div style='display:flex;align-items:center;gap:12px'>")
        sb.append("<div class='avatar' style='background:$bg;color:$fg'>${esc(initials)}</div>")
        sb.append("<div><div style='font-size:15px;font-weight:bold'>${esc(name)}</div><div style='font-size:12px;color:#777'>${esc(college)}</div></div>")
        sb.append("</div>")
        sb.append("<div class='kv-grid'>")
        sb.append("<div class='kv-item'><div class='lbl'>المعدل التراكمي</div><div class='val'>${esc(gpa)}</div></div>")
        sb.append("<div class='kv-item'><div class='lbl'>متوقع تخرجه</div><div class='val'>${esc(gradExpected)}</div></div>")
        sb.append("</div>")
        if (regStart.isNotBlank() || addDropStart.isNotBlank()) {
            sb.append("<div style='border-top:1px solid #eee;margin-top:10px;padding-top:8px'>")
            if (regStart.isNotBlank()) sb.append("<div class='row-kv'><span>فترة التسجيل</span><span>${esc(regStart)} إلى ${esc(regEnd)}</span></div>")
            if (addDropStart.isNotBlank()) sb.append("<div class='row-kv'><span>فترة السحب والإضافة</span><span>${esc(addDropStart)} إلى ${esc(addDropEnd)}</span></div>")
            sb.append("</div>")
        }
        sb.append("</div>")

        val courses = buildCourseMap()
        if (courses.isEmpty()) {
            sb.append("<div class='empty'>ما قدرنا نجيب المواد المسجلة</div>")
        } else {
            sb.append("<div style='background:#fff;border-radius:12px;overflow:hidden'>")
            for ((key, c) in courses) {
                val displayName = c.nameAr.ifBlank { c.nameEn }
                val circleHtml: String
                if (c.attendancePct != null) {
                    val (bg2, fg2, isBlack) = attendanceColor(c.attendancePct!!)
                    val alertIcon = if (isBlack) "<span style='margin-left:4px'>&#9888;</span>" else ""
                    circleHtml = "$alertIcon<div class='att-circle' style='background:$bg2;color:$fg2'>${c.attendancePct!!.toInt()}%</div>"
                } else {
                    circleHtml = "<div class='att-circle' style='background:#eee;color:#999'>-</div>"
                }
                sb.append("<div class='course-row' onclick=\"openModal('$key')\">")
                sb.append("<div><div style='font-size:13px;font-weight:bold'>${esc(displayName)}</div>")
                sb.append("<div style='font-size:11px;color:#777;margin-top:3px'>${esc(c.days)} — ${esc(c.timeFrom)} - ${esc(c.timeTo)} — ${esc(c.room)}</div></div>")
                sb.append("<div style='display:flex;align-items:center'>$circleHtml</div>")
                sb.append("</div>")
            }
            sb.append("</div>")

            sb.append("<script>var COURSES = {")
            for ((key, c) in courses) {
                sb.append("\"$key\": {")
                sb.append("name: \"${esc(c.nameAr.ifBlank { c.nameEn })}\",")
                sb.append("section: \"${esc(c.section)}\",")
                sb.append("instructor: \"${esc(c.instructor)}\",")
                sb.append("midtermGrade: \"${esc(c.midtermGrade)}\",")
                sb.append("otherWorkGrade: \"${esc(c.otherWorkGrade)}\",")
                sb.append("finalGrade: \"${esc(c.finalGrade)}\",")
                sb.append("gradeStatus: \"${esc(c.gradeStatus)}\",")
                sb.append("midtermDate: \"${esc(c.midtermDate)}\",")
                sb.append("midtermTime: \"${esc(c.midtermTime)}\",")
                sb.append("midtermRoom: \"${esc(c.midtermRoom)}\",")
                sb.append("finalDate: \"${esc(c.finalDate)}\",")
                sb.append("finalTime: \"${esc(c.finalTime)}\",")
                sb.append("finalRoom: \"${esc(c.finalRoom)}\"")
                sb.append("},")
            }
            sb.append("};</script>")
        }
        return sb.toString()
    }

    private fun buildPlanSection(): String {
        val planDetails = DataStore.planDetails
        val categoryNames = DataStore.categoryNames
        if (planDetails.isEmpty()) return "<div class='empty'>ما قدرنا نجيب الخطة الدراسية</div>"

        val currentCodes = buildCourseMap().keys

        val sb = StringBuilder()
        val keys = planDetails.keys.sorted()
        for (idx in keys) {
            val rows = planDetails[idx] ?: continue
            if (rows.isEmpty()) continue
            val header = rows[0]
            val studiedIdx = colIndex(header, "درسها")
            val doneIdx = colIndex(header, "اجتازها")
            val nameIdx = colIndex(header, "اسم المقرر")
            val codeIdx = colIndex(header, "رمز")
            val hoursIdx = colIndex(header, "عدد الساعات")
            val catName = categoryNames.getOrNull(idx) ?: "فئة ${idx + 1}"

            val displayRows = mutableListOf<Triple<String, String, String>>()
            for (r in 1 until rows.size) {
                val row = rows[r]
                val studied = studiedIdx >= 0 && studiedIdx < row.size && row[studiedIdx].trim() == "نعم"
                if (!studied) continue
                val passed = doneIdx >= 0 && doneIdx < row.size && row[doneIdx].trim() == "نعم"
                val name = if (nameIdx >= 0 && nameIdx < row.size) row[nameIdx] else "مقرر"
                val code = if (codeIdx >= 0 && codeIdx < row.size) row[codeIdx] else ""
                val hours = if (hoursIdx >= 0 && hoursIdx < row.size) row[hoursIdx] else ""
                val inProgress = currentCodes.contains(normCode(code))
                val cls = when {
                    inProgress -> "blue"
                    passed -> "green"
                    else -> "red"
                }
                val statusLabel = when {
                    inProgress -> "قيد الدراسة"
                    passed -> "مكتملة"
                    else -> "لم تُجتز"
                }
                displayRows.add(Triple(name, "${esc(code)} — $hours ساعات — $statusLabel", cls))
            }
            if (displayRows.isEmpty()) continue

            sb.append("<div class='cat-header'><div style='font-weight:bold'>${esc(catName)}</div></div>")
            for ((name, sub, cls) in displayRows) {
                sb.append("<div class='card $cls'><div class='card-title'>${esc(name)}</div><div class='card-sub'>$sub</div></div>")
            }
        }
        if (sb.isEmpty()) return "<div class='empty'>ما فيه مواد مدروسة بعد</div>"
        return sb.toString()
    }

    private fun buildGradesSection(): String {
        val rows = DataStore.grades
        val summary = DataStore.gradesSummary
        if (rows.isEmpty()) return "<div class='empty'>ما قدرنا نجيب كشف الدرجات</div>"

        val gpa = kv(summary, "المعدل التراكمي")
        val totalHours = kv(summary, "مجموع الساعات التراكمية")
        val passedHours = kv(summary, "الساعات التى نجح بها")
        val onHonor = kv(summary, "على لائحة الشرف")
        val planHours = kv(summary, "ساعات الخطة الدراسية")
        val remaining = try {
            val p = planHours.toDoubleOrNull()
            val t = totalHours.toDoubleOrNull()
            if (p != null && t != null) (p - t).toInt().toString() else ""
        } catch (e: Exception) { "" }

        val header = rows[0]
        val nameIdx = colIndex(header, "اسم المقرر")
        val codeIdx = colIndex(header, "رمز")
        val finalIdx = colIndex(header, "النهائي")
        val gradeIdx = colIndex(header, "العلامة")
        val statusIdx = colIndex(header, "حالة العلامه").takeIf { it >= 0 } ?: colIndex(header, "حالة العلامة")

        val sb = StringBuilder()
        sb.append("<div class='stu-card'>")
        sb.append("<div style='display:flex;justify-content:space-between;align-items:center;margin-bottom:8px'>")
        sb.append("<div style='font-size:12px;color:#777'>ملخص كشف الدرجات</div>")
        if (onHonor == "نعم") sb.append("<div class='badge-gold'>لائحة الشرف</div>")
        sb.append("</div>")
        sb.append("<div class='kv-grid'>")
        sb.append("<div class='kv-item'><div class='lbl'>المعدل التراكمي</div><div class='val' style='font-size:16px;font-weight:bold'>${esc(gpa)}</div></div>")
        sb.append("<div class='kv-item'><div class='lbl'>الساعات التراكمية</div><div class='val' style='font-size:16px;font-weight:bold'>${esc(totalHours)}</div></div>")
        sb.append("<div class='kv-item'><div class='lbl'>ساعات ناجحة</div><div class='val' style='font-size:16px;font-weight:bold'>${esc(passedHours)}</div></div>")
        sb.append("<div class='kv-item'><div class='lbl'>متبقي على الخطة</div><div class='val' style='font-size:16px;font-weight:bold'>${esc(remaining)}</div></div>")
        sb.append("</div></div>")

        sb.append(
            """
            <div class='filters' style='margin-bottom:6px'>
                <div style='display:flex;gap:8px;flex-wrap:wrap;font-size:10px;color:#777'>
                    <span><span style='display:inline-block;width:8px;height:8px;border-radius:50%;background:#1D9E75'></span> ٩٠-١٠٠</span>
                    <span><span style='display:inline-block;width:8px;height:8px;border-radius:50%;background:#0F6E56'></span> ٨٠-٨٩</span>
                    <span><span style='display:inline-block;width:8px;height:8px;border-radius:50%;background:#378ADD'></span> ٧٠-٧٩</span>
                    <span><span style='display:inline-block;width:8px;height:8px;border-radius:50%;background:#BA7517'></span> ٦٠-٦٩</span>
                    <span><span style='display:inline-block;width:8px;height:8px;border-radius:50%;background:#E24B4A'></span> ٥٠-٥٩</span>
                    <span><span style='display:inline-block;width:8px;height:8px;border-radius:50%;background:#1a1a1a'></span> اقل من ٥٠</span>
                </div>
            </div>
            <div class='filters'>
                <div class='chip active' onclick="filterGrades('all')">الكل</div>
                <div class='chip' onclick="filterGrades('semester')">حسب الفصل</div>
            </div>
            """.trimIndent()
        )

        for (r in 1 until rows.size) {
            val row = rows[r]
            if (row.size <= 3) {
                sb.append("<div class='sem-header'>${esc(row.joinToString(" "))}</div>")
                continue
            }
            val name = if (nameIdx >= 0 && nameIdx < row.size) row[nameIdx] else ""
            val code = if (codeIdx >= 0 && codeIdx < row.size) row[codeIdx] else ""
            val finalGrade = if (finalIdx >= 0 && finalIdx < row.size) row[finalIdx] else ""
            val gradeVal = if (gradeIdx >= 0 && gradeIdx < row.size) row[gradeIdx] else ""
            val status = if (statusIdx >= 0 && statusIdx < row.size) row[statusIdx] else ""
            val numeric = finalGrade.toDoubleOrNull() ?: gradeVal.toDoubleOrNull()
            val color: String = if (numeric != null) {
                gradeColor(numeric)
            } else if (status.contains("ناجح")) {
                "#1D9E75"
            } else if (status.contains("راسب")) {
                "#E24B4A"
            } else {
                "#ccc"
            }
            sb.append("<div class='card grade-card' style='border-right:6px solid $color'>")
            sb.append("<div class='card-title'>${esc(name)}</div><div class='card-sub'>${esc(code)}</div>")
            sb.append("</div>")
        }
        return sb.toString()
    }

    private fun buildAccountSection(): String {
        val rows = DataStore.account
        val payment = DataStore.payment
        val balance = DataStore.accountBalance
        if (rows.isEmpty()) return "<div class='empty'>ما قدرنا نجيب كشف الحساب</div>"

        var nextInstallment = ""
        if (payment.isNotEmpty()) {
            val h = payment[0]
            val paidIdx = colIndex(h, "تم دفع")
            val amountIdx = colIndex(h, "القسط")
            for (r in 1 until payment.size) {
                val row = payment[r]
                if (paidIdx in row.indices && row[paidIdx].trim() == "لا") {
                    nextInstallment = if (amountIdx in row.indices) row[amountIdx] else ""
                    break
                }
            }
        }

        val header = rows[0]
        val dateIdx = colIndex(header, "التاريخ")
        val typeIdx = colIndex(header, "نوع الوثيقة")
        val feesIdx = colIndex(header, "الرسوم")
        val paidAmountIdx = colIndex(header, "المدفوع")

        val sb = StringBuilder()
        sb.append("<div class='stu-card'>")
        sb.append("<div style='font-size:12px;color:#777;margin-bottom:8px'>ملخص الحساب</div>")
        sb.append("<div class='kv-grid'>")
        sb.append("<div class='kv-item'><div class='lbl'>الرصيد المطلوب</div><div class='val' style='font-size:16px;font-weight:bold;color:#E24B4A'>${esc(balance)}</div></div>")
        sb.append("<div class='kv-item'><div class='lbl'>القسط القادم</div><div class='val' style='font-size:16px;font-weight:bold'>${esc(nextInstallment)}</div></div>")
        sb.append("</div></div>")

        sb.append(
            """
            <div class='filters'>
                <div class='chip active' onclick="filterAccount('recent')">آخر 3 أشهر</div>
                <div class='chip' onclick="filterAccount('all')">الكشف الكامل</div>
            </div>
            """.trimIndent()
        )

        sb.append("<div id='accountList' style='background:#fff;border-radius:12px;overflow:hidden'>")
        for (r in 1 until rows.size) {
            val row = rows[r]
            fun cell(i: Int) = if (i in row.indices) row[i] else ""
            val date = cell(dateIdx)
            val type = cell(typeIdx)
            val fees = cell(feesIdx)
            val paidAmt = cell(paidAmountIdx)
            val isDebit = fees.isNotBlank() && fees != "-"
            val amountLabel = if (isDebit) fees else paidAmt
            val color = if (isDebit) "#E24B4A" else "#1D9E75"
            val sign = if (isDebit) "-" else "+"
            sb.append("<div class='account-row' data-date=\"${esc(date)}\" style='display:flex;justify-content:space-between;padding:11px 12px;border-bottom:1px solid #eee'>")
            sb.append("<div><div style='font-size:13px'>${esc(type)}</div><div style='font-size:11px;color:#999'>${esc(date)}</div></div>")
            sb.append("<div style='font-size:13px;color:$color'>${esc(amountLabel)}$sign</div>")
            sb.append("</div>")
        }
        sb.append("</div>")
        return sb.toString()
    }

    private fun buildModalAndScript(): String {
        return """
            <div class='overlay' id='overlay' onclick="if(event.target===this) closeModal()">
                <div class='modal'>
                    <span class='modal-close' onclick="closeModal()">&times;</span>
                    <div id='mName' style='font-size:15px;font-weight:bold;margin-bottom:10px'></div>
                    <div class='row-kv'><span>الشعبة</span><span id='mSection'></span></div>
                    <div class='row-kv'><span>المدرس</span><span id='mInstructor'></span></div>
                    <div class='row-kv'><span>علامة المنتصف</span><span id='mMid'></span></div>
                    <div class='row-kv'><span>أعمال أخرى</span><span id='mOther'></span></div>
                    <div class='row-kv'><span>العلامة النهائية</span><span id='mFinal'></span></div>
                    <div class='row-kv'><span>امتحان المنتصف</span><span id='mMidExam'></span></div>
                    <div class='row-kv'><span>الامتحان النهائي</span><span id='mFinalExam'></span></div>
                </div>
            </div>
            <script>
            function showPage(id) {
                document.querySelectorAll('.page').forEach(function(p){p.classList.remove('active');});
                document.getElementById(id).classList.add('active');
                document.querySelectorAll('.tab').forEach(function(t){t.classList.remove('active');});
                event.currentTarget.classList.add('active');
            }
            function openModal(key) {
                var c = (typeof COURSES !== 'undefined') ? COURSES[key] : null;
                if (!c) return;
                document.getElementById('mName').textContent = c.name;
                document.getElementById('mSection').textContent = c.section || '-';
                document.getElementById('mInstructor').textContent = c.instructor || '-';
                document.getElementById('mMid').textContent = c.midtermGrade || '-';
                document.getElementById('mOther').textContent = c.otherWorkGrade || '-';
                document.getElementById('mFinal').textContent = c.finalGrade || (c.gradeStatus || 'لسه ما صدرت');
                document.getElementById('mMidExam').textContent = (c.midtermDate ? (c.midtermDate + ' — ' + c.midtermTime + ' — ' + c.midtermRoom) : '-');
                document.getElementById('mFinalExam').textContent = (c.finalDate ? (c.finalDate + ' — ' + c.finalTime + ' — ' + c.finalRoom) : '-');
                document.getElementById('overlay').classList.add('show');
            }
            function closeModal() {
                document.getElementById('overlay').classList.remove('show');
            }
            function filterGrades(mode) {
                document.querySelectorAll('#grades .chip').forEach(function(c){c.classList.remove('active');});
                event.currentTarget.classList.add('active');
                document.querySelectorAll('#grades .sem-header').forEach(function(h){
                    h.style.display = (mode === 'semester') ? 'block' : 'none';
                });
            }
            function filterAccount(mode) {
                document.querySelectorAll('#account .chip').forEach(function(c){c.classList.remove('active');});
                event.currentTarget.classList.add('active');
                var rows = document.querySelectorAll('.account-row');
                var cutoff = new Date();
                cutoff.setMonth(cutoff.getMonth() - 3);
                rows.forEach(function(row){
                    if (mode === 'all') { row.style.display = 'flex'; return; }
                    var dateStr = row.getAttribute('data-date');
                    var parts = dateStr.split('-');
                    var d = parts.length === 3 ? new Date(parts[2], parts[1]-1, parts[0]) : null;
                    row.style.display = (d && d >= cutoff) ? 'flex' : 'none';
                });
            }
            filterAccount('recent');
            </script>
        """.trimIndent()
    }
}
