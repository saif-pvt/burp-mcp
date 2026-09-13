package com.burpmcp.ultra.bridge

import burp.api.montoya.MontoyaApi
import burp.api.montoya.core.ByteArray as BurpByteArray
import kotlinx.serialization.json.*

class DecoderBridge(private val api: MontoyaApi) {

    /**
     * Sends data to Burp Suite's Decoder tool for manual encoding,
     * decoding, and hashing operations.
     *
     * @param data The string data to send to the Decoder.
     * @return JSON object confirming the data was sent.
     */
    fun send(data: String): JsonObject {
        api.decoder().sendToDecoder(BurpByteArray.byteArray(data))
        return buildJsonObject {
            put("status", "sent_to_decoder")
            put("data_length", data.length)
        }
    }
}
