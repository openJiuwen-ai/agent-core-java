/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.graph.store.GraphStoreState;
import com.openjiuwen.core.graph.store.Store;
import com.openjiuwen.core.session.WorkflowCancellation;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/**
 * Verifies cancelled checkpoint rollback preserves the primary execution failure and successful state.
 *
 * @since 2026-10-28
 */
class WorkflowCheckpointScopeTest {
    @Test
    void cancelledFailureRestoresPreviousCheckpoint() {
        GraphStoreState previous = mock(GraphStoreState.class);
        Store store = storeWith(Optional.of(previous));
        WorkflowCancellation control = new WorkflowCancellation();
        CancellationException failure = new CancellationException("cancelled execution");
        assertThatThrownBy(() -> {
            try (WorkflowCheckpointScope scope = new WorkflowCheckpointScope(control, store, "session", "workflow")) {
                control.cancel();
                throw failure;
            }
        }).isSameAs(failure);
        verify(store).save("session", "workflow", previous);
        verify(store, never()).delete(any(), any());
    }

    @Test
    void cancelledFailureDeletesCheckpointWhenNoPriorStateExists() {
        Store store = storeWith(Optional.empty());
        WorkflowCancellation control = new WorkflowCancellation();
        try (WorkflowCheckpointScope scope = new WorkflowCheckpointScope(control, store, "session", "workflow")) {
            control.cancel();
        }
        verify(store).delete("session", "workflow");
        verify(store, never()).save(any(), any(), any());
    }

    @Test
    void completedInvocationKeepsCheckpointAfterCancellation() {
        Store store = storeWith(Optional.empty());
        WorkflowCancellation control = new WorkflowCancellation();
        try (WorkflowCheckpointScope scope = new WorkflowCheckpointScope(control, store, "session", "workflow")) {
            control.cancel();
            scope.complete();
        }
        verify(store, never()).delete(any(), any());
        verify(store, never()).save(any(), any(), any());
    }

    @Test
    void rollbackFailureIsSuppressedOnTheOriginalExecutionFailure() {
        Store store = storeWith(Optional.empty());
        IllegalStateException cleanupFailure = new IllegalStateException("checkpoint unavailable");
        when(store.delete("session", "workflow")).thenThrow(cleanupFailure);
        WorkflowCancellation control = new WorkflowCancellation();
        CancellationException failure = new CancellationException("cancelled execution");
        assertThatThrownBy(() -> {
            try (WorkflowCheckpointScope scope = new WorkflowCheckpointScope(control, store, "session", "workflow")) {
                control.cancel();
                throw failure;
            }
        }).isSameAs(failure);
        assertThat(failure.getSuppressed()).containsExactly(cleanupFailure);
    }

    @Test
    void failuresWithoutCancellationDoNotRollBack() {
        Store store = storeWith(Optional.empty());
        WorkflowCancellation control = new WorkflowCancellation();
        IllegalStateException failure = new IllegalStateException("ordinary failure");
        assertThatThrownBy(() -> {
            try (WorkflowCheckpointScope scope = new WorkflowCheckpointScope(control, store, "session", "workflow")) {
                throw failure;
            }
        }).isSameAs(failure);
        verify(store, never()).delete(any(), any());
        verify(store, never()).save(any(), any(), any());
    }

    @Test
    void cancelledFatalFailureStillEscapesAfterCheckpointCleanup() {
        Store store = storeWith(Optional.empty());
        WorkflowCancellation control = new WorkflowCancellation();
        AssertionError failure = new AssertionError("node failed");
        assertThatThrownBy(() -> {
            try (WorkflowCheckpointScope scope = new WorkflowCheckpointScope(control, store, "session", "workflow")) {
                control.cancel();
                throw failure;
            }
        }).isSameAs(failure);
        verify(store).delete("session", "workflow");
    }

    @Test
    void unmanagedAndNonPersistentInvocationsDoNotAccessStorage() {
        Store store = mock(Store.class);
        try (WorkflowCheckpointScope scope = new WorkflowCheckpointScope(null, store, "session", "workflow")) {
            scope.complete();
        }
        WorkflowCancellation control = new WorkflowCancellation();
        try (WorkflowCheckpointScope scope = new WorkflowCheckpointScope(control, null, "session", "workflow")) {
            control.cancel();
        }
        verifyNoInteractions(store);
    }

    private static Store storeWith(Optional<GraphStoreState> previous) {
        Store store = mock(Store.class);
        when(store.get("session", "workflow")).thenReturn(CompletableFuture.completedFuture(previous));
        when(store.save(eq("session"), eq("workflow"), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(store.delete("session", "workflow")).thenReturn(CompletableFuture.completedFuture(null));
        return store;
    }
}
