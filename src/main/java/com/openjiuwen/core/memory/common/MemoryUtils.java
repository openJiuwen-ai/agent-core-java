/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory.common;

import com.openjiuwen.core.common.utils.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Memory utility functions.
 * Corresponds to Python: common/base.py
 */
public final class MemoryUtils {

    private MemoryUtils() {
        // Utility class, prevent instantiation
    }

    /**
     * Generate vector index name.
     *
     * @param usrId user ID
     * @param scopeId scope ID
     * @param memType memory type
     * @return generated index name
     */
    public static String generateIdxName(String usrId, String scopeId, String memType) {
        return String.format("uid_%s_gid_%s_mtype_%s", usrId, scopeId, memType);
    }

    /**
     * Parse memory hit infos from search results.
     *
     * @param hits list of (id, score) pairs
     * @return parsed result containing ids list and scores map
     * @throws IllegalArgumentException if hits contain null pairs
     */
    public static ParsedHitResult parseMemoryHitInfos(List<Pair<String, Double>> hits) {
        if (hits == null || hits.isEmpty()) {
            return new ParsedHitResult(new ArrayList<>(), new HashMap<>());
        }

        try {
            List<String> ids = new ArrayList<>();
            Map<String, Double> scores = new HashMap<>();

            for (Pair<String, Double> hit : hits) {
                if (hit == null) {
                    throw new IllegalArgumentException("Hit pair cannot be null");
                }
                ids.add(hit.getKey());
                scores.put(hit.getKey(), hit.getValue());
            }

            return new ParsedHitResult(ids, scores);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse memory hit infos: " + e.getMessage(), e);
        }
    }

    /**
     * Parsed hit result record.
     *
     * @param ids list of IDs in order
     * @param scores map of ID to score
     */
    public record ParsedHitResult(List<String> ids, Map<String, Double> scores) {
    }

    /**
     * Parse memory hit infos from search results into a Pair.
     *
     * @param hits list of (id, score) pairs
     * @return Pair containing (ids list, scores map)
     */
    public static Pair<List<String>, Map<String, Double>> parseMemoryHitInfosAsPair(List<Pair<String, Double>> hits) {
        if (hits == null || hits.isEmpty()) {
            return new Pair<>(new ArrayList<>(), new HashMap<>());
        }

        List<String> ids = new ArrayList<>();
        Map<String, Double> scores = new HashMap<>();

        for (Pair<String, Double> hit : hits) {
            if (hit != null) {
                ids.add(hit.getKey());
                scores.put(hit.getKey(), hit.getValue());
            }
        }

        return new Pair<>(ids, scores);
    }
}

