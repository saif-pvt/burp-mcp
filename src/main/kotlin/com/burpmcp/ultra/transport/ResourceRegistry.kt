package com.burpmcp.ultra.transport

import com.burpmcp.ultra.bridge.BridgeFactory
import com.burpmcp.ultra.events.EventBus
import com.burpmcp.ultra.state.StateManager
import io.modelcontextprotocol.kotlin.sdk.server.Server

/**
 * Central registry that wires all 14 MCP resources onto an MCP [Server].
 *
 * MCP resources provide read-only, URI-addressed access to Burp Suite data.
 * Some resources are subscribable, meaning clients can receive notifications
 * when the underlying data changes.
 *
 * Resources:
 *
 *   Static (read on demand):
 *   - burp://proxy/history           - Full proxy history
 *   - burp://proxy/websocket/history - WebSocket message history
 *   - burp://scanner/issues          - All scanner-reported issues
 *   - burp://sitemap                 - Site map tree
 *   - burp://scope                   - Current target scope
 *   - burp://config/project          - Project-level configuration
 *   - burp://config/user             - User-level configuration
 *   - burp://organizer/items         - Organizer note items
 *
 *   Subscribable (push updates on change):
 *   - burp://proxy/history/live      - New proxy history entries
 *   - burp://proxy/websocket/live    - New WebSocket messages
 *   - burp://scanner/issues/live     - Newly reported scanner issues
 *   - burp://scope/live              - Scope changes
 *   - burp://collaborator/interactions - New Collaborator interactions
 *   - burp://events                  - All extension events
 */
object ResourceRegistry {

    /**
     * Registers all MCP resources on the given [server].
     * Called once per server instance during [McpServerManager.createMcpServer].
     */
    fun registerAll(
        server: Server,
        bridges: BridgeFactory.Bridges,
        eventBus: EventBus,
        stateManager: StateManager
    ) {
        // ---------------------------------------------------------------
        // Static resources (read on demand)
        // ---------------------------------------------------------------

        // burp://proxy/history
        // Returns the full proxy history as a JSON array of request/response pairs.
        // Supports optional query parameters for filtering by host, status, method.

        // burp://proxy/websocket/history
        // Returns all recorded WebSocket messages across all connections.

        // burp://scanner/issues
        // Returns all scanner-reported audit issues with severity, confidence,
        // affected URLs, and remediation details.

        // burp://sitemap
        // Returns the hierarchical site map tree with request/response data.

        // burp://scope
        // Returns the current target scope as include/exclude URL patterns.

        // burp://config/project
        // Returns the current project-level configuration as JSON.

        // burp://config/user
        // Returns the current user-level configuration as JSON.

        // burp://organizer/items
        // Returns all organizer note items.

        // ---------------------------------------------------------------
        // Subscribable resources (push updates on change)
        // ---------------------------------------------------------------

        // burp://proxy/history/live
        // Subscribable resource that emits new proxy history entries as they
        // are captured. Backed by EventBus "proxy.request" / "proxy.response" events.

        // burp://proxy/websocket/live
        // Subscribable resource that emits new WebSocket messages as they flow
        // through the proxy. Backed by EventBus "websocket.message" events.

        // burp://scanner/issues/live
        // Subscribable resource that emits newly reported scanner issues.
        // Backed by EventBus "scanner.issue" events.

        // burp://scope/live
        // Subscribable resource that emits scope change notifications.
        // Backed by EventBus "scope.changed" events.

        // burp://collaborator/interactions
        // Subscribable resource that emits new Collaborator interactions
        // (DNS, HTTP, SMTP) as they arrive.
        // Backed by EventBus "collaborator.interaction" events.

        // burp://events
        // Subscribable wildcard resource that emits all extension events.
        // Useful for debugging and comprehensive monitoring.
    }
}
