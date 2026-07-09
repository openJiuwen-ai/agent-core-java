/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

/**
 * Expression for array field operations.
 * 
 * @since 0.1.7
 */
public class ArrayExpr extends QueryExpr {
    private final String field;
    private final Integer index;
    private final String operator;
    private final Object value;

    /**
     * ArrayExpr.
     * 
     * @param field field
     * @param index index
     * @param operator operator
     * @param value value
     * @since 0.1.7
     */
    public ArrayExpr(String field, Integer index, String operator, Object value) {
        this.field = field;
        this.index = index;
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
     * May be {@code null} when not filtering by index.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Integer getIndex() {
        return index;
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
        return QueryLanguageRegistry.get(database).applyArray(this);
    }
}
