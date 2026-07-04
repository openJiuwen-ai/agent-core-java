/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code MultiKBRetrievalResult} in
 * {@code openjiuwen/core/retrieval/common/retrieval_result.py}.
 */
public class MultiKBRetrievalResult {

    private String text;
    private double score;
    private double rawScore;
    private double rawScoreScaled;
    private List<Object> kbIds = new ArrayList<>();
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public MultiKBRetrievalResult() {
    }

    public MultiKBRetrievalResult(
            String text,
            double score,
            double rawScore,
            double rawScoreScaled,
            List<?> kbIds,
            Map<String, Object> metadata
    ) {
        this.text = text;
        this.score = score;
        this.rawScore = rawScore;
        this.rawScoreScaled = rawScoreScaled;
        setKbIds(kbIds);
        setMetadata(metadata);
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public double getRawScore() {
        return rawScore;
    }

    public void setRawScore(double rawScore) {
        this.rawScore = rawScore;
    }

    public double getRawScoreScaled() {
        return rawScoreScaled;
    }

    public void setRawScoreScaled(double rawScoreScaled) {
        this.rawScoreScaled = rawScoreScaled;
    }

    public List<Object> getKbIds() {
        return kbIds;
    }

    public void setKbIds(List<?> kbIds) {
        this.kbIds = kbIds == null ? new ArrayList<>() : new ArrayList<>(kbIds);
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
