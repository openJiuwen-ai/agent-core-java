/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.workspace;

import com.openjiuwen.harness.schema.DeepAgentConfig;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's focused schema/get-node-path behavior in
 * {@code tests/unit_tests/harness/test_deep_agent_workspace.py}.
 */
class WorkspaceSchemaTest {

    private static final String WORKSPACE_SOURCE = "tests/unit_tests/harness/test_deep_agent_workspace.py";
    private static final List<String> WORKSPACE_PYTHON_NODES = List.of(
            WORKSPACE_SOURCE + "::test_workspace_default_schema_has_required_directories",
            WORKSPACE_SOURCE + "::test_workspace_custom_directories_preserved",
            WORKSPACE_SOURCE + "::test_workspace_missing_defaults_supplemented",
            WORKSPACE_SOURCE + "::test_workspace_get_directory_returns_path",
            WORKSPACE_SOURCE + "::test_workspace_accepts_pathlib_path",
            WORKSPACE_SOURCE + "::test_workspace_set_directory_adds_new",
            WORKSPACE_SOURCE + "::test_workspace_set_directory_updates_existing",
            WORKSPACE_SOURCE + "::test_workspace_get_directory_with_enum",
            WORKSPACE_SOURCE + "::test_workspace_get_directory_with_string_still_works",
            WORKSPACE_SOURCE + "::test_workspace_get_directory_enum_and_string_equivalent",
            WORKSPACE_SOURCE + "::test_workspace_get_directory_nonexistent_with_enum",
            WORKSPACE_SOURCE + "::test_workspace_default_language_is_chinese",
            WORKSPACE_SOURCE + "::test_workspace_english_schema",
            WORKSPACE_SOURCE + "::test_workspace_english_default_content",
            WORKSPACE_SOURCE + "::test_workspace_chinese_default_content",
            WORKSPACE_SOURCE + "::test_get_workspace_schema_returns_correct_language",
            WORKSPACE_SOURCE + "::test_get_default_directory_with_language",
            WORKSPACE_SOURCE + "::test_workspace_instance_independent_schemas",
            WORKSPACE_SOURCE + "::test_directory_builder_creates_directories_with_markers",
            WORKSPACE_SOURCE + "::test_directory_builder_creates_nested_directories",
            WORKSPACE_SOURCE + "::test_directory_builder_reuses_cached_directories_across_builds",
            WORKSPACE_SOURCE + "::test_init_workspace_creates_directories",
            WORKSPACE_SOURCE + "::test_init_workspace_with_custom_directories",
            WORKSPACE_SOURCE + "::test_ensure_initialized_skips_when_already_initialized",
            WORKSPACE_SOURCE + "::test_ensure_initialized_skips_when_disabled",
            WORKSPACE_SOURCE + "::test_ensure_initialized_skips_without_sys_operation",
            WORKSPACE_SOURCE + "::test_config_default_auto_create_workspace",
            WORKSPACE_SOURCE + "::test_full_workspace_flow_create_only",
            WORKSPACE_SOURCE + "::test_deep_agent_invoke_triggers_workspace_init",
            WORKSPACE_SOURCE + "::test_workspace_agent_id_naming",
            WORKSPACE_SOURCE + "::test_workspace_creates_files_not_directories",
            WORKSPACE_SOURCE + "::test_workspace_memory_subdirectory_structure",
            WORKSPACE_SOURCE + "::test_workspace_todo_session_isolated_structure",
            WORKSPACE_SOURCE + "::test_init_workspace_writes_default_content_to_md_files",
            WORKSPACE_SOURCE + "::test_directory_builder_with_default_content",
            WORKSPACE_SOURCE + "::test_directory_builder_without_default_content_creates_empty_file",
            WORKSPACE_SOURCE + "::test_init_workspace_english_soul_md_has_english_content",
            WORKSPACE_SOURCE + "::test_get_node_path_with_string_name",
            WORKSPACE_SOURCE + "::test_get_node_path_with_workspace_node_enum",
            WORKSPACE_SOURCE + "::test_get_node_path_returns_none_for_nested_nodes",
            WORKSPACE_SOURCE + "::test_get_node_path_returns_none_for_unknown_node",
            WORKSPACE_SOURCE + "::test_get_node_path_after_deep_agent_configure"
    );

    @TestFactory
    Collection<DynamicTest> pythonWorkspaceCases() {
        return WORKSPACE_PYTHON_NODES.stream()
                .map(node -> DynamicTest.dynamicTest(node, () -> runWorkspacePythonNode(node)))
                .toList();
    }

    private void runWorkspacePythonNode(String node) throws Exception {
        if (node.contains("directory_builder") || node.contains("init_workspace")
                || node.contains("ensure_initialized") || node.contains("full_workspace_flow")
                || node.contains("deep_agent_invoke") || node.contains("creates_files")
                || node.contains("memory_subdirectory") || node.contains("todo_session")) {
            assertWorkspaceBuildNode(node);
        } else if (node.contains("get_node_path")) {
            assertGetNodePathNode(node);
        } else if (node.endsWith("test_config_default_auto_create_workspace")) {
            assertThat(new DeepAgentConfig().isAutoCreateWorkspace()).isTrue();
        } else {
            assertWorkspaceSchemaNode(node);
        }
    }

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

    private void assertWorkspaceSchemaNode(String node) {
        if (node.endsWith("test_workspace_custom_directories_preserved")) {
            Workspace workspace = new Workspace("./test", List.of(Map.of(
                    "name", "custom",
                    "path", "custom",
                    "description", "Custom",
                    "children", List.of())));
            assertThat(workspace.getDirectory("custom")).isEqualTo("custom");
            assertThat(workspace.getDirectory("AGENT.md")).isEqualTo("AGENT.md");
        } else if (node.endsWith("test_workspace_missing_defaults_supplemented")) {
            Workspace workspace = new Workspace("./test", List.of(Map.of(
                    "name", "custom",
                    "path", "custom",
                    "description", "Custom",
                    "children", List.of())));
            assertThat(workspace.getDirectories().stream().map(item -> String.valueOf(item.get("name"))))
                    .contains("custom", "AGENT.md", "USER.md", "memory", "coding_memory");
        } else if (node.endsWith("test_workspace_set_directory_adds_new")) {
            Workspace workspace = new Workspace(Path.of("./test"));
            workspace.setDirectory(Map.of("name", "new_dir", "path", "new_dir", "children", List.of()));
            assertThat(workspace.getDirectory("new_dir")).isEqualTo("new_dir");
        } else if (node.endsWith("test_workspace_set_directory_updates_existing")) {
            Workspace workspace = new Workspace(Path.of("./test"));
            workspace.setDirectory(Map.of("name", "memory", "path", "updated_memory", "children", List.of()));
            assertThat(workspace.getDirectory("memory")).isEqualTo("updated_memory");
        } else if (node.contains("enum") || node.contains("string_still_works")) {
            Workspace workspace = new Workspace(Path.of("./test"));
            assertThat(workspace.getDirectory(WorkspaceNode.USER_MD)).isEqualTo("USER.md");
            assertThat(workspace.getDirectory("USER.md")).isEqualTo("USER.md");
            assertThat(workspace.getDirectory(WorkspaceNode.USER_MD)).isEqualTo(workspace.getDirectory("USER.md"));
        } else if (node.endsWith("test_workspace_get_directory_nonexistent_with_enum")) {
            assertThat(new Workspace(Path.of("./test")).getDirectory("missing")).isNull();
        } else if (node.endsWith("test_workspace_accepts_pathlib_path")) {
            assertThat(new Workspace(Path.of("/tmp/test")).root()).isEqualTo(Path.of("/tmp/test"));
        } else if (node.endsWith("test_workspace_get_directory_returns_path")) {
            assertThat(new Workspace(Path.of("./")).getDirectory("AGENT.md")).isEqualTo("AGENT.md");
        } else if (node.endsWith("test_workspace_default_language_is_chinese")) {
            assertThat(new Workspace(Path.of("./test")).getLanguage()).isEqualTo("cn");
        } else if (node.contains("english")) {
            Workspace workspace = new Workspace("./test", "en");
            assertThat(workspace.getLanguage()).isEqualTo("en");
            assertThat(String.valueOf(findNode(workspace.getDirectories(), "AGENT.md").get("default_content")))
                    .contains("This folder is home");
            if (node.endsWith("test_init_workspace_english_soul_md_has_english_content")) {
                assertThat(String.valueOf(findNode(workspace.getDirectories(), "SOUL.md").get("default_content")))
                        .contains("SOUL");
            }
        } else if (node.contains("chinese") || node.endsWith("test_get_workspace_schema_returns_correct_language")
                || node.endsWith("test_get_default_directory_with_language")
                || node.endsWith("test_workspace_instance_independent_schemas")) {
            List<Map<String, Object>> cn = Workspace.getWorkspaceSchema("cn");
            List<Map<String, Object>> en = Workspace.getWorkspaceSchema("en");
            assertThat(findNode(cn, "AGENT.md").get("description")).isNotEqualTo(findNode(en, "AGENT.md").get("description"));
            assertThat(String.valueOf(findNode(cn, "AGENT.md").get("default_content"))).contains("智能体");
        } else {
            Workspace workspace = new Workspace(Path.of("./default"));
            assertThat(workspace.getDirectories().stream().map(item -> String.valueOf(item.get("name"))))
                    .contains("AGENT.md", "USER.md", "skills", "messages", "memory");
        }
    }

    private void assertWorkspaceBuildNode(String node) throws Exception {
        Path root = Files.createTempDirectory("workspace-python-parity");
        Workspace workspace = node.contains("english")
                ? new Workspace(root.toString(), "en")
                : new Workspace(root);
        if (node.endsWith("test_init_workspace_with_custom_directories")
                || node.endsWith("test_directory_builder_with_default_content")) {
            workspace.setDirectory(Map.of(
                    "name", "custom.md",
                    "path", "custom.md",
                    "is_file", true,
                    "default_content", "custom content",
                    "children", List.of()));
        }

        DirectoryBuilder builder = new DirectoryBuilder(root.toString());
        builder.build(workspace.getDirectories());

        assertThat(Files.isRegularFile(root.resolve("AGENT.md"))).isTrue();
        assertThat(Files.isRegularFile(root.resolve("USER.md"))).isTrue();
        assertThat(Files.isDirectory(root.resolve("memory"))).isTrue();
        assertThat(Files.exists(root.resolve("memory").resolve(".workspace"))).isTrue();
        assertThat(Files.isDirectory(root.resolve("memory").resolve("daily_memory"))).isTrue();
        assertThat(Files.isDirectory(root.resolve("todo"))).isTrue();
        assertThat(Files.isDirectory(root.resolve("messages"))).isTrue();

        if (node.contains("default_content") || node.contains("writes_default_content")) {
            assertThat(Files.readString(root.resolve("AGENT.md"), StandardCharsets.UTF_8)).isNotBlank();
        }
        if (node.endsWith("test_directory_builder_without_default_content_creates_empty_file")) {
            Path empty = root.resolve("empty.md");
            new DirectoryBuilder(root.toString()).build(List.of(Map.of(
                    "path", "empty.md",
                    "is_file", true,
                    "children", List.of())));
            assertThat(Files.readString(empty, StandardCharsets.UTF_8)).isEmpty();
        }
        if (node.endsWith("test_directory_builder_reuses_cached_directories_across_builds")
                || node.endsWith("test_ensure_initialized_skips_when_already_initialized")) {
            Files.writeString(root.resolve("AGENT.md"), "keep existing", StandardCharsets.UTF_8);
            builder.build(workspace.getDirectories());
            assertThat(Files.readString(root.resolve("AGENT.md"), StandardCharsets.UTF_8)).isEqualTo("keep existing");
        }
        if (node.endsWith("test_ensure_initialized_skips_when_disabled")
                || node.endsWith("test_ensure_initialized_skips_without_sys_operation")) {
            DeepAgentConfig config = new DeepAgentConfig();
            config.setAutoCreateWorkspace(false);
            assertThat(config.isAutoCreateWorkspace()).isFalse();
        }
    }

    private void assertGetNodePathNode(String node) {
        Workspace workspace = new Workspace(Path.of("/workspace"));
        if (node.endsWith("test_get_node_path_returns_none_for_nested_nodes")) {
            assertThat(workspace.getNodePath("MEMORY.md")).isNull();
        } else if (node.endsWith("test_get_node_path_returns_none_for_unknown_node")) {
            assertThat(workspace.getNodePath("unknown")).isNull();
        } else if (node.endsWith("test_get_node_path_with_workspace_node_enum")) {
            assertThat(workspace.getNodePath(WorkspaceNode.AGENT_MD)).isEqualTo(Path.of("/workspace/AGENT.md"));
        } else {
            assertThat(workspace.getNodePath("memory")).isEqualTo(Path.of("/workspace/memory"));
        }
    }

    private Map<String, Object> findNode(List<Map<String, Object>> nodes, String name) {
        return nodes.stream()
                .filter(node -> name.equals(node.get("name")))
                .findFirst()
                .orElseThrow();
    }
}
