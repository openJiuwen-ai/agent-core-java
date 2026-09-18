/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.query;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.core.foundation.store.query.chroma_query_func} in
 * {@code openjiuwen/core/foundation/store/query/chroma_query_func.py}.
 */
public final class ChromaQueryLanguage {

    private static final Map<String, String> OPERATOR_MAP = createOperatorMap();

    public static final QueryLanguageDefinition CHROMA_DEF = new QueryLanguageDefinition(
            ChromaQueryLanguage::chromaComparisonFilter,
            ChromaQueryLanguage::chromaRangeFilter,
            ChromaQueryLanguage::chromaArithmeticFilter,
            ChromaQueryLanguage::chromaNullFilter,
            ChromaQueryLanguage::chromaJsonFilter,
            ChromaQueryLanguage::chromaArrayFilter,
            ChromaQueryLanguage::chromaLogicalFilter,
            ChromaQueryLanguage::chromaTextMatchFilter
    );

    private ChromaQueryLanguage() {
    }

    public static Map<String, Object> chromaComparisonFilter(ComparisonExpr expr) {
        Map<String, Object> whereFilter = new LinkedHashMap<>();
        Map<String, Object> whereDocumentFilter = new LinkedHashMap<>();

        String chromaOperator = OPERATOR_MAP.get(expr.getOperator());
        if (chromaOperator == null) {
            QueryExpr.raiseQueryError("Unsupported comparison operator: " + expr.getOperator());
        }

        switch (chromaOperator) {
            case "$eq":
                whereFilter.put(expr.getField(), expr.getValue());
                break;
            case "$nin":
                Map<String, Object> notInFilter = new LinkedHashMap<>();
                notInFilter.put(chromaOperator, java.util.Collections.singletonList(expr.getValue()));
                whereFilter.put(expr.getField(), notInFilter);
                break;
            default:
                Map<String, Object> comparisonFilter = new LinkedHashMap<>();
                comparisonFilter.put(chromaOperator, expr.getValue());
                whereFilter.put(expr.getField(), comparisonFilter);
                break;
        }
        return result(whereFilter, whereDocumentFilter);
    }

    public static Map<String, Object> chromaRangeFilter(RangeExpr expr) {
        Map<String, Object> whereFilter = new LinkedHashMap<>();
        Map<String, Object> whereDocumentFilter = new LinkedHashMap<>();

        if ("in".equalsIgnoreCase(expr.getOperator())) {
            List<Object> values = asSequence(expr.getValue());
            if (values == null) {
                QueryExpr.raiseQueryError("in operator requires a sequence or set value");
            }
            Map<String, Object> inFilter = new LinkedHashMap<>();
            inFilter.put("$in", values);
            whereFilter.put(expr.getField(), inFilter);
            return result(whereFilter, whereDocumentFilter);
        }

        QueryExpr.raiseQueryError("Unsupported range operator: " + expr.getOperator());
        return result(whereFilter, whereDocumentFilter);
    }

    public static Map<String, Object> chromaArithmeticFilter(ArithmeticExpr expr) {
        QueryExpr.raiseQueryError(
                "Chroma does not support arithmetic operations in metadata filters. "
                        + "Consider pre-computing the arithmetic result and storing it as a metadata field."
        );
        return Map.of();
    }

    public static Map<String, Object> chromaNullFilter(NullExpr expr) {
        QueryExpr.raiseQueryError(
                "Chroma does not support nested JSON fields in metadata. "
                        + "Chroma only supports flat metadata (str, int, float, bool, None). "
                        + "Consider flattening your metadata structure (e.g., 'user.name' -> 'user_name')."
        );
        return Map.of();
    }

    public static Map<String, Object> chromaJsonFilter(JSONExpr expr) {
        QueryExpr.raiseQueryError(
                "Chroma does not support nested JSON fields in metadata. "
                        + "Chroma only supports flat metadata (str, int, float, bool, None). "
                        + "Consider flattening your metadata structure (e.g., 'user.name' -> 'user_name')."
        );
        return Map.of();
    }

    public static Map<String, Object> chromaArrayFilter(ArrayExpr expr) {
        QueryExpr.raiseQueryError(
                "Chroma does not support array indexing in metadata. "
                        + "Chroma only supports flat metadata (str, int, float, bool, None). "
                        + "Consider flattening your array structure (e.g., 'tags[0]' -> 'tag_0')."
        );
        return Map.of();
    }

    public static Map<String, Object> chromaLogicalFilter(LogicalExpr expr) {
        Map<String, Object> leftResult = castResult(expr.getLeft().toExpr("chroma"));
        Map<String, Object> rightResult = expr.getRight() == null ? null : castResult(expr.getRight().toExpr("chroma"));

        Map<String, Object> leftWhere = castInner(leftResult.get("where"));
        Map<String, Object> leftWhereDocument = castInner(leftResult.get("where_document"));
        Map<String, Object> rightWhere = rightResult == null ? new LinkedHashMap<>() : castInner(rightResult.get("where"));
        Map<String, Object> rightWhereDocument = rightResult == null
                ? new LinkedHashMap<>()
                : castInner(rightResult.get("where_document"));

        Map<String, Object> whereFilter;
        Map<String, Object> whereDocumentFilter;
        switch (expr.getOperator().toLowerCase(java.util.Locale.ROOT)) {
            case "and":
                whereFilter = combineFilters("$and", leftWhere, rightWhere);
                whereDocumentFilter = combineFilters("$and", leftWhereDocument, rightWhereDocument);
                break;
            case "or":
                whereFilter = combineFilters("$or", leftWhere, rightWhere);
                whereDocumentFilter = combineFilters("$or", leftWhereDocument, rightWhereDocument);
                break;
            default:
                QueryExpr.raiseQueryError("Unsupported logical operator: " + expr.getOperator());
                return Map.of();
        }

        if (expr.getRight() == null) {
            QueryExpr.raiseQueryError(expr.getOperator().toLowerCase(java.util.Locale.ROOT)
                    + " operator requires both left and right operands");
        }
        return result(whereFilter, whereDocumentFilter);
    }

    public static Map<String, Object> chromaTextMatchFilter(MatchExpr expr) {
        Map<String, Object> whereFilter = new LinkedHashMap<>();
        Map<String, Object> whereDocumentFilter = new LinkedHashMap<>();
        String pattern = expr.getValue();

        switch (expr.getMatchMode().toPythonValue()) {
            case "exact":
                whereDocumentFilter.put("$contains", pattern);
                break;
            case "prefix":
                whereDocumentFilter.put("$regex", "^" + pattern);
                break;
            case "suffix":
                whereDocumentFilter.put("$regex", pattern + "$");
                break;
            case "infix":
                whereDocumentFilter.put("$contains", pattern);
                break;
            default:
                QueryExpr.raiseQueryError("Unknown match mode: " + expr.getMatchMode().toPythonValue());
                break;
        }

        return result(whereFilter, whereDocumentFilter);
    }

    private static Map<String, String> createOperatorMap() {
        Map<String, String> operators = new LinkedHashMap<>();
        operators.put("==", "$eq");
        operators.put("!=", "$nin");
        operators.put(">", "$gt");
        operators.put(">=", "$gte");
        operators.put("<", "$lt");
        operators.put("<=", "$lte");
        return operators;
    }

    private static Map<String, Object> result(Map<String, Object> whereFilter, Map<String, Object> whereDocumentFilter) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("where", whereFilter);
        result.put("where_document", whereDocumentFilter);
        return result;
    }

    private static Map<String, Object> combineFilters(
            String operator,
            Map<String, Object> leftFilter,
            Map<String, Object> rightFilter) {
        if (!leftFilter.isEmpty() && !rightFilter.isEmpty()) {
            Map<String, Object> combined = new LinkedHashMap<>();
            combined.put(operator, List.of(leftFilter, rightFilter));
            return combined;
        }
        if (!leftFilter.isEmpty()) {
            return new LinkedHashMap<>(leftFilter);
        }
        if (!rightFilter.isEmpty()) {
            return new LinkedHashMap<>(rightFilter);
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castResult(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private static Map<String, Object> castInner(Object value) {
        return castResult(value);
    }

    private static List<Object> asSequence(Object value) {
        if (value instanceof CharSequence chars) {
            List<Object> items = new ArrayList<>(chars.length());
            for (int i = 0; i < chars.length(); i++) {
                items.add(String.valueOf(chars.charAt(i)));
            }
            return items;
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> items = new ArrayList<>();
            for (Object item : iterable) {
                items.add(item);
            }
            return items;
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> items = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                items.add(Array.get(value, i));
            }
            return items;
        }
        return null;
    }
}
