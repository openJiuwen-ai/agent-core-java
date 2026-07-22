/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant;

/**
 * Cleans up tenant-scoped resources across workspaces, KV state and distributed locks.
 *
 * @since 0.1.7
 */
public interface TenantResourceCleaner {
    /**
     * Remove the entire workspace tree for the given tenant.
     *
     * @param tenantId the tenant identifier
     * @since 0.1.7
     */
    void cleanupWorkspace(String tenantId);

    /**
     * Remove the skills directory for the given tenant.
     *
     * @param tenantId the tenant identifier
     * @since 0.1.7
     */
    void cleanupSkills(String tenantId);

    /**
     * Remove the checkpoint directory for the given tenant and session.
     *
     * @param tenantId the tenant identifier
     * @param sessionId the session identifier
     * @since 0.1.7
     */
    void cleanupCheckpoints(String tenantId, String sessionId);

    /**
     * Remove the team memory directory for the given tenant and team.
     *
     * @param tenantId the tenant identifier
     * @param teamId the team identifier
     * @since 0.1.7
     */
    void cleanupTeamMemory(String tenantId, String teamId);

    /**
     * Remove the todo directory for the given tenant and session.
     *
     * @param tenantId the tenant identifier
     * @param sessionId the session identifier
     * @since 0.1.7
     */
    void cleanupTodo(String tenantId, String sessionId);

    /**
     * Remove all KV state entries for the given tenant.
     *
     * @param tenantId the tenant identifier
     * @since 0.1.7
     */
    void cleanupKVState(String tenantId);

    /**
     * Remove the KV state entries scoped to the given tenant and session.
     *
     * @param tenantId the tenant identifier
     * @param sessionId the session identifier
     * @since 0.1.7
     */
    void cleanupKVState(String tenantId, String sessionId);

    /**
     * Remove the distributed lock entries held by the given tenant.
     *
     * @param tenantId the tenant identifier
     * @since 0.1.7
     */
    void cleanupDistributedLocks(String tenantId);

    /**
     * Remove all resources associated with the given tenant.
     *
     * @param tenantId the tenant identifier
     * @since 0.1.7
     */
    void cleanupAll(String tenantId);
}
