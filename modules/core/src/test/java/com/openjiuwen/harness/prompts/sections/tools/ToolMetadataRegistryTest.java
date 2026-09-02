
package com.openjiuwen.harness.prompts.sections.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.tool.ToolCard;

import org.junit.jupiter.api.Test;

import java.util.Map;

class ToolMetadataRegistryTest {
    private static final java.util.List<String> PYTHON_BUILT_IN_TOOL_NAMES = java.util.List.of("ask_user", "bash",
            "powershell", "audio_transcription", "audio_question_answering", "audio_metadata", "code", "cron",
            "read_file", "write_file", "edit_file", "glob", "list_files", "grep", "list_skill", "search_tools",
            "load_tools", "sessions_list", "sessions_spawn", "sessions_cancel", "skill_tool", "todo_create",
            "todo_list", "todo_modify", "todo_get", "image_ocr", "visual_question_answering", "video_understanding",
            "task_tool", "lsp", "free_search", "paid_search", "fetch_webpage", "switch_mode", "enter_plan_mode",
            "exit_plan_mode", "list_mcp_resources", "read_mcp_resource", "memory_search", "memory_get", "write_memory",
            "edit_memory", "read_memory", "coding_memory_read", "coding_memory_write", "coding_memory_edit");

    @Test
    void askUserProviderExposesPythonAlignedMetadata() {
        ToolCard card = ToolMetadataRegistry.buildToolCard("ask_user", "ask_user", "cn");

        assertThat(card.getName()).isEqualTo("ask_user");
        assertThat(card.getId()).isEqualTo("ask_user");
        assertThat(card.getDescription()).contains("向用户提问以收集信息");
        assertThat(card.getInputParams()).containsEntry("type", "object");
        assertThat(card.getInputParams()).containsEntry("required", java.util.List.of("questions"));
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) card.getInputParams().get("properties");
        assertThat(properties).containsKey("questions");
        assertThat(properties).doesNotContainKey("query");
    }

    @Test
    void registryShouldExposeHarnessPlanningAndSessionMetadata() {
        ToolCard switchMode = ToolMetadataRegistry.buildToolCard("switch_mode", "switch_mode", "en");
        ToolCard taskTool = ToolMetadataRegistry.buildToolCard("task_tool", "task_tool", "cn");
        ToolCard sessionsSpawn = ToolMetadataRegistry.buildToolCard("sessions_spawn", "sessions_spawn", "en");
        ToolCard todoCreate = ToolMetadataRegistry.buildToolCard("todo_create", "todo_create", "cn");

        assertThat(switchMode.getDescription()).contains("normal and plan");
        assertThat(taskTool.getInputParams()).containsEntry("type", "object");
        assertThat(sessionsSpawn.getDescription()).contains("async background subagent task");
        @SuppressWarnings("unchecked")
        Map<String, Object> taskProps = (Map<String, Object>) taskTool.getInputParams().get("properties");
        assertThat(taskProps).containsKeys("subagent_type", "task_description", "parent_session_id");
        @SuppressWarnings("unchecked")
        Map<String, Object> todoProps = (Map<String, Object>) todoCreate.getInputParams().get("properties");
        assertThat(todoProps).containsKeys("session_id", "tasks");
    }

    @Test
    void registryShouldExposeBashMetadata() {
        ToolCard bash = ToolMetadataRegistry.buildToolCard("bash", "bash", "en");

        assertThat(bash.getDescription()).contains("Executes a given bash command");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) bash.getInputParams().get("properties");
        assertThat(properties).containsKeys("command", "timeout", "description", "run_in_background", "workdir",
                "max_output_chars", "shell_type");
        assertThat(bash.getInputParams()).containsEntry("required", java.util.List.of("command"));
        @SuppressWarnings("unchecked")
        Map<String, Object> shellType = (Map<String, Object>) properties.get("shell_type");
        assertThat(shellType).containsEntry("enum", java.util.List.of("auto", "cmd", "powershell", "bash", "sh"));
    }

    @Test
    void registryShouldExposeFilesystemMetadata() {
        ToolCard readFile = ToolMetadataRegistry.buildToolCard("read_file", "read_file", "en");
        ToolCard editFile = ToolMetadataRegistry.buildToolCard("edit_file", "edit_file", "cn");
        ToolCard grep = ToolMetadataRegistry.buildToolCard("grep", "grep", "en");

        assertThat(readFile.getDescription()).contains("Enhanced file reader");
        assertThat(readFile.getInputParams()).containsEntry("required", java.util.List.of("file_path"));

        @SuppressWarnings("unchecked")
        Map<String, Object> editProps = (Map<String, Object>) editFile.getInputParams().get("properties");
        assertThat(editProps).containsKeys("file_path", "old_string", "new_string", "replace_all");

        @SuppressWarnings("unchecked")
        Map<String, Object> grepProps = (Map<String, Object>) grep.getInputParams().get("properties");
        assertThat(grepProps).containsKeys("pattern", "path", "ignore_case", "glob", "output_mode", "-B", "-A", "-C",
                "context", "-n", "-i", "type", "head_limit", "offset", "multiline");
        @SuppressWarnings("unchecked")
        Map<String, Object> outputMode = (Map<String, Object>) grepProps.get("output_mode");
        assertThat(outputMode).containsEntry("enum", java.util.List.of("content", "files_with_matches", "count"));
    }

    @Test
    void registryShouldBuildAllPythonBuiltinToolCards() {
        for (String toolName : PYTHON_BUILT_IN_TOOL_NAMES) {
            ToolCard cnCard = ToolMetadataRegistry.buildToolCard(toolName, toolName, "cn");
            ToolCard enCard = ToolMetadataRegistry.buildToolCard(toolName, toolName, "en");

            assertThat(cnCard.getDescription()).as(toolName + " cn description").isNotBlank();
            assertThat(enCard.getDescription()).as(toolName + " en description").isNotBlank();
            assertThat(cnCard.getInputParams()).as(toolName + " cn schema").containsKeys("type", "properties",
                    "required");
            assertThat(enCard.getInputParams()).as(toolName + " en schema").containsKeys("type", "properties",
                    "required");
        }
    }

    @Test
    void registryShouldExposeRepresentativeNewToolSchemas() {
        ToolCard lsp = ToolMetadataRegistry.buildToolCard("lsp", "lsp", "en");
        ToolCard loadTools = ToolMetadataRegistry.buildToolCard("load_tools", "load_tools", "cn");
        ToolCard video = ToolMetadataRegistry.buildToolCard("video_understanding", "video_understanding", "en");
        ToolCard memorySearch = ToolMetadataRegistry.buildToolCard("memory_search", "memory_search", "cn");
        ToolCard cron = ToolMetadataRegistry.buildToolCard("cron", "cron", "en");

        @SuppressWarnings("unchecked")
        Map<String, Object> lspProps = (Map<String, Object>) lsp.getInputParams().get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> lspOperation = (Map<String, Object>) lspProps.get("operation");
        assertThat(lspOperation).containsEntry("enum",
                java.util.List.of("goToDefinition", "findReferences", "documentSymbol", "workspaceSymbol",
                        "goToImplementation", "prepareCallHierarchy", "incomingCalls", "outgoingCalls"));

        @SuppressWarnings("unchecked")
        Map<String, Object> loadProps = (Map<String, Object>) loadTools.getInputParams().get("properties");
        assertThat(loadProps).containsKeys("tool_names", "replace");
        assertThat(loadTools.getInputParams()).containsEntry("required", java.util.List.of("tool_names"));

        assertThat(video.getInputParams()).containsEntry("required", java.util.List.of("query", "video_path"));

        @SuppressWarnings("unchecked")
        Map<String, Object> memoryProps = (Map<String, Object>) memorySearch.getInputParams().get("properties");
        assertThat(memoryProps).containsKeys("query", "max_results", "min_score", "session_key");

        @SuppressWarnings("unchecked")
        Map<String, Object> cronProps = (Map<String, Object>) cron.getInputParams().get("properties");
        assertThat(cronProps).containsKeys("action", "job", "jobId", "patch", "includeDisabled", "text", "mode");
    }
}
