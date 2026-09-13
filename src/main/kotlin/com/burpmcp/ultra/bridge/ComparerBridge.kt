package com.burpmcp.ultra.bridge

import burp.api.montoya.MontoyaApi
import burp.api.montoya.core.ByteArray as BurpByteArray
import kotlinx.serialization.json.*

class ComparerBridge(private val api: MontoyaApi) {

    /**
     * Sends one or more data items to Burp Suite's Comparer tool for
     * side-by-side comparison.
     *
     * @param dataItems List of string data items to send.
     * @return JSON object confirming the number of items sent.
     */
    fun send(dataItems: List<String>): JsonObject {
        val byteArrays = dataItems.map { BurpByteArray.byteArray(it) }.toTypedArray()
        api.comparer().sendToComparer(*byteArrays)
        return buildJsonObject {
            put("items_sent", dataItems.size)
            put("status", "sent_to_comparer")
        }
    }
}
