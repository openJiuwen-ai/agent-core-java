/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.query;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.spi.store.query.ArithmeticExpr;
import com.openjiuwen.spi.store.query.ArrayExpr;
import com.openjiuwen.spi.store.query.ComparisonExpr;
import com.openjiuwen.spi.store.query.JSONExpr;
import com.openjiuwen.spi.store.query.LogicalExpr;
import com.openjiuwen.spi.store.query.MatchExpr;
import com.openjiuwen.spi.store.query.MatchMode;
import com.openjiuwen.spi.store.query.NullExpr;
import com.openjiuwen.spi.store.query.QueryLanguageDefinition;
import com.openjiuwen.spi.store.query.RangeExpr;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Query expression support for Milvus.
 * <p>
 * Returns Milvus filter expression strings.
 */
public final class MilvusQueryDialect {

    private MilvusQueryDialect() {
    }

    public static QueryLanguageDefinition definition() {
        return QueryLanguageDefinition.builder()
                .comparison(expr -> comparisonFilter((ComparisonExpr) expr))
                .range(expr -> rangeFilter((RangeExpr) expr))
                .arithmetic(expr -> arithmeticFilter((ArithmeticExpr) expr))
                .nullCheck(expr -> nullFilter((NullExpr) expr))
                .jsonFilter(expr -> jsonFilter((JSONExpr) expr))
                .array(expr -> arrayFilter((ArrayExpr) expr))
                .logical(expr -> logicalFilter((LogicalExpr) expr))
                .textMatch(expr -> textMatchFilter((MatchExpr) expr))
                .build();
    }

    static String comparisonFilter(ComparisonExpr self) {
        if (self.getValue() instanceof String) {
            return self.getField() + " " + self.getOperator() + " " + sanitize(self.getValue());
        }
        return self.getField() + " " + self.getOperator() + " " + self.getValue();
    }

    static String rangeFilter(RangeExpr self) {
        String op = self.getOperator().toLowerCase();
        switch (op) {
            case "in": {
                Object val = self.getValue();
                if (val instanceof Collection<?>) {
                    Collection<?> coll = (Collection<?>) val;
                    boolean allStrings = coll.stream().allMatch(v -> v instanceof String);
                    String valuesStr;
                    if (allStrings) {
                        valuesStr = coll.stream()
                                .map(v -> sanitize(v))
                                .collect(Collectors.joining(","));
                    } else {
                        valuesStr = coll.stream()
                                .map(String::valueOf)
                                .collect(Collectors.joining(","));
                    }
                    return self.getField() + " in [" + valuesStr + "]";
                }
                raiseQueryError("in operator requires a collection value");
                break;
            }
            case "like": {
                Object val = self.getValue();
                if (val instanceof String) {
                    String strVal = (String) val;
                    if (!strVal.contains("%")) {
                        raiseQueryError("Milvus's like operator uses % for wildcard matching");
                    }
                    return self.getField() + " like " + sanitize(strVal);
                }
                raiseQueryError("like operator requires a string value");
                break;
            }
            default:
                raiseQueryError("Unsupported range operator: " + self.getOperator());
        }
        return null; // unreachable
    }

    static String arithmeticFilter(ArithmeticExpr self) {
        return self.getField() + " " + self.getArithmeticOperator() + " " + self.getArithmeticValue()
                + self.getComparisonOperator() + " " + self.getComparisonValue();
    }

    static String nullFilter(NullExpr self) {
        if (self.isNull()) {
            return self.getField() + " is null";
        }
        return self.getField() + " is not null";
    }

    static String jsonFilter(JSONExpr self) {
        if (self.getValue() instanceof String) {
            return self.getField() + "[" + sanitize(self.getKey()) + "] "
                    + self.getOperator() + " " + sanitize(self.getValue());
        }
        return self.getField() + "[" + sanitize(self.getKey()) + "] "
                + self.getOperator() + " " + self.getValue();
    }

    static String arrayFilter(ArrayExpr self) {
        if (self.getIndex() != null) {
            if (self.getValue() instanceof String) {
                return self.getField() + "[" + self.getIndex() + "] "
                        + self.getOperator() + " " + sanitize(self.getValue());
            } else {
                return self.getField() + "[" + self.getIndex() + "] "
                        + self.getOperator() + " " + self.getValue();
            }
        } else {
            if (self.getValue() instanceof String) {
                return self.getField() + " " + self.getOperator() + " " + sanitize(self.getValue());
            } else {
                return self.getField() + " " + self.getOperator() + " " + self.getValue();
            }
        }
    }

    static String logicalFilter(LogicalExpr self) {
        String op = self.getOperator().toLowerCase();
        switch (op) {
            case "not":
                if (self.getRight() != null) {
                    raiseQueryError("not operator should not have a right operand");
                }
                return "not (" + self.getLeft().toExpr("milvus") + ")";
            case "and":
            case "or":
                if (self.getRight() == null) {
                    raiseQueryError(op + " operator requires both left and right operands");
                }
                return "(" + self.getLeft().toExpr("milvus") + ") " + op
                        + " (" + self.getRight().toExpr("milvus") + ")";
            default:
                raiseQueryError("Unsupported logical operator: " + self.getOperator());
        }
        return null; // unreachable
    }

    static String textMatchFilter(MatchExpr self) {
        String pattern = self.getValue();
        MatchMode mode = self.getMatchMode();
        switch (mode) {
            case EXACT:
                return "TEXT_MATCH(" + self.getField() + ", " + sanitize(pattern) + ")";
            case PREFIX:
                pattern = pattern + "%";
                break;
            case SUFFIX:
                pattern = "%" + pattern;
                break;
            case INFIX:
                pattern = "%" + pattern + "%";
                break;
            default:
                raiseQueryError("Unknown match mode: " + mode);
        }
        return self.getField() + " like " + sanitize(pattern);
    }

    private static String sanitize(Object value) {
        return com.openjiuwen.spi.store.query.QueryExpr.sanitizeStr(value);
    }

    private static void raiseQueryError(String reason) {
        throw ErrorHelper.buildError(StatusCode.RETRIEVAL_VECTOR_STORE_QUERY_INVALID,
                "reason", reason);
    }
}
