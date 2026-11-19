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
}
