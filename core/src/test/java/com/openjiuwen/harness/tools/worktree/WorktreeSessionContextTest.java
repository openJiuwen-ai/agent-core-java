/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.worktree;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.harness.tools.worktree.test_session} in
 * {@code tests/unit_tests/harness/tools/worktree/test_session.py}.</p>
 */
class WorktreeSessionContextTest {

    @AfterEach
    void resetContext() {
        WorktreeSessionContext.setCurrentSession(null);
        WorktreeSessionContext.setDefaultWorktreeName(null);
    }

    @Test
    void defaultSessionIsNull() {
        assertThat(WorktreeSessionContext.getCurrentSession()).isNull();
    }

    @Test
    void sessionAndDefaultNameRoundTripThroughThreadLocalState() {
        WorktreeSessionContext.initSessionState();
        WorktreeSession session = new WorktreeSession("c:/repo", "c:/repo/.wt", "demo");

        WorktreeSessionContext.setCurrentSession(session);
        WorktreeSessionContext.setDefaultWorktreeName("demo");

        assertThat(WorktreeSessionContext.getCurrentSession()).isEqualTo(session);
        assertThat(WorktreeSessionContext.getDefaultWorktreeName()).isEqualTo("demo");
        assertThat(WorktreeSessionContext.requireCurrentSession()).isEqualTo(session);
    }

    @Test
    void defaultWorktreeNameCanBeSetAndCleared() {
        assertThat(WorktreeSessionContext.getDefaultWorktreeName()).isNull();

        WorktreeSessionContext.setDefaultWorktreeName("bold-elm-1732");
        assertThat(WorktreeSessionContext.getDefaultWorktreeName()).isEqualTo("bold-elm-1732");

        WorktreeSessionContext.setDefaultWorktreeName(null);
        assertThat(WorktreeSessionContext.getDefaultWorktreeName()).isNull();
    }

    @Test
    void clearingActiveSessionKeepsDefaultName() {
        WorktreeSessionContext.setDefaultWorktreeName("bold-elm-1732");
        WorktreeSessionContext.setCurrentSession(new WorktreeSession("c:/repo", "c:/repo/.wt", "bold-elm-1732"));

        WorktreeSessionContext.setCurrentSession(null);

        assertThat(WorktreeSessionContext.getCurrentSession()).isNull();
        assertThat(WorktreeSessionContext.getDefaultWorktreeName()).isEqualTo("bold-elm-1732");
    }

    @Test
    void clearSessionRemovesCurrentSession() {
        WorktreeSession session = new WorktreeSession("c:/repo", "c:/repo/.wt", "demo");
        WorktreeSessionContext.setCurrentSession(session);

        WorktreeSessionContext.setCurrentSession(null);

        assertThat(WorktreeSessionContext.getCurrentSession()).isNull();
    }

    @Test
    void requireCurrentSessionFailsWhenUnset() {
        WorktreeSessionContext.setCurrentSession(null);

        assertThatThrownBy(WorktreeSessionContext::requireCurrentSession)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Not in a worktree session");
    }

    @Test
    void requireCurrentSessionReturnsSessionWhenPresent() {
        WorktreeSession session = new WorktreeSession("c:/repo", "c:/repo/.wt", "demo");
        WorktreeSessionContext.setCurrentSession(session);

        assertThat(WorktreeSessionContext.requireCurrentSession()).isEqualTo(session);
    }

    @Test
    void initializedHolderObservesChildDefaultMutation() throws Exception {
        WorktreeSessionContext.initSessionState();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> WorktreeSessionContext.setDefaultWorktreeName("shared-default")).get();
        } finally {
            executor.shutdownNow();
        }

        assertThat(WorktreeSessionContext.getDefaultWorktreeName()).isEqualTo("shared-default");
    }

    @Test
    void initializedHolderObservesChildSessionMutation() throws Exception {
        WorktreeSessionContext.initSessionState();
        WorktreeSession session = new WorktreeSession("c:/repo", "c:/repo/.wt", "child");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> WorktreeSessionContext.setCurrentSession(session)).get();
        } finally {
            executor.shutdownNow();
        }

        assertThat(WorktreeSessionContext.getCurrentSession()).isEqualTo(session);
    }

    @Test
    void siblingTasksObserveSharedHolderAfterInitialization() throws Exception {
        WorktreeSessionContext.initSessionState();
        CountDownLatch updated = new CountDownLatch(1);
        WorktreeSession[] seen = new WorktreeSession[1];
        WorktreeSession shared = new WorktreeSession("c:/repo", "c:/repo/.wt", "shared");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                WorktreeSessionContext.setCurrentSession(shared);
                updated.countDown();
            });
            var second = executor.submit(() -> {
                try {
                    updated.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(exception);
                }
                seen[0] = WorktreeSessionContext.getCurrentSession();
            });
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }

        assertThat(WorktreeSessionContext.getCurrentSession()).isEqualTo(shared);
        assertThat(seen[0]).isSameAs(shared);
    }
}
