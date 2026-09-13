# BurpMCP-Ultra Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the most comprehensive Burp Suite Pro MCP server — 121 tools, 14 resources, 12 event types, all three MCP transports.

**Architecture:** Pure Kotlin Burp Suite extension using Montoya API 2026.2 with embedded MCP server via MCP Kotlin SDK 0.8.3. Single fat JAR artifact deployed into Burp's Extensions tab. Three transports: SSE (:9876), Streamable HTTP (:9877), stdio.

**Tech Stack:** Kotlin 2.0.21, Montoya API 2026.2, MCP Kotlin SDK 0.8.3, Ktor CIO, kotlinx.serialization, kotlinx.coroutines, Shadow JAR plugin 8.1.1.

---

## File Structure

```
burpmcp-ultra/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/
├── src/main/kotlin/com/burpmcp/ultra/
│   ├── core/
│   │   └── BurpMcpUltraExtension.kt      # Extension entry point
│   ├── bridge/                             # Montoya API wrappers (one per sub-API)
│   │   ├── ProxyBridge.kt
│   │   ├── HttpBridge.kt
│   │   ├── ScannerBridge.kt
│   │   ├── CollaboratorBridge.kt
│   │   ├── RepeaterBridge.kt
│   │   ├── IntruderBridge.kt
│   │   ├── SitemapBridge.kt
│   │   ├── ScopeBridge.kt
│   │   ├── ComparerBridge.kt
│   │   ├── DecoderBridge.kt
│   │   ├── OrganizerBridge.kt
│   │   ├── WebSocketBridge.kt
│   │   ├── BurpSuiteBridge.kt
│   │   ├── UtilitiesBridge.kt
│   │   ├── AiBridge.kt
│   │   ├── BambdaBridge.kt
│   │   ├── PersistenceBridge.kt
│   │   ├── ProjectBridge.kt
│   │   ├── LoggingBridge.kt
│   │   ├── ExtensionBridge.kt
│   │   ├── AnalysisBridge.kt
│   │   ├── ConfigBridge.kt
│   │   ├── SessionBridge.kt
│   │   └── BridgeFactory.kt
│   ├── tools/                              # MCP tool registration (one per category)
│   │   ├── proxy/ProxyTools.kt             # 12 tools
│   │   ├── http/HttpTools.kt               # 10 tools
│   │   ├── scanner/ScannerTools.kt         # 12 tools
│   │   ├── collaborator/CollaboratorTools.kt # 6 tools
│   │   ├── repeater/RepeaterTools.kt       # 1 tool
│   │   ├── intruder/IntruderTools.kt       # 3 tools
│   │   ├── sitemap/SitemapTools.kt         # 4 tools
│   │   ├── scope/ScopeTools.kt             # 4 tools
│   │   ├── comparer/ComparerTools.kt       # 1 tool
│   │   ├── decoder/DecoderTools.kt         # 1 tool
│   │   ├── organizer/OrganizerTools.kt     # 2 tools
│   │   ├── websocket/WebSocketTools.kt     # 7 tools
│   │   ├── burpsuite/BurpSuiteTools.kt     # 9 tools
│   │   ├── utilities/UtilitiesTools.kt     # 13 tools
│   │   ├── ai/AiTools.kt                   # 2 tools
│   │   ├── bambda/BambdaTools.kt           # 1 tool
│   │   ├── persistence/PersistenceTools.kt # 5 tools
│   │   ├── project/ProjectTools.kt         # 1 tool
│   │   ├── logging/LoggingTools.kt         # 2 tools
│   │   ├── events/EventsTools.kt           # 5 tools
│   │   ├── analysis/AnalysisTools.kt       # 7 tools
│   │   ├── config/ConfigTools.kt           # 7 tools
│   │   ├── session/SessionTools.kt         # 3 tools
│   │   └── extension/ExtensionTools.kt     # 1 tool
│   ├── transport/
│   │   ├── McpServerManager.kt             # MCP server lifecycle
│   │   ├── ToolRegistry.kt                 # Central tool registration
│   │   └── ResourceRegistry.kt            # MCP resource registration
│   ├── events/
│   │   └── EventBus.kt                    # Unified event bus
│   ├── state/
│   │   └── StateManager.kt                # Connection/rule/task state
│   └── resources/
│       └── McpResources.kt                # MCP resource implementations
└── docs/
    ├── BurpMCP-Ultra-Tool-Catalog.md
    └── superpowers/plans/
        └── 2026-03-24-burpmcp-ultra-implementation.md
```

---

## Phase 1: Project Scaffold & Infrastructure

### Task 1: Gradle Build Configuration

**Files:**
- Create: `build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/wrapper/gradle-wrapper.properties`

- [x] **Step 1: Create build files** — DONE
- [x] **Step 2: Verify build resolves dependencies** — DONE (verified with `./gradlew tasks`)

### Task 2: Core Infrastructure

**Files:**
- Create: `core/BurpMcpUltraExtension.kt`
- Create: `bridge/BridgeFactory.kt`
- Create: `events/EventBus.kt`
- Create: `state/StateManager.kt`
- Create: `transport/McpServerManager.kt`
- Create: `transport/ToolRegistry.kt`
- Create: `transport/ResourceRegistry.kt`

- [x] **Step 1: Create all core files** — DONE (7 files, ~931 lines)

---

## Phase 2: Bridge & Tool Implementation (Parallelized)

6 independent work streams running in parallel:

### Task 3: Proxy + HTTP Tools (22 tools)

**Files:**
- Create: `bridge/ProxyBridge.kt`
- Create: `bridge/HttpBridge.kt`
- Create: `tools/proxy/ProxyTools.kt`
- Create: `tools/http/HttpTools.kt`

- [ ] **Step 1: Implement ProxyBridge** — history access, search, intercept control, annotation, rule-based handlers
- [ ] **Step 2: Implement ProxyTools** — 12 tool registrations
- [ ] **Step 3: Implement HttpBridge** — request sending, parallel requests, request chains, cookie jar, keyword/variation analysis, traffic rules
- [ ] **Step 4: Implement HttpTools** — 10 tool registrations

### Task 4: Scanner + Collaborator Tools (18 tools)

**Files:**
- Create: `bridge/ScannerBridge.kt`
- Create: `bridge/CollaboratorBridge.kt`
- Create: `tools/scanner/ScannerTools.kt`
- Create: `tools/collaborator/CollaboratorTools.kt`

- [ ] **Step 1: Implement ScannerBridge** — crawl/audit start, task management, issue handling, report gen, BCheck import, custom scan checks
- [ ] **Step 2: Implement ScannerTools** — 12 tool registrations
- [ ] **Step 3: Implement CollaboratorBridge** — client lifecycle, payload generation, interaction polling
- [ ] **Step 4: Implement CollaboratorTools** — 6 tool registrations

### Task 5: Repeater + Intruder + Sitemap + Scope Tools (12 tools)

**Files:**
- Create: `bridge/RepeaterBridge.kt`, `bridge/IntruderBridge.kt`, `bridge/SitemapBridge.kt`, `bridge/ScopeBridge.kt`
- Create: `tools/repeater/RepeaterTools.kt`, `tools/intruder/IntruderTools.kt`, `tools/sitemap/SitemapTools.kt`, `tools/scope/ScopeTools.kt`

- [ ] **Step 1: Implement all 4 bridges**
- [ ] **Step 2: Implement all 4 tool registrations**

### Task 6: Comparer + Decoder + Organizer + WebSocket Tools (11 tools)

**Files:**
- Create: `bridge/ComparerBridge.kt`, `bridge/DecoderBridge.kt`, `bridge/OrganizerBridge.kt`, `bridge/WebSocketBridge.kt`
- Create: `tools/comparer/ComparerTools.kt`, `tools/decoder/DecoderTools.kt`, `tools/organizer/OrganizerTools.kt`, `tools/websocket/WebSocketTools.kt`

- [ ] **Step 1: Implement all 4 bridges** (WebSocketBridge is the most complex — connection lifecycle management)
- [ ] **Step 2: Implement all 4 tool registrations**

### Task 7: BurpSuite + Utilities + AI + Bambda Tools (25 tools)

**Files:**
- Create: `bridge/BurpSuiteBridge.kt`, `bridge/UtilitiesBridge.kt`, `bridge/AiBridge.kt`, `bridge/BambdaBridge.kt`
- Create: `tools/burpsuite/BurpSuiteTools.kt`, `tools/utilities/UtilitiesTools.kt`, `tools/ai/AiTools.kt`, `tools/bambda/BambdaTools.kt`

- [ ] **Step 1: Implement all 4 bridges**
- [ ] **Step 2: Implement all 4 tool registrations**

### Task 8: Remaining Tools (33 tools)

**Files:**
- Create: 16 files (bridges + tools for Persistence, Project, Logging, Events, Analysis, Config, Session, Extension)

- [ ] **Step 1: Implement Persistence bridge + tools** (5 tools)
- [ ] **Step 2: Implement Project bridge + tools** (1 tool)
- [ ] **Step 3: Implement Logging bridge + tools** (2 tools)
- [ ] **Step 4: Implement Events tools** (5 tools, operates on EventBus directly)
- [ ] **Step 5: Implement Analysis bridge + tools** (7 tools, custom algorithms)
- [ ] **Step 6: Implement Config bridge + tools** (7 tools, JSON config manipulation)
- [ ] **Step 7: Implement Session bridge + tools** (3 tools)
- [ ] **Step 8: Implement Extension tools** (1 tool)

---

## Phase 3: MCP Resources & Event Integration

### Task 9: MCP Resources

**Files:**
- Modify: `transport/ResourceRegistry.kt`

- [ ] **Step 1: Implement 8 static resources** (proxy/history, proxy/websocket/history, scanner/issues, sitemap, scope, config/project, config/user, organizer/items)
- [ ] **Step 2: Implement 6 subscribable resources** (proxy/history/live, proxy/websocket/live, scanner/issues/live, scope/live, collaborator/interactions, events)
- [ ] **Step 3: Wire resource subscriptions to EventBus**

---

## Phase 4: Compilation & Integration

### Task 10: Compile and Fix

- [ ] **Step 1: Run `./gradlew shadowJar`**
- [ ] **Step 2: Fix compilation errors** (import mismatches, API signature differences)
- [ ] **Step 3: Verify fat JAR size and contents**
- [ ] **Step 4: Test loading in Burp Suite Pro**

### Task 11: Integration Testing

- [ ] **Step 1: Load extension in Burp Suite Pro**
- [ ] **Step 2: Connect MCP client to SSE transport**
- [ ] **Step 3: Test tool discovery (list all 121 tools)**
- [ ] **Step 4: Test core tools** (proxy_history, http_send_request, scanner_start_audit, scope_include)
- [ ] **Step 5: Test event streaming**
- [ ] **Step 6: Test MCP resource subscriptions**

---

## Dependency Graph

```
Phase 1 (scaffold) ──┬──> Task 3 (Proxy+HTTP) ──────────┐
                      ├──> Task 4 (Scanner+Collab) ──────┤
                      ├──> Task 5 (Repeat+Intrud+Site) ──┤
                      ├──> Task 6 (Comp+Dec+Org+WS) ─────┼──> Task 9 (Resources) ──> Task 10 (Compile)
                      ├──> Task 7 (Burp+Util+AI+Bamb) ───┤
                      └──> Task 8 (Remaining 33) ─────────┘
```

Tasks 3-8 are fully independent and run in parallel.
Task 9 depends on all bridges being available.
Task 10 depends on everything.
