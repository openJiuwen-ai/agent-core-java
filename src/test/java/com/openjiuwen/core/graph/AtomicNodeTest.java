/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.session.state.WorkflowCommitState;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's atomic node behavior in
 * {@code openjiuwen/core/graph/atomic_node.py}.
 */
class AtomicNodeTest {

    @Test
    void atomicInvokeCommitsAfterSuccessfulBody() throws Exception {
        RecordingCommitState state = new RecordingCommitState();
        RecordingSession session = new RecordingSession(state);
        AtomicNode node = new AtomicNode() {
            @Override
            protected Object atomicInvokeInternal(Map<String, Object> kwargs) {
                return "ok:" + kwargs.get("value");
            }
        };

        Object result = node.atomicInvoke(Map.of("session", session, "value", 7));

        assertEquals("ok:7", result);
        assertEquals(1, state.commitCount);
    }

    @Test
    void atomicInvokeDoesNotCommitWhenBodyFails() {
        RecordingCommitState state = new RecordingCommitState();
        RecordingSession session = new RecordingSession(state);
        AtomicNode node = new AtomicNode() {
            @Override
            protected Object atomicInvokeInternal(Map<String, Object> kwargs) {
                throw new IllegalStateException("boom");
            }
        };

        assertThrows(IllegalStateException.class, () -> node.atomicInvoke(Map.of("session", session)));
        assertEquals(0, state.commitCount);
    }

    @Test
    void asyncAtomicInvokeCommitsAfterSuccessfulBody() {
        RecordingCommitState state = new RecordingCommitState();
        RecordingSession session = new RecordingSession(state);
        AsyncAtomicNode node = new AsyncAtomicNode() {
            @Override
            protected CompletableFuture<Object> atomicInvokeInternal(Map<String, Object> kwargs) {
                return CompletableFuture.completedFuture("async:" + kwargs.get("value"));
            }
        };

        Object result = node.atomicInvoke(Map.of("session", session, "value", 3)).toCompletableFuture().join();

        assertEquals("async:3", result);
        assertEquals(1, state.commitCount);
    }

    @Test
    void asyncAtomicInvokeDoesNotCommitFailedStage() {
        RecordingCommitState state = new RecordingCommitState();
        RecordingSession session = new RecordingSession(state);
        AsyncAtomicNode node = new AsyncAtomicNode() {
            @Override
            protected CompletableFuture<Object> atomicInvokeInternal(Map<String, Object> kwargs) {
                return CompletableFuture.failedFuture(new IllegalStateException("boom"));
            }
        };

        assertThrows(CompletionException.class,
                () -> node.atomicInvoke(Map.of("session", session)).toCompletableFuture().join());
        assertEquals(0, state.commitCount);
    }

    @Test
    void validateRejectsMissingSession() {
        BaseError error = assertThrows(BaseError.class, () -> AtomicNode.validateSessionAndState(null));

        assertEquals(StatusCode.GRAPH_STATE_COMMIT_ERROR, error.getStatus());
        assertEquals("session is None", error.getParams().get("reason"));
    }

    @Test
    void validateRejectsNonBaseSession() {
        BaseError error = assertThrows(BaseError.class, () -> AtomicNode.validateSessionAndState("not-session"));

        assertEquals(StatusCode.GRAPH_STATE_COMMIT_ERROR, error.getStatus());
        assertEquals("session is not base session", error.getParams().get("reason"));
    }

    @Test
    void validateRejectsNonCommitState() {
        BaseError error = assertThrows(BaseError.class,
                () -> AtomicNode.validateSessionAndState(new RecordingSession(new Object())));

        assertEquals(StatusCode.GRAPH_STATE_COMMIT_ERROR, error.getStatus());
        assertEquals("session is not support commit state", error.getParams().get("reason"));
    }

    /**
     * Mirrors Python's session object supplying {@code state()} in
     * {@code openjiuwen/core/graph/atomic_node.py}.
     */
    private record RecordingSession(Object state) implements GraphSession {
    }

    /**
     * Mirrors Python's commit-capable {@code CommitState} dependency in
     * {@code openjiuwen/core/graph/atomic_node.py}.
     */
    private static final class RecordingCommitState extends WorkflowCommitState {
        private int commitCount;

        private RecordingCommitState() {
            super(null, null, null, null, Map.of(), "", "");
        }

        @Override
        public void commitCmp() {
            commitCount += 1;
        }
    }
}
