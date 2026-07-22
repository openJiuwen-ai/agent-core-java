package com.openjiuwen.core.multitenant;

import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.spi.store.BaseKVStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TenantResourceCleanerTest {

    @TempDir
    Path baseDir;

    TenantWorkspaceResolver resolver;
    BaseKVStore kvStore;
    TenantResourceCleaner cleaner;

    @BeforeEach
    void setUp() {
        TenantContextHolder.clearCurrentTenant();
        resolver = new TenantWorkspaceResolver(baseDir.toString());
        kvStore = new InMemoryKVStore();
        cleaner = new DefaultTenantResourceCleaner(resolver, kvStore);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    void testCleanupWorkspace_deletesTenantRoot() throws Exception {
        TenantContext ctx = TenantContext.builder().tenantId("tenantA").build();
        Path tenantRoot = resolver.initializeTenantSpace(ctx);
        Files.writeString(tenantRoot.resolve("file.txt"), "data");
        assertThat(tenantRoot).isDirectory();

        cleaner.cleanupWorkspace("tenantA");
        assertThat(tenantRoot).doesNotExist();
    }

    @Test
    void testCleanupSkills_deletesSkillDir() throws Exception {
        TenantContext ctx = TenantContext.builder().tenantId("tenantA").build();
        Path tenantRoot = resolver.initializeTenantSpace(ctx);
        Path skillDir = tenantRoot.resolve("skills");
        Files.createDirectories(skillDir.resolve("mySkill"));
        Files.writeString(skillDir.resolve("mySkill").resolve("SKILL.md"), "skill content");

        assertThat(skillDir).isDirectory();

        cleaner.cleanupSkills("tenantA");
        assertThat(skillDir).doesNotExist();
        assertThat(tenantRoot).isDirectory();
    }

    @Test
    void testCleanupCheckpoints_deletesSessionData() throws Exception {
        TenantContext ctx = TenantContext.builder().tenantId("tenantA").build();
        Path tenantRoot = resolver.initializeTenantSpace(ctx);
        Path checkpointDir = tenantRoot.resolve("checkpoints");
        Path sessionDir = checkpointDir.resolve("session-1");
        Files.createDirectories(sessionDir);
        Files.writeString(sessionDir.resolve("state.json"), "{}");

        assertThat(sessionDir).isDirectory();

        cleaner.cleanupCheckpoints("tenantA", "session-1");
        assertThat(sessionDir).doesNotExist();
        assertThat(checkpointDir).isDirectory();
    }

    @Test
    void testCleanupTeamMemory_deletesTeamDir() throws Exception {
        TenantContext ctx = TenantContext.builder().tenantId("tenantA").build();
        Path tenantRoot = resolver.initializeTenantSpace(ctx);
        Path memoryDir = tenantRoot.resolve("team_memory");
        Path teamDir = memoryDir.resolve("team-1");
        Files.createDirectories(teamDir);
        Files.writeString(teamDir.resolve("memory.json"), "{}");

        assertThat(teamDir).isDirectory();

        cleaner.cleanupTeamMemory("tenantA", "team-1");
        assertThat(teamDir).doesNotExist();
        assertThat(memoryDir).isDirectory();
    }

    @Test
    void testCleanupTodo_deletesTodoDir() throws Exception {
        TenantContext ctx = TenantContext.builder().tenantId("tenantA").build();
        Path tenantRoot = resolver.initializeTenantSpace(ctx);
        Path todoDir = tenantRoot.resolve("todo");
        Path sessionDir = todoDir.resolve("session-1");
        Files.createDirectories(sessionDir);
        Files.writeString(sessionDir.resolve("tasks.json"), "[]");

        assertThat(sessionDir).isDirectory();

        cleaner.cleanupTodo("tenantA", "session-1");
        assertThat(sessionDir).doesNotExist();
        assertThat(todoDir).isDirectory();
    }

    @Test
    void testCleanupKVState_deletesByTenantPrefix() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        kvStore.set("tenantA:some_key", "value1");
        kvStore.set("tenantA:other_key", "value2");
        kvStore.set("tenantB:other_key", "valueB");

        assertThat(kvStore.getByPrefix("tenantA:")).hasSize(2);

        cleaner.cleanupKVState("tenantA");
        assertThat(kvStore.getByPrefix("tenantA:")).isEmpty();
        assertThat(kvStore.isExists("tenantB:other_key")).isTrue();
    }

    @Test
    void testCleanupKVState_withSessionId_deletesBySessionPrefix() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        kvStore.set("tenantA:session-1:state", "v1");
        kvStore.set("tenantA:session-2:state", "v2");
        kvStore.set("tenantA:lock:resource", "lock1");

        assertThat(kvStore.getByPrefix("tenantA:")).hasSize(3);

        cleaner.cleanupKVState("tenantA", "session-1");
        assertThat(kvStore.isExists("tenantA:session-1:state")).isFalse();
        assertThat(kvStore.isExists("tenantA:session-2:state")).isTrue();
        assertThat(kvStore.isExists("tenantA:lock:resource")).isTrue();
    }

    @Test
    void testCleanupDistributedLocks_releasesTenantLocks() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        kvStore.set("tenantA:lock:resource1", "locked");
        kvStore.set("tenantA:lock:resource2", "locked");
        kvStore.set("tenantA:state", "data");

        assertThat(kvStore.getByPrefix("tenantA:lock:")).hasSize(2);

        cleaner.cleanupDistributedLocks("tenantA");
        assertThat(kvStore.getByPrefix("tenantA:lock:")).isEmpty();
        assertThat(kvStore.isExists("tenantA:state")).isTrue();
    }

    @Test
    void testCleanupAll_deletesEntireTenant_plusKVData() throws Exception {
        TenantContext ctx = TenantContext.builder().tenantId("tenantA").build();
        Path tenantRoot = resolver.initializeTenantSpace(ctx);
        Files.writeString(tenantRoot.resolve("file.txt"), "data");
        Files.writeString(tenantRoot.resolve("skills").resolve("skill.txt"), "data");

        TenantContextHolder.setCurrentTenant(ctx);
        kvStore.set("tenantA:state", "value");
        kvStore.set("tenantA:lock:res", "locked");
        kvStore.set("tenantB:state", "preserved");

        assertThat(tenantRoot).isDirectory();
        assertThat(kvStore.getByPrefix("tenantA:")).hasSize(2);

        cleaner.cleanupAll("tenantA");
        assertThat(tenantRoot).doesNotExist();
        assertThat(kvStore.getByPrefix("tenantA:")).isEmpty();
        assertThat(kvStore.isExists("tenantB:state")).isTrue();
    }

    @Test
    void testCleanupWorkspace_nonExistentTenant_noError() {
        assertThatCode(() -> cleaner.cleanupWorkspace("nonexistent"))
            .doesNotThrowAnyException();
    }

    @Test
    void testCleanupAll_preservesPublicSkills() throws Exception {
        TenantContext ctxA = TenantContext.builder().tenantId("tenantA").build();
        TenantContext ctxB = TenantContext.builder().tenantId("tenantB").build();

        Path rootA = resolver.initializeTenantSpace(ctxA);
        Path rootB = resolver.initializeTenantSpace(ctxB);

        Files.writeString(rootB.resolve("skills").resolve("public_skill.txt"), "public");
        assertThat(rootB.resolve("skills")).isDirectory();

        cleaner.cleanupAll("tenantA");
        assertThat(rootA).doesNotExist();
        assertThat(rootB).isDirectory();
        assertThat(rootB.resolve("skills")).isDirectory();
        assertThat(rootB.resolve("skills").resolve("public_skill.txt")).exists();
    }
}
