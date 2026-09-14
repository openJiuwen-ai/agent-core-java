/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

/**
 * Arithmetic filter expression (field OP value OP value).
 * <p>
 * Mirrors Python's {@code ArithmeticExpr} in
 * {@code openjiuwen/core/foundation/store/query/base.py}.
 * </p>
 */
public final class ArithmeticExpr extends QueryExpr {

    private final String field;
    private final String arithmeticOperator;
    private final Number arithmeticValue;
    private final String comparisonOperator;
    private final Number comparisonValue;

    /**
     * Create an arithmetic filter expression.
     *
     * @param field             field name
     * @param arithmeticOperator arithmetic operator (e.g. "+", "-")
     * @param arithmeticValue   arithmetic operand
     * @param comparisonOperator comparison operator (e.g. ">", "<=")
     * @param comparisonValue   comparison value
     */
    public ArithmeticExpr(
            String field,
            String arithmeticOperator,
            Number arithmeticValue,
            String comparisonOperator,
            Number comparisonValue) {
        this.field = field;
        this.arithmeticOperator = arithmeticOperator;
        this.arithmeticValue = arithmeticValue;
        this.comparisonOperator = comparisonOperator;
        this.comparisonValue = comparisonValue;
    }

    public String getField() {
        return field;
    }

    public String getArithmeticOperator() {
        return arithmeticOperator;
    }

    public Number getArithmeticValue() {
        return arithmeticValue;
    }

    public String getComparisonOperator() {
        return comparisonOperator;
    }

    public Number getComparisonValue() {
        return comparisonValue;
    }

    @Override
    public Object toExpr(String database) {
        return QueryLanguageRegistry.get(database).applyArithmetic(this);
    }
}
