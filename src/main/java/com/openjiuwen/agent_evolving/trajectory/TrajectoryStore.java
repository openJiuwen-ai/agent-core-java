/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

import java.util.List;

/**
 * Trajectory persistence interface.
 * <p>
 * Provides interface for saving/loading/querying trajectory data
 * with optional version isolation.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.trajectory.store.TrajectoryStore}.
 */
public interface TrajectoryStore {

    /**
     * Save trajectory. Version is used for experiment isolation.
     *
     * @param trajectory Trajectory to save
     * @param version Optional version identifier
     */
    void save(Trajectory trajectory, String version);

    /**
     * Load a specific trajectory.
     *
     * @param executionId Execution ID of the trajectory
     * @param version Optional version identifier
     * @return Loaded Trajectory or null if not found
     */
    Trajectory load(String executionId, String version);

    /**
     * Query trajectories by session ID.
     *
     * @param sessionId Session ID to query
     * @return List of matching trajectories
     */
    List<Trajectory> queryBySessionId(String sessionId);

    /**
     * Query trajectories with optional filters.
     *
     * @param sessionId Optional session ID filter
     * @param executionId Optional execution ID filter
     * @param version Optional version filter
     * @return List of matching trajectories
     */
    List<Trajectory> query(String sessionId, String executionId, String version);
}