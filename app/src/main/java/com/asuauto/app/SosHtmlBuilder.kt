package com.asuauto.app

import org.json.JSONObject

data class SosCard(val label: String, val href: String)

object SosHtmlBuilder {

    var LANG = "ar"
    fun t(ar: String, en: String) = if (LANG == "en") en else ar
    private fun esc(s: String) = s.replace("\"", "&quot;").replace("<", "&lt;")

    private fun baseStyle() = """
        body{font-family:sans-serif;background:#f2f3f5;margin:0}
        .header{position:sticky;top:0;background:#2c3e50;color:#fff;padding:14px;font-size:15px;font-weight:bold}
        .page{padding:12px}
        .card{background:#fff;border-radius:12px;padding:14px;margin-bottom:12px}
        .list{background:#fff;border-radius:12px;overflow:hidden}
        .row{display:flex;justify-content:space-between;align-items:center;padding:14px;border-bottom:1px solid #f0f0f0;cursor:pointer}
        .row:last-child{border-bottom:none}
        label{display:block;font-size:12px;color:#666;margin:10px 0 4px}
        input[type=text], textarea{width:100%;box-sizing:border-box;padding:10px;border:1px solid #ddd;border-radius:8px;font-size:13px}
        textarea{min-height:90px}
        .btn{display:block;text-align:center;background:#1D9E75;color:#fff;font-size:13px;font-weight:bold;padding:12px;border-radius:10px;margin-top:14px}
        .btn.danger{background:#A32D2D}
        .btn.secondary{background:#2c3e50}
        .btnRow{display:flex;gap:8px;margin-top:14px}
        .btnRow .btn{flex:1;margin-top:0}
        .respTable{width:100%;font-size:12px;border-collapse:collapse}
        .respTable td, .respTable th{padding:8px;border-bottom:1px solid #eee;text-align:right}
        .empty{text-align:center;color:#aaa;padding:30px 10px}
    """.trimIndent()

    fun buildCategoryList(title: String, cards: List<SosCard>): String {
        val dir = if (LANG == "en") "ltr" else "rtl"
        val sb = StringBuilder()
        sb.append("<html dir='$dir'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><style>${baseStyle()}</style></head><body>")
        sb.append("<div class='header'>${esc(title)}</div><div class='page'>")
        if (cards.isEmpty()) {
            sb.append("<div class='empty'>${t("لا توجد تصنيفات", "No categories")}</div>")
        } else {
            sb.append("<div class='list'>")
            for (c in cards) {
                sb.append("<div class='row' onclick=\"AndroidBridge.openCategory('${esc(c.href)}')\"><div>${esc(c.label)}</div><div>&#8250;</div></div>")
            }
            sb.append("</div>")
        }
        sb.append("</div></body></html>")
        return sb.toString()
    }

    fun buildCreateForm(sosType: String): String {
        val dir = if (LANG == "en") "ltr" else "rtl"
        val isComplaint = sosType == "COMPLAINT"
        val sb = StringBuilder()
        sb.append("<html dir='$dir'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><style>${baseStyle()}</style></head><body>")
        sb.append("<div class='header'>${t("تفاصيل الطلب", "Details")}</div><div class='page'><div class='card'>")

        sb.append("<label>${t("رقم التواصل", "Contact Number")}</label>")
        sb.append("<input type='text' id='contactNo' inputmode='tel'>")

        if (isComplaint) {
            sb.append("<label>${t("الموقع والشهود", "Signature & Witnesses")}</label>")
            sb.append("<textarea id='witnesses' maxlength='500'></textarea>")
            sb.append("<label>${t("التفاصيل", "Details")}</label>")
            sb.append("<textarea id='dscp' maxlength='1500'></textarea>")
        } else {
            sb.append("<label>${t("التفاصيل", "Details")}</label>")
            sb.append("<textarea id='dscp'></textarea>")
        }

        sb.append("<label>${t("ملاحظات", "Remarks")}</label>")
        sb.append("<textarea id='remarks' maxlength='1000'></textarea>")

        sb.append("<div class='btn' onclick='submitForm()'>${t("التالي", "Next")}</div>")
        sb.append("</div></div>")

        sb.append(
            """
            <script>
            function submitForm(){
                var contactNo = document.getElementById('contactNo').value;
                var dscp = document.getElementById('dscp').value;
                var remarks = document.getElementById('remarks').value;
                var witnesses = ${if (isComplaint) "document.getElementById('witnesses').value" else "''"};
                if(typeof AndroidBridge!=='undefined') AndroidBridge.submitForm(contactNo, dscp, remarks, witnesses);
            }
            </script>
            """.trimIndent()
        )

        sb.append("</body></html>")
        return sb.toString()
    }

    fun buildTrack(rawJson: String): String {
        val dir = if (LANG == "en") "ltr" else "rtl"
        val o = try { JSONObject(rawJson) } catch (e: Exception) { JSONObject() }
        val cat = o.optString("cat")
        val date = o.optString("date")
        val dscp = o.optString("dscp")
        val remarks = o.optString("remarks")
        val responses = o.optJSONArray("responses")
        val noAttach = o.optBoolean("noAttach", true)
        val sendInfoUrl = o.optString("sendInfoUrl")
        val attachUrl = o.optString("attachUrl")
        val withdrawUrl = o.optString("withdrawUrl")

        val sb = StringBuilder()
        sb.append("<html dir='$dir'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><style>${baseStyle()}</style></head><body>")
        sb.append("<div class='header'>${t("متابعة الطلب", "Track Request")}</div><div class='page'>")

        sb.append("<div class='card'>")
        sb.append("<label>${t("التصنيف", "Category")}</label><div>${esc(cat)}</div>")
        sb.append("<label>${t("تاريخ التقديم", "Submit Date")}</label><div>${esc(date)}</div>")
        sb.append("<label>${t("التفاصيل", "Details")}</label><div>${esc(dscp)}</div>")
        if (remarks.isNotBlank()) {
            sb.append("<label>${t("ملاحظات", "Remarks")}</label><div>${esc(remarks)}</div>")
        }
        sb.append("</div>")

        sb.append("<div class='card'><label>${t("الاستجابات", "Responses")}</label>")
        if (responses == null || responses.length() == 0) {
            sb.append("<div class='empty'>${t("لا توجد استجابات بعد", "No responses yet")}</div>")
        } else {
            sb.append("<table class='respTable'>")
            for (i in 0 until responses.length()) {
                val row = responses.optJSONArray(i) ?: continue
                sb.append("<tr>")
                for (j in 0 until row.length()) sb.append("<td>${esc(row.optString(j))}</td>")
                sb.append("</tr>")
            }
            sb.append("</table>")
        }
        sb.append("</div>")

        sb.append("<div class='card'><label>${t("المرفقات", "Attachments")}</label>")
        sb.append(if (noAttach) "<div class='empty'>${t("لم يتم تحميل أي مرفق", "No attachments uploaded")}</div>" else "")
        sb.append("</div>")

        sb.append("<div class='card'>")
        sb.append("<label>${t("رسالة إضافية", "Additional message")}</label>")
        sb.append("<textarea id='infoMsg'></textarea>")
        sb.append("<div class='btnRow'>")
        sb.append("<div class='btn' onclick='sendInfo()'>${t("إرسال البيانات", "Send Info")}</div>")
        sb.append("<div class='btn secondary' onclick='attach()'>${t("تحميل المرفقات", "Attach File")}</div>")
        sb.append("</div>")
        sb.append("<label>${t("سبب الإنسحاب", "Withdrawal reason")}</label>")
        sb.append("<textarea id='quitReason'></textarea>")
        sb.append("<div class='btn danger' onclick='withdraw()'>${t("إنسحاب", "Withdraw")}</div>")
        sb.append("</div>")

        sb.append("</div>")

        sb.append(
            """
            <script>
            function sendInfo(){
                var msg = document.getElementById('infoMsg').value;
                if(typeof AndroidBridge!=='undefined') AndroidBridge.sendInfo('${esc(sendInfoUrl)}', msg);
            }
            function attach(){
                if(typeof AndroidBridge!=='undefined') AndroidBridge.openAttachUrl('${esc(attachUrl)}');
            }
            function withdraw(){
                var reason = document.getElementById('quitReason').value;
                if(!reason){ alert('${t("يرجى كتابة سبب الإنسحاب", "Please enter a withdrawal reason")}'); return; }
                if(typeof AndroidBridge!=='undefined') AndroidBridge.withdraw('${esc(withdrawUrl)}', reason);
            }
            </script>
            """.trimIndent()
        )

        sb.append("</body></html>")
        return sb.toString()
    }
}
