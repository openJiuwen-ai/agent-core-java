/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trajectory;

import java.util.Objects;

/**
 * Mirrors Python's {@code MemberTrajectorySnapshot} in
 * {@code openjiuwen/agent_evolving/trajectory/registry.py}.
 */
public final class MemberTrajectorySnapshot {

    private final String teamId;
    private final String sessionId;
    private final String memberId;
    private final String memberRole;
    private final Trajectory trajectory;
    private final long recordedAtMs;

    public MemberTrajectorySnapshot(String teamId,
                                    String sessionId,
                                    String memberId,
                                    String memberRole,
                                    Trajectory trajectory,
                                    long recordedAtMs) {
        this.teamId = Objects.requireNonNull(teamId, "teamId must not be null");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        this.memberId = Objects.requireNonNull(memberId, "memberId must not be null");
        this.memberRole = memberRole;
        this.trajectory = Objects.requireNonNull(trajectory, "trajectory must not be null");
        this.recordedAtMs = recordedAtMs;
    }

    public static MemberTrajectorySnapshot make(String teamId,
                                                String memberId,
                                                Trajectory trajectory,
                                                String memberRole,
                                                String sessionId,
                                                Long recordedAtMs) {
        String resolvedSessionId = sessionId != null
                ? sessionId
                : trajectory.getSessionId() != null ? trajectory.getSessionId() : "";
        long resolvedRecordedAtMs = recordedAtMs != null
                ? recordedAtMs
                : InMemoryTrajectoryRegistry.nowMs();
        return new MemberTrajectorySnapshot(
                teamId,
                resolvedSessionId,
                memberId,
                memberRole,
                trajectory,
                resolvedRecordedAtMs
        );
    }

    public String getTeamId() {
        return teamId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getMemberId() {
        return memberId;
    }

    public String getMemberRole() {
        return memberRole;
    }

    public Trajectory getTrajectory() {
        return trajectory;
    }

    public long getRecordedAtMs() {
        return recordedAtMs;
    }
}
