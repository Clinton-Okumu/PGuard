package com.example.pguard.presentation.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FocusScheduleTest {

    @Test
    fun `formats duration and time range correctly for standard day window`() {
        val schedule = FocusSchedule(
            startHour = 0,
            startMinute = 0,
            endHour = 5,
            endMinute = 5
        )

        assertEquals(305, schedule.durationMinutes)
        assertEquals("5 hr 5 min", schedule.formattedDuration)
        assertEquals("00:00 - 05:05 (5 hr 5 min)", schedule.formattedTimeRange)
    }

    @Test
    fun `formats duration correctly for overnight window`() {
        val schedule = FocusSchedule(
            startHour = 22,
            startMinute = 30,
            endHour = 6,
            endMinute = 0
        )

        assertEquals(450, schedule.durationMinutes)
        assertEquals("7 hr 30 min", schedule.formattedDuration)
        assertEquals("22:30 - 06:00 (7 hr 30 min)", schedule.formattedTimeRange)
    }

    @Test
    fun `evaluates active state correctly for standard window`() {
        val schedule = FocusSchedule(
            startHour = 9,
            startMinute = 0,
            endHour = 17,
            endMinute = 0,
            isEnabled = true,
            repeatDaily = true
        )

        val calInside = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 30)
        }
        assertTrue(schedule.isCurrentlyActive(calInside))

        val calOutside = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 18)
            set(Calendar.MINUTE, 0)
        }
        assertFalse(schedule.isCurrentlyActive(calOutside))
    }

    @Test
    fun `evaluates active state correctly for overnight window`() {
        val schedule = FocusSchedule(
            startHour = 23,
            startMinute = 0,
            endHour = 5,
            endMinute = 0,
            isEnabled = true,
            repeatDaily = true
        )

        val calNight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 30)
        }
        assertTrue(schedule.isCurrentlyActive(calNight))

        val calMorning = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 4)
            set(Calendar.MINUTE, 15)
        }
        assertTrue(schedule.isCurrentlyActive(calMorning))

        val calDaytime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
        }
        assertFalse(schedule.isCurrentlyActive(calDaytime))
    }

    @Test
    fun `disabled schedule is never active`() {
        val schedule = FocusSchedule(
            startHour = 0,
            startMinute = 0,
            endHour = 23,
            endMinute = 59,
            isEnabled = false
        )

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }
        assertFalse(schedule.isCurrentlyActive(cal))
    }

    @Test
    fun `formats recurrence badges correctly`() {
        val dailySchedule = FocusSchedule(repeatDaily = true)
        assertEquals("Daily", dailySchedule.formattedDaysBadge)

        val weekdaysSchedule = FocusSchedule(
            repeatDaily = false,
            repeatDays = setOf(
                Calendar.MONDAY,
                Calendar.TUESDAY,
                Calendar.WEDNESDAY,
                Calendar.THURSDAY,
                Calendar.FRIDAY
            )
        )
        assertEquals("Weekdays", weekdaysSchedule.formattedDaysBadge)

        val weekendsSchedule = FocusSchedule(
            repeatDaily = false,
            repeatDays = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
        )
        assertEquals("Weekends", weekendsSchedule.formattedDaysBadge)

        val customThreeDays = FocusSchedule(
            repeatDaily = false,
            repeatDays = setOf(Calendar.MONDAY, Calendar.WEDNESDAY, Calendar.FRIDAY)
        )
        assertEquals("Mon, Wed, Fri", customThreeDays.formattedDaysBadge)
    }

    @Test
    fun `evaluates active state with custom days and overnight window correctly`() {
        val schedule = FocusSchedule(
            startHour = 22,
            startMinute = 0,
            endHour = 4,
            endMinute = 0,
            isEnabled = true,
            repeatDaily = false,
            repeatDays = setOf(Calendar.MONDAY) // Only active Monday night into Tuesday morning
        )

        // Monday 23:00 -> Active
        val calMonNight = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 0)
        }
        assertTrue(schedule.isCurrentlyActive(calMonNight))

        // Tuesday 02:00 -> Active (started Monday night)
        val calTueMorning = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY)
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 0)
        }
        assertTrue(schedule.isCurrentlyActive(calTueMorning))

        // Tuesday 23:00 -> Inactive (Tuesday not in repeatDays)
        val calTueNight = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 0)
        }
        assertFalse(schedule.isCurrentlyActive(calTueNight))

        // Wednesday 02:00 -> Inactive (Tuesday was not in repeatDays)
        val calWedMorning = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY)
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 0)
        }
        assertFalse(schedule.isCurrentlyActive(calWedMorning))
    }

    @Test
    fun `zero-duration schedule is invalid and never active`() {
        val zeroSchedule = FocusSchedule(
            startHour = 9,
            startMinute = 0,
            endHour = 9,
            endMinute = 0,
            isEnabled = true,
            repeatDaily = true
        )
        assertFalse(zeroSchedule.isValidDuration)

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
        }
        assertFalse(zeroSchedule.isCurrentlyActive(cal))
    }

    @Test
    fun `displayName trims custom labels and falls back to Focus Session`() {
        assertEquals("Focus Session", FocusSchedule(name = "   ").displayName)
        assertEquals("Deep Work", FocusSchedule(name = "  Deep Work  ").displayName)
    }

    @Test
    fun `isAllowedAcrossSchedules enforces strictest intersection across overlapping schedules`() {
        val lightAllDay = FocusSchedule(
            id = "light",
            startHour = 8,
            startMinute = 0,
            endHour = 20,
            endMinute = 0,
            allowedApps = setOf("com.spotify.music", "com.slack")
        )
        val strictMorning = FocusSchedule(
            id = "strict",
            startHour = 9,
            startMinute = 0,
            endHour = 12,
            endMinute = 0,
            allowedApps = setOf("com.slack")
        )

        val cal10am = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }
        val active = FocusSchedule.activeSchedules(listOf(lightAllDay, strictMorning), cal10am)
        assertEquals(2, active.size)

        // Slack is in both -> allowed; Spotify is missing from strict -> blocked
        assertTrue(FocusSchedule.isAllowedAcrossSchedules("com.slack", active, isEssentialSystemPackage = false))
        assertFalse(FocusSchedule.isAllowedAcrossSchedules("com.spotify.music", active, isEssentialSystemPackage = false))
        assertTrue(FocusSchedule.isAllowedAcrossSchedules("com.android.dialer", active, isEssentialSystemPackage = true))
    }

    @Test
    fun `computeMonotonicRemainingMs resists wall clock manipulation on same boot and clamps across reboot`() {
        // Wall clock jumped 5 hours forward, but elapsedRealtime only advanced 10s -> 110s still left!
        val remainingSameBoot = FocusModeManager.computeMonotonicRemainingMs(
            elapsedEndMs = 220_000L,
            nowElapsedMs = 110_000L,
            wallEndMs = 1_000_120_000L,
            nowWallMs = 1_020_000_000L,
            savedBootCount = 7,
            currentBootCount = 7,
            maxDurationMs = 120_000L
        )
        assertEquals(110_000L, remainingSameBoot)

        // Once monotonic end elapses -> 0 even if wall clock was pushed backwards
        val elapsedExpired = FocusModeManager.computeMonotonicRemainingMs(
            elapsedEndMs = 220_000L,
            nowElapsedMs = 225_000L,
            wallEndMs = 1_000_120_000L,
            nowWallMs = 500_000L,
            savedBootCount = 7,
            currentBootCount = 7,
            maxDurationMs = 120_000L
        )
        assertEquals(0L, elapsedExpired)

        // Across reboot: clamps wall fallback to maxDurationMs
        val acrossRebootClamped = FocusModeManager.computeMonotonicRemainingMs(
            elapsedEndMs = 220_000L,
            nowElapsedMs = 5_000L,
            wallEndMs = 1_000_120_000L,
            nowWallMs = 900_000_000L,
            savedBootCount = 7,
            currentBootCount = 8,
            maxDurationMs = 120_000L
        )
        assertEquals(120_000L, acrossRebootClamped)
    }
}
