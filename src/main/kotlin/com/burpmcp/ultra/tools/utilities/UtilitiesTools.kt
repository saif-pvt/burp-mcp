package com.burpmcp.ultra.tools.utilities

import com.burpmcp.ultra.bridge.UtilitiesBridge
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.server.Server
import kotlinx.serialization.json.*

object UtilitiesTools {

    fun register(server: Server, bridge: UtilitiesBridge) {

        // 1. util_url_encode
        server.addTool(
            name = "util_url_encode",
            description = "URL-encode a string. Parameters: data (required, the string " +
                "to encode), encode_all (optional boolean, default false. If true, " +
                "encodes all characters including unreserved ones)."
        ) { request ->
            try {
                val args = request.params.arguments ?: emptyMap()
                val data = args["data"]?.jsonPrimitive?.contentOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("""{"error":"Missing required parameter: data"}""")),
                        isError = true
                    )
                val encodeAll = args["encode_all"]?.jsonPrimitive?.booleanOrNull ?: false

                val result = bridge.urlEncode(data, encodeAll)
                CallToolResult(content = listOf(TextContent("""{"encoded":"$result"}""")))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("""{"error":"${e.message}"}""")),
                    isError = true
                )
            }
        }

        // 2. util_url_decode
        server.addTool(
            name = "util_url_decode",
            description = "URL-decode a string. Parameters: data (required, the " +
                "URL-encoded string to decode)."
        ) { request ->
            try {
                val args = request.params.arguments ?: emptyMap()
                val data = args["data"]?.jsonPrimitive?.contentOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("""{"error":"Missing required parameter: data"}""")),
                        isError = true
                    )

                val result = bridge.urlDecode(data)
                CallToolResult(content = listOf(TextContent("""{"decoded":"$result"}""")))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("""{"error":"${e.message}"}""")),
                    isError = true
                )
            }
        }

        // 3. util_base64_encode
        server.addTool(
            name = "util_base64_encode",
            description = "Base64-encode a string. Parameters: data (required, the " +
                "string to encode), url_safe (optional boolean, default false. If " +
                "true, uses URL-safe base64 encoding without padding)."
        ) { request ->
            try {
                val args = request.params.arguments ?: emptyMap()
                val data = args["data"]?.jsonPrimitive?.contentOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("""{"error":"Missing required parameter: data"}""")),
                        isError = true
                    )
                val urlSafe = args["url_safe"]?.jsonPrimitive?.booleanOrNull ?: false

                val result = bridge.base64Encode(data, urlSafe)
                CallToolResult(content = listOf(TextContent("""{"encoded":"$result"}""")))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("""{"error":"${e.message}"}""")),
                    isError = true
                )
            }
        }

        // 4. util_base64_decode
        server.addTool(
            name = "util_base64_decode",
            description = "Base64-decode a string. Parameters: data (required, the " +
                "base64-encoded string to decode). Supports both standard and " +
                "URL-safe base64."
        ) { request ->
            try {
                val args = request.params.arguments ?: emptyMap()
                val data = args["data"]?.jsonPrimitive?.contentOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("""{"error":"Missing required parameter: data"}""")),
                        isError = true
                    )

                val result = bridge.base64Decode(data)
                CallToolResult(content = listOf(TextContent("""{"decoded":"$result"}""")))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("""{"error":"${e.message}"}""")),
                    isError = true
                )
            }
        }

        // 5. util_html_encode
        server.addTool(
            name = "util_html_encode",
            description = "HTML-encode a string, escaping special characters like " +
                "<, >, &, \", and '. Parameters: data (required, the string to encode)."
        ) { request ->
            try {
                val args = request.params.arguments ?: emptyMap()
                val data = args["data"]?.jsonPrimitive?.contentOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("""{"error":"Missing required parameter: data"}""")),
                        isError = true
                    )

                val result = bridge.htmlEncode(data)
                CallToolResult(content = listOf(TextContent("""{"encoded":"$result"}""")))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("""{"error":"${e.message}"}""")),
                    isError = true
                )
            }
        }

        // 6. util_hash
        server.addTool(
            name = "util_hash",
            description = "Compute a cryptographic hash of a string. Parameters: " +
                "data (required, the string to hash), algorithm (required, one of: " +
                "MD5, SHA_1, SHA_256, SHA_384, SHA_512)."
        ) { request ->
            try {
                val args = request.params.arguments ?: emptyMap()
                val data = args["data"]?.jsonPrimitive?.contentOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("""{"error":"Missing required parameter: data"}""")),
                        isError = true
                    )
                val algorithm = args["algorithm"]?.jsonPrimitive?.contentOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("""{"error":"Missing required parameter: algorithm"}""")),
                        isError = true
                    )

                val result = bridge.hash(data, algorithm)
                CallToolResult(content = listOf(TextContent(result.toString())))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("""{"error":"${e.message}"}""")),
                    isError = true
                )
            }
        }

        // 7. util_compress
        server.addTool(
            name = "util_compress",
            description = "Compress data using the specified algorithm. Parameters: " +
                "data (required, base64-encoded input data), algorithm (required, " +
                "one of: GZIP, DEFLATE, BROTLI). Returns base64-encoded compressed data."
        ) { request ->
            try {
                val args = request.params.arguments ?: emptyMap()
                val data = args["data"]?.jsonPrimitive?.contentOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("""{"error":"Missing required parameter: data"}""")),
                        isError = true
                    )
                val algorithm = args["algorithm"]?.jsonPrimitive?.contentOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("""{"error":"Missing required parameter: algorithm"}""")),
                        isError = true
                    )

                val result = bridge.compress(data, algorithm)
                CallToolResult(content = listOf(TextContent(result.toString())))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("""{"error":"${e.message}"}""")),
                    isError = true
                )
            }
        }

        // 8. util_decompress
        server.addTool(
            name = "util_decompress",
            description = "Decompress data using the specified algorithm. Parameters: " +
                "data (required, base64-encoded compressed data), algorithm (required, " +
                "one of: GZIP, DEFLATE, BROTLI). Returns base64-encoded decompressed " +
                "data and a text representation if valid UTF-8."
        ) { request ->
            try {
                val args = request.params.arguments ?: emptyMap()
                val data = args["data"]?.jsonPrimitive?.contentOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("""{"error":"Missing required parameter: data"}""")),
                        isError = true
                    )
                val algorithm = args["algorithm"]?.jsonPrimitive?.contentOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("""{"error":"Missing required parameter: algorithm"}""")),
                        isError = true
                    )

                val result = bridge.decompress(data, algorithm)
                CallToolResult(content = listOf(TextContent(result.toString())))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("""{"error":"${e.message}"}""")),
                    isError = true
                )
            }
        }

        // 9. util_random_string
        server.addTool(
            name = "util_random_string",
            description = "Generate a cryptographically random string. Parameters: " +
                "length (required, integer length of the string), charset (optional, " +
                "default 'alphanumeric'. Predefined sets: 'alphanumeric', 'alpha', " +
                "'numeric', 'hex', 'ascii'. Or provide a custom string of characters)."
        ) { request ->
            try {
                val args = request.params.arguments ?: emptyMap()
                val length = args["length"]?.jsonPrimitive?.intOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("""{"error":"Missing required parameter: length"}""")),
                        isError = true
                    )
                val charset = args["charset"]?.jsonPrimitive?.contentOrNull ?: "alphanumeric"

                if (length < 1 || length > 10000) {
                    return@addTool CallToolResult(
                        content = listOf(TextContent("""{"error":"Length must be between 1 and 10000"}""")),
                        isError = true
                    )
                }

                val result = bridge.randomString(length, charset)
                CallToolResult(content = listOf(TextContent(result.toString())))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("""{"error":"${e.message}"}""")),
                    isError = true
                )
            }
        }

        // 10. util_random_bytes
        server.addTool(
            name = "util_random_bytes",
            description = "Generate cryptographically random bytes. Parameters: " +
                "length (required, integer number of bytes to generate). Returns " +
                "hex and base64 encoded representations."
        ) { request ->
            try {
                val args = request.params.arguments ?: emptyMap()
                val length = args["length"]?.jsonPrimitive?.intOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("""{"error":"Missing required parameter: length"}""")),
                        isError = true
                    )

                if (length < 1 || length > 10000) {
                    return@addTool CallToolResult(
                        content = listOf(TextContent("""{"error":"Length must be between 1 and 10000"}""")),
                        isError = true
                    )
                }

                val result = bridge.randomBytes(length)
                CallToolResult(content = listOf(TextContent(result.toString())))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("""{"error":"${e.message}"}""")),
                    isError = true
                )
            }
        }

        // 11. util_jwt_decode
        server.addTool(
            name = "util_jwt_decode",
            description = "Decode a JSON Web Token (JWT) and extract its header, " +
                "payload, and signature. Checks the exp claim for expiration. " +
                "Parameters: token (required, the JWT string), verify_signature " +
                "(optional boolean, default false. If true, verifies HMAC signature), " +
                "secret (optional, the secret key for HMAC signature verification)."
        ) { request ->
            try {
                val args = request.params.arguments ?: emptyMap()
                val token = args["token"]?.jsonPrimitive?.contentOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("""{"error":"Missing required parameter: token"}""")),
                        isError = true
                    )
                val verifySignature = args["verify_signature"]?.jsonPrimitive?.booleanOrNull ?: false
                val secret = args["secret"]?.jsonPrimitive?.contentOrNull

                val result = bridge.jwtDecode(token, verifySignature, secret)
                CallToolResult(content = listOf(TextContent(result.toString())))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("""{"error":"${e.message}"}""")),
                    isError = true
                )
            }
        }

        // 12. util_decode_smart
        server.addTool(
            name = "util_decode_smart",
            description = "Automatically detect and decode multiple layers of encoding " +
                "(base64, URL encoding, hex) iteratively until stable or max depth is " +
                "reached. Parameters: data (required, the encoded data string), " +
                "max_depth (optional integer, default 10, maximum decoding iterations)."
        ) { request ->
            try {
                val args = request.params.arguments ?: emptyMap()
                val data = args["data"]?.jsonPrimitive?.contentOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("""{"error":"Missing required parameter: data"}""")),
                        isError = true
                    )
                val maxDepth = args["max_depth"]?.jsonPrimitive?.intOrNull ?: 10

                val result = bridge.decodeSmart(data, maxDepth)
                CallToolResult(content = listOf(TextContent(result.toString())))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("""{"error":"${e.message}"}""")),
                    isError = true
                )
            }
        }

        // 13. util_shell_execute
        server.addTool(
            name = "util_shell_execute",
            description = "Execute a shell command and capture its output. " +
                "Parameters: command (required, the command to execute), args " +
                "(optional string array of arguments), timeout_ms (optional integer, " +
                "execution timeout in milliseconds, default 30000), working_dir " +
                "(optional, working directory for the command)."
        ) { request ->
            try {
                val args = request.params.arguments ?: emptyMap()
                val command = args["command"]?.jsonPrimitive?.contentOrNull
                    ?: return@addTool CallToolResult(
                        content = listOf(TextContent("""{"error":"Missing required parameter: command"}""")),
                        isError = true
                    )
                val cmdArgs = args["args"]?.jsonArray
                    ?.map { it.jsonPrimitive.content }
                    ?: emptyList()
                val timeoutMs = args["timeout_ms"]?.jsonPrimitive?.longOrNull ?: 30000L
                val workingDir = args["working_dir"]?.jsonPrimitive?.contentOrNull

                val result = bridge.shellExecute(command, cmdArgs, timeoutMs, workingDir)
                CallToolResult(content = listOf(TextContent(result.toString())))
            } catch (e: Exception) {
                CallToolResult(
                    content = listOf(TextContent("""{"error":"${e.message}"}""")),
                    isError = true
                )
            }
        }
    }
}
