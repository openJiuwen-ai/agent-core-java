/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.RunnerImpl;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.sysop.cwd.CwdContext;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowChunk;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.component.ComponentAbility;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BUG-0006 验收用例：流式入口不得把租户上下文泄漏到绑定线程。
 * <p>
 * 缺陷判据不是「是否流式」，而是「bind 与 unbind 是否落在同一线程、且 unbind 一定被执行」。
 * 本类覆盖验收矩阵 B1-B7；异常路径 B8 与既有
 * {@code RunnerImplTenantTest#testRunAgentStreaming_tenantUnbindIterator_unbindsOnException} 对应。
 *
 * @since 0.1.7
 */
@DisplayName("BUG-0006 流式入口租户解绑作用域测试")
class RunnerStreamingTenantUnbindTest {

    private static final String TENANT_ID = "scope_tenant";

    @TempDir
    Path tempDir;

    private RunnerImpl runner;
    private final List<String> sessionsToRelease = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
        RunnerConfig config = RunnerConfig.builder().distributedMode(false).enableTenantIsolation(true)
                .tenantDataRoot(tempDir.toString()).build();
        runner = new RunnerImpl("stream-tenant-scope", config);
        runner.start();
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

    private TenantContext tenant() {
        return TenantContext.builder().tenantId(TENANT_ID).build();
    }

    private void trackSession(String sessionId) {
        sessionsToRelease.add(sessionId);
    }

    private void assertNoTenantResidue(String where) {
        assertThat(TenantContextHolder.getCurrentTenant()).as("%s: TenantContextHolder 无残留", where).isNull();
        assertThat(CwdContext.getTenantRoot()).as("%s: CwdContext.TENANT_ROOT 无残留", where).isNull();
    }

    @Test
    @DisplayName("B1 7 参 runAgentStreaming 返回后不消费 → 调用线程无租户残留")
    void testRunAgentStreaming_withoutConsumption_hasNoTenantResidueOnCallerThread() {
        String sessionId = "scope-b1-session";
        trackSession(sessionId);

        runner.runAgentStreaming(new MultiItemStreamAgent(), Map.of("conversation_id", sessionId), null, null,
                null, null, tenant());

        assertNoTenantResidue("B1 调用线程");
    }

    @Test
    @DisplayName("B2 7 参 runAgentStreaming 返回后部分消费 → 调用线程无租户残留")
    void testRunAgentStreaming_partialConsumption_hasNoTenantResidueOnCallerThread() {
        String sessionId = "scope-b2-session";
        trackSession(sessionId);

        Iterator<Object> iterator = runner.runAgentStreaming(new MultiItemStreamAgent(),
                Map.of("conversation_id", sessionId), null, null, null, null, tenant());

        assertThat(iterator.hasNext()).isTrue();
        assertThat(iterator.next()).isNotNull();

        assertNoTenantResidue("B2 调用线程（部分消费后）");
    }

    @Test
    @DisplayName("B3 7 参 runAgentStreaming 跨线程消费 → 调用线程与消费线程均无残留")
    void testRunAgentStreaming_crossThreadConsumption_hasNoTenantResidueOnBothThreads() throws Exception {
        String sessionId = "scope-b3-session";
        trackSession(sessionId);

        Iterator<Object> iterator = runner.runAgentStreaming(new MultiItemStreamAgent(),
                Map.of("conversation_id", sessionId), null, null, null, null, tenant());

        AtomicReference<String> consumerTenantAfterRun = new AtomicReference<>("not-executed");
        Thread consumer = new Thread(() -> {
            while (iterator.hasNext()) {
                iterator.next();
            }
            TenantContext current = TenantContextHolder.getCurrentTenant();
            consumerTenantAfterRun.set(current == null ? null : current.getTenantId());
        }, "bug0006-consumer");
        consumer.start();
        consumer.join(30_000L);

        assertThat(consumer.isAlive()).as("消费线程应已结束").isFalse();
        assertThat(consumerTenantAfterRun.get()).as("B3 消费线程无残留").isNull();
        assertNoTenantResidue("B3 调用线程");
    }

    @Test
    @DisplayName("B4 消费期可见性：探针在 next() 内读租户 → 租户与租户工作区可见")
    void testRunAgentStreaming_consumerObservesTenantDuringIteration() {
        String sessionId = "scope-b4-session";
        trackSession(sessionId);

        Iterator<Object> iterator = runner.runAgentStreaming(new ConsumptionProbeAgent(),
                Map.of("conversation_id", sessionId), null, null, null, null, tenant());

        List<Object> chunks = new ArrayList<>();
        iterator.forEachRemaining(chunks::add);

        assertThat(chunks).hasSize(2);
        for (Object chunk : chunks) {
            @SuppressWarnings("unchecked")
            Map<String, Object> value = (Map<String, Object>) chunk;
            assertThat(value.get("tenant_id")).as("B4 消费期租户可见").isEqualTo(TENANT_ID);
            assertThat((String) value.get("tenant_root")).as("B4 消费期租户工作区可见").isNotNull();
        }
        assertNoTenantResidue("B4 调用线程（全量消费后）");
    }

    @Test
    @DisplayName("B5 7 参 runAgentGroupStreaming 返回后不消费 → 调用线程无租户残留")
    void testRunAgentGroupStreaming_withoutConsumption_hasNoTenantResidueOnCallerThread() {
        String sessionId = "scope-b5-session";
        trackSession(sessionId);

        runner.runAgentGroupStreaming(new MultiItemGroupAgent(), Map.of("conversation_id", sessionId), null, null,
                null, null, tenant());

        assertNoTenantResidue("B5 调用线程");
    }

    @Test
    @DisplayName("B6 7 参 runWorkflowStreaming 返回后不消费 → 调用线程无租户残留")
    void testRunWorkflowStreaming_withoutConsumption_hasNoTenantResidueOnCallerThread() {
        String sessionId = "scope-b6-session";
        trackSession(sessionId);

        Iterator<WorkflowChunk> iterator = runner.runWorkflowStreaming(createStreamingWorkflow("scope-b6-workflow"),
                Map.of("query", "hello"), sessionId, null, null, null, tenant());

        assertThat(iterator).isNotNull();
        assertNoTenantResidue("B6 调用线程");
    }

    @Test
    @DisplayName("B7 7 参 runAgentStreamingAsync 订阅完成后 → 工作线程无租户残留")
    void testRunAgentStreamingAsync_hasNoTenantResidueOnWorkerThreads() {
        String sessionId = "scope-b7-session";
        trackSession(sessionId);

        runner.runAgentStreamingAsync(new AsyncCaptureAgent(), Map.of("conversation_id", sessionId), null, null,
                null, null, tenant()).blockLast();

        assertNoTenantResidue("B7 调用线程");

        List<String> leakedTenants = new ArrayList<>();
        for (int i = 0; i < 600 && leakedTenants.isEmpty(); i++) {
            String observed = Mono.fromCallable(() -> {
                TenantContext current = TenantContextHolder.getCurrentTenant();
                String tenantRoot = CwdContext.getTenantRoot();
                return (current == null ? "none" : current.getTenantId()) + "|" + tenantRoot;
            }).subscribeOn(Schedulers.boundedElastic()).block();
            if (observed != null && observed.startsWith(TENANT_ID)) {
                leakedTenants.add(observed);
            }
        }
        assertThat(leakedTenants).as("B7 boundedElastic 工作线程不得残留租户").isEmpty();
    }

    private Workflow createStreamingWorkflow(String workflowId) {
        Workflow workflow = new Workflow(WorkflowCard.builder().id(workflowId).name(workflowId).version("1").build());
        workflow.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
        workflow.addWorkflowComp("producer", new StreamProbeNode(), null, Map.of("query", "${start.query}"), null,
                null, null, List.of(ComponentAbility.STREAM));
        workflow.setEndComp("end", new End(), null, null, Map.of("session_id", "${producer.session_id}"), null,
                "streaming");
        workflow.addConnection("start", "producer");
        workflow.addStreamConnection("producer", "end");
        return workflow;
    }

    private static class StreamProbeNode extends WorkflowComponent {
        @Override
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            TenantContext current = TenantContextHolder.getCurrentTenant();
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("tenant_id", current == null ? null : current.getTenantId());
            value.put("tenant_root", CwdContext.getTenantRoot());
            value.put("session_id", session.getSessionId());
            return List.<Object>of(value).iterator();
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

    private static class ConsumptionProbeAgent {
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
                    TenantContext current = TenantContextHolder.getCurrentTenant();
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("tenant_id", current == null ? null : current.getTenantId());
                    value.put("tenant_root", CwdContext.getTenantRoot());
                    return value;
                }
            };
        }
    }

    private static class MultiItemGroupAgent {
        public Iterator<Object> stream(Object inputs, Object session, ModelContext context) {
            List<Object> items = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                items.add(Map.of("item", i));
            }
            return items.iterator();
        }
    }

    private static class AsyncCaptureAgent {
        public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session) {
            return List.<Object>of(Map.of("item", 1), Map.of("item", 2)).iterator();
        }
    }
}
