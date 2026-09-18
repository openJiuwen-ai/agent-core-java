/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.query;

import java.util.Collection;
import java.util.List;

/**
 * Mirrors Python's top-level query factory helpers in
 * {@code openjiuwen/core/foundation/store/query/base.py}.
 */
public final class QueryExpressions {

    private QueryExpressions() {
    }

    public static ComparisonExpr eq(String field, Object value) {
        return new ComparisonExpr(field, "==", value);
    }

    public static ComparisonExpr ne(String field, Object value) {
        return new ComparisonExpr(field, "!=", value);
    }

    public static ComparisonExpr gt(String field, Number value) {
        return new ComparisonExpr(field, ">", value);
    }

    public static ComparisonExpr lt(String field, Number value) {
        return new ComparisonExpr(field, "<", value);
    }

    public static ComparisonExpr gte(String field, Number value) {
        return new ComparisonExpr(field, ">=", value);
    }

    public static ComparisonExpr lte(String field, Number value) {
        return new ComparisonExpr(field, "<=", value);
    }

    public static QueryExpr inList(String field, Collection<?> values) {
        if (values.size() == 1) {
            return new ComparisonExpr(field, "==", values.iterator().next());
        }
        return new RangeExpr(field, "in", values);
    }

    public static RangeExpr wildcardMatch(String field, String pattern, String operator) {
        return new RangeExpr(field, operator, pattern);
    }

    public static RangeExpr wildcardMatch(String field, String pattern) {
        return wildcardMatch(field, pattern, "wildcard");
    }

    public static NullExpr isNull(String field) {
        return new NullExpr(field, true);
    }

    public static NullExpr isNotNull(String field) {
        return new NullExpr(field, false);
    }

    public static JSONExpr jsonKey(String field, String key, String operator, Object value) {
        return new JSONExpr(field, key, operator, value);
    }

    public static ArrayExpr arrayIndex(String field, int index, String operator, Object value) {
        return new ArrayExpr(field, index, operator, value);
    }

    public static QueryExpr filterUser(String user) {
        return filterUser(List.of(user), "user_id");
    }

    public static QueryExpr filterUser(List<String> users) {
        return filterUser(users, "user_id");
    }

    public static QueryExpr filterUser(List<String> users, String userIdField) {
        return inList(userIdField, users);
    }

    public static QueryExpr chainFilters(List<QueryExpr> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        QueryExpr finalExpr = filters.remove(0);
        for (QueryExpr expr : filters) {
            finalExpr = finalExpr.and(expr);
        }
        return finalExpr;
    }
}
