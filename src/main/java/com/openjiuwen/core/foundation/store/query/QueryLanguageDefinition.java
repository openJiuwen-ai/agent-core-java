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

    /**
     * Creates a new builder for {@link QueryLanguageDefinition}.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link QueryLanguageDefinition}.
     */
    public static final class Builder {

        private Function<ComparisonExpr, Object> comparison;
        private Function<RangeExpr, Object> range;
        private Function<ArithmeticExpr, Object> arithmetic;
        private Function<NullExpr, Object> nullExpr;
        private Function<JSONExpr, Object> jsonFilter;
        private Function<ArrayExpr, Object> array;
        private Function<LogicalExpr, Object> logical;
        private Function<MatchExpr, Object> textMatch;

        private Builder() {
        }

        public Builder comparison(Function<ComparisonExpr, Object> comparison) {
            this.comparison = comparison;
            return this;
        }

        public Builder range(Function<RangeExpr, Object> range) {
            this.range = range;
            return this;
        }

        public Builder arithmetic(Function<ArithmeticExpr, Object> arithmetic) {
            this.arithmetic = arithmetic;
            return this;
        }

        public Builder nullCheck(Function<NullExpr, Object> nullExpr) {
            this.nullExpr = nullExpr;
            return this;
        }

        public Builder jsonFilter(Function<JSONExpr, Object> jsonFilter) {
            this.jsonFilter = jsonFilter;
            return this;
        }

        public Builder array(Function<ArrayExpr, Object> array) {
            this.array = array;
            return this;
        }

        public Builder logical(Function<LogicalExpr, Object> logical) {
            this.logical = logical;
            return this;
        }

        public Builder textMatch(Function<MatchExpr, Object> textMatch) {
            this.textMatch = textMatch;
            return this;
        }

        public QueryLanguageDefinition build() {
            return new QueryLanguageDefinition(
                    comparison, range, arithmetic, nullExpr,
                    jsonFilter, array, logical, textMatch);
        }
    }
}
