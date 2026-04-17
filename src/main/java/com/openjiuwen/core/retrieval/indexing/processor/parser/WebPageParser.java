/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generic web page parser.
 */
public class WebPageParser extends Parser {

    private static final Pattern HTTP_URL_PATTERN = Pattern.compile("^https?://.+", Pattern.CASE_INSENSITIVE);
    protected static final Pattern TITLE_META_PATTERN = Pattern.compile(
            "<meta[^>]+property=[\"']og:title[\"'][^>]+content=[\"']([^\"']+)[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    protected static final Pattern TITLE_PATTERN = Pattern.compile(
            "<title[^>]*>(.*?)</title>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ARTICLE_PATTERN = Pattern.compile(
            "<article[^>]*>(.*?)</article>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern BODY_PATTERN = Pattern.compile(
            "<body[^>]*>(.*?)</body>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SCRIPT_STYLE_PATTERN = Pattern.compile(
            "<(script|style)[^>]*>.*?</\\1>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    protected final HttpClient httpClient;

    public WebPageParser() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build());
    }

    public WebPageParser(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options) {
        if (WeChatArticleParser.isWechatArticleUrl(doc)) {
            throw RetrievalExceptions.validation("Use WeChatArticleParser for WeChat URLs");
        }
        String html = fetchHtml(doc);
        String title = extractFirst(html, TITLE_META_PATTERN, extractFirst(html, TITLE_PATTERN, ""));
        String text = extractReadableText(html, ARTICLE_PATTERN);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source_url", doc);
        metadata.put("title", title);
        metadata.put("source_type", "web_page");
        return List.of(new Document(docId, text, metadata));
    }

    @Override
    protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options) {
        return null;
    }

    @Override
    public boolean supports(String doc) {
        return doc != null
                && HTTP_URL_PATTERN.matcher(doc.trim()).matches()
                && !WeChatArticleParser.isWechatArticleUrl(doc);
    }

    protected String fetchHtml(String url) {
        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .GET()
                            .timeout(Duration.ofSeconds(30))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw RetrievalExceptions.validation("Failed to fetch URL: " + url);
        }
    }

    protected static String extractReadableText(String html, Pattern preferredPattern) {
        String body = extractFirst(html, preferredPattern, extractFirst(html, BODY_PATTERN, html));
        body = SCRIPT_STYLE_PATTERN.matcher(body).replaceAll(" ");
        body = TAG_PATTERN.matcher(body).replaceAll(" ");
        return WHITESPACE_PATTERN.matcher(body).replaceAll(" ").trim();
    }

    protected static String extractFirst(String html, Pattern pattern, String fallback) {
        if (html == null) {
            return fallback;
        }
        Matcher matcher = pattern.matcher(html);
        if (!matcher.find()) {
            return fallback;
        }
        return WHITESPACE_PATTERN.matcher(matcher.group(1)).replaceAll(" ").trim();
    }
}
