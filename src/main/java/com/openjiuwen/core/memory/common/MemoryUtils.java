/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.memory.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility methods for memory module.
 */
public final class MemoryUtils {

    private MemoryUtils() {
    }

    /**
     * Generate vector index name from user id, scope id and memory type.
     */
    public static String generateIdxName(String userId, String scopeId, String memType) {
        return String.format("uid_%s_gid_%s_mtype_%s", userId, scopeId, memType);
    }

    /**
     * Parse memory type from vector index name.
     */
    public static String parseMemTypeFromIdxName(String idxName) {
        String[] parts = idxName.split("_");
        return parts[parts.length - 1];
    }

    /**
     * Parse memory hit infos from search results.
     *
     * @param hits list of (id, score) pairs
     * @return tuple of (ids list, scores map)
     */
    public static HitParseResult parseMemoryHitInfos(List<Map.Entry<String, Double>> hits) {
        List<String> ids = new ArrayList<>();
        Map<String, Double> scores = new HashMap<>();
        if (hits != null) {
            for (Map.Entry<String, Double> hit : hits) {
                ids.add(hit.getKey());
                scores.put(hit.getKey(), hit.getValue());
            }
        }
        return new HitParseResult(ids, scores);
    }

    /**
     * Result of parsing memory hit infos.
     */
    public static class HitParseResult {
        private final List<String> ids;
        private final Map<String, Double> scores;

        public HitParseResult(List<String> ids, Map<String, Double> scores) {
            this.ids = ids;
            this.scores = scores;
        }

        public List<String> getIds() {
            return ids;
        }

        public List<String> ids() {
            return ids;
        }

        public Map<String, Double> getScores() {
            return scores;
        }

        public Map<String, Double> scores() {
            return scores;
        }
    }
}
