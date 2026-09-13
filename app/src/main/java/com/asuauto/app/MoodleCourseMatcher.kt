package com.asuauto.app

/**
 * Links a SIS course code (from DataStore.registration) to its Moodle
 * course id (from the /my/ course list), by comparing course names.
 *
 * Why by name and not by code: SIS course codes (e.g. "NLAW-334-1") and
 * Moodle course ids (e.g. "1062") are unrelated numbering systems with no
 * shared key. The course *name* is the only thing both sides show the
 * same way — SIS registration table's "اسم المقرر" column holds the
 * English course name (see DashboardHtmlBuilder.courses(), x.nameEn),
 * and Moodle's /my/ page also shows English names — so we normalize both
 * and compare.
 */
object MoodleCourseMatcher {

    /**
     * @return map of normalized SIS course code -> Moodle course id,
     *         for every SIS course that found a Moodle name match.
     */
    fun match(
        registration: List<List<String>>,
        moodleCourses: List<CourseSummary>
    ): Map<String, String> {
        if (registration.size < 2 || moodleCourses.isEmpty()) return emptyMap()

        val header = registration[0]
        val codeIdx = colIdx(header, "رمز")
        val nameIdx = colIdx(header, "اسم المقرر")
        if (codeIdx < 0 || nameIdx < 0) return emptyMap()

        val moodleByNormName = moodleCourses.associateBy { normalizeName(it.name) }

        val result = mutableMapOf<String, String>()
        for (r in 1 until registration.size) {
            val row = registration[r]
            val code = row.getOrNull(codeIdx)?.trim().orEmpty()
            val name = row.getOrNull(nameIdx)?.trim().orEmpty()
            if (code.isBlank() || name.isBlank()) continue

            val normalized = normalizeName(name)
            val exact = moodleByNormName[normalized]
            val match = exact ?: bestFuzzyMatch(normalized, moodleByNormName)
            if (match != null) {
                result[normalizeCode(code)] = match.moodleId
            }
        }
        return result
    }

    private fun normalizeCode(code: String) =
        code.uppercase().replace(Regex("[^A-Z0-9]"), "")

    /** Lowercase, strip punctuation/whitespace, so "Economic & Electronic Crimes"
     *  and "economic electronic crimes" compare equal. */
    private fun normalizeName(name: String) =
        name.lowercase().replace(Regex("[^a-z0-9\\u0600-\\u06FF]"), "")

    /**
     * Fallback when normalized names aren't byte-identical (extra words,
     * different ampersand handling, etc). Picks the Moodle course whose
     * normalized name shares the most characters in common via a simple
     * containment/overlap check — good enough for course-name variants,
     * not a general string-similarity algorithm.
     */
    private fun bestFuzzyMatch(
        target: String,
        candidates: Map<String, CourseSummary>
    ): CourseSummary? {
        if (target.length < 6) return null // too short to fuzzy-match safely
        var best: CourseSummary? = null
        var bestScore = 0
        for ((candidateName, course) in candidates) {
            val score = when {
                candidateName.indexOf(target) >= 0 || target.indexOf(candidateName) >= 0 ->
                    minOf(candidateName.length, target.length)
                else -> longestCommonSubstring(target, candidateName)
            }
            // Require a reasonably strong overlap before accepting a fuzzy match.
            val threshold = (minOf(target.length, candidateName.length) * 0.7).toInt()
            if (score >= threshold && score > bestScore) {
                bestScore = score
                best = course
            }
        }
        return best
    }

    private fun longestCommonSubstring(a: String, b: String): Int {
        if (a.isEmpty() || b.isEmpty()) return 0
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        var max = 0
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                if (a[i - 1] == b[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1] + 1
                    if (dp[i][j] > max) max = dp[i][j]
                }
            }
        }
        return max
    }

    private fun colIdx(header: List<String>, needle: String): Int {
        for (i in header.indices) if (header[i].replace("\n", " ").contains(needle)) return i
        return -1
    }
}
