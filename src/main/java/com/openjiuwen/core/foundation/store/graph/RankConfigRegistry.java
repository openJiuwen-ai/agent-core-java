/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Result ranking configuration and registry for hybrid graph search.
 * <p>
 * Mirrors Python's result ranking classes from
 * <code>foundation/store/graph/result_ranking.py</code>.
 */
public final class RankConfigRegistry {

    private static final Map<String, Map<String, Object>> RANKER_CLS = new ConcurrentHashMap<>();

    private RankConfigRegistry() {}

    /**
     * Register result ranker classes for a graph store backend.
     *
     * @param name     database backend identifier (e.g. "milvus")
     * @param weighted callable that builds a weighted ranker
     * @param rrf      callable that builds an RRF ranker
     */
    public static void registerResultRankerCls(String name, Object weighted, Object rrf) {
        registerResultRankerCls(name, weighted, rrf, Map.of());
    }

    /**
     * Register result ranker classes plus backend-specific extra entries.
     *
     * @param name     database backend identifier (e.g. "milvus")
     * @param weighted callable that builds a weighted ranker
     * @param rrf      callable that builds an RRF ranker
     * @param extra    additional name-to-callable entries for this backend
     */
    public static void registerResultRankerCls(String name, Object weighted, Object rrf, Map<String, Object> extra) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("weighted", weighted);
        entry.put("rrf", rrf);
        if (extra != null) {
            entry.putAll(extra);
        }
        RANKER_CLS.put(name, entry);
    }

    public static Object getRankerCls(String database, String rankType) {
        Map<String, Object> entry = RANKER_CLS.get(database);
        return entry != null ? entry.get(rankType) : null;
    }

    /**
     * Check if a database has registered rankers.
     *
     * @param database database backend name
     * @return true if registered, false otherwise
     */
    public static boolean hasRanker(String database) {
        return RANKER_CLS.containsKey(database);
    }

    /**
     * Check if a specific ranker type is registered for a database.
     *
     * @param database database backend name
     * @param rankType ranker type (e.g. "weighted", "rrf")
     * @return true if registered, false otherwise
     */
    public static boolean hasRanker(String database, String rankType) {
        Map<String, Object> entry = RANKER_CLS.get(database);
        return entry != null && entry.containsKey(rankType);
    }
}
