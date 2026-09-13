package com.burpmcp.ultra.bridge

import burp.api.montoya.MontoyaApi
import kotlinx.serialization.json.*

/**
 * Bridge providing convenient shortcuts for common Burp Suite configuration
 * operations. All config changes are performed by exporting the current
 * project options as JSON, modifying the relevant section, and re-importing.
 */
class ConfigBridge(private val api: MontoyaApi) {

    /**
     * Lists all proxy listeners from the current project configuration.
     */
    fun listProxyListeners(): JsonObject {
        return try {
            val configJson = api.burpSuite().exportProjectOptionsAsJson("proxy.request_listeners")
            val config = Json.parseToJsonElement(configJson)
            val listeners = config.jsonObject["proxy"]
                ?.jsonObject?.get("request_listeners")
                ?.jsonArray ?: JsonArray(emptyList())

            buildJsonObject {
                put("count", listeners.size)
                put("listeners", listeners)
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("error", "Failed to list proxy listeners: ${e.message}")
            }
        }
    }

    /**
     * Adds a new proxy listener by modifying the project configuration.
     */
    fun addProxyListener(
        listenerInterface: String,
        tls: Boolean,
        redirectHost: String?,
        redirectPort: Int?,
        certificate: String?
    ): JsonObject {
        return try {
            val configJson = api.burpSuite().exportProjectOptionsAsJson("proxy.request_listeners")
            val config = Json.parseToJsonElement(configJson).jsonObject.toMutableMap()

            val proxyObj = config["proxy"]?.jsonObject?.toMutableMap()
                ?: mutableMapOf()
            val existingListeners = proxyObj["request_listeners"]
                ?.jsonArray?.toMutableList() ?: mutableListOf()

            // Build new listener entry
            val newListener = buildJsonObject {
                put("listener_interface", listenerInterface)
                put("running", true)
                put("certificate_mode", certificate ?: "per_host")
                if (tls) {
                    put("tls", true)
                }
                if (redirectHost != null) {
                    put("redirect_host", redirectHost)
                    put("redirect_port", redirectPort ?: 80)
                    put("redirect_to_host", true)
                    put("redirect_to_port", true)
                }
            }

            existingListeners.add(newListener)
            proxyObj["request_listeners"] = JsonArray(existingListeners)
            config["proxy"] = JsonObject(proxyObj)

            val modifiedConfig = JsonObject(config).toString()
            api.burpSuite().importProjectOptionsFromJson(modifiedConfig)

            buildJsonObject {
                put("status", "added")
                put("interface", listenerInterface)
                put("tls", tls)
                put("total_listeners", existingListeners.size)
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("error", "Failed to add proxy listener: ${e.message}")
            }
        }
    }

    /**
     * Removes a proxy listener matching the given interface string.
     */
    fun removeProxyListener(listenerInterface: String): JsonObject {
        return try {
            val configJson = api.burpSuite().exportProjectOptionsAsJson("proxy.request_listeners")
            val config = Json.parseToJsonElement(configJson).jsonObject.toMutableMap()

            val proxyObj = config["proxy"]?.jsonObject?.toMutableMap()
                ?: return buildJsonObject { put("error", "No proxy configuration found") }
            val existingListeners = proxyObj["request_listeners"]
                ?.jsonArray?.toMutableList() ?: mutableListOf()

            val sizeBefore = existingListeners.size
            existingListeners.removeAll { listener ->
                val iface = listener.jsonObject["listener_interface"]?.jsonPrimitive?.contentOrNull
                iface == listenerInterface
            }

            if (existingListeners.size == sizeBefore) {
                return buildJsonObject {
                    put("error", "No listener found with interface: $listenerInterface")
                }
            }

            proxyObj["request_listeners"] = JsonArray(existingListeners)
            config["proxy"] = JsonObject(proxyObj)

            val modifiedConfig = JsonObject(config).toString()
            api.burpSuite().importProjectOptionsFromJson(modifiedConfig)

            buildJsonObject {
                put("status", "removed")
                put("interface", listenerInterface)
                put("remaining_listeners", existingListeners.size)
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("error", "Failed to remove proxy listener: ${e.message}")
            }
        }
    }

    /**
     * Adds a match-and-replace rule to the proxy configuration.
     */
    fun addMatchReplaceRule(
        type: String,
        match: String,
        replace: String,
        comment: String?,
        enabled: Boolean
    ): JsonObject {
        return try {
            val configJson = api.burpSuite().exportProjectOptionsAsJson("proxy.match_replace_rules")
            val config = Json.parseToJsonElement(configJson).jsonObject.toMutableMap()

            val proxyObj = config["proxy"]?.jsonObject?.toMutableMap()
                ?: mutableMapOf()
            val existingRules = proxyObj["match_replace_rules"]
                ?.jsonArray?.toMutableList() ?: mutableListOf()

            val newRule = buildJsonObject {
                put("type", type)
                put("match", match)
                put("replace", replace)
                put("comment", comment ?: "")
                put("enabled", enabled)
                put("is_regex", false)
            }

            existingRules.add(newRule)
            proxyObj["match_replace_rules"] = JsonArray(existingRules)
            config["proxy"] = JsonObject(proxyObj)

            val modifiedConfig = JsonObject(config).toString()
            api.burpSuite().importProjectOptionsFromJson(modifiedConfig)

            buildJsonObject {
                put("status", "added")
                put("type", type)
                put("match", match)
                put("replace", replace)
                put("enabled", enabled)
                put("total_rules", existingRules.size)
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("error", "Failed to add match/replace rule: ${e.message}")
            }
        }
    }

    /**
     * Lists all match-and-replace rules from the proxy configuration.
     */
    fun listMatchReplaceRules(): JsonObject {
        return try {
            val configJson = api.burpSuite().exportProjectOptionsAsJson("proxy.match_replace_rules")
            val config = Json.parseToJsonElement(configJson)
            val rules = config.jsonObject["proxy"]
                ?.jsonObject?.get("match_replace_rules")
                ?.jsonArray ?: JsonArray(emptyList())

            buildJsonObject {
                put("count", rules.size)
                put("rules", buildJsonArray {
                    rules.forEachIndexed { idx, rule ->
                        addJsonObject {
                            put("index", idx)
                            for ((key, value) in rule.jsonObject) {
                                put(key, value)
                            }
                        }
                    }
                })
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("error", "Failed to list match/replace rules: ${e.message}")
            }
        }
    }

    /**
     * Removes a match-and-replace rule by its index.
     */
    fun removeMatchReplaceRule(index: Int): JsonObject {
        return try {
            val configJson = api.burpSuite().exportProjectOptionsAsJson("proxy.match_replace_rules")
            val config = Json.parseToJsonElement(configJson).jsonObject.toMutableMap()

            val proxyObj = config["proxy"]?.jsonObject?.toMutableMap()
                ?: return buildJsonObject { put("error", "No proxy configuration found") }
            val existingRules = proxyObj["match_replace_rules"]
                ?.jsonArray?.toMutableList() ?: mutableListOf()

            if (index < 0 || index >= existingRules.size) {
                return buildJsonObject {
                    put("error", "Invalid rule index: $index. Valid range: 0..${existingRules.size - 1}")
                }
            }

            val removed = existingRules.removeAt(index)
            proxyObj["match_replace_rules"] = JsonArray(existingRules)
            config["proxy"] = JsonObject(proxyObj)

            val modifiedConfig = JsonObject(config).toString()
            api.burpSuite().importProjectOptionsFromJson(modifiedConfig)

            buildJsonObject {
                put("status", "removed")
                put("removed_index", index)
                put("removed_rule", removed)
                put("remaining_rules", existingRules.size)
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("error", "Failed to remove match/replace rule: ${e.message}")
            }
        }
    }

    /**
     * Configures an upstream proxy server.
     */
    fun setUpstreamProxy(
        host: String,
        port: Int,
        proxyType: String,
        authUser: String?,
        authPass: String?,
        destinationHost: String?
    ): JsonObject {
        return try {
            val upstreamConfig = buildJsonObject {
                put("upstream_proxy", buildJsonObject {
                    putJsonArray("servers") {
                        addJsonObject {
                            put("proxy_host", host)
                            put("proxy_port", port)
                            put("proxy_type", proxyType.uppercase())
                            put("destination_host", destinationHost ?: "*")
                            put("enabled", true)
                            if (authUser != null) {
                                put("authentication", buildJsonObject {
                                    put("enabled", true)
                                    put("username", authUser)
                                    put("password", authPass ?: "")
                                })
                            }
                        }
                    }
                })
            }

            api.burpSuite().importProjectOptionsFromJson(upstreamConfig.toString())

            buildJsonObject {
                put("status", "configured")
                put("proxy_host", host)
                put("proxy_port", port)
                put("proxy_type", proxyType.uppercase())
                put("destination_host", destinationHost ?: "*")
                put("has_auth", authUser != null)
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("error", "Failed to set upstream proxy: ${e.message}")
            }
        }
    }
}
