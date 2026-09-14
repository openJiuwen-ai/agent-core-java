/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.runner;

import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.drunner.remote_client.ProtocolEnum;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.schema.AgentResult;
import com.openjiuwen.core.singleagent.schema.Artifact;
import com.openjiuwen.core.singleagent.schema.Part;
import com.openjiuwen.extensions.a2a.A2AAgentExecutor.InMemoryEventQueue;
import com.openjiuwen.extensions.a2a.A2AClient;
import com.openjiuwen.extensions.a2a.A2AClient.A2AClientTransport;
import com.openjiuwen.extensions.a2a.A2AClient.A2AEventStream;
import com.openjiuwen.extensions.a2a.A2AClient.CancelTaskRequest;
import com.openjiuwen.extensions.a2a.A2AClient.ClientConfig;
import com.openjiuwen.extensions.a2a.A2AServerAdapter;
import com.openjiuwen.extensions.a2a.A2ATransformer;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aArtifact;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aMessage;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aPart;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aRole;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aTask;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aTaskState;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aTaskStatus;
import com.openjiuwen.extensions.a2a.A2ATransformer.RequestContext;
import com.openjiuwen.extensions.a2a.A2ATransformer.SendMessageRequest;
import com.openjiuwen.extensions.a2a.A2ATransformer.TaskArtifactUpdateEvent;
import com.openjiuwen.extensions.a2a.A2ATransformer.TaskStatusUpdateEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code tests/system_tests/runner/test_runner_a2a_interop.py}.
 */
class RunnerA2AInteropMissingTest {
    private final List<String> registeredAgents = new ArrayList<>();
    private final List<OpenJiuwenA2aServer> openJiuwenServers = new ArrayList<>();

    @BeforeEach
    void startRunner() {
        Runner.start().toCompletableFuture().join();
    }

    @AfterEach
    void cleanup() {
        for (String agentId : registeredAgents) {
            Runner.resourceMgr().removeAgent(agentId);
        }
        registeredAgents.clear();
        for (OpenJiuwenA2aServer server : openJiuwenServers) {
            server.stop();
        }
        openJiuwenServers.clear();
        Runner.stop().toCompletableFuture().join();
        Runner.setConfig(RunnerConfig.DEFAULT_RUNNER_CONFIG.copy());
    }

    @Test
    void openjiuwenAgentRemoteInteropShouldSupportInvokeAndStream() {
        String serverId = "ojw-server-1";
        OpenJiuwenA2aServer server = createOpenJiuwenA2aServer(serverId);
        String remoteAgentId = "ojw-client-1";
        registerRemoteAgent(remoteAgentId, server);

        Map<String, Object> invokeResult = map(Runner.runAgent(
                        remoteAgentId,
                        Map.of("query", "hello a2a", "conversation_id", "conv-1"))
                .toCompletableFuture()
                .join());

        assertInvokeSubmitted(invokeResult, "conv-1");

        Iterator<Object> iterator = Runner.runAgentStreaming(
                        remoteAgentId,
                        Map.of("query", "stream a2a", "conversation_id", "conv-2"),
                        null,
                        null,
                        null,
                        null)
                .toCompletableFuture()
                .join();
        assertStreamResult(drain(iterator), serverId, "stream a2a", "conv-2");
    }

    @Test
    void openjiuwenAgentClientShouldAccessThirdPartyA2aServerForInvokeAndStream() {
        String serverId = "tp-server-1";
        ThirdPartyA2aServer server = new ThirdPartyA2aServer(serverId);
        String remoteAgentId = "ojw-client-2";
        registerRemoteAgent(remoteAgentId, server);

        Map<String, Object> invokeResult = map(Runner.runAgent(
                        remoteAgentId,
                        Map.of("query", "hello third-party", "conversation_id", "conv-3"))
                .toCompletableFuture()
                .join());

        assertInvokeSubmitted(invokeResult, "conv-3");

        Iterator<Object> iterator = Runner.runAgentStreaming(
                        remoteAgentId,
                        Map.of("query", "stream third-party", "conversation_id", "conv-4"),
                        null,
                        null,
                        null,
                        null)
                .toCompletableFuture()
                .join();
        assertStreamResult(drain(iterator), serverId, "stream third-party", "conv-4");
    }

    @Test
    void thirdPartyA2aClientShouldAccessOpenjiuwenServerForInvokeAndStream() {
        String serverId = "ojw-server-2";
        OpenJiuwenA2aServer server = createOpenJiuwenA2aServer(serverId);
        ThirdPartyA2aClient client = new ThirdPartyA2aClient(server);

        AgentResult invokeResult = client.invoke("hello openjiuwen", "conv-5");

        assertThat(invokeResult.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(invokeResult.getSessionId()).isEqualTo("conv-5");
        assertThat(invokeResult.getTaskId()).isNotBlank().isNotEqualTo("conv-5");
        assertThat(artifactTexts(invokeResult))
                .anySatisfy(text -> assertThat(text).contains(serverId).contains("hello openjiuwen"));

        List<AgentResult> chunks = client.stream("stream openjiuwen", "conv-6");
        assertStreamResult(chunks, serverId, "stream openjiuwen", "conv-6");
    }

    @Test
    void openjiuwenA2aServerShouldSupportMultipleInstancesInOneProcess() {
        List<ServerSpec> specs = List.of(
                new ServerSpec("ojw-multi-1", "conv-7", "conv-8"),
                new ServerSpec("ojw-multi-2", "conv-9", "conv-10"),
                new ServerSpec("ojw-multi-3", "conv-11", "conv-12"));

        for (ServerSpec spec : specs) {
            OpenJiuwenA2aServer server = createOpenJiuwenA2aServer(spec.serverId());
            ThirdPartyA2aClient client = new ThirdPartyA2aClient(server);

            String invokeQuery = "hello " + spec.serverId();
            AgentResult invokeResult = client.invoke(invokeQuery, spec.invokeSessionId());

            assertThat(invokeResult.getStatus()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(invokeResult.getSessionId()).isEqualTo(spec.invokeSessionId());
            assertThat(invokeResult.getTaskId()).isNotBlank().isNotEqualTo(spec.invokeSessionId());
            assertThat(artifactTexts(invokeResult))
                    .anySatisfy(text -> assertThat(text).contains(spec.serverId()).contains(invokeQuery));

            String streamQuery = "stream " + spec.serverId();
            List<AgentResult> chunks = client.stream(streamQuery, spec.streamSessionId());
            assertStreamResult(chunks, spec.serverId(), streamQuery, spec.streamSessionId());
        }
    }

    private OpenJiuwenA2aServer createOpenJiuwenA2aServer(String serverId) {
        AgentCard agentCard = new AgentCard(serverId, serverId, "openjiuwen A2A server " + serverId);
        A2AServerAdapter adapter = new A2AServerAdapter(
                serverId,
                "",
                agentCard,
                payload -> CompletableFuture.completedFuture(openJiuwenResult(
                        serverId,
                        serverId + "-invoke-" + UUID.randomUUID().toString().replace("-", ""),
                        conversationId(payload, serverId),
                        query(payload),
                        "invoke",
                        TaskStatus.COMPLETED)),
                payload -> {
                    String taskId = serverId + "-stream-" + UUID.randomUUID().toString().replace("-", "");
                    String conversationId = conversationId(payload, serverId);
                    String query = query(payload);
                    return List.of(
                            openJiuwenResult(serverId, taskId, conversationId, query, "working", TaskStatus.WORKING),
                            openJiuwenResult(serverId, taskId, conversationId, query, "complete", TaskStatus.COMPLETED));
                },
                rpcUrl(serverId),
                A2AServerAdapter.DEFAULT_RPC_URL,
                A2AServerAdapter.DEFAULT_REST_URL);
        OpenJiuwenA2aServer server = new OpenJiuwenA2aServer(serverId, adapter);
        openJiuwenServers.add(server);
        return server;
    }

    private void registerRemoteAgent(String remoteAgentId, A2aEndpoint endpoint) {
        AgentCard card = new AgentCard(remoteAgentId, remoteAgentId, "remote agent " + remoteAgentId);
        RemoteAgent remoteAgent = new RemoteAgent(
                remoteAgentId,
                "",
                null,
                null,
                ProtocolEnum.A2A,
                Map.of(
                        "url", serverBaseUrl(endpoint.serverId()),
                        "kwargs", Map.of(
                                "card", card,
                                "clientFactory", new EndpointClientFactory(endpoint))));
        Runner.resourceMgr().addAgent(new AgentCard(remoteAgentId, remoteAgentId, "remote test agent"), remoteAgent);
        registeredAgents.add(remoteAgentId);
    }

    private static AgentResult openJiuwenResult(String serverId,
                                                String taskId,
                                                String conversationId,
                                                String query,
                                                String phase,
                                                TaskStatus status) {
        AgentResult result = new AgentResult();
        result.setTaskId(taskId);
        result.setSessionId(conversationId);
        result.setStatus(status);
        Artifact artifact = new Artifact();
        artifact.setArtifactId(serverId + "-" + phase);
        artifact.setName("response");
        Part part = new Part();
        part.setText(serverId + " " + phase + ": " + query);
        part.setMetadata(Map.of("server_id", serverId, "phase", phase));
        artifact.setParts(List.of(part));
        artifact.setMetadata(Map.of("server_id", serverId, "phase", phase));
        result.setArtifacts(List.of(artifact));
        result.setMetadata(Map.of("server_id", serverId, "phase", phase, "query", query));
        return result;
    }

    private static void assertInvokeSubmitted(Map<String, Object> result, String sessionId) {
        assertThat(result).containsEntry("status", "submitted").containsEntry("sessionId", sessionId);
        assertThat(result.get("task_id")).isInstanceOf(String.class);
        assertThat((String) result.get("task_id")).isNotBlank().isNotEqualTo(sessionId);
        assertThat((List<?>) result.getOrDefault("artifacts", List.of())).isEmpty();
    }

    private static void assertStreamResult(List<?> chunks, String serverId, String query, String sessionId) {
        assertThat(chunks).isNotEmpty();
        List<ResultSnapshot> snapshots = chunks.stream().map(ResultSnapshot::from).toList();
        ResultSnapshot first = snapshots.get(0);
        ResultSnapshot last = snapshots.get(snapshots.size() - 1);

        assertThat(last.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(first.taskId()).isNotBlank();
        assertThat(snapshots).allSatisfy(snapshot -> {
            assertThat(snapshot.taskId()).isEqualTo(first.taskId());
            assertThat(snapshot.sessionId()).isEqualTo(first.sessionId());
        });
        assertThat(snapshots).allSatisfy(snapshot -> assertThat(snapshot.sessionId()).isEqualTo(sessionId));
        if (snapshots.size() > 1) {
            assertThat(List.of(TaskStatus.SUBMITTED, TaskStatus.WORKING)).contains(first.status());
            assertThat(snapshots.subList(0, snapshots.size() - 1))
                    .anySatisfy(snapshot -> assertThat(List.of(TaskStatus.SUBMITTED, TaskStatus.WORKING))
                            .contains(snapshot.status()));
        } else {
            assertThat(first.status()).isEqualTo(TaskStatus.COMPLETED);
        }
        List<String> artifactTexts = snapshots.stream()
                .flatMap(snapshot -> snapshot.artifactTexts().stream())
                .toList();
        assertThat(artifactTexts)
                .as("stream snapshots: %s", snapshots)
                .anySatisfy(text -> assertThat(text).contains(serverId).contains(query));
    }

    private static List<Object> drain(Iterator<Object> iterator) {
        List<Object> values = new ArrayList<>();
        while (iterator.hasNext()) {
            values.add(iterator.next());
        }
        return values;
    }

    private static String serverBaseUrl(String serverId) {
        return "http://" + serverId + ".test";
    }

    private static String rpcUrl(String serverId) {
        return serverBaseUrl(serverId) + "/a2a/jsonrpc/";
    }

    private static SendMessageRequest sendMessageRequest(String query, String conversationId) {
        A2aPart part = new A2aPart();
        part.setText(query);
        A2aMessage message = new A2aMessage();
        message.setMessageId(UUID.randomUUID().toString().replace("-", ""));
        message.setRole(A2aRole.ROLE_USER);
        message.setParts(List.of(part));
        if (conversationId != null) {
            message.setContextId(conversationId);
        }
        SendMessageRequest request = new SendMessageRequest();
        request.setMessage(message);
        return request;
    }

    private static RequestContext requestContext(String serverId, SendMessageRequest request) {
        A2aMessage message = request.getMessage();
        String taskId = serverId + "-task-" + UUID.randomUUID().toString().replace("-", "");
        String contextId = message.getContextId() == null ? taskId : message.getContextId();
        RequestContext context = new RequestContext();
        context.setMessage(message);
        context.setTaskId(taskId);
        context.setContextId(contextId);
        return context;
    }

    private static String query(Map<String, Object> payload) {
        Object query = payload.get("query");
        return query == null ? "" : String.valueOf(query);
    }

    private static String conversationId(Map<String, Object> payload, String fallback) {
        Object conversationId = payload.get("conversation_id");
        if (conversationId == null) {
            conversationId = payload.get("sessionId");
        }
        return conversationId == null ? fallback : String.valueOf(conversationId);
    }

    private static A2aTask taskEvent(String taskId, String contextId, A2aTaskState state) {
        A2aTask task = new A2aTask();
        task.setId(taskId);
        task.setContextId(contextId);
        task.setStatus(new A2aTaskStatus(state));
        return task;
    }

    private static TaskStatusUpdateEvent statusEvent(String taskId, String contextId, A2aTaskState state) {
        TaskStatusUpdateEvent event = new TaskStatusUpdateEvent();
        event.setTaskId(taskId);
        event.setContextId(contextId);
        event.setStatus(new A2aTaskStatus(state));
        return event;
    }

    private static TaskArtifactUpdateEvent artifactEvent(String taskId,
                                                        String contextId,
                                                        String artifactId,
                                                        String text,
                                                        boolean lastChunk) {
        A2aPart part = new A2aPart();
        part.setText(text);
        A2aArtifact artifact = new A2aArtifact();
        artifact.setArtifactId(artifactId);
        artifact.setName("response");
        artifact.setParts(List.of(part));
        TaskArtifactUpdateEvent event = new TaskArtifactUpdateEvent();
        event.setTaskId(taskId);
        event.setContextId(contextId);
        event.setArtifact(artifact);
        event.setMetadata(Map.of("last_chunk", lastChunk));
        return event;
    }

    private static AgentResult mergeAgentResults(AgentResult base, AgentResult update) {
        AgentResult merged = new AgentResult();
        merged.setTaskId(update.getTaskId() == null ? base.getTaskId() : update.getTaskId());
        merged.setSessionId(update.getSessionId() == null ? base.getSessionId() : update.getSessionId());
        merged.setStatus(update.getStatus() == null || update.getStatus() == TaskStatus.UNKNOWN
                ? base.getStatus()
                : update.getStatus());
        List<Artifact> artifacts = new ArrayList<>(base.getArtifacts());
        artifacts.addAll(update.getArtifacts());
        merged.setArtifacts(artifacts);
        Map<String, Object> metadata = new LinkedHashMap<>(base.getMetadata());
        metadata.putAll(update.getMetadata());
        merged.setMetadata(metadata);
        return merged;
    }

    private static List<String> artifactTexts(AgentResult result) {
        return result.getArtifacts().stream()
                .flatMap(artifact -> artifact.getParts().stream())
                .map(Part::getText)
                .filter(text -> text != null && !text.isBlank())
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    /**
     * Mirrors Python's A2A ASGI app boundary in
     * {@code tests/system_tests/runner/test_runner_a2a_interop.py}.
     */
    private interface A2aEndpoint {
        String serverId();

        A2AEventStream sendMessage(SendMessageRequest request);
    }

    /**
     * Mirrors Python's {@code _create_openjiuwen_a2a_server} in
     * {@code tests/system_tests/runner/test_runner_a2a_interop.py}.
     */
    private static final class OpenJiuwenA2aServer implements A2aEndpoint {
        private final String serverId;
        private final A2AServerAdapter adapter;

        private OpenJiuwenA2aServer(String serverId, A2AServerAdapter adapter) {
            this.serverId = serverId;
            this.adapter = adapter;
        }

        @Override
        public String serverId() {
            return serverId;
        }

        @Override
        public A2AEventStream sendMessage(SendMessageRequest request) {
            RequestContext context = requestContext(serverId, request);
            InMemoryEventQueue eventQueue = new InMemoryEventQueue();
            adapter.getServer().getExecutor().execute(context, eventQueue).toCompletableFuture().join();
            return new A2AClient.ListEventStream(new ArrayList<>(eventQueue.getEvents()));
        }

        private void stop() {
            adapter.stop().toCompletableFuture().join();
        }
    }

    /**
     * Mirrors Python's {@code _ThirdPartyA2AExecutor} in
     * {@code tests/system_tests/runner/test_runner_a2a_interop.py}.
     */
    private static final class ThirdPartyA2aServer implements A2aEndpoint {
        private final String serverId;

        private ThirdPartyA2aServer(String serverId) {
            this.serverId = serverId;
        }

        @Override
        public String serverId() {
            return serverId;
        }

        @Override
        public A2AEventStream sendMessage(SendMessageRequest request) {
            Map<String, Object> payload = A2ATransformer.fromA2aRequest(request);
            String taskId = serverId + "-task-" + UUID.randomUUID().toString().replace("-", "");
            String contextId = conversationId(payload, serverId);
            String query = query(payload);
            List<Object> events = List.of(
                    taskEvent(taskId, contextId, A2aTaskState.TASK_STATE_SUBMITTED),
                    statusEvent(taskId, contextId, A2aTaskState.TASK_STATE_WORKING),
                    artifactEvent(
                            taskId,
                            contextId,
                            serverId + "-processing",
                            serverId + " processing: " + query,
                            false),
                    artifactEvent(
                            taskId,
                            contextId,
                            serverId + "-complete",
                            serverId + " complete: " + query,
                            true),
                    statusEvent(taskId, contextId, A2aTaskState.TASK_STATE_COMPLETED));
            return new A2AClient.ListEventStream(events);
        }
    }

    /**
     * Mirrors Python's monkeypatched {@code ClientFactory} in
     * {@code tests/system_tests/runner/test_runner_a2a_interop.py}.
     */
    private static final class EndpointClientFactory implements A2AClient.A2AClientFactory {
        private final A2aEndpoint endpoint;

        private EndpointClientFactory(A2aEndpoint endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public A2AClientTransport create(ClientConfig config, Object card) {
            return new EndpointTransport(endpoint);
        }
    }

    /**
     * Mirrors Python's in-memory {@code httpx.ASGITransport} route in
     * {@code tests/system_tests/runner/test_runner_a2a_interop.py}.
     */
    private static final class EndpointTransport implements A2AClientTransport {
        private final A2aEndpoint endpoint;
        private boolean closed;

        private EndpointTransport(A2aEndpoint endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public A2AEventStream sendMessage(SendMessageRequest request) {
            return endpoint.sendMessage(request);
        }

        @Override
        public CompletionStage<Object> cancelTask(CancelTaskRequest request) {
            return CompletableFuture.completedFuture(taskEvent(
                    request.getId(),
                    request.getId(),
                    A2aTaskState.TASK_STATE_CANCELED));
        }

        @Override
        public CompletionStage<Void> close() {
            closed = true;
            return CompletableFuture.completedFuture(null);
        }

        private boolean isClosed() {
            return closed;
        }
    }

    /**
     * Mirrors Python's A2A SDK client collection helpers in
     * {@code tests/system_tests/runner/test_runner_a2a_interop.py}.
     */
    private static final class ThirdPartyA2aClient {
        private final A2aEndpoint endpoint;

        private ThirdPartyA2aClient(A2aEndpoint endpoint) {
            this.endpoint = endpoint;
        }

        private AgentResult invoke(String query, String conversationId) {
            AgentResult aggregate = new AgentResult();
            try (A2AEventStream eventStream = endpoint.sendMessage(sendMessageRequest(query, conversationId))) {
                while (eventStream.hasNext()) {
                    aggregate = mergeAgentResults(aggregate, A2ATransformer.fromA2aResponse(eventStream.next()));
                }
            }
            return aggregate;
        }

        private List<AgentResult> stream(String query, String conversationId) {
            List<AgentResult> chunks = new ArrayList<>();
            AgentResult aggregate = new AgentResult();
            try (A2AEventStream eventStream = endpoint.sendMessage(sendMessageRequest(query, conversationId))) {
                while (eventStream.hasNext()) {
                    aggregate = mergeAgentResults(aggregate, A2ATransformer.fromA2aResponse(eventStream.next()));
                    chunks.add(aggregate);
                }
            }
            return chunks;
        }
    }

    /**
     * Mirrors Python's server spec tuples in
     * {@code tests/system_tests/runner/test_runner_a2a_interop.py}.
     */
    private record ServerSpec(String serverId, String invokeSessionId, String streamSessionId) {
    }

    /**
     * Mirrors Python's stream result assertions in
     * {@code tests/system_tests/runner/test_runner_a2a_interop.py}.
     */
    private record ResultSnapshot(String taskId,
                                  String sessionId,
                                  TaskStatus status,
                                  List<String> artifactTexts) {
        private static ResultSnapshot from(Object value) {
            if (value instanceof AgentResult result) {
                return new ResultSnapshot(
                        result.getTaskId(),
                        result.getSessionId(),
                        result.getStatus(),
                        RunnerA2AInteropMissingTest.artifactTexts(result));
            }
            Map<String, Object> result = map(value);
            return new ResultSnapshot(
                    stringOrNull(result.get("task_id")),
                    stringOrNull(result.get("sessionId")),
                    taskStatus(result.get("status")),
                    artifactTexts(result.get("artifacts")));
        }

        private static List<String> artifactTexts(Object artifacts) {
            if (!(artifacts instanceof List<?> artifactList)) {
                return List.of();
            }
            List<String> texts = new ArrayList<>();
            for (Object artifact : artifactList) {
                if (artifact instanceof Artifact typedArtifact) {
                    typedArtifact.getParts().stream()
                            .map(Part::getText)
                            .filter(text -> text != null && !text.isBlank())
                            .forEach(texts::add);
                    continue;
                }
                Map<String, Object> artifactMap = map(artifact);
                Object parts = artifactMap.get("parts");
                if (!(parts instanceof List<?> partList)) {
                    continue;
                }
                for (Object part : partList) {
                    if (part instanceof Part typedPart && typedPart.getText() != null) {
                        texts.add(typedPart.getText());
                    } else if (part instanceof Map<?, ?> rawPart) {
                        Object text = rawPart.get("text");
                        if (text != null) {
                            texts.add(String.valueOf(text));
                        }
                    }
                }
            }
            return texts;
        }

        private static TaskStatus taskStatus(Object status) {
            if (status instanceof TaskStatus taskStatus) {
                return taskStatus;
            }
            return status == null ? null : TaskStatus.fromValue(String.valueOf(status));
        }

        private static String stringOrNull(Object value) {
            return value == null ? null : String.valueOf(value);
        }
    }
}
