/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trajectory;

import java.util.List;
import java.util.Map;

/**
 * Trajectory persistence interface.
 * <p>
 * Provides interface for saving, loading, and querying trajectory data with optional version
 * isolation.
 * </p>
 * <p>
 * Mirrors Python's {@code TrajectoryStore} in
 * {@code openjiuwen/agent_evolving/trajectory/store.py}.
 * </p>
 */
public interface TrajectoryStore {

    /**
     * Save trajectory. Version is used for experiment isolation.
     *
     * @param trajectory trajectory to save
     * @param version optional version identifier
     */
    void save(Trajectory trajectory, String version);

    /**
     * Load a specific trajectory.
     *
     * @param executionId execution ID of the trajectory
     * @param version optional version identifier
     * @return loaded trajectory or {@code null} if not found
     */
    Trajectory load(String executionId, String version);

    /**
     * Query trajectories by session ID.
     *
     * @param sessionId session ID to query
     * @return list of matching trajectories
     */
    List<Trajectory> queryBySessionId(String sessionId);

    /**
     * Query trajectories using Python-style field filters.
     *
     * @param version optional version filter
     * @param filters optional field filters such as {@code session_id}, {@code case_id}, and
     *                {@code source}
     * @return list of matching trajectories
     */
    List<Trajectory> query(String version, Map<String, Object> filters);

    /**
     * Query trajectories with optional filters.
     *
     * @param sessionId optional session ID filter
     * @param executionId optional execution ID filter
     * @param version optional version filter
     * @return list of matching trajectories
     */
    List<Trajectory> query(String sessionId, String executionId, String version);
}
