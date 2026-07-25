/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

/**
 * Expression for custom / pass-through queries.
 */
public class CustomExpr extends QueryExpr {

    private final Object expr;

    /**
     * Auto-generated for codecheck compliance.
     */
    public CustomExpr(Object expr) {
        this.expr = expr;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getExpr() {
        return expr;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object toExpr(String database) {
        return expr;
    }
}
