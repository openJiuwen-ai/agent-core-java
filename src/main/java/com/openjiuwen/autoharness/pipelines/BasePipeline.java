/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.pipelines;

import com.openjiuwen.autoharness.contexts.BaseExecutionContext;
import com.openjiuwen.autoharness.schema.PipelineSpec;
import com.openjiuwen.autoharness.schema.StageResult;
import com.openjiuwen.autoharness.stages.BaseStage;

import java.util.ArrayList;
import java.util.List;

/**
 * BasePipeline.
 * 
 * @since 0.1.7
 */
public abstract class BasePipeline {
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
     * expectedOutputs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> expectedOutputs() {
        return List.of();
    }

    /**
     * spec.
     * 
     * @return the result
     * @since 0.1.7
     */
    public PipelineSpec spec() {
        return PipelineSpec.builder().name(name()).pipelineCls(getClass()).description(description())
                .expectedOutputs(new ArrayList<>(expectedOutputs())).build();
    }

    /**
     * stream.
     * 
     * @param ctx ctx
     * @return the result
     * @since 0.1.7
     */
    public List<Object> stream(BaseExecutionContext ctx) {
        return List.of();
    }

    /**
     * streamStage.
     * 
     * @param stage stage
     * @param ctx ctx
     * @param resultHolder resultHolder
     * @return the result
     * @since 0.1.7
     */
    public List<Object> streamStage(BaseStage stage, BaseExecutionContext ctx, List<StageResult> resultHolder) {
        List<Object> events = new ArrayList<>();
        for (Object event : stage.stream(ctx)) {
            if (event instanceof StageResult result) {
                resultHolder.add(result);
                if (result.getArtifacts() != null && !result.getArtifacts().isEmpty()) {
                    ctx.putArtifacts(result.getArtifacts());
                }
                if (result.getMessages() != null) {
                    for (String message : result.getMessages()) {
                        events.add(BaseExecutionContext.message(message));
                    }
                }
                continue;
            }
            events.add(event);
        }
        return events;
    }

    /**
     * requireStageResult.
     * 
     * @param stage stage
     * @param resultHolder resultHolder
     * @return the result
     * @since 0.1.7
     */
    public StageResult requireStageResult(BaseStage stage, List<StageResult> resultHolder) {
        if (resultHolder.isEmpty()) {
            throw new IllegalStateException("Stage '" + stage.name() + "' did not return a StageResult");
        }
        return resultHolder.get(resultHolder.size() - 1);
    }

    /**
     * didStageFail.
     * 
     * @param stage stage
     * @param resultHolder resultHolder
     * @return the result
     * @since 0.1.7
     */
    public boolean didStageFail(BaseStage stage, List<StageResult> resultHolder) {
        return "failed".equals(requireStageResult(stage, resultHolder).getStatus());
    }
}
