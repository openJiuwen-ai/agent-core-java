package com.openjiuwen.harness.deep_agent;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.multitenant.TenantWorkspaceResolver;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.cwd.CwdContext;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DeepAgentTenantTest {

    @TempDir
    Path baseDir;

    @BeforeEach
    void resetContext() {
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
    }

    @AfterEach
    void cleanupContext() {
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
    }

    @Test
    void testTenantWorkspaceResolver_generatesCorrectTenantPath() {
        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(baseDir.toString());
        TenantContext ctx = TenantContext.builder().tenantId("abc123").build();

        Path workspaceRoot = resolver.resolveWorkspaceRoot(ctx);
        assertThat(workspaceRoot.toString()).contains("tenants").contains("abc123");
        assertThat(workspaceRoot).isAbsolute();
    }

    @Test
    void testTenantWorkspaceResolver_noTenant_returnsBase() {
        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(baseDir.toString());
        TenantContext ctx = TenantContext.builder().tenantId(null).build();

        Path workspaceRoot = resolver.resolveWorkspaceRoot(ctx);
        assertThat(workspaceRoot).isEqualTo(baseDir);
    }

    @Test
    void testCwdContext_afterBindTenantWorkspace_setsCorrectValues() {
        TenantContext ctx = TenantContext.builder().tenantId("test_tenant").build();
        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(baseDir.toString());
        resolver.initializeTenantSpace(ctx);

        Path tenantWorkspace = resolver.resolveWorkspaceRoot(ctx);
        CwdContext.setWorkspace(tenantWorkspace.toString());
        CwdContext.setOriginalCwd(tenantWorkspace.toString());
        CwdContext.setTenantRoot(tenantWorkspace.toString());

        assertThat(CwdContext.getWorkspace()).contains("tenants").contains("test_tenant");
        assertThat(CwdContext.getOriginalCwd()).contains("tenants").contains("test_tenant");
        assertThat(CwdContext.getTenantRoot()).contains("tenants").contains("test_tenant");
        assertThat(CwdContext.getTenantRoot()).isEqualTo(CwdContext.getWorkspace());
    }

    @Test
    void testCwdContext_noTenant_remainsUnchanged() {
        CwdContext.reset();
        String originalWorkspace = CwdContext.getWorkspace();
        String originalTenantRoot = CwdContext.getTenantRoot();

        TenantContext ctx = TenantContext.builder().tenantId(null).build();
        assertThat(ctx.isTenantAware()).isFalse();

        assertThat(CwdContext.getTenantRoot()).isEqualTo(originalTenantRoot);
    }

    @Test
    void testDeepAgentConfig_enableTenantIsolation_defaultFalse() {
        DeepAgentConfig config = DeepAgentConfig.builder().build();
        assertThat(config.isEnableTenantIsolation()).isFalse();
    }

    @Test
    void testDeepAgentConfig_tenantDataRoot_defaultNull() {
        DeepAgentConfig config = DeepAgentConfig.builder().build();
        assertThat(config.getTenantDataRoot()).isNull();
    }

    @Test
    void testDeepAgentConfig_enableTenantIsolation_canBeSet() {
        DeepAgentConfig config = DeepAgentConfig.builder().enableTenantIsolation(true).build();
        assertThat(config.isEnableTenantIsolation()).isTrue();
    }

    @Test
    void testDeepAgentConfig_tenantDataRoot_canBeSet() {
        DeepAgentConfig config = DeepAgentConfig.builder().tenantDataRoot("/data/tenants").build();
        assertThat(config.getTenantDataRoot()).isEqualTo("/data/tenants");
    }

    @Test
    void testCwdContext_reset_clearsTenantRoot() {
        CwdContext.setTenantRoot("/data/tenants/abc123");
        assertThat(CwdContext.getTenantRoot()).isEqualTo("/data/tenants/abc123");

        CwdContext.reset();
        assertThat(CwdContext.getTenantRoot()).isNull();
    }

    @Test
    void testBindTenantWorkspace_withTenantDataRoot() {
        TenantContext ctx = TenantContext.builder().tenantId("org01").build();
        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(baseDir.toString());
        resolver.initializeTenantSpace(ctx);

        Path tenantWorkspace = resolver.resolveWorkspaceRoot(ctx);
        assertThat(tenantWorkspace.toString()).contains("tenants").contains("org01");

        assertThat(Files.exists(tenantWorkspace)).isTrue();
    }

    @Test
    void testTenantContextHolder_setAndClear_inTenantInvoke() {
        TenantContext ctx = TenantContext.builder().tenantId("tenant_x").build();

        TenantContextHolder.setCurrentTenant(ctx);
        assertThat(TenantContextHolder.getCurrentTenant()).isEqualTo(ctx);
        assertThat(TenantContextHolder.getCurrentTenant().getTenantId()).isEqualTo("tenant_x");

        TenantContextHolder.clearCurrentTenant();
        assertThat(TenantContextHolder.getCurrentTenant()).isNull();
    }

    @Test
    void testTenantWorkspaceResolver_isolationBetweenTenants() {
        TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(baseDir.toString());
        TenantContext tenantA = TenantContext.builder().tenantId("tenant_a").build();
        TenantContext tenantB = TenantContext.builder().tenantId("tenant_b").build();

        Path workspaceA = resolver.resolveWorkspaceRoot(tenantA);
        Path workspaceB = resolver.resolveWorkspaceRoot(tenantB);

        assertThat(workspaceA).isNotEqualTo(workspaceB);
        assertThat(workspaceA.toString()).contains("tenant_a");
        assertThat(workspaceB.toString()).contains("tenant_b");
    }

    @Test
    @DisplayName("invoke with AgentSessionApi carrying tenantContext sets and clears TenantContextHolder")
    void testInvoke_withSessionTenantContext() {
        TenantContext ctx = TenantContext.builder().tenantId("deep-agent-tenant").build();
        DeepAgentSession session = new DeepAgentSession("deep-agent-session-1").withTenantContext(ctx);

        DeepAgentConfig config = DeepAgentConfig.builder()
                .enableTenantIsolation(true)
                .tenantDataRoot(baseDir.toString())
                .workspacePath(baseDir.toString())
                .build();
        AgentCard card = AgentCard.builder().name("tenant_test_agent").description("test").build();
        Workspace workspace = new Workspace(baseDir.toString(), "cn");
        DeepAgent agent = new DeepAgent(card, config, workspace);

        CwdContext.setTenantRoot("pre-invoke-marker");

        Map<String, Object> result = agent.invoke(Map.of("query", "test", "conversation_id", "s1"), session);

        assertThat(result).isNotNull();
        assertThat(TenantContextHolder.getCurrentTenant()).isNull();
        assertThat(CwdContext.getTenantRoot()).isNull();
    }

    @Test
    @DisplayName("invoke with AgentSessionApi without tenantContext does not enter tenant branch")
    void testInvoke_withoutSessionTenantContext() {
        DeepAgentSession session = new DeepAgentSession("deep-agent-session-2");

        DeepAgentConfig config = DeepAgentConfig.builder()
                .workspacePath(baseDir.toString())
                .build();
        AgentCard card = AgentCard.builder().name("no_tenant_agent").description("test").build();
        Workspace workspace = new Workspace(baseDir.toString(), "cn");
        DeepAgent agent = new DeepAgent(card, config, workspace);

        CwdContext.setTenantRoot("pre-invoke-marker");

        Map<String, Object> result = agent.invoke(Map.of("query", "test", "conversation_id", "s2"), session);

        assertThat(result).isNotNull();
        assertThat(TenantContextHolder.getCurrentTenant()).isNull();
        assertThat(CwdContext.getTenantRoot()).isEqualTo("pre-invoke-marker");
    }

    @Test
    @DisplayName("invoke with AgentSession (not DeepAgentSession) carrying tenantContext binds via interface")
    void testInvoke_withPlainAgentSessionTenantContext() {
        TenantContext ctx = TenantContext.builder().tenantId("plain-agent-session-tenant").build();
        AgentSession session = new AgentSession("plain-session-1", null, null).withTenantContext(ctx);

        DeepAgentConfig config = DeepAgentConfig.builder()
                .enableTenantIsolation(true)
                .tenantDataRoot(baseDir.toString())
                .workspacePath(baseDir.toString())
                .build();
        AgentCard card = AgentCard.builder().name("plain_session_tenant_agent").description("test").build();
        Workspace workspace = new Workspace(baseDir.toString(), "cn");
        DeepAgent agent = new DeepAgent(card, config, workspace);

        CwdContext.setTenantRoot("pre-invoke-marker");

        Map<String, Object> result = agent.invoke(Map.of("query", "test", "conversation_id", "s3"), session);

        assertThat(result).isNotNull();
        assertThat(session.getTenantContext()).isEqualTo(ctx);
        assertThat(TenantContextHolder.getCurrentTenant()).isNull();
        assertThat(CwdContext.getTenantRoot()).isNull();
    }

    @Test
    @DisplayName("workspace resolver uses the locator configured on DeepAgentConfig")
    void testWorkspaceResolver_usesLocatorFromConfig() {
        Path customRoot = baseDir.resolve("branch_01").resolve("dept_07").resolve("u_locator");
        DeepAgentConfig config = DeepAgentConfig.builder()
                .enableTenantIsolation(true)
                .tenantDataRoot(baseDir.toString())
                .allowedTenantRoots(List.of(baseDir.toString()))
                .tenantWorkspaceLocator(ctx -> Optional.of(customRoot))
                .workspacePath(baseDir.toString())
                .build();
        AgentCard card = AgentCard.builder().name("locator_agent").description("test").build();
        Workspace workspace = new Workspace(baseDir.toString(), "cn");
        TenantContext ctx = TenantContext.builder().tenantId("u_locator").build();
        DeepAgentSession session = new DeepAgentSession("deep-agent-locator-session").withTenantContext(ctx);

        try (DeepAgent agent = new DeepAgent(card, config, workspace)) {
            assertThat(agent.getWorkspaceResolver()).isNotNull();
            assertThat(agent.getWorkspaceResolver().resolveTenantRoot(ctx))
                    .isEqualTo(customRoot.toAbsolutePath().normalize());
            assertThat(agent.getWorkspaceResolver().resolveSkillRoot(ctx))
                    .isEqualTo(customRoot.resolve("skills").toAbsolutePath().normalize());

            TenantWorkspaceResolver before = agent.getWorkspaceResolver();
            Map<String, Object> result = agent.invoke(
                    Map.of("query", "test", "conversation_id", "s-locator"), session);

            assertThat(result).isNotNull();
            assertThat(agent.getWorkspaceResolver()).isSameAs(before);
        }
    }

    @Test
    @DisplayName("invoke with locator creates the custom tenant directory tree")
    void testInvoke_withLocator_createsCustomTenantDirectory() {
        Path customRoot = baseDir.resolve("branch_01").resolve("dept_07").resolve("u_invoke");
        DeepAgentConfig config = DeepAgentConfig.builder()
                .enableTenantIsolation(true)
                .tenantDataRoot(baseDir.toString())
                .allowedTenantRoots(List.of(baseDir.toString()))
                .tenantWorkspaceLocator(ctx -> Optional.of(customRoot))
                .workspacePath(baseDir.toString())
                .build();
        AgentCard card = AgentCard.builder().name("locator_invoke_agent").description("test").build();
        Workspace workspace = new Workspace(baseDir.toString(), "cn");
        TenantContext ctx = TenantContext.builder().tenantId("u_invoke").workspaceRoot(null).build();
        DeepAgentSession session = new DeepAgentSession("deep-agent-invoke-session").withTenantContext(ctx);

        try (DeepAgent agent = new DeepAgent(card, config, workspace)) {
            Map<String, Object> result = agent.invoke(
                    Map.of("query", "test", "conversation_id", "s-invoke"), session);
            assertThat(result).isNotNull();
        }

        assertThat(customRoot.resolve("skills")).isDirectory();
        assertThat(customRoot.resolve("tmp")).isDirectory();
        assertThat(customRoot.resolve("checkpoints")).isDirectory();
        assertThat(customRoot.resolve("team_memory")).isDirectory();
        assertThat(customRoot.resolve("todo")).isDirectory();
        assertThat(baseDir.resolve("tenants")).doesNotExist();
    }

    @Test
    @DisplayName("resolveWorkspaceRootForTenant returns the locator tenant path")
    void testResolveWorkspaceRootForTenant_withLocator() {
        Path customRoot = baseDir.resolve("branch_02").resolve("dept_08").resolve("u_direct");
        DeepAgentConfig config = DeepAgentConfig.builder()
                .enableTenantIsolation(true)
                .tenantDataRoot(baseDir.toString())
                .allowedTenantRoots(List.of(baseDir.toString()))
                .tenantWorkspaceLocator(ctx -> Optional.of(customRoot))
                .workspacePath(baseDir.toString())
                .build();
        AgentCard card = AgentCard.builder().name("direct_resolve_agent").description("test").build();
        Workspace workspace = new Workspace(baseDir.toString(), "cn");
        TenantContext ctx = TenantContext.builder().tenantId("u_direct").build();

        try (DeepAgent agent = new DeepAgent(card, config, workspace)) {
            assertThat(agent.resolveWorkspaceRootForTenant(ctx))
                    .isEqualTo(customRoot.toAbsolutePath().normalize());
        }
    }

    @Test
    @DisplayName("resolveWorkspaceRootForTenant falls back to the static workspace root")
    void testResolveWorkspaceRootForTenant_withoutTenantOrIsolation_returnsStaticRoot() {
        Workspace workspace = new Workspace(baseDir.toString(), "cn");
        AgentCard disabledCard = AgentCard.builder().name("disabled_tenant_agent").description("test").build();
        DeepAgentConfig disabledConfig = DeepAgentConfig.builder()
                .workspacePath(baseDir.toString())
                .build();
        TenantContext ctx = TenantContext.builder().tenantId("tenant_ignored").build();

        try (DeepAgent agent = new DeepAgent(disabledCard, disabledConfig, workspace)) {
            assertThat(agent.resolveWorkspaceRootForTenant(null)).isEqualTo(workspace.root());
            assertThat(agent.resolveWorkspaceRootForTenant(ctx)).isEqualTo(workspace.root());
        }

        AgentCard enabledCard = AgentCard.builder().name("enabled_tenant_agent").description("test").build();
        DeepAgentConfig enabledConfig = DeepAgentConfig.builder()
                .enableTenantIsolation(true)
                .tenantDataRoot(baseDir.toString())
                .workspacePath(baseDir.toString())
                .build();
        try (DeepAgent agent = new DeepAgent(enabledCard, enabledConfig, workspace)) {
            assertThat(agent.resolveWorkspaceRootForTenant(null)).isEqualTo(workspace.root());
            assertThat(agent.resolveWorkspaceRootForTenant(TenantContext.builder().tenantId(null).build()))
                    .isEqualTo(workspace.root());
        }
    }

    @Test
    @DisplayName("resolveWorkspaceRootForCurrentTenant follows the bound tenant context")
    void testResolveWorkspaceRootForCurrentTenant_withBoundTenant() {
        Path customRoot = baseDir.resolve("branch_03").resolve("dept_09").resolve("u_current");
        DeepAgentConfig config = DeepAgentConfig.builder()
                .enableTenantIsolation(true)
                .tenantDataRoot(baseDir.toString())
                .allowedTenantRoots(List.of(baseDir.toString()))
                .tenantWorkspaceLocator(ctx -> Optional.of(customRoot))
                .workspacePath(baseDir.toString())
                .build();
        AgentCard card = AgentCard.builder().name("current_resolve_agent").description("test").build();
        Workspace workspace = new Workspace(baseDir.toString(), "cn");
        TenantContext ctx = TenantContext.builder().tenantId("u_current").build();

        try (DeepAgent agent = new DeepAgent(card, config, workspace)) {
            assertThat(agent.resolveWorkspaceRootForCurrentTenant()).isEqualTo(workspace.root());

            TenantContextHolder.setCurrentTenant(ctx);
            assertThat(agent.resolveWorkspaceRootForCurrentTenant())
                    .isEqualTo(customRoot.toAbsolutePath().normalize());
        }
    }
}
