package com.openjiuwen.core.multitenant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.memory.team.SharedMemoryManager;
import com.openjiuwen.core.sysop.cwd.CwdContext;

class TenantSharedMemoryTest {

    @TempDir
    Path tempDir;

    String defaultTeamDir;
    String workspaceBase;

    @BeforeEach
    void setUp() {
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
        defaultTeamDir = tempDir.resolve("default_team_memory").toString();
        workspaceBase = tempDir.resolve("workspace_root").toString();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
    }

    private void setTenant(String tid) {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId(tid).build());
    }

    @Test
    void testSharedMemory_tenantAPathDiffersFromTenantB() {
        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(workspaceBase);
        SharedMemoryManager manager = new SharedMemoryManager(defaultTeamDir, null, resolver);

        setTenant("tenant_a");
        Path pathA = manager.getTeamMemoryDir();

        setTenant("tenant_b");
        Path pathB = manager.getTeamMemoryDir();

        assertThat(pathA).isNotEqualTo(pathB);
        assertThat(pathA.toString()).contains("tenants").contains("tenant_a").contains("team_memory");
        assertThat(pathB.toString()).contains("tenants").contains("tenant_b").contains("team_memory");
    }

    @Test
    void testSharedMemory_writeTeamSummary_isolatedByTenant() throws IOException {
        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(workspaceBase);
        SharedMemoryManager manager = new SharedMemoryManager(defaultTeamDir, null, resolver);

        setTenant("tenant_a");
        manager.writeTeamSummary("summary from tenant A");
        assertThat(manager.readTeamSummary()).isEqualTo("summary from tenant A");

        setTenant("tenant_b");
        assertThat(manager.readTeamSummary()).isEmpty();
    }

    @Test
    void testSharedMemory_readTeamSummary_isolatedByTenant() throws IOException {
        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(workspaceBase);
        SharedMemoryManager manager = new SharedMemoryManager(defaultTeamDir, null, resolver);

        setTenant("tenant_a");
        manager.writeTeamSummary("A's memory");
        assertThat(manager.readTeamSummary()).isEqualTo("A's memory");

        setTenant("tenant_b");
        manager.writeTeamSummary("B's memory");
        assertThat(manager.readTeamSummary()).isEqualTo("B's memory");

        setTenant("tenant_a");
        assertThat(manager.readTeamSummary()).isEqualTo("A's memory");

        setTenant("tenant_b");
        assertThat(manager.readTeamSummary()).isEqualTo("B's memory");
    }

    @Test
    void testSharedMemory_appendEntry_isolatedByTenant() throws IOException {
        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(workspaceBase);
        SharedMemoryManager manager = new SharedMemoryManager(defaultTeamDir, null, resolver);

        setTenant("tenant_a");
        manager.appendEntry("entry one for A");
        manager.appendEntry("entry two for A");

        setTenant("tenant_b");
        assertThat(manager.readTeamSummary()).isEmpty();
        manager.appendEntry("entry for B");
        assertThat(manager.readTeamSummary()).contains("entry for B").doesNotContain("entry one for A");

        setTenant("tenant_a");
        assertThat(manager.readTeamSummary()).contains("entry one for A").contains("entry two for A")
                .doesNotContain("entry for B");
    }

    @Test
    void testSharedMemory_ensureDir_createsTenantTeamMemoryDir() throws IOException {
        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(workspaceBase);
        SharedMemoryManager manager = new SharedMemoryManager(defaultTeamDir, null, resolver);

        setTenant("tenant_ensure");

        manager.ensureDir();
        Path expectedDir = Path.of(workspaceBase).resolve("tenants").resolve("tenant_ensure").resolve("team_memory")
                .toAbsolutePath().normalize();
        assertThat(Files.exists(expectedDir)).isTrue();
        assertThat(Files.isDirectory(expectedDir)).isTrue();
    }

    @Test
    void testSharedMemory_withResolver_usesResolveTeamMemoryDir() {
        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(workspaceBase);
        SharedMemoryManager manager = new SharedMemoryManager(defaultTeamDir, null, resolver);

        setTenant("abc123");
        Path dir = manager.getTeamMemoryDir();

        assertThat(dir.toString()).contains("tenants").contains("abc123").contains("team_memory");
        Path expected = Path.of(workspaceBase).resolve("tenants").resolve("abc123").resolve("team_memory")
                .toAbsolutePath().normalize();
        assertThat(dir.toAbsolutePath().normalize()).isEqualTo(expected);
    }

    @Test
    void testSharedMemory_withoutResolver_fallbacksToCwdContext() {
        SharedMemoryManager manager = new SharedMemoryManager(defaultTeamDir, null);

        Path workspace = tempDir.resolve("cwd_workspace");
        CwdContext.setWorkspace(workspace.toString());
        setTenant("abc123");

        Path dir = manager.getTeamMemoryDir();
        Path expected = workspace.resolve("team_memory").toAbsolutePath().normalize();
        assertThat(dir.toAbsolutePath().normalize()).isEqualTo(expected);
    }

    @Test
    void testSharedMemory_noTenant_usesDefaultDir() {
        SharedMemoryManager manager = new SharedMemoryManager(defaultTeamDir, null);

        assertThat(TenantContextHolder.getCurrentTenant()).isNull();

        Path dir = manager.getTeamMemoryDir();
        Path expected = Path.of(defaultTeamDir).toAbsolutePath().normalize();
        assertThat(dir.toAbsolutePath().normalize()).isEqualTo(expected);
    }

    @Test
    void testSharedMemory_backwardCompat() throws IOException {
        SharedMemoryManager manager = new SharedMemoryManager(defaultTeamDir, null);

        assertThat(TenantContextHolder.getCurrentTenant()).isNull();
        assertThat(CwdContext.getWorkspace()).isNull();

        manager.ensureDir();
        manager.writeTeamSummary("backward compat content");
        assertThat(manager.readTeamSummary()).isEqualTo("backward compat content");

        Path expectedFile = Path.of(defaultTeamDir).resolve(SharedMemoryManager.TEAM_MEMORY_FILENAME)
                .toAbsolutePath().normalize();
        assertThat(Files.exists(expectedFile)).isTrue();
    }
}
