/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.context_engineer;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.prompts.sections.SectionName;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.workspace.Workspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rail for context assembly that injects workspace, tools, and context sections.
 * <p>
 * Mirrors Python's {@code ContextAssembleRail} in
 * {@code openjiuwen.harness.rails.context_engineer.context_assemble_rail}.
 */
public class ContextAssembleRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(ContextAssembleRail.class);
    private static final int PRIORITY = 85;

    private SystemPromptBuilder systemPromptBuilder;
    private AbilityManager abilityManager;

    public ContextAssembleRail() {
        super();
        setPriority(PRIORITY);
    }

    @Override
    public void init(Object agent) {
        systemPromptBuilder = resolveBuilder(agent);
        abilityManager = resolveAbilityManager(agent);
        LOG.info("[ContextAssembleRail] Initialized");
    }

    @Override
    public void uninit(Object agent) {
        if (systemPromptBuilder != null) {
            systemPromptBuilder.removeSection(SectionName.WORKSPACE);
            systemPromptBuilder.removeSection(SectionName.TOOLS);
            systemPromptBuilder.removeSection(SectionName.CONTEXT);
        }
        systemPromptBuilder = null;
        abilityManager = null;
        LOG.info("[ContextAssembleRail] Uninitialized");
    }

    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        if (systemPromptBuilder == null) {
            return;
        }
        if (workspace == null) {
            systemPromptBuilder.removeSection(SectionName.WORKSPACE);
            systemPromptBuilder.removeSection(SectionName.TOOLS);
            systemPromptBuilder.removeSection(SectionName.CONTEXT);
            return;
        }

        String language = systemPromptBuilder.getLanguage() != null ? systemPromptBuilder.getLanguage() : "cn";
        PromptSection workspaceSection = buildWorkspaceSection(workspace, language);
        String toolsContent = buildToolsContent(abilityManager, language);
        PromptSection toolsSection = buildToolsSection(toolsContent, language);
        PromptSection contextSection = buildContextSection(
                workspace,
                language,
                toolsContent,
                ZoneId.of("Asia/Shanghai")
        );

        replaceOrRemove(systemPromptBuilder, SectionName.WORKSPACE, workspaceSection);
        replaceOrRemove(systemPromptBuilder, SectionName.TOOLS, toolsSection);
        replaceOrRemove(systemPromptBuilder, SectionName.CONTEXT, contextSection);
    }

    public SystemPromptBuilder getSystemPromptBuilder() {
        return systemPromptBuilder;
    }

    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    public static PromptSection buildWorkspaceSection(Workspace workspace, String language) {
        if (workspace == null) {
            return null;
        }
        String lang = "en".equals(language) ? "en" : "cn";
        Path root = workspace.root().toAbsolutePath().normalize();
        String header = "en".equals(lang) ? "# Workspace\n" : "# 工作空间\n";
        String directoryLine = "en".equals(lang)
                ? "Your working directory is: `" + root + "`\n"
                : "你的工作目录是：`" + root + "`\n";

        StringBuilder body = new StringBuilder(header).append("\n").append(directoryLine);
        if (Files.exists(root.resolve("README.md"))) {
            body.append("\n");
            body.append("en".equals(lang) ? "README.md is present in the workspace.\n" : "工作区中存在 README.md。\n");
        }
        return new PromptSection(SectionName.WORKSPACE, Map.of(lang, body.toString()), 75);
    }

    public static String buildToolsContent(AbilityManager manager, String language) {
        if (manager == null) {
            return null;
        }
        List<ToolCard> tools = new ArrayList<>();
        for (Object ability : manager.list()) {
            if (ability instanceof ToolCard toolCard
                    && toolCard.getName() != null
                    && !toolCard.getName().isBlank()
                    && toolCard.getDescription() != null
                    && !toolCard.getDescription().isBlank()) {
                tools.add(toolCard);
            }
        }
        if (tools.isEmpty()) {
            return null;
        }

        boolean english = "en".equals(language);
        tools.sort((left, right) -> {
            String l = "paid_search".equals(left.getName()) ? "" : left.getName();
            String r = "paid_search".equals(right.getName()) ? "" : right.getName();
            return l.compareTo(r);
        });

        StringBuilder content = new StringBuilder();
        content.append(english ? "# Available Tools\n" : "# 可用工具\n");
        for (ToolCard tool : tools) {
            String name = tool.getName();
            if ("cron_list_jobs".equals(name) || "t2".equals(name)) {
                continue;
            }
            String description = switch (name) {
                case "free_search" -> english ? "Free web search" : "免费搜索（DuckDuckGo 等）";
                case "paid_search" -> english ? "Paid web search (preferred when configured)" : "付费搜索（已配置时优先）";
                case "read_file", "write_file", "edit_file" ->
                        english ? "Read, write, and edit files" : "文件读写编辑";
                case "bash" -> english ? "Run shell commands" : "执行 Shell 命令";
                case "code" -> english ? "Run Python or JavaScript code" : "执行 Python 或 JavaScript 代码";
                case "list_skill" -> english ? "List available skills" : "列出可用技能";
                default -> tool.getDescription();
            };
            if (description == null || description.isBlank()) {
                continue;
            }
            content.append("- ").append(name).append(": ").append(description).append("\n");
        }
        if (content.indexOf("- bash:") >= 0) {
            content.append(english ? "\n## bash Guidelines\n" : "\n## bash 使用原则\n");
            content.append(english
                    ? "Do not replace dedicated file tools with bash.\n"
                    : "不要用 bash 替代 `glob` / `grep` / `read_file` / `edit_file` / `write_file`。\n");
        }
        if (content.indexOf("- task_tool:") >= 0) {
            content.append(english ? "\n## task_tool Guidelines\n" : "\n## task_tool 使用原则\n");
        }
        return content.toString().endsWith("\n") ? content.toString() : content + "\n";
    }

    public static PromptSection buildToolsSection(String toolsContent, String language) {
        if (toolsContent == null || toolsContent.isBlank()) {
            return null;
        }
        String lang = "en".equals(language) ? "en" : "cn";
        return new PromptSection(SectionName.TOOLS, Map.of(lang, toolsContent), 40);
    }

    public static PromptSection buildContextSection(
            Workspace workspace,
            String language,
            String toolsContent,
            ZoneId zoneId
    ) {
        if (workspace == null) {
            return null;
        }
        String lang = "en".equals(language) ? "en" : "cn";
        Path root = workspace.root().toAbsolutePath().normalize();
        List<String> blocks = new ArrayList<>();

        appendIfExists(blocks, root.resolve("AGENT.md"),
                "en".equals(lang) ? "## AGENT.md - Agent Configuration" : "## AGENT.md - 智能体配置");
        appendIfExists(blocks, root.resolve("SOUL.md"), "## SOUL.md");
        Path dailyMemoryPath = root.resolve("memory").resolve("daily_memory")
                .resolve(LocalDate.now(zoneId != null ? zoneId : ZoneId.of("Asia/Shanghai")) + ".md");
        appendIfExists(blocks, dailyMemoryPath, "## daily_memory/");

        if (toolsContent != null && !toolsContent.isBlank()) {
            blocks.add(toolsContent.stripTrailing());
        }
        if (blocks.isEmpty()) {
            return null;
        }

        String intro = "en".equals(lang)
                ? "The following files are already loaded into context.\n\n"
                : "以下文件已加载到上下文中，无需再次读取。\n\n";
        return new PromptSection(
                SectionName.CONTEXT,
                Map.of(lang, intro + String.join("\n\n", blocks) + "\n"),
                80
        );
    }

    private static void appendIfExists(List<String> blocks, Path path, String header) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            blocks.add(header + "\n" + Files.readString(path).strip());
        } catch (IOException e) {
            LOG.warn("[ContextAssembleRail] Failed to read {}", path, e);
        }
    }

    private static void replaceOrRemove(SystemPromptBuilder builder, String name, PromptSection section) {
        if (section == null) {
            builder.removeSection(name);
        } else {
            builder.addSection(section);
        }
    }

    private static SystemPromptBuilder resolveBuilder(Object agent) {
        Object value = invokeNoArg(agent, "getSystemPromptBuilder");
        if (value instanceof SystemPromptBuilder builder) {
            return builder;
        }
        Object field = readField(agent, "systemPromptBuilder", "system_prompt_builder");
        return field instanceof SystemPromptBuilder builder ? builder : null;
    }

    private static AbilityManager resolveAbilityManager(Object agent) {
        Object value = invokeNoArg(agent, "getAbilityManager");
        if (value instanceof AbilityManager manager) {
            return manager;
        }
        Object field = readField(agent, "abilityManager", "ability_manager");
        return field instanceof AbilityManager manager ? manager : null;
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object readField(Object target, String... names) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            for (String name : names) {
                try {
                    var field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (NoSuchFieldException ignored) {
                    // try next
                } catch (IllegalAccessException e) {
                    return null;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }
}
