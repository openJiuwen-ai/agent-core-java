/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import com.openjiuwen.harness.security.fileguard.FileGuardChecker;
import com.openjiuwen.harness.security.tiered.TieredPolicy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Permission engine applying the dual-pipeline strictest merge.
 *
 * <p>Mirrors Python {@code openjiuwen.harness.security.core.PermissionEngine}. Each
 * tool call is evaluated by Pipeline A ({@link TieredPolicy}) and, when the
 * file-guard layer is enabled, Pipeline B ({@link FileGuardChecker}). The final
 * level is the strictest of both ({@code DENY < ASK < ALLOW}); a {@code null}
 * pipeline result does not raise the other. The {@code enabled=false} flag
 * short-circuits {@link #checkPermission} to ALLOW, while
 * {@link #evaluateGlobalPolicyDirectly} ignores the flag to expose the raw tiered
 * decision (aligning with Python {@code evaluate_global_policy_directly}, which
 * returns {@code None} for the no-config fallback).
 *
 * @since 0.1.7
 */
public class PermissionEngine {
    private static final Logger logger = LoggerFactory.getLogger(PermissionEngine.class);

    private static final String DISABLED_RULE = "disabled";
    private static final String FALLBACK_RULE = "tiered_policy:fallback(no_config)";
    private static final String FILE_GUARD_RULE = "file_guard";

    private final Map<String, Object> config;
    private final Path workspaceRoot;
    private final List<String> trustedDirs;
    private final FileGuardChecker fileGuard;

    /**
     * Build an engine with explicit workspace root and trusted directories.
     *
     * <p>The file-guard checker is compiled once at construction time via
     * {@link FileGuardChecker#build}; when the layer is disabled or absent the
     * reference is {@code null} and Pipeline B is skipped.
     *
     * @param config        permissions config map
     * @param workspaceRoot runtime workspace root (may be {@code null})
     * @param trustedDirs   trusted directories projected to allow-prefix rules
     * @since 0.1.15
     */
    public PermissionEngine(Map<String, Object> config, Path workspaceRoot, List<String> trustedDirs) {
        this.config = config != null ? config : new LinkedHashMap<>();
        this.workspaceRoot = workspaceRoot;
        this.trustedDirs = trustedDirs != null ? trustedDirs : List.of();
        this.fileGuard = FileGuardChecker.build(this.config, workspaceRoot, this.trustedDirs);
    }

    /**
     * Backwards-compatible two-argument constructor delegating to
     * {@link #PermissionEngine(Map, Path, List)} with no trusted directories.
     *
     * @param config        config
     * @param workspaceRoot workspaceRoot
     * @since 0.1.7
     */
    public PermissionEngine(Map<String, Object> config, Path workspaceRoot) {
        this(config, workspaceRoot, List.of());
    }

    /**
     * Evaluate the raw global policy ignoring the {@code enabled} flag.
     *
     * <p>Aligns with Python {@code evaluate_global_policy_directly}: runs
     * {@link TieredPolicy#evaluate} and, when file-guard is configured, merges
     * {@link FileGuardChecker#evaluate}. The no-config fallback yields a
     * {@code null} level so callers can distinguish "no rule matched" from an
     * explicit {@code ASK}.
     *
     * @param toolName tool name
     * @param toolArgs tool arguments
     * @return entry of {@link PermissionLevel} (may be {@code null}) and matched rule (may be {@code null})
     * @since 0.1.7
     */
    public Map.Entry<PermissionLevel, String> evaluateGlobalPolicyDirectly(String toolName,
            Map<String, Object> toolArgs) {
        PermissionResult pipelineA = TieredPolicy.evaluate(config, toolName, toolArgs);
        PermissionLevel level = pipelineA.getPermission();
        String matchedRule = pipelineA.getMatchedRule();
        if (FALLBACK_RULE.equals(matchedRule)) {
            level = null;
            matchedRule = null;
        }
        if (fileGuard != null) {
            PermissionResult pipelineB = fileGuard.evaluate(toolName, toolArgs);
            if (pipelineB != null) {
                String bRule = pipelineB.getMatchedRule() != null ? pipelineB.getMatchedRule() : FILE_GUARD_RULE;
                if (level == null) {
                    level = pipelineB.getPermission();
                    matchedRule = bRule;
                } else {
                    level = strictest(level, pipelineB.getPermission());
                    matchedRule = matchedRule + "|" + bRule;
                }
                logger.debug("[PermissionEngine] direct.file_guard.merged tool={} level={} matched_rule={}",
                        toolName, level, matchedRule);
            }
        }
        return new AbstractMap.SimpleImmutableEntry<>(level, matchedRule);
    }

    /**
     * Check a tool call permission applying the dual-pipeline strictest merge.
     *
     * <p>When the permissions system is disabled ({@code enabled=false}) the call
     * is allowed without further evaluation. Otherwise Pipeline A
     * ({@link TieredPolicy}) and Pipeline B ({@link FileGuardChecker}, when
     * configured) are merged with {@code strictest}; the matched rules are joined
     * as {@code A.rule|B.rule}. {@code needsApproval} is derived from the final
     * level being {@link PermissionLevel#ASK}.
     *
     * @param toolName tool name
     * @param toolArgs tool arguments
     * @return the check result with permission, matched rule and approval flag
     * @since 0.1.7
     */
    public PermissionCheckResult checkPermission(String toolName, Map<String, Object> toolArgs) {
        if (!isEnabled()) {
            logger.debug("[PermissionEngine] permission.check.skip reason=system_disabled decision=allow tool={}",
                    toolName);
            return PermissionCheckResult.builder()
                    .permission(PermissionLevel.ALLOW)
                    .matchedRule(DISABLED_RULE)
                    .needsApproval(false)
                    .build();
        }
        PermissionResult pipelineA = TieredPolicy.evaluate(config, toolName, toolArgs);
        PermissionLevel level = pipelineA.getPermission();
        String matchedRule = pipelineA.getMatchedRule();
        if (fileGuard != null) {
            PermissionResult pipelineB = fileGuard.evaluate(toolName, toolArgs);
            if (pipelineB != null) {
                String bRule = pipelineB.getMatchedRule() != null ? pipelineB.getMatchedRule() : FILE_GUARD_RULE;
                level = strictest(level, pipelineB.getPermission());
                matchedRule = matchedRule + "|" + bRule;
                logger.debug("[PermissionEngine] permission.file_guard.merged tool={} level={} matched_rule={}",
                        toolName, level, matchedRule);
            }
        }
        return PermissionCheckResult.builder()
                .permission(level)
                .matchedRule(matchedRule)
                .needsApproval(level == PermissionLevel.ASK)
                .build();
    }

    /**
     * Whether the permissions system is enabled.
     *
     * <p>Aligns with Python {@code config.get("enabled", True)}: a missing key
     * defaults to enabled, while explicit booleans and the {@code "true"}/
     * {@code "false"} strings produced by YAML loaders are honored.
     *
     * @return true when enabled
     * @since 0.1.15
     */
    private boolean isEnabled() {
        Object raw = config.get("enabled");
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        if (raw instanceof String s) {
            return Boolean.parseBoolean(s.trim());
        }
        return true;
    }

    /**
     * Merge two permission levels with {@code DENY < ASK < ALLOW}; a {@code null}
     * operand is treated as non-participating and never raises the other.
     *
     * @param a first level (may be {@code null})
     * @param b second level (may be {@code null})
     * @return the strictest non-null level, or {@code null} when both are {@code null}
     */
    private static PermissionLevel strictest(PermissionLevel a, PermissionLevel b) {
        if (a == PermissionLevel.DENY || b == PermissionLevel.DENY) {
            return PermissionLevel.DENY;
        }
        if (a == PermissionLevel.ASK || b == PermissionLevel.ASK) {
            return PermissionLevel.ASK;
        }
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return PermissionLevel.ALLOW;
    }

    /**
     * getWorkspaceRoot.
     *
     * @return the workspace root
     * @since 0.1.7
     */
    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    /**
     * getConfig.
     *
     * @return the permissions config map
     * @since 0.1.7
     */
    public Map<String, Object> getConfig() {
        return config;
    }

    /**
     * getTrustedDirs.
     *
     * @return the trusted directories projected to file-guard allow-prefix rules
     * @since 0.1.15
     */
    public List<String> getTrustedDirs() {
        return trustedDirs;
    }

    /**
     * getFileGuard.
     *
     * @return the compiled file-guard checker, or {@code null} when the layer is disabled
     * @since 0.1.15
     */
    public FileGuardChecker getFileGuard() {
        return fileGuard;
    }
}
