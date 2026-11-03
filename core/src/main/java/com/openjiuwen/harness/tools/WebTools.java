/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Web search and webpage fetch tool facades.
 *
 * <p>Mirrors Python's free-search/paid-search/fetch helpers and tool classes in
 * {@code openjiuwen/harness/tools/web_tools.py}.</p>
 */
public final class WebTools {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient DEFAULT_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final Pattern DDG_LINK_PATTERN = Pattern.compile(
            "<a[^>]+class=\"result__a\"[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern DDG_SNIPPET_PATTERN = Pattern.compile(
            "<(?:a|div)[^>]+class=\"result__snippet\"[^>]*>(.*?)</(?:a|div)>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern CHARSET_HEADER_PATTERN = Pattern.compile("charset=([^\\s;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARSET_META_PATTERN = Pattern.compile(
            "<meta[^>]+charset=[\"']?\\s*([A-Za-z0-9._-]+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s)\\]>\"']+");
    private static final List<String> PAID_SEARCH_PROVIDER_ORDER = List.of("perplexity", "bocha", "jina", "serper");
    private static final Map<String, String> PAID_SEARCH_PROVIDER_KEY_ENVS = Map.of(
            "perplexity", "PERPLEXITY_API_KEY",
            "bocha", "BOCHA_API_KEY",
            "jina", "JINA_API_KEY",
            "serper", "SERPER_API_KEY"
    );
    private static final List<String> PAID_SEARCH_API_KEY_ENVS = List.of(
            "PERPLEXITY_API_KEY", "BOCHA_API_KEY", "JINA_API_KEY", "SERPER_API_KEY"
    );
    private static final Set<String> TIMELY_QUERY_HINTS = Set.of(
            "news", "latest", "today", "breaking", "update", "updates",
            "新闻", "最新", "今日", "今天", "动态", "快讯", "头条", "热点", "天气", "weather", "forecast"
    );
    private static final Set<String> QUERY_STOPWORDS = Set.of(
            "the", "and", "for", "with", "news", "latest", "today", "热点", "新闻", "最新", "今日", "今天"
    );
    private static final Set<String> LOW_CONFIDENCE_RESULT_DOMAINS = Set.of(
            "zhihu.com", "baike.baidu.com", "tieba.baidu.com", "zhidao.baidu.com",
            "douban.com", "bilibili.com", "weibo.com"
    );
    private static final Set<String> LOW_FETCH_VALUE_DOMAINS = Set.of("mp.weixin.qq.com", "so.html5.qq.com");
    private static final String DEFAULT_NO_PROXY =
            "127.0.0.1,.huawei.com,localhost,local,.local,10.155.97.247,.myhuaweicloud.com,"
                    + " api.openai.rnd.huawei.com";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final Set<String> PPLX_ALLOWED_MODELS = Set.of("sonar", "sonar-pro");
    private static final Set<String> JINA_ALLOWED_MODELS = Set.of("jina-deepsearch-v1");
    private static volatile HttpTransport httpTransport = WebTools::defaultHttpRequest;

    private WebTools() {
    }

    public static boolean isFreeSearchEnabled() {
        return envFlag("FREE_SEARCH_DDG_ENABLED", false) || envFlag("FREE_SEARCH_BING_ENABLED", false);
    }

    public static boolean isPaidSearchEnabled() {
        return PAID_SEARCH_API_KEY_ENVS.stream().anyMatch(name -> !env(name).isBlank());
    }

    public static boolean isTimelyQuery(String query) {
        String lowered = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return TIMELY_QUERY_HINTS.stream().anyMatch(lowered::contains);
    }

    public static List<Tool> createWebTools(SearchProvider searchProvider) {
        return createWebTools(searchProvider, true, true, true);
    }

    public static List<Tool> createWebTools(SearchProvider searchProvider,
                                            boolean includeFreeSearch,
                                            boolean includePaidSearch,
                                            boolean includeFetchWebpage) {
        List<Tool> tools = new ArrayList<>();
        if (includePaidSearch && isPaidSearchEnabled()) {
            tools.add(new WebPaidSearchTool(searchProvider));
        }
        if (includeFreeSearch && isFreeSearchEnabled()) {
            tools.add(new WebFreeSearchTool(searchProvider));
        }
        if (includeFetchWebpage) {
            tools.add(new WebFetchWebpageTool());
        }
        return tools;
    }

    public static List<Tool> createWebTools() {
        return createWebTools(null);
    }

    public static void setHttpTransport(HttpTransport transport) {
        httpTransport = transport == null ? WebTools::defaultHttpRequest : transport;
    }

    public static void resetHttpTransport() {
        httpTransport = WebTools::defaultHttpRequest;
    }

    public static HttpResult httpRequest(String method, String url, Map<String, Object> kwargs) throws Exception {
        Map<String, Object> requestKwargs = kwargs == null ? Map.of() : new LinkedHashMap<>(kwargs);
        boolean proxyApplied = applyFreeSearchProxy(url, requestKwargs);
        int timeoutSeconds = intValue(requestKwargs.get("timeout"), 20);
        Map<String, String> headers = stringMap(requestKwargs.get("headers"));
        Map<String, Object> jsonBody = stringObjectMap(requestKwargs.get("json"));
        return httpTransport.request(new HttpRequestSpec(
                method == null ? "GET" : method.toUpperCase(Locale.ROOT),
                url,
                headers,
                jsonBody,
                timeoutSeconds,
                proxyApplied,
                proxyApplied ? env("FREE_SEARCH_PROXY_URL") : "",
                requestKwargs
        ));
    }

    /**
     * Mirrors Python's injectable test seam around search helpers in
     * {@code openjiuwen/harness/tools/web_tools.py}.
     */
    public interface SearchProvider {
        Map<String, Object> search(String query, boolean paid, Map<String, Object> inputs) throws Exception;
    }

    /**
     * Mirrors Python's patchable {@code _http_request} collaborator in
     * {@code openjiuwen/harness/tools/web_tools.py}.
     */
    @FunctionalInterface
    public interface HttpTransport {
        HttpResult request(HttpRequestSpec spec) throws Exception;
    }

    /**
     * Mirrors Python's {@code _http_request} method/url/kwargs call shape in
     * {@code openjiuwen/harness/tools/web_tools.py}.
     */
    public record HttpRequestSpec(String method,
                                  String url,
                                  Map<String, String> headers,
                                  Map<String, Object> jsonBody,
                                  int timeoutSeconds,
                                  boolean proxyApplied,
                                  String proxyUrl,
                                  Map<String, Object> kwargs) {
    }

    /**
     * Mirrors Python's minimal {@code requests.Response} fields consumed by
     * {@code openjiuwen/harness/tools/web_tools.py}.
     */
    public static final class HttpResult {
        private final int statusCode;
        private final String url;
        private final Map<String, String> headers;
        private final byte[] content;
        private final String text;
        private final Map<String, Object> json;

        private HttpResult(int statusCode,
                           String url,
                           Map<String, String> headers,
                           byte[] content,
                           String text,
                           Map<String, Object> json) {
            this.statusCode = statusCode;
            this.url = url;
            this.headers = headers == null ? Map.of() : new LinkedHashMap<>(headers);
            this.content = content == null ? new byte[0] : content.clone();
            this.text = text;
            this.json = json == null ? null : new LinkedHashMap<>(json);
        }

        public static HttpResult of(int statusCode, String url, String text) {
            return new HttpResult(statusCode, url, Map.of(), bytes(text), text, null);
        }

        public static HttpResult of(int statusCode, String url, Map<String, String> headers, byte[] content) {
            return new HttpResult(statusCode, url, headers, content, null, null);
        }

        public static HttpResult json(int statusCode, String url, Map<String, Object> json) {
            return new HttpResult(statusCode, url, Map.of(), bytes(json == null ? "{}" : toJson(json)), null, json);
        }

        public int statusCode() {
            return statusCode;
        }

        public String url() {
            return url;
        }

        public Map<String, String> headers() {
            return new LinkedHashMap<>(headers);
        }

        public byte[] content() {
            return content.clone();
        }

        public String text() {
            if (text != null) {
                return text;
            }
            return new String(content, StandardCharsets.UTF_8);
        }

        public Map<String, Object> json() {
            if (json != null) {
                return new LinkedHashMap<>(json);
            }
            try {
                return OBJECT_MAPPER.readValue(text(), new TypeReference<>() {
                });
            } catch (IOException exception) {
                return Map.of();
            }
        }

        public void raiseForStatus() {
            if (statusCode >= 400) {
                throw new IllegalStateException("HTTP " + statusCode);
            }
        }
    }

    /**
     * Mirrors Python's {@code WebFreeSearchTool} in {@code openjiuwen/harness/tools/web_tools.py}.
     */
    public static class WebFreeSearchTool extends AbstractHarnessTool {
        private final SearchProvider searchProvider;

        public WebFreeSearchTool() {
            this(null);
        }

        public WebFreeSearchTool(SearchProvider searchProvider) {
            super(webToolCard("WebFreeSearchTool", "free_search", "Search the web through a free provider."));
            this.searchProvider = searchProvider;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            String query = stringValue(inputs == null ? null : inputs.get("query")).trim();
            int maxResults = clamp(intValue(inputs == null ? null : inputs.get("max_results"), 8), 1, 20);
            int timeoutSeconds = clamp(intValue(inputs == null ? null : inputs.get("timeout_seconds"), 20), 5, 60);
            if (query.isEmpty()) {
                return "[ERROR]: query cannot be empty.";
            }
            try {
                if (searchProvider != null) {
                    return formatFreeRows("custom", fromProvider(searchProvider.search(query, false, inputs), maxResults), query);
                }
                SearchOutcome outcome = searchFree(query, maxResults, timeoutSeconds);
                return formatFreeRows(outcome.engine(), outcome.rows(), query);
            } catch (Exception exception) {
                return "[ERROR]: free search failed: " + exception.getMessage();
            }
        }

        @Override
        protected Iterator<Object> streamInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            throw ErrorHelper.buildError(StatusCode.TOOL_STREAM_NOT_SUPPORTED, "card", String.valueOf(getCard()));
        }

        private static SearchOutcome searchFree(String query, int maxResults, int timeoutSeconds) throws Exception {
            List<String> errors = new ArrayList<>();
            String bestEngine = "";
            List<SearchRow> bestRows = List.of();
            List<EngineRunner> runners = new ArrayList<>();
            if (envFlag("FREE_SEARCH_DDG_ENABLED", false)) {
                runners.add(new EngineRunner("duckduckgo", WebFreeSearchTool::searchDuckDuckGo));
                runners.add(new EngineRunner("duckduckgo-jina", WebFreeSearchTool::searchDuckDuckGoViaJina));
            }
            if (envFlag("FREE_SEARCH_BING_ENABLED", false)) {
                runners.add(new EngineRunner("bing", WebFreeSearchTool::searchBing));
            }
            if (runners.isEmpty()) {
                throw new IllegalStateException("all free search engines are disabled");
            }

            for (EngineRunner runner : runners) {
                List<SearchRow> rows;
                try {
                    rows = filterRankedRows(query, runner.run(query, maxResults, timeoutSeconds)).stream()
                            .limit(maxResults)
                            .toList();
                } catch (Exception exception) {
                    errors.add(runner.name() + ": " + exception.getMessage());
                    continue;
                }
                if (!rows.isEmpty() && bestRows.isEmpty()) {
                    bestEngine = runner.name();
                    bestRows = rows;
                }
                if (rowsAreUsable(query, rows)) {
                    return new SearchOutcome(runner.name(), rows);
                }
                if (!rows.isEmpty() && bestRows.isEmpty()) {
                    bestEngine = runner.name();
                    bestRows = rows;
                }
                errors.add(runner.name() + ": low-quality or empty result");
            }
            if (!bestRows.isEmpty()) {
                return new SearchOutcome(bestEngine, bestRows);
            }
            throw new IllegalStateException(String.join(" | ", errors));
        }

        private static List<SearchRow> searchDuckDuckGo(String query, int maxResults, int timeoutSeconds) throws Exception {
            String url = duckDuckGoSearchUrl(query);
            HttpResult response = httpRequest("GET", url, Map.of("headers", searchRequestHeaders(query), "timeout", timeoutSeconds));
            if (Set.of(202, 418, 429, 503).contains(response.statusCode())
                    || response.text().toLowerCase(Locale.ROOT).contains("challenge-form")
                    || response.text().toLowerCase(Locale.ROOT).contains("/anomaly.js")) {
                throw new IllegalStateException("anti-bot challenge page returned");
            }
            if (response.statusCode() != 200) {
                throw new IllegalStateException("non-200 status: " + response.statusCode());
            }
            response.raiseForStatus();
            String html = response.text();
            Matcher linkMatcher = DDG_LINK_PATTERN.matcher(html);
            List<String[]> links = new ArrayList<>();
            while (linkMatcher.find()) {
                links.add(new String[]{linkMatcher.group(1), linkMatcher.group(2)});
            }
            Matcher snippetMatcher = DDG_SNIPPET_PATTERN.matcher(html);
            List<String> snippets = new ArrayList<>();
            while (snippetMatcher.find()) {
                snippets.add(snippetMatcher.group(1));
            }
            List<SearchRow> rows = new ArrayList<>();
            for (int index = 0; index < Math.min(maxResults, links.size()); index++) {
                String[] link = links.get(index);
                String snippet = index < snippets.size() ? stripTags(snippets.get(index)) : "";
                rows.add(new SearchRow(
                        firstNonBlank(stripTags(link[1]), "Result " + (index + 1)),
                        decodeDuckDuckGoRedirect(htmlUnescape(link[0])),
                        snippet,
                        "",
                        "",
                        "duckduckgo"
                ));
            }
            return rows;
        }

        private static List<SearchRow> searchDuckDuckGoViaJina(String query, int maxResults, int timeoutSeconds) throws Exception {
            String url = "https://r.jina.ai/http://duckduckgo.com/html/?q=" + urlEncode(query);
            HttpResult response = httpRequest("GET", url, Map.of("headers", searchRequestHeaders(query), "timeout", timeoutSeconds));
            response.raiseForStatus();
            Pattern markdownLink = Pattern.compile("\\[([^\\]\\n]+)]\\((https?://[^\\s)]+)\\)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = markdownLink.matcher(response.text());
            List<SearchRow> rows = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            while (matcher.find() && rows.size() < maxResults) {
                String title = stripTags(matcher.group(1));
                String decoded = decodeDuckDuckGoRedirect(matcher.group(2));
                String host = host(decoded);
                if (title.isBlank() || title.startsWith("Image ") || host.contains("duckduckgo.com") || !seen.add(decoded)) {
                    continue;
                }
                rows.add(new SearchRow(title, decoded, "", "", "", "duckduckgo-jina"));
            }
            return rows;
        }

        private static List<SearchRow> searchBing(String query, int maxResults, int timeoutSeconds) throws Exception {
            String locale = containsCjk(query) ? "&setlang=zh-Hans&mkt=zh-CN&cc=CN" : "&setlang=en-US&mkt=en-US&cc=US";
            String url = "https://www.bing.com/search?q=" + urlEncode(query) + locale;
            HttpResult response = httpRequest("GET", url, Map.of("headers", searchRequestHeaders(query), "timeout", timeoutSeconds));
            response.raiseForStatus();
            Document document = Jsoup.parse(response.text());
            Element area = document.selectFirst("main[aria-label=Search Results]");
            Element root = area == null ? document : area;
            List<SearchRow> rows = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (Element item : root.select("li.b_algo")) {
                Element anchor = item.selectFirst("h2 a[href]");
                if (anchor == null) {
                    continue;
                }
                String href = decodeBingRedirect(htmlUnescape(anchor.attr("href")));
                if (href.isBlank() || !seen.add(href)) {
                    continue;
                }
                Element caption = firstElement(item.selectFirst(".b_caption p"), item.selectFirst("p"));
                Element origin = firstElement(item.selectFirst(".tptt"), item.selectFirst(".source"), item.selectFirst("cite"));
                Element date = item.selectFirst(".news_dt");
                rows.add(new SearchRow(
                        firstNonBlank(anchor.text(), "Result " + (rows.size() + 1)),
                        href,
                        caption == null ? "" : caption.text(),
                        origin == null ? "" : origin.text(),
                        date == null ? "" : date.text(),
                        "bing-web"
                ));
                if (rows.size() >= maxResults) {
                    break;
                }
            }
            return rows;
        }

        private static String formatFreeRows(String engine, List<SearchRow> rows, String query) {
            if (rows.isEmpty()) {
                return "No search results for: " + query;
            }
            List<String> lines = new ArrayList<>();
            lines.add("Free search results (" + engineDisplayName(engine) + ") for: " + query);
            for (int index = 0; index < rows.size(); index++) {
                SearchRow row = rows.get(index);
                lines.add((index + 1) + ". " + row.title());
                lines.add("   URL: " + row.url());
                if (!row.snippet().isBlank()) {
                    lines.add("   Snippet: " + row.snippet());
                }
            }
            lines.add("");
            lines.add("Required next step: before reformulating the query, fetch at least 2 relevant URLs from the top "
                    + "results. If the first fetch fails, is a dynamic shell page, or is still incomplete, continue with "
                    + "the next recommended URLs instead of searching again.");
            List<String> urls = rows.stream().map(SearchRow::url).filter(url -> !url.isBlank()).limit(3).toList();
            if (!urls.isEmpty()) {
                lines.add("Recommended fetch targets:");
                for (int index = 0; index < urls.size(); index++) {
                    lines.add((index + 1) + ". " + urls.get(index));
                }
            }
            return String.join("\n", lines);
        }

        private static List<SearchRow> fromProvider(Map<String, Object> result, int maxResults) {
            List<SearchRow> rows = new ArrayList<>();
            Object rowsValue = result == null ? null : result.get("rows");
            if (rowsValue instanceof List<?> list) {
                for (Object item : list) {
                    Map<String, Object> row = stringObjectMap(item);
                    rows.add(new SearchRow(
                            stringValue(row.get("title")),
                            stringValue(row.get("url")),
                            stringValue(row.get("snippet")),
                            stringValue(row.get("origin")),
                            stringValue(row.get("date")),
                            stringValue(row.getOrDefault("source", "custom"))
                    ));
                    if (rows.size() >= maxResults) {
                        break;
                    }
                }
            }
            return rows;
        }
    }

    /**
     * Mirrors Python's {@code WebPaidSearchTool} in {@code openjiuwen/harness/tools/web_tools.py}.
     */
    public static class WebPaidSearchTool extends AbstractHarnessTool {
        private final SearchProvider searchProvider;

        public WebPaidSearchTool() {
            this(null);
        }

        public WebPaidSearchTool(SearchProvider searchProvider) {
            super(webToolCard("WebPaidSearchTool", "paid_search", "Search the web through a configured paid provider."));
            this.searchProvider = searchProvider;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            String query = stringValue(inputs == null ? null : inputs.get("query")).trim();
            String provider = stringValue(inputs == null ? null : inputs.getOrDefault("provider", "auto")).trim()
                    .toLowerCase(Locale.ROOT);
            if (provider.isBlank()) {
                provider = "auto";
            }
            String envProvider = firstNonBlank(env("PAID_SEARCH_PROVIDER"), env("WEB_PAID_SEARCH_PROVIDER"));
            if ("auto".equals(provider) && !envProvider.isBlank()) {
                provider = envProvider.toLowerCase(Locale.ROOT);
            }
            int maxResults = clamp(intValue(inputs == null ? null : inputs.get("max_results"), 8), 1, 20);
            int timeoutSeconds = clamp(intValue(inputs == null ? null : inputs.get("timeout_seconds"), 180), 30, 300);
            if (query.isBlank()) {
                return "[ERROR]: query cannot be empty.";
            }
            if (!Set.of("auto", "bocha", "jina", "serper", "perplexity").contains(provider)) {
                return "[ERROR]: provider must be one of auto|bocha|jina|serper|perplexity.";
            }

            if (searchProvider != null) {
                try {
                    return formatPaidResult("custom", searchProvider.search(query, true, inputs), maxResults);
                } catch (Exception exception) {
                    return "[ERROR]: paid search failed. custom: " + exception.getMessage();
                }
            }

            List<String> order = "auto".equals(provider) ? configuredPaidSearchProviders() : List.of(provider);
            if (order.isEmpty()) {
                return "[ERROR]: no paid search provider API key configured. "
                        + "Set one of BOCHA_API_KEY, PERPLEXITY_API_KEY, SERPER_API_KEY, JINA_API_KEY.";
            }

            List<String> errors = new ArrayList<>();
            for (String name : order) {
                try {
                    Map<String, Object> result = switch (name) {
                        case "bocha" -> bochaSearch(query, maxResults, timeoutSeconds);
                        case "jina" -> jinaSearch(query, timeoutSeconds);
                        case "serper" -> serperSearch(query, maxResults, timeoutSeconds);
                        case "perplexity" -> perplexitySearch(query, maxResults, timeoutSeconds);
                        default -> throw new IllegalStateException("runner not found");
                    };
                    String formatted = formatPaidResult(name, result, maxResults);
                    if (!formatted.isBlank()) {
                        return formatted;
                    }
                    errors.add(name + ": no usable result payload");
                } catch (Exception exception) {
                    errors.add(name + ": " + exception.getMessage());
                }
            }
            return "[ERROR]: paid search failed. " + String.join(" | ", errors);
        }

        @Override
        protected Iterator<Object> streamInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            throw ErrorHelper.buildError(StatusCode.TOOL_STREAM_NOT_SUPPORTED, "card", String.valueOf(getCard()));
        }

        private static Map<String, Object> bochaSearch(String query, int maxResults, int timeoutSeconds) throws Exception {
            String key = env("BOCHA_API_KEY");
            if (key.isBlank()) {
                throw new IllegalStateException("BOCHA_API_KEY");
            }
            HttpResult response = httpRequest("POST", firstNonBlank(env("BOCHA_API_URL"), "https://api.bocha.cn/v1/web-search"),
                    Map.of(
                            "headers", Map.of("Authorization", "Bearer " + key, "Content-Type", "application/json"),
                            "json", WebTools.linkedMap("query", query, "summary", true, "count", maxResults),
                            "timeout", timeoutSeconds
                    ));
            response.raiseForStatus();
            Map<String, Object> data = response.json();
            return WebTools.linkedMap(
                    "provider", "bocha",
                    "answer", extractBochaAnswer(data),
                    "urls", extractBochaUrls(data, maxResults)
            );
        }

        private static Map<String, Object> serperSearch(String query, int maxResults, int timeoutSeconds) throws Exception {
            String key = env("SERPER_API_KEY");
            if (key.isBlank()) {
                throw new IllegalStateException("SERPER_API_KEY");
            }
            Map<String, String> headers = Map.of("X-API-KEY", key, "Content-Type", "application/json");
            HttpResult response = httpRequest("POST", "https://google.serper.dev/search",
                    Map.of("headers", headers, "json", WebTools.linkedMap("q", query, "num", maxResults), "timeout", timeoutSeconds));
            if (response.statusCode() == 400) {
                response = httpRequest("POST", "https://google.serper.dev/search",
                        Map.of("headers", headers, "json", Map.of("q", query), "timeout", timeoutSeconds));
            }
            response.raiseForStatus();
            List<String> urls = new ArrayList<>();
            Object organic = response.json().get("organic");
            if (organic instanceof List<?> list) {
                for (Object item : list) {
                    Map<String, Object> row = stringObjectMap(item);
                    String link = stringValue(row.get("link"));
                    if (!link.isBlank()) {
                        urls.add(link);
                    }
                    if (urls.size() >= maxResults) {
                        break;
                    }
                }
            }
            return WebTools.linkedMap("provider", "serper", "answer", "", "urls", urls);
        }

        private static Map<String, Object> perplexitySearch(String query, int maxResults, int timeoutSeconds) throws Exception {
            String key = env("PERPLEXITY_API_KEY");
            if (key.isBlank()) {
                throw new IllegalStateException("PERPLEXITY_API_KEY");
            }
            Map<String, Object> payload = WebTools.linkedMap(
                    "model", safeEnvChoice("PPLX_MODEL", "sonar-pro", PPLX_ALLOWED_MODELS),
                    "messages", List.of(
                            Map.of("role", "system", "content", "Provide concise answer and include citations."),
                            Map.of("role", "user", "content", query)
                    ),
                    "max_tokens", 1024,
                    "temperature", 0.2,
                    "stream", false
            );
            HttpResult response = httpRequest("POST", firstNonBlank(env("PPLX_API_URL"), "https://api.perplexity.ai/chat/completions"),
                    Map.of(
                            "headers", Map.of("Authorization", "Bearer " + key, "Content-Type", "application/json"),
                            "json", payload,
                            "timeout", timeoutSeconds
                    ));
            response.raiseForStatus();
            Map<String, Object> data = response.json();
            return WebTools.linkedMap(
                    "provider", "perplexity",
                    "answer", extractChoiceAnswer(data),
                    "urls", parseCitationUrls(data).stream().limit(maxResults).toList()
            );
        }

        private static Map<String, Object> jinaSearch(String query, int timeoutSeconds) throws Exception {
            String key = env("JINA_API_KEY");
            if (key.isBlank()) {
                throw new IllegalStateException("JINA_API_KEY");
            }
            Map<String, Object> payload = WebTools.linkedMap(
                    "model", safeEnvChoice("JINA_MODEL", "jina-deepsearch-v1", JINA_ALLOWED_MODELS),
                    "messages", List.of(Map.of("role", "user", "content", query)),
                    "stream", false,
                    "reasoning_effort", "low"
            );
            HttpResult response = httpRequest("POST", "https://deepsearch.jina.ai/v1/chat/completions",
                    Map.of(
                            "headers", Map.of("Authorization", "Bearer " + key, "Content-Type", "application/json"),
                            "json", payload,
                            "timeout", timeoutSeconds
                    ));
            response.raiseForStatus();
            String answer = extractChoiceAnswer(response.json());
            List<String> urls = new ArrayList<>();
            Matcher matcher = URL_PATTERN.matcher(answer);
            while (matcher.find()) {
                urls.add(matcher.group());
            }
            return WebTools.linkedMap("provider", "jina", "answer", answer, "urls", urls);
        }

        private static String formatPaidResult(String name, Map<String, Object> result, int maxResults) {
            String answer = stringValue(result == null ? null : result.get("answer")).trim();
            List<String> urls = stringList(result == null ? null : result.get("urls")).stream()
                    .filter(url -> !url.isBlank())
                    .limit(maxResults)
                    .toList();
            if (answer.isBlank() && urls.isEmpty()) {
                return "";
            }
            List<String> lines = new ArrayList<>();
            lines.add("Paid search provider: " + name);
            if (!answer.isBlank()) {
                lines.add("Answer:");
                lines.add(answer);
            }
            if (!urls.isEmpty()) {
                lines.add("URLs:");
                for (int index = 0; index < urls.size(); index++) {
                    lines.add((index + 1) + ". " + urls.get(index));
                }
            }
            return String.join("\n", lines);
        }
    }

    /**
     * Mirrors Python's {@code WebFetchWebpageTool} in {@code openjiuwen/harness/tools/web_tools.py}.
     */
    public static class WebFetchWebpageTool extends AbstractHarnessTool {

        public WebFetchWebpageTool() {
            super(webToolCard("WebFetchWebpageTool", "fetch_webpage", "Fetch a webpage as text."));
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            String url = normalizeUrl(stringValue(inputs == null ? null : inputs.get("url")));
            int maxChars = intValue(inputs == null ? null : inputs.get("max_chars"), 20_000);
            int timeoutSeconds = intValue(inputs == null ? null : inputs.get("timeout_seconds"), 45);
            if (url.isBlank()) {
                return "[ERROR]: url cannot be empty.";
            }
            int maxCharsCap = intValue(env("MCP_FETCH_WEBPAGE_MAX_CHARS"), 200_000);
            int timeoutCap = intValue(env("MCP_FETCH_WEBPAGE_MAX_TIMEOUT_SECONDS"), 600);
            if (maxChars != 0) {
                maxChars = Math.max(500, Math.min(maxChars, maxCharsCap));
            }
            timeoutSeconds = Math.max(5, Math.min(timeoutSeconds, timeoutCap));
            try {
                FetchResult data = fetchWebpage(url, timeoutSeconds);
                List<String> lines = new ArrayList<>();
                lines.add("URL: " + data.url());
                lines.add("Status: " + data.statusCode());
                if (!data.title().isBlank()) {
                    lines.add("Title: " + data.title());
                }
                lines.add("Content:");
                lines.add(firstNonBlank(clipText(data.content(), maxChars), "[empty]"));
                return String.join("\n", lines);
            } catch (Exception exception) {
                return "[ERROR]: failed to fetch webpage: " + exception.getMessage();
            }
        }

        @Override
        protected Iterator<Object> streamInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            throw ErrorHelper.buildError(StatusCode.TOOL_STREAM_NOT_SUPPORTED, "card", String.valueOf(getCard()));
        }

        public static String decodeResponseText(HttpResult response) {
            byte[] raw = response.content();
            if (raw.length == 0) {
                return "";
            }
            List<String> candidates = new ArrayList<>();
            String declared = extractDeclaredCharset(response).toLowerCase(Locale.ROOT);
            if (!declared.isBlank() && !Set.of("iso-8859-1", "latin-1", "latin1").contains(declared)) {
                candidates.add(declared);
            }
            candidates.addAll(List.of(
                    "utf-8", "utf-8", "gbk", "gb18030", "big5", "shift_jis", "windows-1252", "iso-8859-1"
            ));
            Set<String> seen = new LinkedHashSet<>();
            String best = "";
            double bestScore = Double.NEGATIVE_INFINITY;
            for (String candidate : candidates) {
                String enc = candidate == null ? "" : candidate.trim().toLowerCase(Locale.ROOT);
                if (enc.isBlank() || !seen.add(enc)) {
                    continue;
                }
                try {
                    String decoded = new String(raw, Charset.forName(enc));
                    double score = scoreDecodedText(decoded);
                    if (score > bestScore) {
                        bestScore = score;
                        best = decoded;
                    }
                } catch (Exception ignored) {
                    // Try the next Python-compatible candidate encoding.
                }
            }
            return best.isEmpty() ? new String(raw, StandardCharsets.UTF_8) : best;
        }

        private static FetchResult fetchWebpage(String url, int timeoutSeconds) throws Exception {
            HttpResult response = httpRequest("GET", url, Map.of("headers", requestHeaders(), "timeout", timeoutSeconds));
            if (Set.of(401, 403, 429).contains(response.statusCode())) {
                HttpResult reader = httpRequest("GET", "https://r.jina.ai/" + url,
                        Map.of("headers", requestHeaders(), "timeout", timeoutSeconds));
                reader.raiseForStatus();
                return new FetchResult(url, reader.statusCode(), "", decodeResponseText(reader).trim());
            }
            response.raiseForStatus();
            String text = decodeResponseText(response);
            String contentType = header(response, "Content-Type");
            String title = "";
            if (contentType.toLowerCase(Locale.ROOT).contains("html")) {
                HtmlText html = extractMainTextFromHtml(text);
                title = html.title();
                text = html.content();
            } else {
                text = text.replaceAll("\\s+", " ").trim();
            }
            return new FetchResult(response.url(), response.statusCode(), title, text);
        }

        private static HtmlText extractMainTextFromHtml(String html) {
            Document document = Jsoup.parse(html);
            Element titleTag = document.selectFirst("title");
            String title = titleTag == null ? "" : titleTag.text();
            document.select("script,style,noscript,svg,canvas,iframe").remove();
            document.select("nav,header,footer,aside,form,button,[role=navigation],.nav,.navbar,.header,.footer,"
                    + ".sidebar,.aside,.recommend,.related,.share,.breadcrumb,.menu,.toolbar").remove();
            List<String> selectors = List.of(
                    "main", "[role=main]", "article", ".article", ".article-content", ".article-body",
                    ".post", ".post-content", ".entry-content", ".content", ".detail", ".news", "#content", "#main"
            );
            String best = "";
            for (String selector : selectors) {
                Element node = document.selectFirst(selector);
                if (node == null) {
                    continue;
                }
                String text = textWithNewLines(node);
                if (text.length() > best.length()) {
                    best = text;
                }
            }
            if (best.isBlank()) {
                Element body = document.body() == null ? document : document.body();
                List<String> blocks = new ArrayList<>();
                for (Element node : body.select("p,li,h1,h2,h3")) {
                    String piece = node.text().trim();
                    if (piece.length() >= 20) {
                        blocks.add(piece);
                    }
                }
                best = blocks.isEmpty() ? textWithNewLines(body) : String.join("\n", blocks);
            }
            return new HtmlText(title, best.replaceAll("\\n{3,}", "\n\n").trim());
        }

        private static String normalizeUrl(String value) {
            String raw = value == null ? "" : value.trim();
            if (raw.isBlank()) {
                return raw;
            }
            String decoded = decodeDuckDuckGoRedirect(raw);
            if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
                return decoded;
            }
            return "https://" + decoded;
        }

        private static String clipText(String value, int maxChars) {
            if (maxChars <= 0 || value.length() <= maxChars) {
                return value;
            }
            return value.substring(0, maxChars) + "\n...[truncated]";
        }
    }

    /**
     * Mirrors Python's free-search engine runner tuple in
     * {@code openjiuwen/harness/tools/web_tools.py}.
     */
    private record EngineRunner(String name, EngineSearch search) {
        private List<SearchRow> run(String query, int maxResults, int timeoutSeconds) throws Exception {
            return search.run(query, maxResults, timeoutSeconds);
        }
    }

    /**
     * Mirrors Python's free-search callable runners in
     * {@code openjiuwen/harness/tools/web_tools.py}.
     */
    @FunctionalInterface
    private interface EngineSearch {
        List<SearchRow> run(String query, int maxResults, int timeoutSeconds) throws Exception;
    }

    /**
     * Mirrors Python's normalized search result row dict in
     * {@code openjiuwen/harness/tools/web_tools.py}.
     */
    private record SearchRow(String title, String url, String snippet, String origin, String date, String source) {
    }

    /**
     * Mirrors Python's {@code _search_free_sync} engine-and-rows result in
     * {@code openjiuwen/harness/tools/web_tools.py}.
     */
    private record SearchOutcome(String engine, List<SearchRow> rows) {
    }

    /**
     * Mirrors Python's fetched webpage result dict in
     * {@code openjiuwen/harness/tools/web_tools.py}.
     */
    private record FetchResult(String url, int statusCode, String title, String content) {
    }

    /**
     * Mirrors Python's {@code _extract_main_text_from_html} tuple in
     * {@code openjiuwen/harness/tools/web_tools.py}.
     */
    private record HtmlText(String title, String content) {
    }

    private static ToolCard webToolCard(String id, String name, String description) {
        return ToolCard.builder()
                .id(id)
                .name(name)
                .description(description == null ? "" : description)
                .inputParams(AbstractHarnessTool.emptySchema())
                .build();
    }

    private static HttpResult defaultHttpRequest(HttpRequestSpec spec) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(spec.url()))
                .timeout(Duration.ofSeconds(spec.timeoutSeconds()))
                .method(spec.method(), requestBody(spec.jsonBody()));
        Map<String, String> headers = spec.headers().isEmpty() ? requestHeaders() : spec.headers();
        headers.forEach(builder::header);
        HttpResponse<byte[]> response = DEFAULT_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        Map<String, String> responseHeaders = new LinkedHashMap<>();
        response.headers().map().forEach((key, values) -> responseHeaders.put(key, String.join(",", values)));
        return HttpResult.of(response.statusCode(), response.uri().toString(), responseHeaders, response.body());
    }

    private static HttpRequest.BodyPublisher requestBody(Map<String, Object> jsonBody) throws IOException {
        if (jsonBody == null || jsonBody.isEmpty()) {
            return HttpRequest.BodyPublishers.noBody();
        }
        return HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(jsonBody), StandardCharsets.UTF_8);
    }

    private static boolean applyFreeSearchProxy(String url, Map<String, Object> kwargs) {
        String proxyUrl = env("FREE_SEARCH_PROXY_URL");
        if (proxyUrl.isBlank() || shouldBypassFreeSearchProxy(url)) {
            return false;
        }
        kwargs.putIfAbsent("proxies", Map.of("http", proxyUrl, "https", proxyUrl));
        return true;
    }

    private static boolean shouldBypassFreeSearchProxy(String url) {
        String proxyUrl = env("FREE_SEARCH_PROXY_URL");
        if (proxyUrl.isBlank()) {
            return true;
        }
        String hostname = host(url);
        if (hostname.isBlank()) {
            return false;
        }
        for (String entry : noProxyEntries()) {
            if ("*".equals(entry)) {
                return true;
            }
            if (entry.startsWith(".") && (hostname.equals(entry.substring(1)) || hostname.endsWith(entry))) {
                return true;
            }
            if (hostname.equals(entry) || hostname.endsWith("." + entry)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> noProxyEntries() {
        String configured = firstNonBlank(env("NO_PROXY"), env("no_proxy"), DEFAULT_NO_PROXY);
        List<String> entries = new ArrayList<>();
        for (String item : configured.split(",")) {
            String value = item.trim().toLowerCase(Locale.ROOT);
            if (!value.isBlank()) {
                entries.add(value);
            }
        }
        return entries;
    }

    private static String duckDuckGoSearchUrl(String query) {
        String baseUrl = firstNonBlank(env("FREE_SEARCH_DDG_URL"), "https://html.duckduckgo.com/html/").trim();
        String separator = baseUrl.contains("?") ? "&" : "?";
        return trimRight(baseUrl, '?', '&') + separator + "q=" + urlEncode(query);
    }

    private static Map<String, String> searchRequestHeaders(String query) {
        Map<String, String> headers = requestHeaders();
        if (containsCjk(query)) {
            headers.put("Accept-Language", "zh-CN,zh-Hans;q=0.9,zh;q=0.8,en;q=0.6");
        } else {
            headers.put("Accept-Language", "en-US,en;q=0.9");
        }
        return headers;
    }

    private static Map<String, String> requestHeaders() {
        return new LinkedHashMap<>(Map.of("User-Agent", USER_AGENT));
    }

    private static List<SearchRow> filterRankedRows(String query, List<SearchRow> rows) {
        List<SearchRow> preferred = new ArrayList<>();
        List<SearchRow> deferred = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (SearchRow row : rows) {
            if (row.url().isBlank() || row.title().isBlank() || !seen.add(row.url())) {
                continue;
            }
            if (isLowFetchValueUrl(row.url())) {
                deferred.add(row);
            } else {
                preferred.add(row);
            }
        }
        preferred.addAll(deferred);
        return preferred;
    }

    private static boolean rowsAreUsable(String query, List<SearchRow> rows) {
        if (rows.isEmpty()) {
            return false;
        }
        if (!isTimelyQuery(query)) {
            return true;
        }
        for (SearchRow row : rows.stream().limit(3).toList()) {
            if ("bing-card".equals(row.source())) {
                return true;
            }
            if (!isLowConfidenceResultDomain(row.url())) {
                if (matchTermCount(query, row) >= 1 || !row.date().isBlank() || !row.origin().isBlank()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int matchTermCount(String query, SearchRow row) {
        String haystack = (row.title() + " " + row.snippet() + " " + row.url() + " " + row.origin())
                .toLowerCase(Locale.ROOT);
        int count = 0;
        for (String term : queryTerms(query)) {
            if (haystack.contains(term)) {
                count++;
            }
        }
        return count;
    }

    private static List<String> queryTerms(String query) {
        Matcher matcher = Pattern.compile("[A-Za-z0-9\\p{IsHan}]+").matcher(query == null ? "" : query.toLowerCase(Locale.ROOT));
        List<String> terms = new ArrayList<>();
        while (matcher.find() && terms.size() < 12) {
            String term = matcher.group();
            List<String> expanded = new ArrayList<>();
            if (containsCjk(term)) {
                if (term.length() >= 2) {
                    expanded.add(term.length() <= 4 ? term : term.substring(0, 2));
                    for (int index = 0; index + 2 <= term.length() && term.length() <= 4; index++) {
                        expanded.add(term.substring(index, index + 2));
                    }
                }
            } else {
                expanded.add(term);
            }
            for (String item : expanded) {
                if (item.length() >= 2 && !QUERY_STOPWORDS.contains(item) && !terms.contains(item)) {
                    terms.add(item);
                }
            }
        }
        return terms;
    }

    private static boolean isLowFetchValueUrl(String url) {
        String domain = normalizedDomain(url);
        return LOW_FETCH_VALUE_DOMAINS.stream().anyMatch(item -> domain.equals(item) || domain.endsWith("." + item));
    }

    private static boolean isLowConfidenceResultDomain(String url) {
        String domain = normalizedDomain(url);
        return LOW_CONFIDENCE_RESULT_DOMAINS.stream().anyMatch(item -> domain.equals(item) || domain.endsWith("." + item));
    }

    private static String engineDisplayName(String engine) {
        return switch (engine) {
            case "duckduckgo" -> "DuckDuckGo";
            case "duckduckgo-jina" -> "DuckDuckGo (via jina.ai)";
            case "bing" -> "Bing";
            default -> engine;
        };
    }

    private static List<String> configuredPaidSearchProviders() {
        return PAID_SEARCH_PROVIDER_ORDER.stream()
                .filter(provider -> !env(PAID_SEARCH_PROVIDER_KEY_ENVS.get(provider)).isBlank())
                .toList();
    }

    private static String safeEnvChoice(String name, String defaultValue, Set<String> allowed) {
        String raw = env(name).trim();
        if (raw.isBlank()) {
            return defaultValue;
        }
        String normalized = raw.toLowerCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : defaultValue;
    }

    private static String extractBochaAnswer(Map<String, Object> data) {
        Object[] candidates = {
                data.get("summary"),
                data.get("answer"),
                nested(data, "data", "summary"),
                nested(data, "data", "answer"),
                nested(data, "data", "message")
        };
        for (Object value : candidates) {
            String text = stringValue(value).trim();
            if (!text.isBlank()) {
                return text;
            }
        }
        Object value = nested(data, "data", "webPages", "value");
        if (value instanceof List<?> list) {
            List<String> snippets = new ArrayList<>();
            for (Object item : list.stream().limit(3).toList()) {
                Map<String, Object> row = stringObjectMap(item);
                String snippet = firstNonBlank(stringValue(row.get("summary")), stringValue(row.get("snippet")));
                if (!snippet.isBlank()) {
                    snippets.add(snippet);
                }
            }
            if (!snippets.isEmpty()) {
                return String.join("\n\n", snippets);
            }
        }
        return "";
    }

    private static List<String> extractBochaUrls(Map<String, Object> data, int maxResults) {
        Object candidates = firstList(
                nested(data, "data", "webPages", "value"),
                nested(data, "webPages", "value"),
                nested(data, "data", "webPages"),
                data.get("webPages"),
                nested(data, "data", "results"),
                data.get("results")
        );
        List<String> urls = new ArrayList<>();
        if (candidates instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> row = stringObjectMap(item);
                String url = firstNonBlank(stringValue(row.get("url")), stringValue(row.get("link")));
                if (!url.isBlank()) {
                    urls.add(url);
                }
                if (urls.size() >= maxResults) {
                    break;
                }
            }
        }
        return urls;
    }

    private static String extractChoiceAnswer(Map<String, Object> data) {
        Object choices = data.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty()) {
            Map<String, Object> first = stringObjectMap(list.get(0));
            Object message = first.get("message");
            if (message instanceof Map<?, ?> map) {
                return stringValue(stringObjectMap(map).get("content")).trim();
            }
        }
        return "";
    }

    private static List<String> parseCitationUrls(Map<String, Object> data) {
        for (String key : List.of("citations", "search_results", "web_search_results", "sources")) {
            Object entries = data.get(key);
            if (!(entries instanceof List<?> list)) {
                continue;
            }
            List<String> urls = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String string) {
                    urls.add(string);
                } else {
                    Map<String, Object> row = stringObjectMap(item);
                    String url = firstNonBlank(
                            stringValue(row.get("url")),
                            stringValue(row.get("link")),
                            stringValue(row.get("source_url"))
                    );
                    if (!url.isBlank()) {
                        urls.add(url);
                    }
                }
            }
            if (!urls.isEmpty()) {
                return urls;
            }
        }
        return List.of();
    }

    private static String extractDeclaredCharset(HttpResult response) {
        Matcher header = CHARSET_HEADER_PATTERN.matcher(header(response, "Content-Type"));
        if (header.find()) {
            return header.group(1).replace("\"", "").replace("'", "").trim();
        }
        String head = new String(response.content(), 0, Math.min(response.content().length, 4096), StandardCharsets.ISO_8859_1);
        Matcher meta = CHARSET_META_PATTERN.matcher(head);
        return meta.find() ? meta.group(1).trim() : "";
    }

    private static double scoreDecodedText(String value) {
        if (value == null || value.isEmpty()) {
            return Double.NEGATIVE_INFINITY;
        }
        double score = 0.0;
        score -= count(value, "\uFFFD") * 8.0;
        for (String marker : List.of("mojibake", "Ã", "Â", "â", "ï¿½")) {
            score -= count(value, marker) * 3.0;
        }
        score += value.codePoints().filter(code -> Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN).count() * 0.15;
        score += value.codePoints().filter(Character::isLetterOrDigit).count() * 0.02;
        return score;
    }

    private static String textWithNewLines(Element element) {
        List<String> parts = new ArrayList<>();
        collectText(element, parts);
        return String.join("\n", parts).replaceAll("\\n{3,}", "\n\n").trim();
    }

    private static void collectText(Node node, List<String> parts) {
        if (node instanceof TextNode textNode) {
            String text = textNode.text().trim();
            if (!text.isBlank()) {
                parts.add(text);
            }
            return;
        }
        for (Node child : node.childNodes()) {
            collectText(child, parts);
        }
    }

    private static String header(HttpResult response, String name) {
        for (Map.Entry<String, String> entry : response.headers().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return "";
    }

    private static boolean envFlag(String key, boolean defaultValue) {
        String value = env(key).trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) {
            return defaultValue;
        }
        return Set.of("1", "true", "yes", "on", "enabled").contains(value);
    }

    private static String env(String key) {
        String property = System.getProperty(key);
        if (property != null) {
            return property.trim();
        }
        String value = System.getenv(key);
        return value == null ? "" : value.trim();
    }

    private static int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static Map<String, String> stringMap(Object value) {
        Map<String, String> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, mapValue) -> result.put(String.valueOf(key), String.valueOf(mapValue)));
        }
        return result;
    }

    private static Map<String, Object> stringObjectMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        }
        return result;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(WebTools::stringValue).toList();
    }

    private static boolean containsCjk(String value) {
        return value != null && value.codePoints()
                .anyMatch(code -> Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN);
    }

    private static String stripTags(String value) {
        if (value == null) {
            return "";
        }
        return Jsoup.parse(value).text().replaceAll("\\s+", " ").trim();
    }

    private static String decodeDuckDuckGoRedirect(String url) {
        try {
            URI uri = URI.create(url);
            if (!"/l/".equals(uri.getPath())) {
                return url;
            }
            String query = uri.getRawQuery();
            if (query == null) {
                return url;
            }
            for (String item : query.split("&")) {
                int pos = item.indexOf('=');
                if (pos > 0 && "uddg".equals(item.substring(0, pos))) {
                    return urlDecode(item.substring(pos + 1));
                }
            }
        } catch (Exception ignored) {
            return url;
        }
        return url;
    }

    private static String decodeBingRedirect(String url) {
        try {
            URI uri = URI.create(url);
            if (!host(url).contains("bing.com") || !"/ck/a".equals(uri.getPath())) {
                return url;
            }
            String query = uri.getRawQuery();
            if (query == null) {
                return url;
            }
            for (String item : query.split("&")) {
                int pos = item.indexOf('=');
                if (pos <= 0 || !"u".equals(item.substring(0, pos))) {
                    continue;
                }
                String encoded = urlDecode(item.substring(pos + 1));
                if (encoded.startsWith("a1")) {
                    String payload = encoded.substring(2);
                    String padded = payload + "=".repeat((4 - payload.length() % 4) % 4);
                    String decoded = new String(Base64.getUrlDecoder().decode(padded), StandardCharsets.UTF_8);
                    return decoded.startsWith("http://") || decoded.startsWith("https://") ? decoded : url;
                }
                return encoded.startsWith("http://") || encoded.startsWith("https://") ? encoded : url;
            }
        } catch (Exception ignored) {
            return url;
        }
        return url;
    }

    private static String normalizedDomain(String url) {
        String domain = host(url);
        return domain.startsWith("www.") ? domain.substring(4) : domain;
    }

    private static String host(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String htmlUnescape(String value) {
        return value == null ? "" : value.replace("&amp;", "&").replace("&quot;", "\"");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static Element firstElement(Element... elements) {
        for (Element element : elements) {
            if (element != null) {
                return element;
            }
        }
        return null;
    }

    private static Object nested(Map<String, Object> source, String... keys) {
        Object current = source;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = stringObjectMap(map).get(key);
        }
        return current;
    }

    private static Object firstList(Object... values) {
        for (Object value : values) {
            if (value instanceof List<?>) {
                return value;
            }
        }
        return null;
    }

    private static Map<String, Object> linkedMap(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private static String trimRight(String value, char... chars) {
        int end = value.length();
        while (end > 0) {
            char current = value.charAt(end - 1);
            boolean match = false;
            for (char item : chars) {
                if (current == item) {
                    match = true;
                    break;
                }
            }
            if (!match) {
                break;
            }
            end--;
        }
        return value.substring(0, end);
    }

    private static long count(String value, String needle) {
        if (value == null || needle == null || needle.isEmpty()) {
            return 0;
        }
        long count = 0;
        int index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static byte[] bytes(String value) {
        return (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
    }

    private static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (IOException exception) {
            return "{}";
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
