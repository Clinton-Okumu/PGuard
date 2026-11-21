package com.example.pguard.presentation.habits

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DayConsistencyStatus(
    val dayLetter: String,
    val dayNumber: Int,
    val isPastOrToday: Boolean,
    val isToday: Boolean,
    val isClean: Boolean
)

object HabitsManager {
    private const val PREFS_NAME = "pguard_habits_prefs"
    private const val KEY_STREAK_START_TIME = "streak_start_timestamp"
    private const val KEY_BEST_STREAK = "best_streak_days"
    private const val KEY_URGES_PREVENTED = "urges_prevented_count"
    private const val KEY_RELAPSE_DATES = "relapse_dates_set"
    private const val KEY_LAST_INTERCEPTION_TIME = "last_interception_timestamp"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun todayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun initDefaultsIfNeeded(context: Context) {
        val prefs = getPrefs(context)
        if (!prefs.contains(KEY_STREAK_START_TIME)) {
            // Default initial clean streak: 7 days ago so users start with initial momentum
            val initialStreakStart = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000L)
            prefs.edit()
                .putLong(KEY_STREAK_START_TIME, initialStreakStart)
                .putInt(KEY_BEST_STREAK, 14)
                .putInt(KEY_URGES_PREVENTED, 23)
                .apply()
        }
    }

    fun getStreakDays(context: Context): Int {
        initDefaultsIfNeeded(context)
        val prefs = getPrefs(context)
        val startTime = prefs.getLong(KEY_STREAK_START_TIME, System.currentTimeMillis())
        val diffMs = System.currentTimeMillis() - startTime
        return if (diffMs <= 0) 0 else (diffMs / (24L * 60 * 60 * 1000L)).toInt()
    }

    fun getBestStreakDays(context: Context): Int {
        initDefaultsIfNeeded(context)
        val prefs = getPrefs(context)
        val savedBest = prefs.getInt(KEY_BEST_STREAK, 14)
        val current = getStreakDays(context)
        return maxOf(savedBest, current)
    }

    fun getUrgesPrevented(context: Context): Int {
        initDefaultsIfNeeded(context)
        return getPrefs(context).getInt(KEY_URGES_PREVENTED, 23)
    }

    fun recordBlockInterception(context: Context) {
        initDefaultsIfNeeded(context)
        val prefs = getPrefs(context)
        val currentCount = prefs.getInt(KEY_URGES_PREVENTED, 23)
        prefs.edit()
            .putInt(KEY_URGES_PREVENTED, currentCount + 1)
            .putLong(KEY_LAST_INTERCEPTION_TIME, System.currentTimeMillis())
            .apply()
    }

    fun recordUrgeSurvived(context: Context) {
        recordBlockInterception(context)
    }

    fun resetStreak(context: Context, trigger: String = "Stress", note: String = "") {
        initDefaultsIfNeeded(context)
        val prefs = getPrefs(context)
        val currentStreak = getStreakDays(context)
        val currentBest = getBestStreakDays(context)
        val newBest = maxOf(currentBest, currentStreak)

        val today = todayDateString()
        val relapseDates = prefs.getStringSet(KEY_RELAPSE_DATES, emptySet())?.toMutableSet() ?: mutableSetOf()
        relapseDates.add(today)

        prefs.edit()
            .putLong(KEY_STREAK_START_TIME, System.currentTimeMillis())
            .putInt(KEY_BEST_STREAK, newBest)
            .putStringSet(KEY_RELAPSE_DATES, relapseDates)
            .apply()
    }

    fun getWeeklyConsistency(context: Context): List<DayConsistencyStatus> {
        initDefaultsIfNeeded(context)
        val prefs = getPrefs(context)
        val relapseDates = prefs.getStringSet(KEY_RELAPSE_DATES, emptySet()) ?: emptySet()

        val calendar = Calendar.getInstance()
        val currentDayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val currentYear = calendar.get(Calendar.YEAR)

        // Set calendar to Monday of the current week
        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

        val dayNames = listOf("M", "T", "W", "T", "F", "S", "S")
        val result = mutableListOf<DayConsistencyStatus>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        for (i in 0 until 7) {
            val dateStr = dateFormat.format(calendar.time)
            val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
            val year = calendar.get(Calendar.YEAR)
            val dayNumber = calendar.get(Calendar.DAY_OF_MONTH)

            val isToday = (dayOfYear == currentDayOfYear && year == currentYear)
            val isPast = (year < currentYear) || (year == currentYear && dayOfYear < currentDayOfYear)
            val isPastOrToday = isPast || isToday
            val hadRelapse = relapseDates.contains(dateStr)

            result.add(
                DayConsistencyStatus(
                    dayLetter = dayNames[i],
                    dayNumber = dayNumber,
                    isPastOrToday = isPastOrToday,
                    isToday = isToday,
                    isClean = isPastOrToday && !hadRelapse
                )
            )

            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        return result
    }
}
