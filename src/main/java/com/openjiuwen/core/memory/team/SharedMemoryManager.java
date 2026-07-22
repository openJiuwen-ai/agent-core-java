/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.team;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.multitenant.TenantWorkspaceResolver;
import com.openjiuwen.core.sysop.cwd.CwdContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Read/write team-level TEAM_MEMORY.md under team-memory/.
 * 
 * @since 0.1.7
 */
public class SharedMemoryManager {
    /**
     * TEAM_MEMORY_FILENAME.
     * 
     * @since 0.1.7
     */
    public static final String TEAM_MEMORY_FILENAME = "TEAM_MEMORY.md";

    /**
     * TEAM_MEMORY_MAX_READ_LINES.
     * 
     * @since 0.1.7
     */
    public static final int TEAM_MEMORY_MAX_READ_LINES = 200;

    private final Path teamMemoryDir;
    private final Object sysOperation;
    private final TenantWorkspaceResolver workspaceResolver;

    /**
     * SharedMemoryManager.
     *
     * @param teamMemoryDir teamMemoryDir
     * @param sysOperation sysOperation
     * @since 0.1.7
     */
    public SharedMemoryManager(String teamMemoryDir, Object sysOperation) {
        this(teamMemoryDir, sysOperation, null);
    }

    /**
     * SharedMemoryManager with explicit TenantWorkspaceResolver for tenant-aware path resolution.
     *
     * @param teamMemoryDir teamMemoryDir
     * @param sysOperation sysOperation
     * @param workspaceResolver workspaceResolver (nullable; when set, uses resolveTeamMemoryDir for tenant path)
     * @since 0.1.7
     */
    public SharedMemoryManager(String teamMemoryDir, Object sysOperation, TenantWorkspaceResolver workspaceResolver) {
        this.teamMemoryDir = Path.of(teamMemoryDir).toAbsolutePath().normalize();
        this.sysOperation = sysOperation;
        this.workspaceResolver = workspaceResolver;
    }

    /**
     * ensureDir.
     * 
     * @throws IOException IOException
     * @since 0.1.7
     */
    public void ensureDir() throws IOException {
        Files.createDirectories(tenantAwareTeamMemoryDir());
    }

    /**
     * readTeamSummary.
     * 
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    public String readTeamSummary() throws IOException {
        Path file = tenantAwareTeamMemoryDir().resolve(TEAM_MEMORY_FILENAME);
        if (!Files.exists(file)) {
            return "";
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.size() > TEAM_MEMORY_MAX_READ_LINES) {
            lines = lines.subList(0, TEAM_MEMORY_MAX_READ_LINES);
        }
        return String.join("\n", lines).trim();
    }

    /**
     * writeTeamSummary.
     * 
     * @param content content
     * @throws IOException IOException
     * @since 0.1.7
     */
    public void writeTeamSummary(String content) throws IOException {
        Path dir = tenantAwareTeamMemoryDir();
        Files.createDirectories(dir);
        Path target = dir.resolve(TEAM_MEMORY_FILENAME);
        Path temp = Files.createTempFile(dir, "team_memory_", ".tmp");
        try {
            Files.writeString(temp, content != null ? content : "", StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * appendEntry.
     * 
     * @param entry entry
     * @throws IOException IOException
     * @since 0.1.7
     */
    public void appendEntry(String entry) throws IOException {
        String existing = readTeamSummary();
        String newContent = existing.isBlank() ? entry : existing + "\n\n---\n\n" + entry;
        writeTeamSummary(newContent);
    }

    private Path tenantAwareTeamMemoryDir() {
        TenantContext ctx = TenantContextHolder.getCurrentTenant();
        if (ctx != null && ctx.isTenantAware()) {
            if (workspaceResolver != null) {
                Path teamDir = workspaceResolver.resolveTeamMemoryDir(ctx);
                try {
                    Files.createDirectories(teamDir);
                } catch (IOException e) {
                    // ignored; will be created on write
                }
                return teamDir.toAbsolutePath().normalize();
            }
            String workspace = CwdContext.getWorkspace();
            if (workspace != null) {
                Path teamDir = Path.of(workspace).resolve("team_memory");
                try {
                    Files.createDirectories(teamDir);
                } catch (IOException e) {
                    // ignored; will be created on write
                }
                return teamDir.toAbsolutePath().normalize();
            }
        }
        return teamMemoryDir;
    }

    /**
     * getTeamMemoryDir.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Path getTeamMemoryDir() {
        return tenantAwareTeamMemoryDir();
    }

    /**
     * getSysOperation.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getSysOperation() {
        return sysOperation;
    }
}
