/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.runner;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.drunner.remote_client.ProtocolEnum;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteAgent;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteClientFactory;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import com.openjiuwen.extensions.a2a.A2AClient;
import com.openjiuwen.extensions.a2a.A2AClient.A2AClientTransport;
import com.openjiuwen.extensions.a2a.A2AClient.A2AEventStream;
import com.openjiuwen.extensions.a2a.A2AClient.CancelTaskRequest;
import com.openjiuwen.extensions.a2a.A2AClient.ClientConfig;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aArtifact;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aPart;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aTask;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aTaskState;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aTaskStatus;
import com.openjiuwen.extensions.a2a.A2ATransformer.SendMessageRequest;
import com.openjiuwen.extensions.a2a.A2ATransformer.TaskArtifactUpdateEvent;
import com.openjiuwen.extensions.a2a.A2ATransformer.TaskStatusUpdateEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code tests.system_tests.runner.test_runner_a2a_remote_agent} in
 * {@code tests/system_tests/runner/test_runner_a2a_remote_agent.py}.
 */
class RunnerA2ARemoteAgentMissingTest {
    private static final String AGENT_ID = "remote-a2a-agent";

    private final EchoA2aFactory factory = new EchoA2aFactory();

    @BeforeEach
    void startRunner() {
        Runner.start().toCompletableFuture().join();
        RemoteAgent agent = new RemoteAgent(
                AGENT_ID,
                "",
                null,
                null,
                ProtocolEnum.A2A,
                Map.of(
                        "url", "http://testserver",
                        "kwargs", Map.of(
                                "card", new AgentCard(AGENT_ID, "System Test A2A Agent", "A2A remote card"),
                                "clientFactory", factory)));
        Runner.resourceMgr().addAgent(new AgentCard(AGENT_ID, AGENT_ID, "A2A remote test agent"), agent);
    }

    @AfterEach
    void cleanup() {
        Runner.resourceMgr().removeAgent(AGENT_ID);
        Runner.stop().toCompletableFuture().join();
        RemoteClientFactory.clearCustomRemoteClientsForTest();
    }

    @Test
    void runnerShouldReturnAgentResultFromA2aRemoteAgent() {
        String conversationId = "c-a2a-1";
        Map<String, Object> response = map(Runner.runAgent(
                        AGENT_ID,
                        mutableInputs("hello a2a", conversationId))
                .toCompletableFuture()
                .join());

        assertThat(factory.lastQuery()).isEqualTo("hello a2a");
        assertThat(response)
                .containsEntry("status", "submitted")
                .containsEntry("sessionId", conversationId);
        assertThat((String) response.get("task_id")).isNotBlank().isNotEqualTo(conversationId);
        assertThat((List<?>) response.getOrDefault("artifacts", List.of())).isEmpty();
    }

    @Test
    void runnerShouldStreamAgentResultFromA2aRemoteAgent() {
        String conversationId = "c-a2a-2";
        Iterator<Object> iterator = Runner.runAgentStreaming(
                        AGENT_ID,
                        mutableInputs("stream a2a", conversationId),
                        null,
                        null,
                        null,
                        null)
                .toCompletableFuture()
                .join();
        List<Map<String, Object>> chunks = drain(iterator).stream().map(RunnerA2ARemoteAgentMissingTest::map).toList();

        assertThat(chunks).isNotEmpty();
        Map<String, Object> first = chunks.get(0);
        Map<String, Object> last = chunks.get(chunks.size() - 1);
        assertThat(last).containsEntry("status", "completed");
        assertThat(List.of("submitted", "working")).contains((String) first.get("status"));
        assertThat(chunks.subList(0, chunks.size() - 1))
                .anySatisfy(chunk -> assertThat(List.of("submitted", "working"))
                        .contains((String) chunk.get("status")));
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk).containsEntry("sessionId", conversationId));
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.get("task_id")).isEqualTo(first.get("task_id")));
        assertThat(artifactTexts(chunks)).contains("echo: stream a2a");
    }

    private static Map<String, Object> mutableInputs(String query, String conversationId) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", query);
        inputs.put("conversation_id", conversationId);
        return inputs;
    }

    private static List<Object> drain(Iterator<Object> iterator) {
        List<Object> chunks = new ArrayList<>();
        while (iterator.hasNext()) {
            chunks.add(iterator.next());
        }
        return chunks;
    }

    private static List<String> artifactTexts(List<Map<String, Object>> chunks) {
        List<String> texts = new ArrayList<>();
        for (Map<String, Object> chunk : chunks) {
            Object artifacts = chunk.get("artifacts");
            if (!(artifacts instanceof List<?> artifactList)) {
                continue;
            }
            for (Object artifact : artifactList) {
                Object parts = map(artifact).get("parts");
                if (!(parts instanceof List<?> partList)) {
                    continue;
                }
                for (Object part : partList) {
                    Object text = map(part).get("text");
                    if (text != null) {
                        texts.add(String.valueOf(text));
                    }
                }
            }
        }
        return texts;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static final class EchoA2aFactory implements A2AClient.A2AClientFactory {
        private final AtomicInteger counter = new AtomicInteger();
        private SendMessageRequest lastRequest;

        @Override
        public A2AClientTransport create(ClientConfig config, Object card) {
            return new EchoA2aTransport(this);
        }

        private String nextTaskId() {
            return "task-a2a-" + counter.incrementAndGet();
        }

        private String lastQuery() {
            return query(lastRequest);
        }
    }

    private static final class EchoA2aTransport implements A2AClientTransport {
        private final EchoA2aFactory owner;

        private EchoA2aTransport(EchoA2aFactory owner) {
            this.owner = owner;
        }

        @Override
        public A2AEventStream sendMessage(SendMessageRequest request) {
            owner.lastRequest = request;
            String taskId = owner.nextTaskId();
            String contextId = contextId(request);
            String query = query(request);
            if (query.startsWith("stream")) {
                return new A2AClient.ListEventStream(List.of(
                        task(taskId, contextId, A2aTaskState.TASK_STATE_SUBMITTED),
                        status(taskId, contextId, A2aTaskState.TASK_STATE_WORKING),
                        artifact(taskId, contextId, "echo: " + query),
                        status(taskId, contextId, A2aTaskState.TASK_STATE_COMPLETED)));
            }
            return new A2AClient.ListEventStream(List.of(task(taskId, contextId, A2aTaskState.TASK_STATE_SUBMITTED)));
        }

        @Override
        public CompletionStage<Object> cancelTask(CancelTaskRequest request) {
            return CompletableFuture.completedFuture(task(
                    request.getId(),
                    request.getId(),
                    A2aTaskState.TASK_STATE_CANCELED));
        }

        @Override
        public CompletionStage<Void> close() {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static String query(SendMessageRequest request) {
        if (request == null || request.getMessage() == null || request.getMessage().getParts().isEmpty()) {
            return "";
        }
        String text = request.getMessage().getParts().get(0).getText();
        return text == null ? "" : text;
    }

    private static String contextId(SendMessageRequest request) {
        if (request == null || request.getMessage() == null) {
            return "";
        }
        return request.getMessage().getContextId();
    }

    private static A2aTask task(String taskId, String contextId, A2aTaskState state) {
        A2aTask task = new A2aTask();
        task.setId(taskId);
        task.setContextId(contextId);
        task.setStatus(new A2aTaskStatus(state));
        return task;
    }

    private static TaskStatusUpdateEvent status(String taskId, String contextId, A2aTaskState state) {
        TaskStatusUpdateEvent event = new TaskStatusUpdateEvent();
        event.setTaskId(taskId);
        event.setContextId(contextId);
        event.setStatus(new A2aTaskStatus(state));
        return event;
    }

    private static TaskArtifactUpdateEvent artifact(String taskId, String contextId, String text) {
        A2aPart part = new A2aPart();
        part.setText(text);
        A2aArtifact artifact = new A2aArtifact();
        artifact.setArtifactId("response");
        artifact.setName("response");
        artifact.setParts(List.of(part));
        TaskArtifactUpdateEvent event = new TaskArtifactUpdateEvent();
        event.setTaskId(taskId);
        event.setContextId(contextId);
        event.setArtifact(artifact);
        event.setMetadata(Map.of("last_chunk", true));
        return event;
    }
}
