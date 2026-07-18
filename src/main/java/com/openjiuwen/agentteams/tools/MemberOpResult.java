/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools;

import lombok.Value;

/**
 * Outcome of a member mutation with the Python MemberOpResult-style failure
 * reason.
 *
 * <p>Mirrors Python {@code schema/team.py:MemberOpResult}. Carries a boolean
 * {@code ok} flag plus a human-readable {@code reason} so leader-facing call
 * sites (tools, agent loops) can surface failure context to the LLM rather
 * than a bare {@code false}.</p>
 *
 * @since 2026/7/9
 */
@Value(staticConstructor = "of")
public class MemberOpResult {
    boolean isOk;
    String reason;

    /**
     * Create a success result.
     *
     * @return a success MemberOpResult
     */
    public static MemberOpResult success() {
        return of(true, "");
    }

    /**
     * Create a failure result.
     *
     * @param reason the failure reason
     * @return a failure MemberOpResult
     */
    public static MemberOpResult fail(String reason) {
        return of(false, reason != null ? reason : "");
    }
}
