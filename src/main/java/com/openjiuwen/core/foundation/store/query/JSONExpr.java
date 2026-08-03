/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.query;

/**
 * Mirrors Python's {@code JSONExpr} in
 * {@code openjiuwen/core/foundation/store/query/base.py}.
 */
public final class JSONExpr extends QueryExpr {

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
        return QueryExpr.getLanguageDefinition(database).applyJsonFilter(this);
    }
}
