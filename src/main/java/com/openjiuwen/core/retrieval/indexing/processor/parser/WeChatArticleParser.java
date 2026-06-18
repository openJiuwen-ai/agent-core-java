/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import org.jsoup.nodes.Element;

import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Parser for WeChat official account article URLs.
 *
 * <p>Mirrors Python's {@code WeChatArticleParser} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/wechat_article_parser.py}.</p>
 */
public class WeChatArticleParser extends WebPageParser {

    private static final String SOURCE_TYPE = "wechat_article";

    private final double timeout;
    private final String userAgent;

    public WeChatArticleParser() {
        this(DEFAULT_TIMEOUT, DEFAULT_USER_AGENT);
    }

    public WeChatArticleParser(double timeout, String userAgent) {
        super(timeout, userAgent);
        this.timeout = timeout;
        this.userAgent = normalizeUserAgent(userAgent);
    }

    public WeChatArticleParser(HttpClient httpClient) {
        this(DEFAULT_TIMEOUT, DEFAULT_USER_AGENT, httpClient);
    }

    protected WeChatArticleParser(double timeout, String userAgent, HttpClient httpClient) {
        super(timeout, userAgent, httpClient);
        this.timeout = timeout;
        this.userAgent = normalizeUserAgent(userAgent);
    }

    /**
     * Mirrors Python's imported module-level {@code _is_wechat_article_url} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/wechat_article_parser.py}.
     */
    public static boolean isWechatArticleUrl(String url) {
        return WebPageParser.isWechatArticleUrl(url);
    }

    /**
     * Mirrors Python's module-level {@code parse_wechat_article_url} alias in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/wechat_article_parser.py}.
     */
    public static CompletableFuture<List<Document>> parseWechatArticleUrl(String url) {
        return parseWechatArticleUrl(url, "");
    }

    /**
     * Mirrors Python's module-level {@code parse_wechat_article_url} alias in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/wechat_article_parser.py}.
     */
    public static CompletableFuture<List<Document>> parseWechatArticleUrl(String url, String docId) {
        return new WeChatArticleParser().parseUrl(url, docId);
    }

    /**
     * Mirrors Python's {@code WeChatArticleParser._validate_url} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/wechat_article_parser.py}.
     */
    public static void validateUrl(String url) {
        String safeUrl = url == null ? "" : url.strip();
        if (!isWechatArticleUrl(safeUrl)) {
            throw fetchError("Not a WeChat article URL: '" + safeUrl + "'");
        }
    }

    /**
     * Mirrors Python's module-level {@code _extract_js_content} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/wechat_article_parser.py}.
     */
    static Element extractJsContent(org.jsoup.nodes.Document soup) {
        return soup == null ? null : soup.selectFirst("div#js_content");
    }

    /**
     * Mirrors Python's {@code WeChatArticleParser._parse_html} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/wechat_article_parser.py}.
     */
    public static CompletableFuture<List<Document>> parseWechatHtml(String html, String docId, String source) {
        return CompletableFuture.completedFuture(parseWechatHtmlToDocuments(
                html,
                docId == null ? "" : docId,
                source
        ));
    }

    @Override
    public CompletableFuture<List<Document>> parse(
            String doc,
            String docId,
            BaseModelClient llmClient,
            Map<String, Object> options
    ) {
        Map<String, Object> safeOptions = options == null ? Map.of() : options;
        double effectiveTimeout = optionAsDouble(safeOptions, "timeout", timeout);
        String effectiveUserAgent = optionAsString(safeOptions, "user_agent",
                optionAsString(safeOptions, "userAgent", userAgent));
        String effectiveDocId = docId == null || docId.isBlank() ? doc : docId;
        return parseUrl(doc, effectiveDocId, effectiveTimeout, effectiveUserAgent);
    }

    @Override
    public boolean supports(String doc) {
        return isWechatArticleUrl(doc);
    }

    @Override
    public CompletableFuture<List<Document>> parseUrl(String url, String docId) {
        return parseUrl(url, docId, timeout, userAgent);
    }

    @Override
    public CompletableFuture<List<Document>> parseUrl(
            String url,
            String docId,
            double requestTimeout,
            String requestUserAgent
    ) {
        validateUrl(url);
        String effectiveId = docId == null || docId.isBlank() ? url : docId;
        return downloadHtml(url, requestTimeout, requestUserAgent)
                .thenCompose(html -> parseWechatHtml(html, effectiveId, url))
                .thenApply(documents -> {
                    for (Document document : documents) {
                        Map<String, Object> metadata = document.getMetadata() == null
                                ? new LinkedHashMap<>()
                                : new LinkedHashMap<>(document.getMetadata());
                        metadata.put("source_url", url);
                        document.setMetadata(metadata);
                    }
                    return documents;
                });
    }

    private static List<Document> parseWechatHtmlToDocuments(String html, String docId, String source) {
        org.jsoup.nodes.Document soup = HTMLFileParser.parseHtmlDocument(html);
        String title = HTMLFileParser.extractTitle(soup);
        Element contentNode = extractJsContent(soup);
        if (contentNode == null) {
            throw fetchError("Could not find article content (js_content) in page (source: "
                    + pythonSourceText(source) + ")");
        }

        String text = HTMLFileParser.getTextFromElement(contentNode);
        if (text.isEmpty()) {
            throw fetchError("Article content is empty after parsing (source: "
                    + pythonSourceText(source) + ")");
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("title", title == null || title.isBlank() ? "(无标题)" : title);
        metadata.put("source_type", SOURCE_TYPE);
        return List.of(new Document(docId, text, metadata));
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

    private static String normalizeUserAgent(String value) {
        return value == null || value.isBlank() ? DEFAULT_USER_AGENT : value;
    }

    private static String pythonSourceText(String source) {
        return source == null ? "None" : source;
    }

    private static RuntimeException fetchError(String message) {
        return ErrorHelper.buildError(
                StatusCode.RETRIEVAL_INDEXING_FETCH_ERROR,
                message,
                null,
                null,
                Map.of("error_msg", message)
        );
    }
}
