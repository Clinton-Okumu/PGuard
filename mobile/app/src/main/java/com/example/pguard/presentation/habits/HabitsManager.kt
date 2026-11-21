package com.example.pguard.presentation.habits

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.example.pguard.core.network.NetworkClient
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
    val isTracked: Boolean = true,
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

data class StoicQuote(
    val quote: String,
    val author: String,
    val reflection: String
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
    private const val KEY_CELEBRATED_MILESTONES = "celebrated_milestones_set"
    private const val KEY_LAST_STOIC_SHOWN_DATE = "last_stoic_shown_date"
    private const val KEY_PREFS_VERSION = "prefs_schema_version_v3"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val stoicQuotes = listOf(
        StoicQuote(
            quote = "“He who conquers himself is mightier than he who conquers a city.”",
            author = "Marcus Aurelius",
            reflection = "When urges appear, pause. Self-mastery is not about avoiding temptation; it is the quiet strength to choose your response."
        ),
        StoicQuote(
            quote = "“You have power over your mind — not outside events. Realize this, and you will find strength.”",
            author = "Marcus Aurelius",
            reflection = "External notifications and triggers have no authority over your thumbs. You alone decide your next action."
        ),
        StoicQuote(
            quote = "“No man is free who is not master of himself.”",
            author = "Epictetus",
            reflection = "Freedom is having the discipline to honor the promises you made to yourself when temptation knocks."
        ),
        StoicQuote(
            quote = "“Difficulties strengthen the mind, as labor does the body.”",
            author = "Seneca",
            reflection = "The friction you feel when resisting an urge is neural resistance training. Every pause rewires your brain."
        ),
        StoicQuote(
            quote = "“We suffer more often in imagination than in reality.”",
            author = "Seneca",
            reflection = "The phantom discomfort of an unfulfilled urge lasts only 2 minutes. The clarity of staying clean lasts forever."
        ),
        StoicQuote(
            quote = "“How long are you going to wait before you demand the best for yourself?”",
            author = "Epictetus",
            reflection = "Today is your life in miniature. Win today, and you win everything."
        ),
        StoicQuote(
            quote = "“The soul becomes dyed with the color of its thoughts.”",
            author = "Marcus Aurelius",
            reflection = "Fill your mind with focus, calm, and purpose today. Starve the noise."
        )
    )

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
        val version = prefs.getInt(KEY_PREFS_VERSION, 0)
        if (version < 3) {
            val legacyBest = prefs.getInt(KEY_BEST_STREAK, 0)
            val legacyUrges = prefs.getInt(KEY_URGES_PREVENTED, 0)
            val legacyStart = prefs.getLong(KEY_STREAK_START_TIME, 0L)

            val editor = prefs.edit()
            // If legacy mock values were present (14 best, 23 urges, or start date was ~7 days ago) or first run
            val isLegacyMock = legacyBest == 14 || legacyUrges == 23 ||
                    (legacyStart > 0 && (System.currentTimeMillis() - legacyStart) in (5L * 86400000L)..(9L * 86400000L))

            if (!prefs.contains(KEY_STREAK_START_TIME) || isLegacyMock || version == 0) {
                editor.putLong(KEY_STREAK_START_TIME, System.currentTimeMillis())
                editor.putInt(KEY_BEST_STREAK, 0)
                editor.putInt(KEY_URGES_PREVENTED, 0)
            }
            editor.putInt(KEY_PREFS_VERSION, 3)
            editor.apply()
        }
    }

    fun getStreakStartDateString(context: Context): String {
        initDefaultsIfNeeded(context)
        val startTime = getPrefs(context).getLong(KEY_STREAK_START_TIME, System.currentTimeMillis())
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(startTime))
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
        val savedBest = prefs.getInt(KEY_BEST_STREAK, 0)
        val current = getStreakDays(context)
        return maxOf(savedBest, current)
    }

    fun getUrgesPrevented(context: Context): Int {
        initDefaultsIfNeeded(context)
        return getPrefs(context).getInt(KEY_URGES_PREVENTED, 0)
    }

    fun recordBlockInterception(context: Context) {
        initDefaultsIfNeeded(context)
        val prefs = getPrefs(context)
        val currentCount = prefs.getInt(KEY_URGES_PREVENTED, 0)
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
        val streakStartDate = getStreakStartDateString(context)

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

            // Tracking begins when streak begins
            val isTracked = dateStr >= streakStartDate

            // If today, requires the app doing its job (uninstall protection ON)
            val isClean = isTracked && isPastOrToday && !hadRelapse && (!isToday || isAppDoingJob)

            result.add(
                DayConsistencyStatus(
                    dayLetter = dayNames[i],
                    dayNumber = dayNumber,
                    dateString = dateStr,
                    isPastOrToday = isPastOrToday,
                    isToday = isToday,
                    isTracked = isTracked,
                    isClean = isClean,
                    isRelapse = isTracked && hadRelapse
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
        val streakStartDate = getStreakStartDateString(context)

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

            val isToday = (d == currentDay && month == currentMonth && year == currentYear)
            val isPast = (year < currentYear) ||
                    (year == currentYear && month < currentMonth) ||
                    (year == currentYear && month == currentMonth && d < currentDay)
            val isFuture = !isPast && !isToday

            val hadRelapse = relapseDates.contains(dateStr)
            val isWithinStreak = dateStr >= streakStartDate

            val isClean = isWithinStreak && (isPast || isToday) && !hadRelapse && (!isToday || isAppDoingJob)

            if (isClean && (isPast || isToday)) cleanCount++
            if (isWithinStreak && hadRelapse) relapseCount++

            days.add(
                MonthCalendarDay(
                    dayNumber = d,
                    dateString = dateStr,
                    isPast = isPast,
                    isToday = isToday,
                    isFuture = isFuture,
                    isWithinStreak = isWithinStreak,
                    isClean = isClean,
                    isRelapse = isWithinStreak && hadRelapse
                )
            )
        }

        val totalTrackedDays = days.count { it.isWithinStreak && (it.isPast || it.isToday) }
        val consistencyPercent = if (totalTrackedDays > 0) ((cleanCount.toFloat() / totalTrackedDays) * 100).toInt() else 100

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

    // --- BACKEND CALENDAR & STREAK SYNC ---

    suspend fun syncCalendarWithBackend(context: Context) {
        try {
            val api = NetworkClient.getHabitsApiService(context)
            val streakRes = api.getStreak()
            streakRes.streak?.let { streak ->
                val prefs = getPrefs(context)
                val editor = prefs.edit()
                if (streak.longestStreakDays > getBestStreakDays(context)) {
                    editor.putInt(KEY_BEST_STREAK, streak.longestStreakDays)
                }
                editor.apply()
            }

            val historyRes = api.getRelapseHistory(limit = 100)
            val prefs = getPrefs(context)
            val existingRelapses = prefs.getStringSet(KEY_RELAPSE_DATES, emptySet())?.toMutableSet() ?: mutableSetOf()
            historyRes.relapses.forEach { log ->
                log.createdAt?.take(10)?.let { dateStr ->
                    if (dateStr.length == 10) {
                        existingRelapses.add(dateStr)
                    }
                }
            }
            prefs.edit().putStringSet(KEY_RELAPSE_DATES, existingRelapses).apply()
        } catch (_: Exception) {
            // Graceful fallback to local cache
        }
    }

    // --- GAMIFIED MILESTONES CELEBRATION ---

    fun isMilestoneCelebrated(context: Context, targetDays: Int): Boolean {
        val prefs = getPrefs(context)
        val celebrated = prefs.getStringSet(KEY_CELEBRATED_MILESTONES, emptySet()) ?: emptySet()
        return celebrated.contains(targetDays.toString())
    }

    fun markMilestoneCelebrated(context: Context, targetDays: Int) {
        val prefs = getPrefs(context)
        val celebrated = prefs.getStringSet(KEY_CELEBRATED_MILESTONES, emptySet())?.toMutableSet() ?: mutableSetOf()
        celebrated.add(targetDays.toString())
        prefs.edit().putStringSet(KEY_CELEBRATED_MILESTONES, celebrated).apply()
    }

    fun getUncelebratedMilestone(context: Context, currentStreak: Int, milestoneDays: List<Int>): Int? {
        val prefs = getPrefs(context)
        val celebrated = prefs.getStringSet(KEY_CELEBRATED_MILESTONES, emptySet()) ?: emptySet()
        return milestoneDays
            .filter { currentStreak >= it && !celebrated.contains(it.toString()) }
            .maxOrNull()
    }

    // --- DAILY STOIC REMINDERS ---

    fun getDailyStoicQuote(): StoicQuote {
        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        return stoicQuotes[dayOfYear % stoicQuotes.size]
    }

    fun shouldShowDailyStoic(context: Context): Boolean {
        val prefs = getPrefs(context)
        val lastDate = prefs.getString(KEY_LAST_STOIC_SHOWN_DATE, null)
        return lastDate != todayDateString()
    }

    fun markDailyStoicShown(context: Context) {
        getPrefs(context).edit().putString(KEY_LAST_STOIC_SHOWN_DATE, todayDateString()).apply()
    }

    // --- DAILY HABITS PERSISTENCE & WORKFLOW ---

    fun getHabits(context: Context): List<PersistentHabit> {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_HABITS_JSON, null)
        val today = todayDateString()

        if (jsonStr.isNullOrBlank()) {
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
