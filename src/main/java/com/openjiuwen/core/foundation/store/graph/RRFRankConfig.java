/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion (RRF) rank configuration.
 * <p>
 * Mirrors Python's {@code RRFRankConfig} model from
 * <code>foundation/store/graph/result_ranking.py</code>.
 */
public class RRFRankConfig extends BaseRankConfig {

    private int k = 40;
    private boolean nameDense = true;
    private boolean contentDense = true;
    private boolean contentSparse = true;

    public RRFRankConfig() {
        setName("rrf");
        setHigherIsBetter(true);
    }

    public RRFRankConfig(int k) {
        this();
        this.k = k;
    }

    public RRFRankConfig(boolean nameDense, boolean contentDense, boolean contentSparse) {
        this();
        this.nameDense = nameDense;
        this.contentDense = contentDense;
        this.contentSparse = contentSparse;
    }

    public RRFRankConfig(int k, boolean nameDense, boolean contentDense, boolean contentSparse) {
        this(k);
        this.nameDense = nameDense;
        this.contentDense = contentDense;
        this.contentSparse = contentSparse;
    }

    @Override
    public RankerArgs getArgs() {
        List<Object> positional = new ArrayList<>();
        Map<String, Object> keyword = new HashMap<>();
        positional.add(k);
        return new RankerArgs(positional, keyword);
    }

    @Override
    public List<Integer> getIsActive() {
        return List.of(
                nameDense ? 1 : 0,
                contentDense ? 1 : 0,
                contentSparse ? 1 : 0
        );
    }

    public int getK() { return k; }
    public void setK(int k) { this.k = k; }

    public boolean isNameDense() { return nameDense; }
    public void setNameDense(boolean nameDense) { this.nameDense = nameDense; }

    public boolean isContentDense() { return contentDense; }
    public void setContentDense(boolean contentDense) { this.contentDense = contentDense; }

    public boolean isContentSparse() { return contentSparse; }
    public void setContentSparse(boolean contentSparse) { this.contentSparse = contentSparse; }
}
