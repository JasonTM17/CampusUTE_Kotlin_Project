package com.campusute.backend.common

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Propagates a correlation id across Android -> backend -> ai-service.
 * Clients may send X-Correlation-Id; otherwise one is generated. The value
 * lands in the MDC (log lines) and on the response header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter : OncePerRequestFilter() {
    companion object {
        const val HEADER = "X-Correlation-Id"
        const val MDC_KEY = "traceId"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val traceId = request.getHeader(HEADER)?.takeIf { it.isNotBlank() && it.length <= 64 }
            ?: UUID.randomUUID().toString()
        MDC.put(MDC_KEY, traceId)
        response.setHeader(HEADER, traceId)
        request.setAttribute(HEADER, traceId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }
}
