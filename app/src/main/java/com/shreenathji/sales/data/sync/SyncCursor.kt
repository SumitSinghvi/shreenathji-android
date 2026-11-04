package com.shreenathji.sales.data.sync

/**
 * Helpers for server `updated_at` / `deleted_at` cursors.
 *
 * PostgREST returns timestamptz with a variable number of fractional digits
 * ("...:00.12+00:00" vs "...:00.123456+00:00"), so plain string comparison is wrong.
 * We pad the fraction to 6 digits; after that, same-offset timestamps compare correctly as text.
 */
object SyncCursor {
    private val TS = Regex("""^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d+))?(.*)$""")

    fun normalize(ts: String): String {
        val m = TS.matchEntire(ts) ?: return ts
        val (base, frac, rest) = m.destructured
        return "$base.${frac.padEnd(6, '0').take(6)}$rest"
    }

    /** Latest of [timestamps], normalized; null if there are none. */
    fun max(timestamps: Iterable<String?>): String? =
        timestamps.filterNotNull().filter { it.isNotBlank() }.map(::normalize).maxOrNull()
}
