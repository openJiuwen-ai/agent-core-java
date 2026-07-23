/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.server_adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.cwd.CwdContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@DisplayName("AgentAdapter Tenant Tests")
class AgentAdapterTenantTest {

    private final List<String> sessionsToRelease = new ArrayList<>();
    private AgentAdapter adapter;

    @BeforeEach
    void setUp() {
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
        CheckpointerFactory.setDefaultCheckpointer(null);
        RunnerConfig.setRunnerConfig(null);
        RunnerConfig config = RunnerConfig.builder().distributedMode(false).build();
        Runner.setConfig(config);
        Runner.start();

        TenantAwareTestAgent testAgent = new TenantAwareTestAgent();
        Runner.resourceMgr().addAgent(
            AgentCard.builder().id("adapter-test-agent").name("adapter-test-agent").build(),
            () -> testAgent, null);

        adapter = new AgentAdapter("adapter-test-agent");
    }

    @AfterEach
    void tearDown() {
        for (String sessionId : sessionsToRelease) {
            CheckpointerFactory.getCheckpointer().release(sessionId);
        }
        sessionsToRelease.clear();
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
        Runner.stop();
        Runner.setConfig(RunnerConfig.DEFAULT);
    }

    private void trackSession(String sessionId) {
        sessionsToRelease.add(sessionId);
    }

    @Nested
    @DisplayName("AgentAdapter tenantId extraction")
    class TenantIdExtraction {

        @Test
        @DisplayName("handleInvoke extracts tenantId from inputs and creates TenantContext")
        void testHandleInvokeWithTenantId() {
            String sessionId = "adapter-tenant-session";
            trackSession(sessionId);

            Object result = adapter.handleInvoke(
                Map.of("conversation_id", sessionId, "tenant_id", "adapter_tenant"));

            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertThat(resultMap.get("captured_tenant_id")).isEqualTo("adapter_tenant");
            assertThat(TenantContextHolder.getCurrentTenant()).isNull();
        }

        @Test
        @DisplayName("handleInvoke without tenantId backward compat")
        void testHandleInvokeWithoutTenantId() {
            String sessionId = "adapter-no-tenant-session";
            trackSession(sessionId);

            Object result = adapter.handleInvoke(Map.of("conversation_id", sessionId));

            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            assertThat(resultMap.get("captured_tenant_id")).isNull();
        }

        @Test
        @DisplayName("handleStream extracts tenantId from inputs")
        void testHandleStreamWithTenantId() {
            String sessionId = "adapter-stream-tenant-session";
            trackSession(sessionId);

            Iterator<Object> iterator = adapter.handleStream(
                Map.of("conversation_id", sessionId, "tenant_id", "stream_tenant"));

            List<Object> chunks = new ArrayList<>();
            iterator.forEachRemaining(chunks::add);
            assertThat(chunks).hasSize(1);
            @SuppressWarnings("unchecked")
            Map<String, Object> chunk = (Map<String, Object>) chunks.get(0);
            assertThat(chunk.get("captured_tenant_id")).isEqualTo("stream_tenant");
            assertThat(TenantContextHolder.getCurrentTenant()).isNull();
        }

        @Test
        @DisplayName("handleStream without tenantId backward compat")
        void testHandleStreamWithoutTenantId() {
            String sessionId = "adapter-stream-no-tenant-session";
            trackSession(sessionId);

            Iterator<Object> iterator = adapter.handleStream(
                Map.of("conversation_id", sessionId));

            List<Object> chunks = new ArrayList<>();
            iterator.forEachRemaining(chunks::add);
            assertThat(chunks).hasSize(1);
            @SuppressWarnings("unchecked")
            Map<String, Object> chunk = (Map<String, Object>) chunks.get(0);
            assertThat(chunk.get("captured_tenant_id")).isNull();
        }
    }

    private static class TenantAwareTestAgent {
        public Map<String, Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("session_id", session.getSessionId());
            result.put("captured_tenant_id",
                TenantContextHolder.getCurrentTenant() != null
                    ? TenantContextHolder.getCurrentTenant().getTenantId() : null);
            return result;
        }

        public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("session_id", session.getSessionId());
            result.put("captured_tenant_id",
                TenantContextHolder.getCurrentTenant() != null
                    ? TenantContextHolder.getCurrentTenant().getTenantId() : null);
            return List.<Object>of(result).iterator();
        }
    }
}
