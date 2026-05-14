package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's minimal web-tools factory and free-search behavior slices for P1-02.
 */
class WebToolsTest {

    @AfterEach
    void clearProperties() {
        System.clearProperty("FREE_SEARCH_DDG_ENABLED");
        System.clearProperty("FREE_SEARCH_BING_ENABLED");
        System.clearProperty("BOCHA_API_KEY");
        System.clearProperty("PERPLEXITY_API_KEY");
        System.clearProperty("SERPER_API_KEY");
        System.clearProperty("JINA_API_KEY");
    }

    @Test
    void createWebToolsOmitsFreeSearchByDefault() {
        List<Tool> tools = WebTools.createWebTools("cn");

        assertFalse(WebTools.isFreeSearchEnabled());
        assertEquals(List.of("fetch_webpage"), tools.stream().map(tool -> tool.getCard().getName()).toList());
    }

    @Test
    void createWebToolsRestoresFreeSearchWhenAnyEngineEnabled() {
        System.setProperty("FREE_SEARCH_BING_ENABLED", "true");

        List<Tool> tools = WebTools.createWebTools("cn");

        assertTrue(WebTools.isFreeSearchEnabled());
        assertEquals(List.of("free_search", "fetch_webpage"), tools.stream().map(tool -> tool.getCard().getName()).toList());
    }

    @Test
    void createWebToolsPrioritizesPaidSearchWhenConfigured() {
        System.setProperty("BOCHA_API_KEY", "test-key");
        System.setProperty("FREE_SEARCH_BING_ENABLED", "true");

        List<Tool> tools = WebTools.createWebTools("cn");

        assertTrue(WebTools.isPaidSearchEnabled());
        assertEquals(List.of("paid_search", "free_search", "fetch_webpage"), tools.stream().map(tool -> tool.getCard().getName()).toList());
    }

    @Test
    void freeSearchRequiresQuery() {
        WebFreeSearchTool tool = new WebFreeSearchTool("cn");

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("query", "   "), Map.of());

        assertFalse(output.isSuccess());
        assertEquals("[ERROR]: query cannot be empty.", output.getError());
    }

    @Test
    void freeSearchReturnsFormattedRows() {
        System.setProperty("FREE_SEARCH_DDG_ENABLED", "true");
        WebFreeSearchTool tool = new WebFreeSearchTool("cn") {
            @Override
            protected SearchResult search(String query, int maxResults) {
                assertEquals("test query", query);
                assertEquals(5, maxResults);
                return new SearchResult("DuckDuckGo", List.of(Map.of(
                        "title", "Example Title 1",
                        "url", "https://example.com/page1",
                        "snippet", "Example snippet text 1"
                )));
            }
        };

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("query", "test query", "max_results", 5), Map.of());

        assertTrue(output.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) output.getData();
        assertEquals("DuckDuckGo", data.get("engine"));
        assertTrue(String.valueOf(data.get("text")).contains("Free search results (DuckDuckGo)"));
        assertTrue(String.valueOf(data.get("text")).contains("Example Title 1"));
    }

    @Test
    void freeSearchFallsBackThroughParsingBranches() {
        System.setProperty("FREE_SEARCH_DDG_ENABLED", "true");
        WebFreeSearchTool tool = new WebFreeSearchTool("cn") {
            private int count = 0;

            @Override
            protected String httpGet(String url, String query) {
                count++;
                if (count == 1) {
                    throw new IllegalStateException("duckduckgo unavailable");
                }
                if (count == 2) {
                    return "[Example Title 2](https://example.com/page2)";
                }
                return "<html><main aria-label=\"Search Results\"><li class=\"b_algo\"><h2><a href=\"https://example.com/page3\">Bing Result 3</a></h2><div class=\"b_caption\"><p>Bing snippet 3</p></div></li></main></html>";
            }
        };

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("query", "test query", "max_results", 5), Map.of());

        assertTrue(output.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) output.getData();
        assertEquals("DuckDuckGo", data.get("engine"));
        assertTrue(String.valueOf(data.get("text")).contains("Example Title 2") || String.valueOf(data.get("text")).contains("Bing Result 3"));
    }

    @Test
    void paidSearchRejectsInvalidProvider() {
        WebPaidSearchTool tool = new WebPaidSearchTool("cn");

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("query", "test", "provider", "invalid"), Map.of());

        assertFalse(output.isSuccess());
        assertTrue(output.getError().contains("provider must be one of"));
    }

    @Test
    void paidSearchReturnsBochaPayload() {
        WebPaidSearchTool tool = new WebPaidSearchTool("cn") {
            @Override
            protected SearchResult searchBocha(String query, int maxResults) {
                assertEquals("test query", query);
                assertEquals(8, maxResults);
                return new SearchResult("bocha", "Bocha summary answer.", List.of("https://example.com/page1"));
            }

            @Override
            protected String env(String key) {
                return switch (key) {
                    case "BOCHA_API_KEY" -> "test-bocha-key";
                    default -> "";
                };
            }
        };

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("query", "test query", "provider", "bocha"), Map.of());

        assertTrue(output.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) output.getData();
        assertEquals("bocha", data.get("provider"));
        assertTrue(String.valueOf(data.get("text")).contains("Paid search provider: bocha"));
        assertTrue(String.valueOf(data.get("text")).contains("Bocha summary answer."));
        assertTrue(String.valueOf(data.get("text")).contains("https://example.com/page1"));
    }

    @Test
    void paidSearchAutoFallsBackToSerper() {
        WebPaidSearchTool tool = new WebPaidSearchTool("cn") {
            @Override
            protected SearchResult searchBocha(String query, int maxResults) {
                throw new IllegalStateException("Bocha error");
            }

            @Override
            protected SearchResult searchPerplexity(String query, int maxResults) {
                throw new IllegalStateException("PPLX error");
            }

            @Override
            protected SearchResult searchSerper(String query, int maxResults) {
                return new SearchResult("serper", "", List.of("https://example.com/fallback"));
            }

            @Override
            protected String env(String key) {
                return "x";
            }
        };

        ToolOutput output = (ToolOutput) tool.invoke(Map.of("query", "test query", "provider", "auto"), Map.of());

        assertTrue(output.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) output.getData();
        assertEquals("serper", data.get("provider"));
        assertTrue(String.valueOf(data.get("text")).contains("Paid search provider: serper"));
        assertTrue(String.valueOf(data.get("text")).contains("https://example.com/fallback"));
    }
}
