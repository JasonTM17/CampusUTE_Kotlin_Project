package com.campusute.backend.audit

import com.campusute.backend.common.CorrelationIdFilter
import org.slf4j.MDC
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import java.util.UUID

@Repository
interface AuditLogRepository : JpaRepository<AuditLog, Long>

@Service
class AuditService(private val repo: AuditLogRepository) {
    fun record(actorId: UUID?, action: String, resource: String? = null, result: String = "SUCCESS") {
        repo.save(
            AuditLog(
                actorId = actorId,
                action = action,
                resource = resource,
                result = result,
                traceId = MDC.get(CorrelationIdFilter.MDC_KEY),
            ),
        )
    }
}
