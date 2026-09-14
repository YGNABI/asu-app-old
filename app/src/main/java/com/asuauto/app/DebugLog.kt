package com.asuauto.app

/**
 * A permanent flight-recorder-style log for the SIS/Moodle scraping and
 * matching pipeline. Every meaningful step (data pulled, table empty,
 * course matched/unmatched...) gets one entry here. The dashboard shows
 * a single green checkmark when everything is OK, or the full recorded
 * sequence (including the OK lines, for context) when something isn't —
 * so a problem can be diagnosed from the app itself without needing a
 * screenshot of some specific screen every time.
 *
 * This is meant to stay in the app long-term (not a throwaway debug
 * block) — keep it in sync whenever a new data source or matching step
 * is added.
 */
object DebugLog {

    enum class Level { OK, WARN, ERROR }

    data class Entry(val level: Level, val message: String)

    private val entries = mutableListOf<Entry>()

    fun reset() {
        entries.clear()
    }

    fun ok(message: String) {
        entries.add(Entry(Level.OK, message))
    }

    fun warn(message: String) {
        entries.add(Entry(Level.WARN, message))
    }

    fun error(message: String) {
        entries.add(Entry(Level.ERROR, message))
    }

    fun all(): List<Entry> = entries.toList()

    fun hasIssues(): Boolean = entries.any { it.level != Level.OK }
}
