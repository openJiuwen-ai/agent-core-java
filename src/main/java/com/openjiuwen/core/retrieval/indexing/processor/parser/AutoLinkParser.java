/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Routes URL-like documents to the first matching parser route.
 *
 * <p>Mirrors Python's {@code AutoLinkParser} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/auto_link_parser.py}.</p>
 */
public class AutoLinkParser extends Parser {

    public static final Pattern HTTP_URL_PATTERN = Pattern.compile("^https?://\\S+", Pattern.CASE_INSENSITIVE);

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoLinkParser.class);
    private static final Pattern WECHAT_MP_URL_PATTERN = Pattern.compile(
            "^https?://(?:mp\\.weixin\\.qq\\.com|.*?\\.weixin\\.qq\\.com)/s\\b.*",
            Pattern.CASE_INSENSITIVE);
    private static final String WECHAT_ARTICLE_PARSER_CLASS =
            "com.openjiuwen.core.retrieval.indexing.processor.parser.WeChatArticleParser";
    private static final String WEB_PAGE_PARSER_CLASS =
            "com.openjiuwen.core.retrieval.indexing.processor.parser.WebPageParser";

    private final List<Route> routes;

    public AutoLinkParser() {
        this(null);
    }

    public AutoLinkParser(List<Route> routes) {
        this.routes = routes == null ? defaultRoutes() : List.copyOf(routes);
    }

    /**
     * Mirrors Python's module-level {@code _default_routes()} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/auto_link_parser.py}.
     *
     * @return default URL routes in Python order
     */
    public static List<Route> defaultRoutes() {
        return List.of(
                Route.lazyPattern(WECHAT_MP_URL_PATTERN, WECHAT_ARTICLE_PARSER_CLASS),
                Route.lazyPattern(HTTP_URL_PATTERN, WEB_PAGE_PARSER_CLASS)
        );
    }

    /**
     * Mirrors Python's module-level {@code _match_doc(...)} in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/auto_link_parser.py}.
     *
     * @param pattern pattern to match from the beginning of the trimmed input
     * @param doc document source
     * @return {@code true} when the pattern matches
     */
    public static boolean matchDoc(Pattern pattern, String doc) {
        if (pattern == null || doc == null) {
            return false;
        }
        return pattern.matcher(doc.strip()).find();
    }

    @Override
    public boolean supports(String doc) {
        if (!matchDoc(HTTP_URL_PATTERN, doc)) {
            return false;
        }
        for (Route route : routes) {
            if (route.matches(doc)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public CompletableFuture<List<Document>> parse(
            String doc,
            String docId,
            BaseModelClient llmClient,
            Map<String, Object> options
    ) {
        Map<String, Object> safeOptions = options == null ? Map.of() : options;
        for (Route route : routes) {
            if (route.matches(doc)) {
                return route.parse(doc, docId, safeOptions);
            }
        }
        return CompletableFuture.completedFuture(List.of());
    }

    /**
     * URL route pair used by {@link AutoLinkParser}.
     *
     * <p>Mirrors Python's {@code (pattern_or_callable, parser)} route tuples in
     * {@code openjiuwen/core/retrieval/indexing/processor/parser/auto_link_parser.py}.</p>
     */
    public static final class Route {
        private final Predicate<String> matcher;
        private final Parser parser;
        private final String parserClassName;
        private volatile Parser cachedParser;

        public Route(Pattern pattern, Parser parser) {
            this(value -> AutoLinkParser.matchDoc(pattern, value), parser, null);
        }

        public Route(Predicate<String> matcher, Parser parser) {
            this(matcher, parser, null);
        }

        private Route(Predicate<String> matcher, Parser parser, String parserClassName) {
            this.matcher = matcher;
            this.parser = parser;
            this.parserClassName = parserClassName;
        }

        private static Route lazyPattern(Pattern pattern, String parserClassName) {
            return new Route(value -> AutoLinkParser.matchDoc(pattern, value), null, parserClassName);
        }

        public boolean matches(String value) {
            return matcher != null && matcher.test(value);
        }

        private CompletableFuture<List<Document>> parse(String doc, String docId, Map<String, Object> options) {
            Parser delegate;
            try {
                delegate = resolveParser();
            } catch (RuntimeException ex) {
                return CompletableFuture.failedFuture(ex);
            }
            LOGGER.debug("AutoLinkParser delegating to {}", delegate.getClass().getSimpleName());
            return delegate.parse(doc, docId, null, options);
        }

        private Parser resolveParser() {
            if (parser != null) {
                return parser;
            }
            Parser current = cachedParser;
            if (current != null) {
                return current;
            }
            synchronized (this) {
                if (cachedParser == null) {
                    cachedParser = instantiateParser(parserClassName);
                }
                return cachedParser;
            }
        }

        private static Parser instantiateParser(String className) {
            if (className == null || className.isBlank()) {
                throw new IllegalStateException("AutoLinkParser route has no parser");
            }
            try {
                Class<?> type = Class.forName(className);
                Object instance = type.getDeclaredConstructor().newInstance();
                if (instance instanceof Parser delegate) {
                    return delegate;
                }
                throw new IllegalStateException(className + " is not a Parser");
            } catch (ClassNotFoundException
                     | NoSuchMethodException
                     | InstantiationException
                     | IllegalAccessException
                     | InvocationTargetException ex) {
                throw new IllegalStateException("Failed to create parser route: " + className, ex);
            }
        }
    }
}
