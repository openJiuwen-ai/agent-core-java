/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import java.util.Objects;
import java.util.Optional;

/**
 * Signal that pauses the handoff chain and persists state for later resumption.
 *
 * <p>Mirrors Python's {@code TeamInterruptSignal} in
 * {@code openjiuwen/core/multi_agent/teams/handoff/interrupt.py}.</p>
 */
public final class TeamInterruptSignal {

    private final Object result;
    private final String message;

    public TeamInterruptSignal(Object result) {
        this(result, null);
    }

    public TeamInterruptSignal(Object result, String message) {
        this.result = result;
        this.message = message;
    }

    public Object getResult() {
        return result;
    }

    public Optional<String> getMessage() {
        return Optional.ofNullable(message);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TeamInterruptSignal that)) {
            return false;
        }
        return Objects.equals(result, that.result) && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(result, message);
    }
}
