package com.campusute.backend

import com.campusute.backend.common.ApiEnvelope
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Same rotation/reuse gates as AuthFlowIntegrationTest but against the local
 * docker-compose stack (used when Testcontainers cannot attach to Docker
 * Desktop npipe on this host). Run with the core profile up:
 *
 *   CAMPUSUTE_LOCAL_PG_PASSWORD=<password from .env> \
 *   ./gradlew test --tests "*RefreshRotationComposeStackTest*" \
 *     -Dcampusute.composeStack=true
 *
 * The password is read from the environment at runtime — never committed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class RefreshRotationComposeStackTest {

    companion object {
        // Datasource props come from the LOCAL ENVIRONMENT only (never
        // committed): CAMPUSUTE_PG_PORT + CAMPUSUTE_LOCAL_PG_PASSWORD.
        @JvmStatic
        @DynamicPropertySource
        fun datasourceProps(reg: DynamicPropertyRegistry) {
            val port = System.getenv("CAMPUSUTE_PG_PORT") ?: "15432"
            reg.add("spring.datasource.url") { "jdbc:postgresql://localhost:$port/campusute" }
            reg.add("spring.datasource.username") { "campusute" }
            reg.add("spring.datasource.password") { System.getenv("CAMPUSUTE_LOCAL_PG_PASSWORD") ?: "" }
            reg.add("app.jwt.secret") { "dev-only-secret-change-me-32-bytes-minimum!!" }
            reg.add("app.demo-mode") { "true" }
        }
    }

    @Autowired
    lateinit var rest: TestRestTemplate

    @Test
    fun `rotate then reuse revokes the successor session`() {
        val first = login().data!!
        val refreshed = post("/api/v1/auth/refresh", """{"refreshToken":"${first["refreshToken"]}"}""")
        assertEquals(200, refreshed.statusCode.value(), "rotate failed: ${refreshed.body}")
        val newTokens = envelope(refreshed.body).data!!
        assertTrue(newTokens["refreshToken"] != first["refreshToken"], "rotation must issue new token")

        val reuse = post("/api/v1/auth/refresh", """{"refreshToken":"${first["refreshToken"]}"}""")
        assertEquals(401, reuse.statusCode.value(), "reuse must 401")

        val newest = post("/api/v1/auth/refresh", """{"refreshToken":"${newTokens["refreshToken"]}"}""")
        assertEquals(401, newest.statusCode.value(), "successor must be revoked after reuse")
    }

    private fun login(): ApiEnvelope<Map<String, Any?>> =
        envelope(
            rawPost("/api/v1/auth/login", """{"email":"student@demo.campusute.vn","password":"Demo#Student1"}""").body,
        )

    private fun envelope(body: Any?): ApiEnvelope<Map<String, Any?>> {
        @Suppress("UNCHECKED_CAST")
        return body as ApiEnvelope<Map<String, Any?>>
    }

    private fun rawPost(path: String, json: String): ResponseEntity<ApiEnvelope<Map<String, Any?>>> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        return rest.exchange(path, HttpMethod.POST, HttpEntity(json, headers))
    }

    private fun post(path: String, json: String): ResponseEntity<ApiEnvelope<Map<String, Any?>>> = rawPost(path, json)
}
