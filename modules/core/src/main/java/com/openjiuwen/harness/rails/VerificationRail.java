/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.schema.AgentMode;
import com.openjiuwen.harness.tools.ToolOutput;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Public class VerificationRail used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class VerificationRail extends DeepAgentRail {
    /**
     * REMINDER_SECTION.
     * 
     * @since 0.1.7
     */
    public static final String REMINDER_SECTION = "verification_reminder";

    /**
     * REMINDER_PRIORITY.
     * 
     * @since 0.1.7
     */
    public static final int REMINDER_PRIORITY = 95;

    /**
     * DEFAULT_ALLOWED_TOOLS.
     * 
     * @since 0.1.7
     */
    public static final Set<String> DEFAULT_ALLOWED_TOOLS = Set.of("read_file", "bash", "grep", "glob", "list_files",
            "web_search", "web_fetch", "todo_create", "todo_list", "todo_modify", "skill_tool", "tool_search");

    /**
     * ObjectMapper.
     * 
     * @since 0.1.7
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Map.of.
     * 
     * @since 0.1.7
     */
    private static final Map<String, String> PATH_TOOL_ARGS =
        Map.of("list_files", "path", "read_file", "file_path", "glob", "path", "grep", "path");
    private static final String REMINDER_CONTENT = """
        === VERIFICATION AGENT - ACTIVE CONSTRAINTS ===
        1. You CANNOT create, modify, or delete project files. Use /tmp only for ephemeral test scripts.
        2. Every check MUST include a 'Command run' block with verbatim terminal output. A check without a \
        command block is a SKIP, not a PASS.
        3. You MUST end your final response with exactly one of:
           VERDICT: PASS
           VERDICT: FAIL
           VERDICT: PARTIAL
           No markdown, no punctuation after the verdict word, no variation.
        4. Reading code is NOT verification. Run commands and show actual output.
        """;

    private final Set<String> allowedTools;
    private DeepAgent owner;

    /**
     * VerificationRail.
     * 
     * @since 0.1.7
     */
    public VerificationRail() {
        this(DEFAULT_ALLOWED_TOOLS);
    }

    /**
     * VerificationRail.
     * 
     * @param allowedTools allowedTools
     * @since 0.1.7
     */
    public VerificationRail(Set<String> allowedTools) {
        this.allowedTools = new LinkedHashSet<>(allowedTools == null ? DEFAULT_ALLOWED_TOOLS : allowedTools);
    }

    /**
     * priority.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int priority() {
        return 90;
    }

    /**
     * init.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    @Override
    public void init(Object agent) {
        if (agent instanceof DeepAgent deepAgent) {
            owner = deepAgent;
        }
    }

    /**
     * uninit.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    @Override
    public void uninit(Object agent) {
        if (owner != null) {
            owner.getAgent().getPromptBuilder().removeSection(REMINDER_SECTION);
        }
        owner = null;
    }

    /**
     * beforeModelCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        if (owner != null && owner.getConfig().isEnableTaskLoop() && owner.getCurrentMode() != AgentMode.PLAN) {
            owner.getAgent().addPromptBuilderSection(REMINDER_SECTION, REMINDER_CONTENT, REMINDER_PRIORITY);
            if (ctx.getInputs() instanceof ModelCallInputs inputs) {
                injectReminderMessage(inputs);
            }
        }
    }

    /**
     * beforeToolCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        if (!(ctx.getInputs() instanceof ToolCallInputs inputs)
                || Boolean.TRUE.equals(ctx.getExtra().get("_skip_tool"))) {
            return;
        }
        String toolName = inputs.getToolName();
        if (!allowsTool(toolName)) {
            rejectTool(ctx, inputs,
                    "[VerificationAgent] Tool '" + toolName
                            + "' is not available to the verification agent. Permitted tools: "
                            + String.join(", ", allowedTools.stream().sorted().toList()) + ".");
            return;
        }
        String pathKey = PATH_TOOL_ARGS.get(toolName);
        if (pathKey != null && owner != null) {
            String rawPath = pathValue(inputs.getToolArgs(), pathKey);
            if (rawPath != null && !isWithinWorkspace(rawPath)) {
                rejectTool(ctx, inputs, "[VerificationAgent] Path '" + rawPath
                        + "' is outside the workspace scope (workspace root: '" + owner.getWorkspace().root() + "').");
            }
        }
    }

    /**
     * allowsTool.
     * 
     * @param toolName toolName
     * @return the result
     * @since 0.1.7
     */
    public boolean allowsTool(String toolName) {
        return toolName != null && (toolName.startsWith("mcp__") || allowedTools.contains(toolName));
    }

    /**
     * getAllowedTools.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Set<String> getAllowedTools() {
        return Set.copyOf(allowedTools);
    }

    /**
     * hasReminderPromptSection.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean hasReminderPromptSection() {
        return owner != null && owner.getAgent().getPromptBuilder().hasSection(REMINDER_SECTION);
    }

    /**
     * reminderContent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String reminderContent() {
        return REMINDER_CONTENT.trim();
    }

    /**
     * injectReminderMessage.
     * 
     * @param inputs inputs
     * @since 0.1.7
     */
    private void injectReminderMessage(ModelCallInputs inputs) {
        List<Object> messages =
            inputs.getMessages() != null ? new ArrayList<>(inputs.getMessages()) : new ArrayList<>();
        for (Object message : messages) {
            if (message instanceof BaseMessage baseMessage && "system".equalsIgnoreCase(baseMessage.getRole())
                    && String.valueOf(baseMessage.getContent()).contains("VERIFICATION AGENT - ACTIVE CONSTRAINTS")) {
                return;
            }
        }
        messages.add(0, new SystemMessage(reminderContent()));
        inputs.setMessages(messages);
    }

    /**
     * rejectTool.
     * 
     * @param ctx ctx
     * @param inputs inputs
     * @param error error
     * @since 0.1.7
     */
    private void rejectTool(AgentCallbackContext ctx, ToolCallInputs inputs, String error) {
        ctx.getExtra().put("_skip_tool", Boolean.TRUE);
        inputs.setToolResult(ToolOutput.builder().success(false).error(error).build());
        inputs.setToolMsg(ToolMessage.builder().content(error)
                .toolCallId(inputs.getToolCall() != null ? inputs.getToolCall().getId() : "").build());
    }

    @SuppressWarnings("unchecked")
    /**
     * pathValue.
     * 
     * @param args args
     * @param pathKey pathKey
     * @return the result
     * @since 0.1.7
     */
    private static String pathValue(Object args, String pathKey) {
        if (args instanceof Map<?, ?> map) {
            Object value = map.get(pathKey);
            return value != null ? String.valueOf(value) : null;
        }
        if (args instanceof String raw && !raw.isBlank()) {
            try {
                Map<String, Object> parsed = MAPPER.readValue(raw, Map.class);
                Object value = parsed.get(pathKey);
                return value != null ? String.valueOf(value) : null;
            } catch (JsonProcessingException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * isWithinWorkspace.
     * 
     * @param rawPath rawPath
     * @return the result
     * @since 0.1.7
     */
    private boolean isWithinWorkspace(String rawPath) {
        try {
            Path isResolved = Path.of(rawPath).toAbsolutePath().normalize();
            Path root = owner.getWorkspace().root().toAbsolutePath().normalize();
            return isResolved.equals(root) || isResolved.startsWith(root);
        } catch (Exception ignored) {
            return true;
        }
    }
}
