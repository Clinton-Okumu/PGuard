package com.example.pguard.presentation.focus

import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.Locale
import java.util.UUID

@Serializable
data class FocusSchedule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val startHour: Int = 0,
    val startMinute: Int = 0,
    val endHour: Int = 5,
    val endMinute: Int = 5,
    val isEnabled: Boolean = true,
    val repeatDaily: Boolean = true,
    val repeatDays: Set<Int> = setOf(
        Calendar.SUNDAY,
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
        Calendar.SATURDAY
    ),
    val allowedApps: Set<String> = emptySet()
) {
    val displayName: String
        get() = name.trim().ifBlank { "Focus Session" }

    val durationMinutes: Int
        get() {
            val startTotal = startHour * 60 + startMinute
            val endTotal = endHour * 60 + endMinute
            return if (endTotal >= startTotal) {
                endTotal - startTotal
            } else {
                (24 * 60 - startTotal) + endTotal
            }
        }

    val isValidDuration: Boolean
        get() = durationMinutes > 0

    val formattedDuration: String
        get() {
            val totalMins = durationMinutes
            val hours = totalMins / 60
            val mins = totalMins % 60
            return when {
                hours > 0 && mins > 0 -> "$hours hr $mins min"
                hours > 0 -> "$hours hr"
                else -> "$mins min"
            }
        }

    val formattedWindowOnly: String
        get() = String.format(Locale.US, "%02d:%02d - %02d:%02d", startHour, startMinute, endHour, endMinute)

    val formattedTimeRange: String
        get() = "$formattedWindowOnly ($formattedDuration)"

    val formattedDaysBadge: String
        get() {
            if (repeatDaily || repeatDays.size >= 7) return "Daily"
            val weekdays = setOf(
                Calendar.MONDAY,
                Calendar.TUESDAY,
                Calendar.WEDNESDAY,
                Calendar.THURSDAY,
                Calendar.FRIDAY
            )
            val weekends = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
            if (repeatDays == weekdays) return "Weekdays"
            if (repeatDays == weekends) return "Weekends"

            val dayShortNames = mapOf(
                Calendar.MONDAY to "Mon",
                Calendar.TUESDAY to "Tue",
                Calendar.WEDNESDAY to "Wed",
                Calendar.THURSDAY to "Thu",
                Calendar.FRIDAY to "Fri",
                Calendar.SATURDAY to "Sat",
                Calendar.SUNDAY to "Sun"
            )
            val ordered = listOf(
                Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
            ).filter { it in repeatDays }

            return if (ordered.size <= 3) {
                ordered.joinToString(", ") { dayShortNames[it] ?: "" }
            } else {
                "${ordered.size} Days/Wk"
            }
        }

    fun isCurrentlyActive(cal: Calendar = Calendar.getInstance()): Boolean {
        if (!isEnabled || !isValidDuration) return false

        val currentMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMins = startHour * 60 + startMinute
        val endMins = endHour * 60 + endMinute

        if (repeatDaily || repeatDays.size >= 7) {
            return if (startMins <= endMins) {
                currentMins in startMins until endMins
            } else {
                currentMins >= startMins || currentMins < endMins
            }
        }

        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        return if (startMins <= endMins) {
            currentDay in repeatDays && currentMins in startMins until endMins
        } else {
            if (currentMins >= startMins && currentDay in repeatDays) {
                true
            } else if (currentMins < endMins) {
                val prevDay = if (currentDay == Calendar.SUNDAY) Calendar.SATURDAY else currentDay - 1
                prevDay in repeatDays
            } else {
                false
            }
        }
    }

    companion object {
        fun activeSchedules(
            schedules: List<FocusSchedule>,
            cal: Calendar = Calendar.getInstance()
        ): List<FocusSchedule> = schedules.filter { it.isCurrentlyActive(cal) }

        /**
         * Enforces the strictest intersection when multiple focus schedules overlap:
         * an app is allowed only if it is allowed by every currently active schedule.
         */
        fun isAllowedAcrossSchedules(
            packageName: String,
            activeSchedules: List<FocusSchedule>,
            isEssentialSystemPackage: Boolean
        ): Boolean {
            if (activeSchedules.isEmpty() || isEssentialSystemPackage) return true
            return activeSchedules.all { schedule ->
                schedule.allowedApps.any { it.equals(packageName, ignoreCase = true) }
            }
        }
    }
}
