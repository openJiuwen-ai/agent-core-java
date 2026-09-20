/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.interaction;

/**
 * Raised when a requested human-agent member name does not exist in the current team runtime.
 * 
 * @since 0.1.7
 */
public class UnknownHumanAgentError extends RuntimeException {
    /**
     * UnknownHumanAgentError.
     * 
     * @param message message
     * @since 0.1.7
     */
    public UnknownHumanAgentError(String message) {
        super(message);
    }
}
