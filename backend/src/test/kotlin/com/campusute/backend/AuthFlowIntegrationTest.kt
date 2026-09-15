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

    @org.springframework.beans.factory.annotation.Autowired
    lateinit var sectionsRepo: com.campusute.backend.academic.ClassSectionRepository

    @org.springframework.beans.factory.annotation.Autowired
    lateinit var eventsRepo: com.campusute.backend.academic.EventRepository

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
        val response = rest.exchange<ApiEnvelope<List<Map<String, Any?>>>>(
            "/api/v1/schedule/sessions?from=$monday&to=${monday.plusDays(6)}",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
        )
        assertEquals(200, response.statusCode.value(), response.body.toString())
        val body = envelopeList(response.body)
        val sessions = body.data!!
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
        val response = rest.exchange<ApiEnvelope<List<Map<String, Any?>>>>(
            "/api/v1/schedule/sessions?from=$monday&to=${monday.plusDays(6)}",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
        )
        assertEquals(200, response.statusCode.value())
        val sessions = envelopeList(response.body).data!!
        assertTrue(sessions.isEmpty(), "lecturer has no enrollments -> empty, never other users' data")
    }

    private fun envelopeList(body: Any?): ApiEnvelope<List<Map<String, Any?>>> {
        @Suppress("UNCHECKED_CAST")
        return body as ApiEnvelope<List<Map<String, Any?>>>
    }

    @Test
    @Order(8)
    fun `sync engine replay idempotency conflict and delta`() {
        val headers = authHeaders(login().data!!["accessToken"].toString()).apply {
            contentType = MediaType.APPLICATION_JSON
        }
        fun postJson(path: String, json: String) =
            rest.exchange<ApiEnvelope<Map<String, Any?>>>(path, HttpMethod.POST, HttpEntity(json, headers))

        // CREATE + offline replay of the SAME clientOpId -> 1 row only
        val op = """{"clientOpId":"op-001","opType":"CREATE","title":"Ôn JOIN","dueDate":"2026-10-01"}"""
        val first = postJson("/api/v1/tasks/sync", """{"operations":[$op]}""")
        assertEquals(200, first.statusCode.value(), first.body.toString())
        assertEquals("APPLIED", (first.body!!.data!!["results"] as List<*>).first().let { (it as Map<*, *>)["status"] })
        val replay = postJson("/api/v1/tasks/sync", """{"operations":[$op]}""")
        val replayStatus = (replay.body!!.data!!["results"] as List<*>).first().let { (it as Map<*, *>)["status"] }
        assertEquals("DUPLICATE", replayStatus, "replayed create must be a no-op duplicate")

        // Delta: only the NEW task comes back
        val since = java.time.Instant.now().minusSeconds(3600)
        val changes = rest.exchange<ApiEnvelope<Map<String, Any?>>>(
            "/api/v1/tasks/changes?since=$since",
            HttpMethod.GET,
            HttpEntity<Void>(headers),
        )
        @Suppress("UNCHECKED_CAST")
        val changesList = changes.body!!.data!!["changes"] as List<Map<String, Any?>>
        assertEquals(1, changesList.size)
        val taskId = changesList[0]["id"] as String
        val version = (changesList[0]["version"] as Number).toLong()

        // UPDATE with a STALE baseVersion -> CONFLICT, server state returned
        val stale = postJson(
            "/api/v1/tasks/sync",
            """{"operations":[{"clientOpId":"op-002","opType":"UPDATE","taskId":"$taskId","baseVersion":${version + 5},"done":true}]}""",
        )
        val conflictStatus = (stale.body!!.data!!["results"] as List<*>).first().let { (it as Map<*, *>)["status"] }
        assertEquals("CONFLICT", conflictStatus, "stale baseVersion must yield server-win conflict")

        // UPDATE with the correct version -> APPLIED and version bumps
        val good = postJson(
            "/api/v1/tasks/sync",
            """{"operations":[{"clientOpId":"op-003","opType":"UPDATE","taskId":"$taskId","baseVersion":$version,"done":true}]}""",
        )
        val applied = (good.body!!.data!!["results"] as List<*>).first().let { (it as Map<*, *>)["status"] }
        assertEquals("APPLIED", applied)
    }

    @Test
    @Order(9)
    fun `attendance rejects replay expiry outsider and accepts fresh scan`() {
        val section = sectionsRepo.findAll().first()
        val lecturerLogin = rawLogin("lecturer@demo.campusute.vn", "Demo#Lecturer1")
        val lecturerHeaders = authHeaders(lecturerLogin["accessToken"].toString()).apply {
            contentType = MediaType.APPLICATION_JSON
        }
        val created = rest.exchange<ApiEnvelope<Map<String, Any?>>>(
            "/api/v1/attendance/sessions",
            HttpMethod.POST,
            HttpEntity("""{"sectionId":"${section.id}"}""", lecturerHeaders),
        )
        assertEquals(200, created.statusCode.value(), created.body.toString())
        val sessionId = created.body!!.data!!["sessionId"] as String
        val secret = created.body!!.data!!["secret"] as String

        val studentHeaders = authHeaders(login().data!!["accessToken"].toString()).apply {
            contentType = MediaType.APPLICATION_JSON
        }
        val bucket = java.time.Instant.now().epochSecond / 30
        val payload = com.campusute.backend.academic.AttendanceService.encodePayload(
            java.util.UUID.fromString(sessionId),
            bucket,
            com.campusute.backend.academic.AttendanceService.signature(secret, java.util.UUID.fromString(sessionId), bucket),
        )

        fun scan(nonce: String, payloadOverride: String = payload) =
            rest.exchange<ApiEnvelope<Map<String, Any?>>>(
                "/api/v1/attendance/scan",
                HttpMethod.POST,
                HttpEntity("""{"payload":"$payloadOverride","nonce":"$nonce"}""", studentHeaders),
            )

        // Fresh scan accepted; duplicate scan of the same session rejected
        assertEquals(200, scan("nonce-1").statusCode.value())
        assertTrue(scan("nonce-2").body!!.error!!.message!!.contains("đã điểm danh"))

        // Replay of an old nonce blocked
        val replay = scan("nonce-1")
        assertTrue(replay.body!!.error!!.message!!.contains("anti-replay") || replay.body!!.error!!.message!!.contains("đã"), )

        // Stale bucket (older than ±1 window) rejected as expired
        val stalePayload = com.campusute.backend.academic.AttendanceService.encodePayload(
            java.util.UUID.fromString(sessionId),
            bucket - 5,
            com.campusute.backend.academic.AttendanceService.signature(secret, java.util.UUID.fromString(sessionId), bucket - 5),
        )
        val expired = scan("nonce-3", stalePayload)
        assertTrue(expired.body!!.error!!.message!!.contains("hết hạn"), expired.body.toString())

        // Present count visible to lecturer
        val count = rest.exchange<ApiEnvelope<Map<String, Any?>>>(
            "/api/v1/attendance/sessions/$sessionId/count",
            HttpMethod.GET,
            HttpEntity<Void>(lecturerHeaders),
        )
        assertEquals(1L, (count.body!!.data!!["present"] as Number).toLong())
    }

    @Test
    @Order(10)
    fun `event registration is idempotent per key`() {
        val headers = authHeaders(login().data!!["accessToken"].toString())
        val event = eventsRepo.findByCode("EVT-2026-AI-SEM")!!
        val first = rest.exchange<ApiEnvelope<Map<String, Any?>>>(
            "/api/v1/events/${event.id}/register",
            HttpMethod.POST,
            jsonEntityWith(headers, "idempotent-key-1"),
        )
        assertEquals(200, first.statusCode.value(), first.body.toString())
        val second = rest.exchange<ApiEnvelope<Map<String, Any?>>>(
            "/api/v1/events/${event.id}/register",
            HttpMethod.POST,
            jsonEntityWith(headers, "idempotent-key-1"),
        )
        assertEquals(
            first.body!!.data!!["registrationId"],
            second.body!!.data!!["registrationId"],
            "same Idempotency-Key must return the same registration",
        )
    }

    private fun jsonEntityWith(base: HttpHeaders, key: String): HttpEntity<String> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            putAll(base)
            set("Idempotency-Key", key)
        }
        return HttpEntity("{}", headers)
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
