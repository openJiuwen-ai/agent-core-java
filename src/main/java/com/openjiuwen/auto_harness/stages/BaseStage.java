/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.SessionContext;
import com.openjiuwen.auto_harness.contexts.TaskContext;
import com.openjiuwen.auto_harness.schema.StageResult;
import com.openjiuwen.auto_harness.schema.StageSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Base stage interface for auto-harness.
 *
 * <p>Mirrors Python's {@code BaseStage} in {@code openjiuwen.auto_harness.stages.base}.</p>
 */
public abstract class BaseStage {

    public static final String NAME = "";

    /**
     * Get the stage name.
     *
     * @return the stage name
     */
    public abstract String name();

    /**
     * Get the stage description.
     *
     * @return the description
     */
    public String description() {
        return "";
    }

    /**
     * Get the consumed artifacts.
     *
     * @return the consumed artifacts list
     */
    public List<String> consumes() {
        return List.of();
    }

    /**
     * Get the produced artifacts.
     *
     * @return the produced artifacts list
     */
    public List<String> produces() {
        return List.of();
    }

    /**
     * Get the stage scope (session or task).
     *
     * @return the scope
     */
    public String scope() {
        return "session";
    }

    /**
     * Return the stage specification.
     *
     * @return a StageSpec instance
     */
    public StageSpec spec() {
        return new StageSpec(
                name(),
                getClass(),
                scope(),
                consumes(),
                produces(),
                description()
        );
    }

    /**
     * Run the stage with a context object.
     *
     * @param context the execution context
     * @return the stage result
     */
    public abstract StageResult run(Object context);

    /**
     * Execute the stage with callback-based event streaming.
     *
     * @param ctx       the session context
     * @param eventSink the callback for events
     */
    public void execute(SessionContext ctx, Consumer<Object> eventSink) {
        StageResult result = run(ctx);
        if (result != null) {
            eventSink.accept(result);
        }
    }

    /**
     * Execute the stage with task context.
     *
     * @param ctx       the task context
     * @param eventSink the callback for events
     */
    public void execute(TaskContext ctx, Consumer<Object> eventSink) {
        StageResult result = run(ctx);
        if (result != null) {
            eventSink.accept(result);
        }
    }
}