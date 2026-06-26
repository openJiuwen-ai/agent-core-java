/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.harness.tools.ToolOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's {@code tests/unit_tests/harness/tools/browser_move/test_browser_probe_cards.py}.</p>
 */
class BrowserProbeCardsMissingTest {

    @TempDir
    Path tempDir;

    @Test
    void buildCardProbeJsContainsCardExtractionTerms() {
        String js = BrowserProbes.buildCardProbeJs(10, true, true, "", List.of(), List.of());

        assertTrue(js.contains("max_cards"));
        assertTrue(js.contains("viewport_only"));
        assertTrue(js.contains("recurring_signatures"));
        assertTrue(js.contains("price"));
        assertTrue(js.contains("rating"));
        assertTrue(js.contains("primary_link"));
        assertTrue(js.contains("buttons"));
    }

    @Test
    void buildCardProbeJsClampsMaxCards() {
        String js = BrowserProbes.buildCardProbeJs(999, true, true, "", List.of(), List.of());

        assertTrue(js.contains("\"max_cards\":50"));
    }

    @Test
    void browserProbeCardsToolInvokesRuntimeApi() throws Exception {
        RecordingRuntime runtime = new RecordingRuntime(successCards());
        BrowserRuntimeTools.BrowserProbeCardsTool tool = new BrowserRuntimeTools.BrowserProbeCardsTool(runtime);

        ToolOutput output = (ToolOutput) tool.invoke(Map.of(
                "max_cards", 200,
                "viewport_only", "false",
                "include_buttons", "true",
                "query", "attic"
        ));

        assertEquals(50, runtime.maxCards);
        assertFalse(runtime.viewportOnly);
        assertTrue(runtime.includeButtons);
        assertEquals("attic", runtime.query);
        assertTrue(output.isSuccess());
        assertEquals("A Light in the Attic", firstCard(output).get("title"));
    }

    @Test
    void browserProbeCardsToolReportsRuntimeError() throws Exception {
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("ok", false);
        failure.put("error", "browser_code_executor_not_ready");
        failure.put("cards", List.of());
        BrowserRuntimeTools.BrowserProbeCardsTool tool = new BrowserRuntimeTools.BrowserProbeCardsTool(
                new RecordingRuntime(failure));

        ToolOutput output = (ToolOutput) tool.invoke(Map.of());

        assertFalse(output.isSuccess());
        assertEquals("browser_code_executor_not_ready", output.getError());
        assertEquals(List.of(), dataMap(output).get("cards"));
    }

    @Test
    void runtimeProbeCardsUsesCodeExecutorAndParsesJson() {
        BrowserAgentRuntime runtime = makeRuntime();
        runtime.setSelectorCacheSupplier(() -> new BrowserSelectorCache(tempDir.resolve("selector_cache.json")));
        runtime.setCodeExecutor(js -> Map.of(
                "content",
                List.of(Map.of(
                        "type", "text",
                        "text", "{\"ok\":true,\"url\":\"https://books.toscrape.com/\","
                                + "\"cards\":[{\"id\":\"card_1\",\"title\":\"Book\",\"price\":\"10.00\"}]}"
                ))
        ));

        Map<String, Object> result = runtime.probeCards(10, true, true, "book");

        assertTrue(Boolean.TRUE.equals(result.get("ok")));
        assertEquals("https://books.toscrape.com/", result.get("url"));
        assertEquals("Book", firstCard(result).get("title"));
        assertNotNull(runtime.getCodeExecutor());
    }

    @Test
    void runtimeProbeCardsHandlesMissingCodeExecutor() {
        BrowserAgentRuntime runtime = makeRuntime();

        Map<String, Object> result = runtime.probeCards(20, true, true, "");

        assertFalse(Boolean.TRUE.equals(result.get("ok")));
        assertEquals("browser_code_executor_not_ready", result.get("error"));
        assertEquals(List.of(), result.get("cards"));
    }

    @Test
    void buildCardProbeJsKeepsNormalizationSimple() {
        String js = BrowserProbes.buildCardProbeJs(10, true, true, "", List.of(), List.of());

        assertTrue(js.contains("const normalize = (value, limit = 180)"));
        assertTrue(js.contains("String(value || '')"));
        assertFalse(js.contains("repairCommonEncoding"));
        assertFalse(js.contains("repairCommandEncoding"));
        assertTrue(js.contains("normalizePriceValue"));
    }

    @Test
    void buildCardProbeJsExtractsStarRatingClasses() {
        String js = BrowserProbes.buildCardProbeJs(10, true, true, "", List.of(), List.of());

        assertTrue(js.contains("ratingClassValue"));
        assertTrue(js.contains("Five stars"));
        assertTrue(js.contains("Three stars"));
        assertTrue(js.contains("One star"));
    }

    @Test
    void buildCardProbeJsBuildsDeeperSelectorHints() {
        String js = BrowserProbes.buildCardProbeJs(10, true, true, "", List.of(), List.of());

        assertTrue(js.contains("depth < 8"));
        assertTrue(js.contains("ol"));
        assertTrue(js.contains("ul"));
        assertTrue(js.contains("nth-of-type"));
    }

    @Test
    void buildCardProbeJsPrefersBetterNestedCardCandidate() {
        String js = BrowserProbes.buildCardProbeJs(10, true, true, "", List.of(), List.of());

        assertTrue(js.contains("conflictsWithExisting"));
        assertTrue(js.contains("itemBetter"));
        assertTrue(js.contains("selected.splice"));
    }

    @Test
    void buildCardProbeJsDoesNotReturnRepeatedSimpleSelectorEarly() {
        String js = BrowserProbes.buildCardProbeJs(10, true, true, "", List.of(), List.of());

        assertTrue(js.contains("document.querySelectorAll(simple).length === 1"));
        assertFalse(js.contains("sameSimple.length === 1"));
    }

    @Test
    void buildCardProbeJsButtonExtractionIgnoresViewportForCardChildren() {
        String js = BrowserProbes.buildCardProbeJs(10, true, true, "", List.of(), List.of());

        assertTrue(js.contains("rect.width < 2 || rect.height < 2"));
        assertTrue(js.contains("style.visibility === 'hidden'"));
        assertTrue(js.contains("Number(style.opacity) === 0"));
    }

    @Test
    void buildCardProbeJsAcceptsSiteProfilesAndSelectorCacheRecords() {
        String js = BrowserProbes.buildCardProbeJs(
                10,
                true,
                true,
                "",
                List.of(Map.ofEntries(
                        Map.entry("id", "test_shop"),
                        Map.entry("domains", List.of("example.com")),
                        Map.entry("route_patterns", List.of("^/search")),
                        Map.entry("card_container_selectors", List.of(".product-card")),
                        Map.entry("title_selectors", List.of(".product-title")),
                        Map.entry("price_selectors", List.of(".product-price")),
                        Map.entry("button_selectors", List.of(".add-to-cart"))
                )),
                List.of(Map.of(
                        "domain", "example.com",
                        "route_signature", "/search",
                        "kind", "card_probe",
                        "selectors", Map.of(
                                "card_container_selectors", List.of(".cached-card"),
                                "title_selectors", List.of(".cached-title")
                        )
                ))
        );

        assertTrue(js.contains("site_profiles"));
        assertTrue(js.contains("selector_cache_records"));
        assertTrue(js.contains(".product-card"));
        assertTrue(js.contains(".cached-card"));
        assertTrue(js.contains("profile_ids"));
        assertTrue(js.contains("cache_records_used"));
    }

    @Test
    void runtimeProbeCardsUnwrapsResultFieldAndRecordsCache() {
        BrowserSelectorCache cache = new BrowserSelectorCache(tempDir.resolve("selector_cache.json"));
        BrowserAgentRuntime runtime = makeRuntime();
        runtime.setSelectorCacheSupplier(() -> cache);
        runtime.setCodeExecutor(js -> Map.of("result", "### Result\n" + booksToScrapeResultJson()));

        Map<String, Object> result = runtime.probeCards(10, true, true, "");

        assertTrue(Boolean.TRUE.equals(result.get("ok")));
        assertEquals("A Light in the Attic", firstCard(result).get("title"));
        List<Map<String, Object>> exported = cache.exportForProbe();
        assertEquals(1, exported.size());
        assertEquals("books.toscrape.com", exported.getFirst().get("domain"));
        assertEquals(1, exported.getFirst().get("success_count"));
        assertTrue(Double.parseDouble(String.valueOf(exported.getFirst().get("quality_score"))) > 0);
    }

    @Test
    void runtimeUnwrapMcpResultField() {
        Object raw = Map.of("result", "### Result\n{\"ok\": true, \"cards\": []}");

        assertEquals("### Result\n{\"ok\": true, \"cards\": []}", BrowserAgentRuntime.unwrapMcpTextResult(raw));
    }

    @Test
    void buildCardProbeJsHasCacheFirstDiagnostics() {
        String js = BrowserProbes.buildCardProbeJs(
                10,
                true,
                true,
                "",
                List.of(),
                List.of(Map.of(
                        "domain", "example.com",
                        "route_signature", "/search",
                        "kind", "card_probe",
                        "selectors", Map.of(
                                "card_container_selectors", List.of(".cached-card"),
                                "title_selectors", List.of(".cached-title")
                        ),
                        "quality_score", 0.9
                ))
        );

        assertTrue(js.contains("cacheSelectors"));
        assertTrue(js.contains("siteProfileSelectors"));
        assertTrue(js.contains("cachedCandidates"));
        assertTrue(js.contains("hasEnoughGoodCards(cachedCandidates)"));
        assertTrue(js.contains("selectorSource = 'cache'"));
        assertTrue(js.contains("cache_accepted"));
        assertTrue(js.contains("selector_source"));
        assertTrue(js.contains("cached_container_selectors"));
    }

    @Test
    void buildCardProbeJsFiltersPageChromeCandidates() {
        String js = BrowserProbes.buildCardProbeJs(10, true, true, "", List.of(), List.of());

        assertTrue(js.contains("looksLikePageChrome"));
        assertTrue(js.toLowerCase().contains("fresh & fast"));
        assertTrue(js.contains("breadcrumb"));
        assertTrue(js.contains("navbar"));
    }

    @Test
    void buildCardProbeJsUsesSelectableNotRawScored() {
        String js = BrowserProbes.buildCardProbeJs(10, true, true, "", List.of(), List.of());

        assertTrue(js.contains("const selectable = scored.filter"));
        assertTrue(js.contains("for (const item of selectable)"));
    }

    private static BrowserAgentRuntime makeRuntime() {
        McpServerConfig mcpConfig = McpServerConfig.builder()
                .serverId("test-playwright-runtime")
                .serverName("test-playwright-runtime")
                .serverPath("stdio://playwright")
                .clientType("stdio")
                .params(Map.of("cwd", "."))
                .build();
        return new BrowserAgentRuntime(
                "openai",
                "test-key",
                "https://example.invalid/v1",
                "test-model",
                mcpConfig,
                new BrowserRunGuardrails(3, 1, 30, false, false)
        );
    }

    private static Map<String, Object> successCards() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("cards", List.of(Map.of(
                "id", "card_1",
                "title", "A Light in the Attic",
                "price", "51.77",
                "selector_hint", "article.product_pod"
        )));
        result.put("error", null);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dataMap(ToolOutput output) {
        return (Map<String, Object>) output.getData();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstCard(ToolOutput output) {
        return (Map<String, Object>) ((List<?>) dataMap(output).get("cards")).getFirst();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstCard(Map<String, Object> result) {
        return (Map<String, Object>) ((List<?>) result.get("cards")).getFirst();
    }

    private static String booksToScrapeResultJson() {
        return """
                {"ok": true,
                 "url": "https://books.toscrape.com/",
                 "profile_ids": ["books_to_scrape"],
                 "cache_records_used": 0,
                 "cards": [
                   {"id": "card_1",
                    "title": "A Light in the Attic",
                    "price": "51.77",
                    "rating": "Three stars",
                    "availability": "In stock",
                    "has_image": true,
                    "primary_link": "https://books.toscrape.com/catalogue/a-light-in-the-attic_1000/index.html",
                    "text_preview": "A Light in the Attic Three stars 51.77 In stock Add to basket",
                    "selector_hint": "ol:nth-of-type(1) > li:nth-of-type(1) > article.product_pod:nth-of-type(1)",
                    "title_selector_hint": "article.product_pod:nth-of-type(1) > h3:nth-of-type(1) > a:nth-of-type(1)",
                    "price_selector_hint": "article.product_pod:nth-of-type(1) > div.product_price:nth-of-type(2) > p.price_color:nth-of-type(1)",
                    "rating_selector_hint": "article.product_pod:nth-of-type(1) > p.star-rating:nth-of-type(1)",
                    "primary_link_selector_hint": "article.product_pod:nth-of-type(1) > h3:nth-of-type(1) > a:nth-of-type(1)",
                    "buttons": [{"selector_hint": "article.product_pod:nth-of-type(1) > div.product_price:nth-of-type(2) > form:nth-of-type(1) > button.btn.btn-primary:nth-of-type(1)"}]},
                   {"id": "card_2",
                    "title": "Tipping the Velvet",
                    "price": "53.74",
                    "rating": "One star",
                    "availability": "In stock",
                    "has_image": true,
                    "primary_link": "https://books.toscrape.com/catalogue/tipping-the-velvet_999/index.html",
                    "text_preview": "Tipping the Velvet One star 53.74 In stock Add to basket",
                    "selector_hint": "ol:nth-of-type(1) > li:nth-of-type(2) > article.product_pod:nth-of-type(1)",
                    "title_selector_hint": "article.product_pod:nth-of-type(1) > h3:nth-of-type(1) > a:nth-of-type(1)",
                    "price_selector_hint": "article.product_pod:nth-of-type(1) > div.product_price:nth-of-type(2) > p.price_color:nth-of-type(1)",
                    "rating_selector_hint": "article.product_pod:nth-of-type(1) > p.star-rating:nth-of-type(1)",
                    "primary_link_selector_hint": "article.product_pod:nth-of-type(1) > h3:nth-of-type(1) > a:nth-of-type(1)",
                    "buttons": [{"selector_hint": "article.product_pod:nth-of-type(1) > div.product_price:nth-of-type(2) > form:nth-of-type(1) > button.btn.btn-primary:nth-of-type(1)"}]}
                 ]}
                """;
    }

    /**
     * Mirrors Python's mocked {@code BrowserAgentRuntime} in
     * {@code tests/unit_tests/harness/tools/browser_move/test_browser_probe_cards.py}.
     */
    private static final class RecordingRuntime extends BrowserAgentRuntime {
        private final Map<String, Object> result;
        private int maxCards;
        private boolean viewportOnly;
        private boolean includeButtons;
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
        public Map<String, Object> probeCards(int maxCards, boolean viewportOnly, boolean includeButtons, String query) {
            this.maxCards = maxCards;
            this.viewportOnly = viewportOnly;
            this.includeButtons = includeButtons;
            this.query = query;
            return result;
        }
    }
}
