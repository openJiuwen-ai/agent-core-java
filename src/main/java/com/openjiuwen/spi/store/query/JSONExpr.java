/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

/**
 * Expression for JSON field operations.
 * 
 * @since 0.1.7
 */
public class JSONExpr extends QueryExpr {
    private final String field;
    private final String key;
    private final String operator;
    private final Object value;

    /**
     * JSONExpr.
     * 
     * @param field field
     * @param key key
     * @param operator operator
     * @param value value
     * @since 0.1.7
     */
    public JSONExpr(String field, String key, String operator, Object value) {
        this.field = field;
        this.key = key;
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
     * getKey.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getKey() {
        return key;
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
        return QueryLanguageRegistry.get(database).applyJsonFilter(this);
    }
}
