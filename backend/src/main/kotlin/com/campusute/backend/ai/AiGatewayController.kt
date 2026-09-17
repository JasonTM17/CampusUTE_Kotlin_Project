package com.campusute.backend.ai

import com.campusute.backend.academic.EnrollmentRepository
import com.campusute.backend.auth.currentUserUuid
import com.campusute.backend.common.ApiEnvelope
import com.campusute.backend.common.ApiException
import com.campusute.backend.common.ErrorCode
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate

/**
 * Backend gateway for the AI service (ADR-0004): the app only ever talks to
 * this endpoint; the USER's JWT is forwarded so backend tools inside the AI
 * service are re-authorized here, and the caller's REAL enrolled course codes
 * (resolved from class-section rows, not raw UUIDs) are passed under the
 * internal token so RAG visibility filtering matches true enrollment. AI
 * being down must not affect core features.
 */
@Tag(name = "ai")
@RestController
@RequestMapping("/api/v1/ai")
class AiGatewayController(
    @Value("\${app.ai-service.url:http://ai-service:8600}") private val aiBaseUrl: String,
    @Value("\${app.ai-service.internal-token:\${AI_INGEST_TOKEN:}}") private val internalToken: String,
    private val enrollments: EnrollmentRepository,
    private val sections: com.campusute.backend.academic.ClassSectionRepository,
) {
    private val rest = RestTemplate().apply {
        val factory = org.springframework.http.client.SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(3000)
            setReadTimeout(60_000)
        }
        requestFactory = factory
    }

    data class ChatRequest(val message: String)
    data class CitationDto(val document: String?, val page: Int?, val excerpt: String?, val source: String?)
    data class ChatResponse(val answer: String, val citations: List<CitationDto> = emptyList(), val tools: List<String> = emptyList())

    @Operation(summary = "Campus assistant (RAG + tools, re-authorized here)")
    @PostMapping("/chat")
    fun chat(
        @RequestHeader("Authorization") authorization: String,
        @RequestBody body: ChatRequest,
    ): ApiEnvelope<ChatResponse> {
        val userId = currentUserUuid() ?: throw ApiException(ErrorCode.AUTH_TOKEN_INVALID, "Phiên không hợp lệ.")
        if (body.message.isBlank() || body.message.length > 2000) {
            throw ApiException(ErrorCode.VALIDATION_FAILED, "Câu hỏi không hợp lệ.")
        }
        // Real section codes from the enrollment table (SEC-<CODE>-NN).
        val enrolledCodes = enrollments.sectionIdsOfStudent(userId)
            .mapNotNull { sections.findById(it).orElse(null)?.sectionCode }
            .mapNotNull { code -> Regex("SEC-([A-Z0-9]+)-").find(code)?.groupValues?.get(1) }
            .distinct()
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("Authorization", authorization)
            set("X-Acting-User", userId.toString())
            if (internalToken.isNotBlank()) set("X-Internal-Token", internalToken)
        }
        return try {
            val response = rest.exchange(
                "$aiBaseUrl/chat",
                HttpMethod.POST,
                HttpEntity(mapOf("message" to body.message, "enrolledCourseCodes" to enrolledCodes), headers),
                ChatResponse::class.java,
            )
            ApiEnvelope.ok(response.body ?: ChatResponse("AI không trả lời được lúc này."))
        } catch (_: Exception) {
            // Bounded subsystem (plan §117): campus features keep working
            // when the AI service is unavailable.
            ApiEnvelope.ok(ChatResponse("Trợ lý AI tạm không khả dụng — các tính năng khác vẫn hoạt động bình thường."))
        }
    }

    @GetMapping("/health")
    fun aiHealth(): ApiEnvelope<Map<String, String>> = try {
        val response = rest.getForEntity("$aiBaseUrl/health", Map::class.java)
        @Suppress("UNCHECKED_CAST")
        val body = response.body as? Map<String, String>
        ApiEnvelope.ok(body ?: mapOf("status" to "unknown"))
    } catch (_: Exception) {
        ApiEnvelope.ok(mapOf("status" to "unavailable"))
    }
}
