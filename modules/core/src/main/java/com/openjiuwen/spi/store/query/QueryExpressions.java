/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

import java.util.Collection;
import java.util.List;

/**
 * Static factory methods for creating query filter expressions.
 * <p>
 * Mirrors the top-level factory functions from Python's {@code store.query} module
 * ({@code eq}, {@code ne}, {@code gt}, {@code lt}, etc.).
 * 
 * @since 0.1.7
 */
public final class QueryExpressions {
    /**
     * QueryExpressions.
     * 
     * @since 0.1.7
     */
    private QueryExpressions() {
        // static utility
    }

    /**
     * Create an equality filter: {@code field == value}.
     * 
     * @param field field
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    public static ComparisonExpr eq(String field, Object value) {
        return new ComparisonExpr(field, "==", value);
    }

    /**
     * Create a not-equal filter: {@code field != value}.
     * 
     * @param field field
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    public static ComparisonExpr ne(String field, Object value) {
        return new ComparisonExpr(field, "!=", value);
    }

    /**
     * Create a greater-than filter: {@code field > value}.
     * 
     * @param field field
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    public static ComparisonExpr gt(String field, Number value) {
        return new ComparisonExpr(field, ">", value);
    }

    /**
     * Create a less-than filter: {@code field < value}.
     * 
     * @param field field
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    public static ComparisonExpr lt(String field, Number value) {
        return new ComparisonExpr(field, "<", value);
    }

    /**
     * Create a greater-than-or-equal filter: {@code field >= value}.
     * 
     * @param field field
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    public static ComparisonExpr gte(String field, Number value) {
        return new ComparisonExpr(field, ">=", value);
    }

    /**
     * Create a less-than-or-equal filter: {@code field <= value}.
     * 
     * @param field field
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    public static ComparisonExpr lte(String field, Number value) {
        return new ComparisonExpr(field, "<=", value);
    }

    /**
     * Create an IN filter for a collection of values.
     * <p>
     * If the collection has exactly one element, returns a simple equality comparison.
     * 
     * @param field field
     * @param values values
     * @return the result
     * @since 0.1.7
     */
    public static QueryExpr inList(String field, Collection<?> values) {
        if (values.size() == 1) {
            return new ComparisonExpr(field, "==", values.iterator().next());
        }
        return new RangeExpr(field, "in", values);
    }

    /**
     * Create a wildcard matching filter.
     * 
     * @param field field name
     * @param pattern pattern with {@code *} wildcards
     * @param operator operator name (defaults to "wildcard")
     * @return the result
     * @since 0.1.7
     */
    public static RangeExpr wildcardMatch(String field, String pattern, String operator) {
        return new RangeExpr(field, operator, pattern);
    }

    /**
     * Create a wildcard matching filter with default "wildcard" operator.
     * 
     * @param field field
     * @param pattern pattern
     * @return the result
     * @since 0.1.7
     */
    public static RangeExpr wildcardMatch(String field, String pattern) {
        return wildcardMatch(field, pattern, "wildcard");
    }

    /**
     * Create an IS NULL filter.
     * 
     * @param field field
     * @return the result
     * @since 0.1.7
     */
    public static NullExpr isNull(String field) {
        return new NullExpr(field, true);
    }

    /**
     * Create an IS NOT NULL filter.
     * 
     * @param field field
     * @return the result
     * @since 0.1.7
     */
    public static NullExpr isNotNull(String field) {
        return new NullExpr(field, false);
    }

    /**
     * Create a JSON key filter.
     * 
     * @param field field
     * @param key key
     * @param operator operator
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    public static JSONExpr jsonKey(String field, String key, String operator, Object value) {
        return new JSONExpr(field, key, operator, value);
    }

    /**
     * Create an array index filter.
     * 
     * @param field field
     * @param index index
     * @param operator operator
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    public static ArrayExpr arrayIndex(String field, int index, String operator, Object value) {
        return new ArrayExpr(field, index, operator, value);
    }

    /**
     * Create a user filter.
     * 
     * @param users one or more user IDs
     * @param userIdField the field name to filter on
     * @return the result
     * @since 0.1.7
     */
    public static QueryExpr filterUser(List<String> users, String userIdField) {
        return inList(userIdField, users);
    }

    /**
     * Create a user filter on default field {@code "user_id"}.
     * 
     * @param users users
     * @return the result
     * @since 0.1.7
     */
    public static QueryExpr filterUser(List<String> users) {
        return filterUser(users, "user_id");
    }

    /**
     * Create a user filter for a single user ID.
     * 
     * @param user user
     * @return the result
     * @since 0.1.7
     */
    public static QueryExpr filterUser(String user) {
        return filterUser(List.of(user));
    }

    /**
     * Chain multiple filters with AND.
     * 
     * @param filters list of filters to combine
     * @return combined expression, or {@code null} if the list is empty
     * @since 0.1.7
     */
    public static QueryExpr chainFilters(List<QueryExpr> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        QueryExpr result = filters.get(0);
        for (int i = 1; i < filters.size(); i++) {
            result = result.and(filters.get(i));
        }
        return result;
    }
}
