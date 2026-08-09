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
import java.util.Map;

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
}
