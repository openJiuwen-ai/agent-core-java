/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

import java.util.function.Function;

/**
 * Definition of a database-specific query language.
 * <p>
 * Each function converts a specific {@link QueryExpr} subtype into a
 * database-native expression (usually a String or filter object).
 * 
 * @since 0.1.7
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

    /**
     * QueryLanguageDefinition.
     * 
     * @param builder builder
     * @since 0.1.7
     */
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

    /**
     * applyComparison.
     * 
     * @param expr expr
     * @return the result
     * @since 0.1.7
     */
    public Object applyComparison(QueryExpr expr) {
        return comparison.apply(expr);
    }

    /**
     * applyRange.
     * 
     * @param expr expr
     * @return the result
     * @since 0.1.7
     */
    public Object applyRange(QueryExpr expr) {
        return range.apply(expr);
    }

    /**
     * applyArithmetic.
     * 
     * @param expr expr
     * @return the result
     * @since 0.1.7
     */
    public Object applyArithmetic(QueryExpr expr) {
        return arithmetic.apply(expr);
    }

    /**
     * applyNullCheck.
     * 
     * @param expr expr
     * @return the result
     * @since 0.1.7
     */
    public Object applyNullCheck(QueryExpr expr) {
        return nullCheck.apply(expr);
    }

    /**
     * applyJsonFilter.
     * 
     * @param expr expr
     * @return the result
     * @since 0.1.7
     */
    public Object applyJsonFilter(QueryExpr expr) {
        return jsonFilter.apply(expr);
    }

    /**
     * applyArray.
     * 
     * @param expr expr
     * @return the result
     * @since 0.1.7
     */
    public Object applyArray(QueryExpr expr) {
        return array.apply(expr);
    }

    /**
     * applyLogical.
     * 
     * @param expr expr
     * @return the result
     * @since 0.1.7
     */
    public Object applyLogical(QueryExpr expr) {
        return logical.apply(expr);
    }

    /**
     * applyTextMatch.
     * 
     * @param expr expr
     * @return the result
     * @since 0.1.7
     */
    public Object applyTextMatch(QueryExpr expr) {
        return textMatch.apply(expr);
    }

    /**
     * builder.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
     */
    public static class Builder {
        private Function<QueryExpr, Object> comparison;
        private Function<QueryExpr, Object> range;
        private Function<QueryExpr, Object> arithmetic;
        private Function<QueryExpr, Object> nullCheck;
        private Function<QueryExpr, Object> jsonFilter;
        private Function<QueryExpr, Object> array;
        private Function<QueryExpr, Object> logical;
        private Function<QueryExpr, Object> textMatch;

        /**
         * comparison.
         * 
         * @param comparison comparison
         * @return the result
         * @since 0.1.7
         */
        public Builder comparison(Function<QueryExpr, Object> comparison) {
            this.comparison = comparison;
            return this;
        }

        /**
         * range.
         * 
         * @param range range
         * @return the result
         * @since 0.1.7
         */
        public Builder range(Function<QueryExpr, Object> range) {
            this.range = range;
            return this;
        }

        /**
         * arithmetic.
         * 
         * @param arithmetic arithmetic
         * @return the result
         * @since 0.1.7
         */
        public Builder arithmetic(Function<QueryExpr, Object> arithmetic) {
            this.arithmetic = arithmetic;
            return this;
        }

        /**
         * nullCheck.
         * 
         * @param nullCheck nullCheck
         * @return the result
         * @since 0.1.7
         */
        public Builder nullCheck(Function<QueryExpr, Object> nullCheck) {
            this.nullCheck = nullCheck;
            return this;
        }

        /**
         * jsonFilter.
         * 
         * @param jsonFilter jsonFilter
         * @return the result
         * @since 0.1.7
         */
        public Builder jsonFilter(Function<QueryExpr, Object> jsonFilter) {
            this.jsonFilter = jsonFilter;
            return this;
        }

        /**
         * array.
         * 
         * @param array array
         * @return the result
         * @since 0.1.7
         */
        public Builder array(Function<QueryExpr, Object> array) {
            this.array = array;
            return this;
        }

        /**
         * logical.
         * 
         * @param logical logical
         * @return the result
         * @since 0.1.7
         */
        public Builder logical(Function<QueryExpr, Object> logical) {
            this.logical = logical;
            return this;
        }

        /**
         * textMatch.
         * 
         * @param textMatch textMatch
         * @return the result
         * @since 0.1.7
         */
        public Builder textMatch(Function<QueryExpr, Object> textMatch) {
            this.textMatch = textMatch;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public QueryLanguageDefinition build() {
            return new QueryLanguageDefinition(this);
        }
    }
}
