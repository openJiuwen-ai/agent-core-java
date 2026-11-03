/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.auth;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies custom auth headers and query parameters to HTTP requests.
 *
 * <p>Mirrors Python's {@code AuthHeaderAndQueryProvider} in
 * {@code openjiuwen/core/foundation/tool/auth/auth_callback.py}.
 */
public final class AuthHeaderAndQueryProvider {

    private final Map<String, String> headers;
    private final Map<String, String> queryParams;

    public AuthHeaderAndQueryProvider(Map<String, String> authHeaders, Map<String, String> authQueryParams) {
        this.headers = immutableStringMap(authHeaders);
        this.queryParams = immutableStringMap(authQueryParams);
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public URI mergeQueryParams(URI uri) {
        if (queryParams.isEmpty()) {
            return uri;
        }
        List<QueryPair> pairs = parseQuery(uri.getRawQuery());
        List<QueryPair> merged = new ArrayList<>();
        for (QueryPair pair : pairs) {
            if (!queryParams.containsKey(pair.key())) {
                merged.add(pair);
            }
        }
        queryParams.forEach((key, value) -> merged.add(new QueryPair(key, value)));
        String newQuery = joinQuery(merged);
        try {
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), newQuery, uri.getFragment());
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("Failed to merge authentication query parameters", error);
        }
    }

    public HttpRequest.Builder apply(HttpRequest.Builder builder, URI uri) {
        headers.forEach(builder::header);
        builder.uri(mergeQueryParams(uri));
        return builder;
    }

    private static Map<String, String> immutableStringMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static List<QueryPair> parseQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return List.of();
        }
        List<QueryPair> pairs = new ArrayList<>();
        for (String item : rawQuery.split("&")) {
            int equalsIndex = item.indexOf('=');
            if (equalsIndex < 0) {
                pairs.add(new QueryPair(item, ""));
            } else {
                pairs.add(new QueryPair(item.substring(0, equalsIndex), item.substring(equalsIndex + 1)));
            }
        }
        return pairs;
    }

    private static String joinQuery(List<QueryPair> pairs) {
        List<String> rendered = new ArrayList<>();
        for (QueryPair pair : pairs) {
            rendered.add(pair.key() + "=" + pair.value());
        }
        return String.join("&", rendered);
    }

    /**
     * Mirrors Python's query parameter item handling in
     * {@code openjiuwen/core/foundation/tool/auth/auth_callback.py}.
     */
    private record QueryPair(String key, String value) {
    }
}
