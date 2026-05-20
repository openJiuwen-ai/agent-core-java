/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

/**
 * Expression for text match operations (prefix, suffix, infix, exact).
 */
public class MatchExpr extends QueryExpr {

    private final String field;
    private final String value;
    private final MatchMode matchMode;

    /**
     * Auto-generated for codecheck compliance.
     */
    public MatchExpr(String field, String value, MatchMode matchMode) {
        this.field = field;
        this.value = value;
        this.matchMode = matchMode;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public MatchExpr(String field, String value) {
        this(field, value, MatchMode.EXACT);
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
    public String getValue() {
        return value;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public MatchMode getMatchMode() {
        return matchMode;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object toExpr(String database) {
        return QueryLanguageRegistry.get(database).applyTextMatch(this);
    }
}
