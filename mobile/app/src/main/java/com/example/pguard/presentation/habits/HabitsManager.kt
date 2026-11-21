package com.example.pguard.presentation.habits

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.example.pguard.engine.accessibility.PGuardAccessibilityService
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DayConsistencyStatus(
    val dayLetter: String,
    val dayNumber: Int,
    val dateString: String,
    val isPastOrToday: Boolean,
    val isToday: Boolean,
    val isClean: Boolean,
    val isRelapse: Boolean = false
)

data class StreakStatus(
    val streakDays: Int,
    val bestStreakDays: Int,
    val isStreakActive: Boolean,
    val inactiveReason: String? = null
)

data class MonthCalendarDay(
    val dayNumber: Int,
    val dateString: String,
    val isPast: Boolean,
    val isToday: Boolean,
    val isFuture: Boolean,
    val isWithinStreak: Boolean,
    val isClean: Boolean,
    val isRelapse: Boolean
)

data class MonthCalendarData(
    val year: Int,
    val month: Int,
    val monthName: String,
    val firstDayOfWeekOffset: Int,
    val days: List<MonthCalendarDay>,
    val cleanCount: Int,
    val relapseCount: Int,
    val consistencyPercent: Int
)

@Serializable
data class PersistentHabit(
    val id: String,
    val title: String,
    val iconKey: String = "book",
    val currentStreak: Int = 0,
    val isCompletedToday: Boolean = false,
    val lastCompletedDate: String? = null,
    val targetDays: Int = 30
)

object HabitsManager {
    private const val PREFS_NAME = "pguard_habits_prefs"
    private const val KEY_STREAK_START_TIME = "streak_start_timestamp"
    private const val KEY_BEST_STREAK = "best_streak_days"
    private const val KEY_URGES_PREVENTED = "urges_prevented_count"
    private const val KEY_RELAPSE_DATES = "relapse_dates_set"
    private const val KEY_LAST_INTERCEPTION_TIME = "last_interception_timestamp"
    private const val KEY_HABITS_JSON = "persistent_habits_json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun todayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun isUninstallProtectionEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PGuardAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(PGuardAccessibilityService.KEY_UNINSTALL, false)
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            enabledServices.contains(context.packageName)
        } catch (_: Exception) {
            false
        }
    }

    fun isAppDoingItsJob(context: Context): Boolean {
        return isAccessibilityServiceEnabled(context) && isUninstallProtectionEnabled(context)
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

    fun getStreakStatus(context: Context): StreakStatus {
        initDefaultsIfNeeded(context)
        val isServiceOn = isAccessibilityServiceEnabled(context)
        val isUninstallOn = isUninstallProtectionEnabled(context)
        val isDoingJob = isServiceOn && isUninstallOn

        val rawStreakDays = getStreakDays(context)
        val bestDays = getBestStreakDays(context)

        val reason = when {
            !isServiceOn -> "Accessibility Service is OFF. Turn it ON to activate streak."
            !isUninstallOn -> "Uninstall Protection is OFF. Turn it ON in Settings to activate streak."
            else -> null
        }

        return StreakStatus(
            streakDays = if (isDoingJob) rawStreakDays else 0,
            bestStreakDays = bestDays,
            isStreakActive = isDoingJob,
            inactiveReason = reason
        )
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
        val isAppDoingJob = isAppDoingItsJob(context)

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

            // If today, requires the app doing its job (uninstall protection ON)
            val isClean = isPastOrToday && !hadRelapse && (!isToday || isAppDoingJob)

            result.add(
                DayConsistencyStatus(
                    dayLetter = dayNames[i],
                    dayNumber = dayNumber,
                    dateString = dateStr,
                    isPastOrToday = isPastOrToday,
                    isToday = isToday,
                    isClean = isClean,
                    isRelapse = hadRelapse
                )
            )

            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        return result
    }

    fun getMonthCalendar(context: Context, year: Int, month: Int): MonthCalendarData {
        initDefaultsIfNeeded(context)
        val prefs = getPrefs(context)
        val relapseDates = prefs.getStringSet(KEY_RELAPSE_DATES, emptySet()) ?: emptySet()
        val isAppDoingJob = isAppDoingItsJob(context)
        val streakStartTime = prefs.getLong(KEY_STREAK_START_TIME, System.currentTimeMillis())

        val nowCal = Calendar.getInstance()
        val currentDay = nowCal.get(Calendar.DAY_OF_MONTH)
        val currentMonth = nowCal.get(Calendar.MONTH)
        val currentYear = nowCal.get(Calendar.YEAR)

        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val monthNameFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val monthName = monthNameFormat.format(cal.time)

        // Day of week: Calendar.SUNDAY=1, MONDAY=2, ... SATURDAY=7
        // We want Monday=0, Tuesday=1 ... Sunday=6
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val firstDayOffset = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - 2

        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val days = mutableListOf<MonthCalendarDay>()
        var cleanCount = 0
        var relapseCount = 0

        for (d in 1..daysInMonth) {
            cal.set(Calendar.DAY_OF_MONTH, d)
            val dateStr = dateFormat.format(cal.time)
            val dayTime = cal.timeInMillis

            val isToday = (d == currentDay && month == currentMonth && year == currentYear)
            val isPast = (year < currentYear) ||
                    (year == currentYear && month < currentMonth) ||
                    (year == currentYear && month == currentMonth && d < currentDay)
            val isFuture = !isPast && !isToday

            val hadRelapse = relapseDates.contains(dateStr)
            val isWithinStreak = dayTime >= streakStartTime

            val isClean = (isPast || isToday) && !hadRelapse && (!isToday || isAppDoingJob)

            if (isClean && (isPast || isToday)) cleanCount++
            if (hadRelapse) relapseCount++

            days.add(
                MonthCalendarDay(
                    dayNumber = d,
                    dateString = dateStr,
                    isPast = isPast,
                    isToday = isToday,
                    isFuture = isFuture,
                    isWithinStreak = isWithinStreak,
                    isClean = isClean,
                    isRelapse = hadRelapse
                )
            )
        }

        val totalPastDays = days.count { it.isPast || it.isToday }
        val consistencyPercent = if (totalPastDays > 0) ((cleanCount.toFloat() / totalPastDays) * 100).toInt() else 100

        return MonthCalendarData(
            year = year,
            month = month,
            monthName = monthName,
            firstDayOfWeekOffset = firstDayOffset,
            days = days,
            cleanCount = cleanCount,
            relapseCount = relapseCount,
            consistencyPercent = consistencyPercent
        )
    }

    // --- DAILY HABITS PERSISTENCE & WORKFLOW ---

    fun getHabits(context: Context): List<PersistentHabit> {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_HABITS_JSON, null)
        val today = todayDateString()

        if (jsonStr.isNullOrBlank()) {
            // First time: provide starter habits (user can remove all of them)
            val starterHabits = listOf(
                PersistentHabit(id = "1", title = "Drink 2.5L Water", iconKey = "water", currentStreak = 3, isCompletedToday = false),
                PersistentHabit(id = "2", title = "Workout / Exercise", iconKey = "fitness", currentStreak = 5, isCompletedToday = false),
                PersistentHabit(id = "3", title = "Read 15 Mins", iconKey = "book", currentStreak = 2, isCompletedToday = false)
            )
            saveHabits(context, starterHabits)
            return starterHabits
        }

        return try {
            val list = json.decodeFromString<List<PersistentHabit>>(jsonStr)
            // Daily reset logic: if lastCompletedDate != today, mark isCompletedToday = false
            val updated = list.map { habit ->
                if (habit.lastCompletedDate != today && habit.isCompletedToday) {
                    habit.copy(isCompletedToday = false)
                } else {
                    habit
                }
            }
            if (updated != list) {
                saveHabits(context, updated)
            }
            updated
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveHabits(context: Context, habits: List<PersistentHabit>) {
        try {
            val jsonStr = json.encodeToString(habits)
            getPrefs(context).edit().putString(KEY_HABITS_JSON, jsonStr).apply()
        } catch (_: Exception) {}
    }

    fun addHabit(context: Context, title: String, iconKey: String, targetDays: Int = 30): PersistentHabit {
        val current = getHabits(context).toMutableList()
        val newHabit = PersistentHabit(
            id = System.currentTimeMillis().toString(),
            title = title.trim(),
            iconKey = iconKey,
            currentStreak = 0,
            isCompletedToday = false,
            targetDays = targetDays
        )
        current.add(newHabit)
        saveHabits(context, current)
        return newHabit
    }

    fun deleteHabit(context: Context, habitId: String): List<PersistentHabit> {
        val current = getHabits(context).filter { it.id != habitId }
        saveHabits(context, current)
        return current
    }

    fun toggleHabit(context: Context, habitId: String): List<PersistentHabit> {
        val today = todayDateString()
        val current = getHabits(context).toMutableList()
        val index = current.indexOfFirst { it.id == habitId }
        if (index != -1) {
            val item = current[index]
            val newState = !item.isCompletedToday
            val newStreak = if (newState) item.currentStreak + 1 else maxOf(0, item.currentStreak - 1)
            val updated = item.copy(
                isCompletedToday = newState,
                currentStreak = newStreak,
                lastCompletedDate = if (newState) today else null
            )
            current[index] = updated
            saveHabits(context, current)
        }
        return current
    }
}
