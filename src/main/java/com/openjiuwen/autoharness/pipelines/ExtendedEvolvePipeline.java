/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.pipelines;

import com.openjiuwen.autoharness.contexts.BaseExecutionContext;

import java.util.List;

/**
 * Public class ExtendedEvolvePipeline used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class ExtendedEvolvePipeline extends BasePipeline {
    /**
     * NAME.
     * 
     * @since 0.1.7
     */
    public static final String NAME = "extended_evolve_pipeline";

    /**
     * name.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String name() {
        return NAME;
    }

    /**
     * description.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String description() {
        return "Extended evolve generation pipeline.";
    }

    /**
     * expectedOutputs.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<String> expectedOutputs() {
        return List.of("package_result");
    }

    /**
     * stream.
     * 
     * @param ctx ctx
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<Object> stream(BaseExecutionContext ctx) {
        return List.of(BaseExecutionContext.message("当前已选择扩展流水线，但实现尚未完成: " + NAME));
    }
}
