/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */

package com.openjiuwen.spi.store.query;

/**
 * Expression for arithmetic operations on a field value followed by a comparison.
 * <p>
 * Example: {@code (field + 1) > 5}
 */
public class ArithmeticExpr extends QueryExpr {

    private final String field;
    private final String arithmeticOperator;
    private final Number arithmeticValue;
    private final String comparisonOperator;
    private final Number comparisonValue;

    public ArithmeticExpr(String field,
                          String arithmeticOperator, Number arithmeticValue,
                          String comparisonOperator, Number comparisonValue) {
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
