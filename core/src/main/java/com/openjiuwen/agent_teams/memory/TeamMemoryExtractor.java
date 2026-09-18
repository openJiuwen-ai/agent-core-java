/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.memory;

import com.openjiuwen.agent_teams.tools.TeamMessage;
import com.openjiuwen.agent_teams.tools.TeamTask;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Leader-side extraction agent helpers for team-memory files.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.memory.extractor} in
 * {@code openjiuwen/agent_teams/memory/extractor.py}.</p>
 */
public final class TeamMemoryExtractor {

    public static final int TASK_CONTENT_PREVIEW_MAX = 2000;
    public static final int MESSAGE_CONTENT_PREVIEW_MAX = 1000;
    public static final int EXTRACTION_AGENT_MAX_ITERATIONS = 5;
    public static final String TEAM_MEMORY_FILENAME = "TEAM_MEMORY.md";
    public static final int TEAM_MEMORY_MAX_READ_LINES = 200;

    public static final String EXTRACTION_AGENT_PROMPT = """
            你是团队记忆提取 agent。你的工作目录是团队记忆目录，里面可能已有之前提取的记忆文件。

            ## 你的任务

            分析提供的团队协作记录（任务和消息），从中提炼出对未来团队协作有价值的持久记忆，写入记忆文件。

            ## 工作流程

            1. 先用 Read 读取已有的记忆文件（如 TEAM_MEMORY.md），了解已记录的内容
            2. 分析新的协作记录，判断哪些信息值得记忆
            3. 用 Write/Edit 更新记忆文件：
               - 更新已有记忆条目（如果新信息补充或修正了旧内容）
               - 添加新的记忆条目
               - 删除已过时的条目
               - 合并重复内容

            ## 提取什么

            1. **[decision] 团队决策**: 为什么选择了某个方案、拒绝了哪些替代方案、关键权衡
            2. **[lesson] 经验教训**: 什么做法有效、什么导致了返工或问题、值得复用的模式
            3. **[member] 成员特长**: 谁擅长什么、谁负责哪个领域、协作模式
            4. **[context] 项目背景**: 非代码可推导的业务约束、截止日期、利益相关方要求

            ## 不要提取什么

            - 代码细节、具体文件路径、函数名（可从代码库获取）
            - 临时状态、进行中的调试过程
            - 原始对话内容的复述（提取的是洞察，不是摘要）
            - 任何敏感信息（密钥、凭证、个人隐私）

            ## 记忆文件格式

            TEAM_MEMORY.md 中每条记忆用三级标题 + 类型标签，示例：

                ### [decision] 选择了方案 A 而非 B
                原因是... 权衡是...

                ### [lesson] 并行任务需要先对齐接口
                上次因为没对齐导致返工 2 天...

            保持 TEAM_MEMORY.md 在 200 行以内。超出时合并或删除最旧的条目。
            如果没有值得提取的新信息，不要修改文件。
            """;

    private static final DateTimeFormatter MESSAGE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private TeamMemoryExtractor() {
    }

    /**
     * Assemble task and message records into the prompt context.
     *
     * <p>Mirrors Python's {@code _build_extraction_context} in
     * {@code openjiuwen/agent_teams/memory/extractor.py}.</p>
     */
    public static String buildExtractionContext(
            List<TeamTask> tasks,
            List<TeamMessage> messages,
            double timezoneOffsetHours
    ) {
        ZoneOffset zoneOffset = ZoneOffset.ofTotalSeconds((int) Math.round(timezoneOffsetHours * 3600.0d));
        List<String> parts = new ArrayList<>();
        parts.add("# 本轮团队协作记录\n");

        if (tasks != null && !tasks.isEmpty()) {
            parts.add("## 任务记录\n");
            for (TeamTask task : tasks) {
                String assignee = isBlank(task.getAssignee()) ? "未分配" : task.getAssignee();
                parts.add("### " + nullToEmpty(task.getTitle()) + " [" + nullToEmpty(task.getStatus()) + "] -> "
                        + assignee);
                if (!isBlank(task.getContent())) {
                    parts.add(preview(task.getContent(), TASK_CONTENT_PREVIEW_MAX));
                }
                parts.add("");
            }
        }

        if (messages != null && !messages.isEmpty()) {
            parts.add("## 团队对话\n");
            List<TeamMessage> sortedMessages = messages.stream()
                    .sorted(Comparator.comparing(message -> Optional.ofNullable(message.getTimestamp()).orElse(0L)))
                    .toList();
            for (TeamMessage message : sortedMessages) {
                long timestamp = Optional.ofNullable(message.getTimestamp()).orElse(0L);
                String time = Instant.ofEpochMilli(timestamp)
                        .atZone(zoneOffset)
                        .format(MESSAGE_TIME_FORMATTER);
                String direction = Boolean.TRUE.equals(message.getBroadcast())
                        ? "-> 全体"
                        : "-> " + (message.getToMemberName() == null ? "?" : message.getToMemberName());
                parts.add("[" + time + "] " + nullToEmpty(message.getFromMemberName()) + " " + direction + ": "
                        + preview(nullToEmpty(message.getContent()), MESSAGE_CONTENT_PREVIEW_MAX));
            }
            parts.add("");
        }

        return String.join("\n", parts);
    }

    /**
     * Create restricted memory-file tool descriptors.
     *
     * <p>Mirrors Python's {@code _create_extraction_tools} in
     * {@code openjiuwen/agent_teams/memory/extractor.py}.</p>
     */
    public static List<ExtractionTool> createExtractionTools(String teamMemoryDir, FileSystemView fileSystem) {
        return createExtractionTools(teamMemoryDir, fileSystem, "");
    }

    public static List<ExtractionTool> createExtractionTools(
            String teamMemoryDir,
            FileSystemView fileSystem,
            String teamName
    ) {
        Objects.requireNonNull(teamMemoryDir, "teamMemoryDir");
        Objects.requireNonNull(fileSystem, "fileSystem");
        String prefix = teamName == null || teamName.isEmpty() ? "extract" : "extract." + teamName;
        return List.of(
                new ExtractionTool(
                        prefix + ".read",
                        "read_memory_file",
                        "读取团队记忆目录下的文件",
                        ToolKind.READ
                ),
                new ExtractionTool(
                        prefix + ".write",
                        "write_memory_file",
                        "写入团队记忆目录下的文件（覆盖）",
                        ToolKind.WRITE
                ),
                new ExtractionTool(
                        prefix + ".list",
                        "list_memory_files",
                        "列出团队记忆目录下的所有文件",
                        ToolKind.LIST
                )
        );
    }

    public static CompletionStage<FileToolResult> readMemoryFile(
            String teamMemoryDir,
            FileSystemView fileSystem,
            String path
    ) {
        String safePath = safeMemoryPath(teamMemoryDir, path);
        if (safePath == null) {
            return CompletableFuture.completedFuture(FileToolResult.error("Invalid path"));
        }
        return fileSystem.readFile(safePath)
                .handle((content, throwable) -> {
                    if (throwable != null) {
                        return FileToolResult.content("", safePath, "file not found");
                    }
                    return FileToolResult.content(content.orElse(""), safePath, null);
                });
    }

    public static CompletionStage<FileToolResult> writeMemoryFile(
            String teamMemoryDir,
            FileSystemView fileSystem,
            String path,
            String content
    ) {
        String safePath = safeMemoryPath(teamMemoryDir, path);
        if (safePath == null) {
            return CompletableFuture.completedFuture(FileToolResult.error("Invalid path"));
        }
        return fileSystem.writeFile(safePath, content == null ? "" : content, true)
                .handle((success, throwable) -> {
                    if (throwable != null) {
                        return FileToolResult.writeFailure(exceptionMessage(throwable));
                    }
                    return Boolean.TRUE.equals(success)
                            ? FileToolResult.writeSuccess(safePath)
                            : FileToolResult.writeFailure("write failed");
                });
    }

    public static CompletionStage<FileToolResult> listMemoryFiles(String teamMemoryDir, FileSystemView fileSystem) {
        return fileSystem.listFiles(teamMemoryDir, false)
                .handle((entries, throwable) -> {
                    if (throwable != null) {
                        return FileToolResult.files(List.of());
                    }
                    List<String> files = entries.stream()
                            .filter(entry -> !entry.directory())
                            .map(FileEntry::name)
                            .toList();
                    return FileToolResult.files(files);
                });
    }

    /**
     * Run extraction when all required runtime components are available.
     *
     * <p>Mirrors Python's {@code extract_team_memories} in
     * {@code openjiuwen/agent_teams/memory/extractor.py}.</p>
     */
    public static CompletionStage<Void> extractTeamMemories(ExtractionRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.sysOperation() == null
                || isBlank(request.teamMemoryDir())
                || request.model() == null
                || request.database() == null
                || request.taskManager() == null
                || request.agentFactory() == null
                || request.runner() == null) {
            return CompletableFuture.completedFuture(null);
        }

        return request.taskManager().listTasks()
                .thenCompose(tasks -> request.database().message().getTeamMessages(request.teamName())
                        .thenCompose(messages -> runExtractionIfNeeded(request, tasks, messages)))
                .exceptionally(throwable -> null);
    }

    private static CompletionStage<Void> runExtractionIfNeeded(
            ExtractionRequest request,
            List<TeamTask> tasks,
            List<TeamMessage> messages
    ) {
        List<TeamTask> safeTasks = tasks == null ? List.of() : tasks;
        List<TeamMessage> safeMessages = messages == null ? List.of() : messages;
        if (safeTasks.isEmpty() && safeMessages.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            java.nio.file.Files.createDirectories(Path.of(request.teamMemoryDir()));
            String context = buildExtractionContext(safeTasks, safeMessages, request.timezoneOffsetHours());
            List<ExtractionTool> tools = createExtractionTools(
                    request.teamMemoryDir(),
                    request.sysOperation(),
                    request.teamName()
            );
            AgentHandle agent = request.agentFactory().createAgent(
                    request.model(),
                    EXTRACTION_AGENT_PROMPT,
                    tools,
                    EXTRACTION_AGENT_MAX_ITERATIONS,
                    false
            );
            String query = "请分析以下团队 " + request.teamName() + " 的协作记录并提取记忆：\n\n" + context;
            return request.runner().runAgent(agent, query).exceptionally(throwable -> null);
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(null);
        } catch (java.io.IOException exception) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static String safeMemoryPath(String teamMemoryDir, String path) {
        if (path == null || path.contains("..") || path.startsWith("/")) {
            return null;
        }
        Path fileName = Path.of(path).getFileName();
        if (fileName == null) {
            return null;
        }
        return Path.of(teamMemoryDir, fileName.toString()).toString();
    }

    private static String preview(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String exceptionMessage(Throwable throwable) {
        Throwable current = throwable;
        if (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null ? current.getClass().getSimpleName() : message;
    }

    public record ExtractionTool(String id, String name, String description, ToolKind kind) {
    }

    public enum ToolKind {
        READ,
        WRITE,
        LIST
    }

    public record FileEntry(String name, boolean directory) {
    }

    public record FileToolResult(
            boolean success,
            String path,
            String content,
            String error,
            String note,
            List<String> files
    ) {

        public static FileToolResult content(String content, String path, String note) {
            return new FileToolResult(true, path, content, null, note, null);
        }

        public static FileToolResult error(String error) {
            return new FileToolResult(false, null, null, error, null, null);
        }

        public static FileToolResult writeSuccess(String path) {
            return new FileToolResult(true, path, null, null, null, null);
        }

        public static FileToolResult writeFailure(String error) {
            return new FileToolResult(false, null, null, error, null, null);
        }

        public static FileToolResult files(List<String> files) {
            return new FileToolResult(true, null, null, null, null, files);
        }
    }

    public record ExtractionRequest(
            String teamName,
            TeamDatabaseView database,
            TeamTaskManagerView taskManager,
            String teamMemoryDir,
            FileSystemView sysOperation,
            ModelView model,
            double timezoneOffsetHours,
            AgentFactory agentFactory,
            RunnerView runner
    ) {
    }

    public interface TeamDatabaseView {
        TeamMessageStoreView message();
    }

    public interface TeamMessageStoreView {
        CompletionStage<List<TeamMessage>> getTeamMessages(String teamName);
    }

    public interface TeamTaskManagerView {
        CompletionStage<List<TeamTask>> listTasks();
    }

    public interface FileSystemView {
        CompletionStage<Optional<String>> readFile(String path);

        CompletionStage<Boolean> writeFile(String path, String content, boolean createIfNotExist);

        CompletionStage<List<FileEntry>> listFiles(String path, boolean recursive);
    }

    public interface ModelView {
    }

    public interface AgentHandle {
    }

    public interface AgentFactory {
        AgentHandle createAgent(
                ModelView model,
                String systemPrompt,
                List<ExtractionTool> tools,
                int maxIterations,
                boolean enableTaskLoop
        );
    }

    public interface RunnerView {
        CompletionStage<Void> runAgent(AgentHandle agent, String query);
    }
}
