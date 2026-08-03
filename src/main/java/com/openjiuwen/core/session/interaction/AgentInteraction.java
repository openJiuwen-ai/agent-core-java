/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.stream.OutputSchema;

import java.lang.reflect.InvocationTargetException;

/**
 * Agent interaction facade.
 *
 * <p>Mirrors Python's {@code AgentInteraction} in
 * {@code openjiuwen/core/session/interaction/interaction.py}.</p>
 */
public class AgentInteraction extends BaseInteraction {

    public AgentInteraction(BaseSession session) {
        super(session);
    }

    @Override
    public Object waitUserInputs(Object value) {
        Object inputs = getNextInteractiveInput();
        if (inputs != null) {
            return inputs;
        }
        interruptAgentExecute(session);
        writeOutput(new OutputSchema(
                Constant.INTERACTION,
                index,
                new InteractionOutput(executableId(session), value)));
        throw new IllegalArgumentException(
                "AgentInterrupt.__init__() missing 1 required positional argument: 'message'");
    }

    private static void interruptAgentExecute(BaseSession session) {
        if (session == null || session.checkpointer() == null) {
            return;
        }
        Object checkpointer = session.checkpointer();
        if (checkpointer instanceof Checkpointer typedCheckpointer) {
            typedCheckpointer.interruptAgentExecute(session);
            return;
        }
        try {
            checkpointer.getClass()
                    .getMethod("interruptAgentExecute", BaseSession.class)
                    .invoke(checkpointer, session);
        } catch (NoSuchMethodException | IllegalAccessException ignored) {
            // Missing checkpointer hooks must not mask the interaction interrupt itself.
        } catch (InvocationTargetException exception) {
            Throwable target = exception.getTargetException();
            if (target instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(target);
        }
    }

    private void writeOutput(OutputSchema output) {
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
        return "";
    }
}
