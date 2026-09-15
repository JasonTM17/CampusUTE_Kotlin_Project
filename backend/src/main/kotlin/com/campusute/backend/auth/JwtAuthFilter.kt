package com.campusute.backend.auth

import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class JwtAuthFilter(private val jwt: JwtService) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().authentication == null) {
            try {
                val claims = jwt.parse(header.removePrefix("Bearer ").trim())
                val roles = (claims.get("roles") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val authorities = roles.map { SimpleGrantedAuthority("ROLE_$it") }
                val auth = UsernamePasswordAuthenticationToken(claims.subject, null, authorities)
                auth.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = auth
            } catch (_: JwtException) {
                // Invalid/expired access token -> anonymous; protected routes 401.
                SecurityContextHolder.clearContext()
            } catch (_: IllegalArgumentException) {
                SecurityContextHolder.clearContext()
            }
        }
        filterChain.doFilter(request, response)
    }
}

/** Extracts the authenticated user's id from the security context. */
fun currentUserUuid(): UUID? =
    (SecurityContextHolder.getContext().authentication?.principal as? String)?.let { principal ->
        runCatching { UUID.fromString(principal) }.getOrNull()
    }
