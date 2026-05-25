/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import java.util.*;

/**
 * Coordination manager for agent teams.
 * <p>
 * Handles inter-agent coordination, message routing, and task delegation.
 * <p>
 * Mirrors Python's {@code CoordinationManager} in
 * {@code openjiuwen.agent_teams.agent.coordination_manager}.
 */
public class CoordinationManager {
    
    private String teamName;
    private Map<String, Object> members;
    private Map<String, List<Object>> messageQueues;
    private Map<String, Object> taskAssignments;
    
    /**
     * Create CoordinationManager.
     *
     * @param teamName Team name
     */
    public CoordinationManager(String teamName) {
        this.teamName = teamName;
        this.members = new HashMap<>();
        this.messageQueues = new HashMap<>();
        this.taskAssignments = new HashMap<>();
    }
    
    /**
     * Register a team member.
     *
     * @param memberName Member name
     * @param member Member object
     */
    public void registerMember(String memberName, Object member) {
        members.put(memberName, member);
        messageQueues.put(memberName, new ArrayList<>());
    }
    
    /**
     * Route message to target member.
     *
     * @param sender Sender name
     * @param receiver Receiver name
     * @param message Message content
     */
    public void routeMessage(String sender, String receiver, Object message) {
        List<Object> queue = messageQueues.get(receiver);
        if (queue != null) {
            queue.add(message);
        }
    }
    
    /**
     * Get pending messages for a member.
     *
     * @param memberName Member name
     * @return List of pending messages
     */
    public List<Object> getPendingMessages(String memberName) {
        List<Object> queue = messageQueues.get(memberName);
        return queue != null ? new ArrayList<>(queue) : new ArrayList<>();
    }
    
    /**
     * Clear messages for a member.
     *
     * @param memberName Member name
     */
    public void clearMessages(String memberName) {
        List<Object> queue = messageQueues.get(memberName);
        if (queue != null) {
            queue.clear();
        }
    }
    
    /**
     * Assign task to a member.
     *
     * @param taskId Task ID
     * @param memberName Member name
     */
    public void assignTask(String taskId, String memberName) {
        taskAssignments.put(taskId, memberName);
    }
    
    /**
     * Get member assigned to task.
     *
     * @param taskId Task ID
     * @return Member name or null
     */
    public String getTaskAssignment(String taskId) {
        Object assignment = taskAssignments.get(taskId);
        return assignment != null ? assignment.toString() : null;
    }
    
    /**
     * Broadcast message to all members.
     *
     * @param sender Sender name
     * @param message Message content
     */
    public void broadcast(String sender, Object message) {
        for (String memberName : members.keySet()) {
            if (!memberName.equals(sender)) {
                routeMessage(sender, memberName, message);
            }
        }
    }
    
    /**
     * Get all registered member names.
     *
     * @return Set of member names
     */
    public Set<String> getMemberNames() {
        return new HashSet<>(members.keySet());
    }
}