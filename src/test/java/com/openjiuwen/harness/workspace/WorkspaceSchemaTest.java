/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.workspace;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors focused schema/get-node-path behavior from
 * {@code tests/unit_tests/harness/test_deep_agent_workspace.py}.
 */
class WorkspaceSchemaTest {

    @Test
    void defaultSchemaIncludesNew014Directories() {
        Workspace workspace = new Workspace(Path.of("./workspace"));

        List<String> names = workspace.getDirectories().stream()
                .map(node -> String.valueOf(node.get("name")))
                .toList();

        assertThat(names).contains("AGENT.md", "USER.md", "memory", "coding_memory", "context");
    }

    @Test
    void englishWorkspaceKeepsEnglishAgentPromptContent() {
        Workspace workspace = new Workspace("./workspace", "en");

        Map<String, Object> agentNode = workspace.getDirectories().stream()
                .filter(node -> "AGENT.md".equals(node.get("name")))
                .findFirst()
                .orElseThrow();

        assertThat(workspace.getLanguage()).isEqualTo("en");
        assertThat(String.valueOf(agentNode.get("description"))).contains("Basic");
        assertThat(String.valueOf(agentNode.get("default_content"))).contains("This folder is home");
    }

    @Test
    void workspaceNodeEnumAndStringLookupsStayEquivalent() {
        Workspace workspace = new Workspace(Path.of("./workspace"));

        assertThat(workspace.getDirectory(WorkspaceNode.USER_MD)).isEqualTo("USER.md");
        assertThat(workspace.getDirectory(WorkspaceNode.USER_MD)).isEqualTo(workspace.getDirectory("USER.md"));
        assertThat(workspace.getDirectory(WorkspaceNode.MEMORY)).isEqualTo("memory");
        assertThat(workspace.getDirectory(WorkspaceNode.CODING_MEMORY)).isEqualTo("coding_memory");
    }

    @Test
    void getNodePathOnlyResolvesTopLevelNodes() {
        Workspace workspace = new Workspace(Path.of("/workspace"));

        assertThat(workspace.getNodePath("memory")).isEqualTo(Path.of("/workspace/memory"));
        assertThat(workspace.getNodePath(WorkspaceNode.AGENT_MD)).isEqualTo(Path.of("/workspace/AGENT.md"));
        assertThat(workspace.getNodePath("MEMORY.md")).isNull();
        assertThat(workspace.getNodePath("session_memory.md")).isNull();
    }

    @Test
    void getDefaultDirectoryReturnsIndependentLanguageSpecificCopies() {
        List<Map<String, Object>> schemaCn = Workspace.getDefaultDirectory("cn");
        List<Map<String, Object>> schemaEn = Workspace.getDefaultDirectory("en");

        Map<String, Object> cnAgent = schemaCn.stream()
                .filter(node -> "AGENT.md".equals(node.get("name")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> enAgent = schemaEn.stream()
                .filter(node -> "AGENT.md".equals(node.get("name")))
                .findFirst()
                .orElseThrow();

        assertThat(cnAgent.get("description")).isNotEqualTo(enAgent.get("description"));
        assertThat(String.valueOf(enAgent.get("default_content"))).contains("This folder is home");
    }
}
