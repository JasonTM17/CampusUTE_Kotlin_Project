package com.campusute.backend.config

import com.campusute.backend.auth.JwtAuthFilter
import com.campusute.backend.common.ApiException
import com.campusute.backend.common.ErrorCode
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(private val jwtAuthFilter: JwtAuthFilter) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = Argon2PasswordEncoder(16, 32, 1, 19_456, 2)

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .requestMatchers("/api/v1/auth/**").permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .exceptionHandling { it.authenticationEntryPoint { _, response, _ -> response.sendError(401) } }
            .build()
}

/**
 * Fixed-window login rate limiter backed by Redis. Fails OPEN with a warning
 * when Redis is unreachable: availability of authentication outweighs the
 * limiter, and Redis outages must not lock the whole campus out.
 */
@Component
class LoginRateLimiter(
    private val redis: StringRedisTemplate?,
    private val props: AppProperties,
) {
    private val log = LoggerFactory.getLogger(LoginRateLimiter::class.java)

    fun check(key: String) {
        val limit = props.loginRateLimitPerMinute
        try {
            val redisKey = "rl:login:$key:${Instant.now().epochSecond / 60}"
            val count = redis?.opsForValue()?.increment(redisKey) ?: return
            if (count == 1L) redis.expire(redisKey, Duration.ofSeconds(70))
            if (count > limit) {
                throw ApiException(ErrorCode.RATE_LIMITED, "Quá nhiều lần thử, vui lòng thử lại sau một phút.")
            }
        } catch (ex: ApiException) {
            throw ex
        } catch (ex: Exception) {
            log.warn("login_rate_limiter_unavailable: failing open ({})", ex.javaClass.simpleName)
        }
    }
}
