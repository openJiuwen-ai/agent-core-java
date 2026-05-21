/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.pipelines;

import com.openjiuwen.auto_harness.contexts.SessionContext;
import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.schema.PipelineSpec;
import com.openjiuwen.auto_harness.schema.StageResult;
import com.openjiuwen.auto_harness.stages.BaseStage;
import com.openjiuwen.core.session.stream.OutputSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Base interface for explicit pipeline orchestration.
 *
 * <p>Mirrors Python's {@code BasePipeline} in {@code openjiuwen.auto_harness.pipelines.base}.</p>
 */
public abstract class BasePipeline {

    /**
     * Get the pipeline name.
     *
     * @return the pipeline name
     */
    public abstract String name();

    /**
     * Get the pipeline description.
     *
     * @return the description
     */
    public String description() {
        return "";
    }

    /**
     * Get the expected outputs.
     *
     * @return the expected outputs list
     */
    public List<String> expectedOutputs() {
        return List.of();
    }

    /**
     * Return the pipeline metadata.
     *
     * @return a PipelineSpec instance
     */
    public PipelineSpec spec() {
        return new PipelineSpec(
                name(),
                getClass(),
                description(),
                expectedOutputs()
        );
    }

    /**
     * Execute the pipeline with a callback-based approach.
     *
     * <p>In Java, we use Consumer callbacks instead of Python's AsyncIterator.</p>
     *
     * @param ctx       the session or task context
     * @param eventSink the callback to receive events
     */
    public void execute(SessionContext ctx, Consumer<Object> eventSink) {
        throw new UnsupportedOperationException("Pipeline execution not implemented");
    }

    /**
     * Execute the pipeline with a task context.
     *
     * @param ctx       the task context
     * @param eventSink the callback to receive events
     */
    public void execute(TaskContext ctx, Consumer<Object> eventSink) {
        throw new UnsupportedOperationException("Pipeline execution not implemented");
    }

    /**
     * Stream one stage and capture its final result.
     *
     * @param stage        the stage to execute
     * @param ctx          the context
     * @param resultHolder the list to hold results
     * @param eventSink    the callback for events
     */
    protected void streamStage(
            BaseStage stage,
            SessionContext ctx,
            List<StageResult> resultHolder,
            Consumer<Object> eventSink
    ) {
        stage.execute(ctx, event -> {
            if (event instanceof StageResult result) {
                resultHolder.add(result);
                if (result.getArtifacts() != null && !result.getArtifacts().isEmpty()) {
                    ctx.putArtifacts(result.getArtifacts());
                }
                if (result.getMessages() != null) {
                    for (String message : result.getMessages()) {
                        eventSink.accept(SessionContext.message(message));
                    }
                }
            } else {
                eventSink.accept(event);
            }
        });
    }

    /**
     * Stream one stage with task context.
     *
     * @param stage        the stage to execute
     * @param ctx          the task context
     * @param resultHolder the list to hold results
     * @param eventSink    the callback for events
     */
    protected void streamStage(
            BaseStage stage,
            TaskContext ctx,
            List<StageResult> resultHolder,
            Consumer<Object> eventSink
    ) {
        stage.execute(ctx, event -> {
            if (event instanceof StageResult result) {
                resultHolder.add(result);
                if (result.getArtifacts() != null && !result.getArtifacts().isEmpty()) {
                    ctx.putArtifacts(result.getArtifacts());
                }
                if (result.getMessages() != null) {
                    for (String message : result.getMessages()) {
                        eventSink.accept(TaskContext.message(message));
                    }
                }
            } else {
                eventSink.accept(event);
            }
        });
    }

    /**
     * Return the final stage result or fail loudly.
     *
     * @param stage        the stage
     * @param resultHolder the result holder list
     * @return the final StageResult
     * @throws RuntimeException if no result is available
     */
    protected StageResult requireStageResult(BaseStage stage, List<StageResult> resultHolder) {
        if (resultHolder == null || resultHolder.isEmpty()) {
            throw new RuntimeException("Stage '" + stage.name() + "' did not return a StageResult");
        }
        return resultHolder.get(resultHolder.size() - 1);
    }

    /**
     * Return whether the stage ended in failure.
     *
     * @param stage        the stage
     * @param resultHolder the result holder list
     * @return true if stage failed
     */
    protected boolean didStageFail(BaseStage stage, List<StageResult> resultHolder) {
        StageResult result = requireStageResult(stage, resultHolder);
        return "failed".equals(result.getStatus());
    }
}