/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.resourcemanager.TagMatchStrategy;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Runtime kernel for browser lifecycle and deterministic helper actions.
 *
 * <p>Mirrors Python's {@code BrowserAgentRuntime} in
 * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/runtime.py}.</p>
 */
public class BrowserAgentRuntime {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> STRING_OBJECT_MAP = new TypeReference<>() {
    };

    private final BrowserService service;
    private Object browserCustomActionTool;
    private Object browserListActionsTool;
    private Object browserProbeInteractivesTool;
    private Object browserProbeCardsTool;
    private Function<String, Object> codeExecutor;
    private Function<String, Tool> playwrightMcpToolResolver;
    private Supplier<BrowserSelectorCache> selectorCacheSupplier =
            () -> new BrowserSelectorCache(BrowserSiteProfiles.defaultCachePath());

    public BrowserAgentRuntime(
            String provider,
            String apiKey,
            String apiBase,
            String modelName,
            McpServerConfig mcpConfig,
            BrowserRunGuardrails guardrails
    ) {
        BrowserTools.ensureBrowserRuntimeClientPatch();
        this.service = new BrowserService(provider, apiKey, apiBase, modelName, mcpConfig, guardrails);
    }

    public BrowserService getService() {
        return service;
    }

    public void ensureRuntimeReady() {
        service.ensureRuntimeReady();
    }

    public Function<String, Object> getCodeExecutor() {
        return codeExecutor;
    }

    public void setCodeExecutor(Function<String, Object> codeExecutor) {
        this.codeExecutor = codeExecutor;
    }

    public void setPlaywrightMcpToolResolver(Function<String, Tool> playwrightMcpToolResolver) {
        this.playwrightMcpToolResolver = playwrightMcpToolResolver;
    }

    public void setSelectorCacheSupplier(Supplier<BrowserSelectorCache> selectorCacheSupplier) {
        this.selectorCacheSupplier = selectorCacheSupplier == null
                ? () -> new BrowserSelectorCache(BrowserSiteProfiles.defaultCachePath())
                : selectorCacheSupplier;
    }

    public void ensureStarted() {
        service.ensureStarted();
        if (browserCustomActionTool != null) {
            return;
        }
        java.util.List<com.openjiuwen.core.foundation.tool.Tool> tools =
                BrowserRuntimeTools.buildBrowserRuntimeTools(this, "en");
        browserCustomActionTool = tools.stream()
                .filter(tool -> "browser_custom_action".equals(tool.getCard().getName()))
                .findFirst()
                .orElse(null);
        browserListActionsTool = tools.stream()
                .filter(tool -> "browser_list_custom_actions".equals(tool.getCard().getName()))
                .findFirst()
                .orElse(null);
        browserProbeInteractivesTool = tools.stream()
                .filter(tool -> "browser_probe_interactives".equals(tool.getCard().getName()))
                .findFirst()
                .orElse(null);
        browserProbeCardsTool = tools.stream()
                .filter(tool -> "browser_probe_cards".equals(tool.getCard().getName()))
                .findFirst()
                .orElse(null);
    }

    public Map<String, Object> cancelRun(String sessionId, String requestId) {
        return service.requestCancel(sessionId, requestId);
    }

    public Map<String, Object> clearCancel(String sessionId, String requestId) {
        return service.clearCancel(sessionId, requestId);
    }

    public Map<String, Object> runBrowserTask(
            String task,
            String sessionId,
            String requestId,
            Integer timeoutSeconds
    ) {
        ensureStarted();
        return service.runTask(task, sessionId, requestId, timeoutSeconds);
    }

    public Map<String, Object> runCustomAction(
            String action,
            String sessionId,
            String requestId,
            Map<String, Object> params
    ) {
        ensureRuntimeReady();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", false);
        result.put("action", action == null ? "" : action);
        result.put("session_id", sessionId == null ? "" : sessionId);
        result.put("request_id", requestId == null ? "" : requestId);
        result.put("params", params == null ? Map.of() : new LinkedHashMap<>(params));
        result.put("error", "browser_custom_action_controller_not_bound");
        return result;
    }

    public Map<String, Object> probeInteractives(int maxItems, boolean viewportOnly, String query) {
        ensureRuntimeReady();
        int clampedMaxItems = Math.max(1, Math.min(maxItems, 100));
        String normalizedQuery = query == null ? "" : query;
        if (codeExecutor == null) {
            return Map.of(
                    "ok", false,
                    "error", "browser_code_executor_not_ready",
                    "elements", java.util.List.of(),
                    "max_items", clampedMaxItems,
                    "viewport_only", viewportOnly,
                    "query", normalizedQuery
            );
        }

        String jsCode = BrowserProbes.buildInteractiveProbeJs(clampedMaxItems, viewportOnly, normalizedQuery);
        Object raw;
        try {
            raw = unwrapMcpTextResult(codeExecutor.apply(jsCode));
        } catch (RuntimeException exception) {
            return Map.of(
                    "ok", false,
                    "error", "browser_probe_interactives failed: " + exception.getMessage(),
                    "elements", java.util.List.of()
            );
        }

        Map<String, Object> parsed = extractJsonObject(raw);
        if (parsed.isEmpty()) {
            return Map.of(
                    "ok", false,
                    "error", "Could not parse browser_probe_interactives result JSON",
                    "raw_preview", preview(raw),
                    "elements", java.util.List.of()
            );
        }

        parsed.putIfAbsent("ok", true);
        parsed.putIfAbsent("error", null);
        parsed.putIfAbsent("elements", List.of());
        return parsed;
    }

    public List<String> playwrightClientLookupKeys() {
        List<String> result = new ArrayList<>();
        McpServerConfig mcpConfig = service.getMcpConfig();
        String serverId = mcpConfig == null ? "" : trimToEmpty(mcpConfig.getServerId());
        String serverName = mcpConfig == null ? "" : trimToEmpty(mcpConfig.getServerName());

        appendLookupKey(result, serverId);
        appendLookupKey(result, serverName);
        appendLookupKey(result, serverId.replace("-", "_"));
        appendLookupKey(result, serverId.replace("_", "-"));
        appendLookupKey(result, serverName.replace("-", "_"));
        appendLookupKey(result, serverName.replace("_", "-"));
        appendLookupKey(result, "playwright_official_stdio");
        appendLookupKey(result, "playwright-official");
        appendLookupKey(result, "playwright");
        return result;
    }

    public Object callPlaywrightRunCodeUnsafe(String jsCode) {
        Tool tool;
        String toolName;
        try {
            toolName = "browser_run_code_unsafe";
            tool = getPlaywrightMcpTool(toolName);
        } catch (RuntimeException exception) {
            toolName = "browser_run_code";
            tool = getPlaywrightMcpTool(toolName);
        }

        try {
            Object result = tool.invoke(Map.of("code", jsCode), Map.of());
            return unwrapToolInvocationResult(result, toolName);
        } catch (Exception exception) {
            throw new IllegalStateException(toolName + " failed: " + exception.getMessage(), exception);
        }
    }

    private Tool getPlaywrightMcpTool(String toolName) {
        if (playwrightMcpToolResolver != null) {
            Tool resolved = playwrightMcpToolResolver.apply(toolName);
            if (resolved != null) {
                return resolved;
            }
        }

        List<String> tried = new ArrayList<>();
        List<String> keys = playwrightClientLookupKeys();
        for (String key : keys) {
            tried.add("server_id=" + key);
            Tool tool = firstTool(Runner.getResourceMgr().getMcpTool(
                    List.of(toolName),
                    List.of(key),
                    List.of(),
                    null,
                    TagMatchStrategy.ALL,
                    true,
                    true,
                    null
            ).toCompletableFuture().join());
            if (tool != null) {
                return tool;
            }
        }

        for (String key : keys) {
            tried.add("server_name=" + key);
            Tool tool = firstTool(Runner.getResourceMgr().getMcpTool(
                    List.of(toolName),
                    List.of(),
                    List.of(key),
                    null,
                    TagMatchStrategy.ALL,
                    true,
                    true,
                    null
            ).toCompletableFuture().join());
            if (tool != null) {
                return tool;
            }
        }

        throw new IllegalStateException(
                "Registered Playwright MCP tool not found: " + toolName + ". Tried " + String.join(", ", tried)
        );
    }

    private static Tool firstTool(List<Tool> tools) {
        if (tools == null) {
            return null;
        }
        for (Tool tool : tools) {
            if (tool != null) {
                return tool;
            }
        }
        return null;
    }

    private static Object unwrapToolInvocationResult(Object result, String toolName) {
        if (result instanceof ToolOutput output) {
            if (!output.isSuccess()) {
                String error = output.getError() == null || output.getError().isBlank()
                        ? toolName + " failed"
                        : output.getError();
                throw new IllegalStateException(error);
            }
            if (output.getData() != null) {
                return output.getData();
            }
        }
        return result;
    }

    public Map<String, Object> probeCards(int maxCards, boolean viewportOnly, boolean includeButtons, String query) {
        ensureRuntimeReady();
        int clampedMaxCards = Math.max(1, Math.min(maxCards, 50));
        String normalizedQuery = query == null ? "" : query;
        if (codeExecutor == null) {
            return Map.of(
                    "ok", false,
                    "error", "browser_code_executor_not_ready",
                    "cards", java.util.List.of(),
                    "max_cards", clampedMaxCards,
                    "viewport_only", viewportOnly,
                    "include_buttons", includeButtons,
                    "query", normalizedQuery
            );
        }

        BrowserSelectorCache selectorCache = selectorCacheSupplier.get();
        String jsCode = BrowserProbes.buildCardProbeJs(
                clampedMaxCards,
                viewportOnly,
                includeButtons,
                normalizedQuery,
                BrowserSiteProfiles.builtinSiteProfiles(),
                selectorCache.exportForProbe()
        );
        Object raw;
        try {
            raw = unwrapMcpTextResult(codeExecutor.apply(jsCode));
        } catch (RuntimeException exception) {
            return Map.of(
                    "ok", false,
                    "error", "browser_probe_cards failed: " + exception.getMessage(),
                    "cards", java.util.List.of()
            );
        }

        Map<String, Object> parsed = extractJsonObject(raw);
        if (parsed.isEmpty()) {
            return Map.of(
                    "ok", false,
                    "error", "Could not parse browser_probe_cards result JSON",
                    "raw_preview", preview(raw),
                    "cards", java.util.List.of()
            );
        }

        parsed.putIfAbsent("ok", true);
        parsed.putIfAbsent("error", null);
        parsed.putIfAbsent("cards", List.of());
        if (Boolean.TRUE.equals(parsed.get("ok"))
                && parsed.get("cards") instanceof List<?> cards
                && !cards.isEmpty()) {
            try {
                selectorCache.recordCardProbeResult(parsed);
            } catch (RuntimeException ignored) {
                // Python logs selector-cache failures and still returns the probe result.
            }
        }
        return parsed;
    }

    public static Object unwrapMcpTextResult(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Object content = map.get("content");
            if (content instanceof List<?> contentList) {
                List<String> texts = new ArrayList<>();
                for (Object item : contentList) {
                    if (!(item instanceof Map<?, ?> contentItem)) {
                        continue;
                    }
                    if (!"text".equals(contentItem.get("type"))) {
                        continue;
                    }
                    Object text = contentItem.get("text");
                    texts.add(String.valueOf(text == null ? "" : text));
                }
                if (!texts.isEmpty()) {
                    return String.join("\n", texts);
                }
            }

            if (map.containsKey("result")) {
                return map.get("result");
            }
            if (map.containsKey("text")) {
                return map.get("text");
            }
            if (map.containsKey("data")) {
                return map.get("data");
            }
        }
        return raw;
    }

    private static Map<String, Object> extractJsonObject(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    result.put(key, entry.getValue());
                }
            }
            return result;
        }

        String text = String.valueOf(raw == null ? "" : raw).trim();
        if (text.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> direct = tryReadMap(text);
        if (!direct.isEmpty()) {
            return direct;
        }

        int start = text.indexOf('{');
        while (start >= 0) {
            int depth = 0;
            boolean inString = false;
            boolean escaped = false;
            for (int index = start; index < text.length(); index++) {
                char current = text.charAt(index);
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (current == '\\') {
                    escaped = true;
                    continue;
                }
                if (current == '"') {
                    inString = !inString;
                    continue;
                }
                if (inString) {
                    continue;
                }
                if (current == '{') {
                    depth++;
                } else if (current == '}') {
                    depth--;
                    if (depth == 0) {
                        Map<String, Object> parsed = tryReadMap(text.substring(start, index + 1));
                        if (!parsed.isEmpty()) {
                            return parsed;
                        }
                        break;
                    }
                }
            }
            start = text.indexOf('{', start + 1);
        }
        return Map.of();
    }

    private static Map<String, Object> tryReadMap(String text) {
        try {
            return OBJECT_MAPPER.readValue(text, STRING_OBJECT_MAP);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private static String preview(Object raw) {
        String text = String.valueOf(raw == null ? "" : raw);
        return text.length() <= 400 ? text : text.substring(0, 400);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static void appendLookupKey(List<String> keys, String value) {
        String candidate = trimToEmpty(value);
        if (!candidate.isEmpty() && !keys.contains(candidate)) {
            keys.add(candidate);
        }
    }

    public Map<String, Object> listActions() {
        return Map.of("ok", true, "actions", java.util.List.of(), "details", Map.of());
    }

    public Map<String, Object> runtimeHealth() {
        return Map.of(
                "ok", service.isConnectionHealthy(),
                "started", service.isStarted(),
                "last_heartbeat_ok", service.getLastHeartbeatOk(),
                "provider", service.getProvider(),
                "api_base", service.getApiBase(),
                "model_name", service.getModelName()
        );
    }

    public void shutdown() {
        service.shutdown();
    }

    public Object getBrowserCustomActionTool() {
        return browserCustomActionTool;
    }

    public Object getBrowserListActionsTool() {
        return browserListActionsTool;
    }

    public Object getBrowserProbeInteractivesTool() {
        return browserProbeInteractivesTool;
    }

    public Object getBrowserProbeCardsTool() {
        return browserProbeCardsTool;
    }
}
