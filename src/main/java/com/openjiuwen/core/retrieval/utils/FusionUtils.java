/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.utils;

import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.SearchResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code rrf_fusion} helper in
 * {@code openjiuwen/core/retrieval/utils/fusion.py}.
 */
public final class FusionUtils {

    private FusionUtils() {
    }

    public static List<RetrievalResult> rrfFusionRetrieval(List<List<RetrievalResult>> resultsList) {
        return rrfFusionRetrieval(resultsList, 60);
    }

    public static List<RetrievalResult> rrfFusionRetrieval(List<List<RetrievalResult>> resultsList, int k) {
        return finalizeRetrieval(fuseScores(resultsList, k));
    }

    public static List<SearchResult> rrfFusionSearch(List<List<SearchResult>> resultsList) {
        return rrfFusionSearch(resultsList, 60);
    }

    public static List<SearchResult> rrfFusionSearch(List<List<SearchResult>> resultsList, int k) {
        return finalizeSearch(fuseScores(resultsList, k));
    }

    private static <T> FusionState<T> fuseScores(List<List<T>> resultsList, int k) {
        Map<String, Double> scoreMap = new LinkedHashMap<>();
        Map<String, T> resultMap = new LinkedHashMap<>();
        if (resultsList == null) {
            return new FusionState<>(scoreMap, resultMap);
        }

        for (List<T> results : resultsList) {
            if (results == null) {
                continue;
            }
            for (int i = 0; i < results.size(); i++) {
                T result = results.get(i);
                String key = extractText(result);
                scoreMap.merge(key, 1.0 / (k + i + 1), Double::sum);
                resultMap.putIfAbsent(key, result);
            }
        }

        return new FusionState<>(scoreMap, resultMap);
    }

    private static String extractText(Object result) {
        if (result instanceof RetrievalResult retrievalResult) {
            return retrievalResult.getText();
        }
        if (result instanceof SearchResult searchResult) {
            return searchResult.getText();
        }
        throw new IllegalArgumentException("Unsupported fusion result type: " + result);
    }

    private static List<RetrievalResult> finalizeRetrieval(FusionState<RetrievalResult> state) {
        List<RetrievalResult> fused = new ArrayList<>();
        for (Map.Entry<String, Double> entry : sortedEntries(state.scoreMap())) {
            RetrievalResult result = state.resultMap().get(entry.getKey());
            result.setScore(entry.getValue());
            fused.add(result);
        }
        return fused;
    }

    private static List<SearchResult> finalizeSearch(FusionState<SearchResult> state) {
        List<SearchResult> fused = new ArrayList<>();
        for (Map.Entry<String, Double> entry : sortedEntries(state.scoreMap())) {
            SearchResult result = state.resultMap().get(entry.getKey());
            result.setScore(entry.getValue());
            fused.add(result);
        }
        return fused;
    }

    private static List<Map.Entry<String, Double>> sortedEntries(Map<String, Double> scoreMap) {
        List<Map.Entry<String, Double>> entries = new ArrayList<>(scoreMap.entrySet());
        entries.sort(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()));
        return entries;
    }

    private record FusionState<T>(Map<String, Double> scoreMap, Map<String, T> resultMap) {
    }
}
