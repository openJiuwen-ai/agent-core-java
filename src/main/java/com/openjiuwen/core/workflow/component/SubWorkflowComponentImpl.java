/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.HasDrawable;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowComponent;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Component that wraps a sub-workflow and delegates execution to it.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.flow.workflow_comp.SubWorkflowComponent}.
 */
public class SubWorkflowComponentImpl extends WorkflowComponent implements SubWorkflowComponent {

    private static final String SUB_WORKFLOW_COMPONENT = "sub_workflow";

    private final Workflow subWorkflow;
    private final boolean cacheStream;

    public SubWorkflowComponentImpl(Workflow subWorkflow) {
        this(subWorkflow, false);
    }

    public SubWorkflowComponentImpl(Workflow subWorkflow, boolean cacheStream) {
        if (subWorkflow == null) {
            throw ErrorHelper.buildError(StatusCode.COMPONENT_SUB_WORKFLOW_PARAM_INVALID,
                    "error_msg", "sub_workflow is None");
        }
        this.subWorkflow = subWorkflow;
        this.cacheStream = cacheStream;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
        Map<String, Object> inputsMap = (inputs instanceof Map) ? (Map<String, Object>) inputs : Map.of();
        Object result = subWorkflow.invokeSubWorkflow(
                inputsMap.get(Constant.INPUTS_KEY),
                session,
                context,
                inputsMap.get(Constant.CONFIG_KEY));
        if (!cacheStream) {
            return result;
        }
        return cacheStreamOutput(result);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
        Map<String, Object> inputsMap = (inputs instanceof Map) ? (Map<String, Object>) inputs : Map.of();
        return (Iterator<Object>) (Iterator<?>) subWorkflow.streamSubWorkflow(
                inputsMap.get(Constant.INPUTS_KEY),
                session,
                context,
                inputsMap.get(Constant.CONFIG_KEY));
    }

    @Override
    public boolean graphInvoker() {
        return true;
    }

    public String componentType() {
        return SUB_WORKFLOW_COMPONENT;
    }

    public Workflow getSubWorkflow() {
        return subWorkflow;
    }

    public boolean isCacheStream() {
        return cacheStream;
    }

    @Override
    public HasDrawable getSubWorkflowInternal() {
        // The Drawable accesses workflow._internal which is the BaseWorkflow
        // This is kept as HasDrawable interface — Workflow would need to expose internal
        return subWorkflow.getInternalDrawable();
    }

    @SuppressWarnings("unchecked")
    private static Object cacheStreamOutput(Object result) {
        if (!(result instanceof Map<?, ?> resultMap) || !(resultMap.get("stream") instanceof List<?> streamFrames)) {
            return result;
        }
        Map<String, Object> mergedOutput = new LinkedHashMap<>();
        for (Object frame : streamFrames) {
            Object payload = frame instanceof com.openjiuwen.core.session.stream.OutputSchema outputSchema
                    ? outputSchema.getPayload()
                    : frame;
            if (!(payload instanceof Map<?, ?> payloadMap)) {
                continue;
            }
            Object output = payloadMap.get("output");
            if (output instanceof Map<?, ?> outputMap) {
                mergeMaps(mergedOutput, (Map<String, Object>) outputMap);
            } else {
                mergeMaps(mergedOutput, (Map<String, Object>) payloadMap);
            }
        }
        return Map.of("output", mergedOutput);
    }

    @SuppressWarnings("unchecked")
    private static void mergeMaps(Map<String, Object> target, Map<String, Object> update) {
        for (Map.Entry<String, Object> entry : update.entrySet()) {
            Object existing = target.get(entry.getKey());
            Object next = entry.getValue();
            if (existing instanceof Map<?, ?> existingMap && next instanceof Map<?, ?> nextMap) {
                mergeMaps((Map<String, Object>) existingMap, (Map<String, Object>) nextMap);
            } else {
                target.put(entry.getKey(), mergeValues(existing, next));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Object mergeValues(Object existing, Object next) {
        if (existing == null) {
            if (next instanceof Map<?, ?> nextMap) {
                return new LinkedHashMap<>((Map<String, Object>) nextMap);
            }
            if (next instanceof List<?> nextList) {
                return new java.util.ArrayList<>(nextList);
            }
            return next;
        }
        if (existing instanceof CharSequence || next instanceof CharSequence) {
            return String.valueOf(existing) + String.valueOf(next);
        }
        if (existing instanceof List<?> existingList) {
            List<Object> merged = new java.util.ArrayList<>(existingList);
            if (next instanceof List<?> nextList) {
                merged.addAll(nextList);
            } else {
                merged.add(next);
            }
            return merged;
        }
        if (existing instanceof Map<?, ?> existingMap && next instanceof Map<?, ?> nextMap) {
            Map<String, Object> merged = new LinkedHashMap<>((Map<String, Object>) existingMap);
            mergeMaps(merged, (Map<String, Object>) nextMap);
            return merged;
        }
        return next;
    }
}
