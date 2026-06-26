/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.rails;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.single_agent.interrupt.InterruptRequest;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Coordinates leader approval for teammate tool calls.
 *
 * <p>Mirrors Python's {@code TeamToolApprovalRail} in
 * {@code openjiuwen/agent_teams/rails/tool_approval_rail.py}.</p>
 */
public class TeamToolApprovalRail {

    public static final int PRIORITY = 90;

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;
    private static final String DEFAULT_REJECT_FEEDBACK = "Tool call rejected by leader";

    private final String teamName;
    private final String memberName;
    private final String leaderMemberName;
    private final ApprovalMessageManager messageManager;
    private final Set<String> toolNames;

    public TeamToolApprovalRail(Config config) {
        Config effectiveConfig = Objects.requireNonNull(config, "config");
        this.teamName = defaultString(effectiveConfig.teamName(), "");
        this.memberName = defaultString(effectiveConfig.memberName(), "");
        this.leaderMemberName = defaultString(effectiveConfig.leaderMemberName(), "");
        this.messageManager = Objects.requireNonNull(effectiveConfig.messageManager(), "messageManager");
        this.toolNames = new LinkedHashSet<>(effectiveConfig.toolNames());
    }

    public int getPriority() {
        return PRIORITY;
    }

    public String getTeamName() {
        return teamName;
    }

    public String getMemberName() {
        return memberName;
    }

    public String getLeaderMemberName() {
        return leaderMemberName;
    }

    public Set<String> getTools() {
        return new LinkedHashSet<>(toolNames);
    }

    public void addTool(String toolName) {
        if (isNonBlank(toolName)) {
            toolNames.add(toolName);
        }
    }

    public void addTools(Collection<String> names) {
        if (names == null) {
            return;
        }
        for (String name : names) {
            addTool(name);
        }
    }

    public void addPolicy(String toolName) {
        addTool(toolName);
    }

    public CompletionStage<InterruptDecision> resolveInterrupt(
            ApprovalCallbackContext ignoredContext,
            ToolCall toolCall,
            Object userInput,
            Map<String, ?> autoConfirmConfig
    ) {
        if (toolCall == null) {
            TEAM_LOGGER.error("tool_call not provided for member {}", memberName);
            return CompletableFuture.completedFuture(reject("Invalid tool call"));
        }

        String toolName = defaultString(toolCall.getName(), "");
        if (userInput == null) {
            String autoConfirmKey = getAutoConfirmKey(toolCall);
            if (isAutoConfirmed(autoConfirmConfig, autoConfirmKey)) {
                TEAM_LOGGER.debug("Tool {} auto-approved for member {}", toolName, memberName);
                return CompletableFuture.completedFuture(approve());
            }

            String toolCallId = resolveToolCallId(toolCall);
            String message = buildApprovalMessage(toolName, toolCallId, toolCall.getArguments());
            TEAM_LOGGER.info(
                    "Sending tool approval request to leader for {} (call_id: {})",
                    toolName,
                    toolCallId
            );
            return messageManager.sendMessage(message, leaderMemberName)
                    .thenApply(messageId -> {
                        if (!isNonBlank(messageId)) {
                            TEAM_LOGGER.error("Failed to send approval request for {}", toolName);
                            return reject("Failed to send approval request to leader");
                        }
                        return interrupt(new InterruptRequest(
                                "Awaiting leader approval for tool: " + toolName,
                                ConfirmPayload.toSchema(),
                                autoConfirmKey
                        ));
                    });
        }

        ConfirmPayload payload;
        try {
            payload = parsePayload(userInput);
            if (payload == null) {
                return CompletableFuture.completedFuture(interrupt(new InterruptRequest(
                        "Invalid approval response format for tool: " + toolName,
                        ConfirmPayload.toSchema(),
                        getAutoConfirmKey(toolCall)
                )));
            }
        } catch (RuntimeException exception) {
            TEAM_LOGGER.error("Failed to parse approval response for {}: {}", toolName, exception.getMessage());
            return CompletableFuture.completedFuture(interrupt(new InterruptRequest(
                    "Invalid approval response for tool: " + toolName,
                    ConfirmPayload.toSchema(),
                    getAutoConfirmKey(toolCall)
            )));
        }

        if (payload.approved()) {
            TEAM_LOGGER.info("Tool {} approved by leader for member {}", toolName, memberName);
            return CompletableFuture.completedFuture(approve());
        }

        String feedback = isNonBlank(payload.feedback()) ? payload.feedback() : DEFAULT_REJECT_FEEDBACK;
        TEAM_LOGGER.info("Tool {} rejected by leader for member {}: {}", toolName, memberName, feedback);
        return CompletableFuture.completedFuture(reject(feedback));
    }

    public CompletionStage<InterruptDecision> resolveInterrupt(
            ApprovalCallbackContext context,
            ToolCall toolCall,
            Object userInput
    ) {
        return resolveInterrupt(context, toolCall, userInput, null);
    }

    public ApproveResult approve() {
        return approve(null);
    }

    public ApproveResult approve(String newArgs) {
        return new ApproveResult(newArgs);
    }

    public RejectResult reject(Object toolResult) {
        return new RejectResult(toolResult);
    }

    public InterruptResult interrupt(InterruptRequest request) {
        return new InterruptResult(request);
    }

    public String getAutoConfirmKey(ToolCall toolCall) {
        return toolCall == null ? "" : defaultString(toolCall.getName(), "");
    }

    public String resolveToolCallId(ToolCall toolCall) {
        return toolCall == null ? "" : defaultString(toolCall.getId(), "");
    }

    public static boolean isAutoConfirmed(Map<String, ?> config, String key) {
        if (config == null || key == null || !config.containsKey(key)) {
            return false;
        }
        return truthy(config.get(key));
    }

    private String buildApprovalMessage(String toolName, String toolCallId, String arguments) {
        String args = arguments == null || arguments.isEmpty() ? "{}" : arguments;
        return "Teammate tool approval request.\n"
                + "Member: " + memberName + "\n"
                + "Tool: " + toolName + "\n"
                + "Tool Call ID: " + toolCallId + "\n"
                + "Arguments: " + args + "\n"
                + "Please review and call approve_tool.\n\n";
    }

    private static ConfirmPayload parsePayload(Object userInput) {
        if (userInput instanceof ConfirmPayload payload) {
            return payload;
        }
        if (userInput instanceof Map<?, ?> values) {
            return ConfirmPayload.fromMap(values);
        }
        return null;
    }

    private static boolean booleanField(Object value, String fieldName) {
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        if (value instanceof CharSequence textValue) {
            String normalized = textValue.toString().trim().toLowerCase();
            if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) {
                return true;
            }
            if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) {
                return false;
            }
        }
        throw new IllegalArgumentException("Invalid boolean field: " + fieldName);
    }

    private static boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.doubleValue() != 0.0D;
        }
        if (value instanceof CharSequence textValue) {
            return !textValue.isEmpty();
        }
        if (value instanceof Collection<?> collectionValue) {
            return !collectionValue.isEmpty();
        }
        if (value instanceof Map<?, ?> mapValue) {
            return !mapValue.isEmpty();
        }
        return true;
    }

    private static String defaultString(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Constructor options for {@link TeamToolApprovalRail}.
     *
     * <p>Mirrors Python's {@code TeamToolApprovalRail.__init__} parameters in
     * {@code openjiuwen/agent_teams/rails/tool_approval_rail.py}.</p>
     */
    public record Config(
            String teamName,
            String memberName,
            ApprovalMessageManager messageManager,
            String leaderMemberName,
            Collection<String> toolNames
    ) {
        public Config {
            toolNames = toolNames == null ? Set.of() : new LinkedHashSet<>(toolNames);
        }
    }

    /**
     * Minimal message manager behavior needed by the approval rail.
     *
     * <p>Mirrors Python's {@code TeamMessageManager.send_message} call in
     * {@code openjiuwen/agent_teams/rails/tool_approval_rail.py}.</p>
     */
    @FunctionalInterface
    public interface ApprovalMessageManager {
        CompletionStage<String> sendMessage(String content, String toMemberName);
    }

    /**
     * Minimal callback context marker for approval resolution.
     *
     * <p>Mirrors Python's {@code AgentCallbackContext} parameter in
     * {@code openjiuwen/agent_teams/rails/tool_approval_rail.py}.</p>
     */
    public static final class ApprovalCallbackContext {
    }

    /**
     * Payload for a leader approval response.
     *
     * <p>Mirrors Python's {@code ConfirmPayload} usage in
     * {@code openjiuwen/agent_teams/rails/tool_approval_rail.py}.</p>
     */
    public record ConfirmPayload(boolean approved, String feedback, boolean autoConfirm) {
        public ConfirmPayload {
            feedback = feedback == null ? "" : feedback;
        }

        public static ConfirmPayload approvedPayload() {
            return new ConfirmPayload(true, "", false);
        }

        public static ConfirmPayload rejectedPayload(String feedback) {
            return new ConfirmPayload(false, feedback, false);
        }

        public static ConfirmPayload fromMap(Map<?, ?> values) {
            if (values == null || !values.containsKey("approved")) {
                throw new IllegalArgumentException("Missing approved field");
            }
            boolean approved = booleanField(values.get("approved"), "approved");
            String feedback = values.containsKey("feedback") && values.get("feedback") != null
                    ? String.valueOf(values.get("feedback"))
                    : "";
            boolean autoConfirm = values.containsKey("auto_confirm")
                    && booleanField(values.get("auto_confirm"), "auto_confirm");
            return new ConfirmPayload(approved, feedback, autoConfirm);
        }

        public static Map<String, Object> toSchema() {
            Map<String, Object> approvedProperty = new LinkedHashMap<>();
            approvedProperty.put("title", "Approved");
            approvedProperty.put("type", "boolean");

            Map<String, Object> feedbackProperty = new LinkedHashMap<>();
            feedbackProperty.put("default", "");
            feedbackProperty.put("title", "Feedback");
            feedbackProperty.put("type", "string");

            Map<String, Object> autoConfirmProperty = new LinkedHashMap<>();
            autoConfirmProperty.put("default", false);
            autoConfirmProperty.put("title", "Auto Confirm");
            autoConfirmProperty.put("type", "boolean");

            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("approved", approvedProperty);
            properties.put("feedback", feedbackProperty);
            properties.put("auto_confirm", autoConfirmProperty);

            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("properties", properties);
            schema.put("required", Set.of("approved"));
            schema.put("title", "ConfirmPayload");
            schema.put("type", "object");
            return schema;
        }
    }

    /**
     * Base marker for interrupt decisions.
     *
     * <p>Mirrors Python's {@code InterruptDecision} hierarchy in
     * {@code openjiuwen/agent_teams/rails/tool_approval_rail.py}.</p>
     */
    public sealed interface InterruptDecision permits ApproveResult, RejectResult, InterruptResult {
    }

    /**
     * Decision to continue tool execution.
     *
     * <p>Mirrors Python's {@code ApproveResult} returned by
     * {@code openjiuwen/agent_teams/rails/tool_approval_rail.py}.</p>
     */
    public record ApproveResult(String newArgs) implements InterruptDecision {
    }

    /**
     * Decision to reject the pending tool call.
     *
     * <p>Mirrors Python's {@code RejectResult} returned by
     * {@code openjiuwen/agent_teams/rails/tool_approval_rail.py}.</p>
     */
    public record RejectResult(Object toolResult) implements InterruptDecision {
    }

    /**
     * Decision to interrupt and wait for leader approval.
     *
     * <p>Mirrors Python's {@code InterruptResult} returned by
     * {@code openjiuwen/agent_teams/rails/tool_approval_rail.py}.</p>
     */
    public record InterruptResult(InterruptRequest request) implements InterruptDecision {
    }
}
