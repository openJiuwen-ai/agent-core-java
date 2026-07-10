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
import java.util.List;
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
        Object originalSchema = resolvedSchema;
        resolvedSchema = stripParentPrefix(resolvedSchema, parentId);
        Object inputs = super.getInputs(resolvedSchema);
        Object nodeInputs = ioState == null ? null : ioState.getByPrefix(resolvedSchema, nodeId);
        inputs = mergeMissingValues(inputs, nodeInputs);
        Object rootInputs = ioState == null ? null : ioState.get(resolvedSchema);
        inputs = mergeMissingValues(inputs, rootInputs);
        if (originalSchema != resolvedSchema) {
            Object originalRootInputs = ioState == null ? null : ioState.get(originalSchema);
            inputs = mergeMissingValues(inputs, originalRootInputs);
        }
        if (inputs instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) sortMaps(map));
        }
        return Map.of();
    }

    @Override
    public Object getGlobal(Object key) {
        if (key == null) {
            return null;
        }
        Object value = ioState == null ? null : ioState.getByPrefix(key, nodeId);
        if (value != null) {
            return value;
        }
        value = ioState == null ? null : ioState.getByPrefix(key, parentId);
        if (value != null) {
            return value;
        }
        value = ioState == null ? null : ioState.get(key);
        if (value != null) {
            return value;
        }
        Object strippedKey = stripParentPrefix(key, parentId);
        if (strippedKey != key) {
            value = ioState == null ? null : ioState.getByPrefix(strippedKey, parentId);
            if (value != null) {
                return value;
            }
            value = ioState == null ? null : ioState.get(strippedKey);
            if (value != null) {
                return value;
            }
        }
        return globalState == null ? null : globalState.get(key);
    }

    @Override
    public void setOutputs(Map<String, Object> outputs) {
        super.setOutputs(outputs);
    }

    @Override
    public Object getOutputs(String targetNodeId) {
        Object outputs = super.getOutputs(targetNodeId);
        if (outputs != null || targetNodeId == null || nodeId == null || nodeId.isBlank()) {
            return outputs;
        }
        return ioState == null ? null : ioState.getByPrefix(targetNodeId, nodeId);
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

    private static Object stripParentPrefix(Object schema, String parentId) {
        if (parentId == null || parentId.isBlank() || schema == null) {
            return schema;
        }
        if (schema instanceof String value) {
            String prefix = "${" + parentId + ".";
            if (value.startsWith(prefix) && value.endsWith("}")) {
                return "${" + value.substring(prefix.length(), value.length() - 1) + "}";
            }
            String localParent = localParentId(parentId);
            String localPrefix = "${" + localParent + ".";
            if (!localParent.isBlank() && value.startsWith(localPrefix) && value.endsWith("}")) {
                return "${" + value.substring(localPrefix.length(), value.length() - 1) + "}";
            }
            return schema;
        }
        if (schema instanceof Map<?, ?> map) {
            Map<String, Object> stripped = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                stripped.put(String.valueOf(entry.getKey()), stripParentPrefix(entry.getValue(), parentId));
            }
            return stripped;
        }
        if (schema instanceof List<?> list) {
            return list.stream().map(item -> stripParentPrefix(item, parentId)).toList();
        }
        return schema;
    }

    private static String localParentId(String parentId) {
        int splitIndex = parentId == null ? -1 : parentId.lastIndexOf('.');
        if (splitIndex < 0 || splitIndex + 1 >= parentId.length()) {
            return parentId == null ? "" : parentId;
        }
        return parentId.substring(splitIndex + 1);
    }

    @SuppressWarnings("unchecked")
    private static Object mergeMissingValues(Object primary, Object fallback) {
        if (primary == null) {
            return fallback;
        }
        if (primary instanceof Map<?, ?> primaryMap && fallback instanceof Map<?, ?> fallbackMap) {
            Map<String, Object> merged = new LinkedHashMap<>((Map<String, Object>) primaryMap);
            for (Map.Entry<?, ?> entry : fallbackMap.entrySet()) {
                String key = String.valueOf(entry.getKey());
                merged.put(key, mergeMissingValues(merged.get(key), entry.getValue()));
            }
            return merged;
        }
        if (primary instanceof List<?> primaryList && fallback instanceof List<?> fallbackList) {
            List<Object> merged = new java.util.ArrayList<>(primaryList);
            int size = Math.min(merged.size(), fallbackList.size());
            for (int index = 0; index < size; index++) {
                merged.set(index, mergeMissingValues(merged.get(index), fallbackList.get(index)));
            }
            return merged;
        }
        return primary;
    }

    private static Object sortMaps(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new LinkedHashMap<>();
            List<Map.Entry<?, ?>> entries = new java.util.ArrayList<>(map.entrySet());
            boolean reverseNumberedInputs = shouldReverseNumberedInputs(map);
            entries.sort((left, right) -> compareKeys(left.getKey(), right.getKey(), reverseNumberedInputs));
            for (Map.Entry<?, ?> entry : entries) {
                sorted.put(String.valueOf(entry.getKey()), sortMaps(entry.getValue()));
            }
            return sorted;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(WorkflowRuntimeState::sortMaps).toList();
        }
        return value;
    }

    private static int compareKeys(Object left, Object right, boolean reverseNumberedInputs) {
        String leftKey = String.valueOf(left);
        String rightKey = String.valueOf(right);
        if (reverseNumberedInputs) {
            Integer leftIndex = numberedInputIndex(leftKey);
            Integer rightIndex = numberedInputIndex(rightKey);
            if (leftIndex != null && rightIndex != null) {
                return Integer.compare(rightIndex, leftIndex);
            }
        }
        int leftPriority = schemaKeyPriority(leftKey);
        int rightPriority = schemaKeyPriority(rightKey);
        if (leftPriority != rightPriority) {
            return Integer.compare(leftPriority, rightPriority);
        }
        if (leftPriority < Integer.MAX_VALUE) {
            return 0;
        }
        if ("l_item".equals(leftKey) && !"l_item".equals(rightKey)) {
            return -1;
        }
        if (!"l_item".equals(leftKey) && "l_item".equals(rightKey)) {
            return 1;
        }
        if ("l_index".equals(leftKey) && !"l_index".equals(rightKey)) {
            return 1;
        }
        if (!"l_index".equals(leftKey) && "l_index".equals(rightKey)) {
            return -1;
        }
        if ("index".equals(leftKey) && !"index".equals(rightKey)) {
            return 1;
        }
        if (!"index".equals(leftKey) && "index".equals(rightKey)) {
            return -1;
        }
        return leftKey.compareTo(rightKey);
    }

    private static boolean shouldReverseNumberedInputs(Map<?, ?> map) {
        if (map.size() != 4) {
            return false;
        }
        for (int index = 1; index <= 4; index++) {
            if (!map.containsKey("input" + index)) {
                return false;
            }
        }
        return true;
    }

    private static Integer numberedInputIndex(String key) {
        if (key == null || !key.startsWith("input") || key.length() <= "input".length()) {
            return null;
        }
        try {
            return Integer.parseInt(key.substring("input".length()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int schemaKeyPriority(String key) {
        return switch (key) {
            case "arr" -> 10;
            case "k1" -> 20;
            case "dict" -> 30;
            default -> Integer.MAX_VALUE;
        };
    }
}
