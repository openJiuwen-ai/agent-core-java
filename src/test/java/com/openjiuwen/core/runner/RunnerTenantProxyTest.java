package com.openjiuwen.core.runner;

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
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Runner Tenant Proxy Tests")
class RunnerTenantProxyTest {

    @TempDir
    Path tempDir;

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
        Runner.stop();
        Runner.setConfig(RunnerConfig.DEFAULT);
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
    }

    private void trackSession(String sessionId) {
        sessionsToRelease.add(sessionId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @Nested
    @DisplayName("Runner.runAgent with TenantContext")
    class RunAgentTenantProxy {

        @Test
        @DisplayName("Runner.runAgent with TenantContext delegates to RunnerImpl")
        void testRunAgentWithTenantCtx() {
            RunnerConfig config = RunnerConfig.builder().distributedMode(false)
                    .enableTenantIsolation(true).tenantDataRoot(tempDir.toString()).build();
            Runner.setConfig(config);
            Runner.start();

            TenantContext tenantCtx = TenantContext.builder().tenantId("proxy_tenant").build();
            ProxyCaptureAgent agent = new ProxyCaptureAgent();
            String sessionId = "proxy-tenant-session";
            trackSession(sessionId);

            Map<String, Object> result = castMap(
                    Runner.runAgent(agent, Map.of("conversation_id", sessionId), null, null, null, tenantCtx));

            assertThat(result.get("tenant_id")).isEqualTo("proxy_tenant");
            assertThat(result.get("tenant_root")).isNotNull();
            assertThat((String) result.get("tenant_root")).contains("tenants").contains("proxy_tenant");
        }

        @Test
        @DisplayName("Runner.runAgent without TenantContext backward compat")
        void testRunAgentWithoutTenantCtx() {
            RunnerConfig config = RunnerConfig.builder().distributedMode(false).build();
            Runner.setConfig(config);
            Runner.start();

            ProxyCaptureAgent agent = new ProxyCaptureAgent();
            String sessionId = "proxy-no-tenant-session";
            trackSession(sessionId);

            TenantContextHolder.clearCurrentTenant();
            Map<String, Object> result = castMap(
                    Runner.runAgent(agent, Map.of("conversation_id", sessionId), null, null, null));

            assertThat(result.get("tenant_id")).isNull();
        }
    }

    @Nested
    @DisplayName("Runner.runAgentAsync with TenantContext")
    class RunAgentAsyncTenantProxy {

        @Test
        @DisplayName("Runner.runAgentAsync with TenantContext delegates to RunnerImpl")
        void testRunAgentAsyncWithTenantCtx() {
            RunnerConfig config = RunnerConfig.builder().distributedMode(false)
                    .enableTenantIsolation(true).tenantDataRoot(tempDir.toString()).build();
            Runner.setConfig(config);
            Runner.start();

            TenantContext tenantCtx = TenantContext.builder().tenantId("async_tenant").build();
            ProxyCaptureAgent agent = new ProxyCaptureAgent();

            Object result = Runner.runAgentAsync(agent, Map.of("query", "async_test"), null, null, null, tenantCtx)
                    .block();

            Map<String, Object> resultMap = castMap(result);
            assertThat(resultMap.get("tenant_id")).isEqualTo("async_tenant");
        }
    }

    private static class ProxyCaptureAgent {
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
    }
}
