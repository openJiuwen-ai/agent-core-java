/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import java.util.List;
import java.util.Map;

/**
 * Base type for result-ranker configuration.
 * 
 * @since 0.1.7
 */
public abstract class BaseRankConfig {
    private final String name;
    private final boolean higherIsBetter;

    /**
     * BaseRankConfig.
     * 
     * @param name name
     * @param higherIsBetter higherIsBetter
     * @since 0.1.7
     */
    protected BaseRankConfig(String name, boolean higherIsBetter) {
        this.name = name;
        this.higherIsBetter = higherIsBetter;
    }

    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getName() {
        return name;
    }

    /**
     * isHigherIsBetter.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isHigherIsBetter() {
        return higherIsBetter;
    }

    /**
     * getArgs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public abstract RankerArguments getArgs();

    /**
     * isActive.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Integer> isActive() {
        return List.of(1, 1, 1);
    }

    /**
     * getRankerClass.
     * 
     * @param database database
     * @return the result
     * @since 0.1.7
     */
    public Class<?> getRankerClass(String database) {
        return ResultRankRegistry.getRankerClass(database, name);
    }

    /**
     * Ranker constructor arguments.
     * 
     * @since 0.1.7
     */
    public record RankerArguments(List<Object> positional, Map<String, Object> keyword) {
    }
}
