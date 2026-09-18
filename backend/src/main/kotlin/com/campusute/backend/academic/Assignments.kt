package com.campusute.backend.academic

import com.campusute.backend.auth.currentUserUuid
import com.campusute.backend.common.ApiEnvelope
import com.campusute.backend.common.ApiException
import com.campusute.backend.common.ErrorCode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

// ---------- entities (V4 tables — previously orphaned, now real module) ----------

@Entity
@Table(name = "assignments")
class Assignment(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "section_id", nullable = false) val sectionId: UUID,
    @Column(nullable = false) var title: String,
    @Column(columnDefinition = "text") var description: String = "",
    @Column(name = "due_at") var dueAt: Instant? = null,
)

@Entity
@Table(
    name = "submissions",
    uniqueConstraints = [UniqueConstraint(columnNames = ["assignment_id", "student_id"])],
)
class Submission(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "assignment_id", nullable = false) val assignmentId: UUID,
    @Column(name = "student_id", nullable = false) val studentId: UUID,
    @Column(name = "submitted_at", nullable = false, insertable = false, updatable = false)
    val submittedAt: Instant = Instant.EPOCH,
    @Column(columnDefinition = "text") var note: String = "",
)

interface AssignmentRepository : JpaRepository<Assignment, UUID> {
    fun findBySectionIdIn(sectionIds: Collection<UUID>): List<Assignment>
}

interface SubmissionRepository : JpaRepository<Submission, UUID> {
    fun findByAssignmentIdAndStudentId(assignmentId: UUID, studentId: UUID): Submission?
    fun findByAssignmentId(assignmentId: UUID): List<Submission>
    fun countByAssignmentId(assignmentId: UUID): Long
}

// ---------- API ----------

@Tag(name = "assignments")
@RestController
@RequestMapping("/api/v1/assignments")
class AssignmentController(
    private val assignments: AssignmentRepository,
    private val submissions: SubmissionRepository,
    private val enrollments: EnrollmentRepository,
    private val sections: ClassSectionRepository,
    private val courses: CourseRepository,
    private val notifier: com.campusute.backend.notification.NotificationService,
) {
    data class CreateRequest(val sectionId: UUID, val title: String, val description: String?, val dueAt: String?)
    data class SubmitRequest(val note: String)
    data class AssignmentDto(
        val id: UUID,
        val sectionCode: String,
        val courseCode: String,
        val title: String,
        val description: String,
        val dueAt: String?,
        val submitted: Boolean,
        val submissionCount: Long = -1,
    )

    @Operation(summary = "Student: assignments for enrolled sections, with own submission state")
    @GetMapping("/me")
    fun mine(): ApiEnvelope<List<AssignmentDto>> {
        val studentId = currentUserUuid() ?: throw ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Phiên không hợp lệ.")
        val sectionIds = enrollments.sectionIdsOfStudent(studentId)
        val rows = assignments.findBySectionIdIn(sectionIds)
        val sectionById = sections.findAllById(sectionIds).associateBy { it.id }
        val courseById = courses.findAllById(sectionById.values.map { it.courseId }).associateBy { it.id }
        return ApiEnvelope.ok(rows.sortedWith(compareBy { it.dueAt ?: Instant.MAX }).map { a ->
            val section = sectionById[a.sectionId]!!
            AssignmentDto(
                id = a.id,
                sectionCode = section.sectionCode,
                courseCode = courseById[section.courseId]?.code ?: "-",
                title = a.title,
                description = a.description,
                dueAt = a.dueAt?.toString(),
                submitted = submissions.findByAssignmentIdAndStudentId(a.id, studentId) != null,
            )
        })
    }

    @Operation(summary = "Student: submit (one per assignment; resubmit updates the note before deadline)")
    @PostMapping("/{id}/submit")
    fun submit(
        @PathVariable id: UUID,
        @RequestBody body: SubmitRequest,
    ): ApiEnvelope<Map<String, String>> {
        val studentId = currentUserUuid() ?: throw ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Phiên không hợp lệ.")
        val assignment = assignments.findById(id).orElseThrow {
            ApiException(ErrorCode.NOT_FOUND, "Không tìm thấy bài tập.")
        }
        if (!enrollments.sectionIdsOfStudent(studentId).contains(assignment.sectionId)) {
            throw ApiException(ErrorCode.AUTH_FORBIDDEN, "Bạn không thuộc lớp học phần này.")
        }
        if (assignment.dueAt != null && Instant.now().isAfter(assignment.dueAt)) {
            throw ApiException(ErrorCode.VALIDATION_FAILED, "Đã quá hạn nộp bài.")
        }
        val existing = submissions.findByAssignmentIdAndStudentId(id, studentId)
        if (existing != null) {
            existing.note = body.note.take(5000)
            submissions.save(existing)
        } else {
            submissions.save(
                Submission(assignmentId = id, studentId = studentId, note = body.note.take(5000)),
            )
        }
        return ApiEnvelope.ok(mapOf("status" to "SUBMITTED"))
    }

    @Operation(summary = "LECTURER+: create an assignment for a section")
    @PostMapping
    @PreAuthorize("hasAnyRole('LECTURER','DEPARTMENT_ADMIN','ACADEMIC_STAFF','ADMIN','SUPER_ADMIN')")
    fun create(@RequestBody body: CreateRequest): ApiEnvelope<Map<String, String>> {
        if (body.title.isBlank()) throw ApiException(ErrorCode.VALIDATION_FAILED, "Tiêu đề không được trống.")
        val saved = assignments.save(
            Assignment(
                sectionId = body.sectionId,
                title = body.title.take(255),
                description = body.description ?: "",
                dueAt = body.dueAt?.let { Instant.parse(it) },
            ),
        )
        // Notify every student enrolled in the section (sync-insert, Kongming counsel).
        enrollments.findByClassSectionId(body.sectionId).forEach { e ->
            notifier.notify(e.studentId, "ASSIGNMENT", "Bài tập mới: ${body.title}", "Hạn nộp: ${body.dueAt ?: "không có"}")
        }
        return ApiEnvelope.ok(mapOf("assignmentId" to saved.id.toString(), "status" to "CREATED"))
    }

    @Operation(summary = "LECTURER+: assignments of a section with submission counts")
    @GetMapping("/section/{sectionId}")
    @PreAuthorize("hasAnyRole('LECTURER','DEPARTMENT_ADMIN','ACADEMIC_STAFF','ADMIN','SUPER_ADMIN')")
    fun bySection(@PathVariable sectionId: UUID): ApiEnvelope<List<AssignmentDto>> {
        val rows = assignments.findBySectionIdIn(listOf(sectionId))
        val section = sections.findById(sectionId).orElseThrow {
            ApiException(ErrorCode.NOT_FOUND, "Lớp học phần không tồn tại.")
        }
        val course = courses.findById(section.courseId).orElse(null)
        return ApiEnvelope.ok(rows.map { a ->
            AssignmentDto(
                id = a.id,
                sectionCode = section.sectionCode,
                courseCode = course?.code ?: "-",
                title = a.title,
                description = a.description,
                dueAt = a.dueAt?.toString(),
                submitted = false,
                submissionCount = submissions.countByAssignmentId(a.id),
            )
        })
    }
}
