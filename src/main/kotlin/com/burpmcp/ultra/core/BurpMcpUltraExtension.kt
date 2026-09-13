package com.burpmcp.ultra.core

import burp.api.montoya.BurpExtension
import burp.api.montoya.MontoyaApi
import com.burpmcp.ultra.transport.McpServerManager
import com.burpmcp.ultra.transport.DashboardServer
import com.burpmcp.ultra.events.EventBus
import com.burpmcp.ultra.state.StateManager
import com.burpmcp.ultra.bridge.BridgeFactory
import com.burpmcp.ultra.ui.BurpMcpUltraTab

class BurpMcpUltraExtension : BurpExtension {
    private lateinit var api: MontoyaApi
    private lateinit var serverManager: McpServerManager
    private lateinit var dashboardServer: DashboardServer
    private lateinit var eventBus: EventBus
    private lateinit var stateManager: StateManager
    private lateinit var uiTab: BurpMcpUltraTab

    override fun initialize(api: MontoyaApi) {
        this.api = api
        api.extension().setName("BurpMCP-Ultra")

        // Initialize event bus with ring buffer capacity
        eventBus = EventBus(maxBufferSize = 10000)

        // Initialize centralized state manager
        stateManager = StateManager()

        // Create all bridge instances via factory
        val bridges = BridgeFactory.createAll(api, eventBus, stateManager)

        // Initialize and start MCP server on configured ports
        serverManager = McpServerManager(
            bridges = bridges,
            eventBus = eventBus,
            stateManager = stateManager,
            ssePort = 9876,
            httpPort = 9877,
            logging = api.logging()
        )
        serverManager.start()

        dashboardServer = DashboardServer(bridges, eventBus, stateManager, 9878, api.logging())
        dashboardServer.start()

        // Register Burp Suite event handlers for proxy, scanner, scope, websocket, and HTTP traffic
        registerBurpHandlers(bridges)

        // Initialize and register the UI tab
        uiTab = BurpMcpUltraTab(api, serverManager, eventBus, stateManager)
        api.userInterface().registerSuiteTab("BurpMCP-Ultra", uiTab.getComponent())

        // Register unload handler for clean shutdown
        api.extension().registerUnloadingHandler {
            uiTab.dispose()
            dashboardServer.stop()
            serverManager.stop()
            eventBus.clear()
            stateManager.cleanup()
            api.logging().logToOutput("BurpMCP-Ultra: Extension unloaded")
        }

        uiTab.log("INFO", "System", "BurpMCP-Ultra v2.0.1 started")
        uiTab.log("INFO", "System", "SSE transport: http://127.0.0.1:9876")
        uiTab.log("INFO", "System", "Streamable HTTP transport: http://127.0.0.1:9877")
        uiTab.log("INFO", "System", "Dashboard: http://127.0.0.1:9878")
        uiTab.log("INFO", "System", "Tools registered: 137 | MCP Resources: 14")

        api.logging().logToOutput("BurpMCP-Ultra v2.0.1 started")
        api.logging().logToOutput("  SSE transport:             http://127.0.0.1:9876")
        api.logging().logToOutput("  Streamable HTTP transport:  http://127.0.0.1:9877")
        api.logging().logToOutput("  Dashboard:                  http://127.0.0.1:9878")
        api.logging().logToOutput("  stdio transport:            available")
        api.logging().logToOutput("  Tools registered:           137")
        api.logging().logToOutput("  MCP Resources:              14")

        api.logging().raiseInfoEvent("BurpMCP-Ultra MCP server started on ports 9876/9877")
    }

    private fun registerBurpHandlers(bridges: BridgeFactory.Bridges) {
        // Register proxy request/response handlers for event collection
        api.proxy().registerRequestHandler(bridges.proxy.createRequestHandler())
        api.proxy().registerResponseHandler(bridges.proxy.createResponseHandler())

        // Register scanner audit issue handler (Pro edition only)
        try {
            api.scanner().registerAuditIssueHandler(bridges.scanner.createIssueHandler())
        } catch (e: Exception) {
            api.logging().logToOutput(
                "BurpMCP-Ultra: Scanner handler not available (Community Edition?)"
            )
        }

        // Register scope change handler for live scope tracking
        api.scope().registerScopeChangeHandler(bridges.scope.createScopeChangeHandler())

        // Register WebSocket creation handler
        api.websockets().registerWebSocketCreatedHandler(
            bridges.websocket.createWebSocketHandler()
        )

        // Register global HTTP handler for traffic interception rules
        api.http().registerHttpHandler(bridges.http.createGlobalHttpHandler())
    }
}
