/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Retrieval result aggregated across multiple knowledge bases.
 */
@Getter
@Setter
public class MultiKBRetrievalResult extends RetrievalResult {

    private double rawScore;
    private double rawScoreScaled;
    private List<String> kbIds = new ArrayList<>();

    public MultiKBRetrievalResult(String text,
                                  double score,
                                  double rawScore,
                                  double rawScoreScaled,
                                  List<String> kbIds,
                                  Map<String, Object> metadata) {
        super(text, score, metadata, null, null);
        this.rawScore = rawScore;
        this.rawScoreScaled = rawScoreScaled;
        if (kbIds != null) {
            this.kbIds = new ArrayList<>(kbIds);
        }
    }
}
