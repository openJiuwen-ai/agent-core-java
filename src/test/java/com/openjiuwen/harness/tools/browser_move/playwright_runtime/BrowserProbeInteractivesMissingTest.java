/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.tools.ToolOutput;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's
 * {@code tests/unit_tests/harness/tools/browser_move/test_browser_probe_interactives.py}.</p>
 */
class BrowserProbeInteractivesMissingTest {

    @Test
    void buildInteractiveProbeJsContainsHighValueSelectors() {
        String js = BrowserProbes.buildInteractiveProbeJs(25, true, "");

        assertTrue(js.contains("button"));
        assertTrue(js.contains("a[href]"));
        assertTrue(js.contains("input"));
        assertTrue(js.contains("[aria-label]"));
        assertTrue(js.contains("[data-testid]"));
        assertTrue(js.contains("max_items"));
        assertTrue(js.contains("viewport_only"));
    }

    @Test
    void buildInteractiveProbeJsClampsMaxItems() {
        String js = BrowserProbes.buildInteractiveProbeJs(999, true, "");

        assertTrue(js.contains("\"max_items\":100"));
    }

    @Test
    void browserProbeInteractivesToolInvokesRuntimeApi() throws Exception {
        RecordingRuntime runtime = new RecordingRuntime(successInteractives());
        BrowserRuntimeTools.BrowserProbeInteractivesTool tool =
                new BrowserRuntimeTools.BrowserProbeInteractivesTool(runtime);

        ToolOutput output = (ToolOutput) tool.invoke(Map.of(
                "max_items", 200,
                "viewport_only", "false",
                "query", "cart"
        ));

        assertEquals(100, runtime.maxItems);
        assertFalse(runtime.viewportOnly);
        assertEquals("cart", runtime.query);
        assertTrue(output.isSuccess());
        assertEquals("Add to cart", firstElement(output).get("text"));
    }

    @Test
    void browserProbeInteractivesToolReportsRuntimeError() throws Exception {
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("ok", false);
        failure.put("error", "browser_code_executor_not_ready");
        failure.put("elements", List.of());
        BrowserRuntimeTools.BrowserProbeInteractivesTool tool =
                new BrowserRuntimeTools.BrowserProbeInteractivesTool(new RecordingRuntime(failure));

        ToolOutput output = (ToolOutput) tool.invoke(Map.of());

        assertFalse(output.isSuccess());
        assertEquals("browser_code_executor_not_ready", output.getError());
        assertEquals(List.of(), dataMap(output).get("elements"));
    }

    @Test
    void runtimeProbeInteractivesUsesCodeExecutorAndParsesJson() {
        BrowserAgentRuntime runtime = makeRuntime();
        AtomicReference<String> capturedJs = new AtomicReference<>();
        runtime.setCodeExecutor(js -> {
            capturedJs.set(js);
            return Map.of(
                    "content",
                    List.of(Map.of(
                            "type", "text",
                            "text", "{\"ok\":true,\"url\":\"https://example.com\","
                                    + "\"title\":\"Example\","
                                    + "\"elements\":[{\"id\":\"e1\",\"role\":\"button\","
                                    + "\"text\":\"Search\",\"selector_hint\":\"button:nth-of-type(1)\"}]}"
                    ))
            );
        });

        Map<String, Object> result = runtime.probeInteractives(10, true, "search");

        assertTrue(Boolean.TRUE.equals(result.get("ok")));
        assertEquals("https://example.com", result.get("url"));
        assertEquals("Search", firstElement(result).get("text"));
        assertNotNull(capturedJs.get());
        assertTrue(capturedJs.get().contains("\"max_items\":10"));
        assertTrue(capturedJs.get().contains("search"));
    }

    @Test
    void runtimeProbeInteractivesHandlesMissingCodeExecutor() {
        BrowserAgentRuntime runtime = makeRuntime();

        Map<String, Object> result = runtime.probeInteractives(50, true, "");

        assertFalse(Boolean.TRUE.equals(result.get("ok")));
        assertEquals("browser_code_executor_not_ready", result.get("error"));
        assertEquals(List.of(), result.get("elements"));
    }

    @Test
    void runtimePlaywrightClientLookupKeysIncludeServerNameVariants() {
        BrowserAgentRuntime runtime = makeRuntime();
        runtime.getService().getMcpConfig().setServerId("playwright_official_stdio");
        runtime.getService().getMcpConfig().setServerName("playwright-official");

        List<String> keys = runtime.playwrightClientLookupKeys();

        assertTrue(keys.contains("playwright_official_stdio"));
        assertTrue(keys.contains("playwright-official"));
        assertTrue(keys.contains("playwright_official"));
        assertTrue(keys.contains("playwright"));
    }

    @Test
    void runtimeUnwrapsMcpTextResult() {
        Object raw = Map.of(
                "content",
                List.of(Map.of(
                        "type", "text",
                        "text", "{\"ok\": true, \"elements\": []}"
                ))
        );

        assertEquals("{\"ok\": true, \"elements\": []}", BrowserAgentRuntime.unwrapMcpTextResult(raw));
    }

    @Test
    void runtimeCallPlaywrightRunCodeUnsafeUsesRunnerMcpTool() {
        BrowserAgentRuntime runtime = makeRuntime();
        FakeRunCodeTool fakeTool = new FakeRunCodeTool();
        runtime.setPlaywrightMcpToolResolver(name -> "browser_run_code_unsafe".equals(name) ? fakeTool : null);

        Object result = runtime.callPlaywrightRunCodeUnsafe("async (page) => ({ok: true})");

        assertEquals(Map.of("code", "async (page) => ({ok: true})"), fakeTool.inputs);
        assertEquals("{\"ok\": true, \"elements\": []}", firstContentText(result));
    }

    private static BrowserAgentRuntime makeRuntime() {
        return new BrowserAgentRuntime(
                "openai",
                "test-key",
                "https://example.invalid/v1",
                "test-model",
                McpServerConfig.builder()
                        .serverId("test-playwright-runtime")
                        .serverName("test-playwright-runtime")
                        .serverPath("stdio://playwright")
                        .clientType("stdio")
                        .params(Map.of("cwd", "."))
                        .build(),
                new BrowserRunGuardrails(3, 1, 30, false, false)
        );
    }

    private static Map<String, Object> successInteractives() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("elements", List.of(Map.of(
                "id", "e1",
                "role", "button",
                "text", "Add to cart",
                "selector_hint", "button:nth-of-type(1)"
        )));
        result.put("error", null);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dataMap(ToolOutput output) {
        return (Map<String, Object>) output.getData();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstElement(ToolOutput output) {
        return (Map<String, Object>) ((List<?>) dataMap(output).get("elements")).getFirst();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstElement(Map<String, Object> result) {
        return (Map<String, Object>) ((List<?>) result.get("elements")).getFirst();
    }

    @SuppressWarnings("unchecked")
    private static String firstContentText(Object result) {
        Map<String, Object> data = (Map<String, Object>) result;
        Map<String, Object> content = (Map<String, Object>) ((List<?>) data.get("content")).getFirst();
        return String.valueOf(content.get("text"));
    }

    /**
     * Mirrors Python's mocked {@code BrowserAgentRuntime} in
     * {@code tests/unit_tests/harness/tools/browser_move/test_browser_probe_interactives.py}.
     */
    private static final class RecordingRuntime extends BrowserAgentRuntime {
        private final Map<String, Object> result;
        private int maxItems;
        private boolean viewportOnly;
        private String query;

        private RecordingRuntime(Map<String, Object> result) {
            super(
                    "openai",
                    "test-key",
                    "https://example.invalid/v1",
                    "test-model",
                    McpServerConfig.builder()
                            .serverId("test-playwright-runtime")
                            .serverName("test-playwright-runtime")
                            .serverPath("stdio://playwright")
                            .clientType("stdio")
                            .params(Map.of("cwd", "."))
                            .build(),
                    new BrowserRunGuardrails(3, 1, 30, false, false)
            );
            this.result = result;
        }

        @Override
        public Map<String, Object> probeInteractives(int maxItems, boolean viewportOnly, String query) {
            this.maxItems = maxItems;
            this.viewportOnly = viewportOnly;
            this.query = query;
            return result;
        }
    }

    private static final class FakeRunCodeTool extends Tool {
        private Map<String, Object> inputs;

        private FakeRunCodeTool() {
            super(ToolCard.builder()
                    .id("fake-browser-run-code")
                    .name("browser_run_code_unsafe")
                    .description("fake")
                    .inputParams(Map.of("type", "object"))
                    .build());
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            this.inputs = new LinkedHashMap<>(inputs);
            return ToolOutput.success(Map.of(
                    "content",
                    List.of(Map.of(
                            "type", "text",
                            "text", "{\"ok\": true, \"elements\": []}"
                    ))
            ));
        }
    }
}
