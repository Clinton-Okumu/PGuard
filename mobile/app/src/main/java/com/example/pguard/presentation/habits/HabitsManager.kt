package com.example.pguard.presentation.habits

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.example.pguard.core.common.runSuspendCatching
import com.example.pguard.core.network.NetworkClient
import com.example.pguard.core.network.model.CreateHabitRequest
import com.example.pguard.core.network.model.HabitDto
import com.example.pguard.engine.accessibility.PGuardAccessibilityService
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

enum class StreakInactiveReason {
    ACCESSIBILITY_OFF,
    UNINSTALL_PROTECTION_OFF
}

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
    val inactiveReason: String? = null,
    val inactiveReasonType: StreakInactiveReason? = null
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
    val consistencyPercent: Int,
    val trackedDaysCount: Int = 0
)

data class StoicQuote(
    val quote: String,
    val author: String,
    val reflection: String
)

@Serializable
data class LocalRelapseEntry(
    val dateString: String,
    val trigger: String,
    val notes: String = "",
    val streakDaysAchieved: Int = 0,
    val timestampMs: Long = 0L
)

@Serializable
data class PersistentHabit(
    val id: String,
    val serverId: Long? = null,
    val title: String,
    val iconKey: String = "book",
    val currentStreak: Int = 0,
    val isCompletedToday: Boolean = false,
    val lastCompletedDate: String? = null,
    val previousCompletedDate: String? = null,
    val pendingSync: Boolean = false
)

object HabitsManager {
    const val MAX_HABIT_TITLE_LENGTH = 100
    const val MAX_RELAPSE_NOTE_LENGTH = 500

    private const val PREFS_NAME = "pguard_habits_prefs"
    private const val KEY_STREAK_START_TIME = "streak_start_timestamp"
    private const val KEY_FIRST_TRACKED_DATE = "first_tracked_date"
    private const val KEY_BEST_STREAK = "best_streak_days"
    private const val KEY_URGES_PREVENTED = "urges_prevented_count"
    private const val KEY_RELAPSE_DATES = "relapse_dates_set"
    private const val KEY_RELAPSE_LOGS_JSON = "relapse_logs_json"
    private const val KEY_LAST_INTERCEPTION_TIME = "last_interception_timestamp"
    private const val KEY_HABITS_JSON = "persistent_habits_json"
    private const val KEY_PENDING_DELETED_HABIT_IDS = "pending_deleted_habit_ids"
    private const val KEY_CELEBRATED_MILESTONES = "celebrated_milestones_set"
    private const val KEY_LAST_STOIC_SHOWN_DATE = "last_stoic_shown_date"
    private const val KEY_PREFS_VERSION = "prefs_schema_version_v3"
    private const val KEY_SYNCED_USER_ID = "synced_user_id"
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    private const val STARTER_PREFIX = "starter_"
    private const val LOCAL_PREFIX = "local_"

    private val LEGACY_STARTER_TITLES = mapOf(
        "1" to "Drink 2.5L Water",
        "2" to "Workout / Exercise",
        "3" to "Read 15 Mins"
    )

    private val ISO_TIMESTAMP_REGEX = Regex(
        """^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?(Z|([+-])(\d{2}):(\d{2}))$"""
    )
    private val DATE_STRING_REGEX = Regex("""^\d{4}-\d{2}-\d{2}$""")

    private val habitsLock = Any()

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

    internal fun formatLocalDateFromMillis(
        epochMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            this.timeZone = timeZone
        }
        return formatter.format(Date(epochMs))
    }

    internal fun previousDateString(dateStr: String): String? {
        if (!DATE_STRING_REGEX.matches(dateStr)) return null
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                isLenient = false
            }
            val parsed = parser.parse(dateStr) ?: return null
            val cal = Calendar.getInstance(Locale.US).apply {
                time = parsed
                add(Calendar.DAY_OF_MONTH, -1)
            }
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        } catch (_: Exception) {
            null
        }
    }

    internal fun resolveFirstTrackedDate(
        storedFirstDate: String?,
        streakStartDate: String,
        relapseDates: Set<String>
    ): String {
        val candidates = buildList {
            storedFirstDate?.takeIf { DATE_STRING_REGEX.matches(it) }?.let { add(it) }
            streakStartDate.takeIf { DATE_STRING_REGEX.matches(it) }?.let { add(it) }
            relapseDates.filter { DATE_STRING_REGEX.matches(it) }.forEach { add(it) }
        }
        return candidates.minOrNull() ?: streakStartDate
    }

    internal fun normalizeHabitForDate(habit: PersistentHabit, today: String): PersistentHabit {
        val yesterday = previousDateString(today)
        return when (habit.lastCompletedDate) {
            today -> habit.copy(
                isCompletedToday = true,
                currentStreak = maxOf(1, habit.currentStreak)
            )
            yesterday -> habit.copy(isCompletedToday = false)
            else -> habit.copy(
                isCompletedToday = false,
                currentStreak = 0,
                previousCompletedDate = null
            )
        }
    }

    internal fun applyHabitToggle(habit: PersistentHabit, today: String): PersistentHabit {
        val normalized = normalizeHabitForDate(habit, today)
        val yesterday = previousDateString(today)
        return if (!normalized.isCompletedToday) {
            val newStreak = if (yesterday != null && normalized.lastCompletedDate == yesterday) {
                normalized.currentStreak + 1
            } else {
                1
            }
            normalized.copy(
                isCompletedToday = true,
                currentStreak = newStreak,
                previousCompletedDate = normalized.lastCompletedDate,
                lastCompletedDate = today
            )
        } else {
            val restoredLast = normalized.previousCompletedDate
            val newStreak = if (yesterday != null && restoredLast == yesterday) {
                maxOf(0, normalized.currentStreak - 1)
            } else {
                0
            }
            normalized.copy(
                isCompletedToday = false,
                currentStreak = newStreak,
                lastCompletedDate = restoredLast,
                previousCompletedDate = null
            )
        }
    }

    internal fun mergeRelapseEntries(
        local: List<LocalRelapseEntry>,
        remote: List<LocalRelapseEntry>
    ): List<LocalRelapseEntry> {
        val merged = mutableListOf<LocalRelapseEntry>()
        for (entry in local + remote) {
            if (!DATE_STRING_REGEX.matches(entry.dateString)) continue
            val normTrigger = entry.trigger.trim().lowercase(Locale.US)
            val normNotes = entry.notes.trim().lowercase(Locale.US)

            val matchIndex = merged.indexOfFirst { existing ->
                val sameDate = existing.dateString == entry.dateString
                val sameTrigger = existing.trigger.trim().lowercase(Locale.US) == normTrigger
                val sameNotes = existing.notes.trim().lowercase(Locale.US) == normNotes
                val closeTimestamp = existing.timestampMs > 0L && entry.timestampMs > 0L &&
                    abs(existing.timestampMs - entry.timestampMs) <= 120_000L
                sameDate && sameTrigger && (sameNotes || closeTimestamp)
            }

            if (matchIndex == -1) {
                merged.add(
                    entry.copy(
                        trigger = entry.trigger.trim().ifBlank { "Other" },
                        notes = entry.notes.trim()
                    )
                )
            } else {
                val existing = merged[matchIndex]
                val richerNotes = when {
                    entry.notes.trim().length > existing.notes.trim().length -> entry.notes.trim()
                    else -> existing.notes.trim()
                }
                merged[matchIndex] = existing.copy(
                    trigger = existing.trigger.trim().ifBlank { entry.trigger.trim().ifBlank { "Other" } },
                    notes = richerNotes,
                    streakDaysAchieved = maxOf(existing.streakDaysAchieved, entry.streakDaysAchieved),
                    timestampMs = maxOf(existing.timestampMs, entry.timestampMs)
                )
            }
        }
        return merged.sortedWith(
            compareByDescending<LocalRelapseEntry> { it.dateString }
                .thenByDescending { it.timestampMs }
        )
    }

    internal fun reconcileRemoteAndLocalHabits(
        remoteHabits: List<HabitDto>,
        localHabits: List<PersistentHabit>,
        pendingDeletedIds: Set<Long>,
        today: String
    ): List<PersistentHabit> {
        val yesterday = previousDateString(today)
        val filteredRemote = remoteHabits.filterNot { it.id in pendingDeletedIds }

        val mappedRemote = filteredRemote.map { dto ->
            PersistentHabit(
                id = dto.id.toString(),
                serverId = dto.id,
                title = dto.title.trim(),
                iconKey = dto.icon.ifBlank { "book" },
                currentStreak = dto.streakDays,
                isCompletedToday = dto.isCompleted,
                lastCompletedDate = when {
                    dto.isCompleted -> today
                    dto.streakDays > 0 -> yesterday
                    else -> null
                },
                previousCompletedDate = if (dto.isCompleted && dto.streakDays > 1) yesterday else null,
                pendingSync = false
            )
        }

        val remoteTitlesLower = mappedRemote
            .map { it.title.trim().lowercase(Locale.US) }
            .toSet()

        val normalizedLocal = localHabits.map { normalizeHabitForDate(it, today) }
        val remainingLocal = normalizedLocal.filter { local ->
            if (local.serverId != null) return@filter false
            if (filteredRemote.isNotEmpty()) {
                val isUntouchedStarter = local.id.startsWith(STARTER_PREFIX) &&
                    local.currentStreak == 0 && !local.isCompletedToday
                val duplicatesRemote = local.title.trim().lowercase(Locale.US) in remoteTitlesLower
                !isUntouchedStarter && !duplicatesRemote
            } else {
                true
            }
        }

        return mappedRemote + remainingLocal
    }

    fun todayDateString(): String {
        return formatLocalDateFromMillis(System.currentTimeMillis())
    }

    fun isUninstallProtectionEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PGuardAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(PGuardAccessibilityService.KEY_UNINSTALL, false)
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (PGuardAccessibilityService.isRunning.value) return true
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
            val isLegacyMock = legacyBest == 14 || legacyUrges == 23 ||
                (legacyStart > 0 && (System.currentTimeMillis() - legacyStart) in (5L * 86400000L)..(9L * 86400000L))

            if (!prefs.contains(KEY_STREAK_START_TIME) || isLegacyMock || version == 0) {
                val now = System.currentTimeMillis()
                editor.putLong(KEY_STREAK_START_TIME, now)
                editor.putString(KEY_FIRST_TRACKED_DATE, formatLocalDateFromMillis(now))
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
        return formatLocalDateFromMillis(startTime)
    }

    fun getFirstTrackedDateString(context: Context): String {
        initDefaultsIfNeeded(context)
        val prefs = getPrefs(context)
        val storedFirst = prefs.getString(KEY_FIRST_TRACKED_DATE, null)
        val streakStartDate = getStreakStartDateString(context)
        val relapseDates = prefs.getStringSet(KEY_RELAPSE_DATES, emptySet()) ?: emptySet()
        val resolved = resolveFirstTrackedDate(storedFirst, streakStartDate, relapseDates)
        if (resolved != storedFirst) {
            prefs.edit().putString(KEY_FIRST_TRACKED_DATE, resolved).apply()
        }
        return resolved
    }

    fun getStreakStatus(context: Context): StreakStatus {
        initDefaultsIfNeeded(context)
        val isServiceOn = isAccessibilityServiceEnabled(context)
        val isUninstallOn = isUninstallProtectionEnabled(context)
        val isDoingJob = isServiceOn && isUninstallOn

        val rawStreakDays = getStreakDays(context)
        if (isDoingJob) {
            val prefs = getPrefs(context)
            val savedBest = prefs.getInt(KEY_BEST_STREAK, 0)
            if (rawStreakDays > savedBest) {
                prefs.edit().putInt(KEY_BEST_STREAK, rawStreakDays).apply()
            }
        }
        val bestDays = getBestStreakDays(context)

        val (reasonType, reason) = when {
            !isServiceOn -> StreakInactiveReason.ACCESSIBILITY_OFF to
                "Accessibility Service is OFF. Enable it to activate your clean streak."
            !isUninstallOn -> StreakInactiveReason.UNINSTALL_PROTECTION_OFF to
                "Uninstall Protection is OFF. Enable it in Blocking → Advanced Blocking to activate your streak."
            else -> null to null
        }

        return StreakStatus(
            streakDays = if (isDoingJob) rawStreakDays else 0,
            bestStreakDays = bestDays,
            isStreakActive = isDoingJob,
            inactiveReason = reason,
            inactiveReasonType = reasonType
        )
    }

    fun getStreakDays(context: Context): Int {
        initDefaultsIfNeeded(context)
        val prefs = getPrefs(context)
        val startTime = prefs.getLong(KEY_STREAK_START_TIME, System.currentTimeMillis())
        val diffMs = System.currentTimeMillis() - startTime
        return if (diffMs <= 0) 0 else (diffMs / DAY_MS).toInt()
    }

    fun getBestStreakDays(context: Context): Int {
        initDefaultsIfNeeded(context)
        val savedBest = getPrefs(context).getInt(KEY_BEST_STREAK, 0)
        if (!isAppDoingItsJob(context)) {
            return savedBest
        }
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

    fun getRelapseEntries(context: Context): List<LocalRelapseEntry> {
        initDefaultsIfNeeded(context)
        val raw = getPrefs(context).getString(KEY_RELAPSE_LOGS_JSON, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<LocalRelapseEntry>>(raw)
                .sortedWith(compareByDescending<LocalRelapseEntry> { it.dateString }.thenByDescending { it.timestampMs })
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getRelapseEntriesForMonth(context: Context, year: Int, month: Int): List<LocalRelapseEntry> {
        val monthPrefix = String.format(Locale.US, "%04d-%02d-", year, month + 1)
        return getRelapseEntries(context).filter { it.dateString.startsWith(monthPrefix) }
    }

    fun getRelapseEntriesForDate(context: Context, dateString: String): List<LocalRelapseEntry> {
        return getRelapseEntries(context).filter { it.dateString == dateString }
    }

    private fun saveRelapseEntries(context: Context, entries: List<LocalRelapseEntry>) {
        try {
            val encoded = json.encodeToString(entries)
            getPrefs(context).edit().putString(KEY_RELAPSE_LOGS_JSON, encoded).apply()
        } catch (_: Exception) {
        }
    }

    fun resetStreak(context: Context, trigger: String = "Stress", note: String = "") {
        initDefaultsIfNeeded(context)
        val firstTracked = getFirstTrackedDateString(context)
        val prefs = getPrefs(context)
        val currentStreak = if (isAppDoingItsJob(context)) getStreakDays(context) else 0
        val newBest = maxOf(getBestStreakDays(context), currentStreak)

        val now = System.currentTimeMillis()
        val today = formatLocalDateFromMillis(now)
        val relapseDates = prefs.getStringSet(KEY_RELAPSE_DATES, emptySet())?.toMutableSet() ?: mutableSetOf()
        relapseDates.add(today)

        val entry = LocalRelapseEntry(
            dateString = today,
            trigger = trigger.trim().ifBlank { "Other" }.take(100),
            notes = note.trim().take(MAX_RELAPSE_NOTE_LENGTH),
            streakDaysAchieved = currentStreak,
            timestampMs = now
        )
        val updatedEntries = mergeRelapseEntries(getRelapseEntries(context), listOf(entry))

        prefs.edit()
            .putString(KEY_FIRST_TRACKED_DATE, firstTracked)
            .putLong(KEY_STREAK_START_TIME, now)
            .putInt(KEY_BEST_STREAK, newBest)
            .putStringSet(KEY_RELAPSE_DATES, relapseDates)
            .remove(KEY_CELEBRATED_MILESTONES)
            .apply()
        saveRelapseEntries(context, updatedEntries)
    }

    fun getWeeklyConsistency(context: Context): List<DayConsistencyStatus> {
        initDefaultsIfNeeded(context)
        val prefs = getPrefs(context)
        val relapseDates = prefs.getStringSet(KEY_RELAPSE_DATES, emptySet()) ?: emptySet()
        val isAppDoingJob = isAppDoingItsJob(context)
        val firstTrackedDate = getFirstTrackedDateString(context)

        val calendar = Calendar.getInstance()
        val currentDayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val currentYear = calendar.get(Calendar.YEAR)

        calendar.firstDayOfWeek = Calendar.MONDAY
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

        val dayNames = listOf("M", "T", "W", "T", "F", "S", "S")
        val result = mutableListOf<DayConsistencyStatus>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        for (i in 0 until 7) {
            val dateStr = dateFormat.format(calendar.time)
            val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
            val year = calendar.get(Calendar.YEAR)
            val dayNumber = calendar.get(Calendar.DAY_OF_MONTH)

            val isToday = (dayOfYear == currentDayOfYear && year == currentYear)
            val isPast = (year < currentYear) || (year == currentYear && dayOfYear < currentDayOfYear)
            val isPastOrToday = isPast || isToday
            val hadRelapse = relapseDates.contains(dateStr)

            val isTracked = dateStr >= firstTrackedDate
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
        val firstTrackedDate = getFirstTrackedDateString(context)

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

        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val firstDayOffset = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - 2

        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

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
            val isWithinTracking = dateStr >= firstTrackedDate

            val isClean = isWithinTracking && (isPast || isToday) && !hadRelapse && (!isToday || isAppDoingJob)

            if (isClean && (isPast || isToday)) cleanCount++
            if (isWithinTracking && hadRelapse) relapseCount++

            days.add(
                MonthCalendarDay(
                    dayNumber = d,
                    dateString = dateStr,
                    isPast = isPast,
                    isToday = isToday,
                    isFuture = isFuture,
                    isWithinStreak = isWithinTracking,
                    isClean = isClean,
                    isRelapse = isWithinTracking && hadRelapse
                )
            )
        }

        val totalTrackedDays = days.count { it.isWithinStreak && (it.isPast || it.isToday) }
        val consistencyPercent = if (totalTrackedDays > 0) {
            ((cleanCount.toFloat() / totalTrackedDays) * 100).toInt()
        } else {
            0
        }

        return MonthCalendarData(
            year = year,
            month = month,
            monthName = monthName,
            firstDayOfWeekOffset = firstDayOffset,
            days = days,
            cleanCount = cleanCount,
            relapseCount = relapseCount,
            consistencyPercent = consistencyPercent,
            trackedDaysCount = totalTrackedDays
        )
    }

    // --- BACKEND CALENDAR, STREAK & HABITS SYNC ---

    internal fun parseIsoTimestampMillis(raw: String?): Long? {
        val trimmed = raw?.trim() ?: return null
        val match = ISO_TIMESTAMP_REGEX.matchEntire(trimmed) ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val day = match.groupValues[3].toIntOrNull() ?: return null
        val hour = match.groupValues[4].toIntOrNull() ?: return null
        val minute = match.groupValues[5].toIntOrNull() ?: return null
        val second = match.groupValues[6].toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59 || second !in 0..59) {
            return null
        }
        val fraction = match.groupValues[7]
        val millis = if (fraction.isEmpty()) 0 else fraction.padEnd(3, '0').take(3).toIntOrNull() ?: return null

        val tzGroup = match.groupValues[8]
        val offsetMs = if (tzGroup == "Z") {
            0L
        } else {
            val sign = if (match.groupValues[9] == "-") -1L else 1L
            val tzHour = match.groupValues[10].toIntOrNull() ?: return null
            val tzMin = match.groupValues[11].toIntOrNull() ?: return null
            if (tzHour !in 0..23 || tzMin !in 0..59) return null
            sign * ((tzHour * 60L + tzMin) * 60_000L)
        }

        return try {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                isLenient = false
                set(year, month - 1, day, hour, minute, second)
                set(Calendar.MILLISECOND, millis)
            }
            cal.timeInMillis - offsetMs
        } catch (_: Exception) {
            null
        }
    }

    internal fun resolveSyncedStreakStartMs(
        localStartMs: Long,
        serverStartMs: Long?,
        serverCurrentDays: Int,
        latestServerRelapseMs: Long?,
        isDifferentAccount: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): Long {
        if (isDifferentAccount) {
            return serverStartMs ?: if (serverCurrentDays > 0) nowMs - serverCurrentDays.toLong() * DAY_MS else nowMs
        }
        if (serverStartMs != null) {
            return when {
                localStartMs <= 0L -> serverStartMs
                latestServerRelapseMs != null && latestServerRelapseMs > localStartMs && serverStartMs >= latestServerRelapseMs -> serverStartMs
                serverStartMs < localStartMs -> serverStartMs
                else -> localStartMs
            }
        }
        if (serverCurrentDays > 0) {
            val inferred = nowMs - serverCurrentDays.toLong() * DAY_MS
            if (localStartMs <= 0L || inferred < localStartMs) {
                return inferred
            }
        }
        return if (localStartMs > 0L) localStartMs else nowMs
    }

    private fun clearPerUserLocalData(context: Context) {
        val now = System.currentTimeMillis()
        synchronized(habitsLock) {
            getPrefs(context).edit()
                .putLong(KEY_STREAK_START_TIME, now)
                .putString(KEY_FIRST_TRACKED_DATE, formatLocalDateFromMillis(now))
                .putInt(KEY_BEST_STREAK, 0)
                .putInt(KEY_URGES_PREVENTED, 0)
                .remove(KEY_RELAPSE_DATES)
                .remove(KEY_RELAPSE_LOGS_JSON)
                .remove(KEY_HABITS_JSON)
                .remove(KEY_PENDING_DELETED_HABIT_IDS)
                .remove(KEY_CELEBRATED_MILESTONES)
                .remove(KEY_LAST_STOIC_SHOWN_DATE)
                .apply()
        }
    }

    fun clearAllUserDataOnLogout(context: Context) {
        clearPerUserLocalData(context)
        getPrefs(context).edit().remove(KEY_SYNCED_USER_ID).apply()
    }

    private fun getPendingDeletedHabitIds(context: Context): Set<Long> {
        synchronized(habitsLock) {
            val raw = getPrefs(context).getStringSet(KEY_PENDING_DELETED_HABIT_IDS, emptySet()) ?: emptySet()
            return raw.mapNotNull { it.toLongOrNull() }.toSet()
        }
    }

    private fun addPendingDeletedHabitId(context: Context, serverId: Long) {
        synchronized(habitsLock) {
            val prefs = getPrefs(context)
            val current = prefs.getStringSet(KEY_PENDING_DELETED_HABIT_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
            current.add(serverId.toString())
            prefs.edit().putStringSet(KEY_PENDING_DELETED_HABIT_IDS, current).apply()
        }
    }

    private fun removePendingDeletedHabitId(context: Context, serverId: Long) {
        synchronized(habitsLock) {
            val prefs = getPrefs(context)
            val current = prefs.getStringSet(KEY_PENDING_DELETED_HABIT_IDS, emptySet())?.toMutableSet() ?: return
            if (current.remove(serverId.toString())) {
                prefs.edit().putStringSet(KEY_PENDING_DELETED_HABIT_IDS, current).apply()
            }
        }
    }

    private fun bindHabitServerId(context: Context, localId: String, serverId: Long): Boolean {
        synchronized(habitsLock) {
            val current = getHabitsLocked(context).toMutableList()
            val idx = current.indexOfFirst { it.id == localId || it.serverId == serverId }
            if (idx == -1) return false
            current[idx] = current[idx].copy(
                id = serverId.toString(),
                serverId = serverId,
                pendingSync = false
            )
            saveHabitsLocked(context, current)
            return true
        }
    }

    private fun markHabitPendingSync(context: Context, habitId: String) {
        synchronized(habitsLock) {
            val current = getHabitsLocked(context).toMutableList()
            val idx = current.indexOfFirst { it.id == habitId }
            if (idx != -1) {
                current[idx] = current[idx].copy(pendingSync = true)
                saveHabitsLocked(context, current)
            }
        }
    }

    suspend fun syncCreateHabit(context: Context, localHabit: PersistentHabit) {
        val result = runSuspendCatching {
            NetworkClient.getHabitsApiService(context).createHabit(
                CreateHabitRequest(title = localHabit.title, icon = localHabit.iconKey)
            )
        }
        val remoteId = result.getOrNull()?.habit?.id ?: return
        if (remoteId > 0L) {
            val bound = bindHabitServerId(context, localHabit.id, remoteId)
            if (!bound) {
                addPendingDeletedHabitId(context, remoteId)
                syncDeleteHabit(context, remoteId)
            }
        }
    }

    suspend fun syncToggleHabit(context: Context, habitId: String) {
        val habit = getHabits(context).firstOrNull {
            it.id == habitId || it.serverId?.toString() == habitId
        } ?: return

        val today = todayDateString()
        val api = NetworkClient.getHabitsApiService(context)
        val existingServerId = habit.serverId

        if (existingServerId == null) {
            if (!habit.isCompletedToday && habit.currentStreak == 0 && !habit.pendingSync) {
                return
            }
            val createResult = runSuspendCatching {
                api.createHabit(CreateHabitRequest(title = habit.title, icon = habit.iconKey))
            }
            val createdId = createResult.getOrNull()?.habit?.id
            if (createdId != null && createdId > 0L) {
                val bound = bindHabitServerId(context, habit.id, createdId)
                if (!bound) {
                    addPendingDeletedHabitId(context, createdId)
                    syncDeleteHabit(context, createdId)
                    return
                }
                val refreshed = getHabits(context).firstOrNull { it.serverId == createdId }
                if (refreshed?.isCompletedToday == true) {
                    runSuspendCatching {
                        api.toggleHabit(createdId, date = today)
                    }
                }
            } else {
                markHabitPendingSync(context, habit.id)
            }
        } else {
            runSuspendCatching {
                api.toggleHabit(existingServerId, date = today)
            }
        }
    }

    suspend fun syncDeleteHabit(context: Context, serverId: Long) {
        val result = runSuspendCatching {
            NetworkClient.getHabitsApiService(context).deleteHabit(serverId)
        }
        val err = result.exceptionOrNull()
        if (result.isSuccess || (err is HttpException && err.code() == 404)) {
            removePendingDeletedHabitId(context, serverId)
        }
    }

    suspend fun syncCalendarWithBackend(context: Context, userId: Long? = null) {
        runSuspendCatching {
            initDefaultsIfNeeded(context)
            val prefs = getPrefs(context)
            val savedUserId = prefs.getLong(KEY_SYNCED_USER_ID, -1L)
            val isDifferentAccount = userId != null && savedUserId != -1L && savedUserId != userId

            if (isDifferentAccount) {
                clearPerUserLocalData(context)
            }

            val api = NetworkClient.getHabitsApiService(context)
            val streakRes = runSuspendCatching { api.getStreak() }.getOrNull()
            val historyRes = runSuspendCatching { api.getRelapseHistory(limit = 100) }.getOrNull()

            val relapseDates = if (isDifferentAccount) {
                mutableSetOf()
            } else {
                prefs.getStringSet(KEY_RELAPSE_DATES, emptySet())?.toMutableSet() ?: mutableSetOf()
            }

            var latestServerRelapseMs: Long? = null
            val remoteEntries = mutableListOf<LocalRelapseEntry>()
            historyRes?.relapses?.forEach { log ->
                val rawTs = log.relapsedAt ?: log.createdAt
                val parsedTs = parseIsoTimestampMillis(rawTs)
                if (parsedTs != null && (latestServerRelapseMs == null || parsedTs > latestServerRelapseMs!!)) {
                    latestServerRelapseMs = parsedTs
                }
                val dateStr = when {
                    parsedTs != null -> formatLocalDateFromMillis(parsedTs)
                    rawTs != null && rawTs.length >= 10 -> rawTs.take(10)
                    else -> null
                }
                if (dateStr != null && DATE_STRING_REGEX.matches(dateStr)) {
                    relapseDates.add(dateStr)
                    remoteEntries.add(
                        LocalRelapseEntry(
                            dateString = dateStr,
                            trigger = log.trigger.trim().ifBlank { "Other" },
                            notes = log.notes.trim(),
                            streakDaysAchieved = log.streakDaysAchieved,
                            timestampMs = parsedTs ?: 0L
                        )
                    )
                }
            }

            val mergedEntries = mergeRelapseEntries(
                local = if (isDifferentAccount) emptyList() else getRelapseEntries(context),
                remote = remoteEntries
            )
            saveRelapseEntries(context, mergedEntries)

            val editor = prefs.edit()
            editor.putStringSet(KEY_RELAPSE_DATES, relapseDates)

            var effectiveStartMs = prefs.getLong(KEY_STREAK_START_TIME, System.currentTimeMillis())
            streakRes?.streak?.let { streak ->
                val resolvedStartMs = resolveSyncedStreakStartMs(
                    localStartMs = if (isDifferentAccount) 0L else prefs.getLong(KEY_STREAK_START_TIME, 0L),
                    serverStartMs = parseIsoTimestampMillis(streak.currentStreakStart),
                    serverCurrentDays = streak.currentDays,
                    latestServerRelapseMs = latestServerRelapseMs,
                    isDifferentAccount = isDifferentAccount
                )
                effectiveStartMs = resolvedStartMs
                val resolvedBest = if (isDifferentAccount) {
                    maxOf(streak.longestStreakDays, streak.currentDays)
                } else {
                    maxOf(getBestStreakDays(context), streak.longestStreakDays)
                }
                editor.putLong(KEY_STREAK_START_TIME, resolvedStartMs)
                editor.putInt(KEY_BEST_STREAK, resolvedBest)
            }

            val existingFirstTracked = if (isDifferentAccount) null else prefs.getString(KEY_FIRST_TRACKED_DATE, null)
            val resolvedFirstTracked = resolveFirstTrackedDate(
                storedFirstDate = existingFirstTracked,
                streakStartDate = formatLocalDateFromMillis(effectiveStartMs),
                relapseDates = relapseDates
            )
            editor.putString(KEY_FIRST_TRACKED_DATE, resolvedFirstTracked)

            if (userId != null) {
                editor.putLong(KEY_SYNCED_USER_ID, userId)
            }
            editor.apply()

            // Flush any pending habit deletes
            for (pendingDeleteId in getPendingDeletedHabitIds(context)) {
                syncDeleteHabit(context, pendingDeleteId)
            }

            // Push unsynced local habits (newly added offline or legacy un-synced user habits)
            val today = todayDateString()
            val unsyncedLocals = getHabits(context).filter { it.serverId == null && it.pendingSync }
            for (localHabit in unsyncedLocals) {
                val created = runSuspendCatching {
                    api.createHabit(CreateHabitRequest(title = localHabit.title, icon = localHabit.iconKey))
                }.getOrNull()?.habit
                if (created != null && created.id > 0L) {
                    val bound = bindHabitServerId(context, localHabit.id, created.id)
                    if (!bound) {
                        addPendingDeletedHabitId(context, created.id)
                        syncDeleteHabit(context, created.id)
                    } else if (localHabit.isCompletedToday) {
                        runSuspendCatching { api.toggleHabit(created.id, date = today) }
                    }
                }
            }

            // Pull authoritative remote habits and reconcile against current local state
            val remoteHabits = runSuspendCatching { api.getHabits(date = today).habits }.getOrNull()
            if (remoteHabits != null) {
                synchronized(habitsLock) {
                    val latestLocal = getHabitsLocked(context)
                    val pendingDeletes = getPendingDeletedHabitIds(context)
                    val reconciled = reconcileRemoteAndLocalHabits(
                        remoteHabits = remoteHabits,
                        localHabits = latestLocal,
                        pendingDeletedIds = pendingDeletes,
                        today = today
                    )
                    saveHabitsLocked(context, reconciled)
                }
            }
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

    private fun getHabitsLocked(context: Context): List<PersistentHabit> {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_HABITS_JSON, null)
        val today = todayDateString()

        if (jsonStr.isNullOrBlank()) {
            val starterHabits = listOf(
                PersistentHabit(id = "${STARTER_PREFIX}1", serverId = null, title = "Drink 2.5L Water", iconKey = "water", currentStreak = 0, isCompletedToday = false),
                PersistentHabit(id = "${STARTER_PREFIX}2", serverId = null, title = "Workout / Exercise", iconKey = "fitness", currentStreak = 0, isCompletedToday = false),
                PersistentHabit(id = "${STARTER_PREFIX}3", serverId = null, title = "Read 15 Mins", iconKey = "book", currentStreak = 0, isCompletedToday = false)
            )
            saveHabitsLocked(context, starterHabits)
            return starterHabits
        }

        return try {
            val list = json.decodeFromString<List<PersistentHabit>>(jsonStr)
            val updated = list.map { habit ->
                val migrated = when {
                    habit.serverId == null &&
                        habit.id in LEGACY_STARTER_TITLES &&
                        LEGACY_STARTER_TITLES[habit.id] == habit.title -> {
                        habit.copy(
                            id = "$STARTER_PREFIX${habit.id}",
                            pendingSync = habit.currentStreak > 0 || habit.isCompletedToday
                        )
                    }
                    habit.serverId == null &&
                        habit.id.length >= 12 &&
                        habit.id.all { it.isDigit() } -> {
                        habit.copy(pendingSync = true)
                    }
                    else -> habit
                }
                normalizeHabitForDate(migrated, today)
            }
            if (updated != list) {
                saveHabitsLocked(context, updated)
            }
            updated
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveHabitsLocked(context: Context, habits: List<PersistentHabit>) {
        try {
            val jsonStr = json.encodeToString(habits)
            getPrefs(context).edit().putString(KEY_HABITS_JSON, jsonStr).apply()
        } catch (_: Exception) {
        }
    }

    fun getHabits(context: Context): List<PersistentHabit> {
        synchronized(habitsLock) {
            return getHabitsLocked(context)
        }
    }

    fun saveHabits(context: Context, habits: List<PersistentHabit>) {
        synchronized(habitsLock) {
            saveHabitsLocked(context, habits)
        }
    }

    fun addHabit(context: Context, title: String, iconKey: String): PersistentHabit {
        val cleanTitle = title.trim().take(MAX_HABIT_TITLE_LENGTH)
        synchronized(habitsLock) {
            val current = getHabitsLocked(context).toMutableList()
            val newHabit = PersistentHabit(
                id = "$LOCAL_PREFIX${System.currentTimeMillis()}",
                serverId = null,
                title = cleanTitle,
                iconKey = iconKey,
                currentStreak = 0,
                isCompletedToday = false,
                pendingSync = true
            )
            current.add(newHabit)
            saveHabitsLocked(context, current)
            return newHabit
        }
    }

    fun deleteHabitAndGetServerId(context: Context, habitId: String): Pair<List<PersistentHabit>, Long?> {
        synchronized(habitsLock) {
            val current = getHabitsLocked(context)
            val target = current.firstOrNull { it.id == habitId }
            val deletedServerId = target?.serverId
            val remaining = current.filter { it.id != habitId }
            saveHabitsLocked(context, remaining)
            if (deletedServerId != null) {
                addPendingDeletedHabitId(context, deletedServerId)
            }
            return remaining to deletedServerId
        }
    }

    fun deleteHabit(context: Context, habitId: String): List<PersistentHabit> {
        return deleteHabitAndGetServerId(context, habitId).first
    }

    fun toggleHabit(context: Context, habitId: String): List<PersistentHabit> {
        val today = todayDateString()
        synchronized(habitsLock) {
            val current = getHabitsLocked(context).toMutableList()
            val index = current.indexOfFirst { it.id == habitId }
            if (index != -1) {
                current[index] = applyHabitToggle(current[index], today)
                saveHabitsLocked(context, current)
            }
            return current
        }
    }
}
