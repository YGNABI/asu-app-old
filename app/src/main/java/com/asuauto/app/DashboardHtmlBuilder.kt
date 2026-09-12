package com.asuauto.app

object DashboardHtmlBuilder {

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
        var room = ""
        var instructor = ""
        var midDate = ""
        var midTime = ""
        var midRoom = ""
        var finDate = ""
        var finTime = ""
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
                x.days = daysAr(c(di)); x.from = to12h(c(fi)); x.to = to12h(c(ti))
                x.room = c(ri); x.instructor = c(li)
                x.midDate = c(mdi); x.midTime = to12h(c(mti)); x.midRoom = c(mri)
                x.finDate = c(fdi); x.finTime = to12h(c(fti)); x.finRoom = c(fri)
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
        sb.append(HEAD)
        sb.append("<div id='home' class='page active'>").append(home()).append("</div>")
        sb.append("<div id='plan' class='page'>").append(plan()).append("</div>")
        sb.append("<div id='grades' class='page'>").append(grades()).append("</div>")
        sb.append("<div id='account' class='page'>").append(account()).append("</div>")
        sb.append(SCRIPT)
        return sb.toString()
    }

    private fun home(): String {
        val d = DataStore
        val name = d.f("studentName").ifBlank { "الطالب" }
        val (bg, fg) = warnColor(d.f("studentWarning"))
        val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }

        val sb = StringBuilder()
        sb.append("<div class='card'>")
        sb.append("<div style='display:flex;align-items:center;gap:12px'>")
        sb.append("<div class='avatar' style='background:$bg;color:$fg'>${esc(initials)}</div>")
        sb.append("<div><div class='big'>${esc(name)}</div><div class='muted'>${esc(d.f("studentCollege"))}</div></div></div>")
        sb.append("<div class='grid'>")
        sb.append(kvBox("المعدل التراكمي", d.f("studentGpa")))
        sb.append(kvBox("متوقع تخرجه", d.f("gradExpected")))
        sb.append(kvBox("المرشد الأكاديمي", d.f("advisor")))
        sb.append(kvBox("الوضع الأكاديمي", d.f("academicStatus")))
        sb.append(kvBox("متبقي للتخرج", "${remainingCoursesCount()} مادة"))
        sb.append("</div>")
        if (d.f("regStart").isNotBlank() || d.f("addDropStart").isNotBlank()) {
            sb.append("<div class='sep'>")
            if (d.f("regStart").isNotBlank())
                sb.append("<div class='kv'><span>فترة التسجيل</span><span>${esc(d.f("regStart"))} إلى ${esc(d.f("regEnd"))}</span></div>")
            if (d.f("addDropStart").isNotBlank())
                sb.append("<div class='kv'><span>السحب والإضافة</span><span>${esc(d.f("addDropStart"))} إلى ${esc(d.f("addDropEnd"))}</span></div>")
            sb.append("</div>")
        }
        sb.append("</div>")

        val cs = courses()
        if (cs.isEmpty()) {
            sb.append("<div class='empty'>ما فيه مواد مسجلة</div>")
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
            val disp = c.nameAr.ifBlank { c.nameEn }
            val circle = if (c.att != null) {
                val (b2, f2, black) = attColor(c.att!!)
                val warn = if (black) "<span style='margin-left:4px;color:#E24B4A'>&#9888;</span>" else ""
                "$warn<div class='circle' style='background:$b2;color:$f2'>${c.att!!.toInt()}%</div>"
            } else "<div class='circle' style='background:#eee;color:#999'>-</div>"
            sb.append("<div class='row' onclick=\"openModal('$k')\">")
            sb.append("<div><div class='rowTitle'>${esc(disp)} <span class='codeTag'>${esc(c.code)}</span></div>")
            sb.append("<div class='rowSub'>${esc(c.days)} — ${esc(c.from)} - ${esc(c.to)} — ${esc(c.room)}</div></div>")
            sb.append("<div style='display:flex;align-items:center'>$circle</div></div>")
        }
        sb.append("</div>")

        sb.append("<script>var C={")
        for ((k, c) in cs) {
            sb.append("\"$k\":{n:\"${esc(c.nameAr.ifBlank { c.nameEn })}\",s:\"${esc(c.section)}\",i:\"${esc(c.instructor)}\",")
            sb.append("mg:\"${esc(c.midGrade)}\",wg:\"${esc(c.workGrade)}\",fg:\"${esc(c.finGrade)}\",tg:\"${esc(c.total)}\",gc:\"${esc(c.gradeCase)}\",")
            sb.append("md:\"${esc(c.midDate)}\",mt:\"${esc(c.midTime)}\",mr:\"${esc(c.midRoom)}\",")
            sb.append("fd:\"${esc(c.finDate)}\",ft:\"${esc(c.finTime)}\",fr:\"${esc(c.finRoom)}\"},")
        }
        sb.append("};</script>")
        return sb.toString()
    }

    private fun kvBox(label: String, value: String) =
        "<div><div class='lbl'>${esc(label)}</div><div class='val'>${esc(value.ifBlank { "-" })}</div></div>"

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
        if (details.isEmpty()) return "<div class='empty'>ما قدرنا نجيب الخطة الدراسية</div>"
        val current = courses().keys
        val sb = StringBuilder()

        val remainingCourses = remainingCoursesCount()
        sb.append("<div class='card' style='text-align:center'><div class='muted'>متبقي لإنهاء الخطة</div><div style='font-size:22px;font-weight:bold;margin-top:4px'>$remainingCourses مادة</div></div>")
        sb.append("<div class='filters'><div class='chip active' onclick=\"fp(this,'reg')\">المسجلة فقط</div><div class='chip' onclick=\"fp(this,'all')\">الخطة كاملة</div></div>")
        sb.append("<div class='legend'><span><i style='background:#27ae60'></i>مكتملة</span><span><i style='background:#3498db'></i>قيد الدراسة</span><span><i style='background:#e74c3c'></i>لم تُجتز</span><span><i style='background:#ccc'></i>لم تُدرس</span></div>")

        for (idx in details.keys.sorted()) {
            val rows = details[idx] ?: continue
            if (rows.size < 2) continue
            val h = rows[0]
            val si = colIdx(h, "درسها"); val pi = colIdx(h, "اجتازها")
            val ni = colIdx(h, "اسم المقرر"); val ci = colIdx(h, "رمز"); val hi = colIdx(h, "عدد الساعات")
            val stat = DataStore.planStats.getOrNull(idx)
            val catName = stat?.getOrNull(0) ?: DataStore.planNames.getOrNull(idx) ?: "فئة ${idx + 1}"

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
                val label = when { !studied -> "لم تُدرس"; inProg -> "قيد الدراسة"; passed -> "مكتملة"; else -> "لم تُجتز" }
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
            sb.append("<div class='catStats'>مطلوبة: $reqC · درسها: $studC · مجتازة: $passC · لم يجتزها: $notPassedC</div></div>")
            sb.append("<div class='p-reg'>"); itemsReg.forEach { sb.append(it) }; sb.append("</div>")
            sb.append("<div class='p-all' style='display:none'>"); itemsAll.forEach { sb.append(it) }; sb.append("</div>")
        }
        if (sb.length < 300) return "<div class='empty'>ما فيه مواد مدروسة</div>"
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
        sb.append("<div class='muted'>ملخص كشف الدرجات</div>")
        if (d.f("honorList") == "نعم") sb.append("<div class='gold'>لائحة الشرف</div>")
        sb.append("</div><div class='grid'>")
        sb.append(kvBox("المعدل التراكمي", d.f("gpa")))
        sb.append(kvBox("الساعات التراكمية", d.f("totalHours")))
        sb.append(kvBox("ساعات ناجحة", d.f("passedHours")))
        sb.append(kvBox("متبقي على الخطة", remaining))
        sb.append("</div></div>")

        if (rows.isEmpty()) {
            sb.append("<div class='empty'>ما قدرنا نجيب كشف الدرجات</div>")
            return sb.toString()
        }

        sb.append("<div class='legend2'>")
        listOf("#1D9E75" to "٩٠-١٠٠", "#0F6E56" to "٨٠-٨٩", "#378ADD" to "٧٠-٧٩", "#BA7517" to "٦٠-٦٩", "#E24B4A" to "٥٠-٥٩", "#1a1a1a" to "أقل من ٥٠")
            .forEach { (c, t) -> sb.append("<span><i style='background:$c'></i>$t</span>") }
        sb.append("</div>")
        sb.append("<div class='filters'><div class='chip active' onclick=\"fg(this,'all')\">الكل</div><div class='chip' onclick=\"fg(this,'sem')\">حسب الفصل</div></div>")

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
            sb.append("<div class='rowSub'>منتصف: ${esc(mid)} · أعمال: ${esc(work)} · نهائي: ${esc(fin)}</div></div>")
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

        sb.append("<div class='card'><div class='muted'>ملخص الحساب</div><div class='grid'>")
        sb.append("<div><div class='lbl'>الرصيد المطلوب</div><div class='val' style='color:#E24B4A;font-weight:bold'>${esc(d.f("balance"))}</div></div>")
        sb.append(kvBox("القسط القادم", nextInst))
        sb.append("</div>")
        sb.append("<div class='sep'>")
        sb.append("<div class='kv'><span>القسط الأول</span><span>${esc(d.f("inst1"))} — ${if (d.f("inst1Paid") == "لا") "غير مدفوع" else "مدفوع"}</span></div>")
        sb.append("<div class='kv'><span>القسط الثاني</span><span>${esc(d.f("inst2"))} — ${if (d.f("inst2Paid") == "لا") "غير مدفوع" else "مدفوع"}</span></div>")
        sb.append("<div class='kv'><span>القسط الثالث</span><span>${esc(d.f("inst3"))} — ${if (d.f("inst3Paid") == "لا") "غير مدفوع" else "مدفوع"}</span></div>")
        sb.append("</div></div>")

        val rows = d.account
        if (rows.size < 2) {
            sb.append("<div class='empty'>ما قدرنا نجيب كشف الحساب</div>")
            return sb.toString()
        }
        val h = rows[0]
        val di = colIdx(h, "التاريخ"); val ti = colIdx(h, "نوع الوثيقة")
        val fi = colIdx(h, "الرسوم"); val pi = colIdx(h, "المدفوع"); val ci = colIdx(h, "نوع المطالبة")

        sb.append("<div class='filters'><div class='chip active' onclick=\"fa(this,'recent')\">آخر 3 أشهر</div><div class='chip' onclick=\"fa(this,'all')\">الكشف الكامل</div></div>")
        sb.append("<div class='list' id='accList'>")
        for (r in 1 until rows.size) {
            val row = rows[r]
            fun c(i: Int) = if (i in row.indices) row[i].trim() else ""
            val fees = c(fi); val paid = c(pi)
            val isDebit = fees.isNotBlank()
            val amount = if (isDebit) fees else paid
            val color = if (isDebit) "#E24B4A" else "#1D9E75"
            val tag = if (isDebit) "رسوم" else "دفعة"
            val label = c(ci).ifBlank { c(ti) }
            sb.append("<div class='arow' data-d=\"${esc(c(di))}\">")
            sb.append("<div><div class='rowTitle' style='font-size:13px'>${esc(label)}</div><div class='rowSub'>${esc(c(di))}</div></div>")
            sb.append("<div style='text-align:left'><span class='pill' style='background:$color'>${esc(tag)}</span><div style='font-size:13px;margin-top:3px'>${esc(amount)}</div></div></div>")
        }
        sb.append("<div class='empty' id='accEmpty' style='display:none'>ما فيه حركات بهذه الفترة</div>")
        sb.append("</div>")
        return sb.toString()
    }

    private val HEAD = """
        <html dir="rtl" lang="ar"><head><meta charset="utf-8">
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
        .ov{display:none;position:fixed;inset:0;background:rgba(0,0,0,.5);z-index:100;align-items:center;justify-content:center}
        .ov.show{display:flex}
        .modal{background:#fff;border-radius:14px;padding:16px;width:86%;max-width:340px}
        </style></head><body>
        <div class="tabs">
        <div class="tab active" onclick="sp(this,'home')">الرئيسية</div>
        <div class="tab" onclick="sp(this,'plan')">الخطة</div>
        <div class="tab" onclick="sp(this,'grades')">الدرجات</div>
        <div class="tab" onclick="sp(this,'account')">الحساب</div>
        <div onclick="if(typeof AndroidBridge!=='undefined')AndroidBridge.refresh()" style="padding:12px 10px;color:#fff;background:#1a252f;cursor:pointer">&#8635;</div>
        <div onclick="doLogout()" style="padding:12px 10px;color:#fff;background:#7a2020;cursor:pointer;font-size:11px">خروج</div>
        </div>
    """.trimIndent()

    private val SCRIPT = """
        <div class='ov' id='ov' onclick="if(event.target===this)cm()">
        <div class='modal'>
        <span onclick="cm()" style="float:left;font-size:20px;cursor:pointer">&times;</span>
        <div id='mn' style='font-size:15px;font-weight:bold;margin-bottom:10px'></div>
        <div class='kv'><span>الشعبة</span><span id='ms'></span></div>
        <div class='kv'><span>المدرس</span><span id='mi'></span></div>
        <div class='kv'><span>علامة المنتصف</span><span id='mmg'></span></div>
        <div class='kv'><span>أعمال أخرى</span><span id='mwg'></span></div>
        <div class='kv'><span>علامة الامتحان النهائي</span><span id='mfg'></span></div>
        <div class='kv'><span>المجموع</span><span id='mtg'></span></div>
        <div class='kv'><span>امتحان المنتصف</span><span id='mme'></span></div>
        <div class='kv'><span>الامتحان النهائي</span><span id='mfe'></span></div>
        </div></div>
        <div class='ov' id='gov' onclick="if(event.target===this)cgm()">
        <div class='modal'>
        <span onclick="cgm()" style="float:left;font-size:20px;cursor:pointer">&times;</span>
        <div id='gmn' style='font-size:15px;font-weight:bold;margin-bottom:10px'></div>
        <div class='kv'><span>رمز المقرر</span><span id='gmc'></span></div>
        <div class='kv'><span>علامة المنتصف</span><span id='gmmid'></span></div>
        <div class='kv'><span>أعمال أخرى</span><span id='gmwork'></span></div>
        <div class='kv'><span>الامتحان النهائي</span><span id='gmfinal'></span></div>
        <div class='kv'><span>المجموع</span><span id='gmtotal'></span></div>
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
        </script></body></html>
    """.trimIndent()
}
