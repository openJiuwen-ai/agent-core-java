/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.spi.store.query;

/**
 * Expression for custom / pass-through queries.
 */
public class CustomExpr extends QueryExpr {

    private final Object expr;

    public CustomExpr(Object expr) {
        this.expr = expr;
    }

    public Object getExpr() {
        return expr;
    }

    @Override
    public Object toExpr(String database) {
        return expr;
    }
}
