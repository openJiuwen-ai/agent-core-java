/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.spi.store.query;

import java.util.function.Function;

/**
 * Definition of a database-specific query language.
 * <p>
 * Each function converts a specific {@link QueryExpr} subtype into a
 * database-native expression (usually a String or filter object).
 */
public class QueryLanguageDefinition {

    private final Function<QueryExpr, Object> comparison;
    private final Function<QueryExpr, Object> range;
    private final Function<QueryExpr, Object> arithmetic;
    private final Function<QueryExpr, Object> nullCheck;
    private final Function<QueryExpr, Object> jsonFilter;
    private final Function<QueryExpr, Object> array;
    private final Function<QueryExpr, Object> logical;
    private final Function<QueryExpr, Object> textMatch;

    private QueryLanguageDefinition(Builder builder) {
        this.comparison = builder.comparison;
        this.range = builder.range;
        this.arithmetic = builder.arithmetic;
        this.nullCheck = builder.nullCheck;
        this.jsonFilter = builder.jsonFilter;
        this.array = builder.array;
        this.logical = builder.logical;
        this.textMatch = builder.textMatch;
    }

    public Object applyComparison(QueryExpr expr) {
        return comparison.apply(expr);
    }

    public Object applyRange(QueryExpr expr) {
        return range.apply(expr);
    }

    public Object applyArithmetic(QueryExpr expr) {
        return arithmetic.apply(expr);
    }

    public Object applyNullCheck(QueryExpr expr) {
        return nullCheck.apply(expr);
    }

    public Object applyJsonFilter(QueryExpr expr) {
        return jsonFilter.apply(expr);
    }

    public Object applyArray(QueryExpr expr) {
        return array.apply(expr);
    }

    public Object applyLogical(QueryExpr expr) {
        return logical.apply(expr);
    }

    public Object applyTextMatch(QueryExpr expr) {
        return textMatch.apply(expr);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Function<QueryExpr, Object> comparison;
        private Function<QueryExpr, Object> range;
        private Function<QueryExpr, Object> arithmetic;
        private Function<QueryExpr, Object> nullCheck;
        private Function<QueryExpr, Object> jsonFilter;
        private Function<QueryExpr, Object> array;
        private Function<QueryExpr, Object> logical;
        private Function<QueryExpr, Object> textMatch;

        public Builder comparison(Function<QueryExpr, Object> comparison) {
            this.comparison = comparison;
            return this;
        }

        public Builder range(Function<QueryExpr, Object> range) {
            this.range = range;
            return this;
        }

        public Builder arithmetic(Function<QueryExpr, Object> arithmetic) {
            this.arithmetic = arithmetic;
            return this;
        }

        public Builder nullCheck(Function<QueryExpr, Object> nullCheck) {
            this.nullCheck = nullCheck;
            return this;
        }

        public Builder jsonFilter(Function<QueryExpr, Object> jsonFilter) {
            this.jsonFilter = jsonFilter;
            return this;
        }

        public Builder array(Function<QueryExpr, Object> array) {
            this.array = array;
            return this;
        }

        public Builder logical(Function<QueryExpr, Object> logical) {
            this.logical = logical;
            return this;
        }

        public Builder textMatch(Function<QueryExpr, Object> textMatch) {
            this.textMatch = textMatch;
            return this;
        }

        public QueryLanguageDefinition build() {
            return new QueryLanguageDefinition(this);
        }
    }
}
