/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.query;

/**
 * Mirrors Python's {@code MatchExpr} in
 * {@code openjiuwen/core/foundation/store/query/base.py}.
 */
public final class MatchExpr extends QueryExpr {

    private final String field;
    private final String value;
    private final MatchMode matchMode;

    public MatchExpr(String field, String value, MatchMode matchMode) {
        this.field = field;
        this.value = value;
        this.matchMode = matchMode;
    }

    public MatchExpr(String field, String value) {
        this(field, value, MatchMode.EXACT);
    }

    public String getField() {
        return field;
    }

    public String getValue() {
        return value;
    }

    public MatchMode getMatchMode() {
        return matchMode;
    }

    @Override
    public Object toExpr(String database) {
        return QueryExpr.getLanguageDefinition(database).applyTextMatch(this);
    }
}
