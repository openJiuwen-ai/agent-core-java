/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers built-in tool metadata providers.
 * <p>
 * Mirrors Python's provider registry in
 * {@code openjiuwen.harness.prompts.tools.__init__}.
 */
public final class BuiltinToolProviders {

    private BuiltinToolProviders() {
    }

    private static volatile boolean registered = false;

    /** Register all built-in tool metadata providers. */
    public static synchronized void registerAll() {
        if (registered) {
            return;
        }

        ToolDescriptionRegistry.register(new BashMetadataProvider());
        ToolDescriptionRegistry.register(new PowerShellMetadataProvider());
        ToolDescriptionRegistry.register(new CodeMetadataProvider());
        ToolDescriptionRegistry.register(new CronMetadataProvider());

        ToolDescriptionRegistry.register(schemaProvider(
                "read_file",
                "Read file contents.",
                "Read file contents.",
                Map.of("file_path", prop("string", "文件路径", "File path")),
                List.of("file_path")));
        ToolDescriptionRegistry.register(schemaProvider(
                "write_file",
                "Write file contents.",
                "Write file contents.",
                Map.of(
                        "file_path", prop("string", "文件路径", "File path"),
                        "content", prop("string", "文件内容", "Content")),
                List.of("file_path", "content")));
        ToolDescriptionRegistry.register(schemaProvider(
                "edit_file",
                "Edit a specific part of a file.",
                "Edit a specific part of a file.",
                Map.of(
                        "file_path", prop("string", "文件路径", "File path"),
                        "old_string", prop("string", "旧文本", "Old text"),
                        "new_string", prop("string", "新文本", "New text")),
                List.of("file_path", "old_string", "new_string")));
        ToolDescriptionRegistry.register(schemaProvider(
                "glob",
                "Search for files by pattern.",
                "Search for files by pattern.",
                Map.of("pattern", prop("string", "模式", "Pattern")),
                List.of("pattern")));
        ToolDescriptionRegistry.register(schemaProvider(
                "list_files",
                "List directory contents.",
                "List directory contents.",
                Map.of("path", prop("string", "路径", "Path")),
                List.of()));
        ToolDescriptionRegistry.register(schemaProvider(
                "list_dir",
                "List directory contents.",
                "List directory contents.",
                Map.of("path", prop("string", "路径", "Path")),
                List.of()));
        ToolDescriptionRegistry.register(schemaProvider(
                "grep",
                "Search file contents with regex.",
                "Search file contents with regex.",
                Map.of("pattern", prop("string", "模式", "Pattern")),
                List.of("pattern")));
        ToolDescriptionRegistry.register(schemaProvider(
                "list_skill",
                "List available skills.",
                "List available skills.",
                Map.of(),
                List.of()));
        ToolDescriptionRegistry.register(schemaProvider(
                "todo_create",
                "Create a new todo item.",
                "Create a new todo item.",
                Map.of("tasks", prop("array", "任务列表", "Tasks")),
                List.of("tasks")));
        ToolDescriptionRegistry.register(schemaProvider(
                "todo_list",
                "List all todo items.",
                "List all todo items.",
                Map.of(),
                List.of()));
        ToolDescriptionRegistry.register(schemaProvider(
                "todo_modify",
                "Modify a todo item.",
                "Modify a todo item.",
                Map.of(
                        "id", prop("string", "任务ID", "Todo id"),
                        "status", prop("string", "状态", "Status")),
                List.of("id")));
        ToolDescriptionRegistry.register(schemaProvider(
                "image_ocr",
                "Recognize text in images.",
                "Recognize text in images.",
                Map.of("image_path_or_url", prop("string", "图片路径或URL", "Image path or URL")),
                List.of("image_path_or_url")));
        ToolDescriptionRegistry.register(schemaProvider(
                "visual_question_answering",
                "Answer questions about images.",
                "Answer questions about images.",
                Map.of(
                        "image_path_or_url", prop("string", "图片路径或URL", "Image path or URL"),
                        "question", prop("string", "问题", "Question")),
                List.of("image_path_or_url", "question")));
        ToolDescriptionRegistry.register(schemaProvider(
                "audio_transcription",
                "Transcribe audio to text.",
                "Transcribe audio to text.",
                Map.of("audio_path_or_url", prop("string", "音频路径或URL", "Audio path or URL")),
                List.of("audio_path_or_url")));
        ToolDescriptionRegistry.register(schemaProvider(
                "audio_question_answering",
                "Answer questions about audio.",
                "Answer questions about audio.",
                Map.of(
                        "audio_path_or_url", prop("string", "音频路径或URL", "Audio path or URL"),
                        "question", prop("string", "问题", "Question")),
                List.of("audio_path_or_url", "question")));
        ToolDescriptionRegistry.register(schemaProvider(
                "audio_metadata",
                "Get audio metadata.",
                "Get audio metadata.",
                Map.of("audio_path_or_url", prop("string", "音频路径或URL", "Audio path or URL")),
                List.of("audio_path_or_url")));
        ToolDescriptionRegistry.register(schemaProvider(
                "sessions_spawn",
                "Create a new background session.",
                "Create a new background session.",
                Map.of(),
                List.of()));
        ToolDescriptionRegistry.register(schemaProvider(
                "sessions_list",
                "List all active sessions.",
                "List all active sessions.",
                Map.of(),
                List.of()));
        ToolDescriptionRegistry.register(schemaProvider(
                "sessions_cancel",
                "Cancel a specified session.",
                "Cancel a specified session.",
                Map.of(),
                List.of()));
        ToolDescriptionRegistry.register(schemaProvider(
                "task_tool",
                "Create and manage sub-tasks.",
                "Create and manage sub-tasks.",
                Map.of(
                        "subagent_type", prop("string", "子代理类型", "Subagent type"),
                        "task_description", prop("string", "任务描述", "Task description")),
                List.of("subagent_type", "task_description")));
        ToolDescriptionRegistry.register(schemaProvider(
                "switch_mode",
                "Switch agent mode.",
                "Switch agent mode.",
                Map.of("mode", prop("string", "模式", "Mode")),
                List.of("mode")));
        ToolDescriptionRegistry.register(schemaProvider(
                "enter_plan_mode",
                "Enter plan mode.",
                "Enter plan mode.",
                Map.of(),
                List.of()));
        ToolDescriptionRegistry.register(schemaProvider(
                "exit_plan_mode",
                "Exit plan mode.",
                "Exit plan mode.",
                Map.of(),
                List.of()));
        ToolDescriptionRegistry.register(schemaProvider(
                "list_mcp_resources",
                "List MCP resources.",
                "List MCP resources.",
                Map.of(),
                List.of()));
        ToolDescriptionRegistry.register(schemaProvider(
                "read_mcp_resource",
                "Read MCP resource.",
                "Read MCP resource.",
                Map.of("uri", prop("string", "资源URI", "Resource URI")),
                List.of("uri")));

        registered = true;
    }

    private static SimpleToolMetadataProvider schemaProvider(
            String name,
            String cnDescription,
            String enDescription,
            Map<String, Map<String, String>> properties,
            List<String> required
    ) {
        Map<String, String> descriptions = new LinkedHashMap<>();
        descriptions.put("cn", cnDescription);
        descriptions.put("en", enDescription);
        Map<String, Map<String, Object>> schemas = new LinkedHashMap<>();
        schemas.put("cn", objectSchema(properties, required, "cn"));
        schemas.put("en", objectSchema(properties, required, "en"));
        return new SimpleToolMetadataProvider(name, descriptions, schemas);
    }

    private static Map<String, Object> objectSchema(
            Map<String, Map<String, String>> properties,
            List<String> required,
            String language
    ) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> propertyMap = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : properties.entrySet()) {
            propertyMap.put(entry.getKey(), Map.of(
                    "type", entry.getValue().get("type"),
                    "description", entry.getValue().get(language)
            ));
        }
        schema.put("properties", propertyMap);
        schema.put("required", required);
        return schema;
    }

    private static Map<String, String> prop(String type, String cnDescription, String enDescription) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("type", type);
        result.put("cn", cnDescription);
        result.put("en", enDescription);
        return result;
    }
}
