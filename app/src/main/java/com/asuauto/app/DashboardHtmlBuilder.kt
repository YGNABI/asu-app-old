package com.asuauto.app

object DashboardHtmlBuilder {

    var LANG = "ar"
    private fun t(ar: String, en: String) = if (LANG == "en") en else ar

    private fun esc(s: String) = s.replace("\"", "&quot;").replace("<", "&lt;")
    private fun norm(s: String) = s.uppercase().replace(Regex("[^A-Z0-9]"), "")

    private fun colIdx(header: List<String>, needle: String): Int {
        for (i in header.indices) if (header[i].replace("\n", " ").contains(needle)) return i
        return -1
    }

    private fun to12h(t: String): String {
        val p = t.trim().split(":")
        if (p.size < 2) return t
        val h = p[0].toIntOrNull() ?: return t
        val period = if (h < 12) "ص" else "م"
        var h12 = h % 12
        if (h12 == 0) h12 = 12
        return "$h12:${p[1]} $period"
    }

    private fun daysAr(code: String): String {
        val m = mapOf('U' to "الأحد", 'M' to "الاثنين", 'T' to "الثلاثاء", 'W' to "الأربعاء", 'H' to "الخميس", 'F' to "الجمعة", 'S' to "السبت")
        return code.trim().mapNotNull { m[it] }.joinToString("، ")
    }

    private fun attColor(p: Double): Triple<String, String, Boolean> = when {
        p <= 10.0 -> Triple("#EAF3DE", "#3B6D11", false)
        p <= 15.0 -> Triple("#FAEEDA", "#854F0B", false)
        p <= 25.0 -> Triple("#FCEBEB", "#A32D2D", false)
        else -> Triple("#1a1a1a", "#ffffff", true)
    }

    private fun gradeColor(g: Double): String = when {
        g >= 90 -> "#1D9E75"
        g >= 80 -> "#0F6E56"
        g >= 70 -> "#378ADD"
        g >= 60 -> "#BA7517"
        g >= 50 -> "#E24B4A"
        else -> "#1a1a1a"
    }

    private fun warnColor(s: String): Pair<String, String> = when {
        s.isEmpty() -> Pair("#EAF3DE", "#3B6D11")
        s.contains("اول") || s.contains("أول") -> Pair("#FAEEDA", "#854F0B")
        s.contains("ثاني") -> Pair("#FCEBEB", "#A32D2D")
        s.contains("ثالث") -> Pair("#1a1a1a", "#ffffff")
        else -> Pair("#EAF3DE", "#3B6D11")
    }

    /**
     * Gold if cumulative GPA > 92, silver if not but the most recent NON-summer
     * semester's "Average" > 85. Summer terms are skipped when picking which
     * semester's Average to check (per the user: summer doesn't count toward
     * honor-roll eligibility) — but the cumulative GPA field itself is used
     * as-is since it's a running total that already includes summer naturally.
     * Returns null when neither threshold is met, so callers fall back to the
     * existing warning-based color.
     */
    private fun honorRollColor(): Pair<String, String>? {
        val d = DataStore
        val cumGpa = d.f("studentGpa").toDoubleOrNull()

        var lastNonSummerTerm: String? = null
        for (row in d.transcript) {
            if (row.size >= 2 && row[0] == "__TERM__") {
                val text = row[1]
                if (!text.contains("Summer", ignoreCase = true) && !text.contains("صيفي")) {
                    lastNonSummerTerm = text
                }
            }
        }
        val semesterAvg = lastNonSummerTerm?.let {
            Regex("Average\\s+([\\d.]+)").find(it)?.groupValues?.get(1)?.toDoubleOrNull()
        }

        return when {
            cumGpa != null && cumGpa > 92.0 -> "#D4AF37" to "#ffffff"
            semesterAvg != null && semesterAvg > 85.0 -> "#BFC1C2" to "#3a3a3a"
            else -> null
        }
    }

    class Course {
        var code = ""
        var nameAr = ""
        var nameEn = ""
        var section = ""
        var days = ""
        var rawDays = ""
        var from = ""
        var rawFrom = ""
        var to = ""
        var rawTo = ""
        var room = ""
        var instructor = ""
        var midDate = ""
        var midTime = ""
        var rawMidTime = ""
        var midRoom = ""
        var finDate = ""
        var finTime = ""
        var rawFinTime = ""
        var finRoom = ""
        var att: Double? = null
        var midGrade = ""
        var workGrade = ""
        var finGrade = ""
        var total = ""
        var gradeCase = ""
    }

    private fun courses(): LinkedHashMap<String, Course> {
        val map = LinkedHashMap<String, Course>()

        val reg = DataStore.registration
        if (reg.size > 1) {
            val h = reg[0]
            val ci = colIdx(h, "رمز"); val ni = colIdx(h, "اسم المقرر")
            val si = colIdx(h, "الشعب"); val di = colIdx(h, "الايام")
            val fi = colIdx(h, "من"); val ti = colIdx(h, "الى")
            val ri = colIdx(h, "القاعة"); val li = colIdx(h, "اسم المدرس")
            val mdi = colIdx(h, "تاريخ امتحان المنتصف"); val mti = colIdx(h, "وقت امتحان المنتصف")
            val mri = colIdx(h, "قاعة امتحان المنتصف")
            val fdi = colIdx(h, "تاريخ الامتحان النهائي"); val fti = colIdx(h, "وقت الامتحان النهائي")
            val fri = colIdx(h, "قاعة الامتحان النهائي")
            for (r in 1 until reg.size) {
                val row = reg[r]
                fun c(i: Int) = if (i in row.indices) row[i].trim() else ""
                val code = c(ci)
                if (code.isBlank()) continue
                val x = Course()
                x.code = code
                x.nameEn = c(ni); x.section = c(si)
                x.rawDays = c(di); x.rawFrom = c(fi)
                x.days = daysAr(c(di)); x.from = to12h(c(fi)); x.to = to12h(c(ti)); x.rawTo = c(ti)
                x.room = c(ri); x.instructor = c(li)
                x.midDate = c(mdi); x.midTime = to12h(c(mti)); x.rawMidTime = c(mti); x.midRoom = c(mri)
                x.finDate = c(fdi); x.finTime = to12h(c(fti)); x.rawFinTime = c(fti); x.finRoom = c(fri)
                map[norm(code)] = x
            }
        }

        val sg = DataStore.semesterGrades
        if (sg.size > 1) {
            val h = sg[0]
            val ci = colIdx(h, "رمز"); val ni = colIdx(h, "اسم المقرر")
            val mi = colIdx(h, "المنتصف"); val oi = colIdx(h, "أعمال")
            val fi = colIdx(h, "النهائي"); val ti = colIdx(h, "العلامة")
            val gi = colIdx(h, "حالة العلامة")
            for (r in 1 until sg.size) {
                val row = sg[r]
                fun c(i: Int) = if (i in row.indices) row[i].trim() else ""
                val code = c(ci)
                if (code.isBlank()) continue
                val x = map.getOrPut(norm(code)) { Course().also { it.code = code } }
                if (x.nameAr.isBlank()) x.nameAr = c(ni)
                x.midGrade = c(mi); x.workGrade = c(oi); x.finGrade = c(fi)
                x.total = c(ti); x.gradeCase = c(gi)
            }
        }

        val att = DataStore.attendance
        if (att.size > 1) {
            val h = att[0]
            val ci = colIdx(h, "رمز"); val ni = colIdx(h, "اسم")
            val pi = colIdx(h, "الغياب")
            for (r in 1 until att.size) {
                val row = att[r]
                fun c(i: Int) = if (i in row.indices) row[i].trim() else ""
                val code = c(ci)
                if (code.isBlank()) continue
                val x = map.getOrPut(norm(code)) { Course().also { it.code = code } }
                if (x.nameAr.isBlank()) x.nameAr = c(ni)
                x.att = c(pi).replace("%", "").trim().toDoubleOrNull()
            }
        }
        return map
    }

    fun build(): String {
        val sb = StringBuilder()
        sb.append(HEAD())
        sb.append("<div id='home' class='page active'>").append(home()).append("</div>")
        sb.append("<div id='plan' class='page'>").append(plan()).append("</div>")
        sb.append("<div id='grades' class='page'>").append(grades()).append("</div>")
        sb.append("<div id='account' class='page'>").append(account()).append("</div>")
        sb.append(SCRIPT())
        return sb.toString()
    }

    private fun home(): String {
        val d = DataStore
        val name = d.f("studentName").ifBlank { "الطالب" }
        val warning = d.f("studentWarning")
        val (bg, fg) = if (warning.isBlank()) (honorRollColor() ?: warnColor(warning)) else warnColor(warning)
        val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }

        val sb = StringBuilder()
        sb.append("<div class='card'>")
        sb.append("<div style='display:flex;align-items:center;gap:12px'>")
        sb.append("<div class='avatar' style='background:$bg;color:$fg'>${esc(initials)}</div>")
        sb.append("<div><div class='big'>${esc(name)}</div><div class='muted'>${esc(d.f("studentCollege"))}</div></div></div>")
        sb.append("<div class='grid'>")
        sb.append(kvBox(t("المعدل التراكمي", "GPA"), d.f("studentGpa"), "toggleGpaChart()"))
        sb.append(kvBox(t("متوقع تخرجه", "Expected to graduate"), d.f("gradExpected")))
        sb.append(kvBox(t("المرشد الأكاديمي", "Academic Advisor"), d.f("advisor")))
        sb.append(kvBox(t("الوضع الأكاديمي", "Academic Status"), d.f("academicStatus")))
        sb.append(kvBox(t("متبقي للتخرج", "Remaining to graduate"), "${remainingCoursesCount()} ${t("مادة", "courses")}"))
        sb.append("</div>")
        if (d.f("regStart").isNotBlank() || d.f("addDropStart").isNotBlank()) {
            sb.append("<div class='sep'>")
            if (d.f("regStart").isNotBlank())
                sb.append("<div class='kv'><span>${t("فترة التسجيل", "Registration period")}</span><span>${esc(d.f("regStart"))} ${t("إلى", "to")} ${esc(d.f("regEnd"))}</span></div>")
            if (d.f("addDropStart").isNotBlank())
                sb.append("<div class='kv'><span>${t("السحب والإضافة", "Add/Drop period")}</span><span>${esc(d.f("addDropStart"))} ${t("إلى", "to")} ${esc(d.f("addDropEnd"))}</span></div>")
            sb.append("</div>")
        }
        sb.append("</div>")
        sb.append(gpaChartOverlay(DataStore.gpaHistory))

        val cs = courses()
        if (cs.isEmpty()) {
            sb.append("<div class='empty'>${t("لا توجد مواد مسجلة", "No registered courses")}</div>")
            return sb.toString()
        }
        val dayOrder = mapOf('U' to 0, 'M' to 1, 'T' to 2, 'W' to 3, 'H' to 4, 'F' to 5, 'S' to 6)
        fun timeMinutes(t: String): Int {
            val p = t.trim().split(":")
            val h = p.getOrNull(0)?.toIntOrNull() ?: 0
            val m = p.getOrNull(1)?.toIntOrNull() ?: 0
            return h * 60 + m
        }
        val sorted = cs.entries.sortedWith(compareBy(
            { dayOrder[it.value.rawDays.trim().firstOrNull()] ?: 9 },
            { timeMinutes(it.value.rawFrom) }
        ))
        sb.append("<div class='list'>")
        for ((k, c) in sorted) {
            val disp = if (LANG == "en") c.nameEn.ifBlank { c.nameAr } else c.nameAr.ifBlank { c.nameEn }
            val circle = if (c.att != null) {
                val (b2, f2, black) = attColor(c.att!!)
                val warn = if (black) "<span style='margin-left:4px;color:#E24B4A'>&#9888;</span>" else ""
                "$warn<div class='circle' style='background:$b2;color:$f2'>${c.att!!.toInt()}%</div>"
            } else "<div class='circle' style='background:#eee;color:#999'>-</div>"
            sb.append("<div class='row' data-code='$k' onclick=\"openModal('$k')\">")
            sb.append("<div><div class='rowTitle'>${esc(disp)} <span class='codeTag'>${esc(c.code)}</span></div>")
            sb.append("<div class='rowSub'>${esc(c.days)} — ${esc(c.from)} - ${esc(c.to)} — ${esc(c.room)}</div></div>")
            sb.append("<div style='display:flex;align-items:center'>$circle</div></div>")
        }
        sb.append("</div>")

        sb.append("<div id='activeBanner' class='activeBanner'>")
        sb.append("<div class='activeBannerHandle'></div>")
        sb.append("<div class='activeBannerText'>")
        sb.append("<div class='activeBannerCourse' id='activeBannerCourse'></div>")
        sb.append("<div class='activeBannerRoom' id='activeBannerRoom'></div>")
        sb.append("</div></div>")

        sb.append("<script>var C={")
        for ((k, c) in cs) {
            val dispName = if (LANG == "en") c.nameEn.ifBlank { c.nameAr } else c.nameAr.ifBlank { c.nameEn }
            val moodleId = DataStore.moodleCourseMap[k] ?: ""
            sb.append("\"$k\":{n:\"${esc(dispName)}\",s:\"${esc(c.section)}\",i:\"${esc(c.instructor)}\",")
            sb.append("mg:\"${esc(c.midGrade)}\",wg:\"${esc(c.workGrade)}\",fg:\"${esc(c.finGrade)}\",tg:\"${esc(c.total)}\",gc:\"${esc(c.gradeCase)}\",")
            sb.append("md:\"${esc(c.midDate)}\",mt:\"${esc(c.midTime)}\",mtr:\"${esc(c.rawMidTime)}\",mr:\"${esc(c.midRoom)}\",")
            sb.append("fd:\"${esc(c.finDate)}\",ft:\"${esc(c.finTime)}\",ftr:\"${esc(c.rawFinTime)}\",fr:\"${esc(c.finRoom)}\",")
            sb.append("mid:\"${esc(moodleId)}\",rd:\"${esc(c.rawDays)}\",rf:\"${esc(c.rawFrom)}\",rt:\"${esc(c.rawTo)}\",rm:\"${esc(c.room)}\"},")
        }
        sb.append("};</script>")
        return sb.toString()
    }

    /**
     * Persistent black-box panel: a single green checkmark when every
     * DebugLog entry is OK, or the full recorded log (including the OK
     * lines, for context) as soon as anything is a warning or error.
     * Stays in the app — this is how "something's missing/wrong" gets
     * diagnosed going forward instead of screenshotting a specific screen.
     */
    /**
     * Builds the GPA-trend overlay (hidden by default, toggled via
     * toggleGpaChart()) from the locally-accumulated history. A smooth
     * Catmull-Rom curve, colored with a gradient that blends green where the
     * GPA rose and red where it dropped between consecutive points — matching
     * app colors, no hard color switch mid-line.
     */
    private fun gpaChartOverlay(history: List<Pair<String, Double>>): String {
        val sb = StringBuilder()
        sb.append("<div id='gpaOv' class='ov' onclick=\"if(event.target===this) document.getElementById('gpaOv').classList.remove('show')\">")
        sb.append("<div class='modal'>")
        sb.append("<div style='display:flex;justify-content:space-between;align-items:center;margin-bottom:10px'>")
        sb.append("<div class='big'>${t("تطور المعدل التراكمي", "GPA Trend")}</div>")
        sb.append("<span onclick=\"document.getElementById('gpaOv').classList.remove('show')\" style='cursor:pointer;font-size:20px;line-height:1'>&times;</span>")
        sb.append("</div>")

        if (history.size < 2) {
            sb.append("<div class='empty' style='padding:20px 4px'>${t("سنبدأ بتتبع تطور معدلك التراكمي من الفتحة القادمة للوحة", "We'll start tracking your GPA trend from the next time you open the dashboard")}</div>")
        } else {
            val w = 300.0; val h = 150.0; val padL = 30.0; val padR = 8.0; val padT = 10.0; val padB = 20.0
            val values = history.map { it.second }
            var minV = values.min() - 1.0
            var maxV = values.max() + 1.0
            if (maxV - minV < 2.0) { minV -= 1.0; maxV += 1.0 }
            minV = minV.coerceAtLeast(0.0)
            maxV = maxV.coerceAtMost(100.0)
            val n = history.size
            val span = (maxV - minV).let { if (it == 0.0) 1.0 else it }
            fun xAt(i: Int) = padL + (w - padL - padR) * i / (n - 1)
            fun yAt(v: Double) = padT + (h - padT - padB) * (1 - (v - minV) / span)

            val pts = history.mapIndexed { i, pair -> xAt(i) to yAt(pair.second) }
            val path = StringBuilder("M${pts[0].first},${pts[0].second} ")
            for (i in 0 until pts.size - 1) {
                val p0 = pts.getOrElse(i - 1) { pts[i] }
                val p1 = pts[i]
                val p2 = pts[i + 1]
                val p3 = pts.getOrElse(i + 2) { pts[i + 1] }
                val c1x = p1.first + (p2.first - p0.first) / 6
                val c1y = p1.second + (p2.second - p0.second) / 6
                val c2x = p2.first - (p3.first - p1.first) / 6
                val c2y = p2.second - (p3.second - p1.second) / 6
                path.append("C$c1x,$c1y $c2x,$c2y ${p2.first},${p2.second} ")
            }

            sb.append("<svg viewBox='0 0 $w $h' width='100%' height='150' preserveAspectRatio='xMidYMid meet'>")
            for (gi in 0..2) {
                val gy = padT + (h - padT - padB) * gi / 2
                sb.append("<line x1='$padL' y1='$gy' x2='${w - padR}' y2='$gy' stroke='#eee' stroke-width='1'/>")
            }
            sb.append("<defs><linearGradient id='gpaGrad' x1='0' y1='0' x2='1' y2='0'>")
            for (i in history.indices) {
                val offset = if (n == 1) 0 else i * 100 / (n - 1)
                val color = if (i == 0) "#378ADD" else if (history[i].second >= history[i - 1].second) "#1D9E75" else "#E24B4A"
                sb.append("<stop offset='$offset%' stop-color='$color'/>")
            }
            sb.append("</linearGradient></defs>")
            sb.append("<path d='$path' fill='none' stroke='url(#gpaGrad)' stroke-width='3' stroke-linecap='round'/>")
            pts.forEachIndexed { i, p ->
                val color = if (i == 0) "#378ADD" else if (history[i].second >= history[i - 1].second) "#1D9E75" else "#E24B4A"
                sb.append("<circle cx='${p.first}' cy='${p.second}' r='3' fill='$color'/>")
            }
            val labelIdxs = if (n <= 4) history.indices.toList() else listOf(0, n / 2, n - 1)
            for (i in labelIdxs) {
                sb.append("<text x='${pts[i].first}' y='${h - 4}' font-size='8' fill='#999' text-anchor='middle'>${esc(history[i].first.substring(5))}</text>")
            }
            sb.append("</svg>")
        }
        sb.append("</div></div>")
        return sb.toString()
    }

    private fun debugBoxSection(): String {
        val sb = StringBuilder()
        val issues = DebugLog.hasIssues()
        val borderColor = if (issues) "#A32D2D" else "#3B6D11"
        sb.append("<div class='card' style='border:2px solid $borderColor;margin-bottom:10px'>")
        if (!issues) {
            sb.append("<div style='color:#3B6D11;font-weight:bold;font-size:13px'>✔ كل شي تمام</div>")
        } else {
            val errorCount = DebugLog.all().count { it.level == DebugLog.Level.ERROR }
            val warnCount = DebugLog.all().count { it.level == DebugLog.Level.WARN }
            sb.append("<div style='color:#A32D2D;font-weight:bold;font-size:13px;margin-bottom:8px'>")
            sb.append("⚠ فيه $errorCount خطأ و$warnCount ملاحظة — التفاصيل:</div>")
            for (e in DebugLog.all()) {
                val color = when (e.level) {
                    DebugLog.Level.OK -> "#3B6D11"
                    DebugLog.Level.WARN -> "#854F0B"
                    DebugLog.Level.ERROR -> "#A32D2D"
                }
                val icon = when (e.level) {
                    DebugLog.Level.OK -> "✔"
                    DebugLog.Level.WARN -> "⚠"
                    DebugLog.Level.ERROR -> "✘"
                }
                sb.append("<div style='font-size:11px;color:$color;margin-bottom:3px;white-space:pre-wrap'>$icon ${esc(e.message)}</div>")
            }
        }
        sb.append("</div>")
        return sb.toString()
    }

    private fun kvBox(label: String, value: String, onclick: String? = null): String {
        val clickAttr = if (onclick != null) " onclick=\"$onclick\" style='cursor:pointer'" else ""
        return "<div$clickAttr><div class='lbl'>${esc(label)}</div><div class='val'>${esc(value.ifBlank { "-" })}</div></div>"
    }

    private fun hoursToCourses(hoursStr: String): Int {
        val h = hoursStr.toDoubleOrNull() ?: return 0
        return Math.round(h / 3.0).toInt()
    }

    private fun remainingCoursesCount(): Int {
        var remainingHours = 0.0
        for (stat in DataStore.planStats) {
            if (stat.getOrNull(0) == "خارج الخطة") continue
            val req = stat.getOrNull(1)?.toDoubleOrNull() ?: 0.0
            val passed = stat.getOrNull(3)?.toDoubleOrNull() ?: 0.0
            remainingHours += (req - passed)
        }
        return Math.round(remainingHours / 3.0).toInt()
    }

    private fun plan(): String {
        val details = DataStore.planDetails
        if (details.isEmpty()) return "<div class='empty'>${t("تعذّر تحميل الخطة الدراسية", "Couldn't load the study plan")}</div>"
        val current = courses().keys
        val sb = StringBuilder()

        val remainingCourses = remainingCoursesCount()
        sb.append("<div class='card' style='text-align:center'><div class='muted'>${t("متبقي لإنهاء الخطة", "Remaining to complete plan")}</div><div style='font-size:22px;font-weight:bold;margin-top:4px'>$remainingCourses ${t("مادة", "courses")}</div></div>")
        sb.append("<div class='filters'><div class='chip active' onclick=\"fp(this,'reg')\">${t("المسجلة فقط", "Studied only")}</div><div class='chip' onclick=\"fp(this,'all')\">${t("الخطة كاملة", "Full plan")}</div></div>")
        sb.append("<div class='legend'><span><i style='background:#27ae60'></i>${t("مكتملة", "Passed")}</span><span><i style='background:#3498db'></i>${t("قيد الدراسة", "In progress")}</span><span><i style='background:#e74c3c'></i>${t("لم تُجتز", "Not passed")}</span><span><i style='background:#ccc'></i>${t("لم تُدرس", "Not taken")}</span></div>")

        for (idx in details.keys.sorted()) {
            val rows = details[idx] ?: continue
            if (rows.size < 2) continue
            val h = rows[0]
            val si = colIdx(h, "درسها"); val pi = colIdx(h, "اجتازها")
            val ni = colIdx(h, "اسم المقرر"); val ci = colIdx(h, "رمز"); val hi = colIdx(h, "عدد الساعات")
            val stat = DataStore.planStats.getOrNull(idx)
            val catName = stat?.getOrNull(0) ?: DataStore.planNames.getOrNull(idx) ?: "${t("فئة", "Category")} ${idx + 1}"

            val itemsReg = mutableListOf<String>()
            val itemsAll = mutableListOf<String>()
            for (r in 1 until rows.size) {
                val row = rows[r]
                fun c(i: Int) = if (i in row.indices) row[i].trim() else ""
                val studied = c(si) == "نعم"
                val passed = c(pi) == "نعم"
                val code = c(ci)
                val inProg = current.contains(norm(code))
                val color = when { !studied -> "#ccc"; inProg -> "#3498db"; passed -> "#27ae60"; else -> "#e74c3c" }
                val label = when {
                    !studied -> t("لم تُدرس", "Not taken")
                    inProg -> t("قيد الدراسة", "In progress")
                    passed -> t("مكتملة", "Passed")
                    else -> t("لم تُجتز", "Not passed")
                }
                val card = "<div class='pcard' style='border-right:5px solid $color'><div class='rowTitle'>${esc(c(ni))} <span class='codeTag'>${esc(code)}</span></div><div class='rowSub'>$label</div></div>"
                itemsAll.add(card)
                if (studied) itemsReg.add(card)
            }
            if (itemsAll.isEmpty()) continue
            val reqC = hoursToCourses(stat?.getOrNull(1) ?: "0")
            val studC = hoursToCourses(stat?.getOrNull(2) ?: "0")
            val passC = hoursToCourses(stat?.getOrNull(3) ?: "0")
            val notPassedC = (studC - passC).coerceAtLeast(0)
            sb.append("<div class='cat'><div class='catName'>${esc(catName)}</div>")
            sb.append("<div class='catStats'>${t("مطلوبة", "Required")}: $reqC · ${t("درسها", "Taken")}: $studC · ${t("مجتازة", "Passed")}: $passC · ${t("لم يجتزها", "Not passed")}: $notPassedC</div></div>")
            sb.append("<div class='p-reg'>"); itemsReg.forEach { sb.append(it) }; sb.append("</div>")
            sb.append("<div class='p-all' style='display:none'>"); itemsAll.forEach { sb.append(it) }; sb.append("</div>")
        }
        if (sb.length < 300) return "<div class='empty'>${t("لا توجد مواد مدروسة", "No studied courses yet")}</div>"
        return sb.toString()
    }

    private fun grades(): String {
        val d = DataStore
        val rows = d.transcript
        val sb = StringBuilder()

        val total = d.f("totalHours").toDoubleOrNull()
        val planH = d.f("planHours").toDoubleOrNull()
        val remaining = if (total != null && planH != null) (planH - total).toInt().toString() else "-"

        sb.append("<div class='card'>")
        sb.append("<div style='display:flex;justify-content:space-between;align-items:center'>")
        sb.append("<div class='muted'>${t("ملخص كشف الدرجات", "Grades Summary")}</div>")
        if (d.f("honorList") == "نعم") sb.append("<div class='gold'>${t("لائحة الشرف", "Honor List")}</div>")
        sb.append("</div><div class='grid'>")
        sb.append(kvBox(t("المعدل التراكمي", "GPA"), d.f("gpa")))
        sb.append(kvBox(t("الساعات التراكمية", "Cumulative Hours"), d.f("totalHours")))
        sb.append(kvBox(t("ساعات ناجحة", "Passed Hours"), d.f("passedHours")))
        sb.append(kvBox(t("متبقي على الخطة", "Remaining Hours"), remaining))
        sb.append("</div></div>")

        if (rows.isEmpty()) {
            sb.append("<div class='empty'>${t("تعذّر تحميل كشف الدرجات", "Couldn't load the grade report")}</div>")
            return sb.toString()
        }

        sb.append("<div class='legend2'>")
        listOf("#1D9E75" to "٩٠-١٠٠", "#0F6E56" to "٨٠-٨٩", "#378ADD" to "٧٠-٧٩", "#BA7517" to "٦٠-٦٩", "#E24B4A" to "٥٠-٥٩", "#1a1a1a" to t("أقل من ٥٠", "Below 50"))
            .forEach { (col, lbl) -> sb.append("<span><i style='background:$col'></i>$lbl</span>") }
        sb.append("</div>")
        sb.append("<div class='filters'><div class='chip active' onclick=\"fg(this,'all')\">${t("الكل", "All")}</div><div class='chip' onclick=\"fg(this,'sem')\">${t("حسب الفصل", "By Semester")}</div></div>")

        val gMap = StringBuilder("<script>var G={")
        var gi = 0
        for (row in rows) {
            if (row.size >= 2 && row[0] == "__TERM__") {
                sb.append("<div class='term'>${esc(row[1])}</div>")
                continue
            }
            if (row.size < 8) continue
            val code = row[0]; val name = row[1]
            val mid = row[3]; val work = row[4]; val fin = row[5]
            val totalG = row[6]; val case = row[7]
            val num = totalG.toDoubleOrNull()
            val color = when {
                num != null -> gradeColor(num)
                case.contains("ناجح") -> "#1D9E75"
                case.contains("منسحب") -> "#999"
                else -> "#E24B4A"
            }
            val key = "g${gi++}"
            val circleTxt = if (num != null) totalG else if (case.isNotBlank() && case != "-") case.take(4) else "-"
            sb.append("<div class='row' style='background:#fff;border-radius:8px;margin-bottom:8px;border-right:5px solid $color' onclick=\"openGrade('$key')\">")
            sb.append("<div><div class='rowTitle'>${esc(name)} <span class='codeTag'>${esc(code)}</span></div>")
            sb.append("<div class='rowSub'>${t("منتصف", "Mid")}: ${esc(mid)} · ${t("أعمال", "Work")}: ${esc(work)} · ${t("نهائي", "Final")}: ${esc(fin)}</div></div>")
            sb.append("<div class='circle' style='background:$color;color:#fff;flex-shrink:0'>${esc(circleTxt)}</div>")
            sb.append("</div>")
            gMap.append("\"$key\":{n:\"${esc(name)}\",c:\"${esc(code)}\",mg:\"${esc(mid)}\",wg:\"${esc(work)}\",fg:\"${esc(fin)}\",tg:\"${esc(totalG)}\",gc:\"${esc(case)}\"},")
        }
        gMap.append("};</script>")
        sb.append(gMap)
        return sb.toString()
    }

    private fun account(): String {
        val d = DataStore
        val sb = StringBuilder()

        var nextInst = ""
        if (d.f("inst1Paid") == "لا") nextInst = d.f("inst1")
        else if (d.f("inst2Paid") == "لا") nextInst = d.f("inst2")
        else if (d.f("inst3Paid") == "لا") nextInst = d.f("inst3")

        sb.append("<div class='card'><div class='muted'>${t("ملخص الحساب", "Account Summary")}</div><div class='grid'>")
        sb.append("<div><div class='lbl'>${t("الرصيد المطلوب", "Balance Due")}</div><div class='val' style='color:#E24B4A;font-weight:bold'>${esc(d.f("balance"))}</div></div>")
        sb.append(kvBox(t("القسط القادم", "Next Installment"), nextInst))
        sb.append("</div>")
        sb.append("<div class='sep'>")
        sb.append("<div class='kv'><span>${t("القسط الأول", "1st Installment")}</span><span>${esc(d.f("inst1"))} — ${if (d.f("inst1Paid") == "لا") t("غير مدفوع", "Unpaid") else t("مدفوع", "Paid")}</span></div>")
        sb.append("<div class='kv'><span>${t("القسط الثاني", "2nd Installment")}</span><span>${esc(d.f("inst2"))} — ${if (d.f("inst2Paid") == "لا") t("غير مدفوع", "Unpaid") else t("مدفوع", "Paid")}</span></div>")
        sb.append("<div class='kv'><span>${t("القسط الثالث", "3rd Installment")}</span><span>${esc(d.f("inst3"))} — ${if (d.f("inst3Paid") == "لا") t("غير مدفوع", "Unpaid") else t("مدفوع", "Paid")}</span></div>")
        sb.append("</div></div>")

        val rows = d.account
        if (rows.size < 2) {
            sb.append("<div class='empty'>${t("تعذّر تحميل كشف الحساب", "Couldn't load the account statement")}</div>")
            return sb.toString()
        }
        val h = rows[0]
        val di = colIdx(h, "التاريخ"); val ti = colIdx(h, "نوع الوثيقة")
        val fi = colIdx(h, "الرسوم"); val pi = colIdx(h, "المدفوع"); val ci = colIdx(h, "نوع المطالبة")

        sb.append("<div class='filters'><div class='chip active' onclick=\"fa(this,'recent')\">${t("آخر 3 أشهر", "Last 3 months")}</div><div class='chip' onclick=\"fa(this,'all')\">${t("الكشف الكامل", "Full statement")}</div></div>")
        sb.append("<div class='list' id='accList'>")
        for (r in 1 until rows.size) {
            val row = rows[r]
            fun c(i: Int) = if (i in row.indices) row[i].trim() else ""
            val fees = c(fi); val paid = c(pi)
            val isDebit = fees.isNotBlank()
            val amount = if (isDebit) fees else paid
            val color = if (isDebit) "#E24B4A" else "#1D9E75"
            val tag = if (isDebit) t("رسوم", "Fee") else t("دفعة", "Payment")
            val label = c(ci).ifBlank { c(ti) }
            sb.append("<div class='arow' data-d=\"${esc(c(di))}\">")
            sb.append("<div><div class='rowTitle' style='font-size:13px'>${esc(label)}</div><div class='rowSub'>${esc(c(di))}</div></div>")
            sb.append("<div style='text-align:left'><span class='pill' style='background:$color'>${esc(tag)}</span><div style='font-size:13px;margin-top:3px'>${esc(amount)}</div></div></div>")
        }
        sb.append("<div class='empty' id='accEmpty' style='display:none'>${t("لا توجد حركات خلال هذه الفترة", "No transactions in this period")}</div>")
        sb.append("</div>")
        return sb.toString()
    }

    private fun HEAD(): String {
        val dir = if (LANG == "en") "ltr" else "rtl"
        val langCode = if (LANG == "en") "en" else "ar"
        return """
        <html dir="$dir" lang="$langCode"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <style>
        body{font-family:sans-serif;background:#f2f3f5;margin:0}
        .tabs{display:flex;position:sticky;top:0;background:#2c3e50;z-index:20}
        .tab{flex:1;text-align:center;padding:12px 2px;color:#fff;font-size:12px;cursor:pointer}
        .tab.active{background:#34495e;border-bottom:3px solid #3498db}
        .page{display:none;padding:12px}
        .page.active{display:block}
        .card{background:#fff;border-radius:12px;padding:14px;margin-bottom:10px}
        .avatar{width:46px;height:46px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-weight:bold}
        .big{font-size:15px;font-weight:bold}
        .muted{font-size:12px;color:#888}
        .grid{display:grid;grid-template-columns:1fr 1fr;gap:8px 12px;margin-top:10px}
        .lbl{font-size:10px;color:#999}
        .val{font-size:14px}
        .sep{border-top:1px solid #eee;margin-top:10px;padding-top:8px}
        .kv{display:flex;justify-content:space-between;font-size:12px;padding:3px 0}
        .list{background:#fff;border-radius:12px;overflow:hidden}
        .row,.arow{display:flex;justify-content:space-between;align-items:center;padding:12px;border-bottom:1px solid #f0f0f0;cursor:pointer}
        .row:last-child,.arow:last-child{border-bottom:none}
        .rowTitle{font-size:14px;font-weight:bold}
        .codeTag{font-size:10px;color:#aaa;font-weight:normal}
        .rowSub{font-size:11px;color:#888;margin-top:3px}
        .circle{width:36px;height:36px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:11px;font-weight:bold}
        .pcard{background:#fff;border-radius:8px;padding:11px 12px;margin-bottom:8px}
        .cat{background:#e3e8ee;border-radius:8px;padding:9px 12px;margin:14px 0 8px}
        .catName{font-size:13px;font-weight:bold}
        .catStats{font-size:11px;color:#666;margin-top:3px}
        .term{background:#2c3e50;color:#fff;padding:8px 10px;border-radius:6px;margin:14px 0 8px;font-size:12px}
        .legend,.legend2{display:flex;gap:10px;flex-wrap:wrap;font-size:10px;color:#777;margin-bottom:10px}
        .legend i,.legend2 i{display:inline-block;width:9px;height:9px;border-radius:50%;margin-left:4px}
        .filters{display:flex;gap:6px;margin-bottom:10px}
        .chip{background:#e6e6e6;border-radius:14px;padding:6px 14px;font-size:12px;cursor:pointer}
        .chip.active{background:#3498db;color:#fff}
        .empty{text-align:center;color:#aaa;padding:40px 10px}
        .gold{background:#caa23e;color:#fff;font-size:11px;padding:3px 10px;border-radius:12px}
        .pill{color:#fff;font-size:10px;padding:2px 9px;border-radius:10px}
        .calBtn{display:inline-block;margin-top:6px;background:#eef4fb;color:#2c6cb0;font-size:11px;padding:5px 10px;border-radius:8px;cursor:pointer}
        .ov{display:none;position:fixed;inset:0;background:rgba(0,0,0,.5);z-index:100;align-items:center;justify-content:center}
        .ov.show{display:flex}
        .modal{background:#fff;border-radius:14px;padding:16px;width:86%;max-width:340px}
        .row.rowActive{background:#EAF3DE;border-right:4px solid #3B6D11}
        .row.rowActive .rowTitle{color:#2c5b0e}
        .activeBanner{position:fixed;left:12px;right:12px;bottom:14px;background:#2c3e50;color:#fff;border-radius:14px;padding:12px 16px;display:none;align-items:center;justify-content:space-between;z-index:90;box-shadow:0 4px 14px rgba(0,0,0,.25);touch-action:none}
        .activeBanner.show{display:flex}
        .activeBannerHandle{width:34px;height:4px;background:rgba(255,255,255,.4);border-radius:3px;position:absolute;top:6px;left:50%;transform:translateX(-50%)}
        .activeBannerText{width:100%;padding-top:6px}
        .activeBannerCourse{font-size:12px;color:#cbd3da}
        .activeBannerRoom{font-size:18px;font-weight:bold;margin-top:2px}
        </style></head><body>
        <div class="tabs">
        <div class="tab active" onclick="sp(this,'home')">${t("الرئيسية", "Home")}</div>
        <div class="tab" onclick="sp(this,'plan')">${t("الخطة", "Plan")}</div>
        <div class="tab" onclick="sp(this,'grades')">${t("الدرجات", "Grades")}</div>
        <div class="tab" onclick="sp(this,'account')">${t("الحساب", "Account")}</div>
        <div onclick="if(typeof AndroidBridge!=='undefined')AndroidBridge.refresh()" style="padding:12px 10px;color:#fff;background:#1a252f;cursor:pointer">&#8635;</div>
        </div>
        """.trimIndent()
    }

    private fun SCRIPT(): String {
        val calLbl = t("أضف للتقويم", "Add to Calendar")
        val calLblMid = t("أضف امتحان المنتصف للتقويم", "Add Midterm to Calendar")
        val calLblFin = t("أضف الامتحان النهائي للتقويم", "Add Final to Calendar")
        val materialsLbl = t("عرض المادة التعليمية", "View Course Materials")
        val assessmentsLbl = t("عرض الواجبات والبحوث", "View Assignments & Research")
        val lSection = t("الشعبة", "Section"); val lTeacher = t("المدرس", "Instructor")
        val lMidGrade = t("علامة المنتصف", "Midterm Grade"); val lWork = t("أعمال أخرى", "Other Work")
        val lFinGrade = t("علامة الامتحان النهائي", "Final Exam Grade"); val lTotal = t("المجموع", "Total")
        val lMidExam = t("امتحان المنتصف", "Midterm Exam"); val lFinExam = t("الامتحان النهائي", "Final Exam")
        val lCode = t("رمز المقرر", "Course Code")
        return """
        <script>var CALLBL="$calLbl",CALLBL_MID="$calLblMid",CALLBL_FIN="$calLblFin",MATERIALSLBL="$materialsLbl",ASSESSMENTSLBL="$assessmentsLbl",ROOMLBL="${t("القاعة", "Room")}";</script>
        <div class='ov' id='ov' onclick="if(event.target===this)cm()">
        <div class='modal'>
        <span onclick="cm()" style="float:left;font-size:20px;cursor:pointer">&times;</span>
        <div id='mn' style='font-size:15px;font-weight:bold;margin-bottom:10px'></div>
        <div class='kv'><span>$lSection</span><span id='ms'></span></div>
        <div class='kv'><span>$lTeacher</span><span id='mi'></span></div>
        <div class='kv'><span>$lMidGrade</span><span id='mmg'></span></div>
        <div class='kv'><span>$lWork</span><span id='mwg'></span></div>
        <div class='kv'><span>$lFinGrade</span><span id='mfg'></span></div>
        <div class='kv'><span>$lTotal</span><span id='mtg'></span></div>
        <div class='kv'><span>$lMidExam</span><span id='mme'></span></div>
        <div class='kv'><span>$lFinExam</span><span id='mfe'></span></div>
        <div id='mCalBtns'></div>
        <div id='mMaterialsBtn'></div>
        <div id='mAssessmentsBtn'></div>
        </div></div>
        <div class='ov' id='gov' onclick="if(event.target===this)cgm()">
        <div class='modal'>
        <span onclick="cgm()" style="float:left;font-size:20px;cursor:pointer">&times;</span>
        <div id='gmn' style='font-size:15px;font-weight:bold;margin-bottom:10px'></div>
        <div class='kv'><span>$lCode</span><span id='gmc'></span></div>
        <div class='kv'><span>$lMidGrade</span><span id='gmmid'></span></div>
        <div class='kv'><span>$lWork</span><span id='gmwork'></span></div>
        <div class='kv'><span>$lFinExam</span><span id='gmfinal'></span></div>
        <div class='kv'><span>$lTotal</span><span id='gmtotal'></span></div>
        </div></div>
        <script>
        function sp(el,id){
          document.querySelectorAll('.page').forEach(function(p){p.classList.remove('active')});
          document.getElementById(id).classList.add('active');
          document.querySelectorAll('.tab').forEach(function(t){t.classList.remove('active')});
          el.classList.add('active');
        }
        function v(x){return (x&&x!=='-'&&x!=='')?x:'-';}
        function openModal(k){
          var c=(typeof C!=='undefined')?C[k]:null; if(!c)return;
          document.getElementById('mn').textContent=c.n;
          document.getElementById('ms').textContent=v(c.s);
          document.getElementById('mi').textContent=v(c.i);
          document.getElementById('mmg').textContent=v(c.mg);
          document.getElementById('mwg').textContent=v(c.wg);
          document.getElementById('mfg').textContent=v(c.fg);
          document.getElementById('mtg').textContent=v(c.tg)!=='-'?c.tg:(v(c.gc)!=='-'?c.gc:'لم تصدر');
          document.getElementById('mme').textContent=c.md?(c.md+' — '+c.mt+(c.mr?' — '+c.mr:'')):'-';
          document.getElementById('mfe').textContent=c.fd?(c.fd+' — '+c.ft+(c.fr?' — '+c.fr:'')):'-';
          var btnsHtml='';
          if(c.md) btnsHtml+="<span class='calBtn' onclick=\"addExamToCal('"+c.n+" - Midterm','"+c.md+"','"+c.mtr+"','"+c.mr+"')\">"+CALLBL_MID+"</span> ";
          if(c.fd) btnsHtml+="<span class='calBtn' onclick=\"addExamToCal('"+c.n+" - Final','"+c.fd+"','"+c.ftr+"','"+c.fr+"')\">"+CALLBL_FIN+"</span>";
          document.getElementById('mCalBtns').innerHTML=btnsHtml;
          var matHtml = c.mid ? "<span class='calBtn' onclick=\"openCourseMaterials('"+c.mid+"','"+c.n+"')\">"+MATERIALSLBL+"</span>" : '';
          document.getElementById('mMaterialsBtn').innerHTML = matHtml;
          var assHtml = c.mid ? "<span class='calBtn' onclick=\"openCourseAssessments('"+c.mid+"','"+c.n+"','"+c.md+"','"+c.mtr+"','"+c.mr+"','"+c.fd+"','"+c.ftr+"','"+c.fr+"')\">"+ASSESSMENTSLBL+"</span>" : '';
          document.getElementById('mAssessmentsBtn').innerHTML = assHtml;
          document.getElementById('ov').classList.add('show');
        }
        function openGrade(k){
          var g=(typeof G!=='undefined')?G[k]:null; if(!g)return;
          document.getElementById('gmn').textContent=g.n;
          document.getElementById('gmc').textContent=v(g.c);
          document.getElementById('gmmid').textContent=v(g.mg);
          document.getElementById('gmwork').textContent=v(g.wg);
          document.getElementById('gmfinal').textContent=v(g.fg);
          document.getElementById('gmtotal').textContent=v(g.tg)!=='-'?g.tg:(v(g.gc)!=='-'?g.gc:'-');
          document.getElementById('gov').classList.add('show');
        }
        function cgm(){document.getElementById('gov').classList.remove('show')}
        function cm(){document.getElementById('ov').classList.remove('show')}
        function fp(el,m){
          el.parentNode.querySelectorAll('.chip').forEach(function(c){c.classList.remove('active')});
          el.classList.add('active');
          document.querySelectorAll('.p-reg').forEach(function(x){x.style.display=(m==='reg')?'block':'none'});
          document.querySelectorAll('.p-all').forEach(function(x){x.style.display=(m==='all')?'block':'none'});
        }
        function doLogout(){
          if(typeof AndroidBridge!=='undefined') AndroidBridge.logout();
        }
        function addExamToCal(name,date,time,room){
          if(typeof AndroidBridge!=='undefined') AndroidBridge.addCalendarEvent(name,date,time,room||'');
        }
        function addInstallmentToCal(name,date){
          if(typeof AndroidBridge!=='undefined') AndroidBridge.addCalendarEvent(name,date,'','');
        }
        function openCourseMaterials(moodleId,courseName){
          if(typeof AndroidBridge!=='undefined') AndroidBridge.openCourseMaterials(moodleId,courseName);
        }
        function openCourseAssessments(moodleId,courseName,midDate,midTime,midRoom,finDate,finTime,finRoom){
          if(typeof AndroidBridge!=='undefined') AndroidBridge.openCourseAssessments(moodleId,courseName,midDate,midTime,midRoom,finDate,finTime,finRoom);
        }
        function fg(el,m){
          el.parentNode.querySelectorAll('.chip').forEach(function(c){c.classList.remove('active')});
          el.classList.add('active');
          document.querySelectorAll('#grades .term').forEach(function(t){t.style.display=(m==='sem')?'block':'none'});
        }
        function fa(el,m){
          el.parentNode.querySelectorAll('.chip').forEach(function(c){c.classList.remove('active')});
          el.classList.add('active');
          var cut=new Date();cut.setMonth(cut.getMonth()-3);
          var visible=0;
          document.querySelectorAll('.arow').forEach(function(r){
            var show=true;
            if(m!=='all'){
              var p=(r.getAttribute('data-d')||'').split('-');
              var d=p.length===3?new Date(p[2],p[1]-1,p[0]):null;
              show=(d&&d>=cut);
            }
            r.style.display=show?'flex':'none';
            if(show)visible++;
          });
          var empty=document.getElementById('accEmpty');
          if(empty)empty.style.display=(visible===0)?'block':'none';
        }
        (function(){
          var c=document.querySelector('#account .chip');
          if(c)fa(c,'recent');
        })();

        function toggleGpaChart(){
          var ov=document.getElementById('gpaOv');
          if(ov) ov.classList.add('show');
        }

        function toMinutesOfDay(t){
          var p=(t||'').split(':');
          return (parseInt(p[0])||0)*60+(parseInt(p[1])||0);
        }

        var lastActiveCode=null, dismissedCode=null;
        function checkCurrentClass(){
          if(typeof C==='undefined') return;
          var dayMap=['U','M','T','W','H','F','S'];
          var now=new Date();
          var todayLetter=dayMap[now.getDay()];
          var nowMin=now.getHours()*60+now.getMinutes();
          var activeCode=null, activeCourse=null;
          for(var k in C){
            var c=C[k];
            if(!c.rd || c.rd.indexOf(todayLetter)===-1) continue;
            if(!c.rf || !c.rt) continue;
            var fromMin=toMinutesOfDay(c.rf), toMin=toMinutesOfDay(c.rt);
            if(nowMin>=fromMin && nowMin<=toMin){ activeCode=k; activeCourse=c; break; }
          }
          document.querySelectorAll('.row.rowActive').forEach(function(r){r.classList.remove('rowActive')});
          if(activeCode){
            var row=document.querySelector('.row[data-code="'+activeCode+'"]');
            if(row) row.classList.add('rowActive');
            var banner=document.getElementById('activeBanner');
            if(banner && activeCode!==dismissedCode){
              document.getElementById('activeBannerCourse').textContent=activeCourse.n;
              document.getElementById('activeBannerRoom').textContent=activeCourse.rm ? (ROOMLBL+': '+activeCourse.rm) : '';
              banner.style.transform='';
              banner.classList.add('show');
            }
          } else {
            var banner2=document.getElementById('activeBanner');
            if(banner2) banner2.classList.remove('show');
            dismissedCode=null;
          }
          lastActiveCode=activeCode;
        }

        (function(){
          var banner=document.getElementById('activeBanner');
          if(!banner) return;
          var startY=null, currentY=0;
          banner.addEventListener('touchstart',function(e){
            startY=e.touches[0].clientY; currentY=0;
          },{passive:true});
          banner.addEventListener('touchmove',function(e){
            if(startY===null) return;
            var dy=e.touches[0].clientY-startY;
            if(dy>0){ currentY=dy; banner.style.transform='translateY('+dy+'px)'; }
          },{passive:true});
          banner.addEventListener('touchend',function(){
            if(currentY>40){
              banner.classList.remove('show');
              dismissedCode=lastActiveCode;
            }
            banner.style.transform='';
            startY=null; currentY=0;
          });
        })();

        checkCurrentClass();
        setInterval(checkCurrentClass, 30000);
        </script></body></html>
    """.trimIndent()
    }
}
