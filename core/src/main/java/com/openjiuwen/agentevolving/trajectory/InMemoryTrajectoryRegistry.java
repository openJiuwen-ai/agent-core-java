/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trajectory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Mirrors Python's {@code InMemoryTrajectoryRegistry} in
 * {@code openjiuwen/agent_evolving/trajectory/registry.py}.
 */
public final class InMemoryTrajectoryRegistry implements TrajectorySink, TrajectorySource {

    private final Map<SessionKey, Map<String, SnapshotEntry>> snapshots = new LinkedHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private long sequence;

    @Override
    public void publishMemberTrajectory(MemberTrajectorySnapshot snapshot) {
        SessionKey key = new SessionKey(snapshot.getTeamId(), snapshot.getSessionId());
        lock.lock();
        try {
            sequence += 1;
            SnapshotEntry incoming = new SnapshotEntry(snapshot, sequence);
            Map<String, SnapshotEntry> members = snapshots.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
            SnapshotEntry current = members.get(snapshot.getMemberId());
            if (current != null && shouldKeepCurrent(current, incoming)) {
                return;
            }
            members.put(snapshot.getMemberId(), incoming);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Trajectory getTrajectory(String teamId, String sessionId, boolean filterCollaborative) {
        SessionKey key = new SessionKey(teamId, sessionId);
        List<MemberTrajectorySnapshot> currentSnapshots = new ArrayList<>();
        lock.lock();
        try {
            Map<String, SnapshotEntry> members = snapshots.get(key);
            if (members != null) {
                for (SnapshotEntry entry : members.values()) {
                    currentSnapshots.add(entry.snapshot());
                }
            }
        } finally {
            lock.unlock();
        }

        if (currentSnapshots.isEmpty()) {
            return null;
        }

        List<Trajectory> trajectories = new ArrayList<>();
        for (MemberTrajectorySnapshot snapshot : currentSnapshots) {
            trajectories.add(trajectoryForSnapshot(snapshot));
        }
        return TeamTrajectoryAggregator.aggregateMemberTrajectories(
                trajectories,
                teamId,
                sessionId,
                filterCollaborative
        );
    }

    public void clearSession(String teamId, String sessionId) {
        lock.lock();
        try {
            snapshots.remove(new SessionKey(teamId, sessionId));
        } finally {
            lock.unlock();
        }
    }

    public static long nowMs() {
        return System.currentTimeMillis();
    }

    private static Trajectory trajectoryForSnapshot(MemberTrajectorySnapshot snapshot) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (snapshot.getTrajectory().getMeta() != null) {
            meta.putAll(snapshot.getTrajectory().getMeta());
        }
        meta.put("member_id", snapshot.getMemberId());
        if (snapshot.getMemberRole() != null) {
            meta.put("member_role", snapshot.getMemberRole());
        }
        return Trajectory.builder()
                .executionId(snapshot.getTrajectory().getExecutionId())
                .steps(snapshot.getTrajectory().getSteps())
                .source(snapshot.getTrajectory().getSource())
                .caseId(snapshot.getTrajectory().getCaseId())
                .sessionId(snapshot.getTrajectory().getSessionId())
                .traceId(snapshot.getTrajectory().getTraceId())
                .cost(snapshot.getTrajectory().getCost())
                .edges(snapshot.getTrajectory().getEdges())
                .meta(meta)
                .build();
    }

    private static boolean shouldKeepCurrent(SnapshotEntry current, SnapshotEntry incoming) {
        if (incoming.snapshot().getRecordedAtMs() != current.snapshot().getRecordedAtMs()) {
            return current.snapshot().getRecordedAtMs() > incoming.snapshot().getRecordedAtMs();
        }
        return current.sequence() >= incoming.sequence();
    }

    private record SessionKey(String teamId, String sessionId) {
    }

    /**
     * Mirrors Python's {@code _SnapshotEntry} in
     * {@code openjiuwen/agent_evolving/trajectory/registry.py}.
     */
    private record SnapshotEntry(MemberTrajectorySnapshot snapshot, long sequence) {
    }
}
