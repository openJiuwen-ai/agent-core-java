/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.subagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.schema.DeepAgentState;
import com.openjiuwen.harness.workspace.Workspace;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tool allowlist and per-turn reminder for the verification agent.
 *
 * <p>Mirrors Python's {@code VerificationRail} in
 * {@code openjiuwen/harness/rails/subagent/verification_rail.py}.</p>
 */
public class VerificationRail extends DeepAgentRail {

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
    private static final String REMINDER_EN = """
            === VERIFICATION AGENT ACTIVE CONSTRAINTS ===
            1. You cannot create, modify, or delete project files. Use temporary paths only for ephemeral scripts.
            2. Every check must include the command run and terminal output.
            3. End final output with exactly VERDICT: PASS, VERDICT: FAIL, or VERDICT: PARTIAL.
            4. Reading code is not verification; run commands and show actual output.
            """;
    private static final String REMINDER_CN = REMINDER_EN;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Set<String> allowedTools;
    private DeepAgent agent;
    private SystemPromptBuilder systemPromptBuilder;

    public VerificationRail() {
        this(VERIFICATION_ALLOWED_TOOLS);
    }

    public VerificationRail(Set<String> allowedTools) {
        setPriority(90);
        this.allowedTools = allowedTools == null ? VERIFICATION_ALLOWED_TOOLS : Set.copyOf(allowedTools);
    }

    @Override
    public void init(DeepAgent agent) {
        super.init(agent);
        this.agent = agent;
        String language = agent == null || agent.deepConfig() == null ? "cn" : agent.deepConfig().getLanguage();
        this.systemPromptBuilder = new SystemPromptBuilder(language);
    }

    @Override
    public void uninit(DeepAgent agent) {
        if (systemPromptBuilder != null) {
            systemPromptBuilder.removeSection(REMINDER_SECTION_NAME);
        }
        this.agent = null;
        this.systemPromptBuilder = null;
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        if (ctx == null || systemPromptBuilder == null) {
            return;
        }
        DeepAgent effectiveAgent = ctx.getAgent() != null ? ctx.getAgent() : agent;
        if (!isTaskLoopEnabled(effectiveAgent)) {
            return;
        }
        if ("plan".equals(String.valueOf(ctx.get("mode"))) || isPlanMode(effectiveAgent, ctx.get("session"))) {
            return;
        }
        PromptSection section = new PromptSection(
                REMINDER_SECTION_NAME,
                Map.of("en", REMINDER_EN, "cn", REMINDER_CN),
                REMINDER_PRIORITY
        );
        systemPromptBuilder.removeSection(REMINDER_SECTION_NAME);
        systemPromptBuilder.addSection(section);
        ctx.put("verification_reminder_section", section);
    }

    @Override
    public void beforeToolCall(CallbackContext ctx) {
        Object skip = ctx.get("_skip_tool");
        if (Boolean.TRUE.equals(skip)) {
            return;
        }
        String toolName = String.valueOf(ctx.getValues().getOrDefault("tool_name", ""));
        if (toolName.startsWith("mcp__")) {
            return;
        }
        if (!allowedTools.contains(toolName)) {
            rejectTool(ctx, "[VerificationAgent] Tool '" + toolName + "' is not available to the verification agent.");
            return;
        }
        String pathArgKey = PATH_TOOL_ARG.get(toolName);
        if (pathArgKey != null) {
            enforceWorkspacePath(ctx, toolName, pathArgKey);
        }
    }

    public Set<String> getAllowedTools() {
        return Set.copyOf(allowedTools);
    }

    public SystemPromptBuilder getSystemPromptBuilder() {
        return systemPromptBuilder;
    }

    void setSystemPromptBuilder(SystemPromptBuilder systemPromptBuilder) {
        this.systemPromptBuilder = systemPromptBuilder;
    }

    private void enforceWorkspacePath(CallbackContext ctx, String toolName, String pathArgKey) {
        Path root = workspaceRoot();
        if (root == null) {
            return;
        }
        Map<String, Object> args = parseToolArgs(ctx.get("tool_args"));
        Object rawPath = args.get(pathArgKey);
        if (rawPath == null || String.valueOf(rawPath).isBlank()) {
            return;
        }
        try {
            Path resolved = Path.of(String.valueOf(rawPath)).toAbsolutePath().normalize();
            if (!(resolved.equals(root) || resolved.startsWith(root))) {
                rejectTool(
                        ctx,
                        "[VerificationAgent] Path '" + rawPath + "' is outside the workspace scope (workspace root: '"
                                + root + "')."
                );
            }
        } catch (Exception ignored) {
            // Unresolvable paths fall through to the tool layer, matching Python behavior.
        }
    }

    private void rejectTool(CallbackContext ctx, String errorMessage) {
        ctx.put("_skip_tool", true);
        ctx.put("tool_result", Map.of("error", errorMessage));
        ctx.reject(errorMessage);
    }

    private Path workspaceRoot() {
        Object workspace = getWorkspace();
        if (workspace == null) {
            return null;
        }
        if (workspace instanceof Workspace workspaceValue) {
            return Path.of(workspaceValue.getRootPath()).toAbsolutePath().normalize();
        }
        if (workspace instanceof Path path) {
            return path.toAbsolutePath().normalize();
        }
        return Path.of(String.valueOf(workspace)).toAbsolutePath().normalize();
    }

    private static Map<String, Object> parseToolArgs(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
        } else if (value instanceof String text && !text.isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = OBJECT_MAPPER.readValue(text, Map.class);
                result.putAll(parsed);
            } catch (Exception ignored) {
                return Map.of();
            }
        }
        return result;
    }

    private static boolean isTaskLoopEnabled(DeepAgent candidateAgent) {
        return candidateAgent != null
                && candidateAgent.deepConfig() != null
                && candidateAgent.deepConfig().isEnableTaskLoop();
    }

    private static boolean isPlanMode(DeepAgent candidateAgent, Object session) {
        if (candidateAgent == null || session == null) {
            return false;
        }
        try {
            DeepAgentState state = candidateAgent.loadState(session);
            return state != null
                    && state.getPlanMode() != null
                    && "plan".equals(state.getPlanMode().getMode());
        } catch (Exception ignored) {
            return false;
        }
    }
}
