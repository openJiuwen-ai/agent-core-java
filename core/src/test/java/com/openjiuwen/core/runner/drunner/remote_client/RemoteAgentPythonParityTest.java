/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remote_client;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.RunnerTermination;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Mirrors Python's {@code TestRunnerIntegration} in
 * {@code tests/unit_tests/core/runner/dunner/test_remote_agent.py}.
 */
class RemoteAgentPythonParityTest {

    private final List<String> registeredAgents = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (String agentId : registeredAgents) {
            Runner.resourceMgr().removeAgent(agentId);
        }
        registeredAgents.clear();
        RemoteClientFactory.clearCustomRemoteClientsForTest();
        Runner.stop().toCompletableFuture().join();
        Runner.setConfig(RunnerConfig.DEFAULT_RUNNER_CONFIG.copy());
    }

    @Test
    void testAgentNormalLifecycle() {
        RecordingRemoteClient client = RecordingRemoteClient.success(
                Map.of("MOCK_INVOKE", "CUSTOM_RESPONSE"),
                List.of(
                        Map.of("MOCK_STREAM", "chunk_0"),
                        Map.of("MOCK_STREAM", "chunk_1"),
                        Map.of("MOCK_STREAM", "chunk_2")));
        registerA2aClient(client);
        registerRemoteAgent("remote-weather-single_agent", "weather-single_agent");

        Object response = Runner.runAgent("remote-weather-single_agent", new LinkedHashMap<>(Map.of("city", "London")))
                .toCompletableFuture()
                .join();

        assertThat(response).isEqualTo(Map.of("MOCK_INVOKE", "CUSTOM_RESPONSE"));
        assertThat(client.invokeInputs.get("city")).isEqualTo("London");
        assertThat(client.invokeInputs.get("conversation_id")).isEqualTo("default_session");

        Iterator<Object> iterator = Runner.runAgentStreaming(
                        "remote-weather-single_agent",
                        new LinkedHashMap<>(Map.of("city", "Paris")),
                        null,
                        null,
                        null,
                        null)
                .toCompletableFuture()
                .join();

        assertThat(drain(iterator)).hasSize(3);

        Runner.resourceMgr().removeAgent("remote-weather-single_agent");
        registeredAgents.remove("remote-weather-single_agent");

        BaseError error = assertBaseError(
                () -> Runner.runAgent("remote-weather-single_agent", Map.of("city", "London"))
                        .toCompletableFuture()
                        .join());
        assertThat(error.getCode()).isEqualTo(StatusCode.RUNNER_RUN_AGENT_ERROR.getCode());
    }

    @Test
    void testAgentRequestCancellation() {
        RecordingRemoteClient client = RecordingRemoteClient.invokeFailure(new CancellationException("cancelled"));
        registerA2aClient(client);
        registerRemoteAgent("weather-agent2", "weather-agent2");

        BaseError error = assertBaseError(
                () -> Runner.runAgent("weather-agent2", Map.of("city", "London")).toCompletableFuture().join());

        assertThat(error.getCode()).isEqualTo(StatusCode.REMOTE_AGENT_EXECUTION_ERROR.getCode());
    }

    @Test
    void testAgentRequestTimeout() {
        RecordingRemoteClient client = RecordingRemoteClient.invokeFailure(new TimeoutException("request timeout"));
        registerA2aClient(client);
        RemoteAgent agent = new RemoteAgent("slow-single_agent", "", null, null, ProtocolEnum.A2A, null);

        BaseError error = assertBaseError(() -> agent.invoke(Map.of("test", "data"), 0.1).toCompletableFuture().join());

        assertThat(error.getCode()).isEqualTo(StatusCode.REMOTE_AGENT_EXECUTION_TIMEOUT.getCode());
    }

    @Test
    void testRemoteAgentShouldRequireCardForA2aProtocol() {
        assertThatThrownBy(() -> new RemoteAgent(
                "a2a-agent",
                "",
                null,
                null,
                ProtocolEnum.A2A,
                Map.of("url", "http://127.0.0.1:41241")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("card is required when protocol is A2A");
    }

    @Test
    void testAgentRunnerShutdownCancelsClients() {
        RecordingRemoteClient client = RecordingRemoteClient.invokeFailure(
                new RunnerTermination("runner stopped", StatusCode.RUNNER_TERMINATION_ERROR));
        registerA2aClient(client);
        registerRemoteAgent("slow-single_agent", "slow-single_agent");

        Throwable thrown = catchThrowable(
                () -> Runner.runAgent("slow-single_agent", Map.of("city", "Berlin")).toCompletableFuture().join());

        assertThat(unwrap(thrown)).isInstanceOf(RunnerTermination.class);
    }

    @Test
    void testAgentAdapterExceptionPropagation() {
        RecordingRemoteClient client = RecordingRemoteClient.invokeFailure(new TimeoutException("ADAPTER_ERROR"));
        registerA2aClient(client);
        registerRemoteAgent("remote-weather-error", "weather-single_agent");

        BaseError error = assertBaseError(
                () -> Runner.runAgent("remote-weather-error", Map.of("city", "London")).toCompletableFuture().join());

        assertThat(error.getCode()).isEqualTo(StatusCode.REMOTE_AGENT_EXECUTION_TIMEOUT.getCode());
    }

    @Test
    void testAgentCallWithoutRunnerStartShouldRaiseException() {
        BaseError startError = ErrorHelper.buildError(
                StatusCode.DIST_MESSAGE_QUEUE_CLIENT_START_ERROR,
                "reason",
                "reply topic not initialized");
        RecordingRemoteClient client = RecordingRemoteClient.startFailure(startError);
        registerA2aClient(client);
        registerRemoteAgent("slow-single_agent-2", "slow-single_agent-2");

        BaseError error = assertBaseError(
                () -> Runner.runAgent("slow-single_agent-2", Map.of("city", "Berlin"))
                        .toCompletableFuture()
                        .join());

        assertThat(error.getCode()).isEqualTo(StatusCode.DIST_MESSAGE_QUEUE_CLIENT_START_ERROR.getCode());
    }

    @Disabled("Skipped in Python source: Skip performance tests")
    @Test
    void testConcurrentVsSequentialPerformanceComparison() {
    }

    @Disabled("Skipped in Python source: Skip performance tests")
    @Test
    void testConcurrentStreaming() {
    }

    private void registerRemoteAgent(String resourceId, String agentId) {
        RemoteAgent agent = new RemoteAgent(agentId, "", null, null, ProtocolEnum.A2A, null);
        Runner.resourceMgr().addAgent(new AgentCard(resourceId, resourceId, "remote test agent"), agent);
        registeredAgents.add(resourceId);
    }

    private static void registerA2aClient(RecordingRemoteClient client) {
        RemoteClientFactory.registerRemoteClient("A2A", ignored -> client);
    }

    private static List<Object> drain(Iterator<Object> iterator) {
        List<Object> values = new ArrayList<>();
        while (iterator.hasNext()) {
            values.add(iterator.next());
        }
        return values;
    }

    private static BaseError assertBaseError(ThrowingRunnable runnable) {
        Throwable thrown = catchThrowable(runnable::run);
        assertThat(thrown).isNotNull();
        assertThat(unwrap(thrown)).isInstanceOf(BaseError.class);
        return (BaseError) unwrap(thrown);
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }

    /**
     * Mirrors Python's in-test mock remote handlers in
     * {@code tests/unit_tests/core/runner/dunner/test_remote_agent.py}.
     */
    private static final class RecordingRemoteClient implements RemoteClient {
        private final Map<String, Object> invokeResponse;
        private final List<Object> streamResponse;
        private final Throwable invokeFailure;
        private final Throwable startFailure;
        private boolean started;
        private Map<String, Object> invokeInputs = Map.of();

        private RecordingRemoteClient(Map<String, Object> invokeResponse,
                                      List<Object> streamResponse,
                                      Throwable invokeFailure,
                                      Throwable startFailure) {
            this.invokeResponse = invokeResponse;
            this.streamResponse = streamResponse;
            this.invokeFailure = invokeFailure;
            this.startFailure = startFailure;
        }

        private static RecordingRemoteClient success(Map<String, Object> invokeResponse, List<Object> streamResponse) {
            return new RecordingRemoteClient(invokeResponse, streamResponse, null, null);
        }

        private static RecordingRemoteClient invokeFailure(Throwable error) {
            return new RecordingRemoteClient(Map.of(), List.of(), error, null);
        }

        private static RecordingRemoteClient startFailure(Throwable error) {
            return new RecordingRemoteClient(Map.of(), List.of(), null, error);
        }

        @Override
        public CompletionStage<Void> start() {
            if (startFailure != null) {
                return CompletableFuture.failedFuture(startFailure);
            }
            started = true;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> stop() {
            started = false;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean isStarted() {
            return started;
        }

        @Override
        public CompletionStage<Map<String, Object>> invoke(Map<String, Object> inputs, Double timeoutSeconds) {
            invokeInputs = new LinkedHashMap<>(inputs);
            if (invokeFailure != null) {
                return CompletableFuture.failedFuture(invokeFailure);
            }
            return CompletableFuture.completedFuture(invokeResponse);
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) {
            return streamResponse.iterator();
        }
    }
}
