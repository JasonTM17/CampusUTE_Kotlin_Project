package com.campusute.backend.ai

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
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The AI chat throttle, gated against a real Redis.
 *
 * `FixedWindowRateLimiter` fails open whenever `StringRedisTemplate` is absent, which is exactly
 * what happens in a Postgres-only context — so `AiChatGatewayTest` can never observe a 429 and the
 * throttle was, until now, an untested claim. This class supplies the missing collaborator and
 * lowers the budget to two per minute so the boundary is reached in three calls instead of thirteen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = ["app.ai-chat-rate-limit-per-minute=2"])
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AiChatRateLimitTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        @Container
        @JvmStatic
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine").withExposedPorts(6379)

        @JvmStatic
        @DynamicPropertySource
        fun infra(reg: DynamicPropertyRegistry) {
            reg.add("spring.datasource.url", postgres::getJdbcUrl)
            reg.add("spring.datasource.username", postgres::getUsername)
            reg.add("spring.datasource.password", postgres::getPassword)
            reg.add("spring.data.redis.host", redis::getHost)
            reg.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }

    @Autowired
    lateinit var rest: TestRestTemplate

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

    private fun chat(token: String) = rest.exchange<ApiEnvelope<Map<String, Any?>>>(
        "/api/v1/ai/chat",
        HttpMethod.POST,
        HttpEntity("""{"message":"Điều kiện tốt nghiệp là gì?"}""", authHeaders(token)),
    )

    /** Drives the counter past the budget; returns the first response the throttle rejected. */
    private fun exhaustChatBudget(token: String) =
        (1..6).map { chat(token) }.first { it.statusCode.value() == 429 }

    @Test
    @Order(1)
    fun `chat is throttled with a real 429 once the per-minute budget is spent`() {
        val token = login()

        // The first two attempts pass the gate. ai-service is not running here, so they answer
        // with the bounded-subsystem envelope at HTTP 200 — the point is that they were not
        // rejected for rate, which distinguishes the two failure modes end to end.
        val allowed = chat(token)
        assertEquals(200, allowed.statusCode.value(), allowed.body?.toString())
        chat(token)

        val rejected = chat(token)
        assertEquals(429, rejected.statusCode.value(), "the third chat in a minute must be throttled")
        val envelope = rejected.body!!
        assertNull(envelope.data, "a throttled chat must not fabricate an answer")
        assertNotNull(envelope.error, "the rejection must be reported in the envelope")
        assertEquals("RATE_LIMITED", envelope.error!!.code)
        assertTrue(envelope.error!!.message.isNotBlank(), "the client shows this text verbatim")
    }

    @Test
    @Order(2)
    fun `a spent chat budget does not touch the login scope`() {
        // Both limits are per-minute fixed windows, so a shared key prefix would let one chatty
        // student lock themselves out of authenticating. The scopes must stay disjoint.
        val token = login()
        val rejected = exhaustChatBudget(token)
        assertEquals(429, rejected.statusCode.value())

        val relogin = rest.exchange<ApiEnvelope<Map<String, Any?>>>(
            "/api/v1/auth/login",
            HttpMethod.POST,
            HttpEntity("""{"email":"student@demo.campusute.vn","password":"Demo#Student1"}""", jsonHeaders()),
        )
        assertEquals(200, relogin.statusCode.value(), "signing in must survive an exhausted chat budget")
        assertNotNull(relogin.body?.data?.get("accessToken"))
    }
}
