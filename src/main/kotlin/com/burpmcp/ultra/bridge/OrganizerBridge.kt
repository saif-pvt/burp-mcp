package com.burpmcp.ultra.bridge

import burp.api.montoya.MontoyaApi
import burp.api.montoya.http.message.requests.HttpRequest
import burp.api.montoya.http.message.responses.HttpResponse
import burp.api.montoya.http.HttpService
import burp.api.montoya.http.message.HttpRequestResponse
import kotlinx.serialization.json.*

class OrganizerBridge(private val api: MontoyaApi) {

    /**
     * Sends a request (and optionally a response) to Burp Suite's Organizer
     * tool for bookmarking and note-taking.
     *
     * If [response] is provided, both request and response are sent as an
     * HttpRequestResponse pair. Otherwise only the request is sent.
     *
     * @param request Raw HTTP request string.
     * @param response Optional raw HTTP response string.
     * @param host Target hostname.
     * @param port Target port number.
     * @param useTls Whether the connection uses TLS.
     * @return JSON object confirming the item was sent to the Organizer.
     */
    fun send(
        request: String,
        response: String?,
        host: String,
        port: Int,
        useTls: Boolean
    ): JsonObject {
        val httpService = HttpService.httpService(host, port, useTls)
        val httpRequest = HttpRequest.httpRequest(httpService, request)

        if (response != null) {
            val httpResponse = HttpResponse.httpResponse(response)
            val requestResponse = HttpRequestResponse.httpRequestResponse(httpRequest, httpResponse)
            api.organizer().sendToOrganizer(requestResponse)
        } else {
            api.organizer().sendToOrganizer(httpRequest)
        }

        return buildJsonObject {
            put("status", "sent_to_organizer")
            put("host", host)
            put("port", port)
            put("use_tls", useTls)
            put("has_response", response != null)
        }
    }

    /**
     * Retrieves items currently in the Organizer, with optional URL prefix
     * filtering and result limiting.
     *
     * @param urlPrefix Optional URL prefix to filter items (e.g. "https://example.com").
     * @param maxResults Maximum number of items to return (default 100).
     * @return JSON object containing the matching Organizer items.
     */
    fun getItems(urlPrefix: String?, maxResults: Int): JsonObject {
        val allItems = api.organizer().items()

        val filtered = if (urlPrefix != null) {
            allItems.filter { item ->
                try {
                    item.url().contains(urlPrefix, ignoreCase = true)
                } catch (_: Exception) {
                    false
                }
            }
        } else {
            allItems
        }

        val limited = filtered.take(maxResults)

        return buildJsonObject {
            put("total_items", allItems.size)
            put("filtered_items", filtered.size)
            put("returned_items", limited.size)
            putJsonArray("items") {
                for (item in limited) {
                    addJsonObject {
                        try {
                            put("url", item.url())
                            put("method", item.request().method())
                            put("host", item.httpService().host())
                            put("port", item.httpService().port())
                            put("use_tls", item.httpService().secure())

                            if (item.hasResponse()) {
                                val resp = item.response()
                                put("status_code", resp.statusCode())
                                put("response_length", resp.body().length())
                            }
                        } catch (_: Exception) {
                            put("error", "failed to read item details")
                        }
                    }
                }
            }
        }
    }
}
