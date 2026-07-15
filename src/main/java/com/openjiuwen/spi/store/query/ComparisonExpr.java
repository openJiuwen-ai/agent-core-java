/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

/**
 * Expression for comparison operations (==, !=, &gt;, &lt;, &gt;=, &lt;=).
 * 
 * @since 0.1.7
 */
public class ComparisonExpr extends QueryExpr {
    private final String field;
    private final String operator;
    private final Object value;

    /**
     * ComparisonExpr.
     * 
     * @param field field
     * @param operator operator
     * @param value value
     * @since 0.1.7
     */
    public ComparisonExpr(String field, String operator, Object value) {
        this.field = field;
        this.operator = operator;
        this.value = value;
    }

    /**
     * getField.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getField() {
        return field;
    }

    /**
     * getOperator.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getOperator() {
        return operator;
    }

    /**
     * getValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getValue() {
        return value;
    }

    /**
     * toExpr.
     * 
     * @param database database
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object toExpr(String database) {
        return QueryLanguageRegistry.get(database).applyComparison(this);
    }
}
