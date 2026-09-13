/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Public class SecurityRail used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class SecurityRail extends DeepAgentRail {
    private static final Set<String> DEFAULT_WRITE_TOOLS = Set.of("write_file", "edit_file", "todo_create",
            "todo_modify", "write_memory", "edit_memory", "browser_custom_action");

    /**
     * Set.of.
     * 
     * @param add" add"
     * @param commit" commit"
     * @param push" push"
     * @param install" install"
     * @param install" install"
     * @param install" install"
     * @since 0.1.7
     */
    private static final Set<String> DEFAULT_WRITE_COMMAND_TOKENS =
        Set.of(">", ">>", "rm", "rmdir", "mv", "cp", "mkdir", "touch", "chmod", "chown", "git add", "git commit",
                "git push", "npm install", "pip install", "mvn install");

    private final boolean isReadOnly;
    private final Set<String> writeTools;
    private final Set<String> writeCommandTokens;

    /**
     * SecurityRail.
     * 
     * @since 0.1.7
     */
    public SecurityRail() {
        this(false, DEFAULT_WRITE_TOOLS, DEFAULT_WRITE_COMMAND_TOKENS);
    }

    /**
     * SecurityRail.
     * 
     * @param isReadOnly isReadOnly
     * @since 0.1.7
     */
    public SecurityRail(boolean isReadOnly) {
        this(isReadOnly, DEFAULT_WRITE_TOOLS, DEFAULT_WRITE_COMMAND_TOKENS);
    }

    /**
     * SecurityRail.
     * 
     * @param isReadOnly isReadOnly
     * @param writeTools writeTools
     * @param writeCommandTokens writeCommandTokens
     * @since 0.1.7
     */
    public SecurityRail(boolean isReadOnly, Set<String> writeTools, Set<String> writeCommandTokens) {
        this.isReadOnly = isReadOnly;
        this.writeTools = writeTools == null || writeTools.isEmpty() ? DEFAULT_WRITE_TOOLS : Set.copyOf(writeTools);
        this.writeCommandTokens = writeCommandTokens == null || writeCommandTokens.isEmpty()
                ? DEFAULT_WRITE_COMMAND_TOKENS
                : Set.copyOf(writeCommandTokens);
    }

    /**
     * priority.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int priority() {
        return 80;
    }

    /**
     * allowsDestructiveAction.
     * 
     * @param isConfirmed isConfirmed
     * @return the result
     * @since 0.1.7
     */
    public boolean allowsDestructiveAction(boolean isConfirmed) {
        return isConfirmed;
    }

    /**
     * validateReadOnlyToolCall.
     * 
     * @param toolName toolName
     * @param toolArgs toolArgs
     * @return the result
     * @since 0.1.7
     */
    public ToolOutput validateReadOnlyToolCall(String toolName, Map<String, Object> toolArgs) {
        if (!isReadOnly) {
            return ToolOutput.builder().success(true).build();
        }
        if (toolName != null && writeTools.contains(toolName)) {
            return ToolOutput.builder().success(false)
                    .error("[SecurityRail] read-only agent cannot call write tool: " + toolName).build();
        }
        if (isShellTool(toolName) && containsWriteCommand(toolArgs)) {
            return ToolOutput.builder().success(false)
                    .error("[SecurityRail] read-only agent cannot run write shell command").build();
        }
        return ToolOutput.builder().success(true).build();
    }

    /**
     * beforeToolCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        if (!(ctx.getInputs() instanceof ToolCallInputs inputs)) {
            return;
        }
        Map<String, Object> args = normalizeArgs(inputs.getToolArgs());
        ToolOutput result = validateReadOnlyToolCall(inputs.getToolName(), args);
        if (result.isSuccess()) {
            return;
        }
        ctx.getExtra().put("_skip_tool", Boolean.TRUE);
        inputs.setToolResult(result);
        inputs.setToolMsg(ToolMessage.builder().content(result.getError())
                .toolCallId(inputs.getToolCall() != null ? inputs.getToolCall().getId() : "").build());
    }

    /**
     * isReadOnly.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isReadOnly() {
        return isReadOnly;
    }

    /**
     * containsWriteCommand.
     * 
     * @param toolArgs toolArgs
     * @return the result
     * @since 0.1.7
     */
    private boolean containsWriteCommand(Map<String, Object> toolArgs) {
        String command = commandValue(toolArgs);
        if (command == null || command.isBlank()) {
            return false;
        }
        String normalized = command.toLowerCase(Locale.ROOT);
        return writeCommandTokens.stream().anyMatch(token -> normalized.contains(token.toLowerCase(Locale.ROOT)));
    }

    /**
     * isShellTool.
     * 
     * @param toolName toolName
     * @return the result
     * @since 0.1.7
     */
    private static boolean isShellTool(String toolName) {
        return "bash".equals(toolName) || "powershell".equals(toolName);
    }

    /**
     * commandValue.
     * 
     * @param toolArgs toolArgs
     * @return the result
     * @since 0.1.7
     */
    private static String commandValue(Map<String, Object> toolArgs) {
        if (toolArgs == null) {
            return null;
        }
        Object command = toolArgs.get("command");
        if (command == null) {
            command = toolArgs.get("cmd");
        }
        return command != null ? String.valueOf(command) : null;
    }

    @SuppressWarnings("unchecked")
    /**
     * normalizeArgs.
     * 
     * @param args args
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> normalizeArgs(Object args) {
        if (args instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }
}
