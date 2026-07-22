/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.spi.store.BaseKVStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * File-system and KV-store backed default implementation of {@link TenantResourceCleaner}.
 *
 * @since 0.1.7
 */
public class DefaultTenantResourceCleaner implements TenantResourceCleaner {
    private final TenantWorkspaceResolver workspaceResolver;
    private final BaseKVStore kvStore;

    public DefaultTenantResourceCleaner(TenantWorkspaceResolver workspaceResolver, BaseKVStore kvStore) {
        this.workspaceResolver = workspaceResolver;
        this.kvStore = kvStore;
    }

    @Override
    public void cleanupWorkspace(String tenantId) {
        TenantContext ctx = TenantContext.builder().tenantId(tenantId).build();
        Path root = workspaceResolver.resolveWorkspaceRoot(ctx);
        deleteDirectory(root);
    }

    @Override
    public void cleanupSkills(String tenantId) {
        TenantContext ctx = TenantContext.builder().tenantId(tenantId).build();
        Path skillRoot = workspaceResolver.resolveSkillRoot(ctx);
        deleteDirectory(skillRoot);
    }

    @Override
    public void cleanupCheckpoints(String tenantId, String sessionId) {
        TenantContext ctx = TenantContext.builder().tenantId(tenantId).build();
        Path checkpointDir = workspaceResolver.resolveCheckpointDir(ctx);
        if (sessionId != null) {
            Path sessionDir = checkpointDir.resolve(sessionId);
            deleteDirectory(sessionDir);
        } else {
            deleteDirectory(checkpointDir);
        }
    }

    @Override
    public void cleanupTeamMemory(String tenantId, String teamId) {
        TenantContext ctx = TenantContext.builder().tenantId(tenantId).build();
        Path memoryDir = workspaceResolver.resolveTeamMemoryDir(ctx);
        if (teamId != null) {
            Path teamDir = memoryDir.resolve(teamId);
            deleteDirectory(teamDir);
        } else {
            deleteDirectory(memoryDir);
        }
    }

    @Override
    public void cleanupTodo(String tenantId, String sessionId) {
        TenantContext ctx = TenantContext.builder().tenantId(tenantId).build();
        Path todoDir = workspaceResolver.resolveTodoDir(ctx);
        if (sessionId != null) {
            Path sessionDir = todoDir.resolve(sessionId);
            deleteDirectory(sessionDir);
        } else {
            deleteDirectory(todoDir);
        }
    }

    @Override
    public void cleanupKVState(String tenantId) {
        if (kvStore == null) {
            return;
        }
        String prefix = tenantId + ":";
        kvStore.deleteByPrefix(prefix, null);
    }

    @Override
    public void cleanupKVState(String tenantId, String sessionId) {
        if (kvStore == null) {
            return;
        }
        String prefix = tenantId + ":" + sessionId + ":";
        kvStore.deleteByPrefix(prefix, null);
    }

    @Override
    public void cleanupDistributedLocks(String tenantId) {
        if (kvStore == null) {
            return;
        }
        String prefix = tenantId + ":lock:";
        kvStore.deleteByPrefix(prefix, null);
    }

    @Override
    public void cleanupAll(String tenantId) {
        TenantContext ctx = TenantContext.builder().tenantId(tenantId).build();
        Path tenantRoot = workspaceResolver.resolveTenantRoot(ctx);
        deleteDirectory(tenantRoot);
        cleanupKVState(tenantId);
        cleanupDistributedLocks(tenantId);
    }

    private void deleteDirectory(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        Loggers.AGENT.warn("Failed to delete file during cleanup: {}", p, e);
                    }
                });
            Loggers.AGENT.info("Cleaned up directory: {}", dir);
        } catch (IOException e) {
            Loggers.AGENT.warn("Failed to cleanup directory: {} - {}", dir, e.getMessage());
        }
    }
}
