/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Mirrors Python's {@code WorkflowEvents} in
 * {@code openjiuwen/core/runner/callback/events.py}.
 */
public final class WorkflowEvents {
    public static final String WORKFLOW_STARTED = Events.getEvent("workflow_started");
    public static final String WORKFLOW_FINISHED = Events.getEvent("workflow_finished");
    public static final String WORKFLOW_ERROR = Events.getEvent("workflow_error");
    public static final String WORKFLOW_CANCELLED = Events.getEvent("workflow_cancelled");
    public static final String NODE_EXECUTED = Events.getEvent("workflow_node_executed");
    public static final String NODE_ERROR = Events.getEvent("workflow_node_error");
    public static final String EDGE_TRAVERSED = Events.getEvent("workflow_edge_traversed");
    public static final String LOOP_STARTED = Events.getEvent("workflow_loop_started");
    public static final String LOOP_FINISHED = Events.getEvent("workflow_loop_finished");
    public static final String WORKFLOW_INVOKE_INPUT = Events.getEvent("workflow_invoke_input");
    public static final String WORKFLOW_INVOKE_OUTPUT = Events.getEvent("workflow_invoke_output");
    public static final String WORKFLOW_STREAM_INPUT = Events.getEvent("workflow_stream_input");
    public static final String WORKFLOW_STREAM_OUTPUT = Events.getEvent("workflow_stream_output");
    public static final String COMPONENT_BATCH_INPUT = Events.getEvent("workflow_component_batch_input");
    public static final String COMPONENT_BATCH_OUTPUT = Events.getEvent("workflow_component_batch_output");
    public static final String COMPONENT_STREAM_INPUT = Events.getEvent("workflow_component_stream_input");
    public static final String COMPONENT_STREAM_OUTPUT = Events.getEvent("workflow_component_stream_output");

    private WorkflowEvents() {
    }
}
