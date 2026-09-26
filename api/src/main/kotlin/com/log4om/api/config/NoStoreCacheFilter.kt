package com.log4om.api.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Prevent HAProxy / intermediary caches from storing auth or tenant data.
 * Stateless JWT + no-store = safe round-robin without sticky sessions.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class NoStoreCacheFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path = request.requestURI
        val sensitive = path.startsWith("/api/") ||
            path.startsWith("/auth/") ||
            path.startsWith("/me/") ||
            path.startsWith("/qsos") ||
            path.startsWith("/adif") ||
            path.startsWith("/lookup") ||
            path.startsWith("/stats")
        if (sensitive) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private")
            response.setHeader("Pragma", "no-cache")
            response.setHeader("Vary", "Authorization, Cookie")
        }
        filterChain.doFilter(request, response)
    }
}
