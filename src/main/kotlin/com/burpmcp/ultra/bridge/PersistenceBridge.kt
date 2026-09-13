package com.burpmcp.ultra.bridge

import burp.api.montoya.MontoyaApi
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Bridge wrapping the Montoya Persistence API.
 *
 * Since PersistedObject may not expose a listKeys() method, this bridge
 * maintains an internal shadow map of all keys stored through it. The key
 * list is itself persisted under the special key "_burpmcp_keys" so it
 * survives extension reloads.
 */
class PersistenceBridge(private val api: MontoyaApi) {

    companion object {
        private const val KEY_INDEX = "_burpmcp_keys"
        private const val KEY_SEPARATOR = "\u001F" // Unit separator
    }

    /** Shadow store mirroring what is in extensionData. */
    private val shadowStore = ConcurrentHashMap<String, String>()

    init {
        // Restore the key index from persistence on load
        try {
            val storedKeys = api.persistence().extensionData().getString(KEY_INDEX)
            if (storedKeys != null && storedKeys.isNotEmpty()) {
                storedKeys.split(KEY_SEPARATOR).filter { it.isNotEmpty() }.forEach { key ->
                    val value = api.persistence().extensionData().getString(key)
                    if (value != null) {
                        shadowStore[key] = value
                    }
                }
            }
        } catch (_: Exception) {
            // First load or corrupted index -- start fresh
        }
    }

    /**
     * Persists the current key index so it survives extension reloads.
     */
    private fun persistKeyIndex() {
        val keyList = shadowStore.keys().toList().joinToString(KEY_SEPARATOR)
        api.persistence().extensionData().setString(KEY_INDEX, keyList)
    }

    /**
     * Stores a string value under the given key.
     */
    fun store(key: String, value: String): JsonObject {
        api.persistence().extensionData().setString(key, value)
        shadowStore[key] = value
        persistKeyIndex()

        return buildJsonObject {
            put("status", "stored")
            put("key", key)
            put("value_length", value.length)
        }
    }

    /**
     * Retrieves a value by key.
     */
    fun get(key: String): JsonObject {
        val value = try {
            api.persistence().extensionData().getString(key)
        } catch (_: Exception) {
            null
        }

        return buildJsonObject {
            put("key", key)
            put("found", value != null)
            if (value != null) {
                put("value", value)
            }
        }
    }

    /**
     * Deletes a key from persistence.
     */
    fun delete(key: String): JsonObject {
        val existed = shadowStore.containsKey(key)
        api.persistence().extensionData().deleteString(key)
        shadowStore.remove(key)
        persistKeyIndex()

        return buildJsonObject {
            put("key", key)
            put("deleted", existed)
        }
    }

    /**
     * Lists all known keys and their values.
     */
    fun list(): JsonObject {
        val entries = buildJsonArray {
            for ((key, value) in shadowStore) {
                addJsonObject {
                    put("key", key)
                    put("value", value)
                }
            }
        }

        return buildJsonObject {
            put("count", shadowStore.size)
            put("entries", entries)
        }
    }

    /**
     * Stores a preference value (user-level, survives across projects).
     */
    fun preferenceStore(key: String, value: String): JsonObject {
        api.persistence().preferences().setString(key, value)

        return buildJsonObject {
            put("status", "stored")
            put("key", key)
            put("scope", "preferences")
            put("value_length", value.length)
        }
    }

    /**
     * Retrieves a preference value.
     */
    fun preferenceGet(key: String): JsonObject {
        val value = try {
            api.persistence().preferences().getString(key)
        } catch (_: Exception) {
            null
        }

        return buildJsonObject {
            put("key", key)
            put("scope", "preferences")
            put("found", value != null)
            if (value != null) {
                put("value", value)
            }
        }
    }
}
