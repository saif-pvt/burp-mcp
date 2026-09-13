package com.burpmcp.ultra.bridge

import com.burpmcp.ultra.state.SessionRule
import com.burpmcp.ultra.state.StateManager
import kotlinx.serialization.json.*

/**
 * Bridge for managing session handling rules that extract dynamic values
 * (e.g. CSRF tokens, session tokens) from responses and inject them into
 * subsequent requests.
 *
 * Rules are stored in [StateManager.sessionRules] and applied by the
 * global HTTP handler registered in [HttpBridge].
 */
class SessionBridge(private val stateManager: StateManager) {

    /**
     * Creates a new session token extraction/injection rule.
     */
    fun createTokenRule(
        ruleName: String,
        scope: String,
        scopePattern: String?,
        extractFrom: String,
        extractHeaderName: String?,
        extractRegex: String,
        injectInto: String,
        injectName: String,
        injectValueTemplate: String,
        enabled: Boolean
    ): JsonObject {
        // Check for duplicate name
        val existing = stateManager.sessionRules.find { it.ruleName == ruleName }
        if (existing != null) {
            return buildJsonObject {
                put("error", "A session rule named '$ruleName' already exists")
            }
        }

        // Validate scope
        val validScopes = listOf("all", "suite", "custom")
        if (scope.lowercase() !in validScopes) {
            return buildJsonObject {
                put("error", "Invalid scope: $scope. Must be one of: ${validScopes.joinToString(", ")}")
            }
        }

        // Validate extractFrom
        val validExtractFrom = listOf("header", "body")
        if (extractFrom.lowercase() !in validExtractFrom) {
            return buildJsonObject {
                put("error", "Invalid extract_from: $extractFrom. Must be one of: ${validExtractFrom.joinToString(", ")}")
            }
        }

        // Validate injectInto
        val validInjectInto = listOf("header", "body", "cookie")
        if (injectInto.lowercase() !in validInjectInto) {
            return buildJsonObject {
                put("error", "Invalid inject_into: $injectInto. Must be one of: ${validInjectInto.joinToString(", ")}")
            }
        }

        // Validate regex compiles
        try {
            Regex(extractRegex)
        } catch (e: Exception) {
            return buildJsonObject {
                put("error", "Invalid extract_regex: ${e.message}")
            }
        }

        val rule = SessionRule(
            ruleName = ruleName,
            scope = scope.lowercase(),
            scopePattern = scopePattern,
            extractFrom = extractFrom.lowercase(),
            extractHeaderName = extractHeaderName,
            extractRegex = extractRegex,
            injectInto = injectInto.lowercase(),
            injectName = injectName,
            injectValueTemplate = injectValueTemplate,
            enabled = enabled
        )

        stateManager.sessionRules.add(rule)

        return buildJsonObject {
            put("status", "created")
            put("rule_name", ruleName)
            put("scope", scope.lowercase())
            put("extract_from", extractFrom.lowercase())
            put("extract_regex", extractRegex)
            put("inject_into", injectInto.lowercase())
            put("inject_name", injectName)
            put("inject_value_template", injectValueTemplate)
            put("enabled", enabled)
            put("total_rules", stateManager.sessionRules.size)
        }
    }

    /**
     * Lists all session handling rules.
     */
    fun listRules(): JsonObject {
        val rules = buildJsonArray {
            for (rule in stateManager.sessionRules) {
                addJsonObject {
                    put("rule_name", rule.ruleName)
                    put("scope", rule.scope)
                    put("scope_pattern", rule.scopePattern ?: "")
                    put("extract_from", rule.extractFrom)
                    put("extract_header_name", rule.extractHeaderName ?: "")
                    put("extract_regex", rule.extractRegex)
                    put("inject_into", rule.injectInto)
                    put("inject_name", rule.injectName)
                    put("inject_value_template", rule.injectValueTemplate)
                    put("enabled", rule.enabled)
                    put("last_extracted_value", rule.lastExtractedValue ?: "(none)")
                }
            }
        }

        return buildJsonObject {
            put("count", stateManager.sessionRules.size)
            put("rules", rules)
        }
    }

    /**
     * Removes a session handling rule by name.
     */
    fun removeRule(ruleName: String): JsonObject {
        val removed = stateManager.sessionRules.removeIf { it.ruleName == ruleName }

        return if (removed) {
            buildJsonObject {
                put("status", "removed")
                put("rule_name", ruleName)
                put("remaining_rules", stateManager.sessionRules.size)
            }
        } else {
            buildJsonObject {
                put("error", "No session rule found with name: $ruleName")
            }
        }
    }
}
