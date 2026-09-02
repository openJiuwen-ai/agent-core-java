/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.team;

import com.openjiuwen.agentteams.tools.TeamTaskManager;
import com.openjiuwen.agentteams.tools.database.MessageRecord;
import com.openjiuwen.agentteams.tools.database.TeamDatabase;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leader-side extraction agent: distill team tasks/messages into team-memory files.
 * 
 * @since 0.1.7
 */
public final class TeamMemoryExtractor {
    /**
     * EXTRACTION_AGENT_MAX_ITERATIONS.
     * 
     * @since 0.1.7
     */
    public static final int EXTRACTION_AGENT_MAX_ITERATIONS = 5;

    /**
     * TASK_CONTENT_PREVIEW_MAX.
     * 
     * @since 0.1.7
     */
    public static final int TASK_CONTENT_PREVIEW_MAX = 2000;

    /**
     * MESSAGE_CONTENT_PREVIEW_MAX.
     * 
     * @since 0.1.7
     */
    public static final int MESSAGE_CONTENT_PREVIEW_MAX = 1000;

    /**
     * TEAM_MEMORY_FILENAME.
     * 
     * @since 0.1.7
     */
    public static final String TEAM_MEMORY_FILENAME = "TEAM_MEMORY.md";

    /**
     * TEAM_MEMORY_MAX_READ_LINES.
     * 
     * @since 0.1.7
     */
    public static final int TEAM_MEMORY_MAX_READ_LINES = 200;

    /**
     * EXTRACTION_AGENT_PROMPT.
     * 
     * @since 0.1.7
     */
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

    /**
     * TeamMemoryExtractor.
     * 
     * @since 0.1.7
     */
    private TeamMemoryExtractor() {
    }

    /**
     * buildExtractionContext.
     * 
     * @param tasks tasks
     * @param messages messages
     * @param tzOffsetHours tzOffsetHours
     * @return the result
     * @since 0.1.7
     */
    public static String buildExtractionContext(List<Map<String, Object>> tasks, List<Map<String, Object>> messages,
            double tzOffsetHours) {
        StringBuilder builder = new StringBuilder("# 本轮团队协作记录\n");
        if (tasks != null && !tasks.isEmpty()) {
            builder.append("\n## 任务记录\n");
            for (Map<String, Object> task : tasks) {
                String assignee = String.valueOf(task.getOrDefault("assignee", "未分配"));
                builder.append("### ").append(String.valueOf(task.getOrDefault("title", ""))).append(" [")
                        .append(String.valueOf(task.getOrDefault("status", ""))).append("] -> ")
                        .append(assignee.isBlank() ? "未分配" : assignee).append("\n");
                Object content = task.get("content");
                if (content != null) {
                    String text = String.valueOf(content);
                    builder.append(text, 0, Math.min(text.length(), TASK_CONTENT_PREVIEW_MAX)).append("\n\n");
                }
            }
        }
        if (messages != null && !messages.isEmpty()) {
            builder.append("\n## 团队对话\n");
            List<Map<String, Object>> sorted = new ArrayList<>(messages);
            sorted.sort((left, right) -> Double.compare(messageTimestamp(left), messageTimestamp(right)));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
                    .withZone(ZoneOffset.ofTotalSeconds(tzOffsetSeconds(tzOffsetHours)));
            for (Map<String, Object> message : sorted) {
                String content = String.valueOf(message.getOrDefault("content", ""));
                String timestamp = formatter.format(Instant.ofEpochMilli((long) (messageTimestamp(message) * 1000)));
                String toMemberName = String.valueOf(message.getOrDefault("to_member_name", ""));
                String direction = Boolean.TRUE.equals(message.get("broadcast")) || toMemberName.isBlank()
                        ? "-> 全体"
                        : "-> " + toMemberName;
                builder.append("[").append(timestamp).append("] ")
                        .append(String.valueOf(message.getOrDefault("from_member_name", "?"))).append(" ")
                        .append(direction).append(": ")
                        .append(content, 0, Math.min(content.length(), MESSAGE_CONTENT_PREVIEW_MAX)).append("\n");
            }
        }
        return builder.toString();
    }

    /**
     * createExtractionTools.
     * 
     * @param teamMemoryDir teamMemoryDir
     * @param sysOperation sysOperation
     * @param teamName teamName
     * @return the result
     * @since 0.1.7
     */
    public static List<Object> createExtractionTools(String teamMemoryDir, SysOperation sysOperation, String teamName) {
        String prefix = teamName == null || teamName.isBlank() ? "extract" : "extract." + teamName;
        List<Object> tools = new ArrayList<>();

        tools.add(new LocalFunction(
                ToolCard.builder().id(prefix + ".read").name("read_memory_file").description("读取团队记忆目录下的文件")
                        .inputParams(Map.of("type", "object", "properties", Map.of("path", Map.of("type", "string")),
                                "required", List.of("path")))
                        .build(),
                inputs -> readFile(teamMemoryDir, sysOperation, String.valueOf(inputs.getOrDefault("path", "")))));
        tools.add(new LocalFunction(
                ToolCard.builder().id(prefix + ".write").name("write_memory_file").description("写入团队记忆目录下的文件（覆盖）")
                        .inputParams(Map.of("type", "object", "properties",
                                Map.of("path", Map.of("type", "string"), "content", Map.of("type", "string")),
                                "required", List.of("path", "content")))
                        .build(),
                inputs -> writeFile(teamMemoryDir, sysOperation, String.valueOf(inputs.getOrDefault("path", "")),
                        String.valueOf(inputs.getOrDefault("content", "")))));
        tools.add(new LocalFunction(
                ToolCard.builder().id(prefix + ".list").name("list_memory_files").description("列出团队记忆目录下的所有文件")
                        .inputParams(Map.of("type", "object", "properties", Map.of())).build(),
                inputs -> listFiles(teamMemoryDir, sysOperation)));
        return tools;
    }

    /**
     * extractTeamMemories.
     * 
     * @param teamName teamName
     * @param db db
     * @param taskManager taskManager
     * @param teamMemoryDir teamMemoryDir
     * @param sysOperation sysOperation
     * @param model model
     * @param tzOffsetHours tzOffsetHours
     * @since 0.1.7
     */
    public static void extractTeamMemories(String teamName, TeamDatabase db, TeamTaskManager taskManager,
            String teamMemoryDir, SysOperation sysOperation, Model model, double tzOffsetHours) {
        if (sysOperation == null || teamMemoryDir == null || model == null || db == null || taskManager == null) {
            return;
        }
        try {
            List<com.openjiuwen.agentteams.tools.TeamTask> taskRecords = taskManager.list();
            List<MessageRecord> messageRecords = db.getTeamMessages(teamName);
            if ((taskRecords == null || taskRecords.isEmpty())
                    && (messageRecords == null || messageRecords.isEmpty())) {
                return;
            }
            Files.createDirectories(Path.of(teamMemoryDir));
            String context = buildExtractionContext(taskRecords.stream().map(TeamMemoryExtractor::taskToMap).toList(),
                    messageRecords.stream().map(TeamMemoryExtractor::messageToMap).toList(), tzOffsetHours);
            Workspace workspace = Workspace.builder().rootPath(teamMemoryDir).build();
            DeepAgent agent = HarnessFactory.createDeepAgent(null,
                    DeepAgentConfig.builder().systemPrompt(EXTRACTION_AGENT_PROMPT)
                            .tools(createExtractionTools(teamMemoryDir, sysOperation, teamName)).model(model)
                            .maxIterations(EXTRACTION_AGENT_MAX_ITERATIONS).isTaskLoopEnabled(false)
                            .workspacePath(workspace.root().toString()).build(),
                    workspace);
            agent.ensureInitialized();
            Runner.runAgent(agent, Map.of("query", "请分析以下团队 " + teamName + " 的协作记录并提取记忆：\n\n" + context), null, null);
            Loggers.MEMORY.info("[extractor] Extraction agent completed for {}", teamName);
        } catch (RuntimeException | java.io.IOException e) {
            Loggers.MEMORY.warning("[extractor] extractTeamMemories failed: {}", e.getMessage());
        }
    }

    /**
     * taskToMap.
     * 
     * @param task task
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> taskToMap(com.openjiuwen.agentteams.tools.TeamTask task) {
        if (task == null) {
            return Map.of();
        }
        return Map.of("title", task.getTitle() != null ? task.getTitle() : "", "status",
                task.getStatus() != null ? task.getStatus() : "", "assignee",
                task.getAssignee() != null ? task.getAssignee() : "", "content",
                task.getContent() != null ? task.getContent() : "");
    }

    /**
     * messageToMap.
     * 
     * @param message message
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> messageToMap(MessageRecord message) {
        if (message == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", message.getTimestamp());
        result.put("from_member_name", message.getFromMemberName() != null ? message.getFromMemberName() : "");
        result.put("to_member_name", message.getToMemberName() != null ? message.getToMemberName() : "");
        result.put("content", message.getContent() != null ? message.getContent() : "");
        result.put("broadcast", message.isBroadcast());
        return result;
    }

    /**
     * readFile.
     * 
     * @param teamMemoryDir teamMemoryDir
     * @param sysOperation sysOperation
     * @param path path
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> readFile(String teamMemoryDir, SysOperation sysOperation, String path) {
        String basename = safeBasename(path);
        if (basename == null) {
            return Map.of("error", "Invalid path");
        }
        Path target = Path.of(teamMemoryDir).resolve(basename).normalize();
        try {
            var result = sysOperation.fs().readFile(target.toString(), "text", null, null, null,
                    StandardCharsets.UTF_8.name(), 0, null);
            if (result != null && result.getData() != null) {
                Object content = result.getData().getContent();
                return Map.of("content", content != null ? String.valueOf(content) : "", "path", target.toString());
            }
            return Map.of("content", "", "path", target.toString());
        } catch (RuntimeException ignored) {
            return Map.of("content", "", "path", target.toString(), "note", "file not found");
        }
    }

    /**
     * writeFile.
     * 
     * @param teamMemoryDir teamMemoryDir
     * @param sysOperation sysOperation
     * @param path path
     * @param content content
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> writeFile(String teamMemoryDir, SysOperation sysOperation, String path,
            String content) {
        String basename = safeBasename(path);
        if (basename == null) {
            return Map.of("error", "Invalid path");
        }
        try {
            Path target = Path.of(teamMemoryDir).resolve(basename).normalize();
            sysOperation.fs().writeFile(target.toString(), content, "text", false, false, true, "644",
                    StandardCharsets.UTF_8.name(), null);
            return Map.of("success", true, "path", target.toString());
        } catch (RuntimeException e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * safeBasename.
     * 
     * @param path path
     * @return the result
     * @since 0.1.7
     */
    private static String safeBasename(String path) {
        if (path == null || path.contains("..") || path.startsWith("/") || Path.of(path).isAbsolute()) {
            return null;
        }
        return Path.of(path).getFileName().toString();
    }

    /**
     * listFiles.
     * 
     * @param teamMemoryDir teamMemoryDir
     * @param sysOperation sysOperation
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> listFiles(String teamMemoryDir, SysOperation sysOperation) {
        try {
            Path dir = Path.of(teamMemoryDir);
            var result = sysOperation.fs().listFiles(dir.toString(), false, null, null, false, List.of(), null);
            if (result != null && result.getData() != null && result.getData().getListItems() != null) {
                List<String> files = result.getData().getListItems().stream()
                        .filter(item -> item != null && !item.isDirectory()).map(item -> item.getName()).toList();
                return Map.of("files", files);
            }
        } catch (RuntimeException e) {
            return Map.of("files", List.of());
        }
        return Map.of("files", List.of());
    }

    /**
     * messageTimestamp.
     * 
     * @param message message
     * @return the result
     * @since 0.1.7
     */
    private static double messageTimestamp(Map<String, Object> message) {
        Object ts = message != null ? message.get("timestamp") : null;
        if (ts instanceof Number number) {
            return number.doubleValue();
        }
        if (ts != null) {
            try {
                return Double.parseDouble(String.valueOf(ts));
            } catch (NumberFormatException ignored) {
                // Invalid message timestamps fall back to the current team-clock value below.
            }
        }
        return TeamDatabase.getCurrentTime() / 1000.0;
    }

    /**
     * tzOffsetSeconds.
     * 
     * @param tzOffsetHours tzOffsetHours
     * @return the result
     * @since 0.1.7
     */
    private static int tzOffsetSeconds(double tzOffsetHours) {
        return BigDecimal.valueOf(tzOffsetHours).multiply(BigDecimal.valueOf(3600)).intValue();
    }
}
