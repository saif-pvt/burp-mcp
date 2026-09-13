package com.burpmcp.ultra.bridge

import burp.api.montoya.MontoyaApi
import kotlinx.serialization.json.*

/**
 * Bridge wrapping the Montoya Project API.
 *
 * Provides access to the current Burp Suite project metadata.
 */
class ProjectBridge(private val api: MontoyaApi) {

    /**
     * Returns information about the current Burp Suite project.
     */
    fun getInfo(): JsonObject {
        return try {
            val project = api.project()
            buildJsonObject {
                put("name", project.name())
                put("id", project.id())
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("error", "Failed to retrieve project info: ${e.message}")
            }
        }
    }
}
