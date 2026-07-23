/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.team;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.sysop.cwd.CwdContext;

class TenantSharedMemoryTest {

    @TempDir
    Path tempDir;

    Path defaultTeamDir;
    Path tenantWorkspace;

    SharedMemoryManager manager;

    @BeforeEach
    void setUp() {
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
        defaultTeamDir = tempDir.resolve("default_team_memory");
        tenantWorkspace = tempDir.resolve("tenant_workspace");
        manager = new SharedMemoryManager(defaultTeamDir.toString(), null);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
    }

    @Test
    void noTenant_getTeamMemoryDir_returnsDefaultPath() {
        assertThat(manager.getTeamMemoryDir()).isEqualTo(defaultTeamDir.toAbsolutePath().normalize());
    }

    @Test
    void withTenant_getTeamMemoryDir_returnsTenantWorkspaceTeamMemory() throws Exception {
        Files.createDirectories(tenantWorkspace);
        CwdContext.setWorkspace(tenantWorkspace.toString());
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("abc123").build());

        Path expected = tenantWorkspace.resolve("team_memory").toAbsolutePath().normalize();
        assertThat(manager.getTeamMemoryDir()).isEqualTo(expected);
    }

    @Test
    void withTenant_writeAndReadTeamSummary_isolation() throws Exception {
        Path workspaceA = tempDir.resolve("workspace_a");
        Files.createDirectories(workspaceA);
        CwdContext.setWorkspace(workspaceA.toString());
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenant_a").build());

        manager.writeTeamSummary("tenant_a summary");
        assertThat(manager.readTeamSummary()).isEqualTo("tenant_a summary");

        Path workspaceB = tempDir.resolve("workspace_b");
        Files.createDirectories(workspaceB);
        CwdContext.setWorkspace(workspaceB.toString());
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenant_b").build());

        assertThat(manager.readTeamSummary()).isEmpty();
        manager.writeTeamSummary("tenant_b summary");
        assertThat(manager.readTeamSummary()).isEqualTo("tenant_b summary");

        CwdContext.setWorkspace(workspaceA.toString());
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenant_a").build());
        assertThat(manager.readTeamSummary()).isEqualTo("tenant_a summary");
    }

    @Test
    void noTenant_writeAndRead_backwardCompat() throws Exception {
        Files.createDirectories(defaultTeamDir);
        manager.writeTeamSummary("default summary");
        assertThat(manager.readTeamSummary()).isEqualTo("default summary");
    }

    @Test
    void tenantNullId_fallsBackToDefault() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId(null).build());
        assertThat(manager.getTeamMemoryDir()).isEqualTo(defaultTeamDir.toAbsolutePath().normalize());
    }

    @Test
    void tenantEmptyId_fallsBackToDefault() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("").build());
        assertThat(manager.getTeamMemoryDir()).isEqualTo(defaultTeamDir.toAbsolutePath().normalize());
    }

    @Test
    void tenantWithWorkspaceNull_fallsBackToDefault() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("abc123").build());
        assertThat(CwdContext.getWorkspace()).isNull();
        assertThat(manager.getTeamMemoryDir()).isEqualTo(defaultTeamDir.toAbsolutePath().normalize());
    }

    @Test
    void ensureDir_withTenant_createsTenantTeamMemoryDir() throws Exception {
        Path workspace = tempDir.resolve("tenant_workspace2");
        Files.createDirectories(workspace);
        CwdContext.setWorkspace(workspace.toString());
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("abc123").build());

        manager.ensureDir();
        Path teamDir = workspace.resolve("team_memory");
        assertThat(Files.exists(teamDir)).isTrue();
        assertThat(Files.isDirectory(teamDir)).isTrue();
    }

    @Test
    void appendEntry_withTenant_appendsToTenantDir() throws Exception {
        Path workspace = tempDir.resolve("tenant_workspace3");
        Files.createDirectories(workspace);
        CwdContext.setWorkspace(workspace.toString());
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("abc123").build());

        manager.appendEntry("first entry");
        assertThat(manager.readTeamSummary()).isEqualTo("first entry");
        manager.appendEntry("second entry");
        assertThat(manager.readTeamSummary()).contains("first entry");
        assertThat(manager.readTeamSummary()).contains("second entry");
    }
}
