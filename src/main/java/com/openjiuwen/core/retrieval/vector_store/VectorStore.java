/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.query.QueryExpr;
import com.openjiuwen.core.retrieval.common.RetrievalResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Unified vector-store abstraction.
 * <p>
 * Mirrors Python's {@code VectorStore} in
 * {@code openjiuwen/core/retrieval/vector_store/base.py}.
 * </p>
 */
public interface VectorStore extends AutoCloseable {

    static void checkConfigsMatching(Map<String, ?> configured, Map<String, ?> actual) {
        Map<String, Object> matches = new LinkedHashMap<>();
        Map<String, Map<String, String>> mismatches = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : configured.entrySet()) {
            String attr = entry.getKey();
            if ("efSearchFactor".equals(attr)) {
                continue;
            }
            Object expected = entry.getValue();
            String expectedValue = pythonCaseFold(expected);
            String actualValue = pythonCaseFold(actual.get(attr));
            boolean valid = actualValue.equals(expectedValue);
            if (!valid && expected instanceof Number && isPythonNumeric(actualValue)) {
                valid = isClose(Double.parseDouble(actualValue), Double.parseDouble(expectedValue), 1e-2, 1e-3);
            }
            if (valid) {
                matches.put(attr, expected);
            } else {
                mismatches.put(attr, Map.of("settings", expectedValue, "actual", actualValue));
            }
        }
        if (!mismatches.isEmpty()) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                    "error_msg",
                    "database actual config differs from current knowledge base, "
                            + "\nmatches=" + matches + "\nmismatches=" + mismatches
            );
        }
    }

    void checkVectorField();

    CompletableFuture<Void> add(List<Map<String, Object>> data,
                                Integer batchSize,
                                Map<String, Object> kwargs);

    default CompletableFuture<Void> add(Map<String, Object> data,
                                        Integer batchSize,
                                        Map<String, Object> kwargs) {
        return add(List.of(data), batchSize, kwargs);
    }

    CompletableFuture<List<RetrievalResult>> search(List<Double> queryVector,
                                                    int topK,
                                                    VectorStoreFilter filters,
                                                    Map<String, Object> kwargs);

    CompletableFuture<List<RetrievalResult>> sparseSearch(String queryText,
                                                          int topK,
                                                          VectorStoreFilter filters,
                                                          Map<String, Object> kwargs);

    CompletableFuture<List<RetrievalResult>> hybridSearch(String queryText,
                                                          List<Double> queryVector,
                                                          int topK,
                                                          double alpha,
                                                          VectorStoreFilter filters,
                                                          Map<String, Object> kwargs);

    CompletableFuture<Boolean> delete(List<String> ids,
                                      DeleteFilter filterExpr,
                                      Map<String, Object> kwargs);

    CompletableFuture<Boolean> tableExists(String tableName);

    CompletableFuture<Void> deleteTable(String tableName);

    @Override
    default void close() {
    }

    private static boolean isPythonNumeric(String value) {
        String cleaned = value.replace(".", "");
        if (cleaned.isEmpty()) {
            return false;
        }
        for (int index = 0; index < cleaned.length(); index++) {
            if (!Character.isDigit(cleaned.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isClose(double left, double right, double relTol, double absTol) {
        double diff = Math.abs(left - right);
        return diff <= Math.max(relTol * Math.max(Math.abs(left), Math.abs(right)), absTol);
    }

    private static String pythonCaseFold(Object value) {
        if (value == null) {
            return "none";
        }
        return String.valueOf(value).toLowerCase(Locale.ROOT);
    }

    @FunctionalInterface
    interface ClientFactory<C> {
        C createClient(String databaseName, String pathOrUri, String token, Map<String, Object> kwargs);
    }

    record VectorStoreFilter(Map<String, Object> mapping, QueryExpr queryExpr) {

        public static VectorStoreFilter none() {
            return new VectorStoreFilter(null, null);
        }

        public static VectorStoreFilter ofMap(Map<String, Object> mapping) {
            return new VectorStoreFilter(mapping == null ? null : new LinkedHashMap<>(mapping), null);
        }

        public static VectorStoreFilter ofQuery(QueryExpr queryExpr) {
            return new VectorStoreFilter(null, queryExpr);
        }
    }

    record DeleteFilter(String expression, QueryExpr queryExpr) {

        public static DeleteFilter none() {
            return new DeleteFilter(null, null);
        }

        public static DeleteFilter ofExpression(String expression) {
            return new DeleteFilter(expression, null);
        }

        public static DeleteFilter ofQuery(QueryExpr queryExpr) {
            return new DeleteFilter(null, queryExpr);
        }
    }
}
