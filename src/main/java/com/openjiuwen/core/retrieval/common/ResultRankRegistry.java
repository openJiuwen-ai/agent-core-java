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
 * 
 * @since 0.1.7
 */
public final class ResultRankRegistry {
    private static final Map<String, Map<String, Class<?>>> RANKER_CLASSES = new ConcurrentHashMap<>();

    /**
     * ResultRankRegistry.
     * 
     * @since 0.1.7
     */
    private ResultRankRegistry() {
    }

    /**
     * registerResultRankerClass.
     * 
     * @param database database
     * @param weightedClass weightedClass
     * @param rrfClass rrfClass
     * @param extras extras
     * @since 0.1.7
     */
    public static void registerResultRankerClass(String database, Class<?> weightedClass, Class<?> rrfClass,
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
     * getRankerClass.
     * 
     * @param database database
     * @param name name
     * @return the result
     * @since 0.1.7
     */
    public static Class<?> getRankerClass(String database, String name) {
        if (database == null || name == null) {
            return null;
        }
        return RANKER_CLASSES.getOrDefault(database.toLowerCase(Locale.ROOT), Map.of()).get(name);
    }

    /**
     * getRankerClasses.
     * 
     * @param database database
     * @return the result
     * @since 0.1.7
     */
    public static Map<String, Class<?>> getRankerClasses(String database) {
        if (database == null) {
            return Map.of();
        }
        return new LinkedHashMap<>(RANKER_CLASSES.getOrDefault(database.toLowerCase(Locale.ROOT), Map.of()));
    }
}
