/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.spi.store.query;

/**
 * Expression for JSON field operations.
 */
public class JSONExpr extends QueryExpr {

    private final String field;
    private final String key;
    private final String operator;
    private final Object value;

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
