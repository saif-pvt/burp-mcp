package com.burpmcp.ultra.transport

import burp.api.montoya.logging.Logging
import com.burpmcp.ultra.bridge.BridgeFactory
import com.burpmcp.ultra.events.EventBus
import com.burpmcp.ultra.state.StateManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.sse.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.*

/**
 * Manages the lifecycle of MCP server instances and their underlying
 * Ktor HTTP servers.
 *
 * Two SSE transports are exposed on separate ports so that multiple
 * MCP clients can connect independently:
 * - **Primary SSE** on [ssePort] (default 9876).
 * - **Secondary SSE** on [httpPort] (default 9877).
 *
 * A third transport (stdio) is available but handled externally by the
 * process launcher, not by this manager.
 *
 * @param bridges All bridge instances for tool/resource registration.
 * @param eventBus Shared event bus for event-related tools/resources.
 * @param stateManager Shared state for stateful tools.
 * @param ssePort TCP port for the primary SSE transport (default 9876).
 * @param httpPort TCP port for the secondary SSE transport (default 9877).
 * @param logging Burp Suite logging API for startup/error messages.
 */
class McpServerManager(
    private val bridges: BridgeFactory.Bridges,
    private val eventBus: EventBus,
    private val stateManager: StateManager,
    private val ssePort: Int = 9876,
    private val httpPort: Int = 9877,
    private val logging: Logging
) {
    private var sseServer: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var httpServer: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Creates a fresh MCP [Server] instance with all tools and resources
     * registered. Each transport gets its own server instance so they
     * maintain independent session state.
     */
    fun createMcpServer(): Server {
        val server = Server(
            serverInfo = Implementation(
                name = "burpmcp-ultra",
                version = "2.0.1"
            ),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = true),
                    resources = ServerCapabilities.Resources(
                        subscribe = true,
                        listChanged = true
                    )
                )
            )
        )

        // Register all 137 tools and 14 resources on this server instance
        ToolRegistry.registerAll(server, bridges, eventBus, stateManager)
        ResourceRegistry.registerAll(server, bridges, eventBus, stateManager)

        return server
    }

    /**
     * Returns a factory function compatible with the MCP SDK's `mcp()` extension.
     * The SDK expects `(ServerSSESession) -> Server`.
     */
    private fun serverFactory(): (ServerSSESession) -> Server = { _ -> createMcpServer() }

    /**
     * Installs CORS on a Ktor [Application] so that browser-based MCP
     * clients (and tools like MCP Inspector) can connect to the SSE
     * endpoints.
     *
     * Wave-45 hardening: replaces the old `anyHost()` (which let ANY
     * webpage in the analyst's browser drive Burp and read proxy history
     * cross-origin) with an explicit allowlist of the platform's own
     * origins. Default allowlist covers the nyxstrike/apex dashboards
     * (localhost:3000/5173/8080 family). The operator can extend it with
     * `BURPMCP_ALLOWED_ORIGINS` (comma-separated list) or restore the old
     * fully-permissive behavior with the explicit operator key
     * `BURPMCP_ALLOW_ANY_ORIGIN=1` (recorded in the Burp log on startup).
     * The wildcard is never combined with credentials.
     */
    private fun Application.installCors() {
        install(CORS) {
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowMethod(HttpMethod.Delete)
            allowNonSimpleContentTypes = true
            CorsOrigins.apply(this)
        }
    }

    /**
     * Starts both transport servers asynchronously. Failures on one
     * transport do not prevent the other from starting.
     *
     * Each server installs CORS for browser-based clients, then mounts
     * the MCP SSE transport via [mcp]. The [mcp] extension auto-installs
     * the Ktor SSE plugin and registers GET /sse (streaming) and
     * POST /message (back-channel) endpoints.
     */
    fun start() {
        // Launch primary SSE transport
        scope.launch {
            try {
                sseServer = embeddedServer(CIO, port = ssePort, host = "127.0.0.1") {
                    installCors()
                    mcp(serverFactory())
                }.also { it.start(wait = false) }

                logging.logToOutput("BurpMCP-Ultra: SSE transport started on port $ssePort")
                logging.logToOutput("BurpMCP-Ultra: Connect MCP clients to http://127.0.0.1:$ssePort/sse")
            } catch (e: Exception) {
                logging.logToError(
                    "BurpMCP-Ultra: Failed to start SSE transport on port $ssePort: ${e.message}"
                )
                logging.logToError("BurpMCP-Ultra: Stack trace: ${e.stackTraceToString()}")
            }
        }

        // Launch secondary SSE transport on a separate port
        scope.launch {
            try {
                httpServer = embeddedServer(CIO, port = httpPort, host = "127.0.0.1") {
                    installCors()
                    mcp(serverFactory())
                }.also { it.start(wait = false) }

                logging.logToOutput(
                    "BurpMCP-Ultra: Secondary SSE transport started on port $httpPort"
                )
                logging.logToOutput("BurpMCP-Ultra: Connect MCP clients to http://127.0.0.1:$httpPort/sse")
            } catch (e: Exception) {
                logging.logToError(
                    "BurpMCP-Ultra: Failed to start secondary SSE transport on port $httpPort: ${e.message}"
                )
                logging.logToError("BurpMCP-Ultra: Stack trace: ${e.stackTraceToString()}")
            }
        }
    }

    /**
     * Gracefully stops both transport servers and cancels the coroutine scope.
     * Called from the extension unload handler.
     */
    fun stop() {
        sseServer?.stop(gracePeriodMillis = 1000, timeoutMillis = 2000)
        httpServer?.stop(gracePeriodMillis = 1000, timeoutMillis = 2000)
        scope.cancel()
        logging.logToOutput("BurpMCP-Ultra: MCP servers stopped")
    }
}
