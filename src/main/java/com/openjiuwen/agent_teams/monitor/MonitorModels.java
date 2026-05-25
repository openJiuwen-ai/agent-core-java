/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.monitor;

import java.util.*;

/**
 * Monitor models for agent teams.
 * <p>
 * Provides data structures for monitoring team execution,
 * including member status, message counts, and performance metrics.
 * <p>
 * Mirrors Python's {@code models.py} in
 * {@code openjiuwen.agent_teams.monitor.models}.
 */
public class MonitorModels {
    
    /**
     * Member status record.
     */
    public static class MemberStatus {
        private String memberName;
        private String status;  // idle, running, waiting, error
        private int messageCount;
        private int pendingTasks;
        private long lastActivityTime;
        
        public MemberStatus(String memberName) {
            this.memberName = memberName;
            this.status = "idle";
            this.messageCount = 0;
            this.pendingTasks = 0;
            this.lastActivityTime = System.currentTimeMillis();
        }
        
        // Getters and setters
        public String getMemberName() { return memberName; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getMessageCount() { return messageCount; }
        public void setMessageCount(int count) { this.messageCount = count; }
        public int getPendingTasks() { return pendingTasks; }
        public void setPendingTasks(int tasks) { this.pendingTasks = tasks; }
        public long getLastActivityTime() { return lastActivityTime; }
        public void setLastActivityTime(long time) { this.lastActivityTime = time; }
    }
    
    /**
     * Team monitor summary.
     */
    public static class TeamMonitorSummary {
        private String teamName;
        private int totalMembers;
        private int activeMembers;
        private int totalMessages;
        private int pendingTasks;
        private double avgResponseTime;
        private List<MemberStatus> memberStatuses;
        private long timestamp;
        
        public TeamMonitorSummary(String teamName) {
            this.teamName = teamName;
            this.memberStatuses = new ArrayList<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        // Getters and setters
        public String getTeamName() { return teamName; }
        public int getTotalMembers() { return totalMembers; }
        public void setTotalMembers(int count) { this.totalMembers = count; }
        public int getActiveMembers() { return activeMembers; }
        public void setActiveMembers(int count) { this.activeMembers = count; }
        public int getTotalMessages() { return totalMessages; }
        public void setTotalMessages(int count) { this.totalMessages = count; }
        public int getPendingTasks() { return pendingTasks; }
        public void setPendingTasks(int tasks) { this.pendingTasks = tasks; }
        public double getAvgResponseTime() { return avgResponseTime; }
        public void setAvgResponseTime(double time) { this.avgResponseTime = time; }
        public List<MemberStatus> getMemberStatuses() { return memberStatuses; }
        public void addMemberStatus(MemberStatus status) { memberStatuses.add(status); }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Message record for monitoring.
     */
    public static class MessageRecord {
        private String messageId;
        private String sender;
        private String receiver;
        private String messageType;
        private long timestamp;
        
        public MessageRecord(String messageId, String sender, String receiver, String messageType) {
            this.messageId = messageId;
            this.sender = sender;
            this.receiver = receiver;
            this.messageType = messageType;
            this.timestamp = System.currentTimeMillis();
        }
        
        // Getters
        public String getMessageId() { return messageId; }
        public String getSender() { return sender; }
        public String getReceiver() { return receiver; }
        public String getMessageType() { return messageType; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Performance metrics for team execution.
     */
    public static class PerformanceMetrics {
        private int totalExecutions;
        private int successfulExecutions;
        private int failedExecutions;
        private double avgExecutionTime;
        private double maxExecutionTime;
        private double minExecutionTime;
        
        public PerformanceMetrics() {
            this.minExecutionTime = Double.MAX_VALUE;
        }
        
        public void recordExecution(double time, boolean success) {
            totalExecutions++;
            if (success) {
                successfulExecutions++;
            } else {
                failedExecutions++;
            }
            
            avgExecutionTime = (avgExecutionTime * (totalExecutions - 1) + time) / totalExecutions;
            maxExecutionTime = Math.max(maxExecutionTime, time);
            minExecutionTime = Math.min(minExecutionTime, time);
        }
        
        // Getters
        public int getTotalExecutions() { return totalExecutions; }
        public int getSuccessfulExecutions() { return successfulExecutions; }
        public int getFailedExecutions() { return failedExecutions; }
        public double getAvgExecutionTime() { return avgExecutionTime; }
        public double getMaxExecutionTime() { return maxExecutionTime; }
        public double getMinExecutionTime() { return minExecutionTime == Double.MAX_VALUE ? 0 : minExecutionTime; }
    }
}