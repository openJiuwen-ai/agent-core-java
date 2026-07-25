/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

import java.util.Objects;
import java.util.function.Function;

/**
 * Definition of a database-specific query language.
 * <p>
 * Each function converts a specific {@link QueryExpr} subtype into a
 * database-native expression (usually a String or filter object).
 * Mirrors Python's {@code QueryLanguageDefinition} in
 * {@code openjiuwen/core/foundation/store/query/base.py}.
 * </p>
 */
public final class QueryLanguageDefinition {

    private final Function<ComparisonExpr, Object> comparison;
    private final Function<RangeExpr, Object> range;
    private final Function<ArithmeticExpr, Object> arithmetic;
    private final Function<NullExpr, Object> nullExpr;
    private final Function<JSONExpr, Object> jsonFilter;
    private final Function<ArrayExpr, Object> array;
    private final Function<LogicalExpr, Object> logical;
    private final Function<MatchExpr, Object> textMatch;

    /**
     * Create a query language definition with typed handler functions.
     *
     * @param comparison  handler for comparison expressions
     * @param range       handler for range expressions
     * @param arithmetic  handler for arithmetic expressions
     * @param nullExpr    handler for null-check expressions
     * @param jsonFilter  handler for JSON filter expressions
     * @param array       handler for array index expressions
     * @param logical     handler for logical combination expressions
     * @param textMatch   handler for text match expressions
     */
    public QueryLanguageDefinition(
            Function<ComparisonExpr, Object> comparison,
            Function<RangeExpr, Object> range,
            Function<ArithmeticExpr, Object> arithmetic,
            Function<NullExpr, Object> nullExpr,
            Function<JSONExpr, Object> jsonFilter,
            Function<ArrayExpr, Object> array,
            Function<LogicalExpr, Object> logical,
            Function<MatchExpr, Object> textMatch) {
        this.comparison = Objects.requireNonNull(comparison, "comparison");
        this.range = Objects.requireNonNull(range, "range");
        this.arithmetic = Objects.requireNonNull(arithmetic, "arithmetic");
        this.nullExpr = Objects.requireNonNull(nullExpr, "nullExpr");
        this.jsonFilter = Objects.requireNonNull(jsonFilter, "jsonFilter");
        this.array = Objects.requireNonNull(array, "array");
        this.logical = Objects.requireNonNull(logical, "logical");
        this.textMatch = Objects.requireNonNull(textMatch, "textMatch");
    }

    /**
     * Apply comparison handler.
     *
     * @param expr comparison expression
     * @return database-specific expression
     */
    public Object applyComparison(ComparisonExpr expr) {
        return comparison.apply(expr);
    }

    /**
     * Apply comparison handler (QueryExpr overload, casts internally).
     *
     * @param expr query expression (must be ComparisonExpr)
     * @return database-specific expression
     */
    public Object applyComparison(QueryExpr expr) {
        return comparison.apply((ComparisonExpr) expr);
    }

    /**
     * Apply range handler.
     *
     * @param expr range expression
     * @return database-specific expression
     */
    public Object applyRange(RangeExpr expr) {
        return range.apply(expr);
    }

    /**
     * Apply range handler (QueryExpr overload, casts internally).
     *
     * @param expr query expression (must be RangeExpr)
     * @return database-specific expression
     */
    public Object applyRange(QueryExpr expr) {
        return range.apply((RangeExpr) expr);
    }

    /**
     * Apply arithmetic handler.
     *
     * @param expr arithmetic expression
     * @return database-specific expression
     */
    public Object applyArithmetic(ArithmeticExpr expr) {
        return arithmetic.apply(expr);
    }

    /**
     * Apply arithmetic handler (QueryExpr overload, casts internally).
     *
     * @param expr query expression (must be ArithmeticExpr)
     * @return database-specific expression
     */
    public Object applyArithmetic(QueryExpr expr) {
        return arithmetic.apply((ArithmeticExpr) expr);
    }

    /**
     * Apply null-check handler.
     *
     * @param expr null expression
     * @return database-specific expression
     */
    public Object applyNull(NullExpr expr) {
        return nullExpr.apply(expr);
    }

    /**
     * Apply null-check handler (QueryExpr overload, casts internally).
     *
     * @param expr query expression (must be NullExpr)
     * @return database-specific expression
     */
    public Object applyNullCheck(QueryExpr expr) {
        return nullExpr.apply((NullExpr) expr);
    }

    /**
     * Apply JSON filter handler.
     *
     * @param expr JSON expression
     * @return database-specific expression
     */
    public Object applyJsonFilter(JSONExpr expr) {
        return jsonFilter.apply(expr);
    }

    /**
     * Apply JSON filter handler (QueryExpr overload, casts internally).
     *
     * @param expr query expression (must be JSONExpr)
     * @return database-specific expression
     */
    public Object applyJsonFilter(QueryExpr expr) {
        return jsonFilter.apply((JSONExpr) expr);
    }

    /**
     * Apply array handler.
     *
     * @param expr array expression
     * @return database-specific expression
     */
    public Object applyArray(ArrayExpr expr) {
        return array.apply(expr);
    }

    /**
     * Apply array handler (QueryExpr overload, casts internally).
     *
     * @param expr query expression (must be ArrayExpr)
     * @return database-specific expression
     */
    public Object applyArray(QueryExpr expr) {
        return array.apply((ArrayExpr) expr);
    }

    /**
     * Apply logical handler.
     *
     * @param expr logical expression
     * @return database-specific expression
     */
    public Object applyLogical(LogicalExpr expr) {
        return logical.apply(expr);
    }

    /**
     * Apply logical handler (QueryExpr overload, casts internally).
     *
     * @param expr query expression (must be LogicalExpr)
     * @return database-specific expression
     */
    public Object applyLogical(QueryExpr expr) {
        return logical.apply((LogicalExpr) expr);
    }

    /**
     * Apply text match handler.
     *
     * @param expr match expression
     * @return database-specific expression
     */
    public Object applyTextMatch(MatchExpr expr) {
        return textMatch.apply(expr);
    }

    /**
     * Apply text match handler (QueryExpr overload, casts internally).
     *
     * @param expr query expression (must be MatchExpr)
     * @return database-specific expression
     */
    public Object applyTextMatch(QueryExpr expr) {
        return textMatch.apply((MatchExpr) expr);
    }
}
