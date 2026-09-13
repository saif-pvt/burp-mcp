package com.burpmcp.ultra.transport

import com.burpmcp.ultra.bridge.BridgeFactory
import com.burpmcp.ultra.events.EventBus
import com.burpmcp.ultra.state.StateManager
import com.burpmcp.ultra.tools.proxy.ProxyTools
import com.burpmcp.ultra.tools.http.HttpTools
import com.burpmcp.ultra.tools.scanner.ScannerTools
import com.burpmcp.ultra.tools.collaborator.CollaboratorTools
import com.burpmcp.ultra.tools.repeater.RepeaterTools
import com.burpmcp.ultra.tools.intruder.IntruderTools
import com.burpmcp.ultra.tools.sitemap.SitemapTools
import com.burpmcp.ultra.tools.scope.ScopeTools
import com.burpmcp.ultra.tools.comparer.ComparerTools
import com.burpmcp.ultra.tools.decoder.DecoderTools
import com.burpmcp.ultra.tools.organizer.OrganizerTools
import com.burpmcp.ultra.tools.websocket.WebSocketTools
import com.burpmcp.ultra.tools.burpsuite.BurpSuiteTools
import com.burpmcp.ultra.tools.utilities.UtilitiesTools
import com.burpmcp.ultra.tools.ai.AiTools
import com.burpmcp.ultra.tools.bambda.BambdaTools
import com.burpmcp.ultra.tools.persistence.PersistenceTools
import com.burpmcp.ultra.tools.project.ProjectTools
import com.burpmcp.ultra.tools.logging.LoggingTools
import com.burpmcp.ultra.tools.events.EventsTools
import com.burpmcp.ultra.tools.analysis.AnalysisTools
import com.burpmcp.ultra.tools.config.ConfigTools
import com.burpmcp.ultra.tools.session.SessionTools
import com.burpmcp.ultra.tools.extension.ExtensionTools
import com.burpmcp.ultra.tools.bcheck.BCheckTools
import com.burpmcp.ultra.tools.scancheck.ScanCheckTools
import com.burpmcp.ultra.tools.authdiff.AuthDiffTools
import com.burpmcp.ultra.tools.apiimport.ApiImportTools
import com.burpmcp.ultra.tools.passiveintel.PassiveIntelTools
import io.modelcontextprotocol.kotlin.sdk.server.Server

/**
 * Central registry that wires all 134 MCP tools onto an MCP [Server].
 *
 * Each tool category is implemented as a standalone object with a
 * `register(server, bridge, ...)` function. This keeps tool definitions
 * modular while giving the registry a single place to invoke them all.
 *
 * Tool categories and approximate counts:
 * - Proxy:        14 tools
 * - HTTP:          6 tools
 * - Scanner:       9 tools
 * - Collaborator:  5 tools
 * - Repeater:      3 tools
 * - Intruder:      5 tools
 * - Sitemap:       4 tools
 * - Scope:         5 tools
 * - Comparer:      2 tools
 * - Decoder:       4 tools
 * - Organizer:     3 tools
 * - WebSocket:     8 tools
 * - BurpSuite:     6 tools
 * - Utilities:     6 tools
 * - AI:            3 tools
 * - Bambda:        4 tools
 * - Persistence:   4 tools
 * - Project:       3 tools
 * - Logging:       3 tools
 * - Events:        5 tools
 * - Analysis:      7 tools
 * - Config:        4 tools
 * - Session:       5 tools
 * - Extension:     3 tools
 * - BCheck:        5 tools
 * - ScanCheck:     5 tools
 */
object ToolRegistry {

    /**
     * Registers all tool categories on the given MCP [server].
     * Called once per server instance during [McpServerManager.createMcpServer].
     */
    fun registerAll(
        server: Server,
        bridges: BridgeFactory.Bridges,
        eventBus: EventBus,
        stateManager: StateManager
    ) {
        // Proxy tools: history, intercept, rules, WebSocket proxy
        ProxyTools.register(server, bridges.proxy, stateManager)

        // HTTP tools: send request, request builder, traffic rules
        HttpTools.register(server, bridges.http, stateManager)

        // Scanner tools: active/passive scan, crawl, audit issues
        ScannerTools.register(server, bridges.scanner, stateManager)

        // Collaborator tools: create client, generate payloads, poll interactions
        CollaboratorTools.register(server, bridges.collaborator, stateManager)

        // Repeater tools: send to repeater, create tab, send request
        RepeaterTools.register(server, bridges.repeater)

        // Intruder tools: create attack, configure payloads, start/stop
        IntruderTools.register(server, bridges.intruder, stateManager)

        // Sitemap tools: get tree, filter, add items
        SitemapTools.register(server, bridges.sitemap)

        // Scope tools: include/exclude, check, get/set
        ScopeTools.register(server, bridges.scope)

        // Comparer tools: compare requests/responses
        ComparerTools.register(server, bridges.comparer)

        // Decoder tools: encode, decode, hash, smart decode
        DecoderTools.register(server, bridges.decoder)

        // Organizer tools: add notes, get items, manage
        OrganizerTools.register(server, bridges.organizer)

        // WebSocket tools: connect, send, receive, intercept
        WebSocketTools.register(server, bridges.websocket, stateManager)

        // BurpSuite tools: version, product info, config
        BurpSuiteTools.register(server, bridges.burpSuite)

        // Utilities tools: URL analysis, regex, diff, timing
        UtilitiesTools.register(server, bridges.utilities)

        // AI tools: Montoya AI integration (Burp 2025+)
        AiTools.register(server, bridges.ai)

        // Bambda tools: evaluate, list, manage Bambda expressions
        BambdaTools.register(server, bridges.bambda)

        // Persistence tools: get/set/delete extension data
        PersistenceTools.register(server, bridges.persistence)

        // Project tools: project info, data, export
        ProjectTools.register(server, bridges.project)

        // Logging tools: log events, get logs, configure
        LoggingTools.register(server, bridges.logging)

        // Events tools: poll, subscribe, filter events
        EventsTools.register(server, eventBus)

        // Analysis tools: cross-bridge analysis and reporting
        AnalysisTools.register(server, bridges)

        // Config tools: get/set project and user config
        ConfigTools.register(server, bridges.burpSuite)

        // Session tools: session rules, token extraction/injection
        SessionTools.register(server, bridges.http, stateManager)

        // Extension tools: extension info, registered handlers, state
        ExtensionTools.register(server, bridges.extension)

        // BCheck tools: create, import, templates, list, remove BCheck scan checks
        BCheckTools.register(server, bridges.bcheck)

        // ScanCheck tools: Script-mode scan checks via Montoya ScanCheck API
        ScanCheckTools.register(server, bridges.scanCheck)

        // AuthDiff tools: auth level diffing for IDOR/privilege escalation detection
        AuthDiffTools.register(server, bridges)

        // API Import tools: import OpenAPI/Swagger specs to generate requests
        ApiImportTools.register(server, bridges.apiImport)

        // Passive Intel tools: scan proxy history for leaked secrets, tokens, internal URLs, etc.
        PassiveIntelTools.register(server, bridges.passiveIntel)
    }
}
