// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.extensions.context_evolver.schema;

import java.util.*;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.schema.trajectory.TrajectoryBatch}.
 * 
 * Batch of trajectories for processing.
 */
public class TrajectoryBatch {
    private List<Trajectory> trajectories = new ArrayList<>();
    private String userId;
    private Map<String, Object> metadata = new HashMap<>();
    
    public TrajectoryBatch() {}
    
    public TrajectoryBatch(List<Trajectory> trajectories, String userId) {
        this.trajectories = trajectories != null ? trajectories : new ArrayList<>();
        this.userId = userId;
    }
    
    // Getters and setters
    public List<Trajectory> getTrajectories() { return trajectories; }
    public void setTrajectories(List<Trajectory> trajectories) { this.trajectories = trajectories; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    
    /**
     * Get successful trajectories.
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
    
    @Override
    public String toString() {
        Map<FeedbackType, Integer> counts = countByFeedback();
        return String.format("TrajectoryBatch(user=%s, total=%d, helpful=%d, harmful=%d)",
            userId, trajectories.size(), 
            counts.get(FeedbackType.HELPFUL), 
            counts.get(FeedbackType.HARMFUL));
    }
}