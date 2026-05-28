/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.indexing.indexer.IndexBackendConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified vector store abstraction.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.retrieval.vector_store.base.VectorStore}.
 * </p>
 */
public interface VectorStore extends IndexBackendConfig, AutoCloseable {

    /**
     * Check if a config dict is equivalent to actual (which may have more keys).
     * <p>
     * Mirrors Python's {@code VectorStore._check_configs_matching(configured, actual)}.
     * </p>
     *
     * @param configured the expected configuration map
     * @param actual the actual configuration map from the database
     * @throws RuntimeException if configuration mismatches are detected
     */
    static void checkConfigsMatching(Map<String, Object> configured, Map<String, Object> actual) {
        Map<String, Object> matches = new LinkedHashMap<>();
        Map<String, Object> mismatches = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : configured.entrySet()) {
            String attr = entry.getKey();
            if ("efSearchFactor".equals(attr)) {
                continue;
            }
            Object val = entry.getValue();
            String valStr = String.valueOf(val).toLowerCase();
            Object actualVal = actual.get(attr);
            String actualValStr = String.valueOf(actualVal).toLowerCase();

            // Check for exact match or numeric close match
            boolean isValid = actualValStr.equals(valStr);
            if (!isValid && val instanceof Number && isNumeric(actualValStr)) {
                isValid = isClose(parseDouble(actualValStr), parseDouble(valStr), 0.01, 0.001);
            }

            if (isValid) {
                matches.put(attr, val);
            } else {
                Map<String, String> mismatchDetail = new LinkedHashMap<>();
                mismatchDetail.put("settings", valStr);
                mismatchDetail.put("actual", actualValStr);
                mismatches.put(attr, mismatchDetail);
            }
        }

        if (!mismatches.isEmpty()) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                    "database actual config differs from current knowledge base, " +
                    "\nmatches=" + matches + "\nmismatches=" + mismatches);
        }
    }

    private static boolean isNumeric(String str) {
        String cleaned = str.replace(".", "");
        for (char c : cleaned.toCharArray()) {
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return !cleaned.isEmpty();
    }

    private static double parseDouble(String str) {
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static boolean isClose(double a, double b, double relTol, double absTol) {
        double diff = Math.abs(a - b);
        return diff <= absTol || diff <= relTol * Math.max(Math.abs(a), Math.abs(b));
    }

    String getCollectionName();

    void setCollectionName(String collectionName);

    VectorStore withCollection(String collectionName);

    /**
     * Check if vector field configuration is consistent with actual database.
     * Corresponds to Python {@code VectorStore.check_vector_field()}.
     */
    default void checkVectorField() {
        // Default no-op; concrete implementations should override if applicable
    }

    default void ensureCollection(String collectionName, String indexType, Integer dimension) {
        ensureCollection(collectionName, indexType, dimension, Map.of());
    }

    default void ensureCollection(String collectionName,
                                  String indexType,
                                  Integer dimension,
                                  Map<String, Object> options) {
        // Default no-op; concrete implementations should override if applicable
    }

    void add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options);

    List<SearchResult> search(List<Float> queryVector, int topK, Map<String, Object> filters, Map<String, Object> options);

    List<SearchResult> sparseSearch(String queryText, int topK, Map<String, Object> filters, Map<String, Object> options);

    List<SearchResult> hybridSearch(String queryText,
                                    List<Float> queryVector,
                                    int topK,
                                    double alpha,
                                    Map<String, Object> filters,
                                    Map<String, Object> options);

    boolean delete(List<String> ids, Map<String, Object> filterExpr, Map<String, Object> options);

    boolean tableExists(String tableName);

    void deleteTable(String tableName);

    List<SearchResult> queryByFilters(Map<String, Object> filters, int limit);

    long count(String tableName);

    @Override
    default void close() {
    }
}
