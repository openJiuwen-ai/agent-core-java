/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.graph.Vertex;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.workflow.HasDrawable;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowChunk;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sub-workflow component implementation exposing Python's {@code sub_workflow}.
 *
 * <p>Mirrors Python's {@code SubWorkflowComponent} in
 * {@code openjiuwen/core/workflow/components/flow/workflow_comp.py}.</p>
 */
public class SubWorkflowComponentImpl extends WorkflowComponent implements SubWorkflowComponent {

    public static final String SUB_WORKFLOW_COMPONENT = "sub_workflow";

    private final Workflow subWorkflow;
    private final boolean cacheStream;
    private final SubWorkflowStreamState streamState = new SubWorkflowStreamState();

    public SubWorkflowComponentImpl(Workflow subWorkflow) {
        this(subWorkflow, false);
    }

    public SubWorkflowComponentImpl(Workflow subWorkflow, boolean cacheStream) {
        if (subWorkflow == null) {
            throw new IllegalArgumentException("sub_workflow is None");
        }
        this.subWorkflow = subWorkflow;
        this.cacheStream = cacheStream;
    }

    @Override
    public Workflow getSubWorkflow() {
        return subWorkflow;
    }

    @Override
    public HasDrawable getSubWorkflowInternal() {
        return subWorkflow.getInternalDrawable();
    }

    @Override
    public boolean isCacheStream() {
        return cacheStream;
    }

    public SubWorkflowStreamState getStreamState() {
        return streamState;
    }

    public Map<String, Object> getStreamOutput() {
        if (!cacheStream) {
            return null;
        }
        return toStringObjectMap(streamState.buildFinalResult());
    }

    public String componentType() {
        return SUB_WORKFLOW_COMPONENT;
    }

    @Override
    public boolean graphInvoker() {
        return true;
    }

    @Override
    public Executable<?, ?> toExecutable() {
        return new SubWorkflowExecutable();
    }

    private final class SubWorkflowExecutable extends Executable<Map<String, Object>, Map<String, Object>>
            implements Vertex.StreamOutputProvider {

        @Override
        public Map<String, Object> onInvoke(Map<String, Object> inputs, BaseSession session, Object... kwargs) {
            if (cacheStream) {
                Map<String, Object> cached = getStreamOutput();
                if (cached != null) {
                    return Map.of("output", cached);
                }
            }
            Object result = subWorkflow.invokeSubWorkflow(
                    workflowInputs(inputs), session, extractContext(kwargs), workflowConfig(inputs));
            return normalizeMapOutput(result);
        }

        @Override
        public Iterator<Map<String, Object>> onStream(
                Map<String, Object> inputs,
                BaseSession session,
                Object... kwargs) {
            Iterator<WorkflowChunk> chunks = subWorkflow.streamSubWorkflow(
                    workflowInputs(inputs), session, extractContext(kwargs), workflowConfig(inputs));
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return chunks.hasNext();
                }

                @Override
                public Map<String, Object> next() {
                    Map<String, Object> frame = streamFrame(chunks.next());
                    if (cacheStream) {
                        streamState.accumulate(frame);
                    }
                    return frame;
                }
            };
        }

        @Override
        public boolean graphInvoker() {
            return SubWorkflowComponentImpl.this.graphInvoker();
        }

        @Override
        public String componentType() {
            return SubWorkflowComponentImpl.this.componentType();
        }

        @Override
        public Map<String, Object> getStreamOutput() {
            return SubWorkflowComponentImpl.this.getStreamOutput();
        }
    }

    private static ModelContext extractContext(Object... kwargs) {
        if (kwargs == null) {
            return null;
        }
        for (Object item : kwargs) {
            if (item instanceof ModelContext modelContext) {
                return modelContext;
            }
        }
        return null;
    }

    private static Object workflowInputs(Map<String, Object> inputs) {
        return inputs == null ? null : inputs.get(Constant.INPUTS_KEY);
    }

    private static Object workflowConfig(Map<String, Object> inputs) {
        return inputs == null ? null : inputs.get(Constant.CONFIG_KEY);
    }

    private static Map<String, Object> streamFrame(WorkflowChunk chunk) {
        Object payload = chunk.getPayload();
        if (payload instanceof Map<?, ?> payloadMap) {
            return toStringObjectMap(payloadMap);
        }
        return Map.of("output", payload);
    }

    private static Map<String, Object> normalizeMapOutput(Object value) {
        if (value instanceof Map<?, ?> valueMap) {
            return toStringObjectMap(valueMap);
        }
        if (value == null) {
            return null;
        }
        return Map.of("output", value);
    }

    private static Map<String, Object> toStringObjectMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }
}
