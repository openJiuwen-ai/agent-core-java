package com.openjiuwen.harness.tools;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Mirrors Python's {@code test_web_tools.py} in
 * {@code tests.unit_tests.harness.tools.test_web_tools}.
 */
class WebToolsTest {

    @AfterEach
    void clearProperties() {
        for (String key : List.of(
                "FREE_SEARCH_DDG_ENABLED",
                "FREE_SEARCH_BING_ENABLED",
                "FREE_SEARCH_PROXY_URL",
                "FREE_SEARCH_SSL_VERIFY",
                "FREE_SEARCH_DDG_URL",
                "NO_PROXY",
                "no_proxy",
                "BOCHA_API_KEY",
                "PERPLEXITY_API_KEY",
                "SERPER_API_KEY",
                "JINA_API_KEY"
        )) {
            System.clearProperty(key);
        }
    }

    @Nested
    class TestWebFreeSearchTool {

        @Test
        void testInvokeEmptyQuery() {
            WebFreeSearchTool tool = new WebFreeSearchTool("cn");

            ToolOutput output = (ToolOutput) tool.invoke(Map.of("query", "   "), Map.of());

            assertFalse(output.isSuccess());
            assertEquals("[ERROR]: query cannot be empty.", output.getError());
        }

        @Test
        void testInvokeDuckduckgoSuccess() {
            System.setProperty("FREE_SEARCH_DDG_ENABLED", "true");
            System.setProperty("FREE_SEARCH_BING_ENABLED", "false");
            WebFreeSearchTool tool = new WebFreeSearchTool("cn") {
                @Override
                protected String httpGet(String url, String query) {
                    assertTrue(url.contains("duckduckgo.com"));
                    assertEquals("test query", query);
                    return """
                            <a class="result__a" href="https://duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fpage1">Example Title 1</a>
                            <a class="result__snippet" href="#">Example snippet text 1</a>
                            """;
                }
            };

            ToolOutput output = (ToolOutput) tool.invoke(Map.of("query", "test query", "max_results", 5), Map.of());

            assertTrue(output.isSuccess());
            Map<String, Object> data = data(output);
            assertEquals("DuckDuckGo", data.get("engine"));
            assertTrue(String.valueOf(data.get("text")).contains("Free search results (DuckDuckGo)"));
            assertTrue(String.valueOf(data.get("text")).contains("Example Title 1"));
        }

        @Test
        void testInvokeBingFallbackSuccess() {
            System.setProperty("FREE_SEARCH_DDG_ENABLED", "true");
            System.setProperty("FREE_SEARCH_BING_ENABLED", "true");
            WebFreeSearchTool tool = new WebFreeSearchTool("cn") {
                @Override
                protected String httpGet(String url, String query) {
                    if (url.contains("duckduckgo.com") || url.contains("r.jina.ai")) {
                        throw new IllegalStateException("duckduckgo unavailable");
                    }
                    return bingHtml("Bing Result 1", "https://example.com/page1", "Bing snippet 1");
                }
            };

            ToolOutput output = (ToolOutput) tool.invoke(Map.of("query", "test query", "max_results", 5), Map.of());

            assertTrue(output.isSuccess());
            Map<String, Object> data = data(output);
            assertEquals("Bing", data.get("engine"));
            assertTrue(String.valueOf(data.get("text")).contains("Free search results (Bing)"));
            assertTrue(String.valueOf(data.get("text")).contains("Bing Result 1"));
        }

        @Test
        void testDdgToggleDisablesDuckduckgoEngines() {
            System.setProperty("FREE_SEARCH_DDG_ENABLED", "false");
            System.setProperty("FREE_SEARCH_BING_ENABLED", "true");
            List<String> requestedUrls = new ArrayList<>();
            WebFreeSearchTool tool = new WebFreeSearchTool("cn") {
                @Override
                protected String httpGet(String url, String query) {
                    requestedUrls.add(url);
                    if (url.contains("duckduckgo.com") || url.contains("r.jina.ai")) {
                        fail("DuckDuckGo engines should not be requested when disabled");
                    }
                    return bingHtml("Bing Result 1", "https://example.com/page1", "Bing snippet 1");
                }
            };

            ToolOutput output = (ToolOutput) tool.invoke(Map.of("query", "test query", "max_results", 5), Map.of());

            assertTrue(output.isSuccess());
            assertTrue(requestedUrls.stream().noneMatch(url -> url.contains("duckduckgo.com")));
            assertTrue(requestedUrls.stream().noneMatch(url -> url.contains("r.jina.ai")));
            assertTrue(String.valueOf(data(output).get("text")).contains("Free search results (Bing)"));
        }

        @Test
        void testAllFreeSearchEnginesDisabledReturnsError() {
            System.setProperty("FREE_SEARCH_DDG_ENABLED", "false");
            System.setProperty("FREE_SEARCH_BING_ENABLED", "false");
            WebFreeSearchTool tool = new WebFreeSearchTool("cn");

            ToolOutput output = (ToolOutput) tool.invoke(Map.of("query", "test query", "max_results", 5), Map.of());

            assertFalse(output.isSuccess());
            assertTrue(output.getError().contains("[ERROR]: free search failed:"));
            assertTrue(output.getError().contains("all free search engines are disabled"));
        }

        @Test
        void testCreateWebToolsOmitsFreeSearchByDefault() {
            List<Tool> tools = WebTools.createWebTools("cn");

            assertFalse(WebTools.isFreeSearchEnabled());
            assertEquals(List.of("fetch_webpage"), tools.stream().map(tool -> tool.getCard().getName()).toList());
        }

        @Test
        void testCreateWebToolsOmitsFreeSearchWhenAllEnginesDisabled() {
            System.setProperty("FREE_SEARCH_DDG_ENABLED", "false");
            System.setProperty("FREE_SEARCH_BING_ENABLED", "false");

            List<Tool> tools = WebTools.createWebTools("cn");

            assertFalse(WebTools.isFreeSearchEnabled());
            assertEquals(List.of("fetch_webpage"), tools.stream().map(tool -> tool.getCard().getName()).toList());
        }

        @Test
        void testCreateWebToolsRestoresFreeSearchWhenAnyEngineEnabled() {
            System.setProperty("FREE_SEARCH_DDG_ENABLED", "false");
            System.setProperty("FREE_SEARCH_BING_ENABLED", "true");

            List<Tool> tools = WebTools.createWebTools("cn");

            assertTrue(WebTools.isFreeSearchEnabled());
            assertEquals(List.of("free_search", "fetch_webpage"), tools.stream().map(tool -> tool.getCard().getName()).toList());
        }

        @Test
        void testCreateWebToolsPrioritizesPaidSearchWhenConfigured() {
            System.setProperty("BOCHA_API_KEY", "test-key");
            System.setProperty("FREE_SEARCH_DDG_ENABLED", "false");
            System.setProperty("FREE_SEARCH_BING_ENABLED", "true");

            List<Tool> tools = WebTools.createWebTools("cn");

            assertTrue(WebTools.isPaidSearchEnabled());
            assertEquals(List.of("paid_search", "free_search", "fetch_webpage"), tools.stream().map(tool -> tool.getCard().getName()).toList());
        }

        @Test
        void testBestEffortReturnsLowQualityBingRows() {
            System.setProperty("FREE_SEARCH_DDG_ENABLED", "true");
            System.setProperty("FREE_SEARCH_BING_ENABLED", "true");
            WebFreeSearchTool tool = new WebFreeSearchTool("cn") {
                @Override
                protected String httpGet(String url, String query) {
                    if (url.contains("duckduckgo.com") || url.contains("r.jina.ai")) {
                        throw new IllegalStateException("duckduckgo unavailable");
                    }
                    return bingHtml("亚洲 - 知乎", "https://www.zhihu.com/question/1", "知乎页面");
                }
            };

            ToolOutput output = (ToolOutput) tool.invoke(Map.of("query", "亚洲新闻 最新", "max_results", 5), Map.of());

            assertTrue(output.isSuccess());
            assertTrue(String.valueOf(data(output).get("text")).contains("Free search results (Bing)"));
            assertTrue(String.valueOf(data(output).get("text")).contains("亚洲 - 知乎"));
        }

        @Test
        void testStreamNotSupported() {
            WebFreeSearchTool tool = new WebFreeSearchTool("cn");

            BaseError error = assertThrows(BaseError.class, () -> tool.stream(Map.of("query", "test"), Map.of()));

            assertEquals(StatusCode.TOOL_STREAM_NOT_SUPPORTED, error.getStatus());
        }

        @Test
        void testHttpRequestAppliesConfiguredSearchProxy() {
            System.setProperty("FREE_SEARCH_PROXY_URL", "http://username:password@proxyhk.huawei.com:8080");
            System.setProperty("NO_PROXY", "");
            System.setProperty("no_proxy", "");

            Proxy proxy = WebFreeSearchTool.proxyForUrl("https://www.bing.com/search?q=test");

            assertEquals(Proxy.Type.HTTP, proxy.type());
            InetSocketAddress address = (InetSocketAddress) proxy.address();
            assertEquals("proxyhk.huawei.com", address.getHostString());
            assertEquals(8080, address.getPort());
        }

        @Test
        void testHttpRequestBypassesConfiguredSearchProxyForNoProxyHosts() {
            System.setProperty("FREE_SEARCH_PROXY_URL", "http://username:password@proxyhk.huawei.com:8080");
            System.setProperty("NO_PROXY", ".huawei.com");
            System.setProperty("no_proxy", "");

            Proxy proxy = WebFreeSearchTool.proxyForUrl("https://service.huawei.com/path");

            assertEquals(Proxy.NO_PROXY, proxy);
        }
    }

    @Nested
    class TestWebPaidSearchTool {

        @Test
        void testInvokeInvalidProvider() {
            WebPaidSearchTool tool = new WebPaidSearchTool("cn");

            ToolOutput output = (ToolOutput) tool.invoke(Map.of("query", "test", "provider", "invalid"), Map.of());

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

                @Override
                protected String env(String key) {
                    return "BOCHA_API_KEY".equals(key) ? "test-bocha-key" : "";
                }
            };

            ToolOutput output = (ToolOutput) tool.invoke(Map.of("query", "test query", "provider", "bocha"), Map.of());

            assertTrue(output.isSuccess());
            Map<String, Object> data = data(output);
            assertEquals("bocha", data.get("provider"));
            assertTrue(String.valueOf(data.get("text")).contains("Paid search provider: bocha"));
            assertTrue(String.valueOf(data.get("text")).contains("Bocha summary answer."));
            assertTrue(String.valueOf(data.get("text")).contains("https://example.com/page1"));
        }

        @Test
        void testInvokeAutoProviderPrefersBocha() {
            WebPaidSearchTool tool = new WebPaidSearchTool("cn") {
                @Override
                protected SearchResult searchBocha(String query, int maxResults) {
                    return new SearchResult("bocha", "Bocha auto summary.", List.of("https://example.com/bocha"));
                }
            };

            ToolOutput output = (ToolOutput) tool.invoke(Map.of("query", "test query", "provider", "auto"), Map.of());

            assertTrue(output.isSuccess());
            assertEquals("bocha", data(output).get("provider"));
            assertTrue(String.valueOf(data(output).get("text")).contains("Paid search provider: bocha"));
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

            ToolOutput output = (ToolOutput) tool.invoke(Map.of("query", "test query", "provider", "auto"), Map.of());

            assertTrue(output.isSuccess());
            Map<String, Object> data = data(output);
            assertEquals("serper", data.get("provider"));
            assertTrue(String.valueOf(data.get("text")).contains("Paid search provider: serper"));
            assertTrue(String.valueOf(data.get("text")).contains("https://example.com/fallback"));
        }
    }

    @Nested
    class TestWebFetchWebpageTool {

        @Test
        void testInvokeBasicHtmlExtractsMainContent() {
            WebFetchWebpageTool tool = new WebFetchWebpageTool("cn") {
                @Override
                protected FetchResponse httpGet(String url, int timeoutSeconds) {
                    String html = "<html><title>Title</title><body><nav>menu</nav><main><p>Main content paragraph.</p></main></body></html>";
                    return new FetchResponse(url, 200, Map.of("Content-Type", "text/html; charset=utf-8"),
                            html.getBytes(StandardCharsets.UTF_8), "utf-8", "utf-8");
                }
            };

            ToolOutput output = (ToolOutput) tool.invoke(Map.of("url", "https://example.com/article"), Map.of());

            assertTrue(output.isSuccess());
            String text = String.valueOf(data(output).get("text"));
            assertTrue(text.contains("Title: Title"));
            assertTrue(text.contains("Main content paragraph."));
            assertFalse(text.contains("menu"));
        }

        @Test
        void testInvokeMaxCharsZeroDisablesClipping() {
            WebFetchWebpageTool tool = new WebFetchWebpageTool("cn") {
                @Override
                protected FetchResponse httpGet(String url, int timeoutSeconds) {
                    return new FetchResponse(url, 200, Map.of("Content-Type", "text/plain"),
                            "abcdefghij".getBytes(StandardCharsets.UTF_8), "utf-8", "utf-8");
                }
            };

            ToolOutput output = (ToolOutput) tool.invoke(Map.of("url", "https://example.com/article", "max_chars", 0), Map.of());

            assertTrue(output.isSuccess());
            String text = String.valueOf(data(output).get("text"));
            assertTrue(text.contains("abcdefghij"));
            assertFalse(text.contains("[truncated]"));
        }

        @Test
        void testDecodeResponseTextPrefersNonMojibake() {
            byte[] content = "【杭州24小时天气查询】".getBytes(StandardCharsets.UTF_8);

            String decoded = WebFetchWebpageTool.decodeResponseText(
                    content,
                    Map.of("Content-Type", "text/html"),
                    "cp1252",
                    "cp1252"
            );

            assertTrue(decoded.contains("杭州"));
        }

        @Test
        void testStreamNotSupported() {
            WebFetchWebpageTool tool = new WebFetchWebpageTool("cn");

            BaseError error = assertThrows(BaseError.class, () -> tool.stream(Map.of("url", "https://example.com"), Map.of()));

            assertEquals(StatusCode.TOOL_STREAM_NOT_SUPPORTED, error.getStatus());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(ToolOutput output) {
        return assertInstanceOf(Map.class, output.getData());
    }

    private static String bingHtml(String title, String url, String snippet) {
        return """
                <html>
                <main aria-label="Search Results">
                  <li class="b_algo">
                    <h2><a href="%s">%s</a></h2>
                    <div class="b_caption"><p>%s</p></div>
                  </li>
                </main>
                </html>
                """.formatted(url, title, snippet);
    }
}
