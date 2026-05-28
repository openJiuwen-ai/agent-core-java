/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.runner;

import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

/**
 * Mock agents for runner tests.
 * Mirrors Python's tests/unit_tests/core/runner/mock_agents.py
 */
class MockAgentsTest {

    /**
     * Simple mock agent that sleeps and returns predefined output.
     * Mirrors Python's MockSimpleAgent.
     */
    static class MockSimpleAgent extends BaseAgent {
        private final long sleepTimeMillis;
        private final Object output;

        MockSimpleAgent() {
            this(100, Map.of("result", "mock_output"));
        }

        MockSimpleAgent(long sleepTimeMillis, Object output) {
            super(AgentCard.builder()
                    .id("mock_simple_agent")
                    .name("mock_simple_agent")
                    .description("MockSimpleAgent for testing")
                    .build());
            this.sleepTimeMillis = sleepTimeMillis;
            this.output = output;
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public Object getConfig() {
            return null;
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            try {
                TimeUnit.MILLISECONDS.sleep(sleepTimeMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return output;
        }

        @Override
        public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
            try {
                TimeUnit.MILLISECONDS.sleep(sleepTimeMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Collections.singletonList(output).iterator();
        }
    }

    /**
     * Mock agent that yields multiple chunks with delays.
     * Mirrors Python's MockStreamingAgent.
     */
    static class MockStreamingAgent extends BaseAgent {
        private final List<Object> chunks;
        private final long sleepBetweenChunksMillis;

        MockStreamingAgent() {
            this(List.of(Map.of("chunk", 1), Map.of("chunk", 2), Map.of("chunk", 3)), 50);
        }

        MockStreamingAgent(List<Object> chunks, long sleepBetweenChunksMillis) {
            super(AgentCard.builder()
                    .id("mock_streaming_agent")
                    .name("mock_streaming_agent")
                    .description("MockStreamingAgent for testing")
                    .build());
            this.chunks = chunks != null ? chunks : List.of(
                    Map.of("chunk", 1),
                    Map.of("chunk", 2),
                    Map.of("chunk", 3));
            this.sleepBetweenChunksMillis = sleepBetweenChunksMillis;
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public Object getConfig() {
            return null;
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            return Map.of("chunks", chunks);
        }

        @Override
        public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
            return new Iterator<Object>() {
                private int index = 0;

                @Override
                public boolean hasNext() {
                    return index < chunks.size();
                }

                @Override
                public Object next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    try {
                        TimeUnit.MILLISECONDS.sleep(sleepBetweenChunksMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return chunks.get(index++);
                }
            };
        }
    }

    /**
     * Mock agent that runs for a specified duration.
     * Mirrors Python's MockLongRunningAgent.
     */
    static class MockLongRunningAgent extends BaseAgent {
        private final long durationMillis;
        private final long checkIntervalMillis;
        private volatile boolean shutdownRequested = false;

        MockLongRunningAgent() {
            this(5000, 100);
        }

        MockLongRunningAgent(long durationMillis, long checkIntervalMillis) {
            super(AgentCard.builder()
                    .id("mock_long_running_agent")
                    .name("mock_long_running_agent")
                    .description("MockLongRunningAgent for testing")
                    .build());
            this.durationMillis = durationMillis;
            this.checkIntervalMillis = checkIntervalMillis;
        }

        public void requestShutdown() {
            this.shutdownRequested = true;
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public Object getConfig() {
            return null;
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            long elapsed = 0;
            while (elapsed < durationMillis && !shutdownRequested) {
                try {
                    TimeUnit.MILLISECONDS.sleep(checkIntervalMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                elapsed += checkIntervalMillis;
            }
            return Map.of(
                    "elapsed", elapsed,
                    "completed", elapsed >= durationMillis);
        }

        @Override
        public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
            List<Object> results = new ArrayList<>();
            long elapsed = 0;
            while (elapsed < durationMillis && !shutdownRequested) {
                try {
                    TimeUnit.MILLISECONDS.sleep(checkIntervalMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                elapsed += checkIntervalMillis;
                results.add(Map.of("elapsed", elapsed));
            }
            results.add(Map.of("completed", elapsed >= durationMillis));
            return results.iterator();
        }
    }

    /**
     * Mock agent that ignores shutdown signals and keeps running.
     * Mirrors Python's MockShutdownIgnoringAgent.
     */
    static class MockShutdownIgnoringAgent extends BaseAgent {
        private final long durationMillis;

        MockShutdownIgnoringAgent() {
            this(30000);
        }

        MockShutdownIgnoringAgent(long durationMillis) {
            super(AgentCard.builder()
                    .id("mock_shutdown_ignoring_agent")
                    .name("mock_shutdown_ignoring_agent")
                    .description("MockShutdownIgnoringAgent for testing")
                    .build());
            this.durationMillis = durationMillis;
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public Object getConfig() {
            return null;
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            try {
                TimeUnit.MILLISECONDS.sleep(durationMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Map.of("result", "should_not_reach_here");
        }

        @Override
        public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
            try {
                TimeUnit.MILLISECONDS.sleep(durationMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            List<Object> result = new ArrayList<>();
            result.add(Map.of("result", "should_not_reach_here"));
            return result.iterator();
        }
    }
}