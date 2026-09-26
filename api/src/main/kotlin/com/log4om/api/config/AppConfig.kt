package com.log4om.api.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableConfigurationProperties(Log4omProperties::class)
class AppConfig(
    private val props: Log4omProperties
) {
    /**
     * Explicit CorsConfigurationSource so Spring Security and MVC share the same rules.
     * JWT is sent via Authorization header (no cookies) → credentials disabled → "*" is allowed.
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val patterns = props.cors.allowedOrigins.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("*") }

        val config = CorsConfiguration().apply {
            allowedOriginPatterns = patterns
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD")
            allowedHeaders = listOf("*")
            exposedHeaders = listOf("Content-Disposition")
            allowCredentials = false
            maxAge = 3600
        }
        return UrlBasedCorsConfigurationSource().also {
            it.registerCorsConfiguration("/**", config)
        }
    }
}
