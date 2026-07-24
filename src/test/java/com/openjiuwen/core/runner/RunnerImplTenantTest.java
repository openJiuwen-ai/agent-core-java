package com.openjiuwen.core.runner;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.multitenant.TenantWorkspaceResolver;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.sysop.cwd.CwdContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("RunnerImpl TenantContext Tests")
class RunnerImplTenantTest {

    @TempDir
    Path tempDir;

    private RunnerImpl runner;
    private final List<String> sessionsToRelease = new ArrayList<>();

    @BeforeEach
    void setup() {
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
    }

    @AfterEach
    void tearDown() {
        for (String sessionId : sessionsToRelease) {
            CheckpointerFactory.getCheckpointer().release(sessionId);
        }
        sessionsToRelease.clear();
        if (runner != null) {
            runner.stop();
        }
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
        RunnerConfig.setRunnerConfig(null);
        CheckpointerFactory.setDefaultCheckpointer(null);
    }

    private void trackSession(String sessionId) {
        sessionsToRelease.add(sessionId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private <T> List<T> collect(Iterator<T> iterator) {
        List<T> values = new ArrayList<>();
        iterator.forEachRemaining(values::add);
        return values;
    }

    @Nested
    @DisplayName("runAgent with TenantContext")
    class RunAgentTenantContext {

        @Test
        @DisplayName("runAgent with TenantContext sets TenantContextHolder during execution")
        void testRunAgentSetsTenantContextHolder() {
            RunnerConfig config = RunnerConfig.builder().distributedMode(false)
                    .enableTenantIsolation(true).tenantDataRoot(tempDir.toString()).build();
            runner = new RunnerImpl("tenant-runner", config);
            runner.start();

            TenantContext tenantCtx = TenantContext.builder().tenantId("abc123").build();
            TenantCaptureAgent agent = new TenantCaptureAgent();
            String sessionId = "tenant-session-1";
            trackSession(sessionId);

            Map<String, Object> result = castMap(
                    runner.runAgent(agent, Map.of("conversation_id", sessionId), null, null, null, tenantCtx));

            assertThat(result.get("tenant_id")).isEqualTo("abc123");
            assertThat(result.get("tenant_root")).isNotNull();
            assertThat((String) result.get("tenant_root")).contains("tenants").contains("abc123");
        }

        @Test
        @DisplayName("runAgent with null TenantContext does not set TenantContextHolder")
        void testRunAgentNullTenantBackwardCompat() {
            RunnerConfig config = RunnerConfig.builder().distributedMode(false).build();
            runner = new RunnerImpl("no-tenant-runner", config);
            runner.start();

            TenantCaptureAgent agent = new TenantCaptureAgent();
            String sessionId = "no-tenant-session";
            trackSession(sessionId);

            TenantContextHolder.clearCurrentTenant();
            Map<String, Object> result = castMap(
                    runner.runAgent(agent, Map.of("conversation_id", sessionId), null, null, null, null));

            assertThat(result.get("tenant_id")).isNull();
        }

        @Test
        @DisplayName("runAgent with TenantContext sets CwdContext TENANT_ROOT when workspaceResolver is available")
        void testRunAgentSetsCwdContextTenantRoot() {
            RunnerConfig config = RunnerConfig.builder().distributedMode(false)
                    .enableTenantIsolation(true).tenantDataRoot(tempDir.toString()).build();
            runner = new RunnerImpl("cwd-tenant-runner", config);
            runner.start();

            TenantContext tenantCtx = TenantContext.builder().tenantId("org01").build();
            TenantCaptureAgent agent = new TenantCaptureAgent();
            String sessionId = "cwd-tenant-session";
            trackSession(sessionId);

            Map<String, Object> result = castMap(
                    runner.runAgent(agent, Map.of("conversation_id", sessionId), null, null, null, tenantCtx));

            assertThat(result.get("tenant_root")).isNotNull();
            String tenantRoot = (String) result.get("tenant_root");
            assertThat(tenantRoot).contains("tenants").contains("org01");
            assertThat(tenantRoot).doesNotContain("workspace");
        }

        @Test
        @DisplayName("runAgent clears TenantContextHolder after execution even on error")
        void testRunAgentClearsContextOnError() {
            RunnerConfig config = RunnerConfig.builder().distributedMode(false)
                    .enableTenantIsolation(true).tenantDataRoot(tempDir.toString()).build();
            runner = new RunnerImpl("error-runner", config);
            runner.start();

            TenantContext tenantCtx = TenantContext.builder().tenantId("error_tenant").build();
            ThrowingAgent agent = new ThrowingAgent();

            assertThatCode(() -> runner.runAgent(agent, Map.of(), null, null, null, tenantCtx))
                    .isInstanceOf(RuntimeException.class);

            assertThat(TenantContextHolder.getCurrentTenant()).isNull();
            assertThat(CwdContext.getTenantRoot()).isNull();
        }

        @Test
        @DisplayName("runAgent with tenantContext in AgentSessionApi sets TenantContextHolder")
        void testRunAgent_withSessionTenantContext() {
            RunnerConfig config = RunnerConfig.builder().distributedMode(false)
                    .enableTenantIsolation(true).tenantDataRoot(tempDir.toString()).build();
            runner = new RunnerImpl("session-tenant-runner", config);
            runner.start();

            TenantContext ctx = TenantContext.builder().tenantId("session-tenant").build();
            AgentSessionApi session = new AgentSessionApi("session-tenant-s1").withTenantContext(ctx);
            TenantCaptureAgent agent = new TenantCaptureAgent();
            trackSession("session-tenant-s1");

            Map<String, Object> result = castMap(
                    runner.runAgent(agent, Map.of("conversation_id", "session-tenant-s1"), session, null, null, null));

            assertThat(result.get("tenant_id")).isEqualTo("session-tenant");
            assertThat(result.get("tenant_root")).isNotNull();
            assertThat((String) result.get("tenant_root")).contains("tenants").contains("session-tenant");
        }

        @Test
        @DisplayName("runAgent without tenantContext in AgentSessionApi and null TenantContext does not set TenantContextHolder")
        void testRunAgent_withoutSessionTenantContext_backwardCompat() {
            RunnerConfig config = RunnerConfig.builder().distributedMode(false).build();
            runner = new RunnerImpl("no-session-tenant-runner", config);
            runner.start();

            AgentSessionApi session = new AgentSessionApi("no-session-tenant-s1");
            TenantCaptureAgent agent = new TenantCaptureAgent();
            trackSession("no-session-tenant-s1");

            TenantContextHolder.clearCurrentTenant();
            Map<String, Object> result = castMap(
                    runner.runAgent(agent, Map.of("conversation_id", "no-session-tenant-s1"), session, null, null, null));

            assertThat(result.get("tenant_id")).isNull();
        }

        @Test
        @DisplayName("runAgent explicit TenantContext takes priority over session tenantContext")
        void testRunAgent_explicitTenantCtxPriorityOverSession() {
            RunnerConfig config = RunnerConfig.builder().distributedMode(false)
                    .enableTenantIsolation(true).tenantDataRoot(tempDir.toString()).build();
            runner = new RunnerImpl("priority-tenant-runner", config);
            runner.start();

            TenantContext sessionCtx = TenantContext.builder().tenantId("session-priority-tenant").build();
            TenantContext explicitCtx = TenantContext.builder().tenantId("explicit-priority-tenant").build();
            AgentSessionApi session = new AgentSessionApi("priority-s1").withTenantContext(sessionCtx);
            TenantCaptureAgent agent = new TenantCaptureAgent();
            trackSession("priority-s1");

            Map<String, Object> result = castMap(
                    runner.runAgent(agent, Map.of("conversation_id", "priority-s1"), session, null, null, explicitCtx));

            assertThat(result.get("tenant_id")).isEqualTo("explicit-priority-tenant");
            assertThat(result.get("tenant_root")).isNotNull();
            assertThat((String) result.get("tenant_root")).contains("tenants").contains("explicit-priority-tenant");
        }
    }

    @Nested
    @DisplayName("RunnerImpl.start() with tenant isolation config")
    class StartTenantIsolation {

        @Test
        @DisplayName("start() with enableTenantIsolation=true initializes workspaceResolver")
        void testStartWithTenantIsolationEnabled() {
            RunnerConfig config = RunnerConfig.builder().distributedMode(false)
                    .enableTenantIsolation(true).tenantDataRoot(tempDir.toString()).build();
            runner = new RunnerImpl("tenant-start-runner", config);
            runner.start();

            TenantContext tenantCtx = TenantContext.builder().tenantId("init_test").build();
            TenantCaptureAgent agent = new TenantCaptureAgent();
            String sessionId = "init-session";
            trackSession(sessionId);

            Map<String, Object> result = castMap(
                    runner.runAgent(agent, Map.of("conversation_id", sessionId), null, null, null, tenantCtx));

            assertThat(result.get("tenant_root")).isNotNull();
            assertThat((String) result.get("tenant_root")).contains("tenants").contains("init_test");
        }

        @Test
        @DisplayName("start() with enableTenantIsolation=false does not initialize workspaceResolver")
        void testStartWithoutTenantIsolation() {
            RunnerConfig config = RunnerConfig.builder().distributedMode(false).build();
            runner = new RunnerImpl("no-isolation-runner", config);
            runner.start();

            TenantContextHolder.clearCurrentTenant();
            TenantContext tenantCtx = TenantContext.builder().tenantId("no_iso_tenant").build();
            TenantCaptureAgent agent = new TenantCaptureAgent();
            String sessionId = "no-iso-session";
            trackSession(sessionId);

            Map<String, Object> result = castMap(
                    runner.runAgent(agent, Map.of("conversation_id", sessionId), null, null, null, tenantCtx));

            assertThat(result.get("tenant_root")).isNull();
            assertThat(result.get("tenant_id")).isEqualTo("no_iso_tenant");
        }
    }

    @Nested
    @DisplayName("runAgentStreaming with TenantContext")
    class RunAgentStreamingTenantContext {

        @Test
        @DisplayName("runAgentStreaming with TenantContext propagates context")
        void testRunAgentStreamingPropagatesTenantContext() {
            RunnerConfig config = RunnerConfig.builder().distributedMode(false)
                    .enableTenantIsolation(true).tenantDataRoot(tempDir.toString()).build();
            runner = new RunnerImpl("stream-tenant-runner", config);
            runner.start();

            TenantContext tenantCtx = TenantContext.builder().tenantId("stream_tenant").build();
            TenantCaptureAgent agent = new TenantCaptureAgent();
            String sessionId = "stream-tenant-session";
            trackSession(sessionId);

            Iterator<Object> iterator = runner.runAgentStreaming(
                    agent, Map.of("conversation_id", sessionId), null, null, null, null, tenantCtx);
            List<Object> chunks = collect(iterator);

            assertThat(chunks).hasSize(1);
            Map<String, Object> chunk = castMap(chunks.get(0));
            assertThat(chunk.get("tenant_id")).isEqualTo("stream_tenant");
        }

        @Test
        @DisplayName("runAgentStreaming with tenantContext in AgentSessionApi propagates context")
        void testRunAgentStreaming_withSessionTenantContext() {
            RunnerConfig config = RunnerConfig.builder().distributedMode(false)
                    .enableTenantIsolation(true).tenantDataRoot(tempDir.toString()).build();
            runner = new RunnerImpl("stream-session-tenant-runner", config);
            runner.start();

            TenantContext ctx = TenantContext.builder().tenantId("stream_session_tenant").build();
            AgentSessionApi session = new AgentSessionApi("stream-session-s1").withTenantContext(ctx);
            TenantCaptureAgent agent = new TenantCaptureAgent();
            trackSession("stream-session-s1");

            Iterator<Object> iterator = runner.runAgentStreaming(
                    agent, Map.of("conversation_id", "stream-session-s1"), session, null, null, null, null);
            List<Object> chunks = collect(iterator);

            assertThat(chunks).hasSize(1);
            Map<String, Object> chunk = castMap(chunks.get(0));
            assertThat(chunk.get("tenant_id")).isEqualTo("stream_session_tenant");
            assertThat(chunk.get("tenant_root")).isNotNull();
            assertThat((String) chunk.get("tenant_root")).contains("tenants").contains("stream_session_tenant");
        }
    }

    private static class TenantCaptureAgent {
        public Map<String, Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
            TenantContext currentTenant = TenantContextHolder.getCurrentTenant();
            String tenantId = currentTenant != null ? currentTenant.getTenantId() : null;
            String tenantRoot = CwdContext.getTenantRoot();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("session_id", session.getSessionId());
            result.put("tenant_id", tenantId);
            result.put("tenant_root", tenantRoot);
            return result;
        }

        public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session) {
            TenantContext currentTenant = TenantContextHolder.getCurrentTenant();
            String tenantId = currentTenant != null ? currentTenant.getTenantId() : null;
            String tenantRoot = CwdContext.getTenantRoot();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("session_id", session.getSessionId());
            result.put("tenant_id", tenantId);
            result.put("tenant_root", tenantRoot);
            return List.<Object>of(result).iterator();
        }
    }

    private static class ThrowingAgent {
        public Map<String, Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
            throw new RuntimeException("intentional error for test");
        }
    }
}
