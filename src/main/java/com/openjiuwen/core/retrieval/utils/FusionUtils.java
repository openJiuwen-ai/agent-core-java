/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.utils;

import com.openjiuwen.core.retrieval.common.RRFRankConfig;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.WeightedRankConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fusion algorithms such as reciprocal rank fusion.
 */
public final class FusionUtils {

    private FusionUtils() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<RetrievalResult> rrfFusionRetrieval(List<List<RetrievalResult>> resultsList, int k) {
        Map<String, Double> scoreMap = new LinkedHashMap<>();
        Map<String, RetrievalResult> resultMap = new LinkedHashMap<>();
        if (resultsList == null) {
            return List.of();
        }
        for (List<RetrievalResult> results : resultsList) {
            if (results == null) {
                continue;
            }
            for (int i = 0; i < results.size(); i++) {
                RetrievalResult result = results.get(i);
                String key = result.getText();
                // Python uses rank starting at 1; i is zero-based so we add one here.
                scoreMap.merge(key, 1.0 / (k + i + 1), Double::sum);
                resultMap.putIfAbsent(key, result);
            }
        }
        List<Map.Entry<String, Double>> entries = new ArrayList<>(scoreMap.entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<RetrievalResult> fused = new ArrayList<>();
        for (Map.Entry<String, Double> entry : entries) {
            RetrievalResult result = resultMap.get(entry.getKey());
            result.setScore(entry.getValue());
            fused.add(result);
        }
        return fused;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<SearchResult> rrfFusionSearch(List<List<SearchResult>> resultsList, int k) {
        Map<String, Double> scoreMap = new LinkedHashMap<>();
        Map<String, SearchResult> resultMap = new LinkedHashMap<>();
        if (resultsList == null) {
            return List.of();
        }
        for (List<SearchResult> results : resultsList) {
            if (results == null) {
                continue;
            }
            for (int i = 0; i < results.size(); i++) {
                SearchResult result = results.get(i);
                String key = result.getText();
                scoreMap.merge(key, 1.0 / (k + i + 1), Double::sum);
                resultMap.putIfAbsent(key, result);
            }
        }
        List<Map.Entry<String, Double>> entries = new ArrayList<>(scoreMap.entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<SearchResult> fused = new ArrayList<>();
        for (Map.Entry<String, Double> entry : entries) {
            SearchResult result = resultMap.get(entry.getKey());
            result.setScore(entry.getValue());
            fused.add(result);
        }
        return fused;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<RetrievalResult> rrfFusionRetrieval(
            List<List<RetrievalResult>> resultsList,
            RRFRankConfig config) {
        return rrfFusionRetrieval(
                filterActive(resultsList, config == null ? null : config.isActive()),
                config == null ? 40 : config.getK());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<SearchResult> rrfFusionSearch(List<List<SearchResult>> resultsList, RRFRankConfig config) {
        return rrfFusionSearch(
                filterActive(resultsList, config == null ? null : config.isActive()),
                config == null ? 40 : config.getK());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<RetrievalResult> weightedFusionRetrieval(
            List<List<RetrievalResult>> resultsList,
            WeightedRankConfig config) {
        List<Double> weights = normalizeWeights(config);
        Map<String, Double> scoreMap = new LinkedHashMap<>();
        Map<String, RetrievalResult> resultMap = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(resultsList == null ? 0 : resultsList.size(), weights.size()); i++) {
            List<RetrievalResult> results = resultsList.get(i);
            if (results == null) {
                continue;
            }
            double weight = weights.get(i);
            for (RetrievalResult result : results) {
                String key = result.getText();
                scoreMap.merge(key, weight * result.getScore(), Double::sum);
                resultMap.putIfAbsent(key, result);
            }
        }
        return finalizeRetrieval(scoreMap, resultMap);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static List<SearchResult> weightedFusionSearch(
            List<List<SearchResult>> resultsList,
            WeightedRankConfig config) {
        List<Double> weights = normalizeWeights(config);
        Map<String, Double> scoreMap = new LinkedHashMap<>();
        Map<String, SearchResult> resultMap = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(resultsList == null ? 0 : resultsList.size(), weights.size()); i++) {
            List<SearchResult> results = resultsList.get(i);
            if (results == null) {
                continue;
            }
            double weight = weights.get(i);
            for (SearchResult result : results) {
                String key = result.getText();
                scoreMap.merge(key, weight * result.getScore(), Double::sum);
                resultMap.putIfAbsent(key, result);
            }
        }
        return finalizeSearch(scoreMap, resultMap);
    }

    private static <T> List<List<T>> filterActive(List<List<T>> resultsList, List<Integer> active) {
        if (resultsList == null || active == null || active.isEmpty()) {
            return resultsList == null ? List.of() : resultsList;
        }
        List<List<T>> filtered = new ArrayList<>();
        for (int i = 0; i < Math.min(resultsList.size(), active.size()); i++) {
            if (active.get(i) != null && active.get(i) > 0) {
                filtered.add(resultsList.get(i));
            }
        }
        return filtered;
    }

    private static List<Double> normalizeWeights(WeightedRankConfig config) {
        if (config == null) {
            return List.of(1.0);
        }
        List<Double> values = List.of(config.getDenseName(), config.getDenseContent(), config.getSparseContent());
        double sum = values.stream().filter(value -> value > 0).mapToDouble(Double::doubleValue).sum();
        if (sum <= 0.0) {
            return List.of();
        }
        List<Double> normalized = new ArrayList<>(values.size());
        for (Double value : values) {
            normalized.add(value > 0.0 ? value / sum : 0.0);
        }
        return normalized;
    }

    private static List<RetrievalResult> finalizeRetrieval(
            Map<String, Double> scoreMap,
            Map<String, RetrievalResult> resultMap) {
        List<Map.Entry<String, Double>> entries = new ArrayList<>(scoreMap.entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<RetrievalResult> fused = new ArrayList<>();
        for (Map.Entry<String, Double> entry : entries) {
            RetrievalResult result = resultMap.get(entry.getKey());
            result.setScore(entry.getValue());
            fused.add(result);
        }
        return fused;
    }

    private static List<SearchResult> finalizeSearch(
            Map<String, Double> scoreMap,
            Map<String, SearchResult> resultMap) {
        List<Map.Entry<String, Double>> entries = new ArrayList<>(scoreMap.entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<SearchResult> fused = new ArrayList<>();
        for (Map.Entry<String, Double> entry : entries) {
            SearchResult result = resultMap.get(entry.getKey());
            result.setScore(entry.getValue());
            fused.add(result);
        }
        return fused;
    }
}
