package com.campusute.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app")
data class AppProperties(
    val demoMode: Boolean = true,
    val jwt: JwtProperties = JwtProperties(),
    val refreshTtlDays: Long = 14,
    val loginRateLimitPerMinute: Long = 10,
    val aiChatRateLimitPerMinute: Long = 12,
) {
    data class JwtProperties(
        val secret: String = "",
        val accessTtlMinutes: Long = 15,
    )
}
