/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.tools.WebTools;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Supplemental parity tests for web search and webpage-fetch harness tools.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.harness.tools.test_web_tools} in
 * {@code tests/unit_tests/harness/tools/test_web_tools.py}.</p>
 */
class WebToolsPythonParityTest {

    private static final String SOURCE = "tests/unit_tests/harness/tools/test_web_tools.py";
    private static final List<String> SEARCH_ENV_KEYS = List.of(
            "FREE_SEARCH_DDG_ENABLED",
            "FREE_SEARCH_BING_ENABLED",
            "FREE_SEARCH_PROXY_URL",
            "NO_PROXY",
            "no_proxy",
            "BOCHA_API_KEY",
            "PERPLEXITY_API_KEY",
            "SERPER_API_KEY",
            "JINA_API_KEY",
            "PPLX_MODEL",
            "JINA_MODEL",
            "JINA_BUDGET_TOKENS",
            "PAID_SEARCH_PROVIDER",
            "WEB_PAID_SEARCH_PROVIDER"
    );

    @TestFactory
    Collection<DynamicTest> pythonWebToolsCases() {
        return pythonTestNodes()
                .map(nodeId -> dynamicTest(nodeId, () -> {
                    clearProperties();
                    WebTools.resetHttpTransport();
                    try {
                        runPythonCase(nodeId);
                    } finally {
                        WebTools.resetHttpTransport();
                        clearProperties();
                    }
                }))
                .toList();
    }

    private static Stream<String> pythonTestNodes() {
        return Stream.of(
                SOURCE + "::TestWebFreeSearchTool::test_invoke_empty_query",
                SOURCE + "::TestWebFreeSearchTool::test_invoke_duckduckgo_success",
                SOURCE + "::TestWebFreeSearchTool::test_invoke_bing_fallback_success",
                SOURCE + "::TestWebFreeSearchTool::test_ddg_toggle_disables_duckduckgo_engines",
                SOURCE + "::TestWebFreeSearchTool::test_all_free_search_engines_disabled_returns_error",
                SOURCE + "::TestWebFreeSearchTool::test_create_web_tools_omits_free_search_by_default",
                SOURCE + "::TestWebFreeSearchTool::test_create_web_tools_omits_free_search_when_all_engines_disabled",
                SOURCE + "::TestWebFreeSearchTool::test_create_web_tools_restores_free_search_when_any_engine_enabled",
                SOURCE + "::TestWebFreeSearchTool::test_create_web_tools_prioritizes_paid_search_when_configured",
                SOURCE + "::TestWebFreeSearchTool::test_best_effort_returns_low_quality_bing_rows",
                SOURCE + "::TestWebFreeSearchTool::test_stream_not_supported",
                SOURCE + "::TestWebFreeSearchTool::test_http_request_applies_configured_search_proxy",
                SOURCE + "::TestWebFreeSearchTool::test_http_request_bypasses_configured_search_proxy_for_no_proxy_hosts",
                SOURCE + "::TestWebPaidSearchTool::test_invoke_invalid_provider",
                SOURCE + "::TestWebPaidSearchTool::test_invoke_bocha_success",
                SOURCE + "::TestWebPaidSearchTool::test_invoke_auto_provider_prefers_perplexity",
                SOURCE + "::TestWebPaidSearchTool::test_invoke_auto_provider_fallback",
                SOURCE + "::TestWebPaidSearchTool::test_invoke_paid_search_clamps_timeout",
                SOURCE + "::TestWebPaidSearchTool::test_serper_retries_minimal_payload_after_bad_request",
                SOURCE + "::TestWebPaidSearchTool::test_perplexity_uses_safe_model_without_forced_search_context",
                SOURCE + "::TestWebPaidSearchTool::test_jina_uses_low_effort_without_budget_tokens",
                SOURCE + "::TestWebFetchWebpageTool::test_invoke_basic_html_extracts_main_content",
                SOURCE + "::TestWebFetchWebpageTool::test_invoke_max_chars_zero_disables_clipping",
                SOURCE + "::TestWebFetchWebpageTool::test_decode_response_text_prefers_non_mojibake",
                SOURCE + "::TestWebFetchWebpageTool::test_stream_not_supported"
        );
    }

    private static void runPythonCase(String nodeId) throws Exception {
        switch (nodeId) {
            case SOURCE + "::TestWebFreeSearchTool::test_invoke_empty_query" -> testInvokeEmptyQuery();
            case SOURCE + "::TestWebFreeSearchTool::test_invoke_duckduckgo_success" -> testInvokeDuckduckgoSuccess();
            case SOURCE + "::TestWebFreeSearchTool::test_invoke_bing_fallback_success" ->
                    testInvokeBingFallbackSuccess();
            case SOURCE + "::TestWebFreeSearchTool::test_ddg_toggle_disables_duckduckgo_engines" ->
                    testDdgToggleDisablesDuckduckgoEngines();
            case SOURCE + "::TestWebFreeSearchTool::test_all_free_search_engines_disabled_returns_error" ->
                    testAllFreeSearchEnginesDisabledReturnsError();
            case SOURCE + "::TestWebFreeSearchTool::test_create_web_tools_omits_free_search_by_default" ->
                    testCreateWebToolsOmitsFreeSearchByDefault();
            case SOURCE + "::TestWebFreeSearchTool::test_create_web_tools_omits_free_search_when_all_engines_disabled" ->
                    testCreateWebToolsOmitsFreeSearchWhenAllEnginesDisabled();
            case SOURCE + "::TestWebFreeSearchTool::test_create_web_tools_restores_free_search_when_any_engine_enabled" ->
                    testCreateWebToolsRestoresFreeSearchWhenAnyEngineEnabled();
            case SOURCE + "::TestWebFreeSearchTool::test_create_web_tools_prioritizes_paid_search_when_configured" ->
                    testCreateWebToolsPrioritizesPaidSearchWhenConfigured();
            case SOURCE + "::TestWebFreeSearchTool::test_best_effort_returns_low_quality_bing_rows" ->
                    testBestEffortReturnsLowQualityBingRows();
            case SOURCE + "::TestWebFreeSearchTool::test_stream_not_supported" -> testFreeStreamNotSupported();
            case SOURCE + "::TestWebFreeSearchTool::test_http_request_applies_configured_search_proxy" ->
                    testHttpRequestAppliesConfiguredSearchProxy();
            case SOURCE + "::TestWebFreeSearchTool::test_http_request_bypasses_configured_search_proxy_for_no_proxy_hosts" ->
                    testHttpRequestBypassesConfiguredSearchProxyForNoProxyHosts();
            case SOURCE + "::TestWebPaidSearchTool::test_invoke_invalid_provider" -> testInvokeInvalidProvider();
            case SOURCE + "::TestWebPaidSearchTool::test_invoke_bocha_success" -> testInvokeBochaSuccess();
            case SOURCE + "::TestWebPaidSearchTool::test_invoke_auto_provider_prefers_perplexity" ->
                    testInvokeAutoProviderPrefersPerplexity();
            case SOURCE + "::TestWebPaidSearchTool::test_invoke_auto_provider_fallback" ->
                    testInvokeAutoProviderFallback();
            case SOURCE + "::TestWebPaidSearchTool::test_invoke_paid_search_clamps_timeout" ->
                    testInvokePaidSearchClampsTimeout();
            case SOURCE + "::TestWebPaidSearchTool::test_serper_retries_minimal_payload_after_bad_request" ->
                    testSerperRetriesMinimalPayloadAfterBadRequest();
            case SOURCE + "::TestWebPaidSearchTool::test_perplexity_uses_safe_model_without_forced_search_context" ->
                    testPerplexityUsesSafeModelWithoutForcedSearchContext();
            case SOURCE + "::TestWebPaidSearchTool::test_jina_uses_low_effort_without_budget_tokens" ->
                    testJinaUsesLowEffortWithoutBudgetTokens();
            case SOURCE + "::TestWebFetchWebpageTool::test_invoke_basic_html_extracts_main_content" ->
                    testInvokeBasicHtmlExtractsMainContent();
            case SOURCE + "::TestWebFetchWebpageTool::test_invoke_max_chars_zero_disables_clipping" ->
                    testInvokeMaxCharsZeroDisablesClipping();
            case SOURCE + "::TestWebFetchWebpageTool::test_decode_response_text_prefers_non_mojibake" ->
                    testDecodeResponseTextPrefersNonMojibake();
            case SOURCE + "::TestWebFetchWebpageTool::test_stream_not_supported" -> testFetchStreamNotSupported();
            default -> throw new IllegalArgumentException("Unknown Python node: " + nodeId);
        }
    }

    private static void testInvokeEmptyQuery() throws Exception {
        String result = invokeFree(Map.of("query", ""));

        assertThat(result).contains("[ERROR]: query cannot be empty.");
    }

    private static void testInvokeDuckduckgoSuccess() throws Exception {
        set("FREE_SEARCH_DDG_ENABLED", "true");
        set("FREE_SEARCH_BING_ENABLED", "false");
        RecordingTransport transport = new RecordingTransport(spec -> html("""
                <a class="result__a" href="/l/?uddg=https%3A%2F%2Fexample.com%2Fpage1">Example Title 1</a>
                <a class="result__snippet" href="#">Example snippet text 1</a>
                """));
        WebTools.setHttpTransport(transport);

        String result = invokeFree(Map.of("query", "test query", "max_results", 5));

        assertThat(result).contains("Free search results (DuckDuckGo)", "Example Title 1");
    }

    private static void testInvokeBingFallbackSuccess() throws Exception {
        set("FREE_SEARCH_DDG_ENABLED", "true");
        set("FREE_SEARCH_BING_ENABLED", "true");
        RecordingTransport transport = new RecordingTransport(spec -> {
            if (spec.url().contains("r.jina.ai")) {
                return WebTools.HttpResult.of(500, spec.url(), "");
            }
            if (spec.url().contains("duckduckgo.com")) {
                return WebTools.HttpResult.of(500, spec.url(), "");
            }
            return bingHtml("Bing Result 1", "Bing snippet 1", "https://example.com/page1");
        });
        WebTools.setHttpTransport(transport);

        String result = invokeFree(Map.of("query", "test query", "max_results", 5));

        assertThat(result).contains("Free search results (Bing)", "Bing Result 1");
    }

    private static void testDdgToggleDisablesDuckduckgoEngines() throws Exception {
        set("FREE_SEARCH_DDG_ENABLED", "false");
        set("FREE_SEARCH_BING_ENABLED", "true");
        RecordingTransport transport = new RecordingTransport(spec ->
                bingHtml("Bing Result 1", "Bing snippet 1", "https://example.com/page1"));
        WebTools.setHttpTransport(transport);

        String result = invokeFree(Map.of("query", "test query", "max_results", 5));

        assertThat(transport.requests)
                .extracting(WebTools.HttpRequestSpec::url)
                .allSatisfy(url -> assertThat(url).doesNotContain("duckduckgo.com", "r.jina.ai"));
        assertThat(result).contains("Free search results (Bing)");
    }

    private static void testAllFreeSearchEnginesDisabledReturnsError() throws Exception {
        set("FREE_SEARCH_DDG_ENABLED", "false");
        set("FREE_SEARCH_BING_ENABLED", "false");

        String result = invokeFree(Map.of("query", "test query", "max_results", 5));

        assertThat(result).contains("[ERROR]: free search failed:", "all free search engines are disabled");
    }

    private static void testCreateWebToolsOmitsFreeSearchByDefault() {
        List<Tool> tools = WebTools.createWebTools(null);

        assertThat(WebTools.isFreeSearchEnabled()).isFalse();
        assertThat(toolNames(tools)).containsExactly("fetch_webpage");
    }

    private static void testCreateWebToolsOmitsFreeSearchWhenAllEnginesDisabled() {
        set("FREE_SEARCH_DDG_ENABLED", "false");
        set("FREE_SEARCH_BING_ENABLED", "false");

        List<Tool> tools = WebTools.createWebTools(null);

        assertThat(WebTools.isFreeSearchEnabled()).isFalse();
        assertThat(toolNames(tools)).containsExactly("fetch_webpage");
    }

    private static void testCreateWebToolsRestoresFreeSearchWhenAnyEngineEnabled() {
        set("FREE_SEARCH_DDG_ENABLED", "false");
        set("FREE_SEARCH_BING_ENABLED", "true");

        List<Tool> tools = WebTools.createWebTools(null);

        assertThat(WebTools.isFreeSearchEnabled()).isTrue();
        assertThat(toolNames(tools)).containsExactly("free_search", "fetch_webpage");
    }

    private static void testCreateWebToolsPrioritizesPaidSearchWhenConfigured() {
        set("BOCHA_API_KEY", "test-key");
        set("FREE_SEARCH_DDG_ENABLED", "false");
        set("FREE_SEARCH_BING_ENABLED", "true");

        List<Tool> tools = WebTools.createWebTools(null);

        assertThat(WebTools.isPaidSearchEnabled()).isTrue();
        assertThat(toolNames(tools)).containsExactly("paid_search", "free_search", "fetch_webpage");
    }

    private static void testBestEffortReturnsLowQualityBingRows() throws Exception {
        set("FREE_SEARCH_DDG_ENABLED", "true");
        set("FREE_SEARCH_BING_ENABLED", "true");
        RecordingTransport transport = new RecordingTransport(spec -> {
            if (spec.url().contains("r.jina.ai") || spec.url().contains("duckduckgo.com")) {
                return WebTools.HttpResult.of(500, spec.url(), "");
            }
            return bingHtml("亚洲 - 知乎", "知乎页面", "https://www.zhihu.com/question/1");
        });
        WebTools.setHttpTransport(transport);

        String result = invokeFree(Map.of("query", "亚洲新闻 最新", "max_results", 5));

        assertThat(result).contains("Free search results (Bing)", "亚洲 - 知乎");
    }

    private static void testFreeStreamNotSupported() {
        assertThatThrownBy(() -> new WebTools.WebFreeSearchTool().stream(Map.of("query", "test")))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(((BaseError) error).getStatus())
                        .isEqualTo(StatusCode.TOOL_STREAM_NOT_SUPPORTED));
    }

    @SuppressWarnings("unchecked")
    private static void testHttpRequestAppliesConfiguredSearchProxy() throws Exception {
        set("FREE_SEARCH_PROXY_URL", "http://username:password@proxyhk.huawei.com:8080");
        clear("NO_PROXY");
        clear("no_proxy");
        RecordingTransport transport = new RecordingTransport(spec -> WebTools.HttpResult.of(200, spec.url(), ""));
        WebTools.setHttpTransport(transport);

        WebTools.httpRequest("GET", "https://www.bing.com/search?q=test", new LinkedHashMap<>());

        WebTools.HttpRequestSpec spec = transport.requests.get(0);
        assertThat(spec.proxyApplied()).isTrue();
        assertThat((Map<String, String>) spec.kwargs().get("proxies"))
                .containsEntry("http", "http://username:password@proxyhk.huawei.com:8080")
                .containsEntry("https", "http://username:password@proxyhk.huawei.com:8080");
    }

    private static void testHttpRequestBypassesConfiguredSearchProxyForNoProxyHosts() throws Exception {
        set("FREE_SEARCH_PROXY_URL", "http://username:password@proxyhk.huawei.com:8080");
        clear("NO_PROXY");
        clear("no_proxy");
        RecordingTransport transport = new RecordingTransport(spec -> WebTools.HttpResult.of(200, spec.url(), ""));
        WebTools.setHttpTransport(transport);

        WebTools.httpRequest("GET", "https://service.huawei.com/path", new LinkedHashMap<>());

        WebTools.HttpRequestSpec spec = transport.requests.get(0);
        assertThat(spec.proxyApplied()).isFalse();
        assertThat(spec.kwargs()).doesNotContainKey("proxies");
    }

    private static void testInvokeInvalidProvider() throws Exception {
        String result = invokePaid(Map.of("query", "test", "provider", "invalid"));

        assertThat(result).contains("[ERROR]: provider must be one of");
    }

    private static void testInvokeBochaSuccess() throws Exception {
        set("BOCHA_API_KEY", "test-bocha-key");
        WebTools.setHttpTransport(new RecordingTransport(spec -> json(Map.of(
                "data", Map.of(
                        "summary", "Bocha summary answer.",
                        "webPages", Map.of("value", List.of(Map.of("url", "https://example.com/page1")))
                )
        ))));

        String result = invokePaid(Map.of("query", "test query", "provider", "bocha"));

        assertThat(result).contains("Paid search provider: bocha", "Bocha summary answer.", "https://example.com/page1");
    }

    private static void testInvokeAutoProviderPrefersPerplexity() throws Exception {
        set("PERPLEXITY_API_KEY", "test-key");
        WebTools.setHttpTransport(new RecordingTransport(spec -> json(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", "PPLX auto answer"))),
                "citations", List.of("https://example.com/pplx")
        ))));

        String result = invokePaid(Map.of("query", "test query", "provider", "auto"));

        assertThat(result).contains("Paid search provider: perplexity");
    }

    private static void testInvokeAutoProviderFallback() throws Exception {
        set("PERPLEXITY_API_KEY", "x");
        set("BOCHA_API_KEY", "x");
        set("JINA_API_KEY", "x");
        set("SERPER_API_KEY", "x");
        WebTools.setHttpTransport(new RecordingTransport(spec -> {
            if (spec.url().contains("perplexity.ai") || spec.url().contains("api.bocha.cn")
                    || spec.url().contains("deepsearch.jina.ai")) {
                return WebTools.HttpResult.of(500, spec.url(), "");
            }
            return json(Map.of("organic", List.of(Map.of("link", "https://example.com/fallback"))));
        }));

        String result = invokePaid(Map.of("query", "test query", "provider", "auto"));

        assertThat(result).contains("Paid search provider: serper", "https://example.com/fallback");
    }

    private static void testInvokePaidSearchClampsTimeout() throws Exception {
        set("SERPER_API_KEY", "test-key");
        RecordingTransport transport = new RecordingTransport(spec ->
                json(Map.of("organic", List.of(Map.of("link", "https://example.com/serper")))));
        WebTools.setHttpTransport(transport);

        String result = invokePaid(Map.of("query", "test query", "provider", "serper", "timeout_seconds", 999));

        assertThat(result).contains("Paid search provider: serper");
        assertThat(transport.requests.get(0).timeoutSeconds()).isEqualTo(300);
    }

    private static void testSerperRetriesMinimalPayloadAfterBadRequest() throws Exception {
        set("SERPER_API_KEY", "test-key");
        RecordingTransport transport = new RecordingTransport(List.of(
                WebTools.HttpResult.of(400, "https://google.serper.dev/search", ""),
                json(Map.of("organic", List.of(Map.of("link", "https://example.com/serper"))))
        ));
        WebTools.setHttpTransport(transport);

        String result = invokePaid(Map.of("query", "test query", "provider", "serper"));

        assertThat(result).contains("Paid search provider: serper");
        assertThat(transport.requests.get(0).jsonBody()).containsEntry("q", "test query").containsEntry("num", 8);
        assertThat(transport.requests.get(1).jsonBody()).containsExactlyEntriesOf(Map.of("q", "test query"));
    }

    private static void testPerplexityUsesSafeModelWithoutForcedSearchContext() throws Exception {
        set("PERPLEXITY_API_KEY", "test-key");
        set("PPLX_MODEL", "sonar-deep-research");
        RecordingTransport transport = new RecordingTransport(spec -> json(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", "PPLX answer"))),
                "citations", List.of("https://example.com/pplx")
        )));
        WebTools.setHttpTransport(transport);

        String result = invokePaid(Map.of("query", "test query", "provider", "perplexity"));

        Map<String, Object> payload = transport.requests.get(0).jsonBody();
        assertThat(result).contains("Paid search provider: perplexity");
        assertThat(payload).containsEntry("model", "sonar-pro").containsEntry("stream", false);
        assertThat(payload).doesNotContainKey("search_context_size");
    }

    private static void testJinaUsesLowEffortWithoutBudgetTokens() throws Exception {
        set("JINA_API_KEY", "test-key");
        set("JINA_MODEL", "unsupported-slow-model");
        set("JINA_BUDGET_TOKENS", "50000");
        RecordingTransport transport = new RecordingTransport(spec -> json(Map.of(
                "choices", List.of(Map.of("message", Map.of("content", "Jina answer https://example.com/jina")))
        )));
        WebTools.setHttpTransport(transport);

        String result = invokePaid(Map.of("query", "test query", "provider", "jina"));

        Map<String, Object> payload = transport.requests.get(0).jsonBody();
        assertThat(result).contains("Paid search provider: jina");
        assertThat(payload).containsEntry("model", "jina-deepsearch-v1").containsEntry("reasoning_effort", "low");
        assertThat(payload).doesNotContainKey("budget_tokens");
    }

    private static void testInvokeBasicHtmlExtractsMainContent() throws Exception {
        byte[] content = ("<html><title>Title</title><body><nav>menu</nav>"
                + "<main><p>Main content paragraph.</p></main></body></html>").getBytes(StandardCharsets.UTF_8);
        WebTools.setHttpTransport(new RecordingTransport(spec -> WebTools.HttpResult.of(
                200,
                "https://example.com/article",
                Map.of("Content-Type", "text/html; charset=utf-8"),
                content
        )));

        String result = invokeFetch(Map.of("url", "https://example.com/article"));

        assertThat(result).contains("Title: Title", "Main content paragraph.").doesNotContain("menu");
    }

    private static void testInvokeMaxCharsZeroDisablesClipping() throws Exception {
        String content = "abcdefghij".repeat(100);
        WebTools.setHttpTransport(new RecordingTransport(spec -> WebTools.HttpResult.of(
                200,
                "https://example.com/article",
                Map.of("Content-Type", "text/plain"),
                content.getBytes(StandardCharsets.UTF_8)
        )));

        String result = invokeFetch(Map.of("url", "https://example.com/article", "max_chars", 0));

        assertThat(result).contains(content);
        assertThat(result).doesNotContain("[truncated]");
    }

    private static void testDecodeResponseTextPrefersNonMojibake() {
        WebTools.HttpResult response = WebTools.HttpResult.of(
                200,
                "https://example.com/weather",
                Map.of("Content-Type", "text/html"),
                "【杭州24小时天气查询】".getBytes(StandardCharsets.UTF_8)
        );

        String decoded = WebTools.WebFetchWebpageTool.decodeResponseText(response);

        assertThat(decoded).contains("杭州");
    }

    private static void testFetchStreamNotSupported() {
        assertThatThrownBy(() -> new WebTools.WebFetchWebpageTool().stream(Map.of("url", "https://example.com")))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(((BaseError) error).getStatus())
                        .isEqualTo(StatusCode.TOOL_STREAM_NOT_SUPPORTED));
    }

    private static String invokeFree(Map<String, Object> inputs) throws Exception {
        return String.valueOf(new WebTools.WebFreeSearchTool().invoke(inputs));
    }

    private static String invokePaid(Map<String, Object> inputs) throws Exception {
        return String.valueOf(new WebTools.WebPaidSearchTool().invoke(inputs));
    }

    private static String invokeFetch(Map<String, Object> inputs) throws Exception {
        return String.valueOf(new WebTools.WebFetchWebpageTool().invoke(inputs));
    }

    private static List<String> toolNames(List<Tool> tools) {
        return tools.stream().map(tool -> tool.getCard().getName()).toList();
    }

    private static WebTools.HttpResult html(String body) {
        return WebTools.HttpResult.of(200, "https://example.com", Map.of("Content-Type", "text/html; charset=utf-8"),
                body.getBytes(StandardCharsets.UTF_8));
    }

    private static WebTools.HttpResult bingHtml(String title, String snippet, String url) {
        return html("""
                <html>
                <main aria-label="Search Results">
                  <li class="b_algo">
                    <h2><a href="%s">%s</a></h2>
                    <div class="b_caption"><p>%s</p></div>
                  </li>
                </main>
                </html>
                """.formatted(url, title, snippet));
    }

    private static WebTools.HttpResult json(Map<String, Object> body) {
        return WebTools.HttpResult.json(200, "https://example.com", body);
    }

    private static void set(String key, String value) {
        System.setProperty(key, value);
    }

    private static void clear(String key) {
        System.clearProperty(key);
    }

    private static void clearProperties() {
        SEARCH_ENV_KEYS.forEach(System::clearProperty);
    }

    /**
     * Mirrors Python's {@code unittest.mock.patch} replacement for
     * {@code openjiuwen.harness.tools.web_tools._http_request} in
     * {@code tests/unit_tests/harness/tools/test_web_tools.py}.
     */
    private static final class RecordingTransport implements WebTools.HttpTransport {
        private final Function<WebTools.HttpRequestSpec, WebTools.HttpResult> responder;
        private final Queue<WebTools.HttpResult> queued = new ArrayDeque<>();
        private final List<WebTools.HttpRequestSpec> requests = new ArrayList<>();

        private RecordingTransport(Function<WebTools.HttpRequestSpec, WebTools.HttpResult> responder) {
            this.responder = responder;
        }

        private RecordingTransport(List<WebTools.HttpResult> responses) {
            this.responder = null;
            this.queued.addAll(responses);
        }

        @Override
        public WebTools.HttpResult request(WebTools.HttpRequestSpec spec) {
            requests.add(spec);
            if (responder != null) {
                return responder.apply(spec);
            }
            return queued.remove();
        }
    }
}
