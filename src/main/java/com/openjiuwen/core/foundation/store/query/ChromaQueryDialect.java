/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Query expression support for ChromaDB.
 * <p>
 * Returns {@code Map<String, Map>} with "where" and "where_document" keys.
 */
public final class ChromaQueryDialect {

    private static final Map<String, String> OPERATOR_MAP = Map.of(
            "==", "$eq",
            "!=", "$nin",
            ">", "$gt",
            ">=", "$gte",
            "<", "$lt",
            "<=", "$lte"
    );

    private ChromaQueryDialect() {
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

    @SuppressWarnings("unchecked")
    static Map<String, Map<String, Object>> comparisonFilter(ComparisonExpr self) {
        Map<String, Object> whereFilter = new HashMap<>();
        Map<String, Object> whereDocumentFilter = new HashMap<>();

        String chromaOp = OPERATOR_MAP.get(self.getOperator());
        if (chromaOp == null) {
            raiseQueryError("Unsupported comparison operator: " + self.getOperator());
        }

        switch (chromaOp) {
            case "$eq":
                whereFilter.put(self.getField(), self.getValue());
                break;
            case "$nin":
                whereFilter.put(self.getField(), Map.of(chromaOp, List.of(self.getValue())));
                break;
            default:
                whereFilter.put(self.getField(), Map.of(chromaOp, self.getValue()));
                break;
        }

        return Map.of("where", whereFilter, "where_document", whereDocumentFilter);
    }

    static Map<String, Map<String, Object>> rangeFilter(RangeExpr self) {
        Map<String, Object> whereFilter = new HashMap<>();
        Map<String, Object> whereDocumentFilter = new HashMap<>();

        if ("in".equalsIgnoreCase(self.getOperator())) {
            Object val = self.getValue();
            if (val instanceof Collection<?>) {
                List<Object> valueList = new ArrayList<>((Collection<?>) val);
                whereFilter.put(self.getField(), Map.of("$in", valueList));
            } else {
                raiseQueryError("in operator requires a collection value");
            }
        } else {
            raiseQueryError("Unsupported range operator: " + self.getOperator());
        }

        return Map.of("where", whereFilter, "where_document", whereDocumentFilter);
    }

    static Map<String, Map<String, Object>> arithmeticFilter(ArithmeticExpr self) {
        raiseQueryError(
                "Chroma does not support arithmetic operations in metadata filters. "
                        + "Consider pre-computing the arithmetic result and storing it as a metadata field.");
        return null; // unreachable
    }

    static Map<String, Map<String, Object>> nullFilter(NullExpr self) {
        raiseQueryError(
                "Chroma does not support null checks in metadata. "
                        + "Chroma only supports flat metadata (str, int, float, bool, None).");
        return null; // unreachable
    }

    static Map<String, Map<String, Object>> jsonFilter(JSONExpr self) {
        raiseQueryError(
                "Chroma does not support nested JSON fields in metadata. "
                        + "Consider flattening your metadata structure (e.g., 'user.name' -> 'user_name').");
        return null; // unreachable
    }

    static Map<String, Map<String, Object>> arrayFilter(ArrayExpr self) {
        raiseQueryError(
                "Chroma does not support array indexing in metadata. "
                        + "Consider flattening your array structure (e.g., 'tags[0]' -> 'tag_0').");
        return null; // unreachable
    }

    @SuppressWarnings("unchecked")
    static Map<String, Map<String, Object>> logicalFilter(LogicalExpr self) {
        Map<String, Object> whereFilter = new HashMap<>();
        Map<String, Object> whereDocumentFilter = new HashMap<>();

        Map<String, Map<String, Object>> leftResult = (Map<String, Map<String, Object>>) self.getLeft().toExpr("chroma");
        Map<String, Map<String, Object>> rightResult = self.getRight() != null
                ? (Map<String, Map<String, Object>>) self.getRight().toExpr("chroma")
                : null;

        Map<String, Object> leftWhere = leftResult.getOrDefault("where", new HashMap<>());
        Map<String, Object> leftWhereDoc = leftResult.getOrDefault("where_document", new HashMap<>());
        Map<String, Object> rightWhere = rightResult != null ? rightResult.getOrDefault("where", new HashMap<>()) : new HashMap<>();
        Map<String, Object> rightWhereDoc = rightResult != null ? rightResult.getOrDefault("where_document", new HashMap<>()) : new HashMap<>();

        String op = self.getOperator().toLowerCase();
        switch (op) {
            case "and":
                whereFilter = combineFilters("$and", leftWhere, rightWhere);
                whereDocumentFilter = combineFilters("$and", leftWhereDoc, rightWhereDoc);
                break;
            case "or":
                whereFilter = combineFilters("$or", leftWhere, rightWhere);
                whereDocumentFilter = combineFilters("$or", leftWhereDoc, rightWhereDoc);
                break;
            default:
                raiseQueryError("Unsupported logical operator: " + self.getOperator());
        }

        if (self.getRight() == null) {
            raiseQueryError(op + " operator requires both left and right operands");
        }

        return Map.of("where", whereFilter, "where_document", whereDocumentFilter);
    }

    static Map<String, Map<String, Object>> textMatchFilter(MatchExpr self) {
        Map<String, Object> whereFilter = new HashMap<>();
        Map<String, Object> whereDocumentFilter = new HashMap<>();

        String pattern = self.getValue();
        MatchMode mode = self.getMatchMode();

        switch (mode) {
            case EXACT:
                whereDocumentFilter.put("$contains", pattern);
                break;
            case PREFIX:
                whereDocumentFilter.put("$regex", "^" + pattern);
                break;
            case SUFFIX:
                whereDocumentFilter.put("$regex", pattern + "$");
                break;
            case INFIX:
                whereDocumentFilter.put("$contains", pattern);
                break;
            default:
                raiseQueryError("Unknown match mode: " + mode);
        }

        return Map.of("where", whereFilter, "where_document", whereDocumentFilter);
    }

    private static Map<String, Object> combineFilters(String op, Map<String, Object> left, Map<String, Object> right) {
        if (!left.isEmpty() && !right.isEmpty()) {
            return Map.of(op, List.of(left, right));
        } else if (!left.isEmpty()) {
            return left;
        } else if (!right.isEmpty()) {
            return right;
        }
        return new HashMap<>();
    }

    private static void raiseQueryError(String reason) {
        throw ErrorHelper.buildError(StatusCode.RETRIEVAL_VECTOR_STORE_QUERY_INVALID,
                "reason", reason);
    }
}
