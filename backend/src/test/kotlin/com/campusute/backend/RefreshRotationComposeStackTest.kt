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
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals

/**
 * Same rotation/reuse gates as AuthFlowIntegrationTest but against the local
 * docker-compose stack (used when Testcontainers cannot attach to Docker
 * Desktop npipe on this host). Run with the core profile up:
 *   docker compose --profile core up -d
 *   ./gradlew test --tests "*RefreshRotationComposeStackTest*"
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:postgresql://localhost:15432/campusute",
        "spring.datasource.username=campusute",
        "spring.datasource.password=change_me_locally",
        "app.jwt.secret=dev-only-secret-change-me-32-bytes-minimum!!",
        "app.demo-mode=true",
    ],
)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class RefreshRotationComposeStackTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Test
    @Order(1)
    fun `rotate then reuse revokes the successor session`() {
        val first = login().data!!
        val refreshed = post("/api/v1/auth/refresh", """{"refreshToken":"${first["refreshToken"]}"}""")
        assertEquals(200, refreshed.statusCode.value(), "rotate failed: ${refreshed.body}")
        val newTokens = envelope(refreshed.body).data!!
        println("DIAG rotate ok, r2!=r1: ${newTokens["refreshToken"] != first["refreshToken"]}")

        val reuse = rawPost("/api/v1/auth/refresh", """{"refreshToken":"${first["refreshToken"]}"}""")
        println("DIAG reuse status=${reuse.statusCode.value()} body=${reuse.body}")
        assertEquals(401, reuse.statusCode.value())

        val newest = rawPost("/api/v1/auth/refresh", """{"refreshToken":"${newTokens["refreshToken"]}"}""")
        println("DIAG newest status=${newest.statusCode.value()} body=${newest.body}")
        assertEquals(401, newest.statusCode.value())
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
        val headers = org.springframework.http.HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        return rest.exchange(path, HttpMethod.POST, HttpEntity(json, headers))
    }

    private fun post(path: String, json: String): ResponseEntity<ApiEnvelope<Map<String, Any?>>> = rawPost(path, json)
}
