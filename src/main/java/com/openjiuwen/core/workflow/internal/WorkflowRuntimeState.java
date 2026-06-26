/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.internal;

import com.openjiuwen.core.graph.CompiledGraph;
import com.openjiuwen.core.graph.Vertex;
import com.openjiuwen.core.session.state.CommitStateLike;
import com.openjiuwen.core.session.state.InMemoryCommitState;
import com.openjiuwen.core.session.state.InMemoryStateLike;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.utils.SessionUtils;
import com.openjiuwen.core.workflow.SchemaOrTransformer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's workflow runtime state surface in
 * {@code openjiuwen/core/workflow/workflow.py}.
 */
public class WorkflowRuntimeState extends WorkflowCommitState
        implements Vertex.VertexState, CompiledGraph.WorkflowState {

    public WorkflowRuntimeState(
            CommitStateLike ioState,
            CommitStateLike globalState,
            CommitStateLike compState,
            CommitStateLike workflowState,
            Map<String, Object> traceState,
            String parentId,
            String nodeId) {
        super(ioState, globalState, compState, workflowState, traceState, parentId, nodeId);
    }

    public static WorkflowRuntimeState create() {
        return create("", DEFAULT_NODE_ID);
    }

    public static WorkflowRuntimeState create(String parentId, String nodeId) {
        return new WorkflowRuntimeState(
                new InMemoryCommitState(new InMemoryStateLike()),
                new InMemoryCommitState(new InMemoryStateLike()),
                new InMemoryCommitState(new InMemoryStateLike()),
                new InMemoryCommitState(new InMemoryStateLike()),
                new HashMap<>(),
                parentId,
                nodeId);
    }

    public static WorkflowRuntimeState from(WorkflowCommitState state) {
        return from(state, "", DEFAULT_NODE_ID);
    }

    public static WorkflowRuntimeState from(WorkflowCommitState state, String parentId, String nodeId) {
        if (state instanceof WorkflowRuntimeState runtimeState) {
            return runtimeState;
        }
        WorkflowRuntimeState runtimeState = create(
                parentId != null ? parentId : "",
                nodeId != null && !nodeId.isBlank() ? nodeId : DEFAULT_NODE_ID);
        if (state != null) {
            runtimeState.setState(state.getState());
            runtimeState.setUpdates(state.getUpdates());
        }
        return runtimeState;
    }

    @Override
    public Map<String, Object> getInputsByTransformer(Vertex.ValueTransformer transformer) {
        if (transformer == null) {
            return Map.of();
        }
        return transformer.apply(getState());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getInputs(Object schema) {
        Object resolvedSchema = unwrapSchema(schema);
        if (resolvedSchema instanceof Vertex.ValueTransformer transformer) {
            return getInputsByTransformer(transformer);
        }
        Object inputs = super.getInputs(resolvedSchema);
        if (inputs instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return Map.of();
    }

    @Override
    public void setOutputs(Map<String, Object> outputs) {
        super.setOutputs(outputs);
    }

    @Override
    public Object getOutputs(String targetNodeId) {
        return super.getOutputs(targetNodeId);
    }

    @Override
    public Object getWorkflowState(String key) {
        return super.getWorkflowState(key);
    }

    @Override
    public void updateAndCommitWorkflowState(Map<String, Object> data) {
        super.updateAndCommitWorkflowState(data);
    }

    @Override
    public Object get(String key) {
        return super.get(key);
    }

    @Override
    public WorkflowRuntimeState createNodeState(String newNodeId, String newParentId) {
        return new WorkflowRuntimeState(
                ioState,
                globalState,
                compState,
                workflowState,
                traceState,
                newParentId,
                newNodeId);
    }

    private static Object unwrapSchema(Object schema) {
        if (schema instanceof SchemaOrTransformer union) {
            if (union.isSchema()) {
                return union.getSchema();
            }
            if (union.isTransformer()) {
                return (Vertex.ValueTransformer) state -> {
                    Object value = union.getTransformer().apply(state);
                    if (value instanceof Map<?, ?> map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> typedMap = (Map<String, Object>) map;
                        return typedMap;
                    }
                    return Map.of();
                };
            }
        }
        if (schema instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedMap = (Map<String, Object>) map;
            return typedMap;
        }
        return schema;
    }
}
