/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.stages;

import com.openjiuwen.autoharness.contexts.BaseExecutionContext;
import com.openjiuwen.autoharness.schema.StageResult;

import java.util.List;

/**
 * BaseStage.
 * 
 * @since 0.1.7
 */
public abstract class BaseStage {
    /**
     * name.
     * 
     * @return the result
     * @since 0.1.7
     */
    public abstract String name();

    /**
     * description.
     * 
     * @return the result
     * @since 0.1.7
     */
    public abstract String description();

    /**
     * consumes.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> consumes() {
        return List.of();
    }

    /**
     * produces.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> produces() {
        return List.of();
    }

    /**
     * scope.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String scope() {
        return "session";
    }

    /**
     * run.
     * 
     * @param ctx ctx
     * @return the result
     * @since 0.1.7
     */
    public StageResult run(BaseExecutionContext ctx) {
        return StageResult.builder().build();
    }

    /**
     * stream.
     * 
     * @param ctx ctx
     * @return the result
     * @since 0.1.7
     */
    public List<Object> stream(BaseExecutionContext ctx) {
        return List.of(run(ctx));
    }
}
