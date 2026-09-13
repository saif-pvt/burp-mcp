package com.burpmcp.ultra.bridge

import burp.api.montoya.MontoyaApi
import kotlinx.serialization.json.*
import java.time.Instant

/**
 * Bridge wrapping the Montoya Logging API.
 *
 * Provides methods to write to Burp Suite's output and error logs,
 * as well as to raise events at varying severity levels in the
 * event log panel.
 */
class LoggingBridge(private val api: MontoyaApi) {

    /**
     * Writes a message to Burp Suite's extension output or error stream.
     *
     * @param message The message text to log.
     * @param level "output" for the output stream, "error" for the error stream.
     */
    fun logMessage(message: String, level: String): JsonObject {
        when (level.lowercase()) {
            "error" -> api.logging().logToError(message)
            else -> api.logging().logToOutput(message)
        }

        return buildJsonObject {
            put("status", "logged")
            put("level", level.lowercase())
            put("message_length", message.length)
            put("timestamp", Instant.now().toString())
        }
    }

    /**
     * Raises an event in Burp Suite's event log panel at the given severity.
     *
     * @param message The event message.
     * @param level Severity: "debug", "info", "error", or "critical".
     */
    fun logEvent(message: String, level: String): JsonObject {
        when (level.lowercase()) {
            "debug" -> api.logging().raiseDebugEvent(message)
            "error" -> api.logging().raiseErrorEvent(message)
            "critical" -> api.logging().raiseCriticalEvent(message)
            else -> api.logging().raiseInfoEvent(message)
        }

        return buildJsonObject {
            put("status", "event_raised")
            put("level", level.lowercase())
            put("message_length", message.length)
            put("timestamp", Instant.now().toString())
        }
    }
}
