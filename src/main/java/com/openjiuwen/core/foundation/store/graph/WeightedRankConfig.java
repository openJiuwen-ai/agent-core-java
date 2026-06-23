/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Weighted combination of scores from name_dense, content_dense, and content_sparse.
 * <p>
 * Mirrors Python's {@code WeightedRankConfig} in
 * {@code openjiuwen/core/foundation/store/graph/result_ranking.py}.
 */
public class WeightedRankConfig extends BaseRankConfig {

    private double nameDense = 0.15;
    private double contentDense = 0.6;
    private double contentSparse = 0.25;

    public WeightedRankConfig() {
        setName("weighted");
    }

    public WeightedRankConfig(double nameDense, double contentDense, double contentSparse) {
        this();
        setNameDense(nameDense);
        setContentDense(contentDense);
        setContentSparse(contentSparse);
    }

    @Override
    public RankerArgs getArgs() {
        List<Double> weights = List.of(nameDense, contentDense, contentSparse).stream()
                .filter(weight -> weight > 0)
                .toList();
        double total = weights.stream().mapToDouble(Double::doubleValue).sum();
        List<Object> positional = new ArrayList<>();
        if (total > 0) {
            weights.forEach(weight -> positional.add(weight / total));
        }
        return new RankerArgs(positional, Map.of());
    }

    @Override
    public List<Integer> getIsActive() {
        return List.of(
                nameDense > 0 ? 1 : 0,
                contentDense > 0 ? 1 : 0,
                contentSparse > 0 ? 1 : 0
        );
    }

    public double getNameDense() {
        return nameDense;
    }

    public void setNameDense(double nameDense) {
        validateWeight("name_dense", nameDense);
        this.nameDense = nameDense;
    }

    public double getContentDense() {
        return contentDense;
    }

    public void setContentDense(double contentDense) {
        validateWeight("content_dense", contentDense);
        this.contentDense = contentDense;
    }

    public double getContentSparse() {
        return contentSparse;
    }

    public void setContentSparse(double contentSparse) {
        validateWeight("content_sparse", contentSparse);
        this.contentSparse = contentSparse;
    }

    private static void validateWeight(String fieldName, double value) {
        if (!Double.isFinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
    }
}
