/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.rail;

/**
 * Class-based rail with lifecycle hooks.
 *
 * <p>Mirrors Python's {@code AgentRail} in
 * {@code openjiuwen.core.single_agent.rail.base}.</p>
 */
public abstract class AgentRail {

    /** Reference to the agent (set by init). */
    protected Object agent;

    /**
     * Initialize the rail with agent reference.
     *
     * @param agent the agent instance
     */
    public void init(Object agent) {
        this.agent = agent;
    }

    /**
     * Uninitialize the rail.
     *
     * @param agent the agent instance
     */
    public void uninit(Object agent) {
        this.agent = null;
    }

    /**
     * Before invoke hook.
     *
     * @param ctx callback context
     */
    public void beforeInvoke(AgentCallbackContext ctx) {
        // Default: no action
    }

    /**
     * After invoke hook.
     *
     * @param ctx callback context
     */
    public void afterInvoke(AgentCallbackContext ctx) {
        // Default: no action
    }

    /**
     * Before model call hook.
     *
     * @param ctx callback context
     */
    public void beforeModelCall(AgentCallbackContext ctx) {
        // Default: no action
    }

    /**
     * After model call hook.
     *
     * @param ctx callback context
     */
    public void afterModelCall(AgentCallbackContext ctx) {
        // Default: no action
    }

    /**
     * On model exception hook.
     *
     * @param ctx callback context
     */
    public void onModelException(AgentCallbackContext ctx) {
        // Default: no action
    }

    /**
     * Before tool call hook.
     *
     * @param ctx callback context
     */
    public void beforeToolCall(AgentCallbackContext ctx) {
        // Default: no action
    }

    /**
     * After tool call hook.
     *
     * @param ctx callback context
     */
    public void afterToolCall(AgentCallbackContext ctx) {
        // Default: no action
    }

    /**
     * On tool exception hook.
     *
     * @param ctx callback context
     */
    public void onToolException(AgentCallbackContext ctx) {
        // Default: no action
    }
}