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
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// ---------- grades (read-only for students; GPA math is client-side deterministic) ----------

@Entity
@Table(name = "grades", uniqueConstraints = [UniqueConstraint(columnNames = ["student_id", "course_id", "component"])])
class Grade(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "student_id", nullable = false) val studentId: UUID,
    @Column(name = "course_id", nullable = false) val courseId: UUID,
    @Column(nullable = false) val component: String,
    @Column(nullable = false) val score: java.math.BigDecimal,
    @Column(nullable = false) val weight: java.math.BigDecimal,
)

interface GradeRepository : JpaRepository<Grade, UUID> {
    fun findByStudentId(studentId: UUID): List<Grade>
}

@Tag(name = "grades")
@RestController
@RequestMapping("/api/v1/grades")
class GradeController(
    private val grades: GradeRepository,
    private val courses: CourseRepository,
) {
    data class GradeComponentDto(val component: String, val score: Double, val weight: Double)
    data class CourseGradesDto(val courseId: UUID, val courseCode: String, val courseName: String, val components: List<GradeComponentDto>)
    data class UpsertGradeRequest(
        val studentId: UUID,
        val courseCode: String,
        val component: String,
        val score: Double,
        val weight: Double,
    )

    @Operation(summary = "Caller's grade components per course")
    @GetMapping("/me")
    fun mine(): ApiEnvelope<List<CourseGradesDto>> {
        val studentId = currentUserUuid() ?: throw ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Phiên không hợp lệ.")
        val mine = grades.findByStudentId(studentId)
        val byCourse = mine.groupBy { it.courseId }.map { (courseId, components) ->
            val course = courses.findById(courseId).orElse(null)
            CourseGradesDto(
                courseId = courseId,
                courseCode = course?.code ?: "-",
                courseName = course?.name ?: "-",
                components = components.map { GradeComponentDto(it.component, it.score.toDouble(), it.weight.toDouble()) },
            )
        }
        return ApiEnvelope.ok(byCourse)
    }

    @Operation(summary = "LECTURER/ADMIN: upsert a grade component")
    @PostMapping
    @PreAuthorize("hasAnyRole('LECTURER','DEPARTMENT_ADMIN','ACADEMIC_STAFF','ADMIN','SUPER_ADMIN')")
    fun upsert(@RequestBody body: UpsertGradeRequest): ApiEnvelope<Map<String, String>> {
        val validComponents = setOf("ASSIGNMENT", "MIDTERM", "FINAL", "OTHER")
        if (body.component !in validComponents) {
            throw ApiException(ErrorCode.VALIDATION_FAILED, "Thành phần điểm không hợp lệ.")
        }
        if (body.score < 0 || body.score > 10 || body.weight < 0 || body.weight > 1) {
            throw ApiException(ErrorCode.VALIDATION_FAILED, "Điểm phải trong [0..10], trọng số [0..1].")
        }
        val course = courses.findAll().firstOrNull { it.code == body.courseCode }
            ?: throw ApiException(ErrorCode.NOT_FOUND, "Không tìm thấy môn học.")
        val existing = grades.findByStudentId(body.studentId)
            .firstOrNull { it.courseId == course.id && it.component == body.component }
        val saved = if (existing != null) {
            grades.save(
                Grade(
                    id = existing.id,
                    studentId = existing.studentId,
                    courseId = existing.courseId,
                    component = existing.component,
                    score = java.math.BigDecimal(body.score),
                    weight = java.math.BigDecimal(body.weight),
                ),
            )
        } else {
            grades.save(
                Grade(
                    studentId = body.studentId,
                    courseId = course.id,
                    component = body.component,
                    score = java.math.BigDecimal(body.score),
                    weight = java.math.BigDecimal(body.weight),
                ),
            )
        }
        return ApiEnvelope.ok(mapOf("gradeId" to saved.id.toString(), "status" to "SAVED"))
    }
}

// ---------- events with Idempotency-Key registration ----------

@Entity
@Table(name = "events")
class CampusEvent(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true) val code: String,
    @Column(nullable = false) val title: String,
    val description: String? = null,
    @Column(name = "starts_at", nullable = false) val startsAt: Instant,
    val location: String? = null,
    val capacity: Int = 100,
)

@Entity
@Table(
    name = "event_registrations",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["event_id", "student_id"]),
        UniqueConstraint(columnNames = ["student_id", "idempotency_key"]),
    ],
)
class EventRegistration(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "event_id", nullable = false) val eventId: UUID,
    @Column(name = "student_id", nullable = false) val studentId: UUID,
    @Column(name = "idempotency_key", nullable = false) val idempotencyKey: String,
)

interface EventRepository : JpaRepository<CampusEvent, UUID> {
    fun findByCode(code: String): CampusEvent?
}
interface EventRegistrationRepository : JpaRepository<EventRegistration, UUID> {
    fun findByStudentIdAndIdempotencyKey(studentId: UUID, key: String): EventRegistration?
    fun findByEventIdAndStudentId(eventId: UUID, studentId: UUID): EventRegistration?
    fun countByEventId(eventId: UUID): Long
}

@Tag(name = "events")
@RestController
@RequestMapping("/api/v1/events")
class EventController(
    private val events: EventRepository,
    private val registrations: EventRegistrationRepository,
) {
    data class EventDto(val id: UUID, val code: String, val title: String, val startsAt: String, val location: String?, val registered: Boolean, val seatsLeft: Long)

    @GetMapping
    fun list(): ApiEnvelope<List<EventDto>> {
        val userId = currentUserUuid() ?: throw ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Phiên không hợp lệ.")
        return ApiEnvelope.ok(
            events.findAll().sortedBy { it.startsAt }.map { e ->
                EventDto(
                    id = e.id, code = e.code, title = e.title, startsAt = e.startsAt.toString(),
                    location = e.location,
                    registered = registrations.findByEventIdAndStudentId(e.id, userId) != null,
                    seatsLeft = e.capacity - registrations.countByEventId(e.id),
                )
            },
        )
    }

    @Operation(summary = "Register; Idempotency-Key makes retries return the same registration")
    @PostMapping("/{id}/register")
    fun register(
        @PathVariable id: UUID,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
    ): ApiEnvelope<Map<String, String>> {
        val userId = currentUserUuid() ?: throw ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Phiên không hợp lệ.")
        if (idempotencyKey.isBlank() || idempotencyKey.length > 64) {
            throw ApiException(ErrorCode.VALIDATION_FAILED, "Idempotency-Key không hợp lệ.")
        }
        // Same caller key -> same result, whatever the event.
        registrations.findByStudentIdAndIdempotencyKey(userId, idempotencyKey)
            ?.let { return ApiEnvelope.ok(mapOf("registrationId" to it.id.toString(), "status" to "REGISTERED")) }

        val event = events.findById(id).orElseThrow { ApiException(ErrorCode.NOT_FOUND, "Không tìm thấy sự kiện.") }
        if (registrations.countByEventId(id) >= event.capacity) {
            throw ApiException(ErrorCode.VALIDATION_FAILED, "Sự kiện đã đủ chỗ.")
        }
        val existing = registrations.findByEventIdAndStudentId(id, userId)
        val saved = existing ?: registrations.save(
            EventRegistration(eventId = id, studentId = userId, idempotencyKey = idempotencyKey),
        )
        return ApiEnvelope.ok(mapOf("registrationId" to saved.id.toString(), "status" to "REGISTERED"))
    }
}

// ---------- QR attendance: rotating signed payload + anti-replay ----------

@Entity
@Table(name = "attendance_sessions")
class AttendanceSession(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "section_id", nullable = false) val sectionId: UUID,
    @Column(name = "lecturer_id", nullable = false) val lecturerId: UUID,
    @Column(nullable = false) val secret: String,
    var active: Boolean = true,
)

interface AttendanceSessionRepository : JpaRepository<AttendanceSession, UUID> {
    fun findFirstBySectionIdAndActiveTrueOrderByIdDesc(sectionId: UUID): AttendanceSession?
}

@Entity
@Table(
    name = "attendance_records",
    uniqueConstraints = [UniqueConstraint(columnNames = ["session_id", "student_id"]), UniqueConstraint(columnNames = ["nonce"])],
)
class AttendanceRecord(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "session_id", nullable = false) val sessionId: UUID,
    @Column(name = "student_id", nullable = false) val studentId: UUID,
    val bucket: Long,
    @Column(nullable = false) val nonce: String,
)

interface AttendanceRecordRepository : JpaRepository<AttendanceRecord, UUID> {
    fun existsByNonce(nonce: String): Boolean
    fun existsBySessionIdAndStudentId(sessionId: UUID, studentId: UUID): Boolean
    fun countBySessionId(sessionId: UUID): Long
}

@Service
class AttendanceService(
    private val sessions: AttendanceSessionRepository,
    private val records: AttendanceRecordRepository,
    private val enrollments: EnrollmentRepository,
    private val lecturerRows: LecturerRepository,
    private val sections: ClassSectionRepository,
) {
    private val random = SecureRandom()

    data class OpenedSession(val session: AttendanceSession, val secret: String)

    /** Lecturer device keeps [secret] and rotates the QR locally (same HMAC scheme). */
    fun openSession(sectionId: UUID, lecturerUserId: UUID): OpenedSession {
        sections.findById(sectionId).orElseThrow {
            ApiException(ErrorCode.NOT_FOUND, "Lớp học phần không tồn tại.")
        }
        val lecturerId = (lecturerRows.findByUserId(lecturerUserId) ?: lecturerRows.save(
            Lecturer(userId = lecturerUserId, fullName = "Lecturer $lecturerUserId"),
        )).id
        val secretBytes = ByteArray(32).also(random::nextBytes)
        val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes)
        val session = sessions.save(
            AttendanceSession(sectionId = sectionId, lecturerId = lecturerId, secret = secret),
        )
        return OpenedSession(session, secret)
    }

    /** Only the lecturer who owns the session (or an admin) may close it. */
    fun closeSession(sessionId: UUID, closerUserId: UUID, isAdmin: Boolean) {
        val session = sessions.findById(sessionId).orElseThrow {
            ApiException(ErrorCode.NOT_FOUND, "Phiên điểm danh không tồn tại.")
        }
        if (!isAdmin) {
            val owns = lecturerRows.findByUserId(closerUserId)?.id == session.lecturerId
            if (!owns) throw ApiException(ErrorCode.AUTH_FORBIDDEN, "Chỉ giảng viên chủ nhiệm mới đóng được phiên.")
        }
        session.active = false
        sessions.save(session)
    }

    fun verifyScan(payload: String, nonce: String, studentId: UUID, now: Instant = Instant.now()): ScanVerdict {
        val parts = payload.split(".")
        if (parts.size != 3) return ScanVerdict(false, "Mã QR không đúng định dạng.")
        val sessionId = try {
            UUID.fromString(String(Base64.getUrlDecoder().decode(parts[0])))
        } catch (_: IllegalArgumentException) {
            return ScanVerdict(false, "Mã QR không hợp lệ.")
        }
        val bucket = parts[1].toLongOrNull() ?: return ScanVerdict(false, "Mã QR không hợp lệ.")
        val session = sessions.findById(sessionId).orElse(null)
            ?: return ScanVerdict(false, "Phiên điểm danh không tồn tại.")
        if (!session.active) return ScanVerdict(false, "Phiên điểm danh đã kết thúc.")
        val deltaBuckets = now.epochSecond / BUCKET_SECONDS - bucket
        if (deltaBuckets > 1 || deltaBuckets < -1) return ScanVerdict(false, "Mã QR đã hết hạn, hãy quét mã mới.")
        if (signature(session.secret, sessionId, bucket) != parts[2]) return ScanVerdict(false, "Chữ ký mã QR không hợp lệ.")
        if (!enrollments.sectionIdsOfStudent(studentId).contains(session.sectionId)) {
            return ScanVerdict(false, "Bạn không thuộc lớp học phần này.")
        }
        if (records.existsByNonce(nonce)) return ScanVerdict(false, "Mã đã được sử dụng (anti-replay).")
        if (records.existsBySessionIdAndStudentId(sessionId, studentId)) {
            return ScanVerdict(false, "Bạn đã điểm danh phiên này.")
        }
        records.save(AttendanceRecord(sessionId = sessionId, studentId = studentId, bucket = bucket, nonce = nonce))
        return ScanVerdict(true, "Điểm danh thành công.")
    }

    fun countPresent(sessionId: UUID): Long = records.countBySessionId(sessionId)

    companion object {
        const val BUCKET_SECONDS = 30L

        /** Shared by the lecturer device (Kotlin) and this verifier. */
        fun signature(secret: String, sessionId: UUID, bucket: Long): String {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal("$sessionId.$bucket".toByteArray()))
        }

        fun encodePayload(sessionId: UUID, bucket: Long, signature: String): String =
            "${Base64.getUrlEncoder().withoutPadding().encodeToString(sessionId.toString().toByteArray())}.$bucket.$signature"
    }

    data class ScanVerdict(val accepted: Boolean, val message: String)
}

@Tag(name = "attendance")
@RestController
@RequestMapping("/api/v1/attendance")
class AttendanceController(private val attendance: AttendanceService) {

    data class CreateSessionRequest(val sectionId: UUID)
    data class CreateSessionResponse(val sessionId: UUID, val secret: String)
    data class ScanRequest(val payload: String, val nonce: String)

    @Operation(summary = "LECTURER: open a rotating-QR attendance session (device rotates the QR from the secret)")
    @PostMapping("/sessions")
    @PreAuthorize("hasAnyRole('LECTURER','ADMIN','SUPER_ADMIN')")
    fun createSession(@RequestBody body: CreateSessionRequest): ApiEnvelope<CreateSessionResponse> {
        val lecturerId = currentUserUuid() ?: throw ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Phiên không hợp lệ.")
        val opened = attendance.openSession(body.sectionId, lecturerId)
        return ApiEnvelope.ok(CreateSessionResponse(opened.session.id, opened.secret))
    }

    @Operation(summary = "LECTURER (owner) or ADMIN: close the session — QR stops working")
    @org.springframework.web.bind.annotation.PatchMapping("/sessions/{id}/close")
    @PreAuthorize("hasAnyRole('LECTURER','ADMIN','SUPER_ADMIN')")
    fun closeSession(@PathVariable id: UUID): ApiEnvelope<Map<String, String>> {
        val userId = currentUserUuid() ?: throw ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Phiên không hợp lệ.")
        val isAdmin = org.springframework.security.core.context.SecurityContextHolder
            .getContext().authentication.authorities.any {
                it.authority in setOf("ROLE_ADMIN", "ROLE_SUPER_ADMIN")
            }
        attendance.closeSession(id, userId, isAdmin)
        return ApiEnvelope.ok(mapOf("status" to "CLOSED"))
    }

    @Operation(summary = "STUDENT: submit a scanned rotating payload with a fresh nonce")
    @PostMapping("/scan")
    fun scan(@RequestBody body: ScanRequest): ApiEnvelope<Map<String, String>> {
        val studentId = currentUserUuid() ?: throw ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Phiên không hợp lệ.")
        if (body.nonce.isBlank() || body.nonce.length > 64) {
            throw ApiException(ErrorCode.VALIDATION_FAILED, "Nonce không hợp lệ.")
        }
        val verdict = attendance.verifyScan(body.payload, body.nonce, studentId)
        if (!verdict.accepted) throw ApiException(ErrorCode.VALIDATION_FAILED, verdict.message)
        return ApiEnvelope.ok(mapOf("status" to verdict.message))
    }

    @Operation(summary = "LECTURER: present count for a session")
    @GetMapping("/sessions/{id}/count")
    @PreAuthorize("hasAnyRole('LECTURER','ADMIN','SUPER_ADMIN')")
    fun count(@PathVariable id: UUID): ApiEnvelope<Map<String, Long>> =
        ApiEnvelope.ok(mapOf("present" to attendance.countPresent(id)))
}
