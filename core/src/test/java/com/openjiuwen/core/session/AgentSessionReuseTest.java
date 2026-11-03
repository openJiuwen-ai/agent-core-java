/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import org.junit.jupiter.api.Test;

import java.util.Map;
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

    @Test
    void copyPreRunStateDoesNotSkipWhenSourceHasNotPreRun() {
        AgentSession source = new AgentSession("src", null, null);
        CountingSession target = new CountingSession();

        target.copyPreRunState(source);
        target.preRun(Map.of());

        assertThat(target.preRuns.get()).isEqualTo(1);
        assertThat(target.isPreRunDone()).isTrue();
    }

    @Test
    void copyPreRunStateSkipsWhenSourceAlreadyPreRan() {
        AgentSession source = new AgentSession("src", null, null);
        source.preRun(Map.of());
        CountingSession target = new CountingSession();

        target.copyPreRunState(source);
        target.preRun(Map.of());

        assertThat(target.preRuns.get()).isZero();
        assertThat(target.isPreRunDone()).isTrue();
    }

    @Test
    void copyRunStateSkipsUpstreamPostRunAfterDownstreamCommit() {
        CountingSession upstream = new CountingSession();
        AgentSession downstream = new AgentSession("down", null, null);
        downstream.preRun(Map.of());
        downstream.postRun();

        upstream.copyRunState(downstream);
        upstream.postRun();

        assertThat(upstream.isPreRunDone()).isTrue();
        assertThat(upstream.isPostRunDone()).isTrue();
        assertThat(upstream.commits.get()).isZero();
    }

    private static final class CountingSession extends AgentSession {
        private final AtomicInteger commits = new AtomicInteger();
        private final AtomicInteger preRuns = new AtomicInteger();

        private CountingSession() {
            super("reuse-session", null, null);
        }

        @Override
        public AgentSession preRun(Map<String, Object> kwargs) {
            if (!isPreRunDone()) {
                preRuns.incrementAndGet();
            }
            return super.preRun(kwargs);
        }

        @Override
        public void commit() {
            commits.incrementAndGet();
        }
    }
}
