/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

/**
 * Standard event names for workflow execution.
 * 
 * <p>Mirrors Python's {@code WorkflowEvents} in
 * {@code openjiuwen.core.runner.callback.events}.</p>
 */
public final class WorkflowEvents {

    /** Workflow execution started */
    public static final String WORKFLOW_STARTED = Events.getEvent("workflow_started");
    
    /** Workflow execution completed successfully */
    public static final String WORKFLOW_FINISHED = Events.getEvent("workflow_finished");
    
    /** Workflow execution failed with an error */
    public static final String WORKFLOW_ERROR = Events.getEvent("workflow_error");
    
    /** Workflow execution was cancelled */
    public static final String WORKFLOW_CANCELLED = Events.getEvent("workflow_cancelled");
    
    /** Workflow node completed execution */
    public static final String NODE_EXECUTED = Events.getEvent("workflow_node_executed");
    
    /** Workflow node execution failed */
    public static final String NODE_ERROR = Events.getEvent("workflow_node_error");
    
    /** Workflow edge was traversed */
    public static final String EDGE_TRAVERSED = Events.getEvent("workflow_edge_traversed");
    
    /** Workflow loop started */
    public static final String LOOP_STARTED = Events.getEvent("workflow_loop_started");
    
    /** Workflow loop completed */
    public static final String LOOP_FINISHED = Events.getEvent("workflow_loop_finished");
    
    /** Fired before Workflow.invoke with call arguments */
    public static final String WORKFLOW_INVOKE_INPUT = Events.getEvent("workflow_invoke_input");
    
    /** Fired after Workflow.invoke with the result */
    public static final String WORKFLOW_INVOKE_OUTPUT = Events.getEvent("workflow_invoke_output");
    
    /** Fired before Workflow.stream with call arguments */
    public static final String WORKFLOW_STREAM_INPUT = Events.getEvent("workflow_stream_input");
    
    /** Fired for each item yielded by Workflow.stream */
    public static final String WORKFLOW_STREAM_OUTPUT = Events.getEvent("workflow_stream_output");

    private WorkflowEvents() {
        // Utility class
    }
}
