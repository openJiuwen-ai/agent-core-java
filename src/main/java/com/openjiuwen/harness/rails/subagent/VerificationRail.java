/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.subagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.rails.DeepAgentRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Rail for subagent verification — validates subagent results.
 * <p>
 * Mirrors Python's {@code VerificationRail} in
 * {@code openjiuwen.harness.rails.subagent.verification_rail}.
 */
public class VerificationRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(VerificationRail.class);

    public static final Set<String> VERIFICATION_ALLOWED_TOOLS = Set.of(
            "read_file",
            "bash",
            "grep",
            "glob",
            "list_files",
            "web_search",
            "web_fetch",
            "todo_create",
            "todo_list",
            "todo_modify",
            "skill_tool",
            "tool_search"
    );

    private static final Map<String, String> PATH_TOOL_ARG = Map.of(
            "list_files", "path",
            "read_file", "file_path",
            "glob", "path",
            "grep", "path"
    );

    private static final String REMINDER_SECTION_NAME = "verification_reminder";
    private static final int REMINDER_PRIORITY = 95;
    private static final String REMINDER_EN = "=== VERIFICATION AGENT - ACTIVE CONSTRAINTS ===\n"
            + "1. You CANNOT create, modify, or delete project files. Use /tmp only for ephemeral test scripts.\n"
            + "2. Every check MUST include a 'Command run' block with verbatim terminal output. "
            + "A check without a command block is a SKIP, not a PASS.\n"
            + "3. You MUST end your final response with exactly one of:\n"
            + "   VERDICT: PASS\n"
            + "   VERDICT: FAIL\n"
            + "   VERDICT: PARTIAL\n"
            + "   No markdown, no punctuation after the verdict word, no variation.\n"
            + "4. Reading code is NOT verification. Run commands and show actual output.";
    private static final String REMINDER_CN = "=== 验证代理 -- 当前约束 ===\n"
            + "1. 你不能创建、修改或删除项目文件。/tmp 仅可用于临时测试脚本。\n"
            + "2. 每项检查必须包含'执行命令'块，并逐字粘贴终端输出。没有命令块的检查视为跳过，而非 PASS。\n"
            + "3. 你必须以以下之一结束最终回复：\n"
            + "   VERDICT: PASS\n"
            + "   VERDICT: FAIL\n"
            + "   VERDICT: PARTIAL\n"
            + "   不加 Markdown，判决词后不加标点，不得有任何格式变体。\n"
            + "4. 阅读代码不等于验证。运行命令并展示实际输出。";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Set<String> allowedTools;
    private Object agent;
    private SystemPromptBuilder systemPromptBuilder;
    private Object workspaceRootOverride;

    public VerificationRail() {
        this(VERIFICATION_ALLOWED_TOOLS);
    }

    public VerificationRail(Set<String> allowedTools) {
        super();
        this.allowedTools = allowedTools != null ? Set.copyOf(allowedTools) : VERIFICATION_ALLOWED_TOOLS;
        setPriority(90);
    }

    @Override
    public void init(Object agent) {
        this.agent = agent;
        this.systemPromptBuilder = resolveSystemPromptBuilder(agent);
        LOG.info("[VerificationRail] Initialized");
    }

    @Override
    public void uninit(Object agent) {
        this.agent = null;
        this.systemPromptBuilder = null;
        LOG.info("[VerificationRail] Uninitialized");
    }

    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        if (systemPromptBuilder == null) {
            return;
        }
        Object effectiveAgent = ctx != null && ctx.getAgent() != null ? ctx.getAgent() : agent;
        if (!isTaskLoopEnabled(effectiveAgent)) {
            return;
        }
        if (isPlanMode(effectiveAgent, ctx)) {
            return;
        }
        systemPromptBuilder.removeSection(REMINDER_SECTION_NAME);
        systemPromptBuilder.addSection(new PromptSection(
                REMINDER_SECTION_NAME,
                Map.of("en", REMINDER_EN, "cn", REMINDER_CN),
                REMINDER_PRIORITY));
    }

    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        if (ctx == null || Boolean.TRUE.equals(ctx.getExtra().get("_skip_tool"))) {
            return;
        }
        if (!(ctx.getInputs() instanceof ToolCallInputs inputs)) {
            return;
        }
        String toolName = resolveToolName(inputs);
        if (toolName == null || toolName.isBlank()) {
            return;
        }
        if (toolName.startsWith("mcp__")) {
            return;
        }
        if (!allowedTools.contains(toolName)) {
            rejectTool(ctx, "[VerificationAgent] Tool '" + toolName
                    + "' is not available to the verification agent. Permitted tools: "
                    + String.join(", ", allowedTools.stream().sorted().toList()) + ".");
            return;
        }
        String pathKey = PATH_TOOL_ARG.get(toolName);
        if (pathKey == null) {
            return;
        }
        Path root = resolveWorkspaceRoot();
        if (root == null) {
            return;
        }
        Object rawPath = parseToolArgs(inputs.getToolArgs()).get(pathKey);
        if (rawPath == null || String.valueOf(rawPath).isBlank()) {
            return;
        }
        try {
            Path resolved = Path.of(String.valueOf(rawPath)).toAbsolutePath().normalize();
            Path normalizedRoot = root.toAbsolutePath().normalize();
            if (!resolved.equals(normalizedRoot) && !resolved.startsWith(normalizedRoot)) {
                rejectTool(ctx, "[VerificationAgent] Path '" + rawPath + "' is outside the workspace scope "
                        + "(workspace root: '" + normalizedRoot + "'). Only paths within the workspace are accessible.");
            }
        } catch (Exception ignored) {
            // Unresolvable paths are left to the underlying tool layer.
        }
    }

    public Set<String> getAllowedTools() {
        return allowedTools;
    }

    public SystemPromptBuilder getSystemPromptBuilder() {
        return systemPromptBuilder;
    }

    public void setSystemPromptBuilder(SystemPromptBuilder systemPromptBuilder) {
        this.systemPromptBuilder = systemPromptBuilder;
    }

    public void setWorkspaceRoot(Object workspaceRoot) {
        this.workspaceRootOverride = workspaceRoot;
    }

    public void rejectTool(AgentCallbackContext ctx, String errorMsg) {
        if (ctx == null || !(ctx.getInputs() instanceof ToolCallInputs inputs)) {
            return;
        }
        ToolCall toolCall = inputs.getToolCall();
        String toolCallId = toolCall != null && toolCall.getId() != null ? toolCall.getId() : "";
        ctx.getExtra().put("_skip_tool", true);
        inputs.setToolResult(Map.of("error", errorMsg));
        inputs.setToolMsg(new ToolMessage(errorMsg, toolCallId));
    }

    private String resolveToolName(ToolCallInputs inputs) {
        if (inputs.getToolName() != null && !inputs.getToolName().isBlank()) {
            return inputs.getToolName();
        }
        ToolCall toolCall = inputs.getToolCall();
        return toolCall != null ? toolCall.getName() : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseToolArgs(Object rawArgs) {
        if (rawArgs instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        if (rawArgs instanceof String text && !text.isBlank()) {
            try {
                return MAPPER.readValue(text, Map.class);
            } catch (Exception ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private Path resolveWorkspaceRoot() {
        Object root = workspaceRootOverride;
        if (root == null && workspace != null) {
            root = workspace.getRootPath();
        }
        if (root == null) {
            return null;
        }
        if (root instanceof Path path) {
            return path;
        }
        return Path.of(String.valueOf(root));
    }

    private boolean isTaskLoopEnabled(Object candidateAgent) {
        Object config = resolveConfig(candidateAgent);
        Object value = readObject(config, "enableTaskLoop");
        if (value == null) {
            value = readObject(config, "enable_task_loop");
        }
        if (value == null) {
            return false;
        }
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private boolean isPlanMode(Object candidateAgent, AgentCallbackContext ctx) {
        if (candidateAgent == null || ctx == null || ctx.getSession() == null) {
            return false;
        }
        try {
            Method loadState = candidateAgent.getClass().getMethod("loadState", com.openjiuwen.core.session.Session.class);
            loadState.setAccessible(true);
            Object state = loadState.invoke(candidateAgent, ctx.getSession());
            Object planMode = readObject(state, "planMode");
            Object mode = readObject(planMode, "mode");
            return "plan".equals(String.valueOf(mode));
        } catch (Exception ignored) {
            return false;
        }
    }

    private Object resolveConfig(Object candidateAgent) {
        Object config = readObject(candidateAgent, "deepConfig");
        if (config != null) {
            return config;
        }
        config = readObject(candidateAgent, "_deep_config");
        if (config != null) {
            return config;
        }
        return readObject(candidateAgent, "config");
    }

    private SystemPromptBuilder resolveSystemPromptBuilder(Object candidateAgent) {
        Object builder = readObject(candidateAgent, "systemPromptBuilder");
        if (builder instanceof SystemPromptBuilder typed) {
            return typed;
        }
        builder = readObject(candidateAgent, "system_prompt_builder");
        return builder instanceof SystemPromptBuilder typed ? typed : null;
    }

    private static Object readObject(Object target, String name) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            return map.get(name);
        }
        String getter = "get" + name.substring(0, 1).toUpperCase() + name.substring(1);
        try {
            Method method = target.getClass().getMethod(getter);
            return method.invoke(target);
        } catch (Exception ignored) {
        }
        String booleanGetter = "is" + name.substring(0, 1).toUpperCase() + name.substring(1);
        try {
            Method method = target.getClass().getMethod(booleanGetter);
            return method.invoke(target);
        } catch (Exception ignored) {
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Exception exc) {
                return null;
            }
        }
        return null;
    }
}
