package com.campusute.backend.academic

import com.campusute.backend.common.ApiEnvelope
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

/**
 * Contract stub for the schedule endpoint (Phase 1 contract freeze).
 * Phase 2 replaces the payload with real seeded sessions; the path, query
 * params, envelope and conflict metadata are already frozen.
 */
@Tag(name = "schedule")
@RestController
@RequestMapping("/api/v1/schedule")
class ScheduleController {

    data class ScheduleSession(
        val id: UUID,
        val courseCode: String,
        val courseName: String,
        val lecturerName: String,
        val building: String,
        val room: String,
        val startAt: String,
        val endAt: String,
    )

    @Operation(summary = "Current student's class sessions in a date range")
    @GetMapping("/sessions")
    fun sessions(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): ApiEnvelope<List<ScheduleSession>> {
        // Phase 2: ABAC ownership check + Room-fed data; meta.conflict=true
        // when overlapping sessions exist for the caller.
        return ApiEnvelope.ok(emptyList(), meta = mapOf("conflict" to false, "count" to 0))
    }
}

/** Minimal admin probe used by the RBAC security tests. */
@Tag(name = "admin")
@RestController
@RequestMapping("/api/v1/admin")
class AdminProbeController {
    @Operation(summary = "ADMIN-only probe (RBAC test)")
    @GetMapping("/ping")
    @PreAuthorize("hasRole('ADMIN')")
    fun ping(): ApiEnvelope<Map<String, String>> = ApiEnvelope.ok(mapOf("pong" to "admin"))
}
