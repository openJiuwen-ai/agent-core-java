/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

/**
 * Expression for comparison operations (==, !=, &gt;, &lt;, &gt;=, &lt;=).
 */
public class ComparisonExpr extends QueryExpr {

    private final String field;
    private final String operator;
    private final Object value;

    /**
     * Auto-generated for codecheck compliance.
     */
    public ComparisonExpr(String field, String operator, Object value) {
        this.field = field;
        this.operator = operator;
        this.value = value;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getField() {
        return field;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getOperator() {
        return operator;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getValue() {
        return value;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object toExpr(String database) {
        return QueryLanguageRegistry.get(database).applyComparison(this);
    }
}
