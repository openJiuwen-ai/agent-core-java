/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.runner.callback.AbortError;
import com.openjiuwen.core.single_agent.interrupt.InterruptConstants;
import com.openjiuwen.core.single_agent.interrupt.InterruptRequest;
import com.openjiuwen.core.single_agent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;
import com.openjiuwen.harness.rails.interrupt.InterruptDecision;
import com.openjiuwen.harness.security.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Permission interrupt rail for tool permission checks.
 *
 * <p>Implements permission checks via PermissionEngine and triggers HITL interrupts
 * for ASK decisions using the built-in interrupt rail flow.
 *
 * <p>- ALLOW: continue
 * - DENY: reject
 * - ASK: interrupt with ConfirmPayload schema
 *
 * <p>Auto-confirm is stored in session state (INTERRUPT_AUTO_CONFIRM_KEY).
 * Supports fine-grained auto-confirm keys for bash commands (e.g., bash_dir, bash_rm).
 *
 * <p>Mirrors Python's {@code PermissionInterruptRail} in
 * {@code openjiuwen.harness.rails.security.tool_security_rail}.
 */
public class PermissionInterruptRail extends ConfirmInterruptRail {

    private static final Logger LOG = LoggerFactory.getLogger(PermissionInterruptRail.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Tool name aliases from internal tools to MCP names. */
    private static final Map<String, String> TOOL_NAME_ALIASES = Map.of(
            "free_search", "mcp_free_search",
            "paid_search", "mcp_paid_search",
            "fetch_webpage", "mcp_fetch_webpage",
            "exec_command", "mcp_exec_command"
    );

    private final int priority = 90;
    private final Map<String, Object> staticConfig;
    private final PermissionEngine engine;
    private final ToolPermissionHost host;

    /**
     * Construct with configuration and engine.
     */
    public PermissionInterruptRail(
            Map<String, Object> config,
            PermissionEngine engine,
            Iterable<String> toolNames,
            ToolPermissionHost host) {
        super(toolNames);
        this.staticConfig = config != null ? config : Map.of();
        this.host = host != null ? host : ToolPermissionHost.defaultHost();
        this.engine = engine != null ? engine : createDefaultEngine();
        LOG.info("[PermissionEngine] permission.rail.init intercept=all_tools optional_tool_tags={} " +
                "tools_keys={} llm_enabled={} model_name={}",
                getToolNames(),
                getToolsKeysFromConfig(),
                isLlmEnabled(),
                getModelName());
    }

    /**
     * Default constructor.
     */
    public PermissionInterruptRail() {
        this(null, null, null, null);
    }

    private PermissionEngine createDefaultEngine() {
        Path workspaceRoot = null;
        if (host.getResolveWorkspaceDir() != null) {
            try {
                workspaceRoot = host.getResolveWorkspaceDir().get();
            } catch (Exception e) {
                LOG.debug("[PermissionEngine] permission.rail.workspace_resolve_failed", e);
            }
        }
        return new PermissionEngine(staticConfig);
    }

    /**
     * Normalize tool name using aliases.
     */
    private String normalizeToolName(String toolName) {
        return TOOL_NAME_ALIASES.getOrDefault(toolName, toolName);
    }

    /**
     * Get auto-confirm key for tool call.
     */
    private String getAutoConfirmKey(ToolCall toolCall) {
        if (toolCall == null || toolCall.getName() == null) {
            return "";
        }
        String toolName = toolCall.getName();
        Map<String, Object> toolArgs = parseToolArgs(toolCall);

        if (Set.of("bash", "mcp_exec_command", "create_terminal").contains(toolName)) {
            String cmd = extractCommand(toolArgs);
            return buildShellAutoConfirmKey(toolName, cmd);
        }
        return toolName;
    }

    /**
     * Build shell auto-confirm key from command.
     */
    private String buildShellAutoConfirmKey(String toolName, String command) {
        if (command == null || command.isEmpty()) {
            return "";
        }
        String text = command.strip();
        ShellStructureAnalysis analysis = ShellStructureParser.analyze(text);
        if (analysis.getKind() != ShellStructureAnalysis.Kind.SIMPLE) {
            return "";
        }
        if (analysis.hasRiskyStructure()) {
            return "";
        }

        // Get base command
        String[] tokens = text.split("\\s+");
        if (tokens.length == 0) {
            return "";
        }
        String subcommand = tokens[0].strip();
        if (subcommand.isEmpty()) {
            return "";
        }
        return toolName + ":" + subcommand;
    }

    /**
     * Check if should store auto-confirm.
     */
    private boolean shouldStoreAutoConfirm(boolean autoConfirm, Object session, String key, boolean persisted) {
        return autoConfirm && session != null && key != null && !key.isEmpty() && !persisted;
    }

    /**
     * Before tool call hook - main permission check entry point.
     */
    @Override
    public void beforeToolCall(AgentCallbackContext ctx) {
        String toolName = ctx.getExtra().get("tool_name") != null ? String.valueOf(ctx.getExtra().get("tool_name")) : "";
        ToolCall toolCall = ctx.getExtra().get("tool_call") instanceof ToolCall tc ? tc : null;
        String normalizedName = normalizeToolName(toolName);

        LOG.info("[PermissionEngine] permission.rail.before_tool_call tool={} normalized={} optional_tool_tags={}",
                toolName, normalizedName, getToolNames());

        String toolCallId = toolCall != null ? resolveToolCallId(toolCall) : "";
        Object userInput = getUserInput(ctx, toolCallId);

        Map<String, Object> autoConfirmConfig = new LinkedHashMap<>();
        if (ctx.getSession() != null) {
            Object configObj = ctx.getSession().getState(InterruptConstants.INTERRUPT_AUTO_CONFIRM_KEY);
            if (configObj instanceof Map) {
                autoConfirmConfig = (Map<String, Object>) configObj;
            }
        }

        InterruptDecision decision;
        try {
            decision = resolveInterrupt(ctx, toolCall, userInput, autoConfirmConfig);
        } catch (Exception e) {
            LOG.error("[PermissionEngine] permission.rail.resolve_interrupt_failed tool={} error={}", toolName, e.getMessage());
            decision = InterruptDecision.approve();
        }
        ctx.getExtra().put("_interrupt_decision", decision);
        applyDecision(ctx, toolCall, toolName, decision);
    }

    /**
     * Resolve interrupt decision.
     */
    public InterruptDecision resolveInterrupt(
            AgentCallbackContext ctx,
            ToolCall toolCall,
            Object userInput,
            Map<String, Object> autoConfirmConfig) throws Exception {

        String toolName = toolCall != null ? toolCall.getName() : "";
        String normalizedName = normalizeToolName(toolName);
        Map<String, Object> toolArgs = parseToolArgs(toolCall);
        String autoConfirmKey = getAutoConfirmKey(toolCall);

        LOG.info("[PermissionEngine] permission.rail.resolve tool={} normalized={} tool_args={} auto_confirm_key={}",
                toolName, normalizedName, toolArgs, autoConfirmKey);

        // Call permission scene hook if available
        if (host.getPermissionSceneHook() != null) {
            try {
                PermissionSceneHookInput hookInput = PermissionSceneHookInput.builder()
                        .ctx(ctx)
                        .toolCall(toolCall)
                        .userInput(userInput)
                        .normalizedToolName(normalizedName)
                        .toolArgs(toolArgs)
                        .engine(engine)
                        .build();

                CompletableFuture<ToolPermissionHost.SceneHookOutput> hookResult =
                        host.getPermissionSceneHook().apply(hookInput);
                ToolPermissionHost.SceneHookOutput sceneOut = hookResult.get();

                if (sceneOut != null) {
                    if ("approve".equals(sceneOut.getAction())) {
                        return InterruptDecision.approve();
                    }
                    if ("reject".equals(sceneOut.getAction())) {
                        String msg = sceneOut.getMessage() != null
                                ? sceneOut.getMessage()
                                : "[PERMISSION_DENIED]";
                        return InterruptDecision.reject(msg);
                    }
                }
            } catch (Exception e) {
                LOG.warn("[PermissionEngine] permission.scene_hook.failed", e);
            }
        }

        // First check (no user input)
        if (userInput == null) {
            LOG.info("[PermissionEngine] permission.rail.first_check tool={} normalized={}", toolName, normalizedName);

            // Sync fresh permissions from host
            if (host.getGetPermissionsSnapshot() != null) {
                try {
                    Object snap = host.getGetPermissionsSnapshot().get();
                    if (snap instanceof Map) {
                        engine.updateConfig((Map<String, Object>) snap);
                    }
                } catch (Exception e) {
                    LOG.debug("[PermissionEngine] permission.rail.snapshot_failed", e);
                }
            } else {
                engine.updateConfig(staticConfig);
            }

            PermissionResult result = engine.checkPermission(normalizedName, toolArgs);

            if (result.isAllowed()) {
                LOG.info("[PermissionEngine] permission.rail.result tool={} decision=allow matched_rule={}",
                        toolName, result.getMatchedRule());
                return InterruptDecision.approve();
            }

            if (result.isDenied()) {
                LOG.warn("[PermissionEngine] permission.rail.result tool={} decision=deny matched_rule={}",
                        toolName, result.getMatchedRule());
                return InterruptDecision.reject("[PERMISSION_DENIED] " +
                        (result.getReason() != null ? result.getReason() : "Operation not allowed"));
            }

            // Check auto-confirm
            if (checkAutoConfirmed(autoConfirmConfig, autoConfirmKey)) {
                LOG.info("[PermissionEngine] permission.auto_confirm.hit tool={} key={}", toolName, autoConfirmKey);
                return InterruptDecision.approve();
            }

            // Request host confirmation if available
            if (host.getRequestPermissionConfirmation() != null) {
                try {
                    PermissionConfirmationRequest req = PermissionConfirmationRequest.builder()
                            .ctx(ctx)
                            .toolCall(toolCall)
                            .result(result)
                            .autoConfirmKey(autoConfirmKey)
                            .build();

                    CompletableFuture<ToolPermissionHost.ConfirmationResult> confirmResult =
                            host.getRequestPermissionConfirmation().apply(req);
                    ToolPermissionHost.ConfirmationResult extOut = confirmResult.get();

                    if (extOut == null || extOut.getType() == ToolPermissionHost.ConfirmationResultType.FAILED) {
                        return InterruptDecision.reject(
                                "[PERMISSION_DENIED] " + (result.getReason() != null ? result.getReason() : "Operation requires approval") +
                                        " (Hosted permission request failed)");
                    }

                    if (extOut.getType() == ToolPermissionHost.ConfirmationResultType.INTERRUPT) {
                        // Fall through to interrupt below
                    } else if (extOut.getType() == ToolPermissionHost.ConfirmationResultType.RESPONSE) {
                        PermissionConfirmResponse confirmPayload = extOut.getResponse();
                        boolean persisted = false;
                        if (confirmPayload.isApproved() && confirmPayload.isAutoConfirm()) {
                            persisted = persistAllowAlways(normalizedName, toolArgs);
                        }
                        LOG.info("[PermissionEngine] permission.persist.result tool={} confirm_path=hosted persisted={}",
                                toolName, persisted);

                        if (shouldStoreAutoConfirm(confirmPayload.isAutoConfirm(), ctx.getSession(),
                                autoConfirmKey, persisted)) {
                            storeAutoConfirm(ctx, autoConfirmKey);
                        }

                        if (confirmPayload.isApproved()) {
                            String decision = confirmPayload.isAutoConfirm() ? "allow_always" : "allow_once";
                            LOG.info("[PermissionEngine] permission.user.decision tool={} confirm_path=hosted decision={} persisted={}",
                                    toolName, decision, persisted);
                            return InterruptDecision.approve();
                        }

                        LOG.info("[PermissionEngine] permission.user.decision tool={} confirm_path=hosted decision=deny", toolName);
                        return InterruptDecision.reject(confirmPayload.getFeedback() != null
                                ? confirmPayload.getFeedback()
                                : "[PERMISSION_REJECTED] User rejected the request.");
                    }
                } catch (Exception e) {
                    LOG.warn("[PermissionEngine] permission.hosted_confirm.failed", e);
                }
            }

            // Interrupt to ask user
            LOG.info("[PermissionEngine] permission.interrupt.ask tool={} matched_rule={}",
                    toolName, result.getMatchedRule());
            String message = buildMessage(toolCall, result);
            return InterruptDecision.interrupt(Map.of(
                    "message", message,
                    "payload_schema", ConfirmPayloadSchema.toSchema()
            ));
        }

        // User response processing
        LOG.info("[PermissionEngine] permission.rail.user_response tool={}", toolName);
        PermissionConfirmResponse payload = parseConfirmPayload(userInput);
        if (payload == null) {
            String message = buildMessage(toolCall, new PermissionResult(
                    PermissionLevel.ASK, null, "Invalid confirmation payload"));
            return InterruptDecision.interrupt(Map.of(
                    "message", message,
                    "payload_schema", ConfirmPayloadSchema.toSchema()
            ));
        }

        boolean persisted = false;
        if (payload.isApproved() && payload.isAutoConfirm()) {
            persisted = persistAllowAlways(normalizedName, toolArgs);
            LOG.info("[PermissionEngine] permission.persist.result tool={} confirm_path={} persisted={}",
                    toolName, getConfirmPathLabel(), persisted);
        }

        if (shouldStoreAutoConfirm(payload.isAutoConfirm(), ctx.getSession(), autoConfirmKey, persisted)) {
            storeAutoConfirm(ctx, autoConfirmKey);
        }

        if (payload.isApproved()) {
            String decision = payload.isAutoConfirm() ? "allow_always" : "allow_once";
            LOG.info("[PermissionEngine] permission.user.decision tool={} confirm_path={} decision={} persisted={}",
                    toolName, getConfirmPathLabel(), decision, persisted);
            return InterruptDecision.approve();
        }

        LOG.info("[PermissionEngine] permission.user.decision tool={} confirm_path={} decision=deny",
                toolName, getConfirmPathLabel());
        return InterruptDecision.reject(payload.getFeedback() != null
                ? payload.getFeedback()
                : "[PERMISSION_REJECTED] User rejected the request.");
    }

    /**
     * Parse tool arguments from tool call.
     */
    private Map<String, Object> parseToolArgs(ToolCall toolCall) {
        if (toolCall == null) return Map.of();
        Object args = toolCall.getArguments();
        if (args instanceof String) {
            try {
                Object parsed = JSON.readValue((String) args, Object.class);
                if (parsed instanceof Map) {
                    return (Map<String, Object>) parsed;
                }
            } catch (Exception e) {
                return Map.of();
            }
        }
        if (args instanceof Map) {
            return (Map<String, Object>) args;
        }
        return Map.of();
    }

    /**
     * Parse confirm payload from user input.
     */
    private PermissionConfirmResponse parseConfirmPayload(Object userInput) {
        if (userInput instanceof PermissionConfirmResponse) {
            return (PermissionConfirmResponse) userInput;
        }
        if (userInput instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) userInput;
            return PermissionConfirmResponse.builder()
                    .approved(Boolean.TRUE.equals(map.get("approved")))
                    .feedback(map.get("feedback") != null ? map.get("feedback").toString() : "")
                    .autoConfirm(Boolean.TRUE.equals(map.get("auto_confirm")))
                    .build();
        }
        if (userInput instanceof String) {
            try {
                Map<String, Object> rawPayload = JSON.readValue((String) userInput, Map.class);
                return parseConfirmPayload(rawPayload);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Persist allow always rule.
     */
    private boolean persistAllowAlways(String normalizedName, Map<String, Object> toolArgs) {
        Map<String, Object> cfg = deepCopy(engine.getConfig());

        List<PermissionSuggestions.Suggestion> suggestions =
                PermissionSuggestions.buildPermissionSuggestions(normalizedName, toolArgs, null);

        boolean okTool = PermissionSuggestions.mergeSuggestionsIntoPermissions(cfg, suggestions);
        if (!okTool) {
            LOG.warn("[PermissionEngine] permission.persist.skip tool={} reason=no_safe_suggestion", normalizedName);
            return false;
        }

        Map<String, Object> prevCfg = deepCopy(engine.getConfig());
        engine.updateConfig(cfg);

        if (host.getPersistAllowRule() != null) {
            try {
                boolean persisted = host.getPersistAllowRule().apply(cfg);
                if (!persisted) {
                    engine.updateConfig(prevCfg);
                }
                return persisted;
            } catch (Exception e) {
                LOG.warn("[PermissionEngine] permission.persist.host_failed", e);
                engine.updateConfig(prevCfg);
                return false;
            }
        } else {
            boolean persisted = PermissionPatterns.writePermissionsSectionToAgentConfigYaml(
                    host.getPermissionYamlPath(), cfg);
            if (!persisted) {
                engine.updateConfig(prevCfg);
            }
            return persisted;
        }
    }

    /**
     * Check if auto-confirmed.
     */
    protected boolean checkAutoConfirmed(Map<String, Object> autoConfirmConfig, String key) {
        if (autoConfirmConfig == null || key == null || key.isEmpty()) {
            return false;
        }
        return Boolean.TRUE.equals(autoConfirmConfig.get(key));
    }

    /**
     * Store auto-confirm in session.
     */
    private void storeAutoConfirm(AgentCallbackContext ctx, String key) {
        if (ctx.getSession() == null || key == null || key.isEmpty()) return;
        Object configObj = ctx.getSession().getState(InterruptConstants.INTERRUPT_AUTO_CONFIRM_KEY);
        Map<String, Object> config = configObj instanceof Map
                ? (Map<String, Object>) configObj
                : new LinkedHashMap<>();
        config.put(key, true);
        ctx.getSession().updateState(Map.of(InterruptConstants.INTERRUPT_AUTO_CONFIRM_KEY, config));
        LOG.info("[PermissionEngine] permission.auto_confirm.store key={}", key);
    }

    /**
     * Build message for interrupt.
     */
    private String buildMessage(ToolCall toolCall, PermissionResult result) {
        String toolName = toolCall != null ? toolCall.getName() : "";
        Map<String, Object> toolArgs = parseToolArgs(toolCall);

        StringBuilder parts = new StringBuilder();
        parts.append("**工具 `").append(toolName).append("` 需要授权才能执行**\n\n");
        parts.append("请确认是否允许该操作。\n\n");

        String argsPreview = formatArgsPreview(toolArgs);
        if (argsPreview != null && !argsPreview.equals("{}")) {
            parts.append("参数：\n```json\n").append(argsPreview).append("\n```\n");
        }

        parts.append("\n匹配规则：`").append(result.getMatchedRule() != null ? result.getMatchedRule() : "N/A").append("`");

        List<String> externalPaths = result.getExternalPaths();
        if (externalPaths != null && !externalPaths.isEmpty()) {
            parts.append("\n\n**外部路径：** `").append(String.join(", ", externalPaths)).append("`");
        }

        parts.append(buildAlwaysAllowHint(toolCall));
        return parts.toString();
    }

    /**
     * Build always allow hint.
     */
    private String buildAlwaysAllowHint(ToolCall toolCall) {
        if (toolCall == null) return "";
        String toolName = toolCall.getName();
        Map<String, Object> toolArgs = parseToolArgs(toolCall);
        String autoConfirmKey = getAutoConfirmKey(toolCall);

        if ("bash".equals(toolName) || "mcp_exec_command".equals(toolName) || "create_terminal".equals(toolName)) {
            String cmd = extractCommand(toolArgs);
            if (buildShellAutoConfirmKey(toolName, cmd) != null && !buildShellAutoConfirmKey(toolName, cmd).isEmpty()) {
                return "\n\n> 若选择「记住 / 总是允许」并提交 ``auto_confirm: true``，" +
                        "将合并权限配置并尝试写回磁盘（与仅本次允许相对）。";
            }
        }

        if (autoConfirmKey != null && !autoConfirmKey.isEmpty()) {
            return "\n\n> 若选择「记住 / 总是允许」并提交 ``auto_confirm: true``，" +
                    "将合并权限配置并写回磁盘；同时可在本会话内自动放行 ``" + autoConfirmKey + "`` 类调用。";
        }
        return "";
    }

    /**
     * Format args preview.
     */
    private String formatArgsPreview(Map<String, Object> toolArgs) {
        try {
            return JSON.writeValueAsString(toolArgs);
        } catch (Exception e) {
            return toolArgs.toString();
        }
    }

    /**
     * Extract command from tool args.
     */
    private String extractCommand(Map<String, Object> toolArgs) {
        Object cmd = toolArgs.get("command");
        if (cmd == null) cmd = toolArgs.get("cmd");
        return cmd != null ? cmd.toString() : "";
    }

    /**
     * Get confirm path label.
     */
    private String getConfirmPathLabel() {
        return host.getRequestPermissionConfirmation() != null ? "hosted" : "interrupt";
    }

    /**
     * Resolve tool call ID.
     */
    private String resolveToolCallId(ToolCall toolCall) {
        return toolCall != null ? toolCall.getId() : null;
    }

    /**
     * Get user input from context.
     */
    private Object getUserInput(AgentCallbackContext ctx, String toolCallId) {
        return ctx.getExtra().get("_resume_user_input");
    }

    /**
     * Apply decision to context.
     */
    private void applyDecision(AgentCallbackContext ctx, ToolCall toolCall, String toolName, InterruptDecision decision) {
        if (decision instanceof InterruptDecision.ApproveResult approve) {
            approve.getNewArgs().ifPresent(newArgs -> {
                if (ctx.getInputs() instanceof ToolCallInputs inputs) {
                    inputs.setToolArgs(newArgs);
                }
            });
            return;
        }

        if (decision instanceof InterruptDecision.RejectResult reject) {
            Object toolResult = reject.getToolResult().orElse(null);
            ToolMessage toolMessage = reject.getToolMessage()
                    .filter(ToolMessage.class::isInstance)
                    .map(ToolMessage.class::cast)
                    .orElseGet(() -> buildToolMessage(toolCall, toolResult));
            ctx.getExtra().put("_skip_tool", true);
            if (ctx.getInputs() instanceof ToolCallInputs inputs) {
                inputs.setToolResult(toolResult);
                inputs.setToolMsg(toolMessage);
            }
            return;
        }

        if (decision instanceof InterruptDecision.InterruptResult interrupt) {
            InterruptRequest request = toInterruptRequest(interrupt.getRequest());
            throw new AbortError(
                    "Tool execution interrupted: " + toolName,
                    new ToolInterruptException(request, toolCall)
            );
        }
    }

    private ToolMessage buildToolMessage(ToolCall toolCall, Object toolResult) {
        String toolCallId = toolCall != null ? toolCall.getId() : "";
        return ToolMessage.builder()
                .content(String.valueOf(toolResult))
                .toolCallId(toolCallId)
                .build();
    }

    @SuppressWarnings("unchecked")
    private InterruptRequest toInterruptRequest(Object rawRequest) {
        if (rawRequest instanceof InterruptRequest request) {
            return request;
        }
        if (rawRequest instanceof Map<?, ?> map) {
            Object schema = map.get("payload_schema");
            Map<String, Object> payloadSchema = schema instanceof Map<?, ?>
                    ? new LinkedHashMap<>((Map<String, Object>) schema)
                    : new LinkedHashMap<>();
            Object message = map.get("message");
            Object autoConfirmKey = map.get("auto_confirm_key");
            return InterruptRequest.builder()
                    .message(message != null ? String.valueOf(message) : "")
                    .payloadSchema(payloadSchema)
                    .autoConfirmKey(autoConfirmKey != null ? String.valueOf(autoConfirmKey) : "")
                    .build();
        }
        return InterruptRequest.builder()
                .message(rawRequest != null ? String.valueOf(rawRequest) : "")
                .build();
    }

    /**
     * Deep copy a map.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopy(Map<String, Object> source) {
        if (source == null) return new LinkedHashMap<>();
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                copy.put(entry.getKey(), deepCopy((Map<String, Object>) value));
            } else if (value instanceof List) {
                copy.put(entry.getKey(), new ArrayList<>((List<?>) value));
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }

    private List<String> getToolsKeysFromConfig() {
        Object toolsObj = staticConfig.get("tools");
        if (toolsObj instanceof Map) {
            return new ArrayList<>(((Map<String, Object>) toolsObj).keySet());
        }
        return List.of();
    }

    private boolean isLlmEnabled() {
        return false; // Simplified - engine doesn't expose LLM
    }

    private String getModelName() {
        return null; // Simplified
    }

    /**
     * Confirm payload schema for interrupt.
     */
    public static class ConfirmPayloadSchema {
        public static Map<String, Object> toSchema() {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("approved", Map.of("type", "boolean", "description", "是否批准"));
            properties.put("feedback", Map.of("type", "string", "description", "反馈或拒绝理由"));
            properties.put("auto_confirm", Map.of("type", "boolean", "description", "是否记住并持久化"));
            schema.put("properties", properties);
            schema.put("required", List.of("approved"));
            return schema;
        }
    }

    @Override
    public int getPriority() {
        return priority;
    }
}
