package com.burpmcp.ultra.tools.bcheck

import com.burpmcp.ultra.bridge.BCheckBridge
import com.burpmcp.ultra.core.asStringList
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*

/**
 * Registers all 5 BCheck MCP tools onto the given [Server].
 *
 * BChecks are Burp Suite's domain-specific language for custom vulnerability
 * scan checks. These tools allow an AI agent to create, import, list, and
 * manage BCheck scripts that run during passive and active scanning.
 */
object BCheckTools {

    fun register(server: Server, bridge: BCheckBridge) {

        // ---------------------------------------------------------------
        // 1. bcheck_create
        // ---------------------------------------------------------------
        server.addTool(
            name = "bcheck_create",
            description = "Generate and deploy a BCheck custom scan check from structured parameters. " +
                "BChecks are Burp Suite's DSL for defining custom vulnerability detection rules that run " +
                "during scanning. Parameters: name (required, check name), description (required, what it " +
                "detects), type (required, one of: passive_response, passive_request, insertion_point, " +
                "host_level, path_level, collaborator), match_pattern (regex/string to match), " +
                "match_location (where to match: response_body, response_headers, request_body, " +
                "request_headers, status_code), match_condition (matches, contains, or is), " +
                "payloads (array of payload strings for active checks), response_match_pattern " +
                "(what to look for in response after injection), collaborator_payload_type (dns or http), " +
                "severity (required, one of: high, medium, low, information), confidence (required, one of: " +
                "certain, firm, tentative), author (optional, defaults to BurpMCP-Ultra AI Agent), " +
                "tags (optional, comma-separated tags), issue_detail (detailed issue description), " +
                "issue_remediation (how to fix the issue). Use bcheck_templates first to understand " +
                "the BCheck DSL patterns before creating checks."
        ) { request ->
            try {
                val args = request.params.arguments ?: emptyMap()

                val name = args["name"]?.jsonPrimitive?.contentOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent(buildJsonObject {
                            put("error", "Missing required parameter: name")
                        }.toString())),
                        isError = true
                    )

                val description = args["description"]?.jsonPrimitive?.contentOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent(buildJsonObject {
                            put("error", "Missing required parameter: description")
                        }.toString())),
                        isError = true
                    )

                val type = args["type"]?.jsonPrimitive?.contentOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent(buildJsonObject {
                            put("error", "Missing required parameter: type (passive_response, passive_request, insertion_point, host_level, path_level, collaborator)")
                        }.toString())),
                        isError = true
                    )

                val severity = args["severity"]?.jsonPrimitive?.contentOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent(buildJsonObject {
                            put("error", "Missing required parameter: severity (high, medium, low, information)")
                        }.toString())),
                        isError = true
                    )

                val confidence = args["confidence"]?.jsonPrimitive?.contentOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent(buildJsonObject {
                            put("error", "Missing required parameter: confidence (certain, firm, tentative)")
                        }.toString())),
                        isError = true
                    )

                val matchPattern = args["match_pattern"]?.jsonPrimitive?.contentOrNull
                val matchLocation = args["match_location"]?.jsonPrimitive?.contentOrNull
                val matchCondition = args["match_condition"]?.jsonPrimitive?.contentOrNull
                val payloads = args["payloads"]?.asStringList()
                val responseMatchPattern = args["response_match_pattern"]?.jsonPrimitive?.contentOrNull
                val collaboratorPayloadType = args["collaborator_payload_type"]?.jsonPrimitive?.contentOrNull
                val author = args["author"]?.jsonPrimitive?.contentOrNull
                val tags = args["tags"]?.jsonPrimitive?.contentOrNull
                val issueDetail = args["issue_detail"]?.jsonPrimitive?.contentOrNull
                val issueRemediation = args["issue_remediation"]?.jsonPrimitive?.contentOrNull

                val result = bridge.create(
                    name = name,
                    description = description,
                    author = author,
                    tags = tags,
                    type = type,
                    matchPattern = matchPattern,
                    matchLocation = matchLocation,
                    matchCondition = matchCondition,
                    payloads = payloads,
                    responseMatchPattern = responseMatchPattern,
                    collaboratorPayloadType = collaboratorPayloadType,
                    severity = severity,
                    confidence = confidence,
                    issueDetail = issueDetail,
                    issueRemediation = issueRemediation
                )
                CallToolResult(content = listOf(TextContent(result.toString())))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent(buildJsonObject {
                        put("error", e.message ?: "Unknown error")
                    }.toString())),
                    isError = true
                )
            }
        }

        // ---------------------------------------------------------------
        // 2. bcheck_import
        // ---------------------------------------------------------------
        server.addTool(
            name = "bcheck_import",
            description = "Import a raw BCheck script directly into Burp Suite. Use this when you have " +
                "a complete BCheck script in BCheck DSL format. The script must include a metadata block " +
                "with language, name, and description fields, followed by the check logic. " +
                "Parameters: script (required, the full BCheck DSL script as a string). " +
                "Use bcheck_templates to see the correct syntax and available DSL constructs."
        ) { request ->
            try {
                val args = request.params.arguments ?: emptyMap()

                val script = args["script"]?.jsonPrimitive?.contentOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent(buildJsonObject {
                            put("error", "Missing required parameter: script")
                        }.toString())),
                        isError = true
                    )

                if (script.isBlank()) {
                    return@addTool CallToolResult(
                        content = listOf(TextContent(buildJsonObject {
                            put("error", "Script cannot be empty")
                        }.toString())),
                        isError = true
                    )
                }

                val result = bridge.importRaw(script)
                CallToolResult(content = listOf(TextContent(result.toString())))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent(buildJsonObject {
                        put("error", e.message ?: "Unknown error")
                    }.toString())),
                    isError = true
                )
            }
        }

        // ---------------------------------------------------------------
        // 3. bcheck_templates
        // ---------------------------------------------------------------
        server.addTool(
            name = "bcheck_templates",
            description = "Get all BCheck templates with full examples, DSL reference, and usage guidance. " +
                "Returns 7 templates covering all BCheck types: passive_response, passive_request, " +
                "insertion_point, host_level, path_level, collaborator, and multi_step. Each template " +
                "includes a complete working example, use cases, and descriptions. Also returns a " +
                "comprehensive DSL reference covering metadata fields, scope keywords, request operations, " +
                "conditions, variables, and reporting options. Call this BEFORE creating BChecks to " +
                "understand the correct syntax. No parameters required."
        ) { _ ->
            try {
                val result = bridge.getTemplates()
                CallToolResult(content = listOf(TextContent(result.toString())))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent(buildJsonObject {
                        put("error", e.message ?: "Unknown error")
                    }.toString())),
                    isError = true
                )
            }
        }

        // ---------------------------------------------------------------
        // 4. bcheck_list
        // ---------------------------------------------------------------
        server.addTool(
            name = "bcheck_list",
            description = "List all BChecks that have been deployed via MCP. Returns the count and details " +
                "of each deployed BCheck including its ID, name, type, deployment timestamp, and a " +
                "preview of the script. No parameters required."
        ) { _ ->
            try {
                val result = bridge.list()
                CallToolResult(content = listOf(TextContent(result.toString())))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent(buildJsonObject {
                        put("error", e.message ?: "Unknown error")
                    }.toString())),
                    isError = true
                )
            }
        }

        // ---------------------------------------------------------------
        // 5. bcheck_remove
        // ---------------------------------------------------------------
        server.addTool(
            name = "bcheck_remove",
            description = "Remove a deployed BCheck from MCP tracking by its ID. The ID is returned " +
                "when creating or importing a BCheck. Note: this removes the BCheck from MCP tracking; " +
                "to fully remove it from Burp Suite, manual removal in the Burp UI may be needed. " +
                "Parameters: id (required, the BCheck ID from bcheck_create or bcheck_import)."
        ) { request ->
            try {
                val args = request.params.arguments ?: emptyMap()

                val id = args["id"]?.jsonPrimitive?.contentOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent(buildJsonObject {
                            put("error", "Missing required parameter: id")
                        }.toString())),
                        isError = true
                    )

                val result = bridge.remove(id)
                CallToolResult(content = listOf(TextContent(result.toString())))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent(buildJsonObject {
                        put("error", e.message ?: "Unknown error")
                    }.toString())),
                    isError = true
                )
            }
        }
    }
}
