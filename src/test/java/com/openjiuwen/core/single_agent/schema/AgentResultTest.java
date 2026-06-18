/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.controller.schema.TaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for agent result schemas.
 *
 * <p>Mirrors Python's {@code Part}, {@code Artifact}, and {@code AgentResult} in
 * {@code openjiuwen/core/single_agent/schema/agent_result.py}.</p>
 */
class AgentResultTest {

    @Test
    void defaultsMirrorPydanticDefaultFactories() {
        Part part = new Part();
        Artifact artifact = new Artifact();
        AgentResult result = new AgentResult();

        assertThat(part.getMetadata()).isEmpty();
        assertThat(artifact.getParts()).isEmpty();
        assertThat(artifact.getMetadata()).isEmpty();
        assertThat(result.getArtifacts()).isEmpty();
        assertThat(result.getMetadata()).isEmpty();
        assertThat(result.getStatus()).isNull();
    }

    @Test
    void preservesPythonSerializationFieldNames() throws Exception {
        Part part = new Part();
        part.setText("hello");
        part.setMediaType("text/plain");
        part.setMetadata(Map.of("lang", "en"));

        Artifact artifact = new Artifact();
        artifact.setArtifactId("artifact-1");
        artifact.setName("summary");
        artifact.setParts(List.of(part));

        AgentResult result = new AgentResult();
        result.setTaskId("task-1");
        result.setSessionId("session-1");
        result.setStatus(TaskStatus.COMPLETED);
        result.setArtifacts(List.of(artifact));

        JsonNode node = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(result));

        assertThat(node.get("task_id").asText()).isEqualTo("task-1");
        assertThat(node.get("sessionId").asText()).isEqualTo("session-1");
        assertThat(node.get("status").asText()).isEqualTo("completed");
        assertThat(node.get("artifacts").get(0).get("artifactId").asText()).isEqualTo("artifact-1");
        assertThat(node.get("artifacts").get(0).get("parts").get(0).get("media_type").asText())
                .isEqualTo("text/plain");
    }

    @Test
    void settersProtectMutableDefaults() {
        byte[] raw = new byte[]{1, 2};
        Part part = new Part();
        part.setRaw(raw);
        raw[0] = 9;

        assertThat(part.getRaw()).containsExactly(1, 2);

        part.setMetadata(null);
        Artifact artifact = new Artifact();
        artifact.setParts(null);
        AgentResult result = new AgentResult();
        result.setArtifacts(null);

        assertThat(part.getMetadata()).isEmpty();
        assertThat(artifact.getParts()).isEmpty();
        assertThat(result.getArtifacts()).isEmpty();
    }
}
