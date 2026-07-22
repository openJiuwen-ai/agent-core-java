package com.openjiuwen.core.multitenant;

public interface TenantResourceCleaner {
    void cleanupWorkspace(String tenantId);
    void cleanupSkills(String tenantId);
    void cleanupCheckpoints(String tenantId, String sessionId);
    void cleanupTeamMemory(String tenantId, String teamId);
    void cleanupTodo(String tenantId, String sessionId);
    void cleanupKVState(String tenantId);
    void cleanupKVState(String tenantId, String sessionId);
    void cleanupDistributedLocks(String tenantId);
    void cleanupAll(String tenantId);
}
