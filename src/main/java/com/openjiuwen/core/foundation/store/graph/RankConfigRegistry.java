/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Result ranking configuration registry for hybrid graph search.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.foundation.store.graph.result_ranking} in
 * {@code openjiuwen/core/foundation/store/graph/result_ranking.py}.
 */
public final class RankConfigRegistry {

    private static final Map<String, Map<String, Object>> RANKER_CLS = new ConcurrentHashMap<>();

    private RankConfigRegistry() {
    }

    public static void registerResultRankerCls(String name, Object weighted, Object rrf) {
        registerResultRankerCls(name, weighted, rrf, Map.of());
    }

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

    public static boolean hasRanker(String database) {
        return RANKER_CLS.containsKey(database);
    }

    public static boolean hasRanker(String database, String rankType) {
        Map<String, Object> entry = RANKER_CLS.get(database);
        return entry != null && entry.containsKey(rankType);
    }
}
