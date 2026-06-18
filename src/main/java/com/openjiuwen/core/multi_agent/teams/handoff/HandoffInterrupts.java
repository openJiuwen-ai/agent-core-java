/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.teams.handoff;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.session.AgentTeamSession;
import com.openjiuwen.core.session.interaction.AgentInterrupt;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Utility methods for handoff-team interrupt signals.
 *
 * <p>Mirrors Python's module functions in
 * {@code openjiuwen/core/multi_agent/teams/handoff/interrupt.py}.</p>
 */
public final class HandoffInterrupts {

    private HandoffInterrupts() {
    }

    public static Optional<TeamInterruptSignal> extractInterruptSignal(Object result) {
        return extractInterruptSignal(result, null);
    }

    public static Optional<TeamInterruptSignal> extractInterruptSignal(Object result, Throwable exception) {
        if (result instanceof Map<?, ?> rawMap && "interrupt".equals(rawMap.get("result_type"))) {
            return Optional.of(new TeamInterruptSignal(stringObjectMap(rawMap)));
        }
        if (exception instanceof AgentInterrupt interrupt) {
            String message = interrupt.message != null ? interrupt.message : String.valueOf(interrupt);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("result_type", "interrupt");
            payload.put("message", message);
            return Optional.of(new TeamInterruptSignal(payload, message));
        }
        return Optional.empty();
    }

    public static void flushTeamSession(AgentTeamSession session) {
        if (session == null) {
            return;
        }
        try {
            session.closeStream();
            session.commit();
        } catch (RuntimeException exception) {
            Loggers.MULTI_AGENT.warning(
                    "[flush_team_session] checkpointer flush failed after interrupt; "
                            + "interrupt state may not be persisted: {}",
                    exception.toString()
            );
        }
    }

    private static Map<String, Object> stringObjectMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}
