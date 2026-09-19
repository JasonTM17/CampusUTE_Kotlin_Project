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
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 2 contract gates (closeout plan 260919-0837):
 * - /api/v1/ai/summarize requires a valid JWT at the GATEWAY (ai-service itself
 *   answers a soft 200 on missing JWT, so 401 must be asserted here)
 * - blank/oversized content is rejected before leaving the backend
 * - when the AI service is unreachable, the envelope still returns 200 with a
 *   graceful fallback and proposed=false (bounded subsystem, ADR-0004)
 * - contract parity: the live springdoc document exposes the 3 paths added to
 *   the frozen snapshot (notes/{id} PUT+DELETE, ai/summarize) — drift fails CI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AiSummarizeGatewayTest {

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

    private fun authHeaders(token: String): HttpHeaders =
        HttpHeaders().apply { set("Authorization", "Bearer $token") }

    private fun login(): String {
        val response = rest.exchange<ApiEnvelope<Map<String, Any?>>>(
            "/api/v1/auth/login",
            HttpMethod.POST,
            HttpEntity("""{"email":"student@demo.campusute.vn","password":"Demo#Student1"}""", jsonHeaders()),
        )
        return response.body!!.data!!["accessToken"].toString()
    }

    private fun jsonHeaders(): HttpHeaders = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

    private fun summarize(body: String, headers: HttpHeaders) =
        rest.exchange<ApiEnvelope<Map<String, Any?>>>(
            "/api/v1/ai/summarize",
            HttpMethod.POST,
            HttpEntity(body, headers),
        )

    @Test
    @Order(1)
    fun `summarize with garbage jwt is rejected at the gateway with 401`() {
        // Missing header -> Spring 400 before the controller; a PRESENT but
        // invalid JWT must hit the gateway's own auth check (currentUserUuid).
        val headers = HttpHeaders().apply { set("Authorization", "Bearer not-a-jwt") }
        val response = summarize("""{"content":"Đây là ghi chú cần tóm tắt."}""", headers)
        assertEquals(401, response.statusCode.value(), response.body?.toString())
        assertEquals("AUTH_TOKEN_INVALID", response.body?.error?.code)
    }

    @Test
    @Order(2)
    fun `summarize with blank content is rejected with 400`() {
        val headers = authHeaders(login()).apply { contentType = MediaType.APPLICATION_JSON }
        val response = summarize("""{"content":"   "}""", headers)
        assertEquals(400, response.statusCode.value(), response.body.toString())
    }

    @Test
    @Order(3)
    fun `summarize degrades gracefully when ai service is unreachable`() {
        val headers = authHeaders(login()).apply { contentType = MediaType.APPLICATION_JSON }
        val response = summarize("""{"title":"Ghi chú DBMS","content":"Học JOIN. Học INDEX. Làm lab 2."}""", headers)
        assertEquals(200, response.statusCode.value(), response.body.toString())
        val data = response.body!!.data!!
        assertNotNull(data["summary"])
        assertEquals(false, data["proposed"], "fallback must never propose (propose-only invariant)")
    }

    @Test
    @Order(4)
    fun `live api docs expose the contract parity paths`() {
        val headers = authHeaders(login())
        val response = rest.exchange<Map<String, Any?>>("/v3/api-docs", HttpMethod.GET, HttpEntity<Void>(headers))
        @Suppress("UNCHECKED_CAST")
        val paths = response.body!!["paths"] as Map<String, Any?>
        for (path in listOf("/api/v1/ai/summarize", "/api/v1/notes/{id}")) {
            assertTrue(paths.containsKey(path), "contract drift: /v3/api-docs is missing $path")
        }
        @Suppress("UNCHECKED_CAST")
        val notesOps = paths["/api/v1/notes/{id}"] as Map<String, Any?>
        assertTrue(notesOps.containsKey("put") && notesOps.containsKey("delete"), "notes/{id} must offer PUT+DELETE")
    }
}
