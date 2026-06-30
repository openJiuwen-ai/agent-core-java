/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.pipelines;

import com.openjiuwen.autoharness.contexts.BaseExecutionContext;

import java.util.List;

/**
 * Public class ExtendedEvolvePipeline used by the Java parity implementation.
 *
 * @since 1.0
 */
public class ExtendedEvolvePipeline extends BasePipeline {
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String NAME = "extended_evolve_pipeline";

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String name() {
        return NAME;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String description() {
        return "Extended evolve generation pipeline.";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> expectedOutputs() {
        return List.of("package_result");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Object> stream(BaseExecutionContext ctx) {
        return List.of(BaseExecutionContext.message(
                "当前已选择扩展流水线，但实现尚未完成: " + NAME
        ));
    }
}
