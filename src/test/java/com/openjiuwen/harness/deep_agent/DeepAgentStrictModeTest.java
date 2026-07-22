package com.openjiuwen.harness.deep_agent;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.cwd.CwdContext;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeepAgentStrictModeTest {

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

    private DeepAgent newStrictModeAgent() {
        DeepAgentConfig config = DeepAgentConfig.builder()
                .enableTenantIsolation(true)
                .tenantDataRoot(baseDir.toString())
                .workspacePath(baseDir.toString())
                .build();
        AgentCard card = AgentCard.builder().name("strict_mode_agent").description("test").build();
        Workspace workspace = Workspace.builder().rootPath(baseDir.toString()).language("cn").build();
        return new DeepAgent(card, config, workspace);
    }

    private DeepAgent newNonStrictAgent() {
        DeepAgentConfig config = DeepAgentConfig.builder()
                .workspacePath(baseDir.toString())
                .build();
        AgentCard card = AgentCard.builder().name("non_strict_agent").description("test").build();
        Workspace workspace = Workspace.builder().rootPath(baseDir.toString()).language("cn").build();
        return new DeepAgent(card, config, workspace);
    }

    @Test
    @DisplayName("strict mode: invoke(inputs, session) without tenantContext throws IllegalStateException")
    void test_invoke_withSession_strictMode_noTenantId_throws() {
        DeepAgent agent = newStrictModeAgent();
        AgentSessionApi session = new AgentSessionApi("strict-session-1");

        assertThatThrownBy(() -> agent.invoke(Map.of("query", "test", "conversation_id", "s1"), session))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant isolation is enabled")
                .hasMessageContaining("no tenantId was provided");
    }

    @Test
    @DisplayName("strict mode: invoke(inputs, null TenantContext) throws IllegalStateException")
    void test_invoke_withTenantContext_strictMode_nullContext_throws() {
        DeepAgent agent = newStrictModeAgent();

        assertThatThrownBy(() -> agent.invoke(Map.of("query", "test", "conversation_id", "s2"), (TenantContext) null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tenantId was provided");
    }

    @Test
    @DisplayName("strict mode: invoke(inputs, TenantContext with empty tenantId) throws IllegalStateException")
    void test_invoke_withTenantContext_strictMode_emptyTenantId_throws() {
        DeepAgent agent = newStrictModeAgent();
        TenantContext emptyCtx = TenantContext.builder().tenantId("").build();

        assertThatThrownBy(() -> agent.invoke(Map.of("query", "test", "conversation_id", "s3"), emptyCtx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tenantId was provided");
    }

    @Test
    @DisplayName("strict mode: stream(inputs, null TenantContext) throws IllegalStateException")
    void test_stream_withTenantContext_strictMode_nullContext_throws() {
        DeepAgent agent = newStrictModeAgent();

        assertThatThrownBy(() -> agent.stream(Map.of("query", "test", "conversation_id", "s4"), (TenantContext) null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tenantId was provided");
    }

    @Test
    @DisplayName("strict mode: stream(inputs, modes, null TenantContext) throws IllegalStateException")
    void test_stream_withModesAndTenantContext_strictMode_nullContext_throws() {
        DeepAgent agent = newStrictModeAgent();

        assertThatThrownBy(() -> agent.stream(
                Map.of("query", "test", "conversation_id", "s5"),
                List.of(StreamMode.OUTPUT),
                (TenantContext) null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tenantId was provided");
    }

    @Test
    @DisplayName("strict mode: stream(inputs, session without tenantContext) throws IllegalStateException")
    void test_stream_withSession_strictMode_noTenantId_throws() {
        DeepAgent agent = newStrictModeAgent();
        AgentSessionApi session = new AgentSessionApi("strict-stream-session");

        assertThatThrownBy(() -> agent.stream(
                Map.of("query", "test", "conversation_id", "s6"),
                session,
                List.of(StreamMode.OUTPUT)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no tenantId was provided");
    }

    @Test
    @DisplayName("strict mode: valid tenantId invoke(inputs, TenantContext) does not throw IllegalStateException")
    void test_invoke_withTenantContext_strictMode_validTenant_passes() {
        DeepAgent agent = newStrictModeAgent();
        TenantContext ctx = TenantContext.builder().tenantId("valid_tenant_01").build();

        Map<String, Object> result = agent.invoke(Map.of("query", "test", "conversation_id", "s7"), ctx);

        assertThat(result).isNotNull();
        assertThat(TenantContextHolder.getCurrentTenant()).isNull();
    }

    @Test
    @DisplayName("strict mode: valid tenantId invoke(inputs, session.withTenantContext) does not throw")
    void test_invoke_withSession_strictMode_validTenant_passes() {
        DeepAgent agent = newStrictModeAgent();
        TenantContext ctx = TenantContext.builder().tenantId("valid_tenant_02").build();
        AgentSessionApi session = new AgentSessionApi("strict-valid-session").withTenantContext(ctx);

        Map<String, Object> result = agent.invoke(Map.of("query", "test", "conversation_id", "s8"), session);

        assertThat(result).isNotNull();
        assertThat(TenantContextHolder.getCurrentTenant()).isNull();
    }

    @Test
    @DisplayName("non-strict mode: invoke(inputs, session without tenantContext) backward compatible (no throw)")
    void test_invoke_nonStrictMode_noTenantId_backwardCompat() {
        DeepAgent agent = newNonStrictAgent();
        AgentSessionApi session = new AgentSessionApi("non-strict-session");

        CwdContext.setTenantRoot("pre-invoke-marker");

        Map<String, Object> result = agent.invoke(Map.of("query", "test", "conversation_id", "s9"), session);

        assertThat(result).isNotNull();
        assertThat(CwdContext.getTenantRoot()).isEqualTo("pre-invoke-marker");
    }

    @Test
    @DisplayName("non-strict mode: invoke(inputs, null TenantContext) backward compatible (no throw)")
    void test_invoke_nonStrictMode_nullTenantContext_backwardCompat() {
        DeepAgent agent = newNonStrictAgent();

        Map<String, Object> result = agent.invoke(Map.of("query", "test", "conversation_id", "s10"), (TenantContext) null);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("strict mode error message contains actionable guidance")
    void test_errorMessage_containsActionableGuidance() {
        DeepAgent agent = newStrictModeAgent();
        AgentSessionApi session = new AgentSessionApi("msg-guidance-session");

        assertThatThrownBy(() -> agent.invoke(Map.of("query", "test", "conversation_id", "s11"), session))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("enableTenantIsolation")
                .hasMessageContaining("TenantContext");
    }
}
