  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.retrieval.reranker;

import com.openjiuwen.core.retrieval.common.RetrievalResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Local lexical reranker based on token overlap.
 */
public class LexicalReranker implements Reranker {

    @Override
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
        for (String part : text.toLowerCase().split("[^\\p{IsAlphabetic}\\p{IsDigit}_]+")) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
        }
        return tokens;
    }
}
