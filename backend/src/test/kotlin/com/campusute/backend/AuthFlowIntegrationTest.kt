package com.campusute.backend

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
import org.springframework.http.ResponseEntity
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 1 contract/authorization gates (plan K2):
 * - login issues access + rotating refresh token
 * - refresh rotation works; presenting the old token revokes ALL sessions
 * - STUDENT cannot reach ADMIN endpoints (403)
 * - error envelope carries code/message/traceId and data=null
 * Envelopes are read as maps to keep Jackson generic-type handling simple.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AuthFlowIntegrationTest {

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

    @Test
    @Order(1)
    fun `login returns access and refresh tokens`() {
        val body = login()
        val data = body.data!!
        assertTrue(data["accessToken"].toString().isNotBlank())
        assertTrue(data["refreshToken"].toString().isNotBlank())
        val roles = (data["user"] as Map<*, *>)["roles"] as List<*>
        assertEquals(listOf("STUDENT"), roles)
    }

    @Test
    @Order(2)
    fun `refresh rotates and reuse revokes all sessions`() {
        val first = login().data!!
        val refreshed = post("""{"refreshToken":"${first["refreshToken"]}"}""")
        assertEquals(200, refreshed.statusCode.value())
        val newTokens = envelope(refreshed.body).data!!
        assertTrue(
            newTokens["refreshToken"] != first["refreshToken"],
            "rotation must issue a new refresh token",
        )

        // Presenting the OLD (rotated) token = reuse -> 401 + all sessions revoked
        val reuse = post("""{"refreshToken":"${first["refreshToken"]}"}""")
        assertEquals(401, reuse.statusCode.value(), "reuse of rotated token must 401, got ${reuse.statusCode}: ${reuse.body}")

        // Even the newest token is dead because of the theft response
        val newest = post("""{"refreshToken":"${newTokens["refreshToken"]}"}""")
        assertEquals(401, newest.statusCode.value(), "session must die after reuse detection, got ${newest.statusCode}: ${newest.body}")
    }

    @Test
    @Order(3)
    fun `student token cannot reach admin endpoint`() {
        val headers = authHeaders(login().data!!["accessToken"].toString())
        val response = get("/api/v1/admin/ping", headers)
        assertEquals(403, response.statusCode.value())
    }

    @Test
    @Order(4)
    fun `error envelope carries code message traceId and null data`() {
        val response = rawPost("""{"email":"nobody@x.vn","password":"wrong"}""")
        assertEquals(401, response.statusCode.value())
        val body = envelope(response.body)
        val error = body.error!!
        assertEquals("AUTH_INVALID_CREDENTIALS", error.code)
        assertNotNull(error.message)
        assertNotNull(error.traceId, "traceId must propagate into error envelope")
        assertEquals(null, body.data)
    }

    @Test
    @Order(5)
    fun `me endpoint returns roles for valid access token`() {
        val headers = authHeaders(login().data!!["accessToken"].toString())
        val response = get("/api/v1/me", headers)
        assertEquals(200, response.statusCode.value())
        val user = envelope(response.body).data!!
        assertEquals(listOf("STUDENT"), user["roles"])
    }

    @Test
    @Order(6)
    fun `schedule returns only own sessions with conflict metadata`() {
        val headers = authHeaders(login().data!!["accessToken"].toString())
        val monday = java.time.LocalDate.now().with(java.time.DayOfWeek.MONDAY)
        val response = rest.exchange<ApiEnvelope<Map<String, Any?>>>(
            "/api/v1/schedule/sessions?from=$monday&to=${monday.plusDays(6)}",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
        )
        assertEquals(200, response.statusCode.value(), response.body.toString())
        val body = envelope(response.body)
        @Suppress("UNCHECKED_CAST")
        val sessions = body.data as List<Map<String, Any?>>
        assertTrue(sessions.isNotEmpty(), "seeded demo student must have sessions this week")
        val seededCodes = setOf("SE104", "DBMS311", "NET325", "OS321", "AI410")
        assertTrue(sessions.all { (it["courseCode"] as String) in seededCodes }, "ownership scoping")
        assertEquals(true, body.meta["conflict"], "deliberate Tuesday overlap must flag conflict")
    }

    @Test
    @Order(7)
    fun `lecturer sees no student schedule (scope by enrollment)`() {
        val lecturer = rawLogin("lecturer@demo.campusute.vn", "Demo#Lecturer1")
        val headers = authHeaders(lecturer["accessToken"].toString())
        val monday = java.time.LocalDate.now().with(java.time.DayOfWeek.MONDAY)
        val response = rest.exchange<ApiEnvelope<Map<String, Any?>>>(
            "/api/v1/schedule/sessions?from=$monday&to=${monday.plusDays(6)}",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
        )
        assertEquals(200, response.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val sessions = envelope(response.body).data as List<Map<String, Any?>>
        assertTrue(sessions.isEmpty(), "lecturer has no enrollments -> empty, never other users' data")
    }

    private fun rawLogin(email: String, password: String): Map<String, Any?> {
        val response = rest.exchange<ApiEnvelope<Map<String, Any?>>>(
            "/api/v1/auth/login",
            HttpMethod.POST,
            jsonEntity("""{"email":"$email","password":"$password"}"""),
        )
        return envelope(response.body).data!!
    }

    private fun login(): ApiEnvelope<Map<String, Any?>> =
        envelope(
            rawPost("""{"email":"student@demo.campusute.vn","password":"Demo#Student1"}""").body,
        )

    private fun envelope(body: Any?): ApiEnvelope<Map<String, Any?>> {
        @Suppress("UNCHECKED_CAST")
        return body as ApiEnvelope<Map<String, Any?>>
    }

    private fun rawPost(json: String): ResponseEntity<ApiEnvelope<Map<String, Any?>>> =
        rest.exchange(
            "/api/v1/auth/login",
            HttpMethod.POST,
            jsonEntity(json),
        )

    private fun post(json: String): ResponseEntity<ApiEnvelope<Map<String, Any?>>> = rest.exchange(
        "/api/v1/auth/refresh",
        HttpMethod.POST,
        jsonEntity(json),
    )

    private fun get(path: String, headers: HttpHeaders) =
        rest.exchange<ApiEnvelope<Map<String, Any?>>>(path, HttpMethod.GET, HttpEntity<Void>(headers))

    private fun authHeaders(token: String): HttpHeaders =
        HttpHeaders().apply { set("Authorization", "Bearer $token") }

    private fun jsonEntity(json: String): HttpEntity<String> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        return HttpEntity(json, headers)
    }
}
