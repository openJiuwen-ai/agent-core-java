/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.experience.PendingChange;
import com.openjiuwen.agent_evolving.signal.EvolutionTarget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code EvolutionApprovalRuntime} in
 * {@code openjiuwen/harness/rails/evolution/approval_runtime.py}.
 *
 * <p>Also mirrors Python's {@code TestEvolutionApprovalRuntime} in
 * {@code tests/unit_tests/harness/rails/evolution/test_evolution_approval_runtime.py}.</p>
 */
class EvolutionApprovalRuntimeTest {

    @Test
    void lookupReturnsNullForUnknownRequest() {
        EvolutionApprovalRuntime runtime = new EvolutionApprovalRuntime(
                new FakeApprovalManager(),
                new PendingApprovalSnapshotStore()
        );

        assertNull(runtime.lookupPendingApprovalSnapshot("missing", "rail", "approve"));
    }

    @Test
    void approvePendingRequestDelegatesToManagerWithApprovedIds() {
        FakeApprovalManager manager = new FakeApprovalManager();
        PendingChange pending = pending("skill-a");
        PendingApprovalSnapshotStore store = new PendingApprovalSnapshotStore(Map.of("req-1", pending));
        EvolutionApprovalRuntime runtime = new EvolutionApprovalRuntime(manager, store);

        EvolutionApprovalRuntime.PendingRequestResult outcome = runtime.approvePendingRequest(
                "req-1",
                "rail",
                "approve",
                List.of("rec-1")
        ).toCompletableFuture().join();

        assertSame(pending, outcome.getPending());
        assertEquals(Map.of("pending_count", 0, "applied_count", 1), outcome.getResult());
        assertEquals("req-1", manager.approvedRequestId);
        assertEquals(List.of("rec-1"), manager.approvedRecordIds);
    }

    @Test
    void approveMissingRequestReturnsEmptyTupleEquivalent() {
        EvolutionApprovalRuntime runtime = new EvolutionApprovalRuntime(
                new FakeApprovalManager(),
                new PendingApprovalSnapshotStore()
        );

        EvolutionApprovalRuntime.PendingRequestResult outcome = runtime.approvePendingRequest(
                "missing",
                "rail",
                "approve"
        ).toCompletableFuture().join();

        assertNull(outcome.getPending());
        assertNull(outcome.getResult());
    }

    @Test
    void approvePartialFailureReturnsResultForCallerRetry() {
        FakeApprovalManager manager = new FakeApprovalManager();
        manager.approveResult = Map.of("pending_count", 2, "applied_count", 1);
        PendingChange pending = pending("skill-a");
        EvolutionApprovalRuntime runtime = new EvolutionApprovalRuntime(
                manager,
                new PendingApprovalSnapshotStore(Map.of("req-partial", pending))
        );

        EvolutionApprovalRuntime.PendingRequestResult outcome = runtime.approvePendingRequest(
                "req-partial",
                "rail",
                "approve"
        ).toCompletableFuture().join();

        assertSame(pending, outcome.getPending());
        assertSame(manager.approveResult, outcome.getResult());
        assertEquals("req-partial", manager.approvedRequestId);
    }

    @Test
    void rejectPendingRequestDelegatesToManager() {
        FakeApprovalManager manager = new FakeApprovalManager();
        PendingChange pending = pending("skill-b");
        EvolutionApprovalRuntime runtime = new EvolutionApprovalRuntime(
                manager,
                new PendingApprovalSnapshotStore(Map.of("req-2", pending))
        );

        EvolutionApprovalRuntime.PendingRequestResult outcome = runtime.rejectPendingRequest(
                "req-2",
                "rail",
                "reject"
        ).toCompletableFuture().join();

        assertSame(pending, outcome.getPending());
        assertEquals("rejected:req-2", outcome.getResult());
        assertEquals("req-2", manager.rejectedRequestId);
    }

    @Test
    void finalizeEmitsApprovalRequestAndAwaitsAsyncOutcome() {
        EvolutionApprovalRuntime runtime = new EvolutionApprovalRuntime(
                new FakeApprovalManager(),
                new PendingApprovalSnapshotStore()
        );
        List<String> events = new ArrayList<>();

        String result = runtime.finalizeStagedEvolutionRequest(
                "request",
                true,
                request -> CompletableFuture.runAsync(() -> events.add("emit:" + request)),
                request -> {
                    events.add("auto:" + request);
                    return null;
                }
        ).toCompletableFuture().join();

        assertEquals("request", result);
        assertEquals(List.of("emit:request"), events);
    }

    @Test
    void finalizeAutoApprovedRequestRunsOptionalCallback() {
        EvolutionApprovalRuntime runtime = new EvolutionApprovalRuntime(
                new FakeApprovalManager(),
                new PendingApprovalSnapshotStore()
        );
        AtomicBoolean called = new AtomicBoolean(false);

        String result = runtime.finalizeStagedEvolutionRequest(
                "request",
                false,
                request -> null,
                request -> {
                    called.set(true);
                    return null;
                }
        ).toCompletableFuture().join();

        assertEquals("request", result);
        assertTrue(called.get());
    }

    private static PendingChange pending(String skillName) {
        return PendingChange.make(
                skillName,
                List.of(EvolutionRecord.builder()
                        .id("rec-1")
                        .source("test")
                        .context("ctx")
                        .change(EvolutionPatch.builder()
                                .section("Workflow")
                                .action("append")
                                .content("content")
                                .target(EvolutionTarget.BODY)
                                .build())
                        .build()),
                null,
                List.of()
        );
    }

    private static final class FakeApprovalManager implements ApprovalManagerProtocol {
        private String approvedRequestId;
        private List<String> approvedRecordIds;
        private String rejectedRequestId;
        private Object approveResult = Map.of("pending_count", 0, "applied_count", 1);

        @Override
        public CompletionStage<Object> approveRequest(String requestId, List<String> approvedRecordIds) {
            this.approvedRequestId = requestId;
            this.approvedRecordIds = approvedRecordIds;
            return CompletableFuture.completedFuture(approveResult);
        }

        @Override
        public CompletionStage<Object> rejectRequest(String requestId) {
            this.rejectedRequestId = requestId;
            return CompletableFuture.completedFuture("rejected:" + requestId);
        }
    }
}
