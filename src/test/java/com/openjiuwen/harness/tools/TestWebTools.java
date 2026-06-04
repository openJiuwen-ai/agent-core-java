/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_web_tools.py} in
 * {@code tests/unit_tests/harness/tools/test_web_tools.py}.
 */
@DisplayName("Web tools tests")
class TestWebTools {

    @AfterEach
    void clearSearchProperties() {
        for (String key : List.of(
                "FREE_SEARCH_DDG_ENABLED",
                "FREE_SEARCH_BING_ENABLED",
                "FREE_SEARCH_PROXY_URL",
                "BOCHA_API_KEY",
                "PERPLEXITY_API_KEY",
                "SERPER_API_KEY",
                "JINA_API_KEY",
                "NO_PROXY",
                "no_proxy"
        )) {
            System.clearProperty(key);
        }
    }

    @Nested
    class TestWebFreeSearchTool {

        @Test
        void testInvokeEmptyQuery() {
            WebFreeSearchTool tool = new WebFreeSearchTool("cn");

            ToolOutput output = invoke(tool, Map.of("query", "   "));

            assertFalse(output.isSuccess());
            assertEquals("[ERROR]: query cannot be empty.", output.getError());
        }

        @Test
        void testInvokeDuckduckgoSuccess() {
            System.setProperty("FREE_SEARCH_DDG_ENABLED", "true");
            System.setProperty("FREE_SEARCH_BING_ENABLED", "false");
            WebFreeSearchTool tool = new WebFreeSearchTool("cn") {
                @Override
                protected SearchResult search(String query, int maxResults) {
                    assertEquals("test query", query);
                    assertEquals(5, maxResults);
                    return new SearchResult("DuckDuckGo", List.of(Map.of(
                            "title", "Example Title 1",
                            "url", "https://example.com/page1",
                            "snippet", "Example snippet text 1",
                            "source", "duckduckgo"
                    )));
                }
            };

            ToolOutput output = invoke(tool, Map.of("query", "test query", "max_results", 5));

            assertTrue(output.isSuccess(), output.getError());
            assertTrue(text(output).contains("Free search results (DuckDuckGo)"));
            assertTrue(text(output).contains("Example Title 1"));
        }

        @Test
        void testInvokeBingFallbackSuccess() {
            System.setProperty("FREE_SEARCH_DDG_ENABLED", "true");
            System.setProperty("FREE_SEARCH_BING_ENABLED", "true");
            WebFreeSearchTool tool = new WebFreeSearchTool("cn") {
                @Override
                protected SearchResult searchDuckDuckGo(String query, int maxResults) {
                    throw new IllegalStateException("ddg down");
                }

                @Override
                protected SearchResult searchDuckDuckGoViaJina(String query, int maxResults) {
                    throw new IllegalStateException("jina down");
                }

                @Override
                protected SearchResult searchBing(String query, int maxResults) {
                    return new SearchResult("Bing", List.of(Map.of(
                            "title", "Bing Result 1",
                            "url", "https://example.com/bing",
                            "snippet", "Bing snippet",
                            "source", "bing"
                    )));
                }
            };

            ToolOutput output = invoke(tool, Map.of("query", "test query"));

            assertTrue(output.isSuccess(), output.getError());
            assertTrue(text(output).contains("Free search results (Bing)"));
            assertTrue(text(output).contains("Bing Result 1"));
        }

        @Test
        void testDdgToggleDisablesDuckduckgoEngines() {
            System.setProperty("FREE_SEARCH_DDG_ENABLED", "false");
            System.setProperty("FREE_SEARCH_BING_ENABLED", "true");
            WebFreeSearchTool tool = new WebFreeSearchTool("cn") {
                @Override
                protected SearchResult searchDuckDuckGo(String query, int maxResults) {
                    throw new AssertionError("DuckDuckGo should be disabled");
                }

                @Override
                protected SearchResult searchDuckDuckGoViaJina(String query, int maxResults) {
                    throw new AssertionError("DuckDuckGo Jina should be disabled");
                }

                @Override
                protected SearchResult searchBing(String query, int maxResults) {
                    return new SearchResult("Bing", List.of(Map.of(
                            "title", "Bing Result",
                            "url", "https://example.com/bing",
                            "snippet", "",
                            "source", "bing"
                    )));
                }
            };

            ToolOutput output = invoke(tool, Map.of("query", "test"));

            assertTrue(output.isSuccess(), output.getError());
            assertTrue(text(output).contains("Free search results (Bing)"));
        }

        @Test
        void testAllFreeSearchEnginesDisabledReturnsError() {
            System.setProperty("FREE_SEARCH_DDG_ENABLED", "false");
            System.setProperty("FREE_SEARCH_BING_ENABLED", "false");

            ToolOutput output = invoke(new WebFreeSearchTool("cn"), Map.of("query", "test"));

            assertFalse(output.isSuccess());
            assertTrue(output.getError().contains("[ERROR]: free search failed:"));
            assertTrue(output.getError().contains("all free search engines are disabled"));
        }

        @Test
        void testCreateWebToolsOmitsFreeSearchByDefault() {
            System.setProperty("FREE_SEARCH_DDG_ENABLED", "false");
            System.setProperty("FREE_SEARCH_BING_ENABLED", "false");

            List<Tool> tools = WebTools.createWebTools("cn");

            assertFalse(WebTools.isFreeSearchEnabled());
            assertEquals(List.of("fetch_webpage"), names(tools));
        }

        @Test
        void testCreateWebToolsOmitsFreeSearchWhenAllEnginesDisabled() {
            System.setProperty("FREE_SEARCH_DDG_ENABLED", "false");
            System.setProperty("FREE_SEARCH_BING_ENABLED", "false");

            List<Tool> tools = WebTools.createWebTools("cn");

            assertFalse(WebTools.isFreeSearchEnabled());
            assertEquals(List.of("fetch_webpage"), names(tools));
        }

        @Test
        void testCreateWebToolsRestoresFreeSearchWhenAnyEngineEnabled() {
            System.setProperty("FREE_SEARCH_DDG_ENABLED", "false");
            System.setProperty("FREE_SEARCH_BING_ENABLED", "true");

            List<Tool> tools = WebTools.createWebTools("cn");

            assertTrue(WebTools.isFreeSearchEnabled());
            assertEquals(List.of("free_search", "fetch_webpage"), names(tools));
        }

        @Test
        void testCreateWebToolsPrioritizesPaidSearchWhenConfigured() {
            System.setProperty("BOCHA_API_KEY", "test-key");
            System.setProperty("FREE_SEARCH_DDG_ENABLED", "false");
            System.setProperty("FREE_SEARCH_BING_ENABLED", "true");

            List<Tool> tools = WebTools.createWebTools("cn");

            assertTrue(WebTools.isPaidSearchEnabled());
            assertEquals(List.of("paid_search", "free_search", "fetch_webpage"), names(tools));
        }

        @Test
        void testBestEffortReturnsLowQualityBingRows() {
            System.setProperty("FREE_SEARCH_DDG_ENABLED", "true");
            System.setProperty("FREE_SEARCH_BING_ENABLED", "true");
            WebFreeSearchTool tool = new WebFreeSearchTool("cn") {
                @Override
                protected SearchResult searchDuckDuckGo(String query, int maxResults) {
                    throw new IllegalStateException("ddg down");
                }

                @Override
                protected SearchResult searchDuckDuckGoViaJina(String query, int maxResults) {
                    return new SearchResult("DuckDuckGo", List.of());
                }

                @Override
                protected SearchResult searchBing(String query, int maxResults) {
                    return new SearchResult("Bing", List.of(Map.of(
                            "title", "亚洲 - 知乎",
                            "url", "https://example.com/zhihu",
                            "snippet", "",
                            "source", "bing"
                    )));
                }
            };

            ToolOutput output = invoke(tool, Map.of("query", "亚洲"));

            assertTrue(output.isSuccess(), output.getError());
            assertTrue(text(output).contains("Free search results (Bing)"));
            assertTrue(text(output).contains("亚洲 - 知乎"));
        }

        @Test
        void testStreamNotSupported() {
            BaseError error = assertThrows(BaseError.class,
                    () -> new WebFreeSearchTool("cn").stream(Map.of(), Map.of()));

            assertEquals(StatusCode.TOOL_STREAM_NOT_SUPPORTED, error.getStatus());
        }

        @Test
        void testHttpRequestAppliesConfiguredSearchProxy() {
            System.setProperty("FREE_SEARCH_PROXY_URL", "http://proxy.example.com:8080");
            System.clearProperty("NO_PROXY");
            System.clearProperty("no_proxy");

            Proxy proxy = WebFreeSearchTool.proxyForUrl("https://www.bing.com/search?q=test");

            assertNotEquals(Proxy.NO_PROXY, proxy);
            InetSocketAddress address = (InetSocketAddress) proxy.address();
            assertEquals("proxy.example.com", address.getHostString());
            assertEquals(8080, address.getPort());
        }

        @Test
        void testHttpRequestBypassesConfiguredSearchProxyForNoProxyHosts() {
            System.setProperty("FREE_SEARCH_PROXY_URL", "http://proxy.example.com:8080");
            System.clearProperty("NO_PROXY");
            System.clearProperty("no_proxy");

            Proxy proxy = WebFreeSearchTool.proxyForUrl("https://service.huawei.com/path");

            assertSame(Proxy.NO_PROXY, proxy);
        }
    }

    @Nested
    class TestWebPaidSearchTool {

        @Test
        void testInvokeInvalidProvider() {
            ToolOutput output = invoke(new WebPaidSearchTool("cn"), Map.of("query", "test", "provider", "invalid"));

            assertFalse(output.isSuccess());
            assertTrue(output.getError().contains("provider must be one of"));
        }

        @Test
        void testInvokeBochaSuccess() {
            WebPaidSearchTool tool = new WebPaidSearchTool("cn") {
                @Override
                protected SearchResult searchBocha(String query, int maxResults) {
                    assertEquals("test query", query);
                    assertEquals(8, maxResults);
                    return new SearchResult("bocha", "Bocha summary answer.", List.of("https://example.com/page1"));
                }
            };

            ToolOutput output = invoke(tool, Map.of("query", "test query", "provider", "bocha"));

            assertTrue(output.isSuccess(), output.getError());
            assertEquals("bocha", data(output).get("provider"));
            assertTrue(text(output).contains("Paid search provider: bocha"));
            assertTrue(text(output).contains("Bocha summary answer."));
            assertTrue(text(output).contains("https://example.com/page1"));
        }

        @Test
        void testInvokeAutoProviderPrefersBocha() {
            WebPaidSearchTool tool = new WebPaidSearchTool("cn") {
                @Override
                protected SearchResult searchBocha(String query, int maxResults) {
                    return new SearchResult("bocha", "Preferred answer.", List.of("https://example.com/bocha"));
                }

                @Override
                protected String env(String key) {
                    return "x";
                }
            };

            ToolOutput output = invoke(tool, Map.of("query", "test query", "provider", "auto"));

            assertTrue(output.isSuccess(), output.getError());
            assertEquals("bocha", data(output).get("provider"));
            assertTrue(text(output).contains("Preferred answer."));
        }

        @Test
        void testInvokeAutoProviderFallback() {
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

            ToolOutput output = invoke(tool, Map.of("query", "test query", "provider", "auto"));

            assertTrue(output.isSuccess(), output.getError());
            assertEquals("serper", data(output).get("provider"));
            assertTrue(text(output).contains("Paid search provider: serper"));
            assertTrue(text(output).contains("https://example.com/fallback"));
        }
    }

    @Nested
    class TestWebFetchWebpageTool {

        @Test
        void testInvokeBasicHtmlExtractsMainContent() {
            WebFetchWebpageTool.ExtractedContent extracted = WebFetchWebpageTool.extractMainTextFromHtml(
                    "<html><head><title>Title</title></head><body><nav>menu</nav>"
                            + "<main><p>Main content paragraph.</p></main></body></html>");

            assertEquals("Title", extracted.title());
            assertTrue(extracted.content().contains("Main content paragraph."));
            assertFalse(extracted.content().contains("menu"));
        }

        @Test
        void testInvokeMaxCharsZeroDisablesClipping() {
            WebFetchWebpageTool tool = new WebFetchWebpageTool("cn") {
                @Override
                protected FetchedPage fetchWebpage(String url, int timeoutSeconds) {
                    return new FetchedPage(url, 200, "Title", "abcdefghij");
                }
            };

            ToolOutput output = invoke(tool, Map.of("url", "https://example.com", "max_chars", 0));

            assertTrue(output.isSuccess(), output.getError());
            assertTrue(text(output).contains("abcdefghij"));
            assertFalse(text(output).contains("[truncated]"));
        }

        @Test
        void testDecodeResponseTextPrefersNonMojibake() {
            byte[] raw = "杭州".getBytes(Charset.forName("GBK"));

            String decoded = WebFetchWebpageTool.decodeResponseText(raw, Map.of(), null, "GBK");

            assertTrue(decoded.contains("杭州"));
        }

        @Test
        void testStreamNotSupported() {
            BaseError error = assertThrows(BaseError.class,
                    () -> new WebFetchWebpageTool("cn").stream(Map.of(), Map.of()));

            assertEquals(StatusCode.TOOL_STREAM_NOT_SUPPORTED, error.getStatus());
        }
    }

    private ToolOutput invoke(AbstractHarnessTool tool, Map<String, Object> inputs) {
        try {
            return (ToolOutput) tool.invoke(inputs, Map.of());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ToolOutput output) {
        return (Map<String, Object>) output.getData();
    }

    private String text(ToolOutput output) {
        return String.valueOf(data(output).get("text"));
    }

    private List<String> names(List<Tool> tools) {
        return tools.stream().map(tool -> tool.getCard().getName()).toList();
    }
}
