/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.interaction.AgentInterrupt;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Interrupt helpers for HandoffTeam.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.multi_agent.teams.handoff.interrupt}.
 */
public final class Interrupt {

    private Interrupt() {
    }

    /**
     * Extract an interrupt signal from an agent result.
     *
     * @param result agent result
     * @return interrupt signal when detected
     */
    public static Optional<TeamInterruptSignal> extractInterruptSignal(Object result) {
        return extractInterruptSignal(result, null);
    }

    /**
     * Extract an interrupt signal from an agent result or exception.
     *
     * @param result agent result
     * @param exc exception raised by the agent
     * @return interrupt signal when detected
     */
    public static Optional<TeamInterruptSignal> extractInterruptSignal(Object result, Throwable exc) {
        if (result instanceof Map<?, ?> map && "interrupt".equals(map.get("result_type"))) {
            return Optional.of(new TeamInterruptSignal(result));
        }
        if (exc instanceof AgentInterrupt agentInterrupt) {
            String message = agentInterrupt.getMessage();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("result_type", "interrupt");
            payload.put("message", message);
            return Optional.of(new TeamInterruptSignal(payload, message));
        }
        return Optional.empty();
    }

    /**
     * Best-effort flush of a team session after interrupt.
     *
     * @param session session to flush
     */
    public static void flushTeamSession(Session session) {
        if (session == null) {
            return;
        }
        try {
            Method postRun = session.getClass().getDeclaredMethod("postRun");
            postRun.setAccessible(true);
            postRun.invoke(session);
        } catch (Exception ignored) {
            // Python intentionally logs and suppresses flush failures; Java keeps the same no-throw contract.
        }
    }
}
