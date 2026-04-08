/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
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

    protected BaseRankConfig(String name, boolean higherIsBetter) {
        this.name = name;
        this.higherIsBetter = higherIsBetter;
    }

    public String getName() {
        return name;
    }

    public boolean isHigherIsBetter() {
        return higherIsBetter;
    }

    public abstract RankerArguments getArgs();

    public List<Integer> isActive() {
        return List.of(1, 1, 1);
    }

    public Class<?> getRankerClass(String database) {
        return ResultRankRegistry.getRankerClass(database, name);
    }

    /**
     * Ranker constructor arguments.
     */
    public record RankerArguments(List<Object> positional, Map<String, Object> keyword) {
    }
}
