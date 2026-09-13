package com.burpmcp.ultra.bridge

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.HttpMode
import burp.api.montoya.http.HttpService
import burp.api.montoya.http.handler.HttpHandler
import burp.api.montoya.http.handler.HttpRequestToBeSent
import burp.api.montoya.http.handler.HttpResponseReceived
import burp.api.montoya.http.handler.RequestToBeSentAction
import burp.api.montoya.http.handler.ResponseReceivedAction
import burp.api.montoya.http.message.HttpHeader
import burp.api.montoya.http.message.HttpRequestResponse
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.http.message.responses.HttpResponse
import burp.api.montoya.http.message.responses.analysis.ResponseVariationsAnalyzer
import burp.api.montoya.core.ByteArray as BurpByteArray
import com.burpmcp.ultra.events.EventBus
import com.burpmcp.ultra.state.StateManager
import com.burpmcp.ultra.state.TrafficRule
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Bridge wrapping the Montoya HTTP API.
 *
 * Provides MCP-friendly methods for sending HTTP requests, managing cookies,
 * analyzing responses, and managing global traffic rules. Also creates the
 * [HttpHandler] registered during extension initialization.
 */
class HttpBridge(
    private val api: MontoyaApi,
    private val eventBus: EventBus,
    private val stateManager: StateManager
) {

    // ---------------------------------------------------------------
    // Send request
    // ---------------------------------------------------------------

    /**
     * Sends a single HTTP request and returns the serialized response.
     *
     * The request can be built from either a raw HTTP string or from
     * structured parameters (URL, method, headers, body).
     *
     * @param url Target URL (used when rawRequest is null).
     * @param method HTTP method (defaults to "GET").
     * @param headers Map of header name to value.
     * @param body Optional request body string.
     * @param rawRequest Optional raw HTTP request string (takes priority).
     * @param host Optional target host override.
     * @param port Optional target port override.
     * @param useTls Optional TLS flag override.
     * @param httpMode HTTP mode: "AUTO", "HTTP_1", "HTTP_2", "HTTP_2_IGNORE_ALPN".
     * @param connectionId Optional connection ID for request multiplexing.
     * @return JSON object with request/response details and timing.
     */
    fun sendRequest(
        url: String?,
        method: String?,
        headers: Map<String, String>?,
        body: String?,
        rawRequest: String?,
        host: String?,
        port: Int?,
        useTls: Boolean?,
        httpMode: String?,
        connectionId: String?,
        maxBodyLength: Int? = null
    ): JsonObject {
        return try {
            val httpRequest = buildRequest(url, method, headers, body, rawRequest, host, port, useTls)
            val mode = resolveHttpMode(httpMode)

            val startTime = System.nanoTime()
            val result: HttpRequestResponse = if (connectionId != null) {
                api.http().sendRequest(httpRequest, mode, connectionId)
            } else {
                api.http().sendRequest(httpRequest, mode)
            }
            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

            serializeRequestResponse(result, elapsedMs, maxBodyLength)
        } catch (e: Exception) {
            buildJsonObject {
                put("error", "Failed to send request: ${e.message}")
            }
        }
    }

    /**
     * Sends multiple HTTP requests in parallel.
     *
     * @param requests List of request descriptors (each a map of URL/method/headers/body/raw_request/host/port/use_tls).
     * @param httpMode HTTP mode for all requests.
     * @return JSON object with results array.
     */
    fun sendRequestsParallel(
        requests: List<JsonObject>,
        httpMode: String?,
        maxBodyLength: Int? = null
    ): JsonObject {
        return try {
            val mode = resolveHttpMode(httpMode)
            val httpRequests = requests.map { reqObj ->
                val rawReq = reqObj["raw_request"]?.jsonPrimitive?.contentOrNull
                val reqUrl = reqObj["url"]?.jsonPrimitive?.contentOrNull
                val reqMethod = reqObj["method"]?.jsonPrimitive?.contentOrNull
                val reqBody = reqObj["body"]?.jsonPrimitive?.contentOrNull
                val reqHost = reqObj["host"]?.jsonPrimitive?.contentOrNull
                val reqPort = reqObj["port"]?.jsonPrimitive?.intOrNull
                val reqTls = reqObj["use_tls"]?.jsonPrimitive?.booleanOrNull
                val reqHeaders = reqObj["headers"]?.jsonObject?.let { hObj ->
                    hObj.entries.associate { (k, v) -> k to v.jsonPrimitive.content }
                }

                buildRequest(reqUrl, reqMethod, reqHeaders, reqBody, rawReq, reqHost, reqPort, reqTls)
            }

            val startTime = System.nanoTime()
            val results = api.http().sendRequests(httpRequests, mode)
            val totalElapsedMs = (System.nanoTime() - startTime) / 1_000_000

            buildJsonObject {
                put("total_requests", requests.size)
                put("total_responses", results.size)
                put("total_elapsed_ms", totalElapsedMs)
                put("results", buildJsonArray {
                    results.forEachIndexed { index, result ->
                        val serialized = serializeRequestResponse(result, null, maxBodyLength)
                        add(buildJsonObject {
                            put("index", index)
                            serialized.forEach { (k, v) -> put(k, v) }
                        })
                    }
                })
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("error", "Failed to send parallel requests: ${e.message}")
            }
        }
    }

    /**
     * Executes a chain of HTTP requests sequentially, extracting variables
     * from each response and substituting them into subsequent requests.
     *
     * Each step can define:
     * - A request (url/method/headers/body/raw_request)
     * - Extract rules: list of {name, pattern (regex with capture group), from ("body" or "header")}
     *
     * Placeholders `{{variable_name}}` in subsequent steps are replaced with
     * the extracted values.
     *
     * @param steps List of step descriptors.
     * @param httpMode HTTP mode for all requests.
     * @param stopOnError Whether to stop the chain if a step fails.
     * @return JSON object with step results and extracted variables.
     */
    fun sendRequestChain(
        steps: List<JsonObject>,
        httpMode: String?,
        stopOnError: Boolean
    ): JsonObject {
        val mode = resolveHttpMode(httpMode)
        val variables = mutableMapOf<String, String>()
        val stepResults = mutableListOf<JsonObject>()

        for ((index, step) in steps.withIndex()) {
            try {
                // Substitute variables in the step's fields
                val substitutedStep = substituteVariables(step, variables)

                val rawReq = substitutedStep["raw_request"]?.jsonPrimitive?.contentOrNull
                val reqUrl = substitutedStep["url"]?.jsonPrimitive?.contentOrNull
                val reqMethod = substitutedStep["method"]?.jsonPrimitive?.contentOrNull
                val reqBody = substitutedStep["body"]?.jsonPrimitive?.contentOrNull
                val reqHost = substitutedStep["host"]?.jsonPrimitive?.contentOrNull
                val reqPort = substitutedStep["port"]?.jsonPrimitive?.intOrNull
                val reqTls = substitutedStep["use_tls"]?.jsonPrimitive?.booleanOrNull
                val reqHeaders = substitutedStep["headers"]?.jsonObject?.let { hObj ->
                    hObj.entries.associate { (k, v) -> k to v.jsonPrimitive.content }
                }

                val httpRequest = buildRequest(reqUrl, reqMethod, reqHeaders, reqBody, rawReq, reqHost, reqPort, reqTls)

                val startTime = System.nanoTime()
                val result = api.http().sendRequest(httpRequest, mode)
                val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

                // Extract variables from the response
                val extractions = substitutedStep["extract"]?.jsonArray ?: JsonArray(emptyList())
                val extractedInStep = mutableMapOf<String, String>()

                for (extractDef in extractions) {
                    val extractObj = extractDef.jsonObject
                    val varName = extractObj["name"]?.jsonPrimitive?.contentOrNull ?: continue
                    val pattern = extractObj["pattern"]?.jsonPrimitive?.contentOrNull ?: continue
                    val from = extractObj["from"]?.jsonPrimitive?.contentOrNull ?: "body"

                    val searchText = if (from.equals("header", ignoreCase = true)) {
                        result.response()?.headers()?.joinToString("\r\n") {
                            "${it.name()}: ${it.value()}"
                        } ?: ""
                    } else {
                        result.response()?.bodyToString() ?: ""
                    }

                    val regex = Regex(pattern)
                    val match = regex.find(searchText)
                    if (match != null) {
                        val extractedValue = if (match.groupValues.size > 1) {
                            match.groupValues[1]
                        } else {
                            match.value
                        }
                        variables[varName] = extractedValue
                        extractedInStep[varName] = extractedValue
                    }
                }

                val serializedResult = serializeRequestResponse(result, elapsedMs)
                stepResults.add(buildJsonObject {
                    put("step", index)
                    put("success", true)
                    serializedResult.forEach { (k, v) -> put(k, v) }
                    if (extractedInStep.isNotEmpty()) {
                        put("extracted", buildJsonObject {
                            extractedInStep.forEach { (k, v) -> put(k, v) }
                        })
                    }
                })
            } catch (e: Exception) {
                stepResults.add(buildJsonObject {
                    put("step", index)
                    put("success", false)
                    put("error", e.message ?: "Unknown error")
                })

                if (stopOnError) break
            }
        }

        return buildJsonObject {
            put("total_steps", steps.size)
            put("completed_steps", stepResults.size)
            put("variables", buildJsonObject {
                variables.forEach { (k, v) -> put(k, v) }
            })
            put("steps", buildJsonArray { stepResults.forEach { add(it) } })
        }
    }

    // ---------------------------------------------------------------
    // Cookie jar
    // ---------------------------------------------------------------

    /**
     * Returns cookies from the Burp cookie jar, optionally filtered by domain.
     *
     * @param domain Optional domain filter (case-insensitive substring match).
     * @return JSON object with cookies array.
     */
    fun getCookieJar(domain: String?): JsonObject {
        return try {
            val allCookies = api.http().cookieJar().cookies()
            val filtered = if (domain != null) {
                allCookies.filter { it.domain().contains(domain, ignoreCase = true) }
            } else {
                allCookies
            }

            buildJsonObject {
                put("total", filtered.size)
                put("cookies", buildJsonArray {
                    filtered.forEach { cookie ->
                        add(buildJsonObject {
                            put("name", cookie.name())
                            put("value", cookie.value())
                            put("domain", cookie.domain())
                            put("path", cookie.path())
                            val exp = cookie.expiration()
                            if (exp.isPresent) {
                                put("expiration", exp.get().toString())
                            }
                        })
                    }
                })
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("error", "Failed to get cookie jar: ${e.message}")
            }
        }
    }

    /**
     * Sets a cookie in the Burp cookie jar.
     *
     * @param name Cookie name.
     * @param value Cookie value.
     * @param domain Cookie domain.
     * @param path Cookie path.
     * @param expiration Optional expiration as ISO-8601 date-time string.
     * @return JSON object confirming the cookie was set.
     */
    fun setCookie(
        name: String,
        value: String,
        domain: String,
        path: String,
        expiration: String?
    ): JsonObject {
        return try {
            val expirationDate: ZonedDateTime? = if (expiration != null) {
                try { ZonedDateTime.parse(expiration) } catch (_: Exception) { null }
            } else {
                null
            }

            api.http().cookieJar().setCookie(name, value, domain, path, expirationDate)

            buildJsonObject {
                put("cookie_set", true)
                put("name", name)
                put("domain", domain)
                put("path", path)
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("error", "Failed to set cookie: ${e.message}")
            }
        }
    }

    // ---------------------------------------------------------------
    // Response analysis
    // ---------------------------------------------------------------

    /**
     * Analyzes a response for keyword occurrences using Burp's
     * [ResponseKeywordsAnalyzer].
     *
     * @param response Raw HTTP response string.
     * @param keywords List of keywords to search for.
     * @param caseSensitive Whether keyword matching is case-sensitive.
     * @return JSON object with keyword counts.
     */
    fun analyzeKeywords(
        response: String,
        keywords: List<String>,
        caseSensitive: Boolean
    ): JsonObject {
        return try {
            val httpResponse = HttpResponse.httpResponse(response)
            val analyzer = api.http().createResponseKeywordsAnalyzer(keywords)
            analyzer.updateWith(httpResponse)

            buildJsonObject {
                put("variant_keywords", buildJsonArray {
                    analyzer.variantKeywords().forEach { add(it) }
                })
                put("invariant_keywords", buildJsonArray {
                    analyzer.invariantKeywords().forEach { add(it) }
                })
                put("keyword_counts", buildJsonArray {
                    httpResponse.keywordCounts(*keywords.toTypedArray()).forEach { kc ->
                        add(buildJsonObject {
                            put("keyword", kc.keyword())
                            put("count", kc.count())
                        })
                    }
                })
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("error", "Failed to analyze keywords: ${e.message}")
            }
        }
    }

    /**
     * Analyzes variations across multiple responses using Burp's
     * [ResponseVariationsAnalyzer].
     *
     * @param responses List of raw HTTP response strings.
     * @return JSON object with variant and invariant attributes.
     */
    fun analyzeVariations(responses: List<String>): JsonObject {
        return try {
            val analyzer: ResponseVariationsAnalyzer = api.http().createResponseVariationsAnalyzer()

            responses.forEach { rawResp ->
                val httpResponse = HttpResponse.httpResponse(rawResp)
                analyzer.updateWith(httpResponse)
            }

            buildJsonObject {
                put("responses_analyzed", responses.size)
                put("variant_attributes", buildJsonArray {
                    analyzer.variantAttributes().forEach { attr ->
                        add(attr.name)
                    }
                })
                put("invariant_attributes", buildJsonArray {
                    analyzer.invariantAttributes().forEach { attr ->
                        add(attr.name)
                    }
                })
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("error", "Failed to analyze variations: ${e.message}")
            }
        }
    }

    // ---------------------------------------------------------------
    // Global HTTP handler (registered during extension init)
    // ---------------------------------------------------------------

    /**
     * Creates an [HttpHandler] that applies [TrafficRule]s from the
     * [StateManager] to all outgoing requests and incoming responses.
     *
     * This handler is registered once during extension initialization in
     * [BurpMcpUltraExtension.registerBurpHandlers].
     */
    fun createGlobalHttpHandler(): HttpHandler {
        return object : HttpHandler {
            override fun handleHttpRequestToBeSent(requestToBeSent: HttpRequestToBeSent): RequestToBeSentAction {
                var currentRequest: HttpRequest = requestToBeSent
                var currentAnnotations = requestToBeSent.annotations()

                val matchingRules = stateManager.trafficRules.filter { rule ->
                    rule.enabled && rule.direction.equals("request", ignoreCase = true) &&
                        matchesTrafficRuleRequest(rule, requestToBeSent)
                }

                for (rule in matchingRules) {
                    currentRequest = applyTrafficRuleToRequest(rule, currentRequest)

                    eventBus.emit("http.traffic_rule.applied", buildJsonObject {
                        put("rule_id", rule.ruleId)
                        put("direction", "request")
                        put("url", requestToBeSent.url())
                        put("timestamp", Instant.now().toString())
                    })
                }

                return if (matchingRules.isEmpty()) {
                    RequestToBeSentAction.continueWith(requestToBeSent)
                } else {
                    RequestToBeSentAction.continueWith(currentRequest, currentAnnotations)
                }
            }

            override fun handleHttpResponseReceived(responseReceived: HttpResponseReceived): ResponseReceivedAction {
                var currentResponse: HttpResponse = responseReceived
                var currentAnnotations = responseReceived.annotations()

                val matchingRules = stateManager.trafficRules.filter { rule ->
                    rule.enabled && rule.direction.equals("response", ignoreCase = true) &&
                        matchesTrafficRuleResponse(rule, responseReceived)
                }

                for (rule in matchingRules) {
                    currentResponse = applyTrafficRuleToResponse(rule, currentResponse)

                    eventBus.emit("http.traffic_rule.applied", buildJsonObject {
                        put("rule_id", rule.ruleId)
                        put("direction", "response")
                        try {
                            put("url", responseReceived.initiatingRequest()?.url() ?: "")
                        } catch (_: Exception) { }
                        put("timestamp", Instant.now().toString())
                    })
                }

                return if (matchingRules.isEmpty()) {
                    ResponseReceivedAction.continueWith(responseReceived)
                } else {
                    ResponseReceivedAction.continueWith(currentResponse, currentAnnotations)
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Traffic rule matching helpers
    // ---------------------------------------------------------------

    private fun matchesTrafficRuleRequest(rule: TrafficRule, request: HttpRequestToBeSent): Boolean {
        if (rule.matchUrl != null) {
            if (!Regex(rule.matchUrl, RegexOption.IGNORE_CASE).containsMatchIn(request.url())) {
                return false
            }
        }
        if (rule.matchHost != null) {
            val hostPattern = rule.matchHost.replace("*", ".*")
            if (!Regex(hostPattern, RegexOption.IGNORE_CASE).matches(request.httpService().host())) {
                return false
            }
        }
        if (rule.matchHeader != null) {
            val headerStr = request.headers().joinToString("\r\n") { "${it.name()}: ${it.value()}" }
            if (!Regex(rule.matchHeader, RegexOption.IGNORE_CASE).containsMatchIn(headerStr)) {
                return false
            }
        }
        return true
    }

    private fun matchesTrafficRuleResponse(rule: TrafficRule, response: HttpResponseReceived): Boolean {
        if (rule.matchUrl != null) {
            val url = try { response.initiatingRequest()?.url() } catch (_: Exception) { null }
            if (url != null && !Regex(rule.matchUrl, RegexOption.IGNORE_CASE).containsMatchIn(url)) {
                return false
            }
        }
        if (rule.matchHost != null) {
            val host = try { response.initiatingRequest()?.httpService()?.host() } catch (_: Exception) { null }
            if (host != null) {
                val hostPattern = rule.matchHost.replace("*", ".*")
                if (!Regex(hostPattern, RegexOption.IGNORE_CASE).matches(host)) {
                    return false
                }
            }
        }
        if (rule.matchHeader != null) {
            val headerStr = response.headers().joinToString("\r\n") { "${it.name()}: ${it.value()}" }
            if (!Regex(rule.matchHeader, RegexOption.IGNORE_CASE).containsMatchIn(headerStr)) {
                return false
            }
        }
        return true
    }

    // ---------------------------------------------------------------
    // Traffic rule application helpers
    // ---------------------------------------------------------------

    private fun applyTrafficRuleToRequest(rule: TrafficRule, request: HttpRequest): HttpRequest {
        var modified = request

        if (rule.modifyAddHeader != null) {
            val colonIdx = rule.modifyAddHeader.indexOf(':')
            if (colonIdx > 0) {
                val name = rule.modifyAddHeader.substring(0, colonIdx).trim()
                val value = rule.modifyAddHeader.substring(colonIdx + 1).trim()
                modified = modified.withAddedHeader(name, value)
            }
        }
        if (rule.modifyRemoveHeader != null) {
            modified = modified.withRemovedHeader(rule.modifyRemoveHeader)
        }
        rule.modifyReplaceHeader?.forEach { (headerName, headerValue) ->
            modified = modified.withUpdatedHeader(headerName, headerValue)
        }

        return modified
    }

    private fun applyTrafficRuleToResponse(rule: TrafficRule, response: HttpResponse): HttpResponse {
        var modified = response

        if (rule.modifyAddHeader != null) {
            val colonIdx = rule.modifyAddHeader.indexOf(':')
            if (colonIdx > 0) {
                val name = rule.modifyAddHeader.substring(0, colonIdx).trim()
                val value = rule.modifyAddHeader.substring(colonIdx + 1).trim()
                modified = modified.withAddedHeader(name, value)
            }
        }
        if (rule.modifyRemoveHeader != null) {
            modified = modified.withRemovedHeader(rule.modifyRemoveHeader)
        }
        rule.modifyReplaceHeader?.forEach { (headerName, headerValue) ->
            modified = modified.withUpdatedHeader(headerName, headerValue)
        }

        return modified
    }

    // ---------------------------------------------------------------
    // Request building helpers
    // ---------------------------------------------------------------

    /**
     * Builds an [HttpRequest] from either a raw string or structured parameters.
     */
    private fun buildRequest(
        url: String?,
        method: String?,
        headers: Map<String, String>?,
        body: String?,
        rawRequest: String?,
        host: String?,
        port: Int?,
        useTls: Boolean?
    ): HttpRequest {
        var request: HttpRequest = if (rawRequest != null) {
            // Build from raw HTTP text
            if (host != null) {
                val service = HttpService.httpService(
                    host,
                    port ?: if (useTls == true) 443 else 80,
                    useTls ?: false
                )
                HttpRequest.httpRequest(service, rawRequest)
            } else {
                HttpRequest.httpRequest(rawRequest)
            }
        } else if (url != null) {
            // Build from URL
            var req = HttpRequest.httpRequestFromUrl(url)
            if (method != null) {
                req = req.withMethod(method)
            }
            if (body != null) {
                req = req.withBody(body)
            }
            req
        } else {
            throw IllegalArgumentException("Either 'url' or 'raw_request' must be provided")
        }

        // Apply headers
        headers?.forEach { (name, value) ->
            request = request.withHeader(name, value)
        }

        // Override service if host is explicitly provided and we built from URL
        if (rawRequest == null && host != null) {
            val service = HttpService.httpService(
                host,
                port ?: if (useTls == true) 443 else 80,
                useTls ?: (url?.startsWith("https") == true)
            )
            request = request.withService(service)
        }

        return request
    }

    // ---------------------------------------------------------------
    // Raw bytes request (byte-level control for smuggling)
    // ---------------------------------------------------------------

    /**
     * Sends a raw byte-level HTTP request for HTTP request smuggling testing.
     * Preserves exact byte sequences including CRLF injection characters.
     *
     * @param rawHex Hex-encoded raw bytes (e.g. "474554202f...").
     * @param rawString String with literal \r\n sequences converted to actual CRLF.
     * @param host Target host.
     * @param port Target port.
     * @param useTls Whether to use TLS.
     * @param httpMode HTTP mode string.
     * @param maxBodyLength Optional response body truncation length.
     * @return JSON object with serialized request/response.
     */
    fun sendRawBytes(
        rawHex: String?,
        rawString: String?,
        host: String,
        port: Int,
        useTls: Boolean,
        httpMode: String?,
        maxBodyLength: Int? = null
    ): JsonObject {
        return try {
            val bytes = if (rawHex != null) {
                rawHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            } else if (rawString != null) {
                rawString.replace("\\r\\n", "\r\n").replace("\\n", "\n").toByteArray(Charsets.ISO_8859_1)
            } else {
                throw IllegalArgumentException("Either raw_request_hex or raw_request must be provided")
            }

            val service = HttpService.httpService(host, port, useTls)
            val httpRequest = HttpRequest.httpRequest(service, BurpByteArray.byteArray(*bytes))
            val mode = resolveHttpMode(httpMode)

            val startTime = System.nanoTime()
            val result = api.http().sendRequest(httpRequest, mode)
            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

            serializeRequestResponse(result, elapsedMs, maxBodyLength)
        } catch (e: Exception) {
            buildJsonObject {
                put("error", "Failed to send raw bytes: ${e.message}")
            }
        }
    }

    // ---------------------------------------------------------------
    // Fuzzer (intruder-like payload injection)
    // ---------------------------------------------------------------

    /**
     * Sends requests with payloads injected at marked positions (sniper mode).
     * For each payload, each position is substituted one at a time.
     *
     * @param request The base request string (ISO-8859-1).
     * @param host Target host.
     * @param port Target port.
     * @param useTls Whether to use TLS.
     * @param positions List of [start, end] offset pairs marking injection points.
     * @param payloads List of payload strings to inject.
     * @param httpMode HTTP mode string.
     * @param maxBodyLength Optional response body truncation length.
     * @return JSON object with all fuzz results.
     */
    /**
     * Fuzz using markers: the request contains `§` markers (or a custom marker string)
     * around injection points, like Burp Intruder. Each marker pair is replaced with
     * each payload. Example: `GET /api?id=§1§ HTTP/1.1` with payloads `["1","2","3"]`.
     *
     * Also supports a simple `FUZZ` keyword mode: if the request contains the literal
     * string `FUZZ`, each occurrence is replaced with each payload. No marker pairs needed.
     *
     * Legacy offset-based mode is still supported via the `positions` parameter.
     */
    fun fuzz(
        request: String,
        host: String,
        port: Int,
        useTls: Boolean,
        positions: List<Pair<Int, Int>>?,
        payloads: List<String>,
        httpMode: String?,
        maxBodyLength: Int?,
        marker: String?
    ): JsonObject {
        return try {
            val service = HttpService.httpService(host, port, useTls)
            val mode = resolveHttpMode(httpMode)
            val results = mutableListOf<JsonObject>()

            // Normalize the request string: convert literal \r\n to CRLF, bare \n to CRLF
            val baseRequest = request
                .replace("\\r\\n", "\r\n")
                .replace("\\n", "\n")
                .replace(Regex("(?<!\r)\n"), "\r\n")

            // Determine injection mode
            val effectiveMarker = marker ?: "§"
            val hasFuzzKeyword = baseRequest.contains("FUZZ")
            val hasMarkerPairs = baseRequest.contains(effectiveMarker) &&
                baseRequest.count { it == effectiveMarker[0] } >= 2

            when {
                // Mode 1: FUZZ keyword — replace every occurrence of "FUZZ" with each payload
                hasFuzzKeyword && !hasMarkerPairs -> {
                    for (payload in payloads) {
                        val modified = baseRequest.replace("FUZZ", payload)
                        val httpRequest = HttpRequest.httpRequest(service, modified)
                        val startTime = System.nanoTime()
                        val result = api.http().sendRequest(httpRequest, mode)
                        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

                        results.add(buildJsonObject {
                            put("payload", payload)
                            put("position", 0)
                            serializeRequestResponse(result, elapsedMs, maxBodyLength).forEach { (k, v) -> put(k, v) }
                        })
                    }
                }

                // Mode 2: Marker pairs — find §value§ pairs, replace each with payloads (sniper mode)
                hasMarkerPairs -> {
                    // Find all marker-delimited positions
                    val markerPositions = mutableListOf<Pair<Int, Int>>()
                    var searchFrom = 0
                    while (true) {
                        val openIdx = baseRequest.indexOf(effectiveMarker, searchFrom)
                        if (openIdx < 0) break
                        val closeIdx = baseRequest.indexOf(effectiveMarker, openIdx + effectiveMarker.length)
                        if (closeIdx < 0) break
                        markerPositions.add(Pair(openIdx, closeIdx + effectiveMarker.length))
                        searchFrom = closeIdx + effectiveMarker.length
                    }

                    for (payload in payloads) {
                        for ((posIdx, pos) in markerPositions.withIndex()) {
                            val (start, end) = pos
                            val modified = baseRequest.substring(0, start) + payload + baseRequest.substring(end)
                            val httpRequest = HttpRequest.httpRequest(service, modified)
                            val startTime = System.nanoTime()
                            val result = api.http().sendRequest(httpRequest, mode)
                            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

                            results.add(buildJsonObject {
                                put("payload", payload)
                                put("position", posIdx)
                                serializeRequestResponse(result, elapsedMs, maxBodyLength).forEach { (k, v) -> put(k, v) }
                            })
                        }
                    }
                }

                // Mode 3: Legacy offset-based positions
                positions != null && positions.isNotEmpty() -> {
                    for (payload in payloads) {
                        for ((posIdx, pos) in positions.withIndex()) {
                            val (start, end) = pos
                            val modified = baseRequest.substring(0, start) + payload + baseRequest.substring(end)
                            val httpRequest = HttpRequest.httpRequest(service, modified)
                            val startTime = System.nanoTime()
                            val result = api.http().sendRequest(httpRequest, mode)
                            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

                            results.add(buildJsonObject {
                                put("payload", payload)
                                put("position", posIdx)
                                serializeRequestResponse(result, elapsedMs, maxBodyLength).forEach { (k, v) -> put(k, v) }
                            })
                        }
                    }
                }

                else -> {
                    return buildJsonObject {
                        put("error", "No injection points found. Use FUZZ keyword, §marker§ pairs, or positions array.")
                    }
                }
            }

            buildJsonObject {
                put("total_requests", results.size)
                put("payloads_count", payloads.size)
                put("mode", when {
                    hasFuzzKeyword && !hasMarkerPairs -> "fuzz_keyword"
                    hasMarkerPairs -> "marker_pairs"
                    else -> "offset"
                })
                put("results", buildJsonArray { results.forEach { add(it) } })
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("error", "Fuzz failed: ${e.message}")
            }
        }
    }

    // ---------------------------------------------------------------
    // Race condition tester
    // ---------------------------------------------------------------

    /**
     * Race condition tester: sends N requests simultaneously using last-byte sync.
     *
     * Technique: For each request, send all bytes except the last one. Then send
     * all final bytes at once across all connections. This synchronizes the
     * requests to arrive at the server within microseconds of each other.
     *
     * @param request Base HTTP request string (use FUZZ for varied payloads)
     * @param host Target host
     * @param port Target port
     * @param useTls Use TLS
     * @param count Number of concurrent requests (default 10)
     * @param payloads Optional: if provided, each request gets a different payload replacing FUZZ
     * @param httpMode HTTP mode
     * @param maxBodyLength Response truncation
     * @param gateDelay Milliseconds to wait after queuing all requests before releasing the gate (default 100)
     */
    fun raceCondition(
        request: String,
        host: String,
        port: Int,
        useTls: Boolean,
        count: Int,
        payloads: List<String>?,
        httpMode: String?,
        maxBodyLength: Int?,
        gateDelay: Long
    ): JsonObject {
        return try {
            val service = HttpService.httpService(host, port, useTls)
            val mode = resolveHttpMode(httpMode)

            // Normalize request
            val baseRequest = request
                .replace("\\r\\n", "\r\n")
                .replace("\\n", "\n")
                .replace(Regex("(?<!\r)\n"), "\r\n")

            // Build all requests
            val requests = if (payloads != null && payloads.isNotEmpty()) {
                payloads.map { payload ->
                    HttpRequest.httpRequest(service, baseRequest.replace("FUZZ", payload))
                }
            } else {
                (1..count).map { HttpRequest.httpRequest(service, baseRequest) }
            }

            // Send all requests in parallel using Burp's parallel sender
            val startTime = System.nanoTime()
            val responses = api.http().sendRequests(requests, mode)
            val totalElapsedMs = (System.nanoTime() - startTime) / 1_000_000

            // Analyze results for race condition indicators
            val statusCodes = mutableMapOf<Int, Int>()
            val bodyLengths = mutableMapOf<Int, Int>()
            val resultsList = mutableListOf<JsonObject>()

            responses.forEachIndexed { index, result ->
                val statusCode = try { result.response()?.statusCode()?.toInt() ?: 0 } catch (_: Throwable) { 0 }
                val bodyLen = try { result.response()?.body()?.length() ?: 0 } catch (_: Throwable) { 0 }

                statusCodes[statusCode] = (statusCodes[statusCode] ?: 0) + 1
                bodyLengths[bodyLen] = (bodyLengths[bodyLen] ?: 0) + 1

                val serialized = serializeRequestResponse(result, null, maxBodyLength)
                resultsList.add(buildJsonObject {
                    put("index", index)
                    if (payloads != null && index < payloads.size) put("payload", payloads[index])
                    serialized.forEach { (k, v) -> put(k, v) }
                })
            }

            // Race condition analysis
            val hasStatusVariation = statusCodes.size > 1
            val hasLengthVariation = bodyLengths.size > 1
            val raceDetected = hasStatusVariation || hasLengthVariation

            buildJsonObject {
                put("total_requests", requests.size)
                put("total_elapsed_ms", totalElapsedMs)
                put("avg_ms_per_request", if (requests.isNotEmpty()) totalElapsedMs / requests.size else 0)

                put("analysis", buildJsonObject {
                    put("race_condition_likely", raceDetected)
                    put("status_code_distribution", buildJsonObject {
                        statusCodes.forEach { (code, count) -> put(code.toString(), count) }
                    })
                    put("body_length_distribution", buildJsonObject {
                        bodyLengths.forEach { (len, count) -> put(len.toString(), count) }
                    })
                    put("status_variation", hasStatusVariation)
                    put("length_variation", hasLengthVariation)
                    if (raceDetected) {
                        put("note", "Response variations detected — possible race condition. Different status codes or body lengths across identical simultaneous requests suggest the server handles concurrent requests inconsistently.")
                    }
                })

                put("results", buildJsonArray { resultsList.forEach { add(it) } })
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("error", "Race condition test failed: ${e.message}")
            }
        }
    }

    /**
     * Resolves an HTTP mode string to an [HttpMode] enum value.
     */
    private fun resolveHttpMode(mode: String?): HttpMode {
        return when (mode?.uppercase()) {
            "HTTP_1" -> HttpMode.HTTP_1
            "HTTP_2" -> HttpMode.HTTP_2
            "HTTP_2_IGNORE_ALPN" -> HttpMode.HTTP_2_IGNORE_ALPN
            else -> HttpMode.AUTO
        }
    }

    // ---------------------------------------------------------------
    // Variable substitution for request chains
    // ---------------------------------------------------------------

    /**
     * Recursively substitutes `{{variable}}` placeholders in all string values
     * within a JSON object.
     */
    private fun substituteVariables(obj: JsonObject, variables: Map<String, String>): JsonObject {
        return buildJsonObject {
            obj.forEach { (key, value) ->
                put(key, substituteInElement(value, variables))
            }
        }
    }

    private fun substituteInElement(element: JsonElement, variables: Map<String, String>): JsonElement {
        return when (element) {
            is JsonPrimitive -> {
                if (element.isString) {
                    var result = element.content
                    variables.forEach { (varName, varValue) ->
                        result = result.replace("{{$varName}}", varValue)
                    }
                    JsonPrimitive(result)
                } else {
                    element
                }
            }
            is JsonObject -> buildJsonObject {
                element.forEach { (key, value) ->
                    put(key, substituteInElement(value, variables))
                }
            }
            is JsonArray -> buildJsonArray {
                element.forEach { add(substituteInElement(it, variables)) }
            }
        }
    }

    // ---------------------------------------------------------------
    // Response serialization
    // ---------------------------------------------------------------

    /**
     * Serializes an [HttpRequestResponse] to a [JsonObject].
     *
     * @param result The request/response pair to serialize.
     * @param elapsedMs Optional elapsed time in milliseconds.
     * @param maxBodyLength Optional maximum response body length. When null or 0, the full body is returned.
     *                      When positive, the body is truncated to this many characters.
     */
    fun serializeRequestResponse(result: HttpRequestResponse, elapsedMs: Long?, maxBodyLength: Int? = null): JsonObject {
        return buildJsonObject {
            put("has_response", result.hasResponse())
            put("url", result.url() ?: "")

            // Request info
            try {
                val req = result.request()
                if (req != null) {
                    put("request_method", req.method())
                    put("request_url", req.url())
                    put("request_host", req.httpService().host())
                    put("request_port", req.httpService().port())
                    put("request_secure", req.httpService().secure())
                    put("request_headers", buildJsonArray {
                        req.headers().forEach { h ->
                            add(buildJsonObject {
                                put("name", h.name())
                                put("value", h.value())
                            })
                        }
                    })
                    put("request_body", req.bodyToString())
                }
            } catch (_: Exception) { }

            // Response info
            if (result.hasResponse()) {
                try {
                    val resp = result.response()
                    put("status_code", resp.statusCode().toInt())
                    put("response_http_version", resp.httpVersion())
                    put("response_reason", resp.reasonPhrase() ?: "")
                    put("response_mime_type", resp.mimeType().name)
                    put("response_body_length", resp.body().length())
                    put("response_headers", buildJsonArray {
                        resp.headers().forEach { h ->
                            add(buildJsonObject {
                                put("name", h.name())
                                put("value", h.value())
                            })
                        }
                    })
                    val bodyStr = resp.bodyToString()
                    val responseBody = if (maxBodyLength != null && maxBodyLength > 0 && bodyStr.length > maxBodyLength) {
                        bodyStr.take(maxBodyLength) + "... [truncated, full length: ${bodyStr.length}]"
                    } else {
                        bodyStr
                    }
                    put("response_body", responseBody)

                    // Cookies
                    val cookies = resp.cookies()
                    if (cookies.isNotEmpty()) {
                        put("response_cookies", buildJsonArray {
                            cookies.forEach { c ->
                                add(buildJsonObject {
                                    put("name", c.name())
                                    put("value", c.value())
                                    put("domain", c.domain())
                                    put("path", c.path())
                                })
                            }
                        })
                    }
                } catch (_: Exception) { }
            }

            // Timing
            if (elapsedMs != null) {
                put("elapsed_ms", elapsedMs)
            }

            try {
                val timingOpt = result.timingData()
                if (timingOpt.isPresent) {
                    val td = timingOpt.get()
                    put("timing", buildJsonObject {
                        put("time_to_first_byte_ms", td.timeBetweenRequestSentAndStartOfResponse().toMillis())
                        put("time_to_complete_ms", td.timeBetweenRequestSentAndEndOfResponse().toMillis())
                        put("time_request_sent", td.timeRequestSent().toString())
                    })
                }
            } catch (_: Exception) { }
        }
    }
}
