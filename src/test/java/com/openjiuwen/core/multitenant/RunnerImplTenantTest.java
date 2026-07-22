package com.openjiuwen.core.multitenant;

import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.RunnerImpl;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.AgentGroupSessionApi;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.sysop.cwd.CwdContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RunnerImpl TenantContext Tests (§10.1)")
class RunnerImplTenantTest {

    @TempDir
    Path tempDir;

    private RunnerImpl runner;
    private final List<String> sessionsToRelease = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
    }

    @AfterEach
    void tearDown() {
        for (String sid : sessionsToRelease) {
            CheckpointerFactory.getCheckpointer().release(sid);
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

    private TenantContext invokeResolveTenantContext(Object session, TenantContext explicitCtx) throws Exception {
        Method method = RunnerImpl.class.getDeclaredMethod("resolveTenantContext", Object.class, TenantContext.class);
        method.setAccessible(true);
        return (TenantContext) method.invoke(runner, session, explicitCtx);
    }

    private void invokeBindTenantContext(TenantContext ctx) throws Exception {
        Method method = RunnerImpl.class.getDeclaredMethod("bindTenantContext", TenantContext.class);
        method.setAccessible(true);
        method.invoke(runner, ctx);
    }

    private void invokeUnbindTenantContext() throws Exception {
        Method method = RunnerImpl.class.getDeclaredMethod("unbindTenantContext");
        method.setAccessible(true);
        method.invoke(runner);
    }

    @Test
    @DisplayName("resolveTenantContext: session has TenantContext → returns session context")
    void testResolveTenantContext_sessionHasContext_returnsSessionContext() throws Exception {
        runner = new RunnerImpl("resolve-test", RunnerConfig.builder().distributedMode(false).build());
        TenantContext sessionCtx = TenantContext.builder().tenantId("session_ctx").build();
        AgentSessionApi session = new AgentSessionApi("s1").withTenantContext(sessionCtx);

        TenantContext result = invokeResolveTenantContext(session, null);

        assertThat(result).isNotNull();
        assertThat(result.getTenantId()).isEqualTo("session_ctx");
    }

    @Test
    @DisplayName("resolveTenantContext: explicit TenantContext overrides session context")
    void testResolveTenantContext_explicitOverridesSession() throws Exception {
        runner = new RunnerImpl("resolve-test", RunnerConfig.builder().distributedMode(false).build());
        TenantContext sessionCtx = TenantContext.builder().tenantId("session_ctx").build();
        TenantContext explicitCtx = TenantContext.builder().tenantId("explicit_ctx").build();
        AgentSessionApi session = new AgentSessionApi("s1").withTenantContext(sessionCtx);

        TenantContext result = invokeResolveTenantContext(session, explicitCtx);

        assertThat(result).isNotNull();
        assertThat(result.getTenantId()).isEqualTo("explicit_ctx");
    }

    @Test
    @DisplayName("resolveTenantContext: both null → returns null")
    void testResolveTenantContext_bothNull_returnsNull() throws Exception {
        runner = new RunnerImpl("resolve-test", RunnerConfig.builder().distributedMode(false).build());

        TenantContext result = invokeResolveTenantContext(null, null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("bindTenantContext: sets TenantContextHolder")
    void testBindTenantContext_setsTenantContextHolder() throws Exception {
        runner = new RunnerImpl("bind-test", RunnerConfig.builder().distributedMode(false).build());
        TenantContext ctx = TenantContext.builder().tenantId("bind_tenant").build();

        invokeBindTenantContext(ctx);

        assertThat(TenantContextHolder.getCurrentTenant()).isNotNull();
        assertThat(TenantContextHolder.getCurrentTenant().getTenantId()).isEqualTo("bind_tenant");

        invokeUnbindTenantContext();
    }

    @Test
    @DisplayName("unbindTenantContext: clears TenantContextHolder and CwdContext")
    void testUnbindTenantContext_clearsTenantContextHolder() throws Exception {
        runner = new RunnerImpl("unbind-test", RunnerConfig.builder().distributedMode(false).build());
        TenantContext ctx = TenantContext.builder().tenantId("unbind_tenant").build();

        invokeBindTenantContext(ctx);
        assertThat(TenantContextHolder.getCurrentTenant()).isNotNull();

        invokeUnbindTenantContext();

        assertThat(TenantContextHolder.getCurrentTenant()).isNull();
        assertThat(CwdContext.getTenantRoot()).isNull();
    }

    @Test
    @DisplayName("bindTenantContext: sets CwdContext when workspaceResolver is available")
    void testBindTenantContext_setsCwdContext() throws Exception {
        RunnerConfig config = RunnerConfig.builder().distributedMode(false)
                .enableTenantIsolation(true).tenantDataRoot(tempDir.toString()).build();
        runner = new RunnerImpl("cwd-test", config);
        runner.start();

        TenantContext ctx = TenantContext.builder().tenantId("cwd_tenant").build();

        invokeBindTenantContext(ctx);

        assertThat(CwdContext.getTenantRoot()).isNotNull();
        assertThat(CwdContext.getTenantRoot()).contains("cwd_tenant");
        assertThat(CwdContext.getWorkspace()).isNotNull();
        assertThat(CwdContext.getWorkspace()).contains("cwd_tenant");

        invokeUnbindTenantContext();
    }

    @Test
    @DisplayName("runAgent with TenantContext: binds during execution, unbinds after")
    void testRunAgent_withTenantCtx_bindsAndUnbinds() {
        RunnerConfig config = RunnerConfig.builder().distributedMode(false)
                .enableTenantIsolation(true).tenantDataRoot(tempDir.toString()).build();
        runner = new RunnerImpl("agent-bind-unbind", config);
        runner.start();

        TenantContext tenantCtx = TenantContext.builder().tenantId("agent_tenant").build();
        TenantCaptureAgent agent = new TenantCaptureAgent();
        String sessionId = "agent_bind_unbind_session";
        trackSession(sessionId);

        Map<String, Object> result = castMap(
                runner.runAgent(agent, Map.of("conversation_id", sessionId), null, null, null, tenantCtx));

        assertThat(result.get("tenant_id")).isEqualTo("agent_tenant");
        assertThat(result.get("tenant_root")).isNotNull();
        assertThat(TenantContextHolder.getCurrentTenant()).isNull();
        assertThat(CwdContext.getTenantRoot()).isNull();
    }

    @Test
    @DisplayName("runAgentStreaming: tenantUnbindIterator delays unbind until iterator exhausted")
    void testRunAgentStreaming_tenantUnbindIterator_delaysUnbind() {
        RunnerConfig config = RunnerConfig.builder().distributedMode(false)
                .enableTenantIsolation(true).tenantDataRoot(tempDir.toString()).build();
        runner = new RunnerImpl("stream-delay", config);
        runner.start();

        TenantContext tenantCtx = TenantContext.builder().tenantId("stream_tenant").build();
        MultiItemStreamAgent agent = new MultiItemStreamAgent();
        String sessionId = "stream_delay_session";
        trackSession(sessionId);

        Iterator<Object> iterator = runner.runAgentStreaming(
                agent, Map.of("conversation_id", sessionId), null, null, null, null, tenantCtx);

        assertThat(TenantContextHolder.getCurrentTenant()).isNotNull();
        assertThat(TenantContextHolder.getCurrentTenant().getTenantId()).isEqualTo("stream_tenant");

        assertThat(iterator.hasNext()).isTrue();
        assertThat(TenantContextHolder.getCurrentTenant()).isNotNull();
        iterator.next();

        assertThat(iterator.hasNext()).isTrue();
        assertThat(TenantContextHolder.getCurrentTenant()).isNotNull();
        iterator.next();

        assertThat(iterator.hasNext()).isTrue();
        assertThat(TenantContextHolder.getCurrentTenant()).isNotNull();
        iterator.next();

        assertThat(TenantContextHolder.getCurrentTenant()).isNull();
        assertThat(CwdContext.getTenantRoot()).isNull();
    }

    @Test
    @DisplayName("runAgentStreaming: tenantUnbindIterator unbinds on exception")
    void testRunAgentStreaming_tenantUnbindIterator_unbindsOnException() {
        RunnerConfig config = RunnerConfig.builder().distributedMode(false)
                .enableTenantIsolation(true).tenantDataRoot(tempDir.toString()).build();
        runner = new RunnerImpl("stream-err", config);
        runner.start();

        TenantContext tenantCtx = TenantContext.builder().tenantId("err_tenant").build();
        FaultyStreamAgent agent = new FaultyStreamAgent();
        String sessionId = "stream_err_session";
        trackSession(sessionId);

        Iterator<Object> iterator = runner.runAgentStreaming(
                agent, Map.of("conversation_id", sessionId), null, null, null, null, tenantCtx);

        assertThatThrownBy(() -> {
            while (iterator.hasNext()) {
                iterator.next();
            }
        }).isInstanceOf(RuntimeException.class);

        assertThat(TenantContextHolder.getCurrentTenant()).isNull();
        assertThat(CwdContext.getTenantRoot()).isNull();
    }

    @Test
    @DisplayName("runAgent without TenantContext: backward compatible, no bind/unbind")
    void testRunAgent_noTenantCtx_backwardCompat() {
        RunnerConfig config = RunnerConfig.builder().distributedMode(false).build();
        runner = new RunnerImpl("no-tenant", config);
        runner.start();

        TenantContextHolder.clearCurrentTenant();
        TenantCaptureAgent agent = new TenantCaptureAgent();
        String sessionId = "no_tenant_session";
        trackSession(sessionId);

        Map<String, Object> result = castMap(
                runner.runAgent(agent, Map.of("conversation_id", sessionId), null, null, null, null));

        assertThat(result.get("tenant_id")).isNull();
        assertThat(TenantContextHolder.getCurrentTenant()).isNull();
    }

    @Test
    @DisplayName("runAgentGroup with TenantContext: verifies TenantContext propagation")
    void testRunAgentGroup_withTenantCtx() {
        RunnerConfig config = RunnerConfig.builder().distributedMode(false)
                .enableTenantIsolation(true).tenantDataRoot(tempDir.toString()).build();
        runner = new RunnerImpl("group-tenant", config);
        runner.start();

        TenantContext tenantCtx = TenantContext.builder().tenantId("group_tenant").build();
        GroupTenantCaptureAgent agent = new GroupTenantCaptureAgent();
        String sessionId = "group_tenant_session";
        trackSession(sessionId);

        Map<String, Object> result = castMap(
                runner.runAgentGroup(agent, Map.of("conversation_id", sessionId), null, null, null, tenantCtx));

        assertThat(result.get("tenant_id")).isEqualTo("group_tenant");
        assertThat(result.get("tenant_root")).isNotNull();
        assertThat(TenantContextHolder.getCurrentTenant()).isNull();
    }

    @Test
    @DisplayName("runWorkflow with TenantContext: skipped — requires registered Workflow instance")
    void testRunWorkflow_withTenantCtx() {
        Assumptions.assumeTrue(false,
                "runWorkflow with TenantContext requires a registered Workflow instance; skipped in unit test");
    }

    private static class TenantCaptureAgent {
        public Map<String, Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
            TenantContext current = TenantContextHolder.getCurrentTenant();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tenant_id", current != null ? current.getTenantId() : null);
            result.put("tenant_root", CwdContext.getTenantRoot());
            return result;
        }

        public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session) {
            TenantContext current = TenantContextHolder.getCurrentTenant();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tenant_id", current != null ? current.getTenantId() : null);
            result.put("tenant_root", CwdContext.getTenantRoot());
            return List.<Object>of(result).iterator();
        }
    }

    private static class MultiItemStreamAgent {
        public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session) {
            List<Object> items = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                items.add(Map.of("item", i));
            }
            return items.iterator();
        }
    }

    private static class FaultyStreamAgent {
        public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session) {
            return new Iterator<Object>() {
                private int count = 0;

                @Override
                public boolean hasNext() {
                    return count < 2;
                }

                @Override
                public Object next() {
                    count++;
                    if (count == 1) {
                        return Map.of("item", 1);
                    }
                    throw new RuntimeException("stream iteration error for test");
                }
            };
        }
    }

    private static class GroupTenantCaptureAgent {
        public Object invoke(Object inputs, Object session) {
            TenantContext current = TenantContextHolder.getCurrentTenant();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tenant_id", current != null ? current.getTenantId() : null);
            result.put("tenant_root", CwdContext.getTenantRoot());
            return result;
        }
    }
}
