// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.

package com.openjiuwen.core.singleagent.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Unit tests for schema classes: {@link AgentCard}, {@link AgentResult}, {@link Artifact}.
 */
class SchemaTest {
    // ========== AgentCard ==========
    @Test
    void testAgentCardBuilder() {
        AgentCard card = AgentCard.builder().name("myAgent").description("A test agent")
                .inputParams(Map.of("query", Map.of("type", "string")))
                .outputParams(Map.of("result", Map.of("type", "string"))).build();

        assertThat(card.getName()).isEqualTo("myAgent");
        assertThat(card.getDescription()).isEqualTo("A test agent");
        assertThat(card.getInputParamsAsMap()).containsKey("query");
        assertThat(card.getOutputParamsAsMap()).containsKey("result");
    }

    @Test
    void testAgentCardDefaultId() {
        AgentCard card = AgentCard.builder().build();
        assertThat(card.getId()).isNotNull().isNotEmpty();
    }

    @Test
    void testAgentCardDefaults() {
        AgentCard card = AgentCard.builder().build();
        assertThat(card.getName()).isEmpty();
        assertThat(card.getDescription()).isEmpty();
    }

    @Test
    void testAgentCardToolInfo() {
        AgentCard card =
            AgentCard.builder().name("agent1").description("desc").inputParams(Map.of("type", "object")).build();

        Object info = card.toolInfo();
        assertThat(info).isInstanceOf(ToolInfo.class);
        ToolInfo toolInfo = (ToolInfo) info;
        assertThat(toolInfo.getName()).isEqualTo("agent1");
        assertThat(toolInfo.getDescription()).isEqualTo("desc");
    }

    @Test
    void testAgentCardToolInfoNullInputParams() {
        AgentCard card = AgentCard.builder().name("agent2").description("no params").build();

        ToolInfo info = (ToolInfo) card.toolInfo();
        assertThat(info.getParameters()).isNotNull();
    }

    // ========== AgentResult ==========

    @Test
    void testAgentResultBuilder() {
        AgentResult result = AgentResult.builder().taskId("task-1").sessionId("sess-1").build();

        assertThat(result.getTaskId()).isEqualTo("task-1");
        assertThat(result.getSessionId()).isEqualTo("sess-1");
        assertThat(result.getArtifacts()).isNotNull().isEmpty();
        assertThat(result.getMetadata()).isNotNull().isEmpty();
    }

    @Test
    void testAgentResultWithArtifacts() {
        Artifact artifact =
            Artifact.builder().artifactId("art-1").name("myArtifact").description("test artifact").build();

        AgentResult result =
            AgentResult.builder().artifacts(java.util.List.of(artifact)).metadata(Map.of("key", "value")).build();

        assertThat(result.getArtifacts()).hasSize(1);
        assertThat(result.getArtifacts().get(0).getName()).isEqualTo("myArtifact");
        assertThat(result.getMetadata()).containsEntry("key", "value");
    }

    // ========== Artifact ==========

    @Test
    void testArtifactBuilder() {
        Artifact artifact = Artifact.builder().artifactId("a1").name("file.txt").description("A file").build();

        assertThat(artifact.getArtifactId()).isEqualTo("a1");
        assertThat(artifact.getName()).isEqualTo("file.txt");
        assertThat(artifact.getDescription()).isEqualTo("A file");
        assertThat(artifact.getParts()).isNotNull().isEmpty();
        assertThat(artifact.getMetadata()).isNotNull().isEmpty();
    }

    @Test
    void testArtifactDefaults() {
        Artifact artifact = Artifact.builder().build();
        assertThat(artifact.getArtifactId()).isNull();
        assertThat(artifact.getParts()).isEmpty();
        assertThat(artifact.getMetadata()).isEmpty();
    }

    @Test
    void testArtifactWithMetadata() {
        Artifact artifact = Artifact.builder().metadata(Map.of("mime", "text/plain")).build();

        assertThat(artifact.getMetadata()).containsEntry("mime", "text/plain");
    }
}
