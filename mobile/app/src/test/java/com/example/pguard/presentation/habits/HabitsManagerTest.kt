package com.example.pguard.presentation.habits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
