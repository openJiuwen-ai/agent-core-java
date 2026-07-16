/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Retrieval result aggregated across multiple knowledge bases.
 * 
 * @since 0.1.7
 */
@Getter
@Setter
public class MultiKBRetrievalResult extends RetrievalResult {
    private double rawScore;
    private double rawScoreScaled;

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<String> kbIds = new ArrayList<>();

    /**
     * MultiKBRetrievalResult.
     * 
     * @param text text
     * @param score score
     * @param rawScore rawScore
     * @param rawScoreScaled rawScoreScaled
     * @param kbIds kbIds
     * @param metadata metadata
     * @since 0.1.7
     */
    public MultiKBRetrievalResult(String text, double score, double rawScore, double rawScoreScaled, List<String> kbIds,
            Map<String, Object> metadata) {
        super(text, score, metadata, null, null);
        this.rawScore = rawScore;
        this.rawScoreScaled = rawScoreScaled;
        if (kbIds != null) {
            this.kbIds = new ArrayList<>(kbIds);
        }
    }
}
