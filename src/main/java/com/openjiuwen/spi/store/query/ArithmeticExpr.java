/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

/**
 * Expression for arithmetic operations on a field value followed by a comparison.
 * <p>
 * Example: {@code (field + 1) > 5}
 * 
 * @since 0.1.7
 */
public class ArithmeticExpr extends QueryExpr {
    private final String field;
    private final String arithmeticOperator;
    private final Number arithmeticValue;
    private final String comparisonOperator;
    private final Number comparisonValue;

    /**
     * ArithmeticExpr.
     * 
     * @param field field
     * @param arithmeticOperator arithmeticOperator
     * @param arithmeticValue arithmeticValue
     * @param comparisonOperator comparisonOperator
     * @param comparisonValue comparisonValue
     * @since 0.1.7
     */
    public ArithmeticExpr(String field, String arithmeticOperator, Number arithmeticValue, String comparisonOperator,
            Number comparisonValue) {
        this.field = field;
        this.arithmeticOperator = arithmeticOperator;
        this.arithmeticValue = arithmeticValue;
        this.comparisonOperator = comparisonOperator;
        this.comparisonValue = comparisonValue;
    }

    /**
     * getField.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getField() {
        return field;
    }

    /**
     * getArithmeticOperator.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getArithmeticOperator() {
        return arithmeticOperator;
    }

    /**
     * getArithmeticValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Number getArithmeticValue() {
        return arithmeticValue;
    }

    /**
     * getComparisonOperator.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getComparisonOperator() {
        return comparisonOperator;
    }

    /**
     * getComparisonValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Number getComparisonValue() {
        return comparisonValue;
    }

    /**
     * toExpr.
     * 
     * @param database database
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object toExpr(String database) {
        return QueryLanguageRegistry.get(database).applyArithmetic(this);
    }
}
