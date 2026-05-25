/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import java.util.*;

/**
 * Spawn manager for agent teams.
 * <p>
 * Handles dynamic member spawning and lifecycle management.
 * <p>
 * Mirrors Python's {@code SpawnManager} in
 * {@code openjiuwen.agent_teams.agent.spawn_manager}.
 */
public class SpawnManager {
    
    private String teamName;
    private Map<String, Object> spawnedMembers;
    private int maxMembers;
    private int currentCount;
    
    /**
     * Create SpawnManager.
     *
     * @param teamName Team name
     * @param maxMembers Maximum number of members
     */
    public SpawnManager(String teamName, int maxMembers) {
        this.teamName = teamName;
        this.maxMembers = maxMembers;
        this.spawnedMembers = new HashMap<>();
        this.currentCount = 0;
    }
    
    /**
     * Spawn a new member.
     *
     * @param memberName Member name
     * @param config Member configuration
     * @return Spawned member object or null if limit reached
     */
    public Object spawnMember(String memberName, Map<String, Object> config) {
        if (currentCount >= maxMembers) {
            return null;
        }
        
        Object member = createMember(memberName, config);
        if (member != null) {
            spawnedMembers.put(memberName, member);
            currentCount++;
        }
        
        return member;
    }
    
    /**
     * Terminate a spawned member.
     *
     * @param memberName Member name
     */
    public void terminateMember(String memberName) {
        Object member = spawnedMembers.remove(memberName);
        if (member != null) {
            currentCount--;
            cleanupMember(member);
        }
    }
    
    /**
     * Get spawned member by name.
     *
     * @param memberName Member name
     * @return Member object or null
     */
    public Object getMember(String memberName) {
        return spawnedMembers.get(memberName);
    }
    
    /**
     * Get all spawned member names.
     *
     * @return Set of member names
     */
    public Set<String> getSpawnedMemberNames() {
        return new HashSet<>(spawnedMembers.keySet());
    }
    
    /**
     * Get current member count.
     *
     * @return Current count
     */
    public int getCurrentCount() {
        return currentCount;
    }
    
    /**
     * Check if can spawn more members.
     *
     * @return True if capacity available
     */
    public boolean canSpawn() {
        return currentCount < maxMembers;
    }
    
    /**
     * Terminate all spawned members.
     */
    public void terminateAll() {
        for (Object member : spawnedMembers.values()) {
            cleanupMember(member);
        }
        spawnedMembers.clear();
        currentCount = 0;
    }
    
    // -- Internal methods --
    
    private Object createMember(String memberName, Map<String, Object> config) {
        // Placeholder: create actual member
        return config;
    }
    
    private void cleanupMember(Object member) {
        // Placeholder: cleanup member resources
    }
}