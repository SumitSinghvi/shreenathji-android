package com.shreenathji.sales.data.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SyncCursorTest {
    @Test
    fun normalize_pads_fraction_to_six_digits() {
        assertThat(SyncCursor.normalize("2026-11-04T10:00:00.12+00:00"))
            .isEqualTo("2026-11-04T10:00:00.120000+00:00")
        assertThat(SyncCursor.normalize("2026-11-04T10:00:00+00:00"))
            .isEqualTo("2026-11-04T10:00:00.000000+00:00")
    }

    @Test
    fun max_compares_by_time_not_by_raw_text() {
        // Raw text compares ".123456+" below ".9+" only by luck of the digits; padding makes it reliable.
        val result = SyncCursor.max(listOf("2026-11-04T10:00:00.9+00:00", "2026-11-04T10:00:00.123456+00:00"))
        assertThat(result).isEqualTo("2026-11-04T10:00:00.900000+00:00")
    }

    @Test
    fun max_ignores_nulls_and_returns_null_when_empty() {
        assertThat(SyncCursor.max(listOf(null, ""))).isNull()
    }
}
