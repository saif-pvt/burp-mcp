package com.burpmcp.ultra.core

import kotlinx.serialization.json.*

/**
 * Robust JSON argument extraction helpers for MCP tool parameters.
 *
 * MCP clients may send arguments in various forms:
 * - Proper JSON types (ideal): `{"urls": ["a", "b"]}`
 * - Double-serialized strings: `{"urls": "[\"a\", \"b\"]"}`
 * - Single values where arrays expected: `{"urls": "a"}`
 * - Stringified objects: `{"headers": "{\"Content-Type\": \"application/json\"}"}`
 *
 * These helpers handle all cases gracefully.
 */

private val json = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Extract a string list from a JsonElement that might be:
 * - A JsonArray of strings
 * - A JsonPrimitive containing a JSON array string
 * - A JsonPrimitive containing a single string (returned as single-element list)
 */
fun JsonElement?.asStringList(): List<String>? {
    if (this == null) return null
    return when (this) {
        is JsonArray -> this.mapNotNull {
            when (it) {
                is JsonPrimitive -> it.contentOrNull
                else -> it.toString()
            }
        }
        is JsonPrimitive -> {
            val str = this.contentOrNull ?: return null
            if (str.trimStart().startsWith("[")) {
                try {
                    json.parseToJsonElement(str).jsonArray.map { it.jsonPrimitive.content }
                } catch (_: Exception) {
                    listOf(str)
                }
            } else {
                listOf(str)
            }
        }
        is JsonObject -> listOf(this.toString())
        else -> null
    }
}

/**
 * Extract a list of JsonObject from a JsonElement that might be:
 * - A JsonArray of objects
 * - A JsonPrimitive containing a JSON array string
 */
fun JsonElement?.asJsonObjectList(): List<JsonObject>? {
    if (this == null) return null
    return when (this) {
        is JsonArray -> this.mapNotNull {
            when (it) {
                is JsonObject -> it
                is JsonPrimitive -> {
                    try { json.parseToJsonElement(it.content).jsonObject } catch (_: Exception) { null }
                }
                else -> null
            }
        }
        is JsonPrimitive -> {
            val str = this.contentOrNull ?: return null
            try {
                json.parseToJsonElement(str).jsonArray.mapNotNull {
                    when (it) {
                        is JsonObject -> it
                        else -> null
                    }
                }
            } catch (_: Exception) { null }
        }
        else -> null
    }
}

/**
 * Extract a JsonObject from a JsonElement that might be:
 * - A JsonObject directly
 * - A JsonPrimitive containing a JSON object string
 */
fun JsonElement?.asJsonObjectSafe(): JsonObject? {
    if (this == null) return null
    return when (this) {
        is JsonObject -> this
        is JsonPrimitive -> {
            val str = this.contentOrNull ?: return null
            if (str.trimStart().startsWith("{")) {
                try { json.parseToJsonElement(str).jsonObject } catch (_: Exception) { null }
            } else null
        }
        else -> null
    }
}

/**
 * Extract a Map<String, String> from a JsonElement that might be:
 * - A JsonObject with string values
 * - A JsonPrimitive containing a JSON object string
 */
fun JsonElement?.asStringMap(): Map<String, String>? {
    val obj = this.asJsonObjectSafe() ?: return null
    return obj.entries.associate { (k, v) ->
        k to (v.jsonPrimitive.contentOrNull ?: v.toString())
    }
}
