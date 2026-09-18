/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Compiled, read-only view of the file-guard configuration consumed by
 * {@link FileGuardChecker}.
 *
 * <p>Mirrors Python {@code file_guard.EffectiveFileGuardConfig}, flattened for the
 * Java parity port: the Python {@code enabled}/{@code mode} flags are represented by
 * absence (a disabled layer yields {@code null} from the normalizer, so the checker
 * is never built) and a single unified extraction path. {@code defaults} is keyed by
 * axis so the checker can look up the fallback level for an unmatched access.
 *
 * @since 2026-01-01
 */
public final class EffectiveFileGuardConfig {

    private final Map<FileGuardAction, PermissionLevel> defaults;
    private final List<FileGuardPathRule> rules;
    private final Path workspaceRoot;
    private final List<String> trustedDirs;

    public EffectiveFileGuardConfig(Map<FileGuardAction, PermissionLevel> defaults, List<FileGuardPathRule> rules,
            Path workspaceRoot, List<String> trustedDirs) {
        this.defaults = defaults == null
                ? new EnumMap<>(FileGuardAction.class)
                : new EnumMap<>(defaults);
        this.rules = rules == null ? List.of() : List.copyOf(rules);
        this.workspaceRoot = workspaceRoot;
        this.trustedDirs = trustedDirs == null ? List.of() : List.copyOf(trustedDirs);
    }

    /**
     * Per-axis fallback levels applied when no rule matches.
     *
     * @return defaults keyed by file-guard action
     */
    public Map<FileGuardAction, PermissionLevel> getDefaults() {
        return defaults;
    }

    /**
     * Compiled path rules, in evaluation order.
     *
     * @return copy of compiled rules
     */
    public List<FileGuardPathRule> getRules() {
        return new ArrayList<>(rules);
    }

    /**
     * Workspace root used to resolve relative paths and bind {@code file_guard.workspace}.
     *
     * @return workspace root, or {@code null}
     */
    public Path getWorkspaceRoot() {
        return workspaceRoot;
    }

    /**
     * Trusted directories projected to allow-prefix rules.
     *
     * @return trusted directory paths
     */
    public List<String> getTrustedDirs() {
        return trustedDirs;
    }
}
