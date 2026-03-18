/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.common;

import com.openjiuwen.core.common.exception.StatusCode;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared retrieval validation helpers.
 */
public final class RetrievalValidation {

    public static final Set<String> INDEX_TYPES = Set.of("hybrid", "bm25", "vector");
    public static final Set<String> DISTANCE_METRICS = Set.of("cosine", "euclidean", "dot");
    public static final Set<String> STORE_TYPES = Set.of("milvus", "chroma", "pgvector");
    private static final Pattern DATABASE_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]*$");

    private RetrievalValidation() {
    }

    public static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw RetrievalExceptions.validation(field + " is required");
        }
    }

    public static void requireNonNull(Object value, String field) {
        if (value == null) {
            throw RetrievalExceptions.validation(field + " is required");
        }
    }

    public static void requirePositive(int value, String field, StatusCode status) {
        if (value <= 0) {
            throw RetrievalExceptions.error(status, field + " must be > 0");
        }
    }

    public static void requireNonNegative(int value, String field, StatusCode status) {
        if (value < 0) {
            throw RetrievalExceptions.error(status, field + " must be >= 0");
        }
    }

    public static String validateIndexType(String value, String field) {
        requireNonBlank(value, field);
        String normalized = value.toLowerCase();
        if (!INDEX_TYPES.contains(normalized)) {
            throw RetrievalExceptions.validation(field + " must be one of " + INDEX_TYPES);
        }
        return normalized;
    }

    public static String validateDistanceMetric(String value, String field) {
        requireNonBlank(value, field);
        String normalized = value.toLowerCase();
        if (!DISTANCE_METRICS.contains(normalized)) {
            throw RetrievalExceptions.validation(field + " must be one of " + DISTANCE_METRICS);
        }
        return normalized;
    }

    public static String validateStoreType(String value, String field) {
        requireNonBlank(value, field);
        String normalized = value.toLowerCase();
        if (!STORE_TYPES.contains(normalized)) {
            throw RetrievalExceptions.validation(field + " must be one of " + STORE_TYPES);
        }
        return normalized;
    }

    public static void validateDatabaseName(String value, String field) {
        if (value == null) {
            return;
        }
        if (!DATABASE_NAME_PATTERN.matcher(value).matches()) {
            throw RetrievalExceptions.validation(field + " must match " + DATABASE_NAME_PATTERN.pattern());
        }
    }
}
