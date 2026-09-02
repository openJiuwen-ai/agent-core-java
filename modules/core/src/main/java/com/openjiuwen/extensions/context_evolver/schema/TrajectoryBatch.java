/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.schema.trajectory.TrajectoryBatch}.
 * Batch of trajectories for processing.
 * 
 * @since 0.1.7
 */
public class TrajectoryBatch {
    private List<Trajectory> trajectories = new ArrayList<>();
    private String userId;

    /**
     * HashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * TrajectoryBatch.
     * 
     * @since 0.1.7
     */
    public TrajectoryBatch() {
    }

    /**
     * TrajectoryBatch.
     * 
     * @param trajectories trajectories
     * @param userId userId
     * @since 0.1.7
     */
    public TrajectoryBatch(List<Trajectory> trajectories, String userId) {
        this.trajectories = trajectories != null ? trajectories : new ArrayList<>();
        this.userId = userId;
    }

    // Getters and setters
    /**
     * getTrajectories.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Trajectory> getTrajectories() {
        return trajectories;
    }

    /**
     * setTrajectories.
     * 
     * @param trajectories trajectories
     * @since 0.1.7
     */
    public void setTrajectories(List<Trajectory> trajectories) {
        this.trajectories = trajectories;
    }

    /**
     * getUserId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getUserId() {
        return userId;
    }

    /**
     * setUserId.
     * 
     * @param userId userId
     * @since 0.1.7
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * getMetadata.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * setMetadata.
     * 
     * @param metadata metadata
     * @since 0.1.7
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    /**
     * Get successful trajectories.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Trajectory> getSuccessTrajectories() {
        List<Trajectory> success = new ArrayList<>();
        for (Trajectory t : trajectories) {
            if (t.isSuccess()) {
                success.add(t);
            }
        }
        return success;
    }

    /**
     * Get failed trajectories.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Trajectory> getFailureTrajectories() {
        List<Trajectory> failures = new ArrayList<>();
        for (Trajectory t : trajectories) {
            if (t.isFailure()) {
                failures.add(t);
            }
        }
        return failures;
    }

    /**
     * Count trajectories by feedback type.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<FeedbackType, Integer> countByFeedback() {
        Map<FeedbackType, Integer> counts = new EnumMap<>(FeedbackType.class);
        counts.put(FeedbackType.HELPFUL, 0);
        counts.put(FeedbackType.HARMFUL, 0);
        counts.put(FeedbackType.NEUTRAL, 0);

        for (Trajectory t : trajectories) {
            counts.merge(t.getFeedback(), 1, Integer::sum);
        }

        return counts;
    }

    /**
     * toString.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String toString() {
        Map<FeedbackType, Integer> counts = countByFeedback();
        return String.format(Locale.ROOT, "TrajectoryBatch(user=%s, total=%d, helpful=%d, harmful=%d)", userId,
                trajectories.size(), counts.get(FeedbackType.HELPFUL), counts.get(FeedbackType.HARMFUL));
    }
}
