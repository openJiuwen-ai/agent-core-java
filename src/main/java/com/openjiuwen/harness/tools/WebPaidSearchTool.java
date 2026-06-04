package com.openjiuwen.harness.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code WebPaidSearchTool} registration surface in
 * {@code openjiuwen.harness.tools.web_tools}.
 */
public class WebPaidSearchTool extends AbstractHarnessTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String language;

    public WebPaidSearchTool() {
        this("cn");
    }

    public WebPaidSearchTool(String language) {
        super(toolCard("paid_search", "paid_search", "Search the web using configured paid providers."), null);
        this.language = language == null || language.isBlank() ? "cn" : language;
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String query = String.valueOf(inputs.getOrDefault("query", "")).trim();
        String provider = String.valueOf(inputs.getOrDefault("provider", "auto")).trim().toLowerCase();
        int maxResults = parseMaxResults(inputs.get("max_results"));

        if (query.isBlank()) {
            return new ToolOutput(false, null, "[ERROR]: query cannot be empty.");
        }
        if (!List.of("auto", "bocha", "jina", "serper", "perplexity").contains(provider)) {
            return new ToolOutput(false, null, "[ERROR]: provider must be one of auto|bocha|jina|serper|perplexity.");
        }

        List<String> order = new ArrayList<>();
        if (!"auto".equals(provider)) {
            order.add(provider);
        } else {
            order.addAll(List.of("bocha", "perplexity", "serper", "jina"));
        }

        List<String> errors = new ArrayList<>();
        for (String name : order) {
            try {
                SearchResult result = switch (name) {
                    case "bocha" -> searchBocha(query, maxResults);
                    case "jina" -> searchJina(query, maxResults);
                    case "serper" -> searchSerper(query, maxResults);
                    case "perplexity" -> searchPerplexity(query, maxResults);
                    default -> null;
                };
                if (result == null) {
                    errors.add(name + ": runner not found");
                    continue;
                }
                return new ToolOutput(true, result.toData(language, query), null);
            } catch (Exception e) {
                errors.add(name + ": " + e.getMessage());
            }
        }
        return new ToolOutput(false, null, "[ERROR]: paid search failed: " + String.join("; ", errors));
    }

    protected SearchResult searchBocha(String query, int maxResults) {
        String key = env("BOCHA_API_KEY");
        if (key.isBlank()) {
            throw new IllegalStateException("BOCHA_API_KEY is not set");
        }
        String apiUrl = env("BOCHA_API_URL");
        if (apiUrl.isBlank()) {
            apiUrl = "https://api.bocha.cn/v1/web-search";
        }
        JsonNode data = postJson(apiUrl, Map.of("query", query, "summary", true, "count", maxResults), Map.of(
                "Authorization", "Bearer " + key,
                "Content-Type", "application/json"
        ));
        return new SearchResult("bocha", extractBochaAnswer(data), extractBochaUrls(data, maxResults));
    }

    protected SearchResult searchJina(String query, int maxResults) {
        String key = env("JINA_API_KEY");
        if (key.isBlank()) {
            throw new IllegalStateException("JINA_API_KEY is not set");
        }
        JsonNode data = postJson("https://deepsearch.jina.ai/v1/chat/completions", Map.of(
                "model", "jina-deepsearch-v1",
                "messages", List.of(Map.of("role", "user", "content", query)),
                "stream", false,
                "reasoning_effort", "low"
        ), Map.of(
                "Authorization", "Bearer " + key,
                "Content-Type", "application/json"
        ));
        String answer = "";
        JsonNode choices = data.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            answer = choices.get(0).path("message").path("content").asText("");
        }
        List<String> urls = new ArrayList<>();
        for (String url : answer.split("\\s+")) {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                urls.add(stripTrailingPunctuation(url));
            }
        }
        return new SearchResult("jina", answer.trim(), urls);
    }

    protected SearchResult searchSerper(String query, int maxResults) {
        String key = env("SERPER_API_KEY");
        if (key.isBlank()) {
            throw new IllegalStateException("SERPER_API_KEY is not set");
        }
        JsonNode data = postJson("https://google.serper.dev/search", Map.of("q", query, "num", maxResults), Map.of(
                "X-API-KEY", key,
                "Content-Type", "application/json"
        ));
        List<String> urls = new ArrayList<>();
        JsonNode organic = data.path("organic");
        if (organic.isArray()) {
            for (JsonNode item : organic) {
                if (urls.size() >= maxResults) {
                    break;
                }
                String link = item.path("link").asText("");
                if (!link.isBlank()) {
                    urls.add(link);
                }
            }
        }
        return new SearchResult("serper", "", urls);
    }

    protected SearchResult searchPerplexity(String query, int maxResults) {
        String key = env("PERPLEXITY_API_KEY");
        if (key.isBlank()) {
            throw new IllegalStateException("PERPLEXITY_API_KEY is not set");
        }
        JsonNode data = postJson("https://api.perplexity.ai/chat/completions", Map.of(
                "model", "sonar",
                "messages", List.of(Map.of("role", "user", "content", query)),
                "stream", false
        ), Map.of(
                "Authorization", "Bearer " + key,
                "Content-Type", "application/json"
        ));
        String answer = data.path("choices").isArray() && !data.path("choices").isEmpty()
                ? data.path("choices").get(0).path("message").path("content").asText("")
                : "";
        List<String> urls = new ArrayList<>();
        for (String candidate : extractCitations(data)) {
            if (!candidate.isBlank()) {
                urls.add(candidate);
            }
        }
        return new SearchResult("perplexity", answer.trim(), urls);
    }

    protected JsonNode postJson(String url, Object payload, Map<String, String> headers) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
            try (var out = connection.getOutputStream()) {
                out.write(MAPPER.writeValueAsBytes(payload));
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(line);
                }
                return MAPPER.readTree(builder.toString());
            }
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    protected List<String> extractBochaUrls(JsonNode data, int maxResults) {
        List<String> urls = new ArrayList<>();
        JsonNode[] candidates = new JsonNode[]{
                data.path("data").path("webPages").path("value"),
                data.path("webPages").path("value"),
                data.path("data").path("webPages"),
                data.path("webPages"),
                data.path("data").path("results"),
                data.path("results")
        };
        for (JsonNode container : candidates) {
            if (!container.isArray()) {
                continue;
            }
            for (JsonNode item : container) {
                if (urls.size() >= maxResults) {
                    break;
                }
                String url = item.path("url").asText("");
                if (url.isBlank()) {
                    url = item.path("link").asText("");
                }
                if (!url.isBlank()) {
                    urls.add(url);
                }
            }
            if (!urls.isEmpty()) {
                break;
            }
        }
        return urls;
    }

    protected String extractBochaAnswer(JsonNode data) {
        for (String key : List.of("summary", "answer", "content", "text")) {
            String value = data.path("data").path(key).asText("");
            if (!value.isBlank()) {
                return value;
            }
            value = data.path(key).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    protected List<String> extractCitations(JsonNode data) {
        List<String> urls = new ArrayList<>();
        for (String key : List.of("citations", "search_results", "web_search_results", "sources")) {
            JsonNode entries = data.path(key);
            if (!entries.isArray()) {
                continue;
            }
            for (JsonNode item : entries) {
                if (item.isTextual()) {
                    urls.add(item.asText());
                } else {
                    String url = item.path("url").asText("");
                    if (url.isBlank()) {
                        url = item.path("link").asText("");
                    }
                    if (!url.isBlank()) {
                        urls.add(url);
                    }
                }
            }
            if (!urls.isEmpty()) {
                break;
            }
        }
        return urls;
    }

    protected Map<String, Object> buildData(SearchResult result, String query) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", result.provider());
        data.put("query", query);
        data.put("language", language);
        data.put("answer", result.answer());
        data.put("urls", result.urls());
        StringBuilder text = new StringBuilder("Paid search provider: ").append(result.provider());
        if (!result.answer().isBlank()) {
            text.append("\nAnswer:\n").append(result.answer());
        }
        if (!result.urls().isEmpty()) {
            text.append("\nURLs:");
            for (int i = 0; i < result.urls().size(); i++) {
                text.append("\n").append(i + 1).append(". ").append(result.urls().get(i));
            }
        }
        if (result.answer().isBlank() && result.urls().isEmpty()) {
            text.append("\nNo usable result payload.");
        }
        data.put("text", text.toString());
        return data;
    }

    protected int parseMaxResults(Object raw) {
        if (raw instanceof Number number) {
            return Math.max(1, Math.min(20, number.intValue()));
        }
        if (raw != null) {
            try {
                return Math.max(1, Math.min(20, Integer.parseInt(String.valueOf(raw))));
            } catch (NumberFormatException ignored) {
                return 8;
            }
        }
        return 8;
    }

    protected String env(String key) {
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

    private static String stripTrailingPunctuation(String url) {
        String value = url == null ? "" : url.trim();
        while (!value.isBlank() && ".,);]}>\"'".indexOf(value.charAt(value.length() - 1)) >= 0) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    protected record SearchResult(String provider, String answer, List<String> urls) {
        public SearchResult {
            urls = urls == null ? List.of() : List.copyOf(urls);
            answer = answer == null ? "" : answer;
            provider = provider == null ? "" : provider;
        }

        protected Map<String, Object> toData(String language, String query) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("provider", provider);
            data.put("language", language);
            data.put("query", query);
            data.put("answer", answer);
            data.put("urls", urls);
            StringBuilder text = new StringBuilder("Paid search provider: ").append(provider);
            if (!answer.isBlank()) {
                text.append("\nAnswer:\n").append(answer);
            }
            if (!urls.isEmpty()) {
                text.append("\nURLs:");
                for (int i = 0; i < urls.size(); i++) {
                    text.append("\n").append(i + 1).append(". ").append(urls.get(i));
                }
            }
            if (answer.isBlank() && urls.isEmpty()) {
                text.append("\nNo usable result payload.");
            }
            data.put("text", text.toString());
            return data;
        }
    }
}
