/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Weighted ranker configuration for dense/sparse fusion.
 * 
 * @since 0.1.7
 */
public class WeightedRankConfig extends BaseRankConfig {
    private double denseName = 0.15;
    private double denseContent = 0.6;
    private double sparseContent = 0.25;

    /**
     * WeightedRankConfig.
     * 
     * @since 0.1.7
     */
    public WeightedRankConfig() {
        super("weighted", false);
    }

    /**
     * getArgs.
     * 
     * @return the result
     * @since 0.1.7
     */
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
        List<Object> normalized = weights.stream().map(weight -> weight / sum).map(value -> (Object) value).toList();
        return new RankerArguments(normalized, Map.of());
    }

    /**
     * getDenseName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getDenseName() {
        return denseName;
    }

    /**
     * setDenseName.
     * 
     * @param denseName denseName
     * @since 0.1.7
     */
    public void setDenseName(double denseName) {
        validateWeight(denseName, "denseName");
        this.denseName = denseName;
    }

    /**
     * getDenseContent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getDenseContent() {
        return denseContent;
    }

    /**
     * setDenseContent.
     * 
     * @param denseContent denseContent
     * @since 0.1.7
     */
    public void setDenseContent(double denseContent) {
        validateWeight(denseContent, "denseContent");
        this.denseContent = denseContent;
    }

    /**
     * getSparseContent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getSparseContent() {
        return sparseContent;
    }

    /**
     * setSparseContent.
     * 
     * @param sparseContent sparseContent
     * @since 0.1.7
     */
    public void setSparseContent(double sparseContent) {
        validateWeight(sparseContent, "sparseContent");
        this.sparseContent = sparseContent;
    }

    /**
     * validateWeight.
     * 
     * @param weight weight
     * @param field field
     * @since 0.1.7
     */
    private static void validateWeight(double weight, String field) {
        if (weight < 0.0 || weight > 1.0) {
            throw RetrievalExceptions.validation(field + " must be between 0 and 1");
        }
    }
}
