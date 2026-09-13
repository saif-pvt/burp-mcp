package com.burpmcp.ultra.bridge

import burp.api.montoya.MontoyaApi
import kotlinx.serialization.json.*

/**
 * Bridge wrapping the Montoya AI API (Burp Suite Professional only, 2025+).
 *
 * All methods guard against [UnsupportedOperationException] thrown when
 * the AI API is not available (Community Edition or older versions),
 * returning meaningful error JSON instead of crashing.
 */
class AiBridge(private val api: MontoyaApi) {

    /**
     * Checks whether the Montoya AI feature is enabled in the running
     * Burp Suite instance.
     *
     * @return JSON object with enabled status and availability info.
     */
    fun isEnabled(): JsonObject {
        return try {
            val enabled = api.ai().isEnabled
            buildJsonObject {
                put("enabled", enabled)
                put("available", true)
            }
        } catch (e: UnsupportedOperationException) {
            buildJsonObject {
                put("enabled", false)
                put("available", false)
                put("reason", "AI API is not available in this edition of Burp Suite")
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("enabled", false)
                put("available", false)
                put("reason", "Failed to check AI status: ${e.message}")
            }
        }
    }

    /**
     * Sends a prompt to Burp Suite's AI and returns the response.
     *
     * @param messages List of message strings to send as the prompt.
     * @param context Optional context string to provide additional background
     *                information for the AI.
     * @return JSON object with the AI response and metadata.
     */
    fun prompt(messages: List<String>, context: String?): JsonObject {
        return try {
            val aiApi = api.ai()

            if (!aiApi.isEnabled) {
                return buildJsonObject {
                    put("error", "AI is not enabled in Burp Suite. Enable it in Settings > AI.")
                    put("enabled", false)
                }
            }

            val promptBuilder = aiApi.prompt()

            // Build the prompt message by joining all messages
            val fullPrompt = if (context != null) {
                "Context: $context\n\n${messages.joinToString("\n")}"
            } else {
                messages.joinToString("\n")
            }

            // Try varargs signature first (execute(String...)), fall back to single string
            val response = try {
                promptBuilder.execute(*messages.toTypedArray())
            } catch (_: Exception) {
                promptBuilder.execute(fullPrompt)
            }

            buildJsonObject {
                put("response", response.content())
                put("message_count", messages.size)
                put("has_context", context != null)
            }
        } catch (e: UnsupportedOperationException) {
            buildJsonObject {
                put("error", "AI API is not available in this edition of Burp Suite")
                put("available", false)
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("error", "AI prompt failed: ${e.message}")
            }
        }
    }
}
