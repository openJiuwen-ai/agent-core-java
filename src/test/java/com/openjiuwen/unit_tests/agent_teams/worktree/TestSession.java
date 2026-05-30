/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.agent_teams.worktree;

import com.openjiuwen.agent_teams.worktree.WorktreeSession;
import com.openjiuwen.agent_teams.worktree.WorktreeSessionHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code tests.unit_tests.agent_teams.worktree.test_session}.
 */
class TestSession {

    @AfterEach
    void tearDown() {
        WorktreeSessionHolder.setCurrentSession(null);
    }

    private static WorktreeSession makeSession() {
        return makeSession("test");
    }

    private static WorktreeSession makeSession(String name) {
        return new WorktreeSession("/repo", "/workspace/.worktrees/" + name, name);
    }

    @Nested
    class TestGetCurrentSession {
        @Test
        void testDefaultNone() {
            WorktreeSessionHolder.setCurrentSession(null);

            assertNull(WorktreeSessionHolder.getCurrentSession());
        }
    }

    @Nested
    class TestSetCurrentSession {
        @Test
        void testSetAndGet() {
            WorktreeSession session = makeSession();

            WorktreeSessionHolder.setCurrentSession(session);

            assertSame(session, WorktreeSessionHolder.getCurrentSession());
        }

        @Test
        void testClear() {
            WorktreeSessionHolder.setCurrentSession(makeSession());

            WorktreeSessionHolder.setCurrentSession(null);

            assertNull(WorktreeSessionHolder.getCurrentSession());
        }
    }

    @Nested
    class TestRequireCurrentSession {
        @Test
        void testRaisesWhenNone() {
            WorktreeSessionHolder.setCurrentSession(null);

            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    WorktreeSessionHolder::requireCurrentSession
            );
            assertEquals("Not in a worktree session", error.getMessage());
        }

        @Test
        void testReturnsSession() {
            WorktreeSession session = makeSession();
            WorktreeSessionHolder.setCurrentSession(session);

            assertSame(session, WorktreeSessionHolder.requireCurrentSession());
        }
    }

    @Nested
    class TestSharedContainerAcrossThreads {
        @Test
        void testMutationPropagatesWithinSharedHolder() throws Exception {
            WorktreeSessionHolder.initSessionState();
            WorktreeSessionHolder.setCurrentSession(null);
            CountDownLatch aDone = new CountDownLatch(1);
            AtomicReference<WorktreeSession> aResult = new AtomicReference<>();
            AtomicReference<WorktreeSession> bResult = new AtomicReference<>();

            Thread taskA = new Thread(() -> {
                WorktreeSessionHolder.setCurrentSession(makeSession("shared"));
                aResult.set(WorktreeSessionHolder.getCurrentSession());
                aDone.countDown();
            });
            Thread taskB = new Thread(() -> {
                try {
                    aDone.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
                bResult.set(WorktreeSessionHolder.getCurrentSession());
            });

            taskA.start();
            taskB.start();
            taskA.join();
            taskB.join();

            assertNotNull(aResult.get());
            assertEquals("shared", aResult.get().getWorktreeName());
            assertSame(aResult.get(), bResult.get());
        }
    }
}
