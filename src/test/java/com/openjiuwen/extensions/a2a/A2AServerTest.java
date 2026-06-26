/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import com.openjiuwen.core.single_agent.schema.AgentResult;
import com.openjiuwen.core.single_agent.schema.Artifact;
import com.openjiuwen.core.single_agent.schema.Part;
import com.openjiuwen.extensions.a2a.A2AAgentExecutor.InMemoryEventQueue;
import com.openjiuwen.extensions.a2a.A2AServer.Mount;
import com.openjiuwen.extensions.a2a.A2AServer.Route;
import com.openjiuwen.extensions.a2a.A2AServer.ServerApp;
import com.openjiuwen.extensions.a2a.A2AServer.ServerRuntime;
import com.openjiuwen.extensions.a2a.A2AServer.TransportProtocol;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aMessage;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aPart;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aTask;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aTaskState;
import com.openjiuwen.extensions.a2a.A2ATransformer.RequestContext;
import com.openjiuwen.extensions.a2a.A2ATransformer.TaskArtifactUpdateEvent;
import com.openjiuwen.extensions.a2a.A2ATransformer.TaskStatusUpdateEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code TestA2AServer} in
 * {@code tests/unit_tests/extensions/a2a/test_a2a_server.py}.
 */
class A2AServerTest {

    @Test
    void serverShouldDefaultToJsonrpcWhenNoSupportedInterfaces() {
        A2AServer server = new A2AServer(agentCard());

        ServerApp app = server.buildApp();

        assertThat(app).isNotNull();
        assertThat(server.getRestApp()).isNull();
        assertThat(server.getTransportProtocols()).containsExactly(TransportProtocol.JSONRPC);
        assertThat(paths(app)).contains("/.well-known/agent-card.json", "/a2a/jsonrpc/");
    }

    @Test
    void serverShouldNormalizeJsonrpcRouteAndInterfaceUrl() {
        A2AServer server = new A2AServer(
                agentCard(),
                A2AServer.DEFAULT_ADAPTER_ID,
                null,
                null,
                "http://127.0.0.1:8080/a2a/jsonrpc",
                "JSONRPC",
                "/a2a/jsonrpc",
                A2AServer.DEFAULT_REST_URL);

        ServerApp app = server.buildApp();

        assertThat(paths(app)).contains("/a2a/jsonrpc/");
        assertThat(server.getRpcUrl()).isEqualTo("/a2a/jsonrpc/");
        assertThat(server.getA2aAgentCard().getSupportedInterfaces().get(0).getUrl())
                .isEqualTo("http://127.0.0.1:8080/a2a/jsonrpc/");
    }

    @Test
    void serverShouldUseAgentCardInterfaceUrlWhenParameterOmitted() {
        AgentCard card = agentCard();
        card.setInterfaceUrl("http://127.0.0.1:8091/a2a/jsonrpc");

        A2AServer server = new A2AServer(card);

        assertThat(server.getA2aAgentCard().getSupportedInterfaces().get(0).getUrl())
                .isEqualTo("http://127.0.0.1:8091/a2a/jsonrpc/");
    }

    @Test
    void serverShouldBuildRestAppWhenHttpJsonIsDeclared() {
        A2AServer server = new A2AServer(
                agentCard(),
                A2AServer.DEFAULT_ADAPTER_ID,
                null,
                null,
                "http://example.com/a2a/rest/",
                "HTTP+JSON",
                A2AServer.DEFAULT_RPC_URL,
                A2AServer.DEFAULT_REST_URL);

        ServerApp app = server.buildApp();

        assertThat(app).isNotNull();
        assertThat(server.getRestApp()).isNotNull();
        assertThat(app).isNotSameAs(server.getRestApp());
        assertThat(server.getTransportProtocols()).containsExactly(TransportProtocol.HTTP_JSON);
        assertThat(app.getMounts()).extracting(Mount::getPath).containsExactly("/a2a/rest");
    }

    @Test
    void executorShouldPublishA2aEvents() {
        A2AAgentExecutor executor = new A2AAgentExecutor(inputs -> CompletableFuture.completedFuture(
                result(TaskStatus.COMPLETED, artifact("artifact-1", "summary", "hello from executor"))));
        InMemoryEventQueue queue = new InMemoryEventQueue();

        executor.execute(request("task-1", "conv-1", "hello"), queue).toCompletableFuture().join();

        List<Object> events = new ArrayList<>(queue.getEvents());
        int firstStatusIndex = firstIndexOf(events, TaskStatusUpdateEvent.class);
        int firstTaskIndex = firstIndexOf(events, A2aTask.class);
        assertThat(firstTaskIndex).isLessThan(firstStatusIndex);
        assertThat(events).anyMatch(TaskStatusUpdateEvent.class::isInstance);
        assertThat(events).anyMatch(TaskArtifactUpdateEvent.class::isInstance);
    }

    @Test
    void executorShouldFailTaskWhenInvokeRaises() {
        A2AAgentExecutor executor = new A2AAgentExecutor(inputs -> CompletableFuture.failedFuture(
                new RuntimeException("invoke failed")));
        InMemoryEventQueue queue = new InMemoryEventQueue();

        assertThatThrownBy(() -> executor.execute(request("task-err", "conv-err", "hello"), queue)
                .toCompletableFuture()
                .join())
                .isInstanceOf(CompletionException.class)
                .hasRootCauseMessage("invoke failed");

        List<Object> events = new ArrayList<>(queue.getEvents());
        assertThat(events).anySatisfy(event -> {
            assertThat(event).isInstanceOf(TaskStatusUpdateEvent.class);
            assertThat(((TaskStatusUpdateEvent) event).getStatus().getState())
                    .isEqualTo(A2aTaskState.TASK_STATE_FAILED);
        });
    }

    @Test
    void serverShouldStopUvicornServer() {
        A2AServer server = new A2AServer(agentCard());
        ServerRuntime runtime = new ServerRuntime(server.buildApp(), "127.0.0.1", 8000, "warning");
        server.setUvicornServer(runtime);

        server.stop().toCompletableFuture().join();

        assertThat(runtime.isShouldExit()).isTrue();
    }

    @Test
    void serverShouldRejectGrpcTransport() {
        assertThatThrownBy(() -> new A2AServer(
                agentCard(),
                A2AServer.DEFAULT_ADAPTER_ID,
                null,
                null,
                "https://grpc.example.com/a2a",
                "GRPC",
                A2AServer.DEFAULT_RPC_URL,
                A2AServer.DEFAULT_REST_URL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gRPC transport is not supported");
    }

    private static AgentCard agentCard() {
        return new AgentCard("demo-a2a-agent", "Demo A2A Agent", "demo");
    }

    private static RequestContext request(String taskId, String contextId, String text) {
        A2aPart part = new A2aPart();
        part.setText(text);
        A2aMessage message = new A2aMessage();
        message.setTaskId(taskId);
        message.setContextId(contextId);
        message.setParts(List.of(part));
        RequestContext context = new RequestContext();
        context.setMessage(message);
        context.setTaskId(taskId);
        context.setContextId(contextId);
        return context;
    }

    private static AgentResult result(TaskStatus status, Artifact artifact) {
        AgentResult result = new AgentResult();
        result.setStatus(status);
        result.setArtifacts(List.of(artifact));
        return result;
    }

    private static Artifact artifact(String artifactId, String name, String text) {
        Part part = new Part();
        part.setText(text);
        Artifact artifact = new Artifact();
        artifact.setArtifactId(artifactId);
        artifact.setName(name);
        artifact.setParts(List.of(part));
        artifact.setMetadata(Map.of("source", "openjiuwen"));
        return artifact;
    }

    private static List<String> paths(ServerApp app) {
        return app.getRoutes().stream().map(Route::getPath).toList();
    }

    private static int firstIndexOf(List<Object> events, Class<?> type) {
        for (int index = 0; index < events.size(); index++) {
            if (type.isInstance(events.get(index))) {
                return index;
            }
        }
        return -1;
    }
}
