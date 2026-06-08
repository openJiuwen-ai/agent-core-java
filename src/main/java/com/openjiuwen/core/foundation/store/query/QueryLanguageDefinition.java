/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.query;

import java.util.Objects;
import java.util.function.Function;

/**
 * Mirrors Python's {@code QueryLanguageDefinition} in
 * {@code openjiuwen/core/foundation/store/query/base.py}.
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

    public Object applyComparison(ComparisonExpr expr) {
        return comparison.apply(expr);
    }

    public Object applyRange(RangeExpr expr) {
        return range.apply(expr);
    }

    public Object applyArithmetic(ArithmeticExpr expr) {
        return arithmetic.apply(expr);
    }

    public Object applyNull(NullExpr expr) {
        return nullExpr.apply(expr);
    }

    public Object applyJsonFilter(JSONExpr expr) {
        return jsonFilter.apply(expr);
    }

    public Object applyArray(ArrayExpr expr) {
        return array.apply(expr);
    }

    public Object applyLogical(LogicalExpr expr) {
        return logical.apply(expr);
    }

    public Object applyTextMatch(MatchExpr expr) {
        return textMatch.apply(expr);
    }
}
