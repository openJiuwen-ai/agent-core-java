/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.single_agent.AbilityManager;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.core.sys_operation.BaseFsOperation;
import com.openjiuwen.core.sys_operation.SysOperation;
import com.openjiuwen.core.sys_operation.result.FileSystemData;
import com.openjiuwen.core.sys_operation.result.FileSystemItem;
import com.openjiuwen.core.sys_operation.result.ListFilesResult;
import com.openjiuwen.core.sys_operation.result.ReadFileData;
import com.openjiuwen.core.sys_operation.result.ReadFileResult;
import com.openjiuwen.harness.prompts.workspace_content.WorkspaceHeader;
import com.openjiuwen.harness.workspace.Workspace;
import com.openjiuwen.harness.workspace.WorkspaceNode;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Context prompt section helpers.
 *
 * <p>Mirrors Python's context prompt module in
 * {@code openjiuwen/harness/prompts/sections/context.py}.</p>
 */
public final class ContextSection {

    private static final int MAX_TEMPLATE_LENGTH = 500;
    private static final int CONTEXT_PRIORITY = 80;
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+.*$", Pattern.MULTILINE);
    private static final List<String> TEMPLATE_MARKERS = List.of(
            "此处应保存的内容",
            "What should be saved here",
            "在你们的第一次对话中填写",
            "Fill this in during your first",
            "在这里添加你需要",
            "Add your periodic tasks here"
    );
    private static final Set<String> HIDDEN_TOOLS = Set.of(
            "cron_list_jobs",
            "cron_get_job",
            "cron_create_job",
            "cron_update_job",
            "cron_delete_job",
            "cron_toggle_job",
            "cron_preview_job"
    );

    private ContextSection() {
    }

    static boolean isUnfilledTemplate(String content) {
        String safeContent = content == null ? "" : content;
        if (safeContent.length() > MAX_TEMPLATE_LENGTH) {
            return false;
        }
        String text = HTML_COMMENT.matcher(safeContent).replaceAll("").strip();
        if (text.isEmpty()) {
            return true;
        }
        for (String marker : TEMPLATE_MARKERS) {
            if (safeContent.contains(marker)) {
                return true;
            }
        }
        String withoutHeadings = MARKDOWN_HEADING.matcher(text).replaceAll("").strip();
        return withoutHeadings.isEmpty();
    }

    static String formatDate(String timezone) {
        String resolvedTimezone = timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone;
        return LocalDate.now(ZoneId.of(resolvedTimezone)).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    static String readContextFile(SysOperation sysOperation, Workspace workspace, String fileKey) {
        if (sysOperation == null || workspace == null || fileKey == null || fileKey.isBlank()) {
            return null;
        }
        Path fullPath;
        if (WorkspaceNode.MEMORY_MD.getValue().equals(fileKey)) {
            Path memoryDir = workspace.getNodePath(WorkspaceNode.MEMORY);
            fullPath = memoryDir == null ? null : memoryDir.resolve(WorkspaceNode.MEMORY_MD.getValue());
        } else {
            fullPath = workspace.getNodePath(fileKey);
        }
        if (fullPath == null) {
            return null;
        }

        try {
            ReadFileResult result = sysOperation.fs().readFile(
                    fullPath.toString(),
                    BaseFsOperation.FileMode.TEXT,
                    null,
                    null,
                    null,
                    "UTF-8",
                    BaseFsOperation.DEFAULT_READ_CHUNK_SIZE,
                    Map.of()
            ).join();
            if (result.getCode() == 0) {
                ReadFileData data = result.getData();
                if (data != null && data.getContent() != null) {
                    String content = String.valueOf(data.getContent());
                    if (!content.isEmpty() && !isUnfilledTemplate(content)) {
                        return content;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    static String readDailyMemory(SysOperation sysOperation, Workspace workspace, String timezone) {
        if (sysOperation == null || workspace == null) {
            return null;
        }
        Path memoryDir = workspace.getNodePath(WorkspaceNode.MEMORY);
        if (memoryDir == null) {
            return null;
        }
        String date = formatDate(timezone);
        Path dailyMemoryDir = memoryDir.resolve(WorkspaceNode.DAILY_MEMORY.getValue());
        String todayFile = date + ".md";
        try {
            ListFilesResult listResult = sysOperation.fs().listFiles(
                    dailyMemoryDir.toString(),
                    false,
                    null,
                    BaseFsOperation.SortBy.NAME,
                    false,
                    null,
                    Map.of()
            ).join();
            FileSystemData data = listResult.getData();
            if (listResult.getCode() != 0 || data == null || data.getListItems() == null) {
                return null;
            }
            boolean todayExists = data.getListItems().stream()
                    .filter(Objects::nonNull)
                    .map(FileSystemItem::getName)
                    .anyMatch(todayFile::equals);
            if (!todayExists) {
                return null;
            }
            ReadFileResult result = sysOperation.fs().readFile(
                    dailyMemoryDir.resolve(todayFile).toString(),
                    BaseFsOperation.FileMode.TEXT,
                    null,
                    null,
                    null,
                    "UTF-8",
                    BaseFsOperation.DEFAULT_READ_CHUNK_SIZE,
                    Map.of()
            ).join();
            ReadFileData readData = result.getData();
            return result.getCode() == 0 && readData != null && readData.getContent() != null
                    ? String.valueOf(readData.getContent())
                    : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static String buildContextContent(SysOperation sysOperation,
                                             Workspace workspace,
                                             String language,
                                             String extraContent,
                                             String timezone,
                                             boolean includeDailyMemory) {
        String resolvedLanguage = "en".equals(language) ? "en" : "cn";
        StringBuilder builder = new StringBuilder(WorkspaceHeader.getContextHeader(resolvedLanguage));
        Map<String, String> titles = WorkspaceHeader.getContextFileTitles(resolvedLanguage);

        for (String fileKey : WorkspaceHeader.CONTEXT_FILES) {
            String content = readContextFile(sysOperation, workspace, fileKey);
            if (content == null) {
                continue;
            }
            String title = titles.getOrDefault(fileKey, "## " + fileKey);
            builder.append(title).append("\n\n").append(content).append("\n\n");
        }

        if ("cn".equals(resolvedLanguage)) {
            builder.append("[以下文件仅在有实际内容时注入，空文件跳过]\n\n");
        } else {
            builder.append("[The following files are injected only when they contain real content; ")
                    .append("empty files are skipped]\n\n");
        }

        if (includeDailyMemory) {
            String dailyContent = readDailyMemory(sysOperation, workspace, timezone);
            if (dailyContent != null && !dailyContent.isEmpty()) {
                String date = formatDate(timezone);
                builder.append(WorkspaceHeader.getDailyMemoryTitle(resolvedLanguage, date))
                        .append("\n\n")
                        .append(dailyContent)
                        .append("\n\n");
            }
        }
        if (extraContent != null && !extraContent.isEmpty()) {
            builder.append(extraContent);
        }
        return builder.toString();
    }

    public static PromptSection buildContextSection(SysOperation sysOperation,
                                                    Workspace workspace,
                                                    String language,
                                                    String toolsContent,
                                                    String timezone,
                                                    boolean includeDailyMemory) {
        if (workspace == null) {
            return null;
        }
        String resolvedLanguage = "en".equals(language) ? "en" : "cn";
        Map<String, String> content = new LinkedHashMap<>();
        content.put(resolvedLanguage, buildContextContent(
                sysOperation,
                workspace,
                resolvedLanguage,
                toolsContent,
                timezone,
                includeDailyMemory
        ));
        return new PromptSection(SectionName.CONTEXT, content, CONTEXT_PRIORITY);
    }

    public static Optional<PromptSection> buildContextSectionOptional(SysOperation sysOperation,
                                                                      Workspace workspace,
                                                                      String language,
                                                                      String toolsContent,
                                                                      String timezone,
                                                                      boolean includeDailyMemory) {
        return Optional.ofNullable(buildContextSection(
                sysOperation,
                workspace,
                language,
                toolsContent,
                timezone,
                includeDailyMemory
        ));
    }

    public static String buildToolsContent(AbilityManager abilityManager, String language) {
        if (abilityManager == null) {
            return null;
        }
        String resolvedLanguage = "en".equals(language) ? "en" : "cn";
        Map<String, String> toolDescriptions = new LinkedHashMap<>();
        for (Object ability : abilityManager.list()) {
            if (ability instanceof ToolCard card
                    && card.getName() != null
                    && !card.getName().isBlank()
                    && card.getDescription() != null
                    && !card.getDescription().isBlank()) {
                toolDescriptions.put(card.getName(), card.getDescription());
            }
        }
        if (toolDescriptions.isEmpty()) {
            return null;
        }

        Map<String, String> summaryOverrides = summaryOverrides(resolvedLanguage);
        StringBuilder builder = new StringBuilder("cn".equals(resolvedLanguage) ? "# 可用工具\n\n" : "# Available Tools\n\n");
        Set<String> renderedNames = new LinkedHashSet<>();

        List<String> preferredOrder = List.of(
                "paid_search",
                "free_search",
                "fetch_webpage",
                "image_ocr",
                "visual_question_answering",
                "audio_transcription",
                "audio_question_answering",
                "audio_metadata",
                "video_understanding",
                "session_new",
                "session_cancel",
                "session_list",
                "cron"
        );
        for (String name : preferredOrder) {
            appendSingleTool(builder, toolDescriptions, summaryOverrides, renderedNames, name);
        }

        renderToolGroup(
                builder,
                toolDescriptions,
                summaryOverrides,
                renderedNames,
                List.of("read_file", "write_file", "edit_file"),
                "read_file / write_file / edit_file",
                "cn".equals(resolvedLanguage) ? "文件读写编辑" : "Read, write, and edit files"
        );
        renderToolGroup(
                builder,
                toolDescriptions,
                summaryOverrides,
                renderedNames,
                List.of("glob", "list_files", "grep"),
                "glob / list_files / grep",
                "cn".equals(resolvedLanguage) ? "文件搜索" : "Search files and file contents"
        );

        appendSingleTool(builder, toolDescriptions, summaryOverrides, renderedNames, "bash");
        appendSingleTool(builder, toolDescriptions, summaryOverrides, renderedNames, "code");
        appendSingleTool(builder, toolDescriptions, summaryOverrides, renderedNames, "list_skill");
        renderToolGroup(
                builder,
                toolDescriptions,
                summaryOverrides,
                renderedNames,
                List.of("memory_search", "memory_get", "write_memory", "edit_memory", "read_memory"),
                "memory_search / memory_get / write_memory / edit_memory / read_memory",
                "cn".equals(resolvedLanguage) ? "记忆系统" : "Memory system"
        );
        appendSingleTool(builder, toolDescriptions, summaryOverrides, renderedNames, "task_tool");

        appendBashGuidelines(builder, renderedNames, resolvedLanguage);
        appendTaskToolGuidelines(builder, toolDescriptions, renderedNames, resolvedLanguage);

        for (Map.Entry<String, String> entry : toolDescriptions.entrySet()) {
            String name = entry.getKey();
            if (renderedNames.contains(name) || HIDDEN_TOOLS.contains(name)) {
                continue;
            }
            builder.append("- ")
                    .append(name)
                    .append(": ")
                    .append(summaryOverrides.getOrDefault(name, firstLine(entry.getValue())))
                    .append("\n");
        }
        return builder.toString();
    }

    public static PromptSection buildToolsSection(AbilityManager abilityManager, String language) {
        String resolvedLanguage = "en".equals(language) ? "en" : "cn";
        String content = buildToolsContent(abilityManager, resolvedLanguage);
        if (content == null || content.isEmpty()) {
            return null;
        }
        return new PromptSection(SectionName.TOOLS, Map.of(resolvedLanguage, content), 30);
    }

    private static void appendSingleTool(StringBuilder builder,
                                         Map<String, String> toolDescriptions,
                                         Map<String, String> summaryOverrides,
                                         Set<String> renderedNames,
                                         String name) {
        if (!toolDescriptions.containsKey(name) || renderedNames.contains(name) || HIDDEN_TOOLS.contains(name)) {
            return;
        }
        builder.append("- ")
                .append(name)
                .append(": ")
                .append(toolSummary(name, toolDescriptions, summaryOverrides))
                .append("\n");
        renderedNames.add(name);
    }

    private static void renderToolGroup(StringBuilder builder,
                                        Map<String, String> toolDescriptions,
                                        Map<String, String> summaryOverrides,
                                        Set<String> renderedNames,
                                        Collection<String> names,
                                        String label,
                                        String summary) {
        List<String> existing = new ArrayList<>();
        for (String name : names) {
            if (toolDescriptions.containsKey(name) && !HIDDEN_TOOLS.contains(name)) {
                existing.add(name);
            }
        }
        if (existing.size() == names.size()) {
            builder.append("- ").append(label).append(": ").append(summary).append("\n");
            renderedNames.addAll(existing);
        } else {
            for (String name : existing) {
                appendSingleTool(builder, toolDescriptions, summaryOverrides, renderedNames, name);
            }
        }
    }

    private static void appendBashGuidelines(StringBuilder builder, Set<String> renderedNames, String language) {
        if (!renderedNames.contains("bash")) {
            return;
        }
        if ("cn".equals(language)) {
            builder.append("\n")
                    .append("## bash 使用原则\n\n")
                    .append("- 优先使用专用工具完成文件搜索、内容搜索、读取、编辑和写入，不要用 bash 替代 `glob` / `grep` / ")
                    .append("`read_file` / `edit_file` / `write_file`\n")
                    .append("- 独立命令尽量并行调用；多步依赖命令才在单次调用里用 `&&` 串联，仅在不关心前序失败时才用 `;`\n")
                    .append("- 长时间运行命令使用 `background: true`，不要用 `sleep` 轮询等待\n")
                    .append("- 尽量使用绝对路径并避免频繁 `cd`；路径包含空格时使用双引号\n")
                    .append("- 执行破坏性 Git 操作前先考虑更安全的替代方案\n");
        } else {
            builder.append("\n")
                    .append("## bash Guidelines\n\n")
                    .append("- Prefer dedicated tools for file search, content search, reading, editing, and writing ")
                    .append("instead of using bash as a substitute for `glob` / `grep` / `read_file` / `edit_file` / ")
                    .append("`write_file`\n")
                    .append("- Run independent commands in parallel; only chain dependent commands with `&&`, and ")
                    .append("use `;` only when earlier failures do not matter\n")
                    .append("- Use `background: true` for long-running commands instead of polling with `sleep`\n")
                    .append("- Prefer absolute paths and avoid frequent `cd`; quote paths with spaces using double quotes\n")
                    .append("- Consider safer alternatives before destructive Git operations\n");
        }
    }

    private static void appendTaskToolGuidelines(StringBuilder builder,
                                                 Map<String, String> toolDescriptions,
                                                 Set<String> renderedNames,
                                                 String language) {
        if (!renderedNames.contains("task_tool")) {
            return;
        }
        if ("cn".equals(language)) {
            builder.append("\n")
                    .append("## task_tool 使用原则\n\n")
                    .append("- 任务复杂、多步骤、可独立执行时使用\n")
                    .append("- 独立任务尽量并行执行\n")
                    .append("- 简单任务直接执行，不使用子代理\n");
        } else {
            builder.append("\n")
                    .append("## task_tool Guidelines\n\n")
                    .append("- Use it for complex, multi-step, independent tasks\n")
                    .append("- Run independent tasks in parallel when possible\n")
                    .append("- Execute simple tasks directly without spawning a sub-agent\n");
        }
        List<String> agentLines = extractTaskToolAgentLines(toolDescriptions.get("task_tool"), language);
        if (!agentLines.isEmpty()) {
            builder.append("\n")
                    .append("cn".equals(language) ? "可用代理类型：" : "Available agent types:")
                    .append("\n");
            for (String line : agentLines) {
                builder.append(line).append("\n");
            }
        }
    }

    static List<String> extractTaskToolAgentLines(String description, String language) {
        if (description == null || description.isBlank()) {
            return List.of();
        }
        boolean chinese = "cn".equals(language);
        String marker = chinese ? "可用代理类型及对应工具：" : "Available agent types and the tools they have access to:";
        String stopMarker = chinese ? "重要：" : "Important:";
        if (!description.contains(marker)) {
            return List.of();
        }
        String body = description.substring(description.indexOf(marker) + marker.length());
        if (body.contains(stopMarker)) {
            body = body.substring(0, body.indexOf(stopMarker));
        }
        List<String> lines = new ArrayList<>();
        for (String rawLine : body.strip().split("\\R")) {
            String line = rawLine.strip();
            if (!line.isEmpty()) {
                lines.add(line.startsWith("- ") ? line : "- " + line);
            }
        }
        return lines;
    }

    private static Map<String, String> summaryOverrides(String language) {
        Map<String, String> values = new LinkedHashMap<>();
        if ("cn".equals(language)) {
            values.put("paid_search", "付费联网搜索（配置 API 时优先使用）");
            values.put("free_search", "免费搜索（DuckDuckGo 等）");
            values.put("fetch_webpage", "抓取网页文本内容");
            values.put("image_ocr", "读取图片中的文字");
            values.put("visual_question_answering", "理解图片内容并回答问题");
            values.put("audio_transcription", "转写音频文件");
            values.put("audio_question_answering", "理解音频内容并回答");
            values.put("audio_metadata", "识别音频时长和歌曲信息");
            values.put("video_understanding", "分析视频内容");
            values.put("session_new", "创建多个协程任务（子 agent 异步运行）");
            values.put("session_cancel", "取消正在运行的协程");
            values.put("session_list", "查看所有协程状态");
            values.put("cron", "管理定时任务与提醒");
            values.put("bash", "执行 Shell 命令");
            values.put("code", "执行 Python 或 JavaScript 代码");
            values.put("list_skill", "列出可用技能");
            values.put("task_tool", "启动临时子代理处理复杂任务");
        } else {
            values.put("paid_search", "Paid web search (preferred when configured)");
            values.put("free_search", "Free web search");
            values.put("fetch_webpage", "Fetch webpage text");
            values.put("image_ocr", "Read text from images");
            values.put("visual_question_answering", "Understand images and answer questions");
            values.put("audio_transcription", "Transcribe audio");
            values.put("audio_question_answering", "Understand audio and answer questions");
            values.put("audio_metadata", "Identify audio duration and song metadata");
            values.put("video_understanding", "Analyze video content");
            values.put("session_new", "Create async sub-agent sessions");
            values.put("session_cancel", "Cancel a running sub-agent session");
            values.put("session_list", "List sub-agent session status");
            values.put("cron", "Manage scheduled jobs and reminders");
            values.put("bash", "Run shell commands");
            values.put("code", "Run Python or JavaScript code");
            values.put("list_skill", "List available skills");
            values.put("task_tool", "Launch a temporary sub-agent for complex work");
        }
        return values;
    }

    private static String toolSummary(String name,
                                      Map<String, String> toolDescriptions,
                                      Map<String, String> summaryOverrides) {
        return summaryOverrides.getOrDefault(name, toolDescriptions.getOrDefault(name, "").strip());
    }

    private static String firstLine(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        return description.strip().split("\\R", 2)[0];
    }
}
