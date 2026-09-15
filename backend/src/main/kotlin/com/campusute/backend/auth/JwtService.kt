package com.campusute.backend.auth

import com.campusute.backend.config.AppProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.util.Date
import java.util.UUID

@Service
class JwtService(private val props: AppProperties) {

    private val key by lazy {
        require(props.jwt.secret.toByteArray().size >= 32) { "JWT secret must be >= 32 bytes" }
        Keys.hmacShaKeyFor(props.jwt.secret.toByteArray())
    }

    fun issue(user: User, roleNames: Collection<String>): String =
        Jwts.builder()
            .subject(user.id.toString())
            .claim("email", user.email)
            .claim("roles", roleNames)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + props.jwt.accessTtlMinutes * 60_000))
            .signWith(key)
            .compact()

    fun parse(token: String): Claims =
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload

    fun subjectOf(token: String): UUID = UUID.fromString(parse(token).subject)
}
