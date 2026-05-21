/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.interrupt;

/**
 * Constants for interruption handling.
 *
 * <p>Mirrors Python's constants in {@code openjiuwen.core.single_agent.interrupt.state}.</p>
 */
public final class InterruptConstants {

    /** Key for storing interruption state in session. */
    public static final String INTERRUPTION_KEY = "__react_agent_interruption__";

    /** Key for resume user input in context extra. */
    public static final String RESUME_USER_INPUT_KEY = "_resume_user_input";

    /** Key for auto-confirm configuration in session state. */
    public static final String INTERRUPT_AUTO_CONFIRM_KEY = "__interrupt_auto_confirm__";

    /** Key for resume start iteration in context extra. */
    public static final String RESUME_START_ITERATION_KEY = "_resume_start_iteration";

    /** Interaction type constant. */
    public static final String INTERACTION = "interaction";

    private InterruptConstants() {
        // Prevent instantiation
    }
}