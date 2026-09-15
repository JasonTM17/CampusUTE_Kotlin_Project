package com.campusute.backend.academic

import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class SessionOccurrence(
    val id: UUID,
    val courseCode: String,
    val courseName: String,
    val lecturerName: String,
    val building: String,
    val room: String,
    val date: LocalDate,
    val startAt: String,
    val endAt: String,
    val conflict: Boolean,
)

/**
 * Expands weekly meetings into concrete occurrences for a date range and
 * flags overlaps per student/day. Ownership is enforced upstream: the query
 * starts from the CALLER's enrollments only, so a student can never see
 * another student's schedule by construction (ABAC by query scoping).
 */
@Service
class ScheduleService(
    private val enrollments: EnrollmentRepository,
    private val sessions: ScheduleSessionRepository,
    private val sections: ClassSectionRepository,
    private val courses: CourseRepository,
    private val lecturers: LecturerRepository,
) {
    fun occurrencesFor(studentId: UUID, from: LocalDate, to: LocalDate): List<SessionOccurrence> {
        val sectionIds = enrollments.sectionIdsOfStudent(studentId)
        if (sectionIds.isEmpty()) return emptyList()

        val sectionList = sections.findAllById(sectionIds)
        val sectionById = sectionList.associateBy { it.id }
        val courseById = courses.findAllById(sectionById.values.map { it.courseId }).associateBy { it.id }
        val lecturerById = lecturers.findAllById(sectionById.values.map { it.lecturerId }).associateBy { it.id }

        val meetings = sessions.findByClassSectionIdInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            sectionIds, to, from,
        )

        val occurrences = ArrayList<SessionOccurrence>()
        for (meeting in meetings) {
            val section = sectionById[meeting.classSectionId] ?: continue
            val course = courseById[section.courseId] ?: continue
            val lecturer = lecturerById[section.lecturerId]?.fullName ?: "-"
            var date = maxOf(from, meeting.startDate)
            while (!date.isAfter(minOf(to, meeting.endDate))) {
                if (date.dayOfWeek.value == meeting.dayOfWeek.toInt()) {
                    occurrences += SessionOccurrence(
                        id = deterministicId(meeting.id, date),
                        courseCode = course.code,
                        courseName = course.name,
                        lecturerName = lecturer,
                        building = section.building,
                        room = section.room,
                        date = date,
                        startAt = "${date}T${meeting.startTime}:00+07:00",
                        endAt = "${date}T${meeting.endTime}:00+07:00",
                        conflict = false,
                    )
                }
                date = date.plusDays(1)
            }
        }

        markConflicts(occurrences)
        return occurrences.sortedWith(
            compareBy({ it.date }, { LocalTime.parse(it.startAt.substringAfter('T').take(8)) }),
        )
    }

    /** Two meetings overlap when they share a day and their time ranges intersect. */
    private fun markConflicts(occurrences: MutableList<SessionOccurrence>) {
        val byDay = occurrences.groupBy { it.date }
        val conflicted = mutableSetOf<UUID>()
        for ((_, daySessions) in byDay) {
            val sorted = daySessions.sortedBy { it.startAt }
            for (i in 0 until sorted.size - 1) {
                val a = sorted[i]
                val b = sorted[i + 1]
                if (overlaps(a, b)) {
                    conflicted += a.id
                    conflicted += b.id
                }
            }
        }
        occurrences.replaceAll { it.copy(conflict = it.id in conflicted) }
    }

    private fun overlaps(a: SessionOccurrence, b: SessionOccurrence): Boolean =
        a.startAt < b.endAt && b.startAt < a.endAt

    private fun deterministicId(sessionId: UUID, date: LocalDate): UUID =
        UUID.nameUUIDFromBytes("$sessionId:$date".toByteArray())
}
