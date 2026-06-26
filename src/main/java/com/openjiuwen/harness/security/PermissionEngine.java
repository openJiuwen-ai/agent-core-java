/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Mirrors Python's {@code PermissionEngine} in
 * {@code openjiuwen/harness/security/core.py}.
 */
public class PermissionEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger(PermissionEngine.class);
    private static final String TIERED_POLICY_FALLBACK = "tiered_policy:fallback(no_config)";

    /**
     * Java adaptation of Python's tuple return.
     */
    public record PermissionEvaluation(PermissionLevel permission, String matchedRule) {
    }

    private Map<String, Object> config;
    private boolean enabled;
    private BooleanSupplier permissionChecksActive;
    private Object llm;
    private String modelName;
    private final Path workspaceRoot;
    private ExternalDirectoryChecker externalChecker;

    public PermissionEngine() {
        this((Map<String, Object>) null, null, null, null);
    }

    public PermissionEngine(Map<String, Object> config) {
        this(config, null, null, null);
    }

    public PermissionEngine(PermissionsSection config) {
        this(config, null, null, null);
    }

    public PermissionEngine(
            Map<String, Object> config,
            Object llm,
            String modelName,
            Path workspaceRoot
    ) {
        this.config = normalizeConfig(config);
        this.enabled = boolOrDefault(this.config.get("enabled"), true);
        this.llm = llm;
        this.modelName = modelName;
        this.workspaceRoot = workspaceRoot;
        this.externalChecker = new ExternalDirectoryChecker(this.config, this.workspaceRoot);
    }

    public PermissionEngine(
            PermissionsSection config,
            Object llm,
            String modelName,
            Path workspaceRoot
    ) {
        this(toConfigMap(config), llm, modelName, workspaceRoot);
    }

    public void updateConfig(Map<String, Object> config) {
        this.config = normalizeConfig(config);
        this.enabled = boolOrDefault(this.config.get("enabled"), true);
        this.externalChecker = new ExternalDirectoryChecker(this.config, this.workspaceRoot);
    }

    public void updateConfig(PermissionsSection config) {
        updateConfig(toConfigMap(config));
    }

    public void updateLlm(Object llm, String modelName) {
        this.llm = llm;
        this.modelName = modelName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Object getLlm() {
        return llm;
    }

    public String getModelName() {
        return modelName;
    }

    public Map<String, Object> getConfig() {
        return new LinkedHashMap<>(config);
    }

    public void setPermissionChecksActive(BooleanSupplier permissionChecksActive) {
        this.permissionChecksActive = permissionChecksActive;
    }

    public PermissionEvaluation checkToolPermissionDirectly(String toolName, Map<String, Object> toolArgs) {
        return evaluateGlobalPolicyDirectly(toolName, toolArgs);
    }

    public PermissionEvaluation evaluateGlobalPolicyDirectly(String toolName, Map<String, Object> toolArgs) {
        return evaluateGlobalPolicyDirectly(toolName, toolArgs, true);
    }

    public PermissionEvaluation evaluateGlobalPolicyDirectly(
            String toolName,
            Map<String, Object> toolArgs,
            boolean includeExternalDirectory
    ) {
        Map<String, Object> resolvedArgs = normalizeToolArgs(toolArgs);
        TieredPolicy.PermissionDecision decision = TieredPolicy.evaluateTieredPolicy(config, toolName, resolvedArgs);
        PermissionLevel permission = decision.permission();
        String matchedRule = decision.matchedRule();
        if (Objects.equals(TIERED_POLICY_FALLBACK, matchedRule)) {
            permission = null;
            matchedRule = null;
        } else if (matchedRule != null && !TieredPolicy.matchedRuleUsesApprovalOverride(matchedRule)) {
            permission = TieredPolicy.maybeEscalateShellOperators(toolName, resolvedArgs, permission);
        }

        if (includeExternalDirectory) {
            PermissionResult externalResult = externalChecker.checkExternalPaths(toolName, resolvedArgs);
            if (externalResult != null) {
                if (permission == null) {
                    permission = externalResult.getPermission();
                    matchedRule = externalResult.getMatchedRule() != null
                            ? externalResult.getMatchedRule()
                            : "external_directory";
                } else {
                    permission = TieredPolicy.strictest(permission, externalResult.getPermission());
                    String externalRule = externalResult.getMatchedRule() != null
                            ? externalResult.getMatchedRule()
                            : "external_directory";
                    matchedRule = matchedRule + "|" + externalRule;
                }
            }
        }
        return new PermissionEvaluation(permission, matchedRule);
    }

    public PermissionResult checkPermission(String toolName, Map<String, Object> toolArgs) {
        Map<String, Object> resolvedArgs = normalizeToolArgs(toolArgs);
        LOGGER.info("permission.check.start tool={} enabled={}", toolName, enabled);

        if (!enabled) {
            LOGGER.info("permission.check.skip reason=system_disabled decision=allow");
            return new PermissionResult(
                    PermissionLevel.ALLOW,
                    null,
                    "Permission system is disabled"
            );
        }

        if (permissionChecksActive != null && !permissionChecksActive.getAsBoolean()) {
            LOGGER.info("permission.check.skip reason=permission_checks_inactive decision=allow");
            return new PermissionResult(
                    PermissionLevel.ALLOW,
                    null,
                    "Tool permission checks are inactive for this context"
            );
        }

        PermissionEvaluation policyResult = evaluateGlobalPolicyDirectly(toolName, resolvedArgs, false);
        PermissionLevel permission = policyResult.permission();
        String matchedRule = policyResult.matchedRule();
        if (permission == null) {
            permission = PermissionLevel.ASK;
            matchedRule = "default";
        }
        LOGGER.info(
                "permission.policy.result tool={} permission={} matched_rule={}",
                toolName,
                permission.value(),
                matchedRule
        );

        List<String> externalPaths = null;
        PermissionResult externalResult = externalChecker.checkExternalPaths(toolName, resolvedArgs);
        if (externalResult != null) {
            permission = TieredPolicy.strictest(permission, externalResult.getPermission());
            matchedRule = matchedRule + "|" + externalResult.getMatchedRule();
            externalPaths = externalResult.getExternalPaths();
            LOGGER.info(
                    "permission.external.result tool={} checked=true permission={} matched_rule={} external_paths={}",
                    toolName,
                    externalResult.getPermission().value(),
                    externalResult.getMatchedRule(),
                    externalPaths
            );
        } else {
            LOGGER.info("permission.external.result tool={} checked=true permission=none matched_rule=none external_paths=[]",
                    toolName);
        }

        PermissionResult result = new PermissionResult(
                permission,
                matchedRule,
                getReason(permission, toolName, matchedRule),
                externalPaths
        );
        LOGGER.info(
                "permission.check.final tool={} permission={} matched_rule={} external_paths={}",
                toolName,
                permission.value(),
                matchedRule,
                externalPaths == null ? List.of() : externalPaths
        );
        return result;
    }

    private static Map<String, Object> normalizeConfig(Map<String, Object> config) {
        return config == null ? new LinkedHashMap<>() : new LinkedHashMap<>(config);
    }

    private static Map<String, Object> normalizeToolArgs(Map<String, Object> toolArgs) {
        return toolArgs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(toolArgs);
    }

    private static boolean boolOrDefault(Object value, boolean defaultValue) {
        return value instanceof Boolean flag ? flag : defaultValue;
    }

    private static String getReason(PermissionLevel permission, String toolName, String matchedRule) {
        if (permission == PermissionLevel.ALLOW) {
            return "Allowed by rule: " + matchedRule;
        }
        if (permission == PermissionLevel.DENY) {
            return "Denied by rule: " + matchedRule;
        }
        return "Approval required for " + toolName + " (rule: " + matchedRule + ")";
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
            for (ApprovalOverrideEntry entry : config.getApprovalOverrides()) {
                if (entry == null) {
                    overrides.add(null);
                    continue;
                }
                Map<String, Object> override = new LinkedHashMap<>();
                override.put("id", entry.getId());
                override.put("tools", entry.getTools() == null ? List.of() : new ArrayList<>(entry.getTools()));
                override.put("match_type", entry.getMatchType());
                override.put("pattern", entry.getPattern());
                override.put("action", entry.getAction());
                overrides.add(override);
            }
            result.put("approval_overrides", overrides);
        }
        if (config.getExternalDirectory() != null) {
            result.put("external_directory", new LinkedHashMap<>(config.getExternalDirectory()));
        }
        result.putAll(config.getExtensions());
        return result;
    }
}
