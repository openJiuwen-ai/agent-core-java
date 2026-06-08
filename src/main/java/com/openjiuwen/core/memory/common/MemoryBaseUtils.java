/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Memory base utilities.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.memory.common.base} in
 * {@code openjiuwen/core/memory/common/base.py}.</p>
 */
public final class MemoryBaseUtils {

    private MemoryBaseUtils() {}

    /**
     * Generate vector index name.
     */
    public static String generateIdxName(String userId, String scopeId, String memType) {
        return String.format("uid_%s_gid_%s_mtype_%s", userId, scopeId, memType);
    }

    /**
     * Parse memory type from vector index name.
     */
    public static String parseMemtypeFromIdxName(String idxName) {
        if (idxName == null || !idxName.contains("_")) {
            return null;
        }
        String[] parts = idxName.split("_");
        return parts[parts.length - 1];
    }

    /**
     * Parse memory hit infos from hits list.
     */
    public static MemoryHitInfos parseMemoryHitInfos(List<Map.Entry<String, Double>> hits) {
        try {
            List<String> ids = new ArrayList<>();
            Map<String, Double> scores = new HashMap<>();
            
            if (hits != null) {
                for (Map.Entry<String, Double> hit : hits) {
                    ids.add(hit.getKey());
                    scores.put(hit.getKey(), hit.getValue());
                }
            }
            
            return new MemoryHitInfos(ids, scores);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse memory hit infos: " + e.getMessage(), e);
        }
    }

    /**
     * Container for memory hit infos.
     */
    public static class MemoryHitInfos {
        private final List<String> ids;
        private final Map<String, Double> scores;

        public MemoryHitInfos(List<String> ids, Map<String, Double> scores) {
            this.ids = ids;
            this.scores = scores;
        }

        public List<String> getIds() { return ids; }
        public Map<String, Double> getScores() { return scores; }
    }
}
