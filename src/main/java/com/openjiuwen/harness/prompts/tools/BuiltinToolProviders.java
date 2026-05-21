/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

/**
 * Registers all built-in tool metadata providers at startup.
 * <p>
 * Call {@link #registerAll()} once during application initialization.
 * <p>
 * Mirrors Python's {@code _PROVIDERS} list in
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

        // Bash (full provider)
        ToolDescriptionRegistry.register(new BashMetadataProvider());

        // Core tools
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "powershell", "执行 PowerShell 命令。", "Execute PowerShell commands."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "ask_user", "向用户提问并获取回答。", "Ask the user a question and get their answer."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "read_file", "读取文件内容。", "Read file contents."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "write_file", "写入文件内容。", "Write file contents."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "edit_file", "编辑文件的指定部分。", "Edit a specific part of a file."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "glob", "按模式匹配搜索文件。", "Search for files by pattern."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "list_dir", "列出目录内容。", "List directory contents."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "grep", "搜索文件内容。", "Search file contents with regex."));

        // Code tools
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "code", "执行代码片段。", "Execute code snippets."));

        // Cron tools
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "cron", "管理定时任务。", "Manage cron jobs."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "cron_list_jobs", "列出所有定时任务。", "List all cron jobs."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "cron_get_job", "获取定时任务详情。", "Get cron job details."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "cron_create_job", "创建定时任务。", "Create a cron job."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "cron_update_job", "更新定时任务。", "Update a cron job."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "cron_delete_job", "删除定时任务。", "Delete a cron job."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "cron_toggle_job", "启用/禁用定时任务。", "Enable/disable a cron job."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "cron_preview_job", "预览定时任务执行时间。", "Preview cron job execution times."));

        // Todo tools
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "todo_create", "创建新任务。", "Create a new todo item."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "todo_list", "查看所有任务。", "List all todo items."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "todo_modify", "更新任务状态。", "Modify a todo item."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "todo_get", "获取任务详情。", "Get todo item details."));

        // Task/session tools
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "task_tool", "创建并管理子任务。", "Create and manage sub-tasks."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "sessions_list", "列出所有活跃会话。", "List all active sessions."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "sessions_spawn", "创建新的后台会话。", "Create a new background session."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "sessions_cancel", "取消指定会话。", "Cancel a specified session."));

        // Skill tools
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "list_skills", "查看可用技能列表。", "List available skills."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "use_skill", "加载并执行指定技能。", "Load and execute a specified skill."));

        // Search/load tools
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "search_tools", "搜索可用工具。", "Search for available tools."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "load_tools", "加载指定工具。", "Load specified tools."));

        // Vision/media tools
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "image_ocr", "识别图片中的文字。", "Recognize text in images."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "visual_qa", "回答关于图片的问题。", "Answer questions about images."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "video_understanding", "分析视频内容。", "Analyze video content."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "audio_transcription", "转录音频为文本。", "Transcribe audio to text."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "audio_qa", "回答关于音频的问题。", "Answer questions about audio."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "audio_metadata", "获取音频元数据。", "Get audio metadata."));

        // Web tools
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "free_search", "免费搜索。", "Free web search."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "paid_search", "付费搜索。", "Paid web search."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "fetch_webpage", "获取网页内容。", "Fetch webpage content."));

        // Mode tools
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "switch_mode", "切换代理模式。", "Switch agent mode."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "enter_plan_mode", "进入规划模式。", "Enter plan mode."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "exit_plan_mode", "退出规划模式。", "Exit plan mode."));

        // MCP tools
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "list_mcp_resources", "列出 MCP 资源。", "List MCP resources."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "read_mcp_resource", "读取 MCP 资源。", "Read MCP resource."));

        // LSP tool
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "lsp_tool", "语言服务器协议工具。", "Language Server Protocol tool."));

        // Memory tools
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "memory_search", "搜索记忆内容。", "Search memory content."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "memory_get", "获取记忆详情。", "Get memory details."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "write_memory", "写入记忆内容。", "Write memory content."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "edit_memory", "编辑记忆内容。", "Edit memory content."));
        ToolDescriptionRegistry.register(SimpleToolMetadataProvider.of(
                "read_memory", "读取记忆内容。", "Read memory content."));

        registered = true;
    }
}
