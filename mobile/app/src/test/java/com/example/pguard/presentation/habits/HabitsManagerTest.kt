package com.example.pguard.presentation.habits

import com.example.pguard.core.network.model.HabitDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class HabitsManagerTest {

    private val dayMs = 24L * 60L * 60L * 1000L

    private fun utcMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
        millis: Int = 0
    ): Long {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(year, month - 1, day, hour, minute, second)
            set(Calendar.MILLISECOND, millis)
        }.timeInMillis
    }

    @Test
    fun `parseIsoTimestampMillis parses UTC Z and fractional nanoseconds`() {
        assertEquals(
            utcMillis(2026, 11, 10, 8, 30, 45, 0),
            HabitsManager.parseIsoTimestampMillis("2026-11-10T08:30:45Z")
        )
        assertEquals(
            utcMillis(2026, 11, 10, 8, 30, 45, 123),
            HabitsManager.parseIsoTimestampMillis("2026-11-10T08:30:45.123456789Z")
        )
        assertEquals(
            utcMillis(2026, 11, 10, 8, 30, 45, 500),
            HabitsManager.parseIsoTimestampMillis("2026-11-10T08:30:45.5Z")
        )
    }

    @Test
    fun `parseIsoTimestampMillis handles positive and negative timezone offsets`() {
        // 20:00+03:00 == 17:00 UTC
        assertEquals(
            utcMillis(2026, 11, 21, 17, 0, 0, 0),
            HabitsManager.parseIsoTimestampMillis("2026-11-21T20:00:00+03:00")
        )
        // 12:00-05:00 == 17:00 UTC
        assertEquals(
            utcMillis(2026, 11, 21, 17, 0, 0, 250),
            HabitsManager.parseIsoTimestampMillis("2026-11-21T12:00:00.25-05:00")
        )
    }

    @Test
    fun `parseIsoTimestampMillis returns null for invalid or blank timestamps`() {
        assertNull(HabitsManager.parseIsoTimestampMillis(null))
        assertNull(HabitsManager.parseIsoTimestampMillis(""))
        assertNull(HabitsManager.parseIsoTimestampMillis("2026-11-21"))
        assertNull(HabitsManager.parseIsoTimestampMillis("2026-02-30T10:00:00Z"))
        assertNull(HabitsManager.parseIsoTimestampMillis("2026-11-21T25:00:00Z"))
        assertNull(HabitsManager.parseIsoTimestampMillis("2026-11-21T10:00:00+25:00"))
    }

    @Test
    fun `resolveSyncedStreakStartMs restores older server streak on fresh login`() {
        val now = 1_800_000_000_000L
        val localStart = now - 10_000L
        val serverStart = now - 12L * dayMs

        val resolved = HabitsManager.resolveSyncedStreakStartMs(
            localStartMs = localStart,
            serverStartMs = serverStart,
            serverCurrentDays = 12,
            latestServerRelapseMs = null,
            isDifferentAccount = false,
            nowMs = now
        )

        assertEquals(serverStart, resolved)
    }

    @Test
    fun `resolveSyncedStreakStartMs preserves older offline local streak for brand new backend user`() {
        val now = 1_800_000_000_000L
        val localStart = now - 5L * dayMs
        val serverStart = now - 1_000L

        val resolved = HabitsManager.resolveSyncedStreakStartMs(
            localStartMs = localStart,
            serverStartMs = serverStart,
            serverCurrentDays = 0,
            latestServerRelapseMs = null,
            isDifferentAccount = false,
            nowMs = now
        )

        assertEquals(localStart, resolved)
    }

    @Test
    fun `resolveSyncedStreakStartMs advances streak start after remote relapse on another device`() {
        val now = 1_800_000_000_000L
        val localStart = now - 10L * dayMs
        val remoteRelapse = now - 2L * dayMs
        val serverStart = now - 2L * dayMs + 1_000L

        val resolved = HabitsManager.resolveSyncedStreakStartMs(
            localStartMs = localStart,
            serverStartMs = serverStart,
            serverCurrentDays = 1,
            latestServerRelapseMs = remoteRelapse,
            isDifferentAccount = false,
            nowMs = now
        )

        assertEquals(serverStart, resolved)
    }

    @Test
    fun `resolveSyncedStreakStartMs replaces local streak when switching accounts`() {
        val now = 1_800_000_000_000L
        val localStart = now - 20L * dayMs
        val serverStart = now - 3L * dayMs

        val resolved = HabitsManager.resolveSyncedStreakStartMs(
            localStartMs = localStart,
            serverStartMs = serverStart,
            serverCurrentDays = 3,
            latestServerRelapseMs = null,
            isDifferentAccount = true,
            nowMs = now
        )

        assertEquals(serverStart, resolved)
    }

    @Test
    fun `resolveSyncedStreakStartMs falls back to serverCurrentDays when serverStartMs is null`() {
        val now = 1_800_000_000_000L
        val localStart = now - 1_000L

        val resolvedSameAccount = HabitsManager.resolveSyncedStreakStartMs(
            localStartMs = localStart,
            serverStartMs = null,
            serverCurrentDays = 7,
            latestServerRelapseMs = null,
            isDifferentAccount = false,
            nowMs = now
        )
        assertEquals(now - 7L * dayMs, resolvedSameAccount)

        val resolvedDifferentAccount = HabitsManager.resolveSyncedStreakStartMs(
            localStartMs = now - 30L * dayMs,
            serverStartMs = null,
            serverCurrentDays = 4,
            latestServerRelapseMs = null,
            isDifferentAccount = true,
            nowMs = now
        )
        assertEquals(now - 4L * dayMs, resolvedDifferentAccount)
    }

    @Test
    fun `formatLocalDateFromMillis respects local timezone across day boundaries`() {
        // 2026-11-21T22:30:00Z is Nov 22 in UTC+3 and Nov 21 in UTC-5
        val instant = utcMillis(2026, 11, 21, 22, 30, 0, 0)
        assertEquals(
            "2026-11-22",
            HabitsManager.formatLocalDateFromMillis(instant, TimeZone.getTimeZone("GMT+03:00"))
        )
        assertEquals(
            "2026-11-21",
            HabitsManager.formatLocalDateFromMillis(instant, TimeZone.getTimeZone("GMT-05:00"))
        )
    }

    @Test
    fun `previousDateString handles month year and leap year boundaries`() {
        assertEquals("2026-10-31", HabitsManager.previousDateString("2026-11-01"))
        assertEquals("2025-12-31", HabitsManager.previousDateString("2026-01-01"))
        assertEquals("2024-02-29", HabitsManager.previousDateString("2024-03-01"))
        assertEquals("2025-02-28", HabitsManager.previousDateString("2025-03-01"))
        assertNull(HabitsManager.previousDateString("invalid"))
    }

    @Test
    fun `normalizeHabitForDate preserves yesterday streak and resets missed days`() {
        val today = "2026-11-23"
        val completedToday = PersistentHabit(
            id = "1",
            title = "Water",
            currentStreak = 4,
            isCompletedToday = false,
            lastCompletedDate = "2026-11-23"
        )
        val normToday = HabitsManager.normalizeHabitForDate(completedToday, today)
        assertTrue(normToday.isCompletedToday)
        assertEquals(4, normToday.currentStreak)

        val completedYesterday = PersistentHabit(
            id = "2",
            title = "Read",
            currentStreak = 4,
            isCompletedToday = true,
            lastCompletedDate = "2026-11-22"
        )
        val normYesterday = HabitsManager.normalizeHabitForDate(completedYesterday, today)
        assertFalse(normYesterday.isCompletedToday)
        assertEquals(4, normYesterday.currentStreak)

        val missedOneDay = PersistentHabit(
            id = "3",
            title = "Run",
            currentStreak = 7,
            isCompletedToday = true,
            lastCompletedDate = "2026-11-21",
            previousCompletedDate = "2026-11-20"
        )
        val normMissed = HabitsManager.normalizeHabitForDate(missedOneDay, today)
        assertFalse(normMissed.isCompletedToday)
        assertEquals(0, normMissed.currentStreak)
        assertNull(normMissed.previousCompletedDate)
    }

    @Test
    fun `applyHabitToggle increments preserves uncheck rollback and resets broken streaks`() {
        val today = "2026-11-23"

        // Fresh habit toggle ON -> 1, OFF -> 0
        val fresh = PersistentHabit(id = "starter_1", title = "Water", currentStreak = 0)
        val checkedFresh = HabitsManager.applyHabitToggle(fresh, today)
        assertTrue(checkedFresh.isCompletedToday)
        assertEquals(1, checkedFresh.currentStreak)
        assertEquals(today, checkedFresh.lastCompletedDate)

        val uncheckedFresh = HabitsManager.applyHabitToggle(checkedFresh, today)
        assertFalse(uncheckedFresh.isCompletedToday)
        assertEquals(0, uncheckedFresh.currentStreak)
        assertNull(uncheckedFresh.lastCompletedDate)

        // Completed yesterday -> toggle ON -> streak 4, uncheck -> back to streak 3 and yesterday preserved
        val continuing = PersistentHabit(
            id = "10",
            serverId = 10L,
            title = "Read",
            currentStreak = 3,
            isCompletedToday = false,
            lastCompletedDate = "2026-11-22"
        )
        val checked = HabitsManager.applyHabitToggle(continuing, today)
        assertTrue(checked.isCompletedToday)
        assertEquals(4, checked.currentStreak)
        assertEquals("2026-11-23", checked.lastCompletedDate)
        assertEquals("2026-11-22", checked.previousCompletedDate)

        val reverted = HabitsManager.applyHabitToggle(checked, today)
        assertFalse(reverted.isCompletedToday)
        assertEquals(3, reverted.currentStreak)
        assertEquals("2026-11-22", reverted.lastCompletedDate)
        assertNull(reverted.previousCompletedDate)

        // Broken streak from 3 days ago -> toggle ON -> starts fresh at 1 (not 9!)
        val stale = PersistentHabit(
            id = "11",
            serverId = 11L,
            title = "Gym",
            currentStreak = 8,
            isCompletedToday = false,
            lastCompletedDate = "2026-11-19"
        )
        val toggledStale = HabitsManager.applyHabitToggle(stale, today)
        assertTrue(toggledStale.isCompletedToday)
        assertEquals(1, toggledStale.currentStreak)
    }

    @Test
    fun `resolveFirstTrackedDate preserves earliest date even after relapse moves streak start forward`() {
        val resolved = HabitsManager.resolveFirstTrackedDate(
            storedFirstDate = "2026-10-15",
            streakStartDate = "2026-11-20",
            relapseDates = setOf("2026-10-02", "2026-11-20")
        )
        assertEquals("2026-10-02", resolved)
    }

    @Test
    fun `mergeRelapseEntries deduplicates matching entries and retains distinct ones`() {
        val local = listOf(
            LocalRelapseEntry(
                dateString = "2026-11-20",
                trigger = "Late Night",
                notes = "Stayed up scrolling",
                streakDaysAchieved = 5,
                timestampMs = 1_700_000_000_000L
            ),
            LocalRelapseEntry(
                dateString = "2026-11-12",
                trigger = "Stress",
                notes = "",
                streakDaysAchieved = 2,
                timestampMs = 1_699_000_000_000L
            )
        )
        val remote = listOf(
            LocalRelapseEntry(
                dateString = "2026-11-20",
                trigger = "late night",
                notes = "",
                streakDaysAchieved = 0,
                timestampMs = 1_700_000_015_000L // within 2 minutes
            ),
            LocalRelapseEntry(
                dateString = "2026-11-20",
                trigger = "Boredom",
                notes = "Afternoon lapse",
                streakDaysAchieved = 0,
                timestampMs = 1_699_980_000_000L
            )
        )

        val merged = HabitsManager.mergeRelapseEntries(local, remote)
        assertEquals(3, merged.size)
        val mergedLateNight = merged.first { it.dateString == "2026-11-20" && it.trigger.equals("Late Night", true) }
        assertEquals("Stayed up scrolling", mergedLateNight.notes)
        assertEquals(5, mergedLateNight.streakDaysAchieved)
    }

    @Test
    fun `reconcileRemoteAndLocalHabits drops untouched starters preserves offline habits and filters deletes`() {
        val today = "2026-11-23"
        val remote = listOf(
            HabitDto(id = 42L, title = "Morning Walk", icon = "walking", isCompleted = true, streakDays = 3),
            HabitDto(id = 43L, title = "Deleted Habit", icon = "book", isCompleted = false, streakDays = 0)
        )
        val local = listOf(
            PersistentHabit(id = "starter_1", title = "Drink 2.5L Water", currentStreak = 0, isCompletedToday = false),
            PersistentHabit(id = "local_999", title = "Offline Meditation", iconKey = "meditation", currentStreak = 1, isCompletedToday = true, lastCompletedDate = today, pendingSync = true),
            PersistentHabit(id = "local_dup", title = "morning walk", iconKey = "walking", pendingSync = true)
        )

        val reconciled = HabitsManager.reconcileRemoteAndLocalHabits(
            remoteHabits = remote,
            localHabits = local,
            pendingDeletedIds = setOf(43L),
            today = today
        )

        assertEquals(2, reconciled.size)
        val synced = reconciled.first { it.serverId == 42L }
        assertEquals("42", synced.id)
        assertTrue(synced.isCompletedToday)
        assertEquals(3, synced.currentStreak)
        assertEquals(today, synced.lastCompletedDate)
        assertEquals("2026-11-22", synced.previousCompletedDate)

        val offline = reconciled.first { it.id == "local_999" }
        assertEquals("Offline Meditation", offline.title)
        assertTrue(offline.isCompletedToday)
    }
}
