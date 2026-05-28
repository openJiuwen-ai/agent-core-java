/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.query;

import java.util.function.Function;

/**
 * Definition of a database-specific query language.
 * <p>
 * Mirrors Python's {@code QueryLanguageDefinition} class from
 * <code>foundation/store/query/base.py</code>.
 */
public class QueryLanguageDefinition {

    private final Function<QueryExpr, String> comparison;
    private final Function<QueryExpr, String> range;
    private final Function<QueryExpr, String> arithmetic;
    private final Function<QueryExpr, String> nullExpr;
    private final Function<QueryExpr, String> jsonFilter;
    private final Function<QueryExpr, String> array;
    private final Function<QueryExpr, String> logical;
    private final Function<QueryExpr, String> textMatch;

    public QueryLanguageDefinition(
        Function<QueryExpr, String> comparison,
        Function<QueryExpr, String> range,
        Function<QueryExpr, String> arithmetic,
        Function<QueryExpr, String> nullExpr,
        Function<QueryExpr, String> jsonFilter,
        Function<QueryExpr, String> array,
        Function<QueryExpr, String> logical,
        Function<QueryExpr, String> textMatch) {
        this.comparison = comparison;
        this.range = range;
        this.arithmetic = arithmetic;
        this.nullExpr = nullExpr;
        this.jsonFilter = jsonFilter;
        this.array = array;
        this.logical = logical;
        this.textMatch = textMatch;
    }

    public Function<QueryExpr, String> getComparison() { return comparison; }
    public Function<QueryExpr, String> getRange() { return range; }
    public Function<QueryExpr, String> getArithmetic() { return arithmetic; }
    public Function<QueryExpr, String> getNullExpr() { return nullExpr; }
    public Function<QueryExpr, String> getJsonFilter() { return jsonFilter; }
    public Function<QueryExpr, String> getArray() { return array; }
    public Function<QueryExpr, String> getLogical() { return logical; }
    public Function<QueryExpr, String> getTextMatch() { return textMatch; }
}