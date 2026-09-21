package com.campusute.backend.ai

import com.campusute.backend.audit.AuditLogRepository
import com.campusute.backend.common.ApiEnvelope
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.exchange
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Chat gateway gates for the assistant deepening:
 * - the gateway, not ai-service, must reject a bad JWT and an out-of-range message
 * - an unreachable AI service must surface as an error envelope, not as a 200 whose
 *   `answer` is fallback prose the client cannot tell apart from a real reply
 * - every chat turn writes an audit row naming the tools, never the prompt text
 * - the frozen snapshot must actually contain /api/v1/ai/chat, which the app calls
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AiChatGatewayTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun datasource(reg: DynamicPropertyRegistry) {
            reg.add("spring.datasource.url", postgres::getJdbcUrl)
            reg.add("spring.datasource.username", postgres::getUsername)
            reg.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var auditLogs: AuditLogRepository

    private fun jsonHeaders(): HttpHeaders = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

    private fun authHeaders(token: String): HttpHeaders =
        HttpHeaders().apply { set("Authorization", "Bearer $token"); contentType = MediaType.APPLICATION_JSON }

    private fun login(): String {
        val response = rest.exchange<ApiEnvelope<Map<String, Any?>>>(
            "/api/v1/auth/login",
            HttpMethod.POST,
            HttpEntity("""{"email":"student@demo.campusute.vn","password":"Demo#Student1"}""", jsonHeaders()),
        )
        return response.body!!.data!!["accessToken"].toString()
    }

    private fun chat(body: String, headers: HttpHeaders) =
        rest.exchange<ApiEnvelope<Map<String, Any?>>>(
            "/api/v1/ai/chat",
            HttpMethod.POST,
            HttpEntity(body, headers),
        )

    @Test
    @Order(1)
    fun `chat with garbage jwt is rejected with 401 at the security boundary`() {
        val headers = authHeaders("not-a-jwt")
        val response = chat("""{"message":"Học phí kỳ này bao nhiêu?"}""", headers)
        assertEquals(401, response.statusCode.value(), response.body?.toString())
    }

    @Test
    @Order(2)
    fun `chat rejects blank and oversized messages before leaving the backend`() {
        val headers = authHeaders(login())
        assertEquals(400, chat("""{"message":"   "}""", headers).statusCode.value())
        val oversized = "a".repeat(2001)
        assertEquals(400, chat("""{"message":"$oversized"}""", headers).statusCode.value())
    }

    @Test
    @Order(3)
    fun `unreachable ai service surfaces as an error envelope not as an answer`() {
        // ai-service is not started in this context, so the upstream call fails.
        // Laundering that into a 200 + prose made "assistant down" indistinguishable
        // from a genuine reply, and left the client's error branch unreachable.
        val headers = authHeaders(login())
        val response = chat("""{"message":"Điều kiện tốt nghiệp là gì?"}""", headers)

        assertEquals(200, response.statusCode.value(), "chat stays a bounded subsystem: no 5xx")
        val envelope = response.body!!
        assertNull(envelope.data, "a failed AI call must not present a fabricated answer")
        assertNotNull(envelope.error, "the failure must be reported in the envelope")
        assertEquals("AI_UNAVAILABLE", envelope.error!!.code)
        assertTrue(
            envelope.error!!.message.isNotBlank(),
            "the user-facing message must survive in the envelope",
        )
    }

    @Test
    @Order(4)
    fun `a chat attempt is audited without storing the prompt`() {
        val before = auditLogs.count()
        chat("""{"message":"Mật khẩu của tôi là hunter2"}""", authHeaders(login()))

        val latest = auditLogs.findAll().toList()
            .filter { it.action == "AI_CHAT" }
            .maxByOrNull { it.id ?: 0L }
        assertNotNull(latest, "the chat gateway must record an AI_CHAT audit row")
        assertEquals("FAILURE", latest!!.result, "ai-service is down here, so the row must say so")
        assertTrue(
            auditLogs.findAll().none { (it.resource ?: "").contains("hunter2") },
            "audit rows must never store prompt content",
        )
        assertTrue(auditLogs.count() > before)
    }

    @Test
    @Order(5)
    fun `frozen snapshot and live api docs both expose the chat path`() {
        val headers = authHeaders(login())
        val live = rest.exchange<Map<String, Any?>>("/v3/api-docs", HttpMethod.GET, HttpEntity<Void>(headers))
        @Suppress("UNCHECKED_CAST")
        val livePaths = live.body!!["paths"] as Map<String, Any?>
        assertTrue(livePaths.containsKey("/api/v1/ai/chat"), "live docs missing /api/v1/ai/chat")

        val snapshot = sequenceOf(
            File("../packages/api-contracts/openapi.json"),
            File("packages/api-contracts/openapi.json"),
        ).firstOrNull(File::exists) ?: error("frozen snapshot not reachable from the backend test")
        val text = snapshot.readText()
        assertTrue(
            text.contains("/api/v1/ai/chat"),
            "contract drift: the app calls /api/v1/ai/chat but the frozen snapshot omits it",
        )
    }
}
