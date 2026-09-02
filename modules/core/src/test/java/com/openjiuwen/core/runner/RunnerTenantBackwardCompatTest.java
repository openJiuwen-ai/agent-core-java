package com.openjiuwen.core.runner;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.sysop.cwd.CwdContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@DisplayName("Runner Tenant Backward Compatibility + Integration Tests")
class RunnerTenantBackwardCompatTest {
    private final List<String> sessionsToRelease = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
    }

    @AfterEach
    void tearDown() {
        for (String sessionId : sessionsToRelease) {
            CheckpointerFactory.getCheckpointer().release(sessionId);
        }
        sessionsToRelease.clear();
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
        CheckpointerFactory.setDefaultCheckpointer(null);
        RunnerConfig.setRunnerConfig(null);
    }

    private void trackSession(String sessionId) {
        sessionsToRelease.add(sessionId);
    }

    @Nested
    @DisplayName("Backward compatibility without TenantContext")
    class BackwardCompatWithoutTenant {
        @Test
        @DisplayName("RunnerImpl.runAgent without TenantContext identical to original")
        void testRunAgentNoTenantIdenticalToOriginal() {
            RunnerConfig config = RunnerConfig.builder().distributedMode(false).build();
            RunnerImpl runner = new RunnerImpl("compat-runner", config);
            runner.start();

            CompatAgent agent = new CompatAgent();
            String sessionId = "compat-session";
            trackSession(sessionId);

            runner.getResourceMgr().addAgent(
                com.openjiuwen.core.singleagent.schema.AgentCard.builder()
                    .id("compat-agent").name("compat-agent").build(),
                () -> agent, null);

            Map<String, Object> result = castMap(
                runner.runAgent("compat-agent", Map.of("conversation_id", sessionId), null, null, null));

            assertThat(result.get("session_id")).isEqualTo(sessionId);
            assertThat(result.get("captured_tenant_id")).isNull();
            assertThat(TenantContextHolder.getCurrentTenant()).isNull();
            assertThat(CwdContext.getTenantRoot()).isNull();

            runner.stop();
        }

        @Test
        @DisplayName("RunnerConfig.DEFAULT has enableTenantIsolation=false")
        void testDefaultConfigNoTenantIsolation() {
            assertThat(RunnerConfig.DEFAULT.isEnableTenantIsolation()).isFalse();
            assertThat(RunnerConfig.DEFAULT.getTenantDataRoot()).isNull();
        }

        @Test
        @DisplayName("Runner without enableTenantIsolation does not set tenant root")
        void testStartNoTenantIsolationNoResolver() {
            RunnerConfig config = RunnerConfig.builder().distributedMode(false).build();
            RunnerImpl runner = new RunnerImpl("no-resolver-runner", config);
            runner.start();

            CompatAgent agent = new CompatAgent();
            String sessionId = "no-resolver-compat-session";
            trackSession(sessionId);

            runner.getResourceMgr().addAgent(
                com.openjiuwen.core.singleagent.schema.AgentCard.builder()
                    .id("no-resolver-agent").name("no-resolver-agent").build(),
                () -> agent, null);

            TenantContext tenantCtx = TenantContext.builder().tenantId("should_not_resolve").build();

            Map<String, Object> result = castMap(
                runner.runAgent("no-resolver-agent",
                    Map.of("conversation_id", sessionId), null, null, null, tenantCtx));

            assertThat(result.get("captured_tenant_id")).isEqualTo("should_not_resolve");
            assertThat(result.get("captured_tenant_root")).isNull();

            assertThat(TenantContextHolder.getCurrentTenant()).isNull();
            assertThat(CwdContext.getTenantRoot()).isNull();
            runner.stop();
        }
    }

    @Nested
    @DisplayName("Checkpointer auto-prefixing with TenantContext")
    class CheckpointerAutoPrefixing {
        @Test
        @DisplayName("InMemoryCheckpointer auto-prefixes when TenantContext is set via Runner")
        void testCheckpointerAutoPrefixViaRunner() {
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .enableTenantIsolation(true)
                    .tenantDataRoot(System.getProperty("user.dir"))
                    .build();
            RunnerImpl runner = new RunnerImpl("cp-tenant-runner", config);
            runner.start();

            TenantContext tenantCtx = TenantContext.builder().tenantId("cp_tenant").build();
            CompatAgent agent = new CompatAgent();
            String sessionId = "cp-tenant-session";
            trackSession(sessionId);

            runner.getResourceMgr().addAgent(
                com.openjiuwen.core.singleagent.schema.AgentCard.builder()
                    .id("cp-agent").name("cp-agent").build(),
                () -> agent, null);

            Map<String, Object> result = castMap(
                runner.runAgent("cp-agent",
                    Map.of("conversation_id", sessionId), null, null, null, tenantCtx));

            assertThat(result.get("captured_tenant_id")).isEqualTo("cp_tenant");

            assertThat(TenantContextHolder.getCurrentTenant()).isNull();
            runner.stop();
        }
    }

    @Nested
    @DisplayName("Full chain integration")
    class FullChainIntegration {
        @Test
        @DisplayName("Runner.start() + runAgent + TenantContext full chain")
        void testFullChainRunnerAgentTenant() {
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .enableTenantIsolation(true)
                    .tenantDataRoot(System.getProperty("user.dir"))
                    .build();
            RunnerImpl runner = new RunnerImpl("full-chain-runner", config);
            runner.start();

            TenantContext tenantCtx = TenantContext.builder().tenantId("full_chain_tenant").build();
            CompatAgent agent = new CompatAgent();
            String sessionId = "full-chain-session";
            trackSession(sessionId);

            runner.getResourceMgr().addAgent(
                com.openjiuwen.core.singleagent.schema.AgentCard.builder()
                    .id("full-chain-agent").name("full-chain-agent").build(),
                () -> agent, null);

            Map<String, Object> result = castMap(
                runner.runAgent("full-chain-agent",
                    Map.of("conversation_id", sessionId), null, null, null, tenantCtx));

            assertThat(result.get("session_id")).isEqualTo(sessionId);
            assertThat(result.get("captured_tenant_id")).isEqualTo("full_chain_tenant");
            assertThat(result.get("captured_tenant_root")).isNotNull();
            assertThat((String) result.get("captured_tenant_root")).contains("tenants");
            assertThat((String) result.get("captured_tenant_root")).contains("full_chain_tenant");

            assertThat(TenantContextHolder.getCurrentTenant()).isNull();
            assertThat(CwdContext.getTenantRoot()).isNull();
            runner.stop();
        }

        @Test
        @DisplayName("Different TenantContexts in same Runner produce different tenant roots")
        void testDifferentTenantsDifferentRoots() {
            RunnerConfig config = RunnerConfig.builder()
                    .distributedMode(false)
                    .enableTenantIsolation(true)
                    .tenantDataRoot(System.getProperty("user.dir"))
                    .build();
            RunnerImpl runner = new RunnerImpl("multi-tenant-runner", config);
            runner.start();

            CompatAgent agent = new CompatAgent();

            runner.getResourceMgr().addAgent(
                com.openjiuwen.core.singleagent.schema.AgentCard.builder()
                    .id("multi-agent").name("multi-agent").build(),
                () -> agent, null);

            TenantContext tenantA = TenantContext.builder().tenantId("tenant_a").build();
            TenantContext tenantB = TenantContext.builder().tenantId("tenant_b").build();

            String sessionA = "multi-session-a";
            String sessionB = "multi-session-b";
            trackSession(sessionA);
            trackSession(sessionB);

            Map<String, Object> resultA = castMap(
                runner.runAgent("multi-agent",
                    Map.of("conversation_id", sessionA), null, null, null, tenantA));
            Map<String, Object> resultB = castMap(
                runner.runAgent("multi-agent",
                    Map.of("conversation_id", sessionB), null, null, null, tenantB));

            assertThat(resultA.get("captured_tenant_root")).isNotEqualTo(resultB.get("captured_tenant_root"));
            assertThat((String) resultA.get("captured_tenant_root")).contains("tenant_a");
            assertThat((String) resultB.get("captured_tenant_root")).contains("tenant_b");

            assertThat(TenantContextHolder.getCurrentTenant()).isNull();
            runner.stop();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static class CompatAgent {
        public Map<String, Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("session_id", session.getSessionId());
            result.put("captured_tenant_id",
                TenantContextHolder.getCurrentTenant() != null
                    ? TenantContextHolder.getCurrentTenant().getTenantId() : null);
            result.put("captured_tenant_root", CwdContext.getTenantRoot());
            return result;
        }
    }
}
