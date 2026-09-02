/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent.coordination.handlers;

import com.openjiuwen.agentteams.agent.coordination.AgentRoundController;
import com.openjiuwen.agentteams.agent.coordination.DispatcherHost;
import com.openjiuwen.agentteams.agent.coordination.EventCallback;
import com.openjiuwen.agentteams.agent.coordination.PollController;
import com.openjiuwen.agentteams.agent.coordination.TeamAgentBlueprint;
import com.openjiuwen.agentteams.agent.coordination.TeamInfra;
import com.openjiuwen.agentteams.agent.coordination.TeamLifecycleController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base class for scenario-scoped coordination event handlers.
 *
 * <p>Mirrors Python {@code handlers/base.py:BaseCoordinationHandler}. Each
 * subclass declares its event bindings by populating {@link #callbacks} in its
 * constructor (event key → bound method), and the base class exposes
 * {@link #getCallbacks()} for framework registration. Subclasses receive
 * references to the round controller, lifecycle controller, poll controller,
 * blueprint, and infra — they never reach back into the agent host.
 *
 * <p>Iron rule: handlers do not hold the original host reference. The host
 * satisfies both {@link AgentRoundController} and
 * {@link TeamLifecycleController}; aliasing under narrower protocol-typed
 * fields documents which surface each call site actually depends on.
 *
 * @since 2026/7/9
 */
public abstract class BaseCoordinationHandler {
    /** Event key -&gt; bound method callback. Subclasses populate in constructor. */
    protected final Map<String, EventCallback> callbacks = new LinkedHashMap<>();

    /** Round-level control surface. */
    protected final AgentRoundController round;

    /** Team-level lifecycle effects. */
    protected final TeamLifecycleController lifecycle;

    /** Poll timer control. */
    protected final PollController poll;

    /** Blueprint-level config. */
    protected final TeamAgentBlueprint blueprint;

    /** Infra-level services. */
    protected final TeamInfra infra;

    /**
     * Construct the base handler.
     *
     * <p>The {@code host} satisfies both {@link AgentRoundController} and
     * {@link TeamLifecycleController} (it is the owning {@code TeamAgent});
     * {@code pollCtrl} is the coordination event bus. Aliasing under narrower
     * protocol-typed fields documents which surface each call site depends on
     * — handlers must not reach for {@code host} directly.
     *
     * @param host the owning TeamAgent as DispatcherHost
     * @param blueprint static config holder
     * @param infra per-process infrastructure services
     * @param pollCtrl the coordination event bus as poll controller
     */
    protected BaseCoordinationHandler(DispatcherHost host, TeamAgentBlueprint blueprint,
                                      TeamInfra infra, PollController pollCtrl) {
        this.round = host;
        this.lifecycle = host;
        this.poll = pollCtrl;
        this.blueprint = blueprint;
        this.infra = infra;
    }

    /**
     * Return the event key -&gt; callback map for framework registration.
     *
     * @return unmodifiable view of the callbacks registered by this handler
     */
    public Map<String, EventCallback> getCallbacks() {
        return Map.copyOf(callbacks);
    }

    /**
     * Extract a string payload field, returning empty string when absent.
     *
     * <p>Returns raw {@code String} rather than {@code Optional<String>} because
     * callers compare against known constants or pass the value to other
     * methods that accept {@code null}. The blueprint/handler layer treats
     * missing payload fields as a normal case, not as optional data.
     *
     * @param payload the event payload map
     * @param key the field key
     * @return the string value, or empty string when the field is absent
     */
    protected static String str(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        return v != null ? String.valueOf(v) : "";
    }

    /**
     * Extract a boolean payload field, returning {@code false} when absent or falsy.
     *
     * @param payload the event payload map
     * @param key the field key
     * @return the boolean value
     */
    protected static boolean bool(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        if (v instanceof Boolean isBool) {
            return isBool;
        }
        return v != null && Boolean.parseBoolean(String.valueOf(v));
    }
}
