package com.shreenathji.sales.service

import com.google.common.truth.Truth.assertThat
import com.shreenathji.sales.service.TrackingHealth.Status
import com.shreenathji.sales.service.TrackingHealth.evaluate
import org.junit.Test

class TrackingHealthTest {
    private fun ok(
        licensed: Boolean = true, hour: Int = 12, fine: Boolean = true, bg: Boolean = true,
        gps: Boolean = true, age: Long? = 60_000L
    ) = evaluate(licensed, hour, fine, bg, gps, age)

    @Test fun healthy_when_recent_fix() = assertThat(ok()).isEqualTo(Status.OK)

    @Test fun ignored_outside_work_hours_or_unlicensed() {
        assertThat(ok(hour = 3, age = null)).isEqualTo(Status.NOT_APPLICABLE)
        assertThat(ok(hour = 21, age = null)).isEqualTo(Status.NOT_APPLICABLE)
        assertThat(ok(licensed = false, age = null)).isEqualTo(Status.NOT_APPLICABLE)
    }

    @Test fun permission_problem_beats_other_signals() {
        assertThat(ok(bg = false, age = null)).isEqualTo(Status.NEEDS_PERMISSION)
        assertThat(ok(fine = false)).isEqualTo(Status.NEEDS_PERMISSION)
    }

    @Test fun gps_off_reported_before_stale() = assertThat(ok(gps = false, age = null)).isEqualTo(Status.GPS_OFF)

    @Test fun stale_after_ten_minutes_or_no_fix_at_all() {
        assertThat(ok(age = TrackingHealth.STALE_AFTER_MS + 1)).isEqualTo(Status.STALE)
        assertThat(ok(age = null)).isEqualTo(Status.STALE)
        assertThat(ok(age = TrackingHealth.STALE_AFTER_MS)).isEqualTo(Status.OK)
    }
}
