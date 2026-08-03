/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Parser for generic web page URLs.
 *
 * <p>Mirrors Python's {@code WebPageParser} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/web_page_parser.py}.</p>
 */
public class WebPageParser extends HTMLFileParser {

    public static final Pattern HTTP_URL_PATTERN = Pattern.compile("^https?://\\S+", Pattern.CASE_INSENSITIVE);
    public static final Pattern WECHAT_MP_URL_PATTERN = Pattern.compile(
            "^https?://(?:mp\\.weixin\\.qq\\.com|.*?\\.weixin\\.qq\\.com)/s\\b.*",
            Pattern.CASE_INSENSITIVE
    );

    private static final Logger LOGGER = LoggerFactory.getLogger(WebPageParser.class);

    private final double timeout;
    private final String userAgent;
    private final HttpClient httpClient;

    public WebPageParser() {
        this(DEFAULT_TIMEOUT, DEFAULT_USER_AGENT);
    }

    public WebPageParser(double timeout, String userAgent) {
        this(timeout, userAgent, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1L, Math.round(timeout))))
                .build());
    }

    protected WebPageParser(double timeout, String userAgent, HttpClient httpClient) {
        this.timeout = timeout;
        this.userAgent = userAgent == null || userAgent.isBlank() ? DEFAULT_USER_AGENT : userAgent;
        this.httpClient = httpClient;
    }

    /**
     * Mirrors Python's module-level {@code _is_wechat_article_url} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/web_page_parser.py}.
     */
    public static boolean isWechatArticleUrl(String url) {
        return url != null && WECHAT_MP_URL_PATTERN.matcher(url.strip()).find();
    }

    /**
     * Mirrors Python's module-level {@code parse_web_page_url} alias in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/web_page_parser.py}.
     */
    public static CompletableFuture<List<Document>> parseWebPageUrl(String url) {
        return parseWebPageUrl(url, "");
    }

    /**
     * Mirrors Python's module-level {@code parse_web_page_url} alias in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/web_page_parser.py}.
     */
    public static CompletableFuture<List<Document>> parseWebPageUrl(String url, String docId) {
        return new WebPageParser().parseUrl(url, docId);
    }

    /**
     * Mirrors Python's {@code WebPageParser._validate_url} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/web_page_parser.py}.
     */
    public static void validateUrl(String url) {
        String safeUrl = url == null ? "" : url.strip();
        if (safeUrl.isBlank() || !HTTP_URL_PATTERN.matcher(safeUrl).find()) {
            throw fetchError("Not a valid HTTP URL: '" + safeUrl + "'");
        }
        if (isWechatArticleUrl(safeUrl)) {
            throw fetchError("Use WeChatArticleParser for WeChat URLs");
        }
    }

    @Override
    public CompletableFuture<List<Document>> parseAsync(
            String doc,
            String docId,
            BaseModelClient llmClient,
            Map<String, Object> options
    ) {
        Map<String, Object> safeOptions = options == null ? Map.of() : options;
        double effectiveTimeout = optionAsDouble(safeOptions, "timeout", timeout);
        String effectiveUserAgent = optionAsString(safeOptions, "user_agent",
                optionAsString(safeOptions, "userAgent", userAgent));
        return parseUrl(doc, docId == null || docId.isBlank() ? doc : docId, effectiveTimeout, effectiveUserAgent);
    }

    @Override
    public boolean supports(String doc) {
        if (doc == null || !HTTP_URL_PATTERN.matcher(doc.strip()).find()) {
            return false;
        }
        return !isWechatArticleUrl(doc);
    }

    public CompletableFuture<List<Document>> parseUrl(String url, String docId) {
        return parseUrl(url, docId, timeout, userAgent);
    }

    public CompletableFuture<List<Document>> parseUrl(String url, String docId, double requestTimeout, String requestUserAgent) {
        validateUrl(url);
        String effectiveId = docId == null || docId.isBlank() ? url : docId;
        return downloadHtml(url, requestTimeout, requestUserAgent)
                .thenCompose(html -> HTMLFileParser.parseHtml(html, effectiveId, url))
                .thenApply(documents -> {
                    for (Document document : documents) {
                        Map<String, Object> metadata = document.getMetadata() == null
                                ? new LinkedHashMap<>()
                                : new LinkedHashMap<>(document.getMetadata());
                        metadata.put("source_url", url);
                        document.setMetadata(metadata);
                        LOGGER.info("Parsed web page: url={} title={}", url,
                                metadata.getOrDefault("title", "(鏃犳爣棰?"));
                    }
                    return documents;
                });
    }

    /**
     * Mirrors Python's {@code WebPageParser._download_html} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/web_page_parser.py}.
     */
    protected CompletableFuture<String> downloadHtml(String url, double requestTimeout, String requestUserAgent) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(Math.max(1L, Math.round(requestTimeout))))
                    .header("User-Agent", requestUserAgent == null || requestUserAgent.isBlank()
                            ? DEFAULT_USER_AGENT
                            : requestUserAgent)
                    .build();
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(fetchError("Web page fetch failed for " + url + ": "
                    + exception.getMessage(), exception));
        }

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    int status = response.statusCode();
                    if (status >= 400) {
                        throw fetchError("Web page request failed: " + status + " for " + url);
                    }
                    return response.body();
                })
                .exceptionally(error -> {
                    if (error.getCause() instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw fetchError("Web page fetch failed for " + url + ": " + error.getMessage(), error);
                });
    }

    private static double optionAsDouble(Map<String, Object> options, String key, double defaultValue) {
        if (options == null || !options.containsKey(key)) {
            return defaultValue;
        }
        Object value = options.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static String optionAsString(Map<String, Object> options, String key, String defaultValue) {
        if (options == null || !options.containsKey(key) || options.get(key) == null) {
            return defaultValue;
        }
        return String.valueOf(options.get(key));
    }

    private static RuntimeException fetchError(String message) {
        return fetchError(message, null);
    }

    private static RuntimeException fetchError(String message, Throwable cause) {
        return ErrorHelper.buildError(
                StatusCode.RETRIEVAL_INDEXING_FETCH_ERROR,
                message,
                null,
                cause,
                Map.of("error_msg", message)
        );
    }
}
