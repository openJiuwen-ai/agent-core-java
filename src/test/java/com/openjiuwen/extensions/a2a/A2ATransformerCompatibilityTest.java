
package com.openjiuwen.extensions.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.singleagent.schema.AgentResult;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class A2ATransformerCompatibilityTest {
    @Test
    void toA2ARequestShouldMapQueryConversationAndMetadata() {
        Map<String, Object> request = Map.of("query", "hello", "conversation_id", "conv-1", "tenant", "demo");

        Map<String, Object> result = A2ATransformer.toA2ARequest(request);

        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) result.get("message");
        assertThat(message).containsEntry("role", "ROLE_USER");
        assertThat(message).containsEntry("contextId", "conv-1");
        assertThat(message).containsEntry("taskId", "conv-1");
        assertThat(message.get("parts")).isEqualTo(List.of(Map.of("text", "hello")));
        assertThat(result).containsEntry("metadata", Map.of("tenant", "demo"));
    }

    @Test
    void fromA2AResponseShouldMapTaskPayload() {
        Map<String, Object> response = Map.of("result",
                Map.of("task", Map.of("id", "task-1", "contextId", "conv-1", "status",
                        Map.of("state", "TASK_STATE_COMPLETED"), "artifacts",
                        List.of(Map.of("artifactId", "artifact-1", "parts", List.of(Map.of("text", "done")))))));

        AgentResult result = A2ATransformer.fromA2AResponse(response);

        assertThat(result.getTaskId()).isEqualTo("task-1");
        assertThat(result.getSessionId()).isEqualTo("conv-1");
        assertThat(result.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.getArtifacts()).hasSize(1);
        assertThat(result.getArtifacts().get(0).getParts()).hasSize(1);
        assertThat(result.getArtifacts().get(0).getParts().get(0).getContent()).isEqualTo("done");
    }

    @Test
    void fromA2AResponseShouldMapStatusUpdatePayload() {
        Map<String, Object> response = Map.of("result", Map.of("statusUpdate",
                Map.of("taskId", "task-2", "contextId", "conv-2", "status", Map.of("state", "TASK_STATE_WORKING"))));

        AgentResult result = A2ATransformer.fromA2AResponse(response);

        assertThat(result.getTaskId()).isEqualTo("task-2");
        assertThat(result.getSessionId()).isEqualTo("conv-2");
        assertThat(result.getStatus()).isEqualTo(TaskStatus.WORKING);
    }
}
