/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * URL parser router.
 */
public class AutoLinkParser extends Parser {

    public static final Pattern HTTP_URL_PATTERN = Pattern.compile("^https?://.+", Pattern.CASE_INSENSITIVE);

    private final List<Route> routes;

    public AutoLinkParser() {
        this(List.of(
                new Route(WeChatArticleParser::isWechatArticleUrl, new WeChatArticleParser()),
                new Route(url -> url != null && HTTP_URL_PATTERN.matcher(url.trim()).matches(), new WebPageParser())));
    }

    public AutoLinkParser(List<Route> routes) {
        this.routes = routes == null ? List.of() : List.copyOf(routes);
    }

    @Override
    public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options) {
        for (Route route : routes) {
            if (route.matches(doc)) {
                return route.parser().parse(doc, docId, llmClient, options);
            }
        }
        return List.of();
    }

    @Override
    protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options) {
        return null;
    }

    @Override
    public boolean supports(String doc) {
        for (Route route : routes) {
            if (route.matches(doc)) {
                return true;
            }
        }
        return false;
    }

    public record Route(Predicate<String> matcher, Parser parser) {
        boolean matches(String value) {
            return matcher != null && matcher.test(value);
        }
    }
}
