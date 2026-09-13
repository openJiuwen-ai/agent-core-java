/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant;

import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.cwd.CwdContext;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;
import com.openjiuwen.spi.store.BaseKVStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TenantIsolationM4IntegrationTest {

    @TempDir
    Path baseDir;

    @AfterEach
    void tearDown() {
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
    }

    @Test
    @DisplayName("ST-M4: TmpFileCleaner TTL cleanup deletes expired files but keeps fresh files")
    void testTmpFileCleaner_ttlCleanup_expiredDeletedFreshSurvives() throws Exception {
        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(baseDir.toString());
        TmpFileCleaner cleaner = new TmpFileCleaner(
                Duration.ofSeconds(2), Duration.ofSeconds(1), baseDir.toString(), resolver);

        TenantContext ctx = TenantContext.builder().tenantId("ttl_tenant").build();
        resolver.initializeTenantSpace(ctx);
        Path tenantTmp = resolver.resolveTempDir(ctx);
        Files.createDirectories(tenantTmp);

        Path expiredFile = tenantTmp.resolve("expired_data.txt");
        Files.writeString(expiredFile, "old content");
        assertThat(expiredFile).exists();

        cleaner.start();
        Thread.sleep(3000);
        assertThat(expiredFile).doesNotExist();

        Path freshFile = tenantTmp.resolve("fresh_data.txt");
        Files.writeString(freshFile, "new content");
        Thread.sleep(1500);
        assertThat(freshFile).exists();

        cleaner.stop();
    }

    @Test
    @DisplayName("ST-M4: TmpFileCleaner cleans both default and tenant-specific tmp directories")
    void testTmpFileCleaner_tenantSpecificCleanup_bothDirectories() throws Exception {
        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(baseDir.toString());
        TmpFileCleaner cleaner = new TmpFileCleaner(
                Duration.ofSeconds(2), Duration.ofSeconds(1), baseDir.toString(), resolver);

        Path defaultTmp = baseDir.resolve("tmp");
        Files.createDirectories(defaultTmp);
        Path defaultExpired = defaultTmp.resolve("default_old.txt");
        Files.writeString(defaultExpired, "expired default");

        TenantContext ctx = TenantContext.builder().tenantId("dual_tenant").build();
        resolver.initializeTenantSpace(ctx);
        Path tenantTmp = resolver.resolveTempDir(ctx);
        Files.createDirectories(tenantTmp);
        Path tenantExpired = tenantTmp.resolve("tenant_old.txt");
        Files.writeString(tenantExpired, "expired tenant");

        assertThat(defaultExpired).exists();
        assertThat(tenantExpired).exists();

        cleaner.start();
        Thread.sleep(3000);

        assertThat(defaultExpired).doesNotExist();
        assertThat(tenantExpired).doesNotExist();

        cleaner.stop();
    }

    @Test
    @DisplayName("ST-M4: TmpFileCleaner lifecycle - start then stop shuts down scheduler")
    void testTmpFileCleaner_lifecycle_startStop() throws Exception {
        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(baseDir.toString());
        TmpFileCleaner cleaner = new TmpFileCleaner(
                Duration.ofSeconds(60), Duration.ofSeconds(1), baseDir.toString(), resolver);

        cleaner.start();
        cleaner.stop();

        assertThatCode(() -> cleaner.stop()).doesNotThrowAnyException();

        Path defaultTmp = baseDir.resolve("tmp");
        Files.createDirectories(defaultTmp);
        Path staleFile = defaultTmp.resolve("stale_after_stop.txt");
        Files.writeString(staleFile, "should persist");
        Thread.sleep(2000);
        assertThat(staleFile).exists();
    }

    @Test
    @DisplayName("ST-M4: TenantResourceCleaner cleanupWorkspace deletes entire tenant root (workspace = tenantRoot)")
    void testTenantResourceCleaner_cleanupWorkspace() throws Exception {
        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(baseDir.toString());
        BaseKVStore kvStore = new InMemoryKVStore();
        DefaultTenantResourceCleaner cleaner = new DefaultTenantResourceCleaner(resolver, kvStore);

        TenantContext ctx = TenantContext.builder().tenantId("workspace_tenant").build();
        Path tenantRoot = resolver.initializeTenantSpace(ctx);
        Path skillsDir = tenantRoot.resolve("skills");
        Path tmpDir = tenantRoot.resolve("tmp");

        Files.createDirectories(tenantRoot.resolve("project"));
        Files.writeString(tenantRoot.resolve("project").resolve("code.java"), "class A {}");
        Files.createDirectories(skillsDir.resolve("skill1"));
        Files.writeString(skillsDir.resolve("skill1").resolve("SKILL.md"), "skill");
        Files.writeString(tmpDir.resolve("temp_data.txt"), "tmp content");

        assertThat(tenantRoot).isDirectory();
        assertThat(skillsDir).isDirectory();
        assertThat(tmpDir).isDirectory();

        cleaner.cleanupWorkspace("workspace_tenant");
        assertThat(tenantRoot).doesNotExist();
    }

    @Test
    @DisplayName("ST-M4: TenantResourceCleaner cleanupSkills deletes only skills directory")
    void testTenantResourceCleaner_cleanupSkills() throws Exception {
        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(baseDir.toString());
        BaseKVStore kvStore = new InMemoryKVStore();
        DefaultTenantResourceCleaner cleaner = new DefaultTenantResourceCleaner(resolver, kvStore);

        TenantContext ctx = TenantContext.builder().tenantId("skills_tenant").build();
        Path tenantRoot = resolver.initializeTenantSpace(ctx);
        Path skillsDir = tenantRoot.resolve("skills");
        Path tmpDir = tenantRoot.resolve("tmp");

        Files.createDirectories(skillsDir.resolve("mySkill"));
        Files.writeString(skillsDir.resolve("mySkill").resolve("SKILL.md"), "skill data");
        assertThat(skillsDir).isDirectory();

        cleaner.cleanupSkills("skills_tenant");
        assertThat(skillsDir).doesNotExist();
        assertThat(tenantRoot).isDirectory();
        assertThat(tmpDir).isDirectory();
    }

    @Test
    @DisplayName("ST-M4: TenantResourceCleaner cleanupAll deletes entire tenantRoot including skills/tmp")
    void testTenantResourceCleaner_cleanupAll() throws Exception {
        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(baseDir.toString());
        BaseKVStore kvStore = new InMemoryKVStore();
        DefaultTenantResourceCleaner cleaner = new DefaultTenantResourceCleaner(resolver, kvStore);

        TenantContext ctx = TenantContext.builder().tenantId("all_tenant").build();
        Path tenantRoot = resolver.initializeTenantSpace(ctx);

        Files.createDirectories(tenantRoot.resolve("project"));
        Files.writeString(tenantRoot.resolve("project").resolve("file.txt"), "ws data");
        Files.createDirectories(tenantRoot.resolve("skills").resolve("skill1"));
        Files.writeString(tenantRoot.resolve("skills").resolve("skill1").resolve("SKILL.md"), "skill");
        Files.writeString(tenantRoot.resolve("tmp").resolve("temp.txt"), "temp");

        assertThat(tenantRoot).isDirectory();
        assertThat(tenantRoot.resolve("skills")).isDirectory();
        assertThat(tenantRoot.resolve("tmp")).isDirectory();

        kvStore.set("all_tenant:state", "value");
        kvStore.set("other_tenant:state", "preserved");

        cleaner.cleanupAll("all_tenant");
        assertThat(tenantRoot).doesNotExist();
        assertThat(kvStore.getByPrefix("all_tenant:")).isEmpty();
        assertThat(kvStore.isExists("other_tenant:state")).isTrue();
    }

    @Test
    @DisplayName("ST-M4: TenantResourceCleaner cleanupKVState deletes tenant-prefixed keys but preserves others")
    void testTenantResourceCleaner_cleanupKVState() {
        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(baseDir.toString());
        BaseKVStore kvStore = new InMemoryKVStore();
        DefaultTenantResourceCleaner cleaner = new DefaultTenantResourceCleaner(resolver, kvStore);

        kvStore.set("kv_tenant:user_memory", "memory_data");
        kvStore.set("kv_tenant:session_state", "session_data");
        kvStore.set("kv_tenant:lock:resource1", "locked");
        kvStore.set("global_tenant:shared_key", "shared_value");
        kvStore.set("unrelated_key", "unrelated_value");

        assertThat(kvStore.getByPrefix("kv_tenant:")).hasSize(3);

        cleaner.cleanupKVState("kv_tenant");
        assertThat(kvStore.getByPrefix("kv_tenant:")).isEmpty();
        assertThat(kvStore.isExists("global_tenant:shared_key")).isTrue();
        assertThat(kvStore.isExists("unrelated_key")).isTrue();
    }

    @Test
    @DisplayName("ST-M4: DeepAgent AutoCloseable lifecycle - close() stops TmpFileCleaner without exception")
    void testDeepAgent_autoCloseableLifecycle() {
        DeepAgentConfig config = DeepAgentConfig.builder()
                .enableTenantIsolation(true)
                .tenantDataRoot(baseDir.toString())
                .workspacePath(baseDir.toString())
                .build();
        AgentCard card = AgentCard.builder().name("m4_agent").description("test").build();
        Workspace ws = Workspace.builder().rootPath(baseDir.toString()).language("cn").build();
        DeepAgent agent = new DeepAgent(card, config, ws);

        TenantContext tenantCtx = TenantContext.builder().tenantId("lifecycle_m4").build();
        AgentSessionApi session = new AgentSessionApi("m4-session").withTenantContext(tenantCtx);

        try {
            TenantContextHolder.setCurrentTenant(tenantCtx);
            Map<String, Object> result = agent.invoke(
                    Map.of("query", "hello", "conversation_id", "m4-session"), session);
            assertThat(result).isNotNull();
        } finally {
            TenantContextHolder.clearCurrentTenant();
        }

        assertThatCode(() -> agent.close()).doesNotThrowAnyException();
        assertThat(TenantContextHolder.getCurrentTenant()).isNull();
    }
}
