/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;

import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * WeChat article parser.
 * 
 * @since 0.1.7
 */
public class WeChatArticleParser extends WebPageParser {
    private static final Pattern WECHAT_URL_PATTERN =
        Pattern.compile("^https?://mp\\.weixin\\.qq\\.com/s/.+", Pattern.CASE_INSENSITIVE);

    /**
     * Pattern.compile.
     * 
     * @since 0.1.7
     */
    private static final Pattern CONTENT_PATTERN =
        Pattern.compile("<div[^>]+id=[\"']js_content[\"'][^>]*>(.*?)</div>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * WeChatArticleParser.
     * 
     * @since 0.1.7
     */
    public WeChatArticleParser() {
        super();
    }

    /**
     * WeChatArticleParser.
     * 
     * @param httpClient httpClient
     * @since 0.1.7
     */
    public WeChatArticleParser(HttpClient httpClient) {
        super(httpClient);
    }

    /**
     * isWechatArticleUrl.
     * 
     * @param url url
     * @return the result
     * @since 0.1.7
     */
    public static boolean isWechatArticleUrl(String url) {
        return url != null && WECHAT_URL_PATTERN.matcher(url.trim()).matches();
    }

    /**
     * parse.
     * 
     * @param doc doc
     * @param docId docId
     * @param llmClient llmClient
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options) {
        if (!isWechatArticleUrl(doc)) {
            throw RetrievalExceptions.validation("Not a WeChat article URL");
        }
        String html = fetchHtml(doc);
        String title = extractFirst(html, TITLE_META_PATTERN, extractFirst(html, TITLE_PATTERN, ""));
        String text = extractReadableText(html, CONTENT_PATTERN);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source_url", doc);
        metadata.put("title", title);
        metadata.put("source_type", "wechat_article");
        return List.of(new Document(docId, text, metadata));
    }

    /**
     * supports.
     * 
     * @param doc doc
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean supports(String doc) {
        return isWechatArticleUrl(doc);
    }
}
