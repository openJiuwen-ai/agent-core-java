/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reusing the same session object must still run postRun on the next invocation.
 */
class AgentSessionReuseTest {

    @Test
    void resetPostRunStateAllowsSecondPostRun() {
        CountingSession session = new CountingSession();
        session.postRun();
        session.postRun();
        assertThat(session.commits.get()).isEqualTo(1);

        session.resetPostRunState();
        session.postRun();
        assertThat(session.commits.get()).isEqualTo(2);
    }

    private static final class CountingSession extends AgentSession {
        private final AtomicInteger commits = new AtomicInteger();

        private CountingSession() {
            super("reuse-session", null, null);
        }

        @Override
        public void commit() {
            commits.incrementAndGet();
        }
    }
}
