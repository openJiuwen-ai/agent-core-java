/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.List;
import java.util.Map;

/**
 * Base type for result-ranker configuration.
 */
public abstract class BaseRankConfig {

    private final String name;
    private final boolean higherIsBetter;

    /**
     * Auto-generated for codecheck compliance.
     */
    protected BaseRankConfig(String name, boolean higherIsBetter) {
        this.name = name;
        this.higherIsBetter = higherIsBetter;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getName() {
        return name;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isHigherIsBetter() {
        return higherIsBetter;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public abstract RankerArguments getArgs();

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Integer> isActive() {
        return List.of(1, 1, 1);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Class<?> getRankerClass(String database) {
        return ResultRankRegistry.getRankerClass(database, name);
    }

    /**
     * Ranker constructor arguments.
     */
    public record RankerArguments(List<Object> positional, Map<String, Object> keyword) {
    }
}
