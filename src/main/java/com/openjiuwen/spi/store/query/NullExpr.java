/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.spi.store.query;

/**
 * Expression for null-value checks (IS NULL / IS NOT NULL).
 */
public class NullExpr extends QueryExpr {

    private final String field;
    private final boolean isNull;

    public NullExpr(String field, boolean isNull) {
        this.field = field;
        this.isNull = isNull;
    }

    public String getField() {
        return field;
    }

    public boolean isNull() {
        return isNull;
    }

    @Override
    public Object toExpr(String database) {
        return QueryLanguageRegistry.get(database).applyNullCheck(this);
    }
}
