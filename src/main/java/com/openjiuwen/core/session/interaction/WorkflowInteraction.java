/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.common.constants.Constant;
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
import java.util.Map;

/**
 * Workflow component interaction helper.
 *
 * <p>Mirrors Python's {@code WorkflowInteraction} in
 * {@code openjiuwen/core/session/interaction/interaction.py}.</p>
 */
public class WorkflowInteraction extends BaseInteraction {

    private static final String RECENT_OUTPUTS_KEY = "__workflow_interaction_outputs__";

    private final String nodeId;

    public WorkflowInteraction(BaseSession session) {
        super(session, popWorkflowInteractiveInput(session));
        this.nodeId = executableId(session);
    }

    @Override
    public Object waitUserInputs(Object value) {
        Object result = getNextInteractiveInput();
        if (result != null) {
            return result;
        }
        if (session != null && session.state() instanceof WorkflowStateCollection stateCollection) {
            stateCollection.commitCmp();
        }
        InteractionOutput payload = new InteractionOutput(nodeId, value);
        OutputSchema output = new OutputSchema(Constant.INTERACTION, index, payload);
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
        OutputSchema writtenOutput = new OutputSchema(Constant.INTERACTION, index, new InteractionOutput(nodeId, value));
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

    private void writeOutput(OutputSchema output) {
        rememberOutput(output);
        Object writerManager = session == null ? null : session.streamWriterManager();
        if (writerManager == null) {
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
                ArrayList<Object> globalOutputs = appendOutput(state.getGlobal(RECENT_OUTPUTS_KEY), output);
                state.updateGlobal(Map.of(RECENT_OUTPUTS_KEY, globalOutputs));
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
