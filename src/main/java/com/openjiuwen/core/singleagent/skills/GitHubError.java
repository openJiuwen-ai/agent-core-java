/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

/**
 * Exception thrown when GitHub API operations fail.
 * <p>
 * Mirrors Python's {@code GitHubError} in {@code single_agent/skills/remote_skill_util.py}.
 * </p>
 * 
 * @since 0.1.7
 */
public class GitHubError extends RuntimeException {
    /**
     * GitHubError.
     * 
     * @param message message
     * @since 0.1.7
     */
    public GitHubError(String message) {
        super(message);
    }

    /**
     * GitHubError.
     * 
     * @param message message
     * @param cause cause
     * @since 0.1.7
     */
    public GitHubError(String message, Throwable cause) {
        super(message, cause);
    }
}
