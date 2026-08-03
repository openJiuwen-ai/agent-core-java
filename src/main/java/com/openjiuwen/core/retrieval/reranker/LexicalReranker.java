/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.reranker;

import com.openjiuwen.core.retrieval.common.RetrievalResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Local lexical reranker based on token overlap.
 */
public class LexicalReranker extends Reranker {

    @Override
    public CompletableFuture<Map<String, Double>> rerank(
            String query, List<Object> doc, Object instruct, Map<String, Object> kwargs) {
        return CompletableFuture.completedFuture(rerankSync(query, doc, instruct, kwargs));
    }

    @Override
    public Map<String, Double> rerankSync(
            String query, List<Object> doc, Object instruct, Map<String, Object> kwargs) {
        int topK = kwargs != null && kwargs.get("top_k") instanceof Number n ? n.intValue() : 10;
        Map<String, Double> scores = new LinkedHashMap<>();
        Set<String> queryTokens = tokens(query);
        for (Object item : doc) {
            String text = item instanceof String s ? s : String.valueOf(item);
            double overlap = score(queryTokens, tokens(text));
            scores.put(text, overlap);
        }
        return scores;
    }

    /**
     * Rerank retrieval results by lexical overlap.
     *
     * @param query query string
     * @param candidates retrieval result candidates
     * @param topK number of top results to return
     * @return reranked results
     */
    public List<RetrievalResult> rerank(String query, List<RetrievalResult> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Set<String> queryTokens = tokens(query);
        List<RetrievalResult> results = new ArrayList<>(candidates);
        for (RetrievalResult result : results) {
            double overlap = score(queryTokens, tokens(result.getText()));
            result.setScore(overlap);
        }
        results.sort(Comparator.comparingDouble(RetrievalResult::getScore).reversed());
        return results.size() <= topK ? results : new ArrayList<>(results.subList(0, topK));
    }

    private static double score(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        int overlap = 0;
        for (String token : left) {
            if (right.contains(token)) {
                overlap++;
            }
        }
        return overlap / Math.sqrt((double) left.size() * right.size());
    }

    private static Set<String> tokens(String text) {
        Set<String> tokens = new LinkedHashSet<>();
        if (text == null) {
            return tokens;
        }
        for (String part : text.toLowerCase(Locale.ROOT).split("[^\\p{IsAlphabetic}\\p{IsDigit}_]+")) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
        }
        return tokens;
    }
}
