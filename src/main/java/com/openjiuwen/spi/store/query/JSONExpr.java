/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public JSONExpr(String field, String key, String operator, Object value) {
        this.field = field;
        this.key = key;
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
    public String getKey() {
        return key;
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
        return QueryLanguageRegistry.get(database).applyJsonFilter(this);
    }
}
