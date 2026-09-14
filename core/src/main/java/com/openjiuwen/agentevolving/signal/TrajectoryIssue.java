/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.signal;

/**
 * Normalized trajectory issue detected from team execution traces.
 *
 * <p>Mirrors Python's {@code TrajectoryIssue} in
 * {@code openjiuwen/agent_evolving/signal/team.py}.</p>
 */
public record TrajectoryIssue(String issueType, String description, String affectedRole, String severity) {
    public TrajectoryIssue {
        issueType = issueType != null ? issueType : "";
        description = description != null ? description : "";
        affectedRole = affectedRole != null ? affectedRole : "";
        severity = severity != null ? severity : "medium";
    }
}
