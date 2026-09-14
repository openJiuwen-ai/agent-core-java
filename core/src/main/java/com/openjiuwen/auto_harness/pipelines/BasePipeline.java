/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.pipelines;

import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;
import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.PipelineSpec;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.auto_harness.stages.BaseStage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Base interface for explicit pipeline orchestration.
 *
 * <p>Mirrors Python's {@code BasePipeline} in
 * {@code openjiuwen/auto_harness/pipelines/base.py}.</p>
 */
public abstract class BasePipeline {

    public record StageOrderEntry(String slot, String displayName) {
    }

    public String name() {
        return "";
    }

    public String description() {
        return "";
    }

    public List<String> expectedOutputs() {
        return List.of();
    }

    public PipelineStageMap stageMap() {
        return new PipelineStageMap();
    }

    public List<StageOrderEntry> stageOrder() {
        return List.of();
    }

    public PipelineSpec spec() {
        return PipelineSpec.builder()
                .name(name())
                .pipelineCls(getClass())
                .description(description())
                .expectedOutputs(List.copyOf(expectedOutputs()))
                .build();
    }

    public Iterator<Object> stream(BaseExecutionContext ctx) {
        throw new UnsupportedOperationException("Pipeline stream not implemented");
    }

    public BaseStage resolveStage(String slot) {
        return stageMap().resolve(slot);
    }

    protected Iterator<Object> streamStage(
            BaseStage stage,
            BaseExecutionContext ctx,
            List<StageResult> resultHolder
    ) {
        List<Object> events = new ArrayList<>();
        String stageName = stage.slot().isEmpty() ? stage.name() : stage.slot();
        if (!stage.displayName().isEmpty()) {
            events.add(ctx.message(stage.displayName(), stageName));
        }
        Iterator<Object> stream = stage.stream(ctx);
        while (stream.hasNext()) {
            Object event = stream.next();
            if (event instanceof StageResult result) {
                resultHolder.add(result);
                if (result.getArtifacts() != null && !result.getArtifacts().isEmpty()) {
                    ctx.putArtifacts(result.getArtifacts());
                }
                for (String message : result.getMessages() == null ? List.<String>of() : result.getMessages()) {
                    events.add(ctx.message(message));
                }
                events.add(result);
                continue;
            }
            events.add(event);
        }
        return events.iterator();
    }

    protected StageResult requireStageResult(BaseStage stage, List<StageResult> resultHolder) {
        if (resultHolder == null || resultHolder.isEmpty()) {
            throw new IllegalStateException("Stage '" + stage.name() + "' did not return a StageResult");
        }
        return resultHolder.get(resultHolder.size() - 1);
    }

    protected boolean didStageFail(BaseStage stage, List<StageResult> resultHolder) {
        return "failed".equals(requireStageResult(stage, resultHolder).getStatus());
    }

    protected String describeContext(BaseExecutionContext ctx) {
        if (ctx instanceof TaskContext taskContext) {
            String topic = taskContext.getTask() == null ? "" : String.valueOf(taskContext.getTask().getTopic());
            String status = taskContext.getTask() == null ? "" : String.valueOf(taskContext.getTask().getStatus());
            return "task=" + topic + " status=" + status;
        }
        return "session=" + ctx.getOrchestrator().getRuntime().getSessionId()
                + " pipeline=" + ctx.getOrchestrator().getRuntime().getSelectedPipeline();
    }
}
