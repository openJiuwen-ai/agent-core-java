/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security.fileguard;

import com.openjiuwen.harness.security.PermissionLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
 * @since 0.1.15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EffectiveFileGuardConfig {
    /** Per-axis fallback levels applied when no rule matches. */
    @Builder.Default
    private Map<FileGuardAction, PermissionLevel> defaults =
            new EnumMap<>(FileGuardAction.class);

    /** Compiled path rules, in evaluation order. */
    @Builder.Default
    private List<FileGuardPathRule> rules = new ArrayList<>();

    /** Workspace root used to resolve relative paths and bind {@code file_guard.workspace}. */
    private Path workspaceRoot;

    /** Trusted directories projected to allow-prefix rules. */
    @Builder.Default
    private List<String> trustedDirs = new ArrayList<>();
}
