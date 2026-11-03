/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.graph.store.GraphStoreState;
import com.openjiuwen.core.graph.store.Store;
import com.openjiuwen.core.session.WorkflowCancellation;

import java.time.Duration;
import java.util.Optional;

/**
 * Restores the preceding checkpoint when a cancelled graph invocation fails.
 * Try-with-resources preserves the execution failure if checkpoint cleanup also fails.
 *
 * @since 2026-10-28
 */
final class WorkflowCheckpointScope implements AutoCloseable {
    private final WorkflowCancellation cancellation;
    private final Store store;
    private final String sessionId;
    private final String workflowId;
    private final Optional<GraphStoreState> previous;
    private boolean hasCompleted;

    WorkflowCheckpointScope(WorkflowCancellation cancellation, Store store,
            String sessionId, String workflowId) {
        this.cancellation = cancellation;
        this.store = store;
        this.sessionId = sessionId;
        this.workflowId = workflowId;
        previous = cancellation == null || store == null
                ? Optional.empty() : store.get(sessionId, workflowId).toCompletableFuture().join();
    }

    void complete() {
        hasCompleted = true;
    }

    @Override
    public void close() {
        if (hasCompleted || cancellation == null || !cancellation.isCancelled() || store == null) {
            return;
        }
        cancellation.awaitQuiescence(Duration.ofSeconds(30));
        if (previous.isPresent()) {
            store.save(sessionId, workflowId, previous.get()).toCompletableFuture().join();
        } else {
            store.delete(sessionId, workflowId).toCompletableFuture().join();
        }
    }
}
