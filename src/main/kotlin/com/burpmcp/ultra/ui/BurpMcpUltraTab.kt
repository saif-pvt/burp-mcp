package com.burpmcp.ultra.ui

import burp.api.montoya.MontoyaApi
import com.burpmcp.ultra.events.EventBus
import com.burpmcp.ultra.state.StateManager
import com.burpmcp.ultra.transport.McpServerManager
import java.awt.*
import java.awt.event.ActionEvent
import java.io.File
import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.TitledBorder
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

/**
 * Comprehensive Burp Suite UI tab for the BurpMCP-Ultra extension.
 *
 * Provides:
 * - Server configuration panel with port settings, start/stop controls, and status indicator
 * - Live scrolling activity log of all MCP tool calls, events, and errors
 * - Real-time statistics: tool call counts, active connections, uptime, per-tool breakdown
 *
 * All UI updates are dispatched to the EDT via [SwingUtilities.invokeLater] to ensure
 * thread safety. The log is capped at [MAX_LOG_ROWS] entries with oldest rows evicted
 * on overflow.
 */
class BurpMcpUltraTab(
    private val api: MontoyaApi,
    private val serverManager: McpServerManager,
    private val eventBus: EventBus,
    private val stateManager: StateManager
) {
    companion object {
        /** Maximum number of rows retained in the activity log table. */
        const val MAX_LOG_ROWS = 5000

        /** Interval in milliseconds for the periodic UI refresh timer. */
        const val REFRESH_INTERVAL_MS = 1000

        private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        private val NUMBER_FORMAT: NumberFormat = NumberFormat.getIntegerInstance()
    }

    // ── Statistics Counters ──────────────────────────────────────────────────

    private val totalToolCalls = AtomicLong(0)
    private val totalErrors = AtomicLong(0)
    private val totalEvents = AtomicLong(0)
    private val perToolCounts = ConcurrentHashMap<String, AtomicLong>()
    private var serverStartTime: Long = System.currentTimeMillis()
    private var serverRunning: Boolean = true

    // ── Swing Components ─────────────────────────────────────────────────────

    private val mainPanel = JPanel(BorderLayout(0, 0))

    // Server config
    private val ssePortField = JTextField("9876", 6)
    private val httpPortField = JTextField("9877", 6)
    private val statusIndicator = JLabel()
    private val statusText = JLabel("Running")
    private val startButton = JButton("Start Server")
    private val stopButton = JButton("Stop Server")
    private val restartButton = JButton("Restart")
    private val activeConnectionsLabel = JLabel("0")
    private val toolCountLabel = JLabel("137")
    private val uptimeLabel = JLabel("00:00:00")

    // Activity log
    private val logTableModel = object : DefaultTableModel(
        arrayOf("Timestamp", "Level", "Category", "Message"), 0
    ) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val logTable = JTable(logTableModel)
    private val logScrollPane = JScrollPane(logTable)

    // Statistics
    private val totalCallsLabel = JLabel("0")
    private val totalErrorsLabel = JLabel("0")
    private val totalEventsLabel = JLabel("0")
    private val topToolsLabel = JLabel("(none)")
    private val activeRulesLabel = JLabel("Proxy(0), Traffic(0), Session(0)")

    // Clear and export buttons
    private val clearButton = JButton("Clear")
    private val exportButton = JButton("Export")

    // Auto-scroll toggle
    private val autoScrollCheckBox = JCheckBox("Auto-scroll", true)

    // Periodic refresh timer
    private val refreshTimer: Timer

    // Event bus subscription id for live events
    private var eventSubscriptionId: String? = null

    init {
        buildUI()
        wireActions()
        wireEventBusSubscription()

        // Periodic timer to update uptime, stats, and connection counts
        refreshTimer = Timer(REFRESH_INTERVAL_MS) { refreshPeriodicState() }
        refreshTimer.isRepeats = true
        refreshTimer.start()

        // Apply Burp's current theme
        api.userInterface().applyThemeToComponent(mainPanel)

        // Initial log entry
        log("INFO", "System", "BurpMCP-Ultra UI tab initialized")
    }

    /**
     * Returns the root Swing component to register with
     * [burp.api.montoya.ui.UserInterface.registerSuiteTab].
     */
    fun getComponent(): Component = mainPanel

    // ── Public Logging API ───────────────────────────────────────────────────

    /**
     * Appends a row to the activity log table. Safe to call from any thread.
     *
     * @param level   Severity level: "INFO", "WARN", "ERROR", "EVENT", "DEBUG".
     * @param category  Logical category: "Tool", "Event", "System", "Connection", etc.
     * @param message   Human-readable description of what happened.
     */
    fun log(level: String, category: String, message: String) {
        val timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT)
        SwingUtilities.invokeLater {
            // Evict oldest rows when limit is reached
            while (logTableModel.rowCount >= MAX_LOG_ROWS) {
                logTableModel.removeRow(0)
            }
            logTableModel.addRow(arrayOf(timestamp, level, category, message))

            // Auto-scroll to bottom if enabled
            if (autoScrollCheckBox.isSelected) {
                val lastRow = logTable.rowCount - 1
                if (lastRow >= 0) {
                    logTable.scrollRectToVisible(logTable.getCellRect(lastRow, 0, true))
                }
            }
        }
    }

    /**
     * Records a tool call for statistics tracking and logs it.
     *
     * @param toolName  The MCP tool name (e.g. "proxy_history").
     * @param detail    Optional detail string (e.g. arguments summary or result status).
     */
    fun logToolCall(toolName: String, detail: String = "") {
        totalToolCalls.incrementAndGet()
        perToolCounts.computeIfAbsent(toolName) { AtomicLong(0) }.incrementAndGet()
        val msg = if (detail.isNotEmpty()) "Tool called: $toolName ($detail)" else "Tool called: $toolName"
        log("INFO", "Tool", msg)
    }

    /**
     * Records an error and logs it.
     *
     * @param category  Error category (e.g. tool name or subsystem).
     * @param message   Error description.
     */
    fun logError(category: String, message: String) {
        totalErrors.incrementAndGet()
        log("ERROR", category, message)
    }

    /**
     * Records a Burp event and logs it.
     *
     * @param eventType  The event type string from the EventBus.
     * @param detail     Summary of the event data.
     */
    fun logEvent(eventType: String, detail: String) {
        totalEvents.incrementAndGet()
        log("EVENT", eventType, detail)
    }

    /**
     * Stops the refresh timer and unsubscribes from the event bus.
     * Called during extension unload.
     */
    fun dispose() {
        refreshTimer.stop()
        eventSubscriptionId?.let { eventBus.unsubscribe(it) }
    }

    // ── UI Construction ──────────────────────────────────────────────────────

    private fun buildUI() {
        mainPanel.border = EmptyBorder(10, 10, 10, 10)

        // Top: server configuration panel
        val configPanel = buildConfigPanel()

        // Center: activity log
        val logPanel = buildLogPanel()

        // Bottom: statistics panel
        val statsPanel = buildStatsPanel()

        // Combine config and stats into a top section
        val topSection = JPanel(BorderLayout(0, 8))
        topSection.isOpaque = false
        topSection.add(configPanel, BorderLayout.NORTH)
        topSection.add(statsPanel, BorderLayout.SOUTH)

        // Use a split pane so the log area is resizable
        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, topSection, logPanel)
        splitPane.resizeWeight = 0.0 // give all extra space to the log
        splitPane.dividerSize = 6
        splitPane.border = null

        mainPanel.add(splitPane, BorderLayout.CENTER)
    }

    private fun buildConfigPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = TitledBorder("Server Configuration")
        val gbc = GridBagConstraints()
        gbc.insets = Insets(4, 6, 4, 6)
        gbc.anchor = GridBagConstraints.WEST

        // Row 0: Port fields and status
        gbc.gridy = 0

        gbc.gridx = 0
        panel.add(JLabel("SSE Port:"), gbc)
        gbc.gridx = 1
        ssePortField.isEditable = true
        panel.add(ssePortField, gbc)

        gbc.gridx = 2
        panel.add(JLabel("HTTP Port:"), gbc)
        gbc.gridx = 3
        httpPortField.isEditable = true
        panel.add(httpPortField, gbc)

        gbc.gridx = 4
        panel.add(JLabel("Status:"), gbc)

        gbc.gridx = 5
        val statusPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        statusPanel.isOpaque = false
        updateStatusIndicator(true)
        statusPanel.add(statusIndicator)
        statusPanel.add(statusText)
        panel.add(statusPanel, gbc)

        // Horizontal filler to push everything left
        gbc.gridx = 6
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        panel.add(JLabel(), gbc)
        gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE

        // Row 1: Buttons
        gbc.gridy = 1

        gbc.gridx = 0
        panel.add(startButton, gbc)
        gbc.gridx = 1
        panel.add(stopButton, gbc)
        gbc.gridx = 2
        panel.add(restartButton, gbc)

        // Initially: server is running, so Start is disabled
        startButton.isEnabled = false
        stopButton.isEnabled = true
        restartButton.isEnabled = true

        // Row 2: Quick stats line
        gbc.gridy = 2
        gbc.gridx = 0
        gbc.gridwidth = 7
        gbc.fill = GridBagConstraints.HORIZONTAL

        val quickStatsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        quickStatsPanel.isOpaque = false
        quickStatsPanel.add(JLabel("Active Connections: "))
        quickStatsPanel.add(activeConnectionsLabel)
        quickStatsPanel.add(createSeparatorLabel())
        quickStatsPanel.add(JLabel("Tools: "))
        quickStatsPanel.add(toolCountLabel)
        quickStatsPanel.add(createSeparatorLabel())
        quickStatsPanel.add(JLabel("Uptime: "))
        quickStatsPanel.add(uptimeLabel)
        panel.add(quickStatsPanel, gbc)

        return panel
    }

    private fun buildLogPanel(): JPanel {
        val panel = JPanel(BorderLayout(0, 4))
        panel.border = TitledBorder("Activity Log")

        // Configure the table
        logTable.fillsViewportHeight = true
        logTable.autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        logTable.rowHeight = 20
        logTable.setShowGrid(false)
        logTable.intercellSpacing = Dimension(0, 0)
        logTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)

        // Column widths
        logTable.columnModel.getColumn(0).preferredWidth = 180  // Timestamp
        logTable.columnModel.getColumn(0).maxWidth = 200
        logTable.columnModel.getColumn(1).preferredWidth = 60   // Level
        logTable.columnModel.getColumn(1).maxWidth = 80
        logTable.columnModel.getColumn(2).preferredWidth = 110  // Category
        logTable.columnModel.getColumn(2).maxWidth = 150
        logTable.columnModel.getColumn(3).preferredWidth = 600  // Message

        // Custom renderer for the Level column to color-code rows
        logTable.columnModel.getColumn(1).cellRenderer = LevelCellRenderer()

        // Scrollpane
        logScrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
        panel.add(logScrollPane, BorderLayout.CENTER)

        // Bottom toolbar: auto-scroll, clear, export
        val toolbar = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 2))
        toolbar.isOpaque = false
        toolbar.add(autoScrollCheckBox)
        toolbar.add(clearButton)
        toolbar.add(exportButton)
        panel.add(toolbar, BorderLayout.SOUTH)

        return panel
    }

    private fun buildStatsPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = TitledBorder("Statistics")
        val gbc = GridBagConstraints()
        gbc.insets = Insets(3, 6, 3, 6)
        gbc.anchor = GridBagConstraints.WEST

        // Row 0: Aggregate counters
        gbc.gridy = 0
        gbc.gridx = 0
        val countersPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        countersPanel.isOpaque = false
        countersPanel.add(JLabel("Total Tool Calls: "))
        countersPanel.add(makeBoldLabel(totalCallsLabel))
        countersPanel.add(createSeparatorLabel())
        countersPanel.add(JLabel("Errors: "))
        countersPanel.add(makeBoldLabel(totalErrorsLabel))
        countersPanel.add(createSeparatorLabel())
        countersPanel.add(JLabel("Events: "))
        countersPanel.add(makeBoldLabel(totalEventsLabel))
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        panel.add(countersPanel, gbc)

        // Row 1: Top tools
        gbc.gridy = 1
        gbc.gridx = 0
        val topToolsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        topToolsPanel.isOpaque = false
        topToolsPanel.add(JLabel("Top Tools: "))
        topToolsPanel.add(topToolsLabel)
        panel.add(topToolsPanel, gbc)

        // Row 2: Active rules
        gbc.gridy = 2
        gbc.gridx = 0
        val rulesPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        rulesPanel.isOpaque = false
        rulesPanel.add(JLabel("Active Rules: "))
        rulesPanel.add(activeRulesLabel)
        panel.add(rulesPanel, gbc)

        return panel
    }

    // ── Action Wiring ────────────────────────────────────────────────────────

    private fun wireActions() {
        startButton.addActionListener { handleStartServer() }
        stopButton.addActionListener { handleStopServer() }
        restartButton.addActionListener { handleRestartServer() }
        clearButton.addActionListener { handleClearLog() }
        exportButton.addActionListener { handleExportLog() }
    }

    private fun handleStartServer() {
        val ssePort = parsePort(ssePortField.text, "SSE Port")
        val httpPort = parsePort(httpPortField.text, "HTTP Port")
        if (ssePort == null || httpPort == null) return

        if (ssePort == httpPort) {
            JOptionPane.showMessageDialog(
                mainPanel,
                "SSE Port and HTTP Port must be different.",
                "Invalid Configuration",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        try {
            serverManager.start()
            serverRunning = true
            serverStartTime = System.currentTimeMillis()
            updateStatusIndicator(true)
            startButton.isEnabled = false
            stopButton.isEnabled = true
            restartButton.isEnabled = true
            ssePortField.isEditable = false
            httpPortField.isEditable = false
            log("INFO", "System", "MCP server started (SSE: $ssePort, HTTP: $httpPort)")
        } catch (e: Exception) {
            logError("System", "Failed to start server: ${e.message}")
            JOptionPane.showMessageDialog(
                mainPanel,
                "Failed to start server: ${e.message}",
                "Server Error",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    private fun handleStopServer() {
        try {
            serverManager.stop()
            serverRunning = false
            updateStatusIndicator(false)
            startButton.isEnabled = true
            stopButton.isEnabled = false
            restartButton.isEnabled = false
            ssePortField.isEditable = true
            httpPortField.isEditable = true
            log("INFO", "System", "MCP server stopped")
        } catch (e: Exception) {
            logError("System", "Failed to stop server: ${e.message}")
        }
    }

    private fun handleRestartServer() {
        log("INFO", "System", "Restarting MCP server...")
        handleStopServer()
        // Small delay to allow ports to be released
        Timer(500) {
            SwingUtilities.invokeLater { handleStartServer() }
        }.apply {
            isRepeats = false
            start()
        }
    }

    private fun handleClearLog() {
        SwingUtilities.invokeLater {
            logTableModel.rowCount = 0
            log("INFO", "System", "Activity log cleared")
        }
    }

    private fun handleExportLog() {
        val chooser = JFileChooser()
        chooser.dialogTitle = "Export Activity Log"
        chooser.fileFilter = FileNameExtensionFilter("CSV Files (*.csv)", "csv")
        chooser.selectedFile = File("burpmcp-ultra-log-${
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        }.csv")

        val result = chooser.showSaveDialog(mainPanel)
        if (result != JFileChooser.APPROVE_OPTION) return

        var file = chooser.selectedFile
        if (!file.name.endsWith(".csv")) {
            file = File(file.absolutePath + ".csv")
        }

        try {
            file.bufferedWriter().use { writer ->
                writer.write("Timestamp,Level,Category,Message")
                writer.newLine()

                for (row in 0 until logTableModel.rowCount) {
                    val timestamp = escapeCSV(logTableModel.getValueAt(row, 0)?.toString() ?: "")
                    val level = escapeCSV(logTableModel.getValueAt(row, 1)?.toString() ?: "")
                    val category = escapeCSV(logTableModel.getValueAt(row, 2)?.toString() ?: "")
                    val message = escapeCSV(logTableModel.getValueAt(row, 3)?.toString() ?: "")
                    writer.write("$timestamp,$level,$category,$message")
                    writer.newLine()
                }
            }
            log("INFO", "System", "Log exported to ${file.absolutePath} (${logTableModel.rowCount} rows)")
        } catch (e: Exception) {
            logError("System", "Failed to export log: ${e.message}")
            JOptionPane.showMessageDialog(
                mainPanel,
                "Failed to export log: ${e.message}",
                "Export Error",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    // ── Event Bus Integration ────────────────────────────────────────────────

    /**
     * Subscribes to all EventBus events so that proxy/scanner/scope/websocket
     * events appear in the activity log in real time.
     */
    private fun wireEventBusSubscription() {
        eventSubscriptionId = eventBus.subscribe(emptyList()) { event ->
            totalEvents.incrementAndGet()
            val summary = buildEventSummary(event.type, event.data.toString())
            log("EVENT", event.type, summary)
        }
    }

    /**
     * Builds a concise human-readable summary from an event type and its JSON data.
     */
    private fun buildEventSummary(type: String, dataJson: String): String {
        // Truncate very long payloads for display
        val maxLen = 200
        val truncated = if (dataJson.length > maxLen) {
            dataJson.substring(0, maxLen) + "..."
        } else {
            dataJson
        }
        return truncated
    }

    // ── Periodic Refresh ─────────────────────────────────────────────────────

    /**
     * Called every [REFRESH_INTERVAL_MS] by the Swing timer. Updates uptime,
     * connection counts, statistics labels, and active rules.
     */
    private fun refreshPeriodicState() {
        SwingUtilities.invokeLater {
            // Uptime
            if (serverRunning) {
                val elapsed = System.currentTimeMillis() - serverStartTime
                uptimeLabel.text = formatDuration(elapsed)
            }

            // Aggregate counters
            totalCallsLabel.text = NUMBER_FORMAT.format(totalToolCalls.get())
            totalErrorsLabel.text = NUMBER_FORMAT.format(totalErrors.get())
            totalEventsLabel.text = NUMBER_FORMAT.format(totalEvents.get())

            // Top tools (top 5 by call count)
            topToolsLabel.text = computeTopTools(5)

            // Active rules from StateManager
            val proxyCount = stateManager.proxyRules.count { it.enabled }
            val trafficCount = stateManager.trafficRules.count { it.enabled }
            val sessionCount = stateManager.sessionRules.count { it.enabled }
            activeRulesLabel.text = "Proxy($proxyCount), Traffic($trafficCount), Session($sessionCount)"

            // Active connections: count WebSocket connections in "connected" state
            val wsConnections = stateManager.websocketConnections.values.count { it.status == "connected" }
            activeConnectionsLabel.text = wsConnections.toString()
        }
    }

    // ── Status Indicator ─────────────────────────────────────────────────────

    /**
     * Updates the status indicator circle and text.
     *
     * @param running True for green/Running, false for red/Stopped.
     */
    private fun updateStatusIndicator(running: Boolean) {
        statusIndicator.icon = CircleIcon(if (running) Color(0x2E, 0xCC, 0x71) else Color(0xE7, 0x4C, 0x3C), 12)
        statusText.text = if (running) "Running" else "Stopped"
        statusText.foreground = if (running) Color(0x2E, 0xCC, 0x71) else Color(0xE7, 0x4C, 0x3C)
    }

    // ── Utility Methods ──────────────────────────────────────────────────────

    private fun parsePort(text: String, fieldName: String): Int? {
        val port = text.trim().toIntOrNull()
        if (port == null || port < 1 || port > 65535) {
            JOptionPane.showMessageDialog(
                mainPanel,
                "$fieldName must be a number between 1 and 65535.",
                "Invalid Port",
                JOptionPane.WARNING_MESSAGE
            )
            return null
        }
        return port
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun computeTopTools(limit: Int): String {
        if (perToolCounts.isEmpty()) return "(none)"
        return perToolCounts.entries
            .sortedByDescending { it.value.get() }
            .take(limit)
            .joinToString(", ") { "${it.key}(${NUMBER_FORMAT.format(it.value.get())})" }
    }

    private fun escapeCSV(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun createSeparatorLabel(): JLabel {
        val sep = JLabel("  |  ")
        sep.foreground = Color.GRAY
        return sep
    }

    private fun makeBoldLabel(label: JLabel): JLabel {
        label.font = label.font.deriveFont(Font.BOLD)
        return label
    }

    // ── Custom Renderers and Icons ───────────────────────────────────────────

    /**
     * A small filled circle icon used for the server status indicator.
     */
    private class CircleIcon(private val color: Color, private val diameter: Int) : Icon {
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = color
            g2.fillOval(x, y, diameter, diameter)
            g2.dispose()
        }

        override fun getIconWidth(): Int = diameter
        override fun getIconHeight(): Int = diameter
    }

    /**
     * Custom cell renderer for the "Level" column that applies color coding:
     * - INFO:  default foreground
     * - WARN:  orange
     * - ERROR: red
     * - EVENT: blue/teal
     * - DEBUG: gray
     */
    private class LevelCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            if (!isSelected) {
                foreground = when (value?.toString()) {
                    "ERROR" -> Color(0xE7, 0x4C, 0x3C)
                    "WARN" -> Color(0xF3, 0x9C, 0x12)
                    "EVENT" -> Color(0x1A, 0xBC, 0x9C)
                    "DEBUG" -> Color.GRAY
                    else -> table.foreground
                }
            }
            return component
        }
    }
}
