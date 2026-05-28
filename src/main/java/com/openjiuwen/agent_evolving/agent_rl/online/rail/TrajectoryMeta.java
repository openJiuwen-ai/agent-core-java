// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import java.util.*;

/**
 * Metadata for a trajectory.
 * <p>
 * Mirrors Python's {@code TrajectoryMeta} dataclass in converter.py.
 */
public class TrajectoryMeta {
    
    private final String trajectoryId;
    private final String sessionId;
    private final String status;
    private final int totalTurns;
    private final double startedAt;
    private final double endedAt;
    private final Map<String, Object> extra;
    
    public TrajectoryMeta(String trajectoryId, String sessionId, String status,
                          int totalTurns, double startedAt, double endedAt,
                          Map<String, Object> extra) {
        this.trajectoryId = trajectoryId;
        this.sessionId = sessionId;
        this.status = status != null ? status : "ok";
        this.totalTurns = totalTurns;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.extra = extra != null ? extra : new LinkedHashMap<>();
    }
    
    public TrajectoryMeta(String trajectoryId, String sessionId) {
        this(trajectoryId, sessionId, "ok", 0, System.currentTimeMillis() / 1000.0, 
             System.currentTimeMillis() / 1000.0, new LinkedHashMap<>());
    }
    
    // Getters
    public String getTrajectoryId() { return trajectoryId; }
    public String getSessionId() { return sessionId; }
    public String getStatus() { return status; }
    public int getTotalTurns() { return totalTurns; }
    public double getStartedAt() { return startedAt; }
    public double getEndedAt() { return endedAt; }
    public Map<String, Object> getExtra() { return extra; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private String trajectoryId;
        private String sessionId;
        private String status = "ok";
        private int totalTurns = 0;
        private double startedAt = System.currentTimeMillis() / 1000.0;
        private double endedAt = System.currentTimeMillis() / 1000.0;
        private Map<String, Object> extra = new LinkedHashMap<>();
        
        public Builder trajectoryId(String trajectoryId) { this.trajectoryId = trajectoryId; return this; }
        public Builder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder totalTurns(int totalTurns) { this.totalTurns = totalTurns; return this; }
        public Builder startedAt(double startedAt) { this.startedAt = startedAt; return this; }
        public Builder endedAt(double endedAt) { this.endedAt = endedAt; return this; }
        public Builder extra(Map<String, Object> extra) { this.extra = extra; return this; }
        
        public TrajectoryMeta build() {
            return new TrajectoryMeta(trajectoryId, sessionId, status, totalTurns, startedAt, endedAt, extra);
        }
    }
}