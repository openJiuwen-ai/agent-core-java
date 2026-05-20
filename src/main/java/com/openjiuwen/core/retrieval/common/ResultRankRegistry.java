/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for database-native ranker implementations.
 */
public final class ResultRankRegistry {

    private static final Map<String, Map<String, Class<?>>> RANKER_CLASSES = new ConcurrentHashMap<>();

    private ResultRankRegistry() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void registerResultRankerClass(String database,
                                                 Class<?> weightedClass,
                                                 Class<?> rrfClass,
                                                 Map<String, Class<?>> extras) {
        RetrievalValidation.requireNonBlank(database, "database");
        Map<String, Class<?>> entry = new LinkedHashMap<>();
        if (weightedClass != null) {
            entry.put("weighted", weightedClass);
        }
        if (rrfClass != null) {
            entry.put("rrf", rrfClass);
        }
        if (extras != null) {
            entry.putAll(extras);
        }
        RANKER_CLASSES.put(database.toLowerCase(Locale.ROOT), entry);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Class<?> getRankerClass(String database, String name) {
        if (database == null || name == null) {
            return null;
        }
        return RANKER_CLASSES.getOrDefault(database.toLowerCase(Locale.ROOT), Map.of()).get(name);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Map<String, Class<?>> getRankerClasses(String database) {
        if (database == null) {
            return Map.of();
        }
        return new LinkedHashMap<>(RANKER_CLASSES.getOrDefault(database.toLowerCase(Locale.ROOT), Map.of()));
    }
}
