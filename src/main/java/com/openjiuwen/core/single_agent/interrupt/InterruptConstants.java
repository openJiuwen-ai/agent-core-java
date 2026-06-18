/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.interrupt;

/**
 * Constants used by the tool-interruption resume flow.
 *
 * <p>Mirrors Python's module constants in
 * {@code openjiuwen/core/single_agent/interrupt/state.py}.</p>
 */
public final class InterruptConstants {
    public static final String INTERRUPTION_KEY = "__react_agent_interruption__";
    public static final String RESUME_USER_INPUT_KEY = "_resume_user_input";
    public static final String INTERRUPT_AUTO_CONFIRM_KEY = "__interrupt_auto_confirm__";
    public static final String RESUME_START_ITERATION_KEY = "_resume_start_iteration";
    public static final String INTERACTION = "interaction";

    private InterruptConstants() {
    }
}
