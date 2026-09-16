    inner class Bridge {
        @JavascriptInterface
        fun txt(tag: String, value: String) {
            DataStore.fields[tag] = value.trim().takeIf { it != "null" } ?: ""
        }

        @JavascriptInterface
        fun tbl(tag: String, json: String) {
            try {
                var unescaped = json.trim()
                if (unescaped.startsWith("\"") && unescaped.endsWith("\"")) {
                    unescaped = org.json.JSONTokener(unescaped).nextValue() as String
                }
                rawTables[tag] = JSONArray(unescaped)
            } catch (e: Exception) {
                try { rawTables[tag] = JSONArray(json) } catch (e2: Exception) {}
            }
        }

        @JavascriptInterface
        fun planLinks(json: String) {
            try {
                var unescaped = json.trim()
                if (unescaped.startsWith("\"") && unescaped.endsWith("\"")) {
                    unescaped = org.json.JSONTokener(unescaped).nextValue() as String
                }
                val arr = JSONArray(unescaped)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    planLinks.add(o.optString("href"))
                    planNames.add(o.optString("name"))
                    DataStore.planStats.add(
                        listOf(
                            o.optString("name"),
                            o.optString("required"),
                            o.optString("studied"),
                            o.optString("passed")
                        )
                    )
                }
            } catch (e: Exception) {}
        }

        @JavascriptInterface
        fun checkLogin(failed: Boolean) {
            if (failed && phase != "done" && phase != "failed") {
                phase = "failed"
                runOnUiThread {
                    val cached = getSharedPreferences(CACHE_PREFS, MODE_PRIVATE).getString("html", null)
                    if (cached != null) {
                        progressLayout.visibility = View.GONE
                        resultWebView.visibility = View.VISIBLE
                        resultWebView.loadDataWithBaseURL("https://sis.asu.edu.bh/", cached, "text/html", "utf-8", null)
                    } else {
                        finish()
                    }
                }
            }
        }

        @JavascriptInterface
        fun refresh() {
            runOnUiThread {
                if (isNetworkAvailable()) {
                    startScraping()
                } else {
                    android.widget.Toast.makeText(this@SisDashboardActivity, "تتطلب عملية التحديث اتصالاً بالإنترنت", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun showGpaChart() {
            runOnUiThread {
                try {
                    val dialog = android.app.Dialog(this@SisDashboardActivity)
                    val webViewDialog = WebView(this@SisDashboardActivity).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                    }
                    
                    val chartHtml = """
                        <html><head><meta name="viewport" content="width=device-width,initial-scale=1">
                        <style>body{background:#1e1e1e;color:#fff;font-family:sans-serif;padding:16px;text-align:center;}</style>
                        </head><body>
                        <h3>تطور المعدل التراكمي</h3>
                        <p>عرض تفاصيل المعدلات السابقة</p>
                        </body></html>
                    """.trimIndent()
                    
                    webViewDialog.loadDataWithBaseURL(null, chartHtml, "text/html", "utf-8", null)
                    dialog.setContentView(webViewDialog)
                    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
                    
                    val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
                    val height = (resources.displayMetrics.heightPixels * 0.70).toInt()
                    dialog.window?.setLayout(width, height)
                    dialog.show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(this@SisDashboardActivity, "تعذر عرض الرسم البياني", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun showAcademicCalendar() {
            runOnUiThread {
                val calendarDetails = """
                    ■ الفصل الدراسي الأول 2026 / 2027
                    - بدء دوام أعضاء هيئة التدريس: 2026/08/30
                    - الإرشاد والتسجيل والسحب والإضافة: 2026/09/01 إلى 2026/09/05
                    - بدء الدراسة: 2026/09/06
                    - التسجيل المتأخر والسحب والإضافة: 2026/09/06 إلى 2026/09/10
                    - يوم التهيئة للطلبة الجدد: 2026/09/14
                    - اختبارات منتصف الفصل الدراسي: 2026/10/17 إلى 2026/10/31
                    - نهاية فترة الانسحاب من المقررات الدراسية: 2026/10/31
                    - الإرشاد والتسجيل المبكر للفصل الدراسي الثاني: 2026/11/08 إلى 2026/11/12
                    - فترة الامتحانات النهائية: 2026/12/08 إلى 2026/12/26
                    - عطلة العيد الوطني: 2026/12/16 إلى 2026/12/17
                    - بدء إجازة الطلبة: 2026/12/27

                    ■ الفصل الدراسي الثاني 2026 / 2027
                    - الإرشاد والتسجيل والانسحاب والإضافة: 2027/01/05 إلى 2027/01/09
                    - بدء الدراسة: 2027/01/10
                    - التسجيل المتأخر والسحب والإضافة: 2027/01/10 إلى 2027/01/14
                    - يوم التهيئة للطلبة الجدد: 2027/01/18
                    - اختبارات منتصف الفصل الدراسي: 2027/02/20 إلى 2027/03/06
                    - نهاية فترة الانسحاب من المقررات الدراسية: 2027/03/06
                    - عطلة عيد الفطر المبارك: 2027/03/09 إلى 2027/03/11
                    - الإرشاد والتسجيل المبكر للفصل الدراسي الصيفي: 2027/03/14 إلى 2027/03/18
                    - فترة الامتحانات النهائية: 2027/04/15 إلى 2027/04/29
                    - بدء إجازة الطلبة: 2027/04/30

                    ■ الفصل الدراسي الصيفي 2026 / 2027
                    - الإرشاد والتسجيل والانسحاب والإضافة: 2027/05/06 إلى 2027/05/08
                    - بدء الدراسة: 2027/05/09
                    - التسجيل المتأخر والسحب والإضافة للفصل الصيفي: 2027/05/09 إلى 2027/05/11
                    - التسجيل المتأخر والسحب والإضافة للفصل الصيفي الممتد: 2027/05/09 إلى 2027/05/13
                    - عطلة عيد الأضحى المبارك: 2027/05/15 إلى 2027/05/18
                    - اختبارات منتصف الفصل الدراسي الصيفي: 2027/05/29 إلى 2027/06/07
                    - انتهاء فترة الانسحاب من المقررات الدراسية للفصل الصيفي: 2027/06/05
                    - عطلة رأس السنة الهجرية: 2027/06/06
                    - اختبارات منتصف الفصل الصيفي الممتد: 2027/06/08 إلى 2027/06/14
                    - عطلة عاشوراء: 2027/06/15 إلى 2027/06/16
                    - الإرشاد والتسجيل المبكر للفصل الدراسي الأول 2028/2027: 2027/06/20 إلى 2027/06/23
                """.trimIndent()

                android.app.AlertDialog.Builder(this@SisDashboardActivity)
                    .setTitle("التقويم الجامعي 2026 / 2027")
                    .setMessage(calendarDetails)
                    .setPositiveButton("إغلاق", null)
                    .show()
            }
        }

        @JavascriptInterface
        fun addCalendarEvent(title: String, dateStr: String, timeStr: String, location: String) {
            runOnUiThread {
                pendingEvent = Triple(title, dateStr, Pair(timeStr, location))
                if (ContextCompat.checkSelfPermission(this@SisDashboardActivity, android.Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(
                        this@SisDashboardActivity,
                        arrayOf(android.Manifest.permission.WRITE_CALENDAR, android.Manifest.permission.READ_CALENDAR),
                        101
                    )
                } else {
                    insertCalendarEvent(title, dateStr, timeStr, location)
                }
            }
        }

        @JavascriptInterface
        fun openCourseMaterials(moodleId: String, courseName: String) {
            runOnUiThread {
                if (moodleId.isBlank()) return@runOnUiThread
                val intent = Intent(this@SisDashboardActivity, CourseMaterialsActivity::class.java)
                intent.putExtra("moodleId", moodleId)
                intent.putExtra("courseName", courseName)
                startActivity(intent)
            }
        }

        @JavascriptInterface
        fun openCourseAssessments(
            moodleId: String, courseName: String,
            midDate: String, midTime: String, midRoom: String,
            finDate: String, finTime: String, finRoom: String
        ) {
            runOnUiThread {
                if (moodleId.isBlank()) return@runOnUiThread
                val intent = Intent(this@SisDashboardActivity, AssessmentsActivity::class.java)
                intent.putExtra("moodleId", moodleId)
                intent.putExtra("courseName", courseName)
                intent.putExtra("midDate", midDate)
                intent.putExtra("midTime", midTime)
                intent.putExtra("midRoom", midRoom)
                intent.putExtra("finDate", finDate)
                intent.putExtra("finTime", finTime)
                intent.putExtra("finRoom", finRoom)
                startActivity(intent)
            }
        }

        @JavascriptInterface
        fun openSos(sosType: String, specialFlag: Boolean) {
            runOnUiThread {
                val intent = Intent(this@SisDashboardActivity, SosCategoryActivity::class.java)
                intent.putExtra("sosType", sosType)
                intent.putExtra("specialFlag", specialFlag)
                startActivity(intent)
            }
        }

        @JavascriptInterface
        fun openExamExcuses() {
            runOnUiThread {
                val intent = Intent(this@SisDashboardActivity, SosExamExcuseActivity::class.java)
                startActivity(intent)
            }
        }

        @JavascriptInterface
        fun openOutlook(instructorName: String, courseName: String) {
            runOnUiThread {
                try {
                    val emailIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "message/rfc822"
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(instructorName))
                        setPackage("com.microsoft.office.outlook")
                    }
                    if (emailIntent.resolveActivity(packageManager) != null) {
                        startActivity(emailIntent)
                    } else {
                        android.widget.Toast.makeText(
                            this@SisDashboardActivity,
                            "تطبيق Microsoft Outlook غير مثبت على الجهاز",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        this@SisDashboardActivity,
                        "تعذر فتح تطبيق Outlook",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
