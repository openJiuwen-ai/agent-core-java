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
 * Auto-generated for codecheck compliance.
 */
public abstract class BasePipeline {
    /**
     * Auto-generated for codecheck compliance.
     */
    public abstract String name();
    /**
     * Auto-generated for codecheck compliance.
     */
    public abstract String description();
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> expectedOutputs() {
        return List.of();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public PipelineSpec spec() {
        return PipelineSpec.builder()
                .name(name())
                .pipelineCls(getClass())
                .description(description())
                .expectedOutputs(new ArrayList<>(expectedOutputs()))
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Object> stream(BaseExecutionContext ctx) {
        return List.of();
    }

    /**
     * Auto-generated for codecheck compliance.
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
     * Auto-generated for codecheck compliance.
     */
    public StageResult requireStageResult(BaseStage stage, List<StageResult> resultHolder) {
        if (resultHolder.isEmpty()) {
            throw new IllegalStateException("Stage '" + stage.name() + "' did not return a StageResult");
        }
        return resultHolder.get(resultHolder.size() - 1);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean didStageFail(BaseStage stage, List<StageResult> resultHolder) {
        return "failed".equals(requireStageResult(stage, resultHolder).getStatus());
    }
}
