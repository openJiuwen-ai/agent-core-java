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

class DefaultTenantResourceCleanerTest {

    @TempDir
    Path baseDir;

    TenantWorkspaceResolver resolver;
    BaseKVStore kvStore;
    DefaultTenantResourceCleaner cleaner;

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
        Files.createDirectories(tenantRoot.resolve("project"));
        Files.writeString(tenantRoot.resolve("project").resolve("file.txt"), "data");
        assertThat(tenantRoot).isDirectory();

        cleaner.cleanupWorkspace("tenantA");
        assertThat(tenantRoot).doesNotExist();
    }

    @Test
    void testCleanupWorkspace_preservesOtherTenants() throws Exception {
        TenantContext ctxA = TenantContext.builder().tenantId("tenantA").build();
        TenantContext ctxB = TenantContext.builder().tenantId("tenantB").build();
        Path rootA = resolver.initializeTenantSpace(ctxA);
        Path rootB = resolver.initializeTenantSpace(ctxB);
        Files.createDirectories(rootA.resolve("project"));
        Files.writeString(rootA.resolve("project").resolve("data.txt"), "a_data");
        Files.writeString(rootB.resolve("skills").resolve("data.txt"), "b_data");

        assertThat(rootA).isDirectory();

        cleaner.cleanupWorkspace("tenantA");
        assertThat(rootA).doesNotExist();
        assertThat(rootB).isDirectory();
    }

    @Test
    void testCleanupSkills_deletesSkillDirOnly() throws Exception {
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
    void testCleanupCheckpoints_deletesSpecificSession() throws Exception {
        TenantContext ctx = TenantContext.builder().tenantId("tenantA").build();
        Path tenantRoot = resolver.initializeTenantSpace(ctx);
        Path checkpointDir = tenantRoot.resolve("checkpoints");
        Path session1 = checkpointDir.resolve("session-1");
        Path session2 = checkpointDir.resolve("session-2");
        Files.createDirectories(session1);
        Files.createDirectories(session2);
        Files.writeString(session1.resolve("state.json"), "{}");
        Files.writeString(session2.resolve("state.json"), "{}");

        assertThat(session1).isDirectory();
        assertThat(session2).isDirectory();

        cleaner.cleanupCheckpoints("tenantA", "session-1");
        assertThat(session1).doesNotExist();
        assertThat(session2).isDirectory();
    }

    @Test
    void testCleanupCheckpoints_nullSession_deletesAll() throws Exception {
        TenantContext ctx = TenantContext.builder().tenantId("tenantA").build();
        Path tenantRoot = resolver.initializeTenantSpace(ctx);
        Path checkpointDir = tenantRoot.resolve("checkpoints");
        Path session1 = checkpointDir.resolve("session-1");
        Files.createDirectories(session1);
        Files.writeString(session1.resolve("state.json"), "{}");

        assertThat(checkpointDir).isDirectory();

        cleaner.cleanupCheckpoints("tenantA", null);
        assertThat(checkpointDir).doesNotExist();
    }

    @Test
    void testCleanupTeamMemory_deletesSpecificTeam() throws Exception {
        TenantContext ctx = TenantContext.builder().tenantId("tenantA").build();
        Path tenantRoot = resolver.initializeTenantSpace(ctx);
        Path memoryDir = tenantRoot.resolve("team_memory");
        Path team1 = memoryDir.resolve("team-1");
        Path team2 = memoryDir.resolve("team-2");
        Files.createDirectories(team1);
        Files.createDirectories(team2);
        Files.writeString(team1.resolve("memory.json"), "{}");
        Files.writeString(team2.resolve("memory.json"), "{}");

        assertThat(team1).isDirectory();
        assertThat(team2).isDirectory();

        cleaner.cleanupTeamMemory("tenantA", "team-1");
        assertThat(team1).doesNotExist();
        assertThat(team2).isDirectory();
    }

    @Test
    void testCleanupTeamMemory_nullTeam_deletesAll() throws Exception {
        TenantContext ctx = TenantContext.builder().tenantId("tenantA").build();
        Path tenantRoot = resolver.initializeTenantSpace(ctx);
        Path memoryDir = tenantRoot.resolve("team_memory");
        Path team1 = memoryDir.resolve("team-1");
        Files.createDirectories(team1);
        Files.writeString(team1.resolve("memory.json"), "{}");

        assertThat(memoryDir).isDirectory();

        cleaner.cleanupTeamMemory("tenantA", null);
        assertThat(memoryDir).doesNotExist();
    }

    @Test
    void testCleanupTodo_deletesSpecificSession() throws Exception {
        TenantContext ctx = TenantContext.builder().tenantId("tenantA").build();
        Path tenantRoot = resolver.initializeTenantSpace(ctx);
        Path todoDir = tenantRoot.resolve("todo");
        Path session1 = todoDir.resolve("session-1");
        Path session2 = todoDir.resolve("session-2");
        Files.createDirectories(session1);
        Files.createDirectories(session2);
        Files.writeString(session1.resolve("tasks.json"), "[]");
        Files.writeString(session2.resolve("tasks.json"), "[]");

        assertThat(session1).isDirectory();
        assertThat(session2).isDirectory();

        cleaner.cleanupTodo("tenantA", "session-1");
        assertThat(session1).doesNotExist();
        assertThat(session2).isDirectory();
    }

    @Test
    void testCleanupTodo_nullSession_deletesAll() throws Exception {
        TenantContext ctx = TenantContext.builder().tenantId("tenantA").build();
        Path tenantRoot = resolver.initializeTenantSpace(ctx);
        Path todoDir = tenantRoot.resolve("todo");
        Path session1 = todoDir.resolve("session-1");
        Files.createDirectories(session1);
        Files.writeString(session1.resolve("tasks.json"), "[]");

        assertThat(todoDir).isDirectory();

        cleaner.cleanupTodo("tenantA", null);
        assertThat(todoDir).doesNotExist();
    }

    @Test
    void testCleanupKVState_deletesByTenantPrefix() {
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
    void testCleanupKVState_nullKvStore_noError() {
        DefaultTenantResourceCleaner noKvCleaner = new DefaultTenantResourceCleaner(resolver, null);
        assertThatCode(() -> noKvCleaner.cleanupKVState("tenantA"))
            .doesNotThrowAnyException();
    }

    @Test
    void testCleanupKVState_withSession_nullKvStore_noError() {
        DefaultTenantResourceCleaner noKvCleaner = new DefaultTenantResourceCleaner(resolver, null);
        assertThatCode(() -> noKvCleaner.cleanupKVState("tenantA", "session-1"))
            .doesNotThrowAnyException();
    }

    @Test
    void testCleanupDistributedLocks_releasesTenantLocks() {
        kvStore.set("tenantA:lock:resource1", "locked");
        kvStore.set("tenantA:lock:resource2", "locked");
        kvStore.set("tenantA:state", "data");

        assertThat(kvStore.getByPrefix("tenantA:lock:")).hasSize(2);

        cleaner.cleanupDistributedLocks("tenantA");
        assertThat(kvStore.getByPrefix("tenantA:lock:")).isEmpty();
        assertThat(kvStore.isExists("tenantA:state")).isTrue();
    }

    @Test
    void testCleanupDistributedLocks_nullKvStore_noError() {
        DefaultTenantResourceCleaner noKvCleaner = new DefaultTenantResourceCleaner(resolver, null);
        assertThatCode(() -> noKvCleaner.cleanupDistributedLocks("tenantA"))
            .doesNotThrowAnyException();
    }

    @Test
    void testCleanupAll_deletesWorkspaceAndKVData() throws Exception {
        TenantContext ctx = TenantContext.builder().tenantId("tenantA").build();
        Path tenantRoot = resolver.initializeTenantSpace(ctx);
        Files.writeString(tenantRoot.resolve("skills").resolve("file.txt"), "data");

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
    void testCleanupAll_preservesOtherTenants() throws Exception {
        TenantContext ctxA = TenantContext.builder().tenantId("tenantA").build();
        TenantContext ctxB = TenantContext.builder().tenantId("tenantB").build();
        Path rootA = resolver.initializeTenantSpace(ctxA);
        Path rootB = resolver.initializeTenantSpace(ctxB);
        Files.writeString(rootB.resolve("skills").resolve("public_skill.txt"), "public");

        kvStore.set("tenantA:state", "a_val");
        kvStore.set("tenantB:state", "b_val");

        cleaner.cleanupAll("tenantA");
        assertThat(rootA).doesNotExist();
        assertThat(rootB).isDirectory();
        assertThat(kvStore.isExists("tenantB:state")).isTrue();
    }

    @Test
    void testCleanupWorkspace_nonExistentTenant_noError() {
        assertThatCode(() -> cleaner.cleanupWorkspace("nonexistent"))
            .doesNotThrowAnyException();
    }

    @Test
    void testCleanupSkills_nonExistentTenant_noError() {
        assertThatCode(() -> cleaner.cleanupSkills("nonexistent"))
            .doesNotThrowAnyException();
    }

    @Test
    void testCleanupCheckpoints_nonExistentTenant_noError() {
        assertThatCode(() -> cleaner.cleanupCheckpoints("nonexistent", "session-1"))
            .doesNotThrowAnyException();
    }

    @Test
    void testCleanupTeamMemory_nonExistentTenant_noError() {
        assertThatCode(() -> cleaner.cleanupTeamMemory("nonexistent", "team-1"))
            .doesNotThrowAnyException();
    }

    @Test
    void testCleanupTodo_nonExistentTenant_noError() {
        assertThatCode(() -> cleaner.cleanupTodo("nonexistent", "session-1"))
            .doesNotThrowAnyException();
    }

    @Test
    void testCleanupAll_nonExistentTenant_noError() {
        assertThatCode(() -> cleaner.cleanupAll("nonexistent"))
            .doesNotThrowAnyException();
    }
}
