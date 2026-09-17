package com.asuauto.app

/**
 * One course entry as it appears on the Moodle "My courses" page
 * (https://elearning.asu.edu.bh/my/).
 */
data class CourseSummary(
    val moodleId: String,   // Moodle course id, e.g. "1062"
    val name: String,        // "Economic & Electronic Crimes"
    val category: String     // "College of Law" (not currently used for matching)
)
