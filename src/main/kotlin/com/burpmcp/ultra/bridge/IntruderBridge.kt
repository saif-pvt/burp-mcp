package com.burpmcp.ultra.bridge

import burp.api.montoya.MontoyaApi
import burp.api.montoya.core.Range
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.http.HttpService
import burp.api.montoya.intruder.HttpRequestTemplate
import burp.api.montoya.intruder.PayloadProcessor
import burp.api.montoya.intruder.PayloadProcessingResult
import burp.api.montoya.intruder.PayloadData
import com.burpmcp.ultra.state.StateManager
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.Base64

class IntruderBridge(private val api: MontoyaApi, private val stateManager: StateManager) {

    fun sendToIntruder(request: String, host: String, port: Int, useTls: Boolean, tabName: String?): JsonObject {
        val httpService = HttpService.httpService(host, port, useTls)
        val httpRequest = HttpRequest.httpRequest(httpService, request)

        if (tabName != null) {
            api.intruder().sendToIntruder(httpRequest, tabName)
        } else {
            api.intruder().sendToIntruder(httpRequest)
        }

        return buildJsonObject {
            put("status", "sent_to_intruder")
            put("host", host)
            put("port", port)
            put("use_tls", useTls)
            put("tab_name", tabName ?: "auto")
        }
    }

    fun sendWithPositions(
        request: String,
        host: String,
        port: Int,
        useTls: Boolean,
        positions: List<Pair<Int, Int>>,
        tabName: String?
    ): JsonObject {
        val httpService = HttpService.httpService(host, port, useTls)
        val httpRequest = HttpRequest.httpRequest(httpService, request)

        val insertionPointOffsets = positions.map { (start, end) ->
            Range.range(start, end)
        }

        val template = HttpRequestTemplate.httpRequestTemplate(
            httpRequest,
            insertionPointOffsets
        )

        if (tabName != null) {
            api.intruder().sendToIntruder(httpService, template, tabName)
        } else {
            api.intruder().sendToIntruder(httpService, template)
        }

        return buildJsonObject {
            put("status", "sent_with_positions")
            put("host", host)
            put("port", port)
            put("use_tls", useTls)
            put("tab_name", tabName ?: "auto")
            put("insertion_points", positions.size)
            putJsonArray("positions") {
                for ((start, end) in positions) {
                    addJsonObject {
                        put("start", start)
                        put("end", end)
                    }
                }
            }
        }
    }

    fun registerPayloadProcessor(
        name: String,
        transformType: String,
        params: Map<String, String>
    ): JsonObject {
        val processor = object : PayloadProcessor {
            override fun displayName(): String = name

            override fun processPayload(payloadData: PayloadData): PayloadProcessingResult {
                val currentPayload = payloadData.currentPayload().toString()

                val transformed = when (transformType) {
                    "encode_base64" -> {
                        Base64.getEncoder().encodeToString(currentPayload.toByteArray())
                    }
                    "encode_url" -> {
                        api.utilities().urlUtils().encode(currentPayload)
                    }
                    "hash_md5" -> {
                        hashPayload(currentPayload, "MD5")
                    }
                    "hash_sha1" -> {
                        hashPayload(currentPayload, "SHA-1")
                    }
                    "hash_sha256" -> {
                        hashPayload(currentPayload, "SHA-256")
                    }
                    "prefix" -> {
                        val prefixValue = params["prefix_value"] ?: ""
                        prefixValue + currentPayload
                    }
                    "suffix" -> {
                        val suffixValue = params["suffix_value"] ?: ""
                        currentPayload + suffixValue
                    }
                    "regex_replace" -> {
                        val pattern = params["regex_pattern"] ?: ""
                        val replacement = params["regex_replacement"] ?: ""
                        currentPayload.replace(Regex(pattern), replacement)
                    }
                    else -> currentPayload
                }

                return PayloadProcessingResult.usePayload(
                    burp.api.montoya.core.ByteArray.byteArray(transformed)
                )
            }
        }

        api.intruder().registerPayloadProcessor(processor)
        stateManager.registeredPayloadProcessors.add(name)

        return buildJsonObject {
            put("status", "registered")
            put("name", name)
            put("transform_type", transformType)
            put("total_registered", stateManager.registeredPayloadProcessors.size)
        }
    }

    private fun hashPayload(input: String, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        val hashBytes = digest.digest(input.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
