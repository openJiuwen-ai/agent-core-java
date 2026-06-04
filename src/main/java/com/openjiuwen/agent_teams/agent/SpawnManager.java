/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spawn manager for agent teams.
 * <p>
 * Handles dynamic member spawning and lifecycle management.
 * <p>
 * Mirrors Python's {@code SpawnManager} in
 * {@code openjiuwen.agent_teams.agent.spawn_manager}.
 *
 * <p>Python version manages spawned_handles (SpawnedProcessHandle) for
 * subprocess and in-process spawning with health checks. This Java version
 * provides a simplified in-memory implementation suitable for same-process
 * agent spawning with lifecycle tracking.
 */
public class SpawnManager {
    
    private static final Logger LOG = LoggerFactory.getLogger(SpawnManager.class);
    
    private final String teamName;
    private final Map<String, SpawnedMember> spawnedMembers;
    private final int maxMembers;
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
        this.spawnedMembers = new ConcurrentHashMap<>();
        this.currentCount = 0;
        LOG.debug("[SpawnManager] Created for team '{}' with maxMembers={}", teamName, maxMembers);
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
            LOG.warn("[SpawnManager] Cannot spawn '{}' - max members {} reached for team '{}'",
                memberName, maxMembers, teamName);
            return null;
        }
        
        SpawnedMember member = createMember(memberName, config);
        if (member != null) {
            spawnedMembers.put(memberName, member);
            currentCount++;
            LOG.info("[SpawnManager] Spawned member '{}' for team '{}' (count={})",
                memberName, teamName, currentCount);
        }
        
        return member;
    }
    
    /**
     * Terminate a spawned member.
     *
     * @param memberName Member name
     */
    public void terminateMember(String memberName) {
        SpawnedMember member = spawnedMembers.remove(memberName);
        if (member != null) {
            cleanupMember(member);
            currentCount--;
            LOG.info("[SpawnManager] Terminated member '{}' for team '{}' (count={})",
                memberName, teamName, currentCount);
        }
    }
    
    /**
     * Get spawned member by name.
     *
     * @param memberName Member name
     * @return Member object or null
     */
    public Object getMember(String memberName) {
        SpawnedMember member = spawnedMembers.get(memberName);
        return member != null ? member.getConfig() : null;
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
        LOG.info("[SpawnManager] Terminating all {} members for team '{}'",
            currentCount, teamName);
        for (SpawnedMember member : spawnedMembers.values()) {
            cleanupMember(member);
        }
        spawnedMembers.clear();
        currentCount = 0;
    }
    
    // -- Internal methods --
    
    /**
     * Create a spawned member instance.
     * <p>
     * Mirrors Python's spawn logic which creates SpawnedProcessHandle.
     * This Java version creates an in-memory SpawnedMember wrapper.
     *
     * @param memberName Member name
     * @param config Member configuration map
     * @return SpawnedMember instance
     */
    private SpawnedMember createMember(String memberName, Map<String, Object> config) {
        if (config == null) {
            LOG.warn("[SpawnManager] Cannot create member '{}' with null config", memberName);
            return null;
        }
        
        SpawnedMember member = new SpawnedMember(memberName, config);
        member.setStatus(MemberStatus.ALIVE);
        member.setSpawnTime(System.currentTimeMillis());
        
        // Initialize member if config contains initialization data
        Object agentSpec = config.get("agent_spec");
        if (agentSpec != null) {
            member.setAgentSpec(agentSpec);
            LOG.debug("[SpawnManager] Member '{}' initialized with agent_spec", memberName);
        }
        
        return member;
    }
    
    /**
     * Cleanup a spawned member's resources.
     * <p>
     * Mirrors Python's cleanup_teammate which calls handle.stop_health_check()
     * and handle.force_kill(). This Java version marks member as terminated
     * and clears resources.
     *
     * @param member SpawnedMember to cleanup
     */
    private void cleanupMember(SpawnedMember member) {
        if (member == null) {
            return;
        }
        
        try {
            // Mark as terminated (mirrors Python's force_kill)
            member.setStatus(MemberStatus.TERMINATED);
            member.setTerminateTime(System.currentTimeMillis());
            
            // Clear member resources
            member.clearResources();
            
            LOG.debug("[SpawnManager] Cleaned up member '{}' (status={})",
                member.getMemberName(), member.getStatus());
        } catch (Exception e) {
            LOG.error("[SpawnManager] Error cleaning up member '{}': {}",
                member.getMemberName(), e.getMessage());
        }
    }
    
    /**
     * Get spawned member wrapper by name.
     *
     * @param memberName Member name
     * @return SpawnedMember wrapper or null
     */
    public SpawnedMember getSpawnedMember(String memberName) {
        return spawnedMembers.get(memberName);
    }
    
    /**
     * Get team name.
     *
     * @return Team name
     */
    public String getTeamName() {
        return teamName;
    }
    
    /**
     * Get max members limit.
     *
     * @return Max members
     */
    public int getMaxMembers() {
        return maxMembers;
    }
    
    // -- Inner classes --
    
    /**
     * Member status enum.
     * <p>
     * Mirrors Python's MemberStatus in openjiuwen.agent_teams.schema.status.
     */
    public enum MemberStatus {
        ALIVE,
        UNHEALTHY,
        TERMINATED
    }
    
    /**
     * Spawned member wrapper.
     * <p>
     * Mirrors Python's SpawnedProcessHandle with basic lifecycle tracking.
     */
    public static class SpawnedMember {
        private final String memberName;
        private final Map<String, Object> config;
        private MemberStatus status;
        private long spawnTime;
        private long terminateTime;
        private Object agentSpec;
        private Map<String, Object> resources;
        
        public SpawnedMember(String memberName, Map<String, Object> config) {
            this.memberName = memberName;
            this.config = config;
            this.status = MemberStatus.ALIVE;
            this.resources = new HashMap<>();
        }
        
        public String getMemberName() {
            return memberName;
        }
        
        public Map<String, Object> getConfig() {
            return config;
        }
        
        public MemberStatus getStatus() {
            return status;
        }
        
        public void setStatus(MemberStatus status) {
            this.status = status;
        }
        
        public long getSpawnTime() {
            return spawnTime;
        }
        
        public void setSpawnTime(long spawnTime) {
            this.spawnTime = spawnTime;
        }
        
        public long getTerminateTime() {
            return terminateTime;
        }
        
        public void setTerminateTime(long terminateTime) {
            this.terminateTime = terminateTime;
        }
        
        public Object getAgentSpec() {
            return agentSpec;
        }
        
        public void setAgentSpec(Object agentSpec) {
            this.agentSpec = agentSpec;
        }
        
        public boolean isAlive() {
            return status == MemberStatus.ALIVE;
        }
        
        public void addResource(String key, Object value) {
            resources.put(key, value);
        }
        
        public Object getResource(String key) {
            return resources.get(key);
        }
        
        public void clearResources() {
            resources.clear();
            agentSpec = null;
        }
    }
}