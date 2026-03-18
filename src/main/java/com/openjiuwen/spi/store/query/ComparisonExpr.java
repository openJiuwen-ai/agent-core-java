/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.spi.store.query;

/**
 * Expression for comparison operations (==, !=, &gt;, &lt;, &gt;=, &lt;=).
 */
public class ComparisonExpr extends QueryExpr {

    private final String field;
    private final String operator;
    private final Object value;

    public ComparisonExpr(String field, String operator, Object value) {
        this.field = field;
        this.operator = operator;
        this.value = value;
    }

    public String getField() {
        return field;
    }

    public String getOperator() {
        return operator;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public Object toExpr(String database) {
        return QueryLanguageRegistry.get(database).applyComparison(this);
    }
}
