package com.burpmcp.ultra.transport

import io.ktor.server.plugins.cors.CORSConfig

/**
 * Wave-45 hardening: the shared CORS origin policy for every BurpMCP-Ultra
 * HTTP surface (both MCP SSE transports and the dashboard).
 *
 * Before wave-45 every surface used Ktor's `anyHost()` — any webpage in the
 * analyst's browser could read proxy history (cookies/tokens from captured
 * traffic) and drive tool calls into Burp (repeater/proxy sends = SSRF
 * pivot). This object scopes CORS to the platform's own origins:
 *
 *  - Default allowlist: the nyxstrike/apex dashboard dev and prod ports —
 *    http://localhost:3000, :5173, :8080 plus the 127.0.0.1 equivalents.
 *  - `BURPMCP_ALLOWED_ORIGINS`: comma-separated extra origins the operator
 *    wants to allow (exact scheme://host:port strings, no wildcards).
 *  - `BURPMCP_ALLOW_ANY_ORIGIN=1`: EXPLICIT OPERATOR KEY that restores the
 *    old permissive behavior (any origin). The wildcard is still never
 *    combined with allowCredentials.
 */
object CorsOrigins {

    /** The platform's own dashboard origins (dev + prod ports). */
    private val DEFAULT_ORIGINS: List<String> = listOf(
        "http://localhost:3000",
        "http://127.0.0.1:3000",
        "http://localhost:5173",
        "http://127.0.0.1:5173",
        "http://localhost:8080",
        "http://127.0.0.1:8080"
    )

    private fun env(name: String): String? =
        System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * True when the operator has explicitly opted back into the pre-wave-45
     * permissive CORS with BURPMCP_ALLOW_ANY_ORIGIN=1.
     */
    val anyOriginOptIn: Boolean
        get() = env("BURPMCP_ALLOW_ANY_ORIGIN") == "1"

    /** Default allowlist + BURPMCP_ALLOWED_ORIGINS entries (deduped). */
    val allowedOrigins: List<String> by lazy {
        val extra = env("BURPMCP_ALLOWED_ORIGINS")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        (DEFAULT_ORIGINS + extra).distinct()
    }

    /** True when [origin] is in the allowlist (exact match, no patterns). */
    fun isAllowed(origin: String?): Boolean {
        if (origin.isNullOrBlank()) return false
        return allowedOrigins.contains(origin.trim())
    }

    /**
     * Applies the origin policy to a [CORSConfig]. Shared by both call
     * sites so the MCP transports and the dashboard cannot drift apart.
     * Uses the Ktor 3.2.x API surface (verified against
     * ktor-server-cors-jvm-3.2.3.jar): `anyHost()` for the explicit opt-in
     * and the `allowOrigins(predicate)` overload for the exact-match
     * allowlist.
     */
    fun apply(config: CORSConfig) {
        if (anyOriginOptIn) {
            // Explicit operator key: fully permissive (old behavior), but
            // never with credentials — wildcard+credentials is the one
            // combination that must not exist.
            config.anyHost()
        } else {
            val allow = allowedOrigins.toHashSet()
            config.allowOrigins { origin -> allow.contains(origin) }
        }
    }
}
