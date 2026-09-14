/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

import java.util.Collection;
import java.util.List;

/**
 * Compatibility facade for 0.1.12 SPI query expression factories.
 *
 * <p>Mirrors Python's top-level query factory helpers in
 * {@code openjiuwen/core/foundation/store/query/base.py}.</p>
 */
public final class QueryExpressions {

    private QueryExpressions() {
    }

    public static QueryExpr eq(String field, Object value) {
        return QueryExpr.wrap(com.openjiuwen.core.foundation.store.query.QueryExpressions.eq(field, value));
    }

    public static QueryExpr ne(String field, Object value) {
        return QueryExpr.wrap(com.openjiuwen.core.foundation.store.query.QueryExpressions.ne(field, value));
    }

    public static QueryExpr gt(String field, Number value) {
        return QueryExpr.wrap(com.openjiuwen.core.foundation.store.query.QueryExpressions.gt(field, value));
    }

    public static QueryExpr lt(String field, Number value) {
        return QueryExpr.wrap(com.openjiuwen.core.foundation.store.query.QueryExpressions.lt(field, value));
    }

    public static QueryExpr gte(String field, Number value) {
        return QueryExpr.wrap(com.openjiuwen.core.foundation.store.query.QueryExpressions.gte(field, value));
    }

    public static QueryExpr lte(String field, Number value) {
        return QueryExpr.wrap(com.openjiuwen.core.foundation.store.query.QueryExpressions.lte(field, value));
    }

    public static QueryExpr inList(String field, Collection<?> values) {
        return QueryExpr.wrap(com.openjiuwen.core.foundation.store.query.QueryExpressions.inList(field, values));
    }

    public static QueryExpr wildcardMatch(String field, String pattern, String operator) {
        return QueryExpr.wrap(com.openjiuwen.core.foundation.store.query.QueryExpressions.wildcardMatch(
                field, pattern, operator));
    }

    public static QueryExpr wildcardMatch(String field, String pattern) {
        return QueryExpr.wrap(com.openjiuwen.core.foundation.store.query.QueryExpressions.wildcardMatch(field, pattern));
    }

    public static QueryExpr isNull(String field) {
        return QueryExpr.wrap(com.openjiuwen.core.foundation.store.query.QueryExpressions.isNull(field));
    }

    public static QueryExpr isNotNull(String field) {
        return QueryExpr.wrap(com.openjiuwen.core.foundation.store.query.QueryExpressions.isNotNull(field));
    }

    public static QueryExpr jsonKey(String field, String key, String operator, Object value) {
        return QueryExpr.wrap(com.openjiuwen.core.foundation.store.query.QueryExpressions.jsonKey(
                field, key, operator, value));
    }

    public static QueryExpr arrayIndex(String field, int index, String operator, Object value) {
        return QueryExpr.wrap(com.openjiuwen.core.foundation.store.query.QueryExpressions.arrayIndex(
                field, index, operator, value));
    }

    public static QueryExpr filterUser(String user) {
        return QueryExpr.wrap(com.openjiuwen.core.foundation.store.query.QueryExpressions.filterUser(user));
    }

    public static QueryExpr filterUser(List<String> users) {
        return QueryExpr.wrap(com.openjiuwen.core.foundation.store.query.QueryExpressions.filterUser(users));
    }

    public static QueryExpr filterUser(List<String> users, String userIdField) {
        return QueryExpr.wrap(com.openjiuwen.core.foundation.store.query.QueryExpressions.filterUser(users, userIdField));
    }

    public static QueryExpr chainFilters(List<QueryExpr> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        QueryExpr result = filters.get(0);
        for (int index = 1; index < filters.size(); index++) {
            result = QueryExpr.wrap(result.and(filters.get(index)));
        }
        return result;
    }
}
