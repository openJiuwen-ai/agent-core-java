/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

/**
 * Exception thrown when GitHub API operations fail.
 *
 * <p>Mirrors Python's {@code GitHubError} in {@code single_agent/skills/remote_skill_util.py}.</p>
 */
public class GitHubError extends RuntimeException {

    public GitHubError(String message) {
        super(message);
    }

    public GitHubError(String message, Throwable cause) {
        super(message, cause);
    }
}
