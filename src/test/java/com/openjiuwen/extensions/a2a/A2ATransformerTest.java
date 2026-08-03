/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.singleagent.schema.AgentResult;
import com.openjiuwen.core.singleagent.schema.Part;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aArtifact;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aMessage;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aPart;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aRole;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aTask;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aTaskState;
import com.openjiuwen.extensions.a2a.A2ATransformer.A2aTaskStatus;
import com.openjiuwen.extensions.a2a.A2ATransformer.RequestContext;
import com.openjiuwen.extensions.a2a.A2ATransformer.SendMessageRequest;
import com.openjiuwen.extensions.a2a.A2ATransformer.StreamResponse;
import com.openjiuwen.extensions.a2a.A2ATransformer.TaskArtifactUpdateEvent;
import com.openjiuwen.extensions.a2a.A2ATransformer.TaskStatusUpdateEvent;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for A2A transformer behavior.
 *
 * <p>Mirrors Python's {@code TestA2ATransformer} in
 * {@code tests/unit_tests/extensions/a2a/test_a2a_transformer.py}.</p>
 *
 * <p>Also mirrors Python's {@code TestA2AClient} request construction in
 * {@code tests/unit_tests/extensions/a2a/test_a2a_client.py}.</p>
 */
class A2ATransformerTest {
    @Test
    void toA2aRequestRaisesClearErrorForNonDictInput() {
        assertThatThrownBy(() -> A2ATransformer.toA2aRequest("hello"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be a dict")
                .hasMessageContaining("String");
    }

    @Test
    void toA2aRequestTreatsOtherFieldsAsMessageMetadata() {
        SendMessageRequest result = A2ATransformer.toA2aRequest(Map.of(
                "query", "hello",
                "sessionId", "conv-2",
                "metadata", List.of(),
                "region", "shenzhen"));

        assertThat(result.getMetadata()).isEmpty();
        assertThat(result.getMessage().getMetadata())
                .containsEntry("metadata", List.of())
                .containsEntry("region", "shenzhen");
    }

    @Test
    void toA2aRequestConvertsOpenjiuwenRequestAndDoesNotSetTaskIdFromSession() {
        SendMessageRequest result = A2ATransformer.to_a2a_request(Map.of(
                "query", "hello",
                "sessionId", "conv-1",
                "metadata", Map.of("tenant", "demo"),
                "city", "shenzhen"));

        A2aMessage message = result.getMessage();
        assertThat(message.getRole()).isEqualTo(A2aRole.ROLE_USER);
        assertThat(message.getMessageId()).isNotBlank();
        assertThat(message.getContextId()).isEqualTo("conv-1");
        assertThat(message.getTaskId()).isNull();
        assertThat(message.getParts()).hasSize(1);
        assertThat(message.getParts().get(0).getText()).isEqualTo("hello");
        assertThat(message.getMetadata()).containsEntry("metadata", Map.of("tenant", "demo"));
        assertThat(message.getMetadata()).containsEntry("city", "shenzhen");
    }

    @Test
    void toA2aRequestPrefersConversationIdAndIgnoresNoneValues() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("query", "please analyze this file");
        request.put("sessionId", "legacy-session");
        request.put("conversation_id", "context-file-1");
        request.put("files", List.of(Map.of(
                "url", "https://example.com/data.csv",
                "media_type", "text/csv",
                "filename", "data.csv",
                "metadata", Map.of("file_size", 10245))));
        request.put("reference_task_ids", List.of("task-reference-1"));
        request.put("city", null);
        request.put("extensions", List.of("https://example.com/extensions/typing-indicator"));

        SendMessageRequest result = A2ATransformer.toA2aRequest(request);

        assertThat(result.getMessage().getContextId()).isEqualTo("context-file-1");
        assertThat(result.getMessage().getParts().get(0).getText()).isEqualTo("please analyze this file");
        assertThat(result.getMessage().getMetadata()).containsKeys("files", "extensions");
        assertThat(result.getMessage().getMetadata()).containsEntry("reference_task_ids", List.of("task-reference-1"));
        assertThat(result.getMessage().getMetadata()).doesNotContainKey("city");
    }

    @Test
    void toA2aRequestBuildsFileRequestMetadata() {
        Map<String, Object> file = Map.of(
                "url", "https://example.com/data.csv",
                "media_type", "text/csv",
                "filename", "data.csv",
                "metadata", Map.of("file_size", 10245));
        Map<String, Object> request = Map.of(
                "query", "please analyze this file",
                "sessionId", "context-file-1",
                "files", List.of(file));

        SendMessageRequest result = A2ATransformer.toA2aRequest(request);

        A2aMessage message = result.getMessage();
        assertThat(message.getContextId()).isEqualTo("context-file-1");
        assertThat(message.getParts()).hasSize(1);
        assertThat(message.getParts().get(0).getText()).isEqualTo("please analyze this file");
        assertThat(message.getMetadata()).containsEntry("files", List.of(file));
    }

    @Test
    void fromA2aRequestNormalizesRequestContext() {
        A2aMessage message = message("task-1", "conv-1", partWithText("hello from a2a"));
        RequestContext request = new RequestContext();
        request.setMessage(message);
        request.setTaskId("task-1");
        request.setContextId("conv-1");

        Map<String, Object> result = A2ATransformer.fromA2aRequest(request);

        assertThat(result).containsEntry("query", "hello from a2a");
        assertThat(result).containsEntry("task_id", "task-1");
        assertThat(result).containsEntry("sessionId", "conv-1");
    }

    @Test
    void fromA2aRequestMergesMessageAndSendRequestMetadataWithoutOverwritingPayload() {
        A2aMessage message = message("task-2", "conv-2", partWithText("hello metadata"));
        message.setMetadata(Map.of("tenant", "message-tenant"));
        SendMessageRequest request = new SendMessageRequest();
        request.setMessage(message);
        request.setMetadata(Map.of("tenant", "request-tenant", "region", "sz"));

        Map<String, Object> result = A2ATransformer.from_a2a_request(request);

        assertThat(result).containsEntry("query", "hello metadata");
        assertThat(result).containsEntry("task_id", "task-2");
        assertThat(result).containsEntry("sessionId", "conv-2");
        assertThat(result).containsEntry("tenant", "message-tenant");
        assertThat(result).containsEntry("region", "sz");
    }

    @Test
    void fromA2aMessageReturnsAgentResult() {
        A2aMessage message = message("task-1", "conv-1", partWithText("hello from agent"));
        message.setMetadata(Map.of("source", "a2a"));

        AgentResult result = A2ATransformer.fromA2aResponse(message);

        assertThat(result.getTaskId()).isEqualTo("task-1");
        assertThat(result.getSessionId()).isEqualTo("conv-1");
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getArtifacts().get(0).getArtifactId()).isEqualTo("message");
        assertThat(result.getArtifacts().get(0).getParts().get(0).getText()).isEqualTo("hello from agent");
        assertThat(result.getMetadata()).containsEntry("source", "a2a");
    }

    @Test
    void fromA2aMessagePreservesRichPartFields() {
        A2aPart filePart = new A2aPart();
        filePart.setUrl("https://example.com/report.pdf");
        filePart.setFilename("report.pdf");
        filePart.setMediaType("application/pdf");
        filePart.setMetadata(Map.of("source", "upload"));
        A2aPart dataPart = new A2aPart();
        dataPart.setRaw(new byte[]{'a', 'b', 'c'});
        dataPart.setData(Map.of("title", "Quarterly Report"));
        A2aMessage message = message("task-rich-1", "conv-rich-1", filePart, dataPart);

        AgentResult result = A2ATransformer.fromA2aResponse(message);

        Part first = result.getArtifacts().get(0).getParts().get(0);
        Part second = result.getArtifacts().get(0).getParts().get(1);
        assertThat(result.getTaskId()).isEqualTo("task-rich-1");
        assertThat(result.getSessionId()).isEqualTo("conv-rich-1");
        assertThat(first.getUrl()).isEqualTo("https://example.com/report.pdf");
        assertThat(first.getFilename()).isEqualTo("report.pdf");
        assertThat(first.getMediaType()).isEqualTo("application/pdf");
        assertThat(first.getMetadata()).containsEntry("source", "upload");
        assertThat(second.getRaw()).containsExactly((byte) 'a', (byte) 'b', (byte) 'c');
        assertThat(second.getData()).isEqualTo("{title=Quarterly Report}");
    }

    @Test
    void fromA2aTaskReturnsAgentResult() {
        A2aTask task = new A2aTask();
        task.setId("task-2");
        task.setContextId("context-2");
        task.setStatus(new A2aTaskStatus(A2aTaskState.TASK_STATE_COMPLETED));
        task.setArtifacts(List.of(artifact("result", "summary", "task result", partWithText("task result body"))));
        task.setMetadata(Map.of("priority", "high"));

        AgentResult result = A2ATransformer.fromA2aResponse(task);

        assertThat(result.getTaskId()).isEqualTo("task-2");
        assertThat(result.getSessionId()).isEqualTo("context-2");
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getArtifacts().get(0).getArtifactId()).isEqualTo("result");
        assertThat(result.getArtifacts().get(0).getParts().get(0).getText()).isEqualTo("task result body");
        assertThat(result.getMetadata()).containsEntry("priority", "high");
    }

    @Test
    void fromA2aStatusUpdateReturnsAgentResult() {
        TaskStatusUpdateEvent event = new TaskStatusUpdateEvent();
        event.setTaskId("task-3");
        event.setContextId("context-3");
        event.setStatus(new A2aTaskStatus(A2aTaskState.TASK_STATE_WORKING));
        event.setMetadata(Map.of("agent_id", "agent-1"));

        AgentResult result = A2ATransformer.fromA2aResponse(event);

        assertThat(result.getTaskId()).isEqualTo("task-3");
        assertThat(result.getSessionId()).isEqualTo("context-3");
        assertThat(result.getStatus()).isEqualTo(TaskStatus.WORKING);
        assertThat(result.getArtifacts()).isEmpty();
        assertThat(result.getMetadata()).containsEntry("agent_id", "agent-1");
    }

    @Test
    void fromA2aStatusUpdateMapsAllTaskStatesExplicitly() {
        Map<Object, TaskStatus> cases = Map.of(
                A2aTaskState.TASK_STATE_UNSPECIFIED, TaskStatus.UNKNOWN,
                A2aTaskState.TASK_STATE_SUBMITTED, TaskStatus.SUBMITTED,
                A2aTaskState.TASK_STATE_WORKING, TaskStatus.WORKING,
                A2aTaskState.TASK_STATE_COMPLETED, TaskStatus.COMPLETED,
                A2aTaskState.TASK_STATE_FAILED, TaskStatus.FAILED,
                A2aTaskState.TASK_STATE_CANCELED, TaskStatus.CANCELED,
                A2aTaskState.TASK_STATE_INPUT_REQUIRED, TaskStatus.INPUT_REQUIRED,
                A2aTaskState.TASK_STATE_REJECTED, TaskStatus.FAILED,
                A2aTaskState.TASK_STATE_AUTH_REQUIRED, TaskStatus.INPUT_REQUIRED);

        for (Map.Entry<Object, TaskStatus> entry : cases.entrySet()) {
            TaskStatusUpdateEvent event = new TaskStatusUpdateEvent();
            event.setTaskId("task-state-map");
            event.setContextId("context-state-map");
            event.setStatus(new A2aTaskStatus(entry.getKey()));

            AgentResult result = A2ATransformer.fromA2aResponse(event);

            assertThat(result.getStatus()).as(String.valueOf(entry.getKey())).isEqualTo(entry.getValue());
        }

    }

    @Test
    void fromA2aStatusUpdateReturnsCompletedAgentResultFromNumericState() {
        TaskStatusUpdateEvent event = new TaskStatusUpdateEvent();
        event.setStatus(new A2aTaskStatus(3));

        assertThat(A2ATransformer.fromA2aResponse(event).getStatus()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void fromA2aArtifactUpdateReturnsAgentResult() {
        TaskArtifactUpdateEvent event = new TaskArtifactUpdateEvent();
        event.setTaskId("task-4");
        event.setContextId("context-4");
        event.setArtifact(artifact(
                "artifact-4",
                "Technical_Specification.md",
                "Generated technical specification document",
                partWithText("Technical Specification")));
        event.setMetadata(Map.of("format", "markdown"));

        AgentResult result = A2ATransformer.fromA2aResponse(event);

        assertThat(result.getTaskId()).isEqualTo("task-4");
        assertThat(result.getSessionId()).isEqualTo("context-4");
        assertThat(result.getStatus()).isEqualTo(TaskStatus.WORKING);
        assertThat(result.getArtifacts().get(0).getArtifactId()).isEqualTo("artifact-4");
        assertThat(result.getArtifacts().get(0).getParts().get(0).getText()).isEqualTo("Technical Specification");
        assertThat(result.getMetadata()).containsEntry("format", "markdown");
    }

    @Test
    void fromClientEventReturnsAgentResult() {
        A2aTask task = new A2aTask();
        task.setId("task-event-1");
        task.setContextId("context-event-1");
        task.setStatus(new A2aTaskStatus(A2aTaskState.TASK_STATE_COMPLETED));
        StreamResponse response = new StreamResponse();
        response.setTask(task);

        AgentResult result = A2ATransformer.fromA2aResponse(List.of(response, task));

        assertThat(result.getTaskId()).isEqualTo("task-event-1");
        assertThat(result.getSessionId()).isEqualTo("context-event-1");
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void fromClientEventFallsBackToTaskWhenStreamResponseHasNoPayload() {
        A2aTask task = new A2aTask();
        StreamResponse empty = new StreamResponse();
        task.setId("task-fallback-1");
        task.setContextId("context-fallback-1");
        task.setStatus(new A2aTaskStatus(A2aTaskState.TASK_STATE_WORKING));

        AgentResult fallback = A2ATransformer.fromA2aResponse(List.of(empty, task));

        assertThat(fallback.getTaskId()).isEqualTo("task-fallback-1");
        assertThat(fallback.getSessionId()).isEqualTo("context-fallback-1");
        assertThat(fallback.getStatus()).isEqualTo(TaskStatus.WORKING);
    }

    @Test
    void fromUnknownResponseReturnsMinimalCompletedAgentResult() {
        AgentResult result = A2ATransformer.fromA2aResponse(new Object());

        assertThat(result.getTaskId()).isNull();
        assertThat(result.getSessionId()).isNull();
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getArtifacts()).isEmpty();
        assertThat(result.getMetadata()).isEmpty();
    }

    @Test
    void toA2aPartConvertsOpenjiuwenPartForExecutorBridge() {
        Part source = new Part();
        source.setText("body");
        source.setRaw(new byte[]{1, 2});
        source.setUrl("https://example.test/file");
        source.setData(Map.of("title", "doc"));
        source.setFilename("doc.md");
        source.setMediaType("text/markdown");
        source.setMetadata(Map.of("source", "unit"));

        A2aPart result = A2ATransformer.to_a2a_part(source);

        assertThat(result.getText()).isEqualTo("body");
        assertThat(result.getRaw()).containsExactly((byte) 1, (byte) 2);
        assertThat(result.getUrl()).isEqualTo("https://example.test/file");
        assertThat(result.getData()).isEqualTo(Map.of("title", "doc"));
        assertThat(result.getFilename()).isEqualTo("doc.md");
        assertThat(result.getMediaType()).isEqualTo("text/markdown");
        assertThat(result.getMetadata()).containsEntry("source", "unit");
    }

    private static A2aMessage message(String taskId, String contextId, A2aPart... parts) {
        A2aMessage message = new A2aMessage();
        message.setTaskId(taskId);
        message.setContextId(contextId);
        message.setParts(List.of(parts));
        return message;
    }

    private static A2aPart partWithText(String text) {
        A2aPart part = new A2aPart();
        part.setText(text);
        return part;
    }

    private static A2aArtifact artifact(String artifactId, String name, String description, A2aPart... parts) {
        A2aArtifact artifact = new A2aArtifact();
        artifact.setArtifactId(artifactId);
        artifact.setName(name);
        artifact.setDescription(description);
        artifact.setParts(List.of(parts));
        return artifact;
    }
}
