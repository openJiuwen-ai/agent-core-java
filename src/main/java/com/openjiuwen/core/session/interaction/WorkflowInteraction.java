/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.graph.Vertex;
import com.openjiuwen.core.graph.pregel.GraphInterrupt;
import com.openjiuwen.core.graph.pregel.Interrupt;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.core.session.state.WorkflowStateCollection;
import com.openjiuwen.core.session.state.SessionStateAccess;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.workflow.internal.WorkflowSessionSupport;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Workflow component interaction helper.
 *
 * <p>Mirrors Python's {@code WorkflowInteraction} in
 * {@code openjiuwen/core/session/interaction/interaction.py}.</p>
 */
public class WorkflowInteraction extends BaseInteraction {

    private static final String RECENT_OUTPUTS_KEY = "__workflow_interaction_outputs__";
    private static final String OUTPUT_INDICES_KEY = "__workflow_interaction_output_indices__";
    private static final String INPUT_HISTORY_KEY = "__workflow_interaction_input_history__";

    private final String nodeId;

    public WorkflowInteraction(BaseSession session) {
        super(session, popWorkflowInteractiveInput(session));
        this.nodeId = executableId(session);
        this.interactiveInputs = mergeRememberedInputs(session, nodeId, interactiveInputs);
        if (interactiveInputs != null && !interactiveInputs.isEmpty()) {
            this.latestInteractiveInputs = interactiveInputs.get(interactiveInputs.size() - 1);
        }
    }

    @Override
    public Object waitUserInputs(Object value) {
        Object result = getNextInteractiveInput();
        if (result != null) {
            rememberConsumedInput(result);
            return result;
        }
        if (session != null && session.state() instanceof WorkflowStateCollection stateCollection) {
            stateCollection.commitCmp();
        }
        InteractionOutput payload = new InteractionOutput(nodeId, value);
        OutputSchema output = new OutputSchema(Constant.INTERACTION, nextOutputIndex(), payload);
        writeOutput(output);
        throwGraphInterrupt(output);
        return null;
    }

    @Override
    public Object userLatestInput(Object value) {
        if (isTruthy(latestInteractiveInputs)) {
            Object result = latestInteractiveInputs;
            latestInteractiveInputs = null;
            return result;
        }
        OutputSchema writtenOutput = new OutputSchema(
                Constant.INTERACTION,
                nextOutputIndex(),
                new InteractionOutput(nodeId, value));
        writeOutput(writtenOutput);
        throwGraphInterrupt(writtenOutput);
        return null;
    }

    private static Object popWorkflowInteractiveInput(BaseSession session) {
        if (session == null || !(session.state() instanceof WorkflowCommitState workflowState)) {
            return null;
        }
        Object interactiveInput = workflowState.getWorkflowState(Constant.INTERACTIVE_INPUT);
        if (interactiveInput != null) {
            workflowState.updateAndCommitWorkflowState(singletonWithNull(Constant.INTERACTIVE_INPUT, null));
        }
        return interactiveInput;
    }

    private int nextOutputIndex() {
        return Math.max(index, rememberedOutputIndex(session, nodeId));
    }

    private void rememberConsumedInput(Object value) {
        if (session == null || nodeId == null || session.state() == null) {
            return;
        }
        synchronized (session) {
            SessionStateAccess state = session.state();
            Map<String, Object> history = inputHistory(state.getGlobal(INPUT_HISTORY_KEY));
            ArrayList<Object> values = history.get(nodeId) instanceof Iterable<?> iterable
                    ? iterableToList(iterable)
                    : new ArrayList<>();
            if (!values.contains(value)) {
                values.add(value);
            }
            history.put(nodeId, values);
            state.updateGlobal(Map.of(INPUT_HISTORY_KEY, history));
            if (state instanceof WorkflowCommitState workflowState) {
                workflowState.commit();
            }
        }
    }

    private void writeOutput(OutputSchema output) {
        rememberOutput(output);
        Object writerManager = session == null ? null : session.streamWriterManager();
        if (writerManager == null) {
            return;
        }
        if (writerManager instanceof Vertex.VertexStreamWriterManager vertexWriterManager
                && vertexWriterManager.getOutputWriter() != null) {
            try {
                vertexWriterManager.getOutputWriter().write(output);
            } catch (RuntimeException runtimeException) {
                throw runtimeException;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
            return;
        }
        try {
            Object outputWriter = writerManager.getClass().getMethod("getOutputWriter").invoke(writerManager);
            outputWriter.getClass().getMethod("write", Object.class).invoke(outputWriter, output);
        } catch (NoSuchMethodException | IllegalAccessException ignored) {
            // Streaming is optional in the Python implementation.
        } catch (InvocationTargetException exception) {
            Throwable target = exception.getTargetException();
            if (target instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(target);
        }
    }

    @SuppressWarnings("unchecked")
    private void rememberOutput(OutputSchema output) {
        if (output == null || session == null) {
            return;
        }
        BaseSession current = session;
        while (current != null) {
            try {
                rememberStateOutput(current, output);
                current = current.parent();
            } catch (RuntimeException ignored) {
                return;
            }
        }
    }

    private static void rememberStateOutput(BaseSession targetSession, OutputSchema output) {
        synchronized (targetSession) {
            SessionStateAccess state = targetSession.state();
            if (state != null) {
                Map<String, Object> update = new LinkedHashMap<>();
                ArrayList<Object> globalOutputs = appendOutput(state.getGlobal(RECENT_OUTPUTS_KEY), output);
                update.put(RECENT_OUTPUTS_KEY, globalOutputs);
                update.put(OUTPUT_INDICES_KEY, outputIndices(globalOutputs));
                state.updateGlobal(update);
                if (state instanceof WorkflowCommitState workflowState) {
                    workflowState.commit();
                }
            }
        }
    }

    private static ArrayList<Object> appendOutput(Object existing, OutputSchema output) {
        ArrayList<Object> outputs = new ArrayList<>();
        if (existing instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                outputs.add(item);
            }
        }
        outputs.add(output);
        return outputs;
    }

    private static Map<String, Object> outputIndices(Iterable<?> outputs) {
        Map<String, Set<Object>> valuesById = new LinkedHashMap<>();
        for (Object item : outputs) {
            if (!(item instanceof OutputSchema outputSchema)
                    || !Constant.INTERACTION.equals(outputSchema.getType())
                    || !(outputSchema.getPayload() instanceof InteractionOutput interactionOutput)
                    || interactionOutput.getId() == null) {
                continue;
            }
            valuesById.computeIfAbsent(interactionOutput.getId(), ignored -> new LinkedHashSet<>())
                    .add(interactionOutput.getValue());
        }
        Map<String, Object> indices = new LinkedHashMap<>();
        for (Map.Entry<String, Set<Object>> entry : valuesById.entrySet()) {
            indices.put(entry.getKey(), entry.getValue().size());
        }
        return indices;
    }

    private static ArrayList<Object> mergeRememberedInputs(
            BaseSession session,
            String nodeId,
            java.util.List<Object> currentInputs) {
        ArrayList<Object> merged = new ArrayList<>();
        if (session != null && nodeId != null && session.state() != null) {
            Object existing = session.state().getGlobal(INPUT_HISTORY_KEY);
            Map<String, Object> history = inputHistory(existing);
            if (history.get(nodeId) instanceof Iterable<?> iterable) {
                merged.addAll(iterableToList(iterable));
            }
        }
        if (currentInputs != null) {
            for (Object item : currentInputs) {
                if (!merged.contains(item)) {
                    merged.add(item);
                }
            }
        }
        return merged.isEmpty() ? currentInputs == null ? null : new ArrayList<>(currentInputs) : merged;
    }

    private static Map<String, Object> inputHistory(Object existing) {
        Map<String, Object> history = new LinkedHashMap<>();
        if (existing instanceof Map<?, ?> existingMap) {
            for (Map.Entry<?, ?> entry : existingMap.entrySet()) {
                history.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return history;
    }

    private static ArrayList<Object> iterableToList(Iterable<?> iterable) {
        ArrayList<Object> result = new ArrayList<>();
        for (Object item : iterable) {
            result.add(item);
        }
        return result;
    }

    private static int rememberedOutputIndex(BaseSession session, String nodeId) {
        if (session == null || nodeId == null || session.state() == null) {
            return 0;
        }
        Object existing = session.state().getGlobal(OUTPUT_INDICES_KEY);
        if (!(existing instanceof Map<?, ?> indexMap)) {
            return 0;
        }
        Object value = indexMap.get(nodeId);
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Math.max(0, Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static String executableId(BaseSession session) {
        if (session == null) {
            return "";
        }
        for (String methodName : new String[] {"executableId", "getExecutableId", "nodeId", "getComponentId"}) {
            try {
                Object value = session.getClass().getMethod(methodName).invoke(session);
                if (value != null) {
                    return String.valueOf(value);
                }
            } catch (NoSuchMethodException | IllegalAccessException ignored) {
                // Try the next Python-compatible accessor.
            } catch (InvocationTargetException exception) {
                Throwable target = exception.getTargetException();
                if (target instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException(target);
            }
        }
        return WorkflowSessionSupport.componentId(session);
    }

    private static Map<String, Object> singletonWithNull(String key, Object value) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put(key, value);
        return result;
    }

    private static void throwGraphInterrupt(OutputSchema output) {
        throwUnchecked(new GraphInterrupt(java.util.List.of(new Interrupt(output))));
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void throwUnchecked(Throwable throwable) throws E {
        throw (E) throwable;
    }

    private static boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof CharSequence sequence) {
            return !sequence.isEmpty();
        }
        if (value instanceof java.util.Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }
}
