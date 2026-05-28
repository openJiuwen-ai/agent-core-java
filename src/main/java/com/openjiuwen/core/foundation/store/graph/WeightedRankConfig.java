/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Weighted combination of scores from name_dense, content_dense, content_sparse.
 * <p>
 * Mirrors Python's {@code WeightedRankConfig} model from
 * <code>foundation/store/graph/result_ranking.py</code>.
 *
 * <p>Weights are normalized; 0 excludes that channel.
 */
public class WeightedRankConfig extends BaseRankConfig {

    private double nameDense = 0.15;
    private double contentDense = 0.6;
    private double contentSparse = 0.25;

    public WeightedRankConfig() {
        setName("weighted");
    }

    public WeightedRankConfig(double nameDense, double contentDense, double contentSparse) {
        setName("weighted");
        this.nameDense = nameDense;
        this.contentDense = contentDense;
        this.contentSparse = contentSparse;
    }

    @Override
    public RankerArgs getArgs() {
        double total = nameDense + contentDense + contentSparse;
        List<Object> positional = new ArrayList<>();
        Map<String, Object> keyword = new HashMap<>();
        if (total > 0) {
            keyword.put("name_dense", nameDense / total);
            keyword.put("content_dense", contentDense / total);
            keyword.put("content_sparse", contentSparse / total);
        }
        return new RankerArgs(positional, keyword);
    }

    @Override
    public List<Integer> getIsActive() {
        return List.of(
                nameDense > 0 ? 1 : 0,
                contentDense > 0 ? 1 : 0,
                contentSparse > 0 ? 1 : 0
        );
    }

    public double getNameDense() { return nameDense; }
    public void setNameDense(double nameDense) { this.nameDense = nameDense; }

    public double getContentDense() { return contentDense; }
    public void setContentDense(double contentDense) { this.contentDense = contentDense; }

    public double getContentSparse() { return contentSparse; }
    public void setContentSparse(double contentSparse) { this.contentSparse = contentSparse; }
}
