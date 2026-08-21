/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.security;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.rails.interrupt.ApproveResult;
import com.openjiuwen.harness.rails.interrupt.BaseInterruptRail;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision;
import com.openjiuwen.harness.rails.interrupt.InterruptResult;
import com.openjiuwen.harness.rails.interrupt.RejectResult;
import com.openjiuwen.harness.security.PermissionCheckResult;
import com.openjiuwen.harness.security.PermissionConfirmResponse;
import com.openjiuwen.harness.security.PermissionConfirmationRequest;
import com.openjiuwen.harness.security.PermissionEngine;
import com.openjiuwen.harness.security.PermissionLevel;
import com.openjiuwen.harness.security.PermissionResult;
import com.openjiuwen.harness.security.ToolPermissionHost;
import com.openjiuwen.harness.security.patterns.PermissionsYamlWriter;
import com.openjiuwen.harness.security.shellast.ShellAst;
import com.openjiuwen.harness.security.shellast.ShellAstParseResult;
import com.openjiuwen.harness.security.shellast.ShellSubcommand;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Permission interrupt rail enforcing ALLOW/DENY/ASK three-state tool guardrails.
 *
 * <p>Mirrors Python {@code openjiuwen.harness.rails.security.tool_security_rail.PermissionInterruptRail}.
 * Unlike the base rail, this rail intercepts <strong>every</strong> tool call (the configured
 * {@code tool_names} are retained only for {@code getToolNames()} display, mirroring Python's
 * "intercept=all_tools" mode) so that tools not listed under {@code tools} still flow through
 * {@code defaults.*}. Each call is normalized (shell aliases collapse to {@code bash}), evaluated
 * by {@link PermissionEngine}, and either approved, rejected with a {@code [PERMISSION_DENIED]}
 * message, or surfaced for confirmation. The confirmation flow supports a hosted callback
 * ({@link ToolPermissionHost#requestPermissionConfirmation}) and the built-in interrupt/resume
 * path, with session-scoped auto-confirm and permanent "always allow" persistence to the agent
 * YAML via {@link PermissionsYamlWriter}.
 *
 * @since 0.1.7
 */
@Getter
public class PermissionInterruptRail extends BaseInterruptRail {
    private static final Logger logger = LoggerFactory.getLogger(PermissionInterruptRail.class);

    private static final Set<String> SHELL_TOOL_ALIASES = Set.of("bash", "mcp_exec_command", "create_terminal");
    private static final String SHELL_COMMAND_KEY = "command";
    private static final String SHELL_CMD_KEY = "cmd";
    private static final String DENIED_PREFIX = "[PERMISSION_DENIED] ";
    private static final String REJECTED_DEFAULT = "[PERMISSION_REJECTED] User rejected the request.";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final PermissionEngine engine;
    private final ToolPermissionHost host;
    private final Map<String, Boolean> sessionAutoConfirm = new ConcurrentHashMap<>();

    /**
     * PermissionInterruptRail.
     *
     * @param engine engine
     * @param host host
     * @since 0.1.7
     */
    public PermissionInterruptRail(PermissionEngine engine, ToolPermissionHost host) {
        super(null);
        this.engine = engine;
        this.host = host;
        @SuppressWarnings("unchecked")
        Map<String, Object> tools = (Map<String, Object>) engine.getConfig().getOrDefault("tools", Map.of());
        addTools(tools.keySet());
    }

    /**
     * Intercept every tool call, bypassing the base rail's tool-name gate so that tools not
     * listed under {@code tools} still resolve through {@code defaults.*}.
     *
     * @param ctx ctx
     * @since 0.1.15
     */
    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        if (!(ctx.getInputs() instanceof ToolCallInputs)) {
            return;
        }
        ToolCallInputs inputs = (ToolCallInputs) ctx.getInputs();
        ToolCall toolCall = inputs.getToolCall();
        String toolCallId = toolCall != null ? toolCall.getId() : "";
        Object userInput = getUserInput(ctx, toolCallId);
        InterruptDecision decision = resolveInterrupt(ctx, toolCall, userInput);
        applyResolvedDecision(ctx, toolCall, decision);
    }

    /**
     * Resolve the permission decision for the current tool invocation.
     *
     * <p>On the first check ({@code userInput == null}) the engine decides ALLOW/DENY/ASK. On
     * resume ({@code userInput != null}) the carried {@link PermissionConfirmResponse} is applied.
     *
     * @param ctx callback context
     * @param toolCall current tool call
     * @param userInput resume input, when present
     * @return rail decision
     * @since 0.1.7
     */
    @Override
    protected InterruptDecision resolveInterrupt(AgentCallbackContext ctx, ToolCall toolCall, Object userInput) {
        String rawName = toolCall != null ? toolCall.getName() : "";
        String toolName = normalizeToolName(rawName);
        Map<String, Object> toolArgs = extractToolArgs(ctx);
        String autoConfirmKey = buildAutoConfirmKey(toolName, toolArgs);

        if (userInput == null) {
            return resolveFirstCheck(toolName, toolArgs, autoConfirmKey);
        }
        PermissionConfirmResponse payload = parseConfirmPayload(userInput);
        if (payload == null) {
            return interrupt(buildInterruptRequest(toolName, toolArgs, autoConfirmKey));
        }
        return handleConfirmResponse(payload, toolName, toolArgs, autoConfirmKey);
    }

    private InterruptDecision resolveFirstCheck(String toolName, Map<String, Object> toolArgs,
                                                 String autoConfirmKey) {
        PermissionCheckResult result = engine.checkPermission(toolName, toolArgs);
        if (result.getPermission() == PermissionLevel.ALLOW) {
            return approve();
        }
        if (result.getPermission() == PermissionLevel.DENY) {
            String rule = result.getMatchedRule();
            String detail = (rule == null || rule.isEmpty()) ? "Operation not allowed" : rule;
            return reject(DENIED_PREFIX + detail);
        }
        if (isAutoConfirmed(autoConfirmKey)) {
            logger.info("[PermissionEngine] permission.auto_confirm.hit tool={} key={}", toolName, autoConfirmKey);
            return approve();
        }
        PermissionConfirmResponse response = host.requestPermissionConfirmation(
                PermissionConfirmationRequest.builder()
                        .toolName(toolName)
                        .toolArgs(toolArgs)
                        .result(toPermissionResult(result))
                        .autoConfirmKey(autoConfirmKey)
                        .build());
        if (response != null) {
            return handleConfirmResponse(response, toolName, toolArgs, autoConfirmKey);
        }
        return interrupt(buildInterruptRequest(toolName, toolArgs, autoConfirmKey));
    }

    private InterruptDecision handleConfirmResponse(PermissionConfirmResponse response, String toolName,
                                                     Map<String, Object> toolArgs, String autoConfirmKey) {
        boolean isPersisted = false;
        if (response.isApproved() && response.isAutoConfirm() && response.isPersistAllow()) {
            isPersisted = persistAllowAlways(toolName, toolArgs);
            logger.info("[PermissionEngine] permission.persist.result tool={} persisted={} persist_allow={}",
                    toolName, isPersisted, response.isPersistAllow());
        }
        if (shouldStoreSessionAutoConfirm(response.isApproved(), response.isAutoConfirm(), autoConfirmKey, isPersisted)) {
            sessionAutoConfirm.put(autoConfirmKey, Boolean.TRUE);
            logger.info("[PermissionEngine] permission.auto_confirm.store key={}", autoConfirmKey);
        }
        if (response.isApproved()) {
            return approve();
        }
        String feedback = response.getFeedback();
        return reject((feedback == null || feedback.isEmpty()) ? REJECTED_DEFAULT : feedback);
    }

    private boolean persistAllowAlways(String toolName, Map<String, Object> toolArgs) {
        Map<String, Object> baseCfg = host.getPermissionsSnapshot();
        if (baseCfg == null || baseCfg.isEmpty()) {
            baseCfg = new LinkedHashMap<>(engine.getConfig());
        } else {
            baseCfg = new LinkedHashMap<>(baseCfg);
        }
        Map<String, Object> merged = PermissionsYamlWriter.mergeAllowRule(baseCfg, toolName, toolArgs);
        boolean isPersisted = host.persistAllowRule(merged);
        if (isPersisted) {
            refreshEngineConfig(merged);
        } else {
            logger.warn("[PermissionEngine] permission.persist.host_failed tool={} rollback_memory=true", toolName);
        }
        return isPersisted;
    }

    private void refreshEngineConfig(Map<String, Object> snapshot) {
        if (snapshot == null) {
            return;
        }
        Map<String, Object> cfg = engine.getConfig();
        try {
            cfg.clear();
            cfg.putAll(snapshot);
        } catch (UnsupportedOperationException ex) {
            logger.warn("[PermissionEngine] permission.rail.config_refresh_skipped reason=immutable_config");
        }
    }

    private String normalizeToolName(String toolName) {
        if (toolName == null) {
            return "";
        }
        if (SHELL_TOOL_ALIASES.contains(toolName)) {
            return "bash";
        }
        return toolName;
    }

    private String buildAutoConfirmKey(String toolName, Map<String, Object> toolArgs) {
        if (toolName == null || toolName.isEmpty()) {
            return "";
        }
        if (SHELL_TOOL_ALIASES.contains(toolName)) {
            return buildShellAutoConfirmKey(toolName, commandText(toolArgs));
        }
        return toolName;
    }

    private String buildShellAutoConfirmKey(String toolName, String command) {
        if (command == null || command.isBlank()) {
            return "";
        }
        ShellAstParseResult parse = ShellAst.parse(command);
        if (!"simple".equals(parse.getKind())) {
            return "";
        }
        if (parse.getFlags() != null && parse.getFlags().hasRiskyStructure()) {
            return "";
        }
        List<ShellSubcommand> subcommands = parse.getSubcommands();
        if (subcommands == null || subcommands.size() != 1) {
            return "";
        }
        ShellSubcommand first = subcommands.get(0);
        String text = first != null ? first.getText() : null;
        if (text == null || text.isBlank()) {
            return "";
        }
        return toolName + ":" + text.strip();
    }

    private boolean isAutoConfirmed(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        return sessionAutoConfirm.getOrDefault(key, Boolean.FALSE);
    }

    private static boolean shouldStoreSessionAutoConfirm(boolean isApproved, boolean isAutoConfirm,
                                                          String autoConfirmKey, boolean isPersisted) {
        return isApproved && isAutoConfirm && autoConfirmKey != null && !autoConfirmKey.isEmpty() && !isPersisted;
    }

    private PermissionConfirmResponse parseConfirmPayload(Object userInput) {
        if (userInput instanceof PermissionConfirmResponse response) {
            return response;
        }
        if (userInput instanceof Map<?, ?> map) {
            return PermissionConfirmResponse.builder()
                    .approved(toBool(map.get("approved")))
                    .feedback(toStr(map.get("feedback")))
                    .autoConfirm(toBool(map.get("auto_confirm")))
                    .persistAllow(toBool(map.get("persist_allow")))
                    .build();
        }
        return null;
    }

    private static boolean toBool(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value).trim());
    }

    private static String toStr(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private PermissionResult toPermissionResult(PermissionCheckResult result) {
        return PermissionResult.builder()
                .permission(result.getPermission())
                .matchedRule(result.getMatchedRule())
                .build();
    }

    private String commandText(Map<String, Object> toolArgs) {
        if (toolArgs == null) {
            return "";
        }
        Object raw = toolArgs.get(SHELL_COMMAND_KEY);
        String value = raw == null ? "" : raw.toString();
        if (value.isEmpty()) {
            Object cmd = toolArgs.get(SHELL_CMD_KEY);
            value = cmd == null ? "" : cmd.toString();
        }
        return value.strip();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractToolArgs(AgentCallbackContext ctx) {
        Object toolArgsObj = ctx.getInputs() instanceof ToolCallInputs inputs ? inputs.getToolArgs() : null;
        if (toolArgsObj instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        if (toolArgsObj instanceof String rawArgs) {
            return parseJsonArgs(rawArgs);
        }
        return new LinkedHashMap<>();
    }

    private static Map<String, Object> parseJsonArgs(String rawArgs) {
        if (rawArgs == null || rawArgs.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = JSON_MAPPER.readValue(rawArgs, new TypeReference<>() {
            });
            return new LinkedHashMap<>(parsed);
        } catch (JsonProcessingException ex) {
            logger.warn("[PermissionEngine] permission.tool_args.parse_failed raw={}", rawArgs, ex);
            return new LinkedHashMap<>();
        }
    }

    private InterruptRequest buildInterruptRequest(String toolName, Map<String, Object> toolArgs,
                                                   String autoConfirmKey) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("tool_name", toolName);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("approved", Map.of("type", "boolean", "description", "approve the tool call"));
        schema.put("feedback", Map.of("type", "string", "description", "rejection reason"));
        schema.put("auto_confirm", Map.of("type", "boolean", "description", "remember for this session"));
        schema.put("persist_allow", Map.of("type", "boolean", "description", "write a permanent allow rule"));
        String argsPreview = formatArgsPreview(toolArgs);
        String message = "Permission approval required for tool: " + toolName + argsPreview;
        return InterruptRequest.builder()
                .message(message)
                .context(context)
                .payloadSchema(schema)
                .autoConfirmKey(autoConfirmKey)
                .build();
    }

    private static String formatArgsPreview(Map<String, Object> toolArgs) {
        if (toolArgs == null || toolArgs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\narguments:\n");
        for (Map.Entry<String, Object> entry : toolArgs.entrySet()) {
            sb.append("  ").append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }
        String preview = sb.toString();
        return preview.length() > 1000 ? preview.substring(0, 1000) : preview;
    }

    private void applyResolvedDecision(AgentCallbackContext ctx, ToolCall toolCall, InterruptDecision decision) {
        if (!(ctx.getInputs() instanceof ToolCallInputs inputs)) {
            return;
        }
        if (decision instanceof ApproveResult approveResult) {
            if (approveResult.getNewArgs() != null) {
                inputs.setToolArgs(approveResult.getNewArgs());
            }
            return;
        }
        if (decision instanceof RejectResult rejectResult) {
            ctx.getExtra().put("_skip_tool", Boolean.TRUE);
            inputs.setToolResult(rejectResult.getToolResult());
            ToolMessage toolMessage = rejectResult.getToolMessage();
            if (toolMessage == null) {
                String toolCallId = toolCall != null ? toolCall.getId() : "";
                toolMessage = ToolMessage.builder()
                        .content(String.valueOf(rejectResult.getToolResult()))
                        .toolCallId(toolCallId)
                        .build();
            }
            inputs.setToolMsg(toolMessage);
            return;
        }
        if (decision instanceof InterruptResult interruptResult) {
            throw new ToolInterruptException(interruptResult.getRequest(), toolCall);
        }
    }
}
