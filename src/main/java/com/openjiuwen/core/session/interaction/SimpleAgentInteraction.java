/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.Checkpointer;

import java.lang.reflect.InvocationTargetException;

/**
 * Simple agent interaction helper.
 *
 * <p>Mirrors Python's {@code SimpleAgentInteraction} in
 * {@code openjiuwen/core/session/interaction/interaction.py}.</p>
 */
public class SimpleAgentInteraction {

    private final BaseSession session;

    public SimpleAgentInteraction(BaseSession session) {
        this.session = session;
    }

    public Object waitUserInputs(Object value) {
        interruptAgentExecute(session);
        throw new AgentInterrupt(value == null ? null : String.valueOf(value));
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
}
