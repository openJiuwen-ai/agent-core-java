/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * URL parser router.
 * 
 * @since 0.1.7
 */
public class AutoLinkParser extends Parser {
    /**
     * HTTP_URL_PATTERN.
     * 
     * @since 0.1.7
     */
    public static final Pattern HTTP_URL_PATTERN = Pattern.compile("^https?://.+", Pattern.CASE_INSENSITIVE);

    private final List<Route> routes;

    /**
     * AutoLinkParser.
     * 
     * @since 0.1.7
     */
    public AutoLinkParser() {
        this(List.of(new Route(WeChatArticleParser::isWechatArticleUrl, new WeChatArticleParser()),
                new Route(url -> url != null && HTTP_URL_PATTERN.matcher(url.trim()).matches(), new WebPageParser())));
    }

    /**
     * AutoLinkParser.
     * 
     * @param routes routes
     * @since 0.1.7
     */
    public AutoLinkParser(List<Route> routes) {
        this.routes = routes == null ? List.of() : List.copyOf(routes);
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
        for (Route route : routes) {
            if (route.matches(doc)) {
                return route.parser().parse(doc, docId, llmClient, options);
            }
        }
        return List.of();
    }

    /**
     * parseContent.
     * 
     * @param doc doc
     * @param llmClient llmClient
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options) {
        return null;
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
        for (Route route : routes) {
            if (route.matches(doc)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Route.
     * 
     * @since 0.1.7
     */
    public record Route(Predicate<String> matcher, Parser parser) {
        boolean matches(String value) {
            return matcher != null && matcher.test(value);
        }
    }
}
