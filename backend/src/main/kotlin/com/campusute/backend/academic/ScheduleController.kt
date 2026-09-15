package com.campusute.backend.academic

import com.campusute.backend.auth.currentUserUuid
import com.campusute.backend.common.ApiEnvelope
import com.campusute.backend.common.ErrorCode
import com.campusute.backend.common.ApiException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Tag(name = "schedule")
@RestController
@RequestMapping("/api/v1/schedule")
class ScheduleController(private val scheduleService: ScheduleService) {

    data class ScheduleSessionDto(
        val id: UUID,
        val courseCode: String,
        val courseName: String,
        val lecturerName: String,
        val building: String,
        val room: String,
        val date: String,
        val startAt: String,
        val endAt: String,
        val conflict: Boolean,
    )

    @Operation(summary = "Current student's class sessions in a date range (ownership-scoped)")
    @GetMapping("/sessions")
    fun sessions(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): ApiEnvelope<List<ScheduleSessionDto>> {
        if (to.isBefore(from)) {
            throw ApiException(ErrorCode.VALIDATION_FAILED, "Khoảng ngày không hợp lệ.")
        }
        if (from.isBefore(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusYears(1)) ||
            to.isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusYears(1))
        ) {
            throw ApiException(ErrorCode.VALIDATION_FAILED, "Khoảng ngày vượt quá giới hạn truy vấn (±1 năm).")
        }
        val studentId = currentUserUuid()
            ?: throw ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Phiên không hợp lệ.")

        val occurrences = scheduleService.occurrencesFor(studentId, from, to)
        return ApiEnvelope.ok(
            occurrences.map {
                ScheduleSessionDto(
                    id = it.id,
                    courseCode = it.courseCode,
                    courseName = it.courseName,
                    lecturerName = it.lecturerName,
                    building = it.building,
                    room = it.room,
                    date = it.date.toString(),
                    startAt = it.startAt,
                    endAt = it.endAt,
                    conflict = it.conflict,
                )
            },
            meta = mapOf(
                "conflict" to occurrences.any { it.conflict },
                "count" to occurrences.size,
            ),
        )
    }
}

/** Minimal admin probe used by the RBAC security tests. */
@Tag(name = "admin")
@RestController
@RequestMapping("/api/v1/admin")
class AdminProbeController {
    @Operation(summary = "ADMIN-only probe (RBAC test)")
    @GetMapping("/ping")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    fun ping(): ApiEnvelope<Map<String, String>> = ApiEnvelope.ok(mapOf("pong" to "admin"))
}
