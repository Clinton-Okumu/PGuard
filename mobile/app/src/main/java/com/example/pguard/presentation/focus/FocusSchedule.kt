package com.example.pguard.presentation.focus

import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.UUID

@Serializable
data class FocusSchedule(
    val id: String = UUID.randomUUID().toString(),
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

    val formattedTimeRange: String
        get() = String.format(
            "%02d:%02d - %02d:%02d (%s)",
            startHour,
            startMinute,
            endHour,
            endMinute,
            formattedDuration
        )

    fun isCurrentlyActive(cal: Calendar = Calendar.getInstance()): Boolean {
        if (!isEnabled) return false

        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
        if (!repeatDaily && currentDay !in repeatDays) return false

        val currentMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMins = startHour * 60 + startMinute
        val endMins = endHour * 60 + endMinute

        return if (startMins <= endMins) {
            currentMins in startMins until endMins
        } else {
            // Spans overnight (e.g. 23:00 to 05:00)
            currentMins >= startMins || currentMins < endMins
        }
    }
}
