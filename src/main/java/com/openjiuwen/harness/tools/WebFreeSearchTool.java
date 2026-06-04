package com.openjiuwen.harness.tools;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mirrors Python's {@code WebFreeSearchTool} in {@code openjiuwen.harness.tools.web_tools}.
 */
public class WebFreeSearchTool extends AbstractHarnessTool {

    private static final Pattern DDG_LINK_PATTERN = Pattern.compile(
            "<a[^>]+class=\"result__a\"[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern DDG_SNIPPET_PATTERN = Pattern.compile(
            "<a[^>]+class=\"result__snippet\"[^>]*>(.*?)</a>|<div[^>]+class=\"result__snippet\"[^>]*>(.*?)</div>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern JINA_MD_LINK_PATTERN = Pattern.compile("\\[([^\\]\\n]+)\\]\\((https?://[^\\s)]+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final String FREE_SEARCH_DDG_ENABLED = "FREE_SEARCH_DDG_ENABLED";
    private static final String FREE_SEARCH_BING_ENABLED = "FREE_SEARCH_BING_ENABLED";
    private static final String FREE_SEARCH_PROXY_URL = "FREE_SEARCH_PROXY_URL";
    private static final String DEFAULT_NO_PROXY = "127.0.0.1,.huawei.com,localhost,local,.local,10.155.97.247,.myhuaweicloud.com, api.openai.rnd.huawei.com";

    private final String language;

    public WebFreeSearchTool() {
        this("cn");
    }

    public WebFreeSearchTool(String language) {
        super(toolCard("free_search", "free_search", "Search the web using free search providers."), null);
        this.language = language == null || language.isBlank() ? "cn" : language;
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String query = String.valueOf(inputs.getOrDefault("query", "")).trim();
        int maxResults = parseMaxResults(inputs.get("max_results"));
        if (query.isBlank()) {
            return new ToolOutput(false, null, "[ERROR]: query cannot be empty.");
        }
        if (!WebTools.isFreeSearchEnabled()) {
            return new ToolOutput(false, null, "[ERROR]: free search failed: all free search engines are disabled");
        }
        try {
            SearchResult result = search(query, maxResults);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("engine", result.engine());
            data.put("query", query);
            data.put("language", language);
            data.put("rows", result.rows());
            data.put("text", render(result.engine(), result.rows()));
            return new ToolOutput(true, data, null);
        } catch (Exception e) {
            return new ToolOutput(false, null, "[ERROR]: free search failed: " + e.getMessage());
        }
    }

    protected SearchResult search(String query, int maxResults) {
        List<String> errors = new ArrayList<>();
        if (envFlag(FREE_SEARCH_DDG_ENABLED, false)) {
            try {
                SearchResult result = searchDuckDuckGo(query, maxResults);
                if (!result.rows().isEmpty()) {
                    return result;
                }
                errors.add("duckduckgo: empty result");
            } catch (RuntimeException failure) {
                errors.add("duckduckgo: " + failure.getMessage());
            }
            try {
                SearchResult result = searchDuckDuckGoViaJina(query, maxResults);
                if (!result.rows().isEmpty()) {
                    return result;
                }
                errors.add("duckduckgo-jina: empty result");
            } catch (RuntimeException failure) {
                errors.add("duckduckgo-jina: " + failure.getMessage());
            }
        }
        if (envFlag(FREE_SEARCH_BING_ENABLED, false)) {
            try {
                SearchResult result = searchBing(query, maxResults);
                if (!result.rows().isEmpty()) {
                    return result;
                }
                errors.add("bing: empty result");
            } catch (RuntimeException failure) {
                errors.add("bing: " + failure.getMessage());
            }
        }
        if (errors.isEmpty()) {
            throw new IllegalStateException("all free search engines are disabled");
        }
        throw new IllegalStateException(String.join(" | ", errors));
    }

    protected SearchResult searchDuckDuckGo(String query, int maxResults) {
        String url = "https://duckduckgo.com/html/?q=" + encode(query);
        String html = httpGet(url, query);
        return new SearchResult("DuckDuckGo", parseDuckDuckGoHtml(html, maxResults));
    }

    protected SearchResult searchDuckDuckGoViaJina(String query, int maxResults) {
        String url = "https://r.jina.ai/http://duckduckgo.com/html/?q=" + encode(query);
        String text = httpGet(url, query);
        return new SearchResult("DuckDuckGo", parseJinaMarkdown(text, maxResults));
    }

    protected SearchResult searchBing(String query, int maxResults) {
        String url = "https://www.bing.com/search?q=" + encode(query);
        String html = httpGet(url, query);
        return new SearchResult("Bing", parseBingHtml(html, maxResults));
    }

    protected String httpGet(String url, String query) {
        try {
            URL target = new URL(url);
            URLConnection rawConnection = openConnection(target);
            HttpURLConnection connection = (HttpURLConnection) rawConnection;
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", query != null && containsCjk(query)
                    ? "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
                    : "Mozilla/5.0");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(line);
                }
                return builder.toString();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    protected URLConnection openConnection(URL url) throws java.io.IOException {
        Proxy proxy = proxyForUrl(url.toString());
        if (proxy == Proxy.NO_PROXY) {
            return url.openConnection();
        }
        return url.openConnection(proxy);
    }

    protected List<Map<String, String>> parseDuckDuckGoHtml(String html, int maxResults) {
        List<Map<String, String>> rows = new ArrayList<>();
        Matcher links = DDG_LINK_PATTERN.matcher(html == null ? "" : html);
        Matcher snippets = DDG_SNIPPET_PATTERN.matcher(html == null ? "" : html);
        List<String> snippetValues = new ArrayList<>();
        while (snippets.find()) {
            String snippet = snippets.group(1) != null ? snippets.group(1) : snippets.group(2);
            snippetValues.add(stripTags(snippet));
        }
        int index = 0;
        while (links.find() && rows.size() < maxResults) {
            String href = links.group(1);
            String titleRaw = links.group(2);
            String snippet = index < snippetValues.size() ? snippetValues.get(index) : "";
            rows.add(resultRow(
                    stripTags(titleRaw).isBlank() ? "Result " + (index + 1) : stripTags(titleRaw),
                    decodeDdgRedirect(href),
                    snippet,
                    "duckduckgo"
            ));
            index++;
        }
        return rows;
    }

    protected List<Map<String, String>> parseJinaMarkdown(String text, int maxResults) {
        List<Map<String, String>> rows = new ArrayList<>();
        Matcher matcher = JINA_MD_LINK_PATTERN.matcher(text == null ? "" : text);
        while (matcher.find() && rows.size() < maxResults) {
            String title = stripTags(matcher.group(1));
            String href = matcher.group(2);
            if (title.isBlank() || title.startsWith("Image ") || href.contains("duckduckgo.com")) {
                continue;
            }
            rows.add(resultRow(title, href, "", "duckduckgo-jina"));
        }
        return rows;
    }

    protected List<Map<String, String>> parseBingHtml(String html, int maxResults) {
        List<Map<String, String>> rows = new ArrayList<>();
        Pattern itemPattern = Pattern.compile(
                "<li[^>]+class=\"b_algo\"[^>]*>\\s*<h2><a href=\"([^\"]+)\">(.*?)</a></h2>(?:\\s*<div[^>]+class=\"b_caption\"><p>(.*?)</p></div>)?",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher matcher = itemPattern.matcher(html == null ? "" : html);
        while (matcher.find() && rows.size() < maxResults) {
            rows.add(resultRow(
                    stripTags(matcher.group(2)),
                    matcher.group(1),
                    stripTags(matcher.group(3)),
                    "bing"
            ));
        }
        return rows;
    }

    protected Map<String, String> resultRow(String title, String url, String snippet, String source) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("title", title == null ? "" : title);
        row.put("url", url == null ? "" : url);
        row.put("snippet", snippet == null ? "" : snippet);
        row.put("source", source == null ? "" : source);
        return row;
    }

    private static boolean containsCjk(String value) {
        return value != null && value.chars().anyMatch(ch -> ch >= 0x4E00 && ch <= 0x9FFF);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String stripTags(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return TAG_PATTERN.matcher(value).replaceAll("").replace("&nbsp;", " ").trim();
    }

    private static String decodeDdgRedirect(String href) {
        if (href == null) {
            return "";
        }
        int idx = href.indexOf("uddg=");
        if (idx < 0) {
            return href;
        }
        String encoded = href.substring(idx + 5);
        int amp = encoded.indexOf('&');
        if (amp >= 0) {
            encoded = encoded.substring(0, amp);
        }
        try {
            return java.net.URLDecoder.decode(encoded, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return href;
        }
    }

    private String render(String engine, List<Map<String, String>> rows) {
        StringBuilder builder = new StringBuilder("Free search results (")
                .append(engine)
                .append(")");
        for (Map<String, String> row : rows) {
            builder.append("\n- ")
                    .append(row.getOrDefault("title", ""))
                    .append(": ")
                    .append(row.getOrDefault("url", ""));
            String snippet = row.getOrDefault("snippet", "");
            if (!snippet.isBlank()) {
                builder.append("\n  ").append(snippet);
            }
        }
        return builder.toString();
    }

    private int parseMaxResults(Object raw) {
        if (raw instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        if (raw != null) {
            try {
                return Math.max(1, Integer.parseInt(String.valueOf(raw)));
            } catch (NumberFormatException ignored) {
                return 5;
            }
        }
        return 5;
    }

    static Proxy proxyForUrl(String url) {
        String proxyUrl = config(FREE_SEARCH_PROXY_URL);
        if (proxyUrl.isBlank() || shouldBypassFreeSearchProxy(url)) {
            return Proxy.NO_PROXY;
        }
        try {
            URI uri = URI.create(proxyUrl);
            int port = uri.getPort();
            if (port < 0) {
                port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            }
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(uri.getHost(), port));
        } catch (RuntimeException ignored) {
            return Proxy.NO_PROXY;
        }
    }

    static boolean shouldBypassFreeSearchProxy(String url) {
        if (config(FREE_SEARCH_PROXY_URL).isBlank()) {
            return true;
        }
        String host;
        try {
            host = URI.create(url).getHost();
        } catch (RuntimeException ignored) {
            return false;
        }
        if (host == null || host.isBlank()) {
            return false;
        }
        String normalizedHost = host.toLowerCase();
        for (String entry : noProxyEntries()) {
            if ("*".equals(entry)) {
                return true;
            }
            if (entry.startsWith(".") && (normalizedHost.equals(entry.substring(1)) || normalizedHost.endsWith(entry))) {
                return true;
            }
            if (normalizedHost.equals(entry) || normalizedHost.endsWith("." + entry)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> noProxyEntries() {
        String configured = config("NO_PROXY");
        if (configured.isBlank()) {
            configured = config("no_proxy");
        }
        if (configured.isBlank()) {
            configured = DEFAULT_NO_PROXY;
        }
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(entry -> !entry.isBlank())
                .toList();
    }

    private static boolean envFlag(String key, boolean defaultValue) {
        String value = config(key).trim().toLowerCase();
        if (value.isBlank()) {
            return defaultValue;
        }
        return List.of("1", "true", "yes", "on", "enabled").contains(value);
    }

    private static String config(String key) {
        String property = System.getProperty(key);
        if (property != null) {
            return property.trim();
        }
        String value = System.getenv(key);
        return value == null ? "" : value.trim();
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
        throw ErrorHelper.buildError(StatusCode.TOOL_STREAM_NOT_SUPPORTED, "card", getCard().toString());
    }

    protected record SearchResult(String engine, List<Map<String, String>> rows) {
        public SearchResult {
            rows = rows == null ? new ArrayList<>() : rows;
        }
    }
}
