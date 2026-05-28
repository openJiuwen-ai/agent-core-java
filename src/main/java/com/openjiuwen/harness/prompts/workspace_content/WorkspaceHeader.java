/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.workspace_content;

import java.util.*;

/**
 * Bilingual text constants for workspace and context sections.
 * <p>
 * Mirrors Python's {@code workspace_header} in
 * {@code openjiuwen.harness.prompts.workspace_content.workspace_header}.
 */
public final class WorkspaceHeader {

    private WorkspaceHeader() {
    }

    // ── Workspace header ──────────────────────────────────────────────

    private static final String CN_WORKSPACE_HEADER = "# 工作空间\n\n";
    private static final String EN_WORKSPACE_HEADER = "# Workspace\n\n";

    private static final Map<String, String> WORKSPACE_HEADER = new LinkedHashMap<>();

    static {
        WORKSPACE_HEADER.put("cn", CN_WORKSPACE_HEADER);
        WORKSPACE_HEADER.put("en", EN_WORKSPACE_HEADER);
    }

    public static String getWorkspaceHeader(String language) {
        return WORKSPACE_HEADER.getOrDefault(language, CN_WORKSPACE_HEADER);
    }

    // ── Important files table ──────────────────────────────────────────

    private static final String CN_IMPORTANT_FILES =
            "## 工作目录下重要文件\n\n"
            + "| 文件 | 用途 | 操作工具 |\n"
            + "|------|------|----------|\n"
            + "| `AGENT.md` | Agent 启动指南 | read_file |\n"
            + "| `IDENTITY.md` | Agent 身份设定 | read_file / edit_file |\n"
            + "| `USER.md` | 用户档案（姓名、职业、爱好等） | read_memory / write_memory / edit_memory |\n"
            + "| `memory/MEMORY.md` | 长期记忆（决策、偏好、持久事实） | read_memory / write_memory / edit_memory |\n"
            + "| `memory/daily_memory/YYYY-MM-DD.md` | 每日会话记录 | read_memory / write_memory / edit_memory |\n";

    private static final String EN_IMPORTANT_FILES =
            "## Important Files in Working Directory\n\n"
            + "| File | Purpose | Tools |\n"
            + "|------|---------|-------|\n"
            + "| `AGENT.md` | Agent startup guide | read_file |\n"
            + "| `IDENTITY.md` | Agent identity settings | read_file / edit_file |\n"
            + "| `USER.md` | User profile (name, occupation, hobbies, etc.) | read_memory / write_memory / edit_memory |\n"
            + "| `memory/MEMORY.md` | Long-term memory (decisions, preferences, persistent facts) "
            + "| read_memory / write_memory / edit_memory |\n"
            + "| `memory/daily_memory/YYYY-MM-DD.md` | Daily session records | read_memory / write_memory / edit_memory |\n";

    private static final Map<String, String> IMPORTANT_FILES = new LinkedHashMap<>();

    static {
        IMPORTANT_FILES.put("cn", CN_IMPORTANT_FILES);
        IMPORTANT_FILES.put("en", EN_IMPORTANT_FILES);
    }

    public static String getImportantFiles(String language) {
        return IMPORTANT_FILES.getOrDefault(language, CN_IMPORTANT_FILES);
    }

    // ── Context header ────────────────────────────────────────────────

    private static final String CN_CONTEXT_HEADER =
            "# 项目上下文\n\n以下文件已加载到上下文中，无需再次读取。\n\n";

    private static final String EN_CONTEXT_HEADER =
            "# Project Context\n\n"
            + "The following files are already loaded into context, so you do not need to "
            + "read them again.\n\n";

    private static final Map<String, String> CONTEXT_HEADER = new LinkedHashMap<>();

    static {
        CONTEXT_HEADER.put("cn", CN_CONTEXT_HEADER);
        CONTEXT_HEADER.put("en", EN_CONTEXT_HEADER);
    }

    public static String getContextHeader(String language) {
        return CONTEXT_HEADER.getOrDefault(language, CN_CONTEXT_HEADER);
    }

    // ── Context file titles ────────────────────────────────────────────

    private static final Map<String, String> CN_CONTEXT_FILE_TITLES = new LinkedHashMap<>();
    private static final Map<String, String> EN_CONTEXT_FILE_TITLES = new LinkedHashMap<>();
    private static final Map<String, Map<String, String>> CONTEXT_FILE_TITLES = new LinkedHashMap<>();

    static {
        CN_CONTEXT_FILE_TITLES.put("AGENT.md", "## AGENT.md - 智能体配置");
        CN_CONTEXT_FILE_TITLES.put("SOUL.md", "## SOUL.md - 灵魂与价值观");
        CN_CONTEXT_FILE_TITLES.put("HEARTBEAT.md", "## HEARTBEAT.md - 心跳任务");
        CN_CONTEXT_FILE_TITLES.put("USER.md", "## USER.md - 用户信息");
        CN_CONTEXT_FILE_TITLES.put("IDENTITY.md", "## IDENTITY.md - 身份凭证");
        CN_CONTEXT_FILE_TITLES.put("MEMORY.md", "## MEMORY.md - 长期记忆");

        EN_CONTEXT_FILE_TITLES.put("AGENT.md", "## AGENT.md - Agent Configuration");
        EN_CONTEXT_FILE_TITLES.put("SOUL.md", "## SOUL.md - Soul & Values");
        EN_CONTEXT_FILE_TITLES.put("HEARTBEAT.md", "## HEARTBEAT.md - Heartbeat Tasks");
        EN_CONTEXT_FILE_TITLES.put("USER.md", "## USER.md - User Information");
        EN_CONTEXT_FILE_TITLES.put("IDENTITY.md", "## IDENTITY.md - Identity Credentials");
        EN_CONTEXT_FILE_TITLES.put("MEMORY.md", "## MEMORY.md - Long-term Memory");

        CONTEXT_FILE_TITLES.put("cn", CN_CONTEXT_FILE_TITLES);
        CONTEXT_FILE_TITLES.put("en", EN_CONTEXT_FILE_TITLES);
    }

    public static Map<String, String> getContextFileTitles(String language) {
        return CONTEXT_FILE_TITLES.getOrDefault(language, CN_CONTEXT_FILE_TITLES);
    }

    public static String getContextFileTitle(String language, String fileName) {
        Map<String, String> titles = getContextFileTitles(language);
        return titles.getOrDefault(fileName, fileName);
    }

    // ── Daily memory title ─────────────────────────────────────────────

    private static final String CN_DAILY_MEMORY_TITLE = "## daily_memory/{date} - 今日记忆";
    private static final String EN_DAILY_MEMORY_TITLE = "## daily_memory/{date} - Today's Memory";

    private static final Map<String, String> DAILY_MEMORY_TITLE = new LinkedHashMap<>();

    static {
        DAILY_MEMORY_TITLE.put("cn", CN_DAILY_MEMORY_TITLE);
        DAILY_MEMORY_TITLE.put("en", EN_DAILY_MEMORY_TITLE);
    }

    public static String getDailyMemoryTitle(String language, String date) {
        String template = DAILY_MEMORY_TITLE.getOrDefault(language, CN_DAILY_MEMORY_TITLE);
        return template.replace("{date}", date);
    }

    // ── Directory/file descriptions ────────────────────────────────────

    private static final Map<String, String> CN_DIRECTORY_DESCRIPTIONS = new LinkedHashMap<>();
    private static final Map<String, String> EN_DIRECTORY_DESCRIPTIONS = new LinkedHashMap<>();
    private static final Map<String, Map<String, String>> DIRECTORY_DESCRIPTIONS = new LinkedHashMap<>();

    static {
        CN_DIRECTORY_DESCRIPTIONS.put("AGENT.md", "智能体配置");
        CN_DIRECTORY_DESCRIPTIONS.put("SOUL.md", "灵魂与价值观");
        CN_DIRECTORY_DESCRIPTIONS.put("HEARTBEAT.md", "心跳任务");
        CN_DIRECTORY_DESCRIPTIONS.put("USER.md", "用户信息");
        CN_DIRECTORY_DESCRIPTIONS.put("IDENTITY.md", "身份凭证");
        CN_DIRECTORY_DESCRIPTIONS.put("MEMORY.md", "长期记忆");
        CN_DIRECTORY_DESCRIPTIONS.put("memory", "记忆核心模块");
        CN_DIRECTORY_DESCRIPTIONS.put("daily_memory", "每日结构化记忆");
        CN_DIRECTORY_DESCRIPTIONS.put("todo", "待办事项");
        CN_DIRECTORY_DESCRIPTIONS.put("messages", "消息历史");
        CN_DIRECTORY_DESCRIPTIONS.put("skills", "技能库");
        CN_DIRECTORY_DESCRIPTIONS.put("agents", "子智能体");

        EN_DIRECTORY_DESCRIPTIONS.put("AGENT.md", "Agent configuration");
        EN_DIRECTORY_DESCRIPTIONS.put("SOUL.md", "Soul & values");
        EN_DIRECTORY_DESCRIPTIONS.put("HEARTBEAT.md", "Heartbeat tasks");
        EN_DIRECTORY_DESCRIPTIONS.put("USER.md", "User information");
        EN_DIRECTORY_DESCRIPTIONS.put("IDENTITY.md", "Identity credentials");
        EN_DIRECTORY_DESCRIPTIONS.put("MEMORY.md", "Long-term memory");
        EN_DIRECTORY_DESCRIPTIONS.put("memory", "Memory core module");
        EN_DIRECTORY_DESCRIPTIONS.put("daily_memory", "Daily structured memory");
        EN_DIRECTORY_DESCRIPTIONS.put("todo", "Todo items");
        EN_DIRECTORY_DESCRIPTIONS.put("messages", "Message history");
        EN_DIRECTORY_DESCRIPTIONS.put("skills", "Skills library");
        EN_DIRECTORY_DESCRIPTIONS.put("agents", "Sub-agents");

        DIRECTORY_DESCRIPTIONS.put("cn", CN_DIRECTORY_DESCRIPTIONS);
        DIRECTORY_DESCRIPTIONS.put("en", EN_DIRECTORY_DESCRIPTIONS);
    }

    public static Map<String, String> getDirectoryDescriptions(String language) {
        return DIRECTORY_DESCRIPTIONS.getOrDefault(language, CN_DIRECTORY_DESCRIPTIONS);
    }

    public static String getDirectoryDescription(String language, String name) {
        Map<String, String> descriptions = getDirectoryDescriptions(language);
        return descriptions.getOrDefault(name, name);
    }

    // ── Fixed context files ────────────────────────────────────────────

    public static final List<String> CONTEXT_FILES = Collections.unmodifiableList(Arrays.asList(
            "AGENT.md",
            "SOUL.md",
            "HEARTBEAT.md",
            "USER.md",
            "IDENTITY.md"
    ));
}