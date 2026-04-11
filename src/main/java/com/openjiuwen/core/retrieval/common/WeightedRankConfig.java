/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Weighted ranker configuration for dense/sparse fusion.
 */
public class WeightedRankConfig extends BaseRankConfig {

    private double denseName = 0.15;
    private double denseContent = 0.6;
    private double sparseContent = 0.25;

    public WeightedRankConfig() {
        super("weighted", false);
    }

    @Override
    public RankerArguments getArgs() {
        List<Double> weights = new ArrayList<>();
        if (denseName > 0) {
            weights.add(denseName);
        }
        if (denseContent > 0) {
            weights.add(denseContent);
        }
        if (sparseContent > 0) {
            weights.add(sparseContent);
        }
        double sum = weights.stream().mapToDouble(Double::doubleValue).sum();
        if (sum <= 0.0) {
            return new RankerArguments(List.of(), Map.of());
        }
        List<Object> normalized = weights.stream()
                .map(weight -> weight / sum)
                .map(value -> (Object) value)
                .toList();
        return new RankerArguments(normalized, Map.of());
    }

    public double getDenseName() {
        return denseName;
    }

    public void setDenseName(double denseName) {
        validateWeight(denseName, "denseName");
        this.denseName = denseName;
    }

    public double getDenseContent() {
        return denseContent;
    }

    public void setDenseContent(double denseContent) {
        validateWeight(denseContent, "denseContent");
        this.denseContent = denseContent;
    }

    public double getSparseContent() {
        return sparseContent;
    }

    public void setSparseContent(double sparseContent) {
        validateWeight(sparseContent, "sparseContent");
        this.sparseContent = sparseContent;
    }

    private static void validateWeight(double weight, String field) {
        if (weight < 0.0 || weight > 1.0) {
            throw RetrievalExceptions.validation(field + " must be between 0 and 1");
        }
    }
}
