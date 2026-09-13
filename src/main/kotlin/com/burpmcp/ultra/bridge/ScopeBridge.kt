package com.burpmcp.ultra.bridge

import burp.api.montoya.MontoyaApi
import burp.api.montoya.scope.ScopeChangeHandler
import com.burpmcp.ultra.events.EventBus
import kotlinx.serialization.json.*
import java.time.Instant

class ScopeBridge(private val api: MontoyaApi, private val eventBus: EventBus) {

    fun check(url: String): JsonObject {
        val inScope = api.scope().isInScope(url)

        return buildJsonObject {
            put("url", url)
            put("in_scope", inScope)
        }
    }

    fun include(url: String): JsonObject {
        api.scope().includeInScope(url)

        return buildJsonObject {
            put("status", "included")
            put("url", url)
            put("in_scope", true)
        }
    }

    fun exclude(url: String): JsonObject {
        api.scope().excludeFromScope(url)

        return buildJsonObject {
            put("status", "excluded")
            put("url", url)
            put("in_scope", false)
        }
    }

    fun getConfig(): JsonObject {
        return try {
            val scopeJson = api.burpSuite().exportProjectOptionsAsJson("target.scope")

            return buildJsonObject {
                put("status", "success")
                put("scope_config", scopeJson)
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("status", "error")
                put("message", e.message ?: "Failed to export scope configuration")
            }
        }
    }

    fun createScopeChangeHandler(): ScopeChangeHandler {
        return ScopeChangeHandler { _ ->
            val eventData = buildJsonObject {
                put("event", "scope_changed")
                put("timestamp", Instant.now().toString())
            }
            eventBus.emit("scope.changed", eventData)
        }
    }
}
