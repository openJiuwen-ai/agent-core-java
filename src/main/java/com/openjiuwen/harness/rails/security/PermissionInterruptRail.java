/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.security;

import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail.ConfirmPayload;
import com.openjiuwen.harness.security.PermissionConfirmResponse;
import com.openjiuwen.harness.security.PermissionEngine;
import com.openjiuwen.harness.security.PermissionLevel;
import com.openjiuwen.harness.security.PermissionPatterns;
import com.openjiuwen.harness.security.PermissionResult;
import com.openjiuwen.harness.security.PermissionsSection;
import com.openjiuwen.harness.security.ShellAst;
import com.openjiuwen.harness.security.ShellAstParseResult;
import com.openjiuwen.harness.security.ToolPermissionHost;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * Tool permission interrupt rail.
 *
 * <p>Mirrors Python's {@code PermissionInterruptRail} in
 * {@code openjiuwen/harness/rails/security/tool_security_rail.py}.</p>
 */
public class PermissionInterruptRail extends BaseSecurityRail {

    private static final Map<String, String> TOOL_NAME_ALIASES = Map.of(
            "free_search", "mcp_free_search",
            "paid_search", "mcp_paid_search",
            "fetch_webpage", "mcp_fetch_webpage",
            "exec_command", "mcp_exec_command"
    );
    private static final Set<String> SHELL_TOOL_NAMES = Set.of("bash", "mcp_exec_command", "create_terminal");

    private Map<String, Object> staticConfig;
    private PermissionEngine engine;
    private Object llm;
    private String modelName;
    private ToolPermissionHost host;

    public PermissionInterruptRail(Map<String, Object> config) {
        this(config, null, Set.of(), null, null, null);
    }

    public PermissionInterruptRail(PermissionsSection config) {
        this(toConfigMap(config), null, Set.of(), null, null, null);
    }

    public PermissionInterruptRail(
            Map<String, Object> config,
            PermissionEngine engine,
            Iterable<String> toolNames,
            Object llm,
            String modelName,
            ToolPermissionHost host
    ) {
        super(toolNames);
        setPriority(90);
        setSupportedEvents(Set.of(BEFORE_TOOL_CALL));
        this.staticConfig = deepCopyMap(config);
        this.llm = llm;
        this.modelName = modelName;
        this.host = host == null ? new ToolPermissionHost() : host;
        this.engine = engine == null ? new PermissionEngine(this.staticConfig, llm, modelName, workspaceRoot()) : engine;
    }

    public void updateConfig(Map<String, Object> config) {
        this.staticConfig = deepCopyMap(config);
        this.engine.updateConfig(this.staticConfig);
    }

    public void updateConfig(PermissionsSection config) {
        updateConfig(toConfigMap(config));
    }

    public void updateLlm(Object llm, String modelName) {
        this.llm = llm;
        this.modelName = modelName;
        this.engine.updateLlm(llm, modelName);
    }

    public Map<String, Object> getStaticConfig() {
        return deepCopyMap(staticConfig);
    }

    public PermissionEngine getEngine() {
        return engine;
    }

    public ToolPermissionHost getHost() {
        return host;
    }

    public Object getLlm() {
        return llm;
    }

    public String getModelName() {
        return modelName;
    }

    @Override
    protected SecurityDecision runSecurityCheck(SecurityCheckContext securityCtx) {
        CallbackContext ctx = securityCtx.callbackContext();
        String rawToolName = stringValue(ctx.get("tool_name"));
        if (rawToolName.isBlank()) {
            return allow();
        }
        String normalizedName = normalizeToolName(rawToolName);

        Map<String, Object> toolArgs = parseToolArgs(ctx.get("tool_args"));
        refreshConfigFromHostSnapshot();

        if (host.getPermissionSceneHook() != null) {
            SecurityDecision sceneDecision = resolveHostedScene(ctx, normalizedName, toolArgs, securityCtx.userInput());
            if (sceneDecision != null) {
                return sceneDecision;
            }
        }

        PermissionResult result = engine.checkPermission(normalizedName, toolArgs);
        ctx.put("permission_result", result);
        if (result.isAllowed()) {
            return allow();
        }
        if (result.isDenied()) {
            return reject("[PERMISSION_DENIED] " + fallback(result.getReason(), "Operation not allowed"));
        }

        String autoConfirmKey = getAutoConfirmKey(rawToolName, toolArgs);
        if (isAutoConfirmed(securityCtx.autoConfirmConfig(), autoConfirmKey)) {
            return allow();
        }

        PermissionConfirmResponse response = hostedConfirmation(ctx, result, autoConfirmKey);
        if (response != null) {
            return resolveConfirmationResponse(ctx, normalizedName, toolArgs, autoConfirmKey, response);
        }

        PermissionConfirmResponse userResponse = parseConfirmPayload(securityCtx.userInput());
        if (userResponse != null) {
            return resolveConfirmationResponse(ctx, normalizedName, toolArgs, autoConfirmKey, userResponse);
        }

        Map<String, Object> request = buildRequest(rawToolName, toolArgs, result, autoConfirmKey);
        return interrupt(request, securityCtx.subjectId());
    }

    private SecurityDecision resolveHostedScene(
            CallbackContext ctx,
            String toolName,
            Map<String, Object> toolArgs,
            Object userInput
    ) {
        try {
            ToolPermissionHost.PermissionSceneHookInput input = new ToolPermissionHost.PermissionSceneHookInput(
                    ctx,
                    ctx.get("tool_call"),
                    userInput,
                    toolName,
                    toolArgs,
                    engine
            );
            CompletionStage<ToolPermissionHost.PermissionSceneDecision> stage = host.getPermissionSceneHook().apply(input);
            ToolPermissionHost.PermissionSceneDecision decision = stage == null ? null : stage.toCompletableFuture().join();
            if (decision == null || decision.action() == null) {
                return null;
            }
            if ("approve".equals(decision.action())) {
                return allow();
            }
            if ("reject".equals(decision.action())) {
                return reject(fallback(decision.message(), "[PERMISSION_DENIED]"));
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private PermissionConfirmResponse hostedConfirmation(
            CallbackContext ctx,
            PermissionResult result,
            String autoConfirmKey
    ) {
        if (host.getRequestPermissionConfirmationHook() == null) {
            return null;
        }
        try {
            ToolPermissionHost.PermissionConfirmationRequest request =
                    new ToolPermissionHost.PermissionConfirmationRequest(
                            ctx,
                            ctx.get("tool_call"),
                            result,
                            autoConfirmKey
                    );
            CompletionStage<ToolPermissionHost.PermissionConfirmationResult> stage =
                    host.getRequestPermissionConfirmationHook().apply(request);
            ToolPermissionHost.PermissionConfirmationResult confirmation =
                    stage == null ? null : stage.toCompletableFuture().join();
            if (confirmation instanceof ToolPermissionHost.PermissionConfirmResponseWrapper wrapper) {
                return wrapper.response();
            }
            if (confirmation instanceof ToolPermissionHost.InterruptPermissionConfirmationResult) {
                return null;
            }
            return null;
        } catch (Exception ignored) {
            return new PermissionConfirmResponse(false, "Hosted permission request failed", false);
        }
    }

    private SecurityDecision resolveConfirmationResponse(
            CallbackContext ctx,
            String toolName,
            Map<String, Object> toolArgs,
            String autoConfirmKey,
            PermissionConfirmResponse response
    ) {
        if (response.isApproved()) {
            boolean persisted = false;
            if (response.isAutoConfirm()) {
                persisted = persistAllowAlways(toolName, toolArgs);
                if (!persisted && !autoConfirmKey.isBlank()) {
                    storeAutoConfirm(ctx, autoConfirmKey);
                }
            }
            ctx.put("permission_confirmed", Map.of(
                    "approved", true,
                    "auto_confirm", response.isAutoConfirm(),
                    "persisted", persisted
            ));
            return allow();
        }
        return reject(fallback(response.getFeedback(), "[PERMISSION_REJECTED] User rejected the request."));
    }

    private boolean persistAllowAlways(String toolName, Map<String, Object> toolArgs) {
        Map<String, Object> previous = deepCopyMap(engine.getConfig());
        PermissionPatterns.PermissionsMergeResult toolMerge =
                PermissionPatterns.mergePermissionAllowRuleIntoPermissions(previous, toolName, toolArgs);
        Map<String, Object> merged = toolMerge.permissions();
        boolean changed = toolMerge.changed();
        List<String> externalPaths = collectExternalDirectoryPersistPaths(toolName, toolArgs, merged);
        if (!externalPaths.isEmpty()) {
            PermissionPatterns.PermissionsMergeResult externalMerge =
                    PermissionPatterns.mergeExternalDirectoryAllowIntoPermissions(merged, externalPaths);
            merged = externalMerge.permissions();
            changed = changed || externalMerge.changed();
        }
        if (!changed) {
            return false;
        }

        Map<String, Object> oldConfig = deepCopyMap(engine.getConfig());
        updateConfig(merged);
        boolean persisted;
        if (host.getPersistAllowRuleHook() != null) {
            try {
                persisted = host.getPersistAllowRuleHook().apply(deepCopyMap(merged));
            } catch (Exception ignored) {
                persisted = false;
            }
        } else {
            persisted = PermissionPatterns.writePermissionsSectionToAgentConfigYaml(
                    host.getPermissionYamlPath(),
                    merged
            );
        }
        if (!persisted) {
            updateConfig(oldConfig);
        }
        return persisted;
    }

    private List<String> collectExternalDirectoryPersistPaths(
            String toolName,
            Map<String, Object> toolArgs,
            Map<String, Object> permissions
    ) {
        Path workspace = workspaceRoot();
        if (workspace == null) {
            return List.of();
        }
        try {
            PermissionEngine checker = new PermissionEngine(permissions, llm, modelName, workspace);
            PermissionResult result = checker.evaluateGlobalPolicyDirectly(toolName, toolArgs, true).permission() == null
                    ? null
                    : checker.checkPermission(toolName, toolArgs);
            if (result == null || result.getPermission() != PermissionLevel.ASK || result.getExternalPaths() == null) {
                return List.of();
            }
            return result.getExternalPaths();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private void refreshConfigFromHostSnapshot() {
        if (host.getPermissionsSnapshotSupplier() == null) {
            engine.updateConfig(staticConfig);
            return;
        }
        try {
            Map<String, Object> snapshot = host.getPermissionsSnapshotSupplier().get();
            if (snapshot != null) {
                updateConfig(snapshot);
                return;
            }
        } catch (Exception ignored) {
            // Fall back to static config below.
        }
        engine.updateConfig(staticConfig);
    }

    private Path workspaceRoot() {
        if (host != null && host.getWorkspaceDirResolver() != null) {
            try {
                return host.getWorkspaceDirResolver().get();
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private Map<String, Object> buildRequest(
            String toolName,
            Map<String, Object> toolArgs,
            PermissionResult result,
            String autoConfirmKey
    ) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("message", buildMessage(toolName, toolArgs, result));
        request.put("tool_name", toolName);
        request.put("tool_args", new LinkedHashMap<>(toolArgs));
        request.put("matched_rule", result.getMatchedRule());
        request.put("reason", result.getReason());
        request.put("payload_schema", ConfirmPayload.toSchema());
        request.put("auto_confirm_key", autoConfirmKey);
        return request;
    }

    private String buildMessage(String toolName, Map<String, Object> toolArgs, PermissionResult result) {
        return "Tool `" + toolName + "` requires permission before execution.\n"
                + "Arguments: " + toolArgs + "\n"
                + "Matched rule: " + fallback(result.getMatchedRule(), "N/A");
    }

    private static String stringValue(Object rawName) {
        return rawName == null ? "" : String.valueOf(rawName).trim();
    }

    private static String normalizeToolName(String rawName) {
        String toolName = rawName == null ? "" : rawName.trim();
        return TOOL_NAME_ALIASES.getOrDefault(toolName, toolName);
    }

    private static String getAutoConfirmKey(String toolName, Map<String, Object> toolArgs) {
        if (SHELL_TOOL_NAMES.contains(toolName)) {
            Object command = toolArgs.containsKey("command") ? toolArgs.get("command") : toolArgs.get("cmd");
            return buildShellAutoConfirmKey(toolName, command == null ? "" : String.valueOf(command));
        }
        return toolName;
    }

    private static String buildShellAutoConfirmKey(String toolName, String command) {
        String text = command == null ? "" : command.trim();
        if (text.isBlank()) {
            return "";
        }
        ShellAstParseResult result = ShellAst.parseShellForPermission(text);
        if (!"simple".equals(result.getKind()) || result.getFlags().hasRiskyStructure()
                || result.getSubcommands().size() != 1) {
            return "";
        }
        String subcommand = result.getSubcommands().get(0).getText().trim();
        return subcommand.isBlank() ? "" : toolName + ":" + subcommand;
    }

    private static boolean isAutoConfirmed(Map<String, Object> config, String key) {
        Object value = config == null ? null : config.get(key);
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseToolArgs(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static PermissionConfirmResponse parseConfirmPayload(Object userInput) {
        if (userInput instanceof PermissionConfirmResponse response) {
            return response;
        }
        if (userInput instanceof ConfirmPayload payload) {
            return new PermissionConfirmResponse(payload.approved(), payload.feedback(), payload.autoConfirm());
        }
        if (!(userInput instanceof Map<?, ?> map)) {
            return null;
        }
        boolean approved = booleanValue(map.get("approved"), false);
        boolean autoConfirm = booleanValue(map.get("auto_confirm"), false);
        Object feedback = map.get("feedback");
        return new PermissionConfirmResponse(approved, feedback == null ? "" : String.valueOf(feedback), autoConfirm);
    }

    private static void storeAutoConfirm(CallbackContext ctx, String key) {
        Object rawConfig = ctx.get("auto_confirm_config");
        Map<String, Object> config = new LinkedHashMap<>();
        if (rawConfig instanceof Map<?, ?> map) {
            map.forEach((mapKey, value) -> config.put(String.valueOf(mapKey), value));
        }
        config.put(key, true);
        ctx.put("auto_confirm_config", config);
    }

    private static boolean booleanValue(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Map<String, Object> toConfigMap(PermissionsSection config) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (config == null) {
            return result;
        }
        if (config.getEnabled() != null) {
            result.put("enabled", config.getEnabled());
        }
        if (config.getSchema() != null) {
            result.put("schema", config.getSchema());
        }
        if (config.getDefaults() != null) {
            result.put("defaults", new LinkedHashMap<>(config.getDefaults()));
        }
        if (config.getTools() != null) {
            result.put("tools", new LinkedHashMap<>(config.getTools()));
        }
        if (config.getRules() != null) {
            List<Map<String, Object>> rules = new ArrayList<>();
            for (Map<String, Object> rule : config.getRules()) {
                rules.add(rule == null ? null : new LinkedHashMap<>(rule));
            }
            result.put("rules", rules);
        }
        if (config.getApprovalOverrides() != null) {
            List<Map<String, Object>> overrides = new ArrayList<>();
            config.getApprovalOverrides().forEach(entry -> {
                Map<String, Object> override = new LinkedHashMap<>();
                override.put("id", entry.getId());
                override.put("tools", entry.getTools());
                override.put("match_type", entry.getMatchType());
                override.put("pattern", entry.getPattern());
                override.put("action", entry.getAction());
                overrides.add(override);
            });
            result.put("approval_overrides", overrides);
        }
        if (config.getExternalDirectory() != null) {
            result.put("external_directory", new LinkedHashMap<>(config.getExternalDirectory()));
        }
        result.putAll(config.getExtensions());
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (source == null) {
            return copy;
        }
        source.forEach((key, value) -> {
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> nested = new LinkedHashMap<>();
                map.forEach((nestedKey, nestedValue) -> nested.put(String.valueOf(nestedKey), nestedValue));
                copy.put(key, deepCopyMap(nested));
            } else if (value instanceof List<?> list) {
                copy.put(key, new ArrayList<>(list));
            } else {
                copy.put(key, value);
            }
        });
        return copy;
    }
}
