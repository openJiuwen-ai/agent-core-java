/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving;

import java.util.Set;

/**
 * Mirrors Python's {@code openjiuwen.agent_evolving.protocols} in
 * {@code openjiuwen/agent_evolving/protocols.py}.
 */
public final class Protocols {
    public static final String APPROVE_ACTION = "approve";
    public static final String APPEND_MODE = "append";
    public static final String CONVERSATION_REVIEW_SIGNAL = "conversation_review";
    public static final String EXECUTION_FAILURE_SIGNAL = "execution_failure";
    public static final String EXPERIENCES_TARGET = "experiences";
    public static final String EXPERIENCE_ENTRY = "experience_entry";
    public static final String LOCAL_APPLY_COMPLETED = "local_apply_completed";
    public static final String MERGE_MODE = "merge";
    public static final String PENDING_CHANGE_EFFECT = "pending_change";
    public static final String REJECT_ACTION = "reject";
    public static final String REPLACE_MODE = "replace";
    public static final String RETRY_ACTION = "retry";
    public static final String SKILL_EXPERIENCE_ENTRY = "skill_experience_entry";
    public static final String STATE_EFFECT = "state";
    public static final String TOOL_FAILURE_SIGNAL = "tool_failure";
    public static final String TRAJECTORY_ISSUE_SIGNAL = "trajectory_issue";
    public static final String USER_INTENT_SIGNAL = "user_intent";

    public static final Set<String> VALID_PATCH_ACTIONS = Set.of("append", "merge", "replace", "skip");
    public static final Set<String> VALID_SECTIONS = Set.of(
            "Instructions",
            "Examples",
            "Troubleshooting",
            "Scripts",
            "Collaboration",
            "Roles",
            "Constraints",
            "Workflow"
    );

    private Protocols() {
    }
}
