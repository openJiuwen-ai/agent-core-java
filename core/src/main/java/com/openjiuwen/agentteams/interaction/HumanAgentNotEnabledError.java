/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.interaction;

/**
 * Raised when human-agent inbox APIs are used on a team without any human-agent members.
 * 
 * @since 0.1.7
 */
public class HumanAgentNotEnabledError extends RuntimeException {
    /**
     * HumanAgentNotEnabledError.
     * 
     * @param message message
     * @since 0.1.7
     */
    public HumanAgentNotEnabledError(String message) {
        super(message);
    }
}
