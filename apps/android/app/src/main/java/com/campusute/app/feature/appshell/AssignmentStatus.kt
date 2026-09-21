package com.campusute.app.feature.appshell

import com.campusute.app.core.designsystem.components.CampusTone
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One reading of a deadline, shared by the home preview and the assignment sheet so the same
 * row cannot be "Sắp hết hạn" on one tab and "Chưa nộp" on the other.
 */
enum class DueStatus { Submitted, Overdue, ClosingSoon, Open, Unknown }

data class AssignmentBadge(val text: String, val tone: CampusTone)

fun dueStatus(submitted: Boolean, dueAt: String?, now: Instant = Instant.now()): DueStatus = when {
    submitted -> DueStatus.Submitted
    dueAt == null -> DueStatus.Unknown
    else -> {
        val due = parseInstant(dueAt) ?: return DueStatus.Unknown
        val remaining = Duration.between(now, due)
        when {
            remaining.isNegative || remaining.isZero -> DueStatus.Overdue
            remaining <= Duration.ofDays(2) -> DueStatus.ClosingSoon
            else -> DueStatus.Open
        }
    }
}

fun badgeFor(status: DueStatus): AssignmentBadge = when (status) {
    DueStatus.Submitted -> AssignmentBadge("Đã nộp", CampusTone.Success)
    DueStatus.Overdue -> AssignmentBadge("Quá hạn", CampusTone.Danger)
    DueStatus.ClosingSoon -> AssignmentBadge("Sắp hết hạn", CampusTone.Warning)
    DueStatus.Open -> AssignmentBadge("Chưa nộp", CampusTone.Info)
    DueStatus.Unknown -> AssignmentBadge("Chưa có hạn nộp", CampusTone.Neutral)
}

private val timestamp = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

/** "2026-09-19T08:00:00Z" -> "19/09/2026 15:00" in the device zone. Null when the server lied. */
fun formatTimestamp(raw: String?, zone: ZoneId = ZoneId.systemDefault()): String? {
    val instant = raw?.let(::parseInstant) ?: return null
    return timestamp.format(instant.atZone(zone))
}

private fun parseInstant(raw: String): Instant? = try {
    Instant.parse(if (raw.endsWith("Z") || raw.contains('+')) raw else raw + "Z")
} catch (_: Exception) {
    null
}
