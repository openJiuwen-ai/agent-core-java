/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

/**
 * JSON key filter expression.
 * <p>
 * Mirrors Python's {@code JSONExpr} in
 * {@code openjiuwen/core/foundation/store/query/base.py}.
 * </p>
 */
public final class JSONExpr extends QueryExpr {

    private final String field;
    private final String key;
    private final String operator;
    private final Object value;

    /**
     * Create a JSON key filter expression.
     *
     * @param field    field name
     * @param key      JSON key
     * @param operator comparison operator
     * @param value    comparison value
     */
    public JSONExpr(String field, String key, String operator, Object value) {
        this.field = field;
        this.key = key;
        this.operator = operator;
        this.value = value;
    }

    public String getField() {
        return field;
    }

    public String getKey() {
        return key;
    }

    public String getOperator() {
        return operator;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public Object toExpr(String database) {
        return QueryLanguageRegistry.get(database).applyJsonFilter(this);
    }
}
