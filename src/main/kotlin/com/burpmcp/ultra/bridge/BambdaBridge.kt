package com.burpmcp.ultra.bridge

import burp.api.montoya.MontoyaApi
import kotlinx.serialization.json.*

/**
 * Bridge wrapping the Montoya Bambda API.
 *
 * Bambda expressions are Java-like lambda expressions that can be used
 * to customize Burp Suite's behavior (e.g., proxy history filters,
 * HTTP match-and-replace rules).
 */
class BambdaBridge(private val api: MontoyaApi) {

    /**
     * Imports a Bambda script into Burp Suite.
     *
     * The script is a Java lambda expression string that Burp Suite will
     * compile and execute in the appropriate context.
     *
     * @param script The Bambda script content (Java lambda expression).
     * @return JSON object confirming the import or reporting an error.
     */
    fun importBambda(script: String): JsonObject {
        return try {
            api.bambda().importBambda(script)
            buildJsonObject {
                put("status", "imported")
                put("script_length", script.length)
            }
        } catch (e: UnsupportedOperationException) {
            buildJsonObject {
                put("error", "Bambda API is not available in this version of Burp Suite")
                put("available", false)
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("error", "Failed to import Bambda: ${e.message}")
                put("script_length", script.length)
            }
        }
    }
}
