/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security.fileguard;

import com.openjiuwen.harness.security.PermissionLevel;
import com.openjiuwen.harness.security.PermissionResult;
import com.openjiuwen.harness.security.files.PathAccessExtractor;
import com.openjiuwen.harness.security.patterns.GlobMatcher;
import com.openjiuwen.harness.security.patterns.PathMatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Path-level permission checker (Pipeline B).
 *
 * <p>Mirrors Python {@code file_guard.FileGuardChecker}. Each tool invocation is mapped
 * to one or more {@code (path, action)} accesses; per access the checker selects the
 * strictest matching rule (longest prefix wins, glob rules aggregate) and falls back
 * to the per-axis defaults when nothing matches. The overall level is the strictest
 * across accesses. When the result is ALLOW or there is no access, {@code evaluate}
 * returns {@code null} so Pipeline A is not raised.
 *
 * <p>Deviation from Python: besides the compile-time Write/Exec&#x21d2;Read implication
 * applied by {@link FileGuardConfigNormalizer}, the checker applies a resolve-time
 * backward safety implication &#x2014; a WRITE/EXEC access also honors the rule's READ
 * axis (strictest), so {@code read=deny} forces {@code write=deny}. This satisfies the
 * acceptance requirement that {@code read=deny} denies writes too.
 *
 * @since 0.1.15
 */
public final class FileGuardChecker {
    private static final Logger logger = LoggerFactory.getLogger(FileGuardChecker.class);

    private final EffectiveFileGuardConfig effective;

    private FileGuardChecker(EffectiveFileGuardConfig effective) {
        this.effective = effective;
    }

    /**
     * Build a checker from raw permissions.
     *
     * @param permissions  raw permissions map
     * @param workspaceRoot runtime workspace root
     * @param trustedDirs  trusted directories
     * @return a checker, or {@code null} when the file-guard layer is disabled
     * @since 0.1.15
     */
    public static FileGuardChecker build(Map<String, Object> permissions, Path workspaceRoot,
                                        List<String> trustedDirs) {
        EffectiveFileGuardConfig cfg = FileGuardConfigNormalizer.normalize(permissions, workspaceRoot, trustedDirs);
        if (cfg == null) {
            return null;
        }
        return new FileGuardChecker(cfg);
    }

    /**
     * Evaluate the path-level permission for a tool call.
     *
     * @param toolName tool name
     * @param toolArgs tool arguments
     * @return the permission result, or {@code null} when there is no opinion / all allow
     * @since 0.1.15
     */
    public PermissionResult evaluate(String toolName, Map<String, Object> toolArgs) {
        Path workspace = effective.getWorkspaceRoot();
        if (workspace == null) {
            logger.debug("[file_guard] skip reason=no_workspace");
            return null;
        }
        List<PathAccessExtractor.PathAccess> accesses =
                PathAccessExtractor.extractNative(toolName, toolArgs, workspace);
        if (accesses.isEmpty()) {
            return null;
        }

        PermissionLevel overall = PermissionLevel.ALLOW;
        List<String> hitExternal = new ArrayList<>();
        List<String> matchedBits = new ArrayList<>();

        for (PathAccessExtractor.PathAccess pa : accesses) {
            Resolve rr = resolveOne(pa.getPath(), pa.getAction());
            overall = strictest(overall, rr.level);
            if (rr.level != PermissionLevel.ALLOW) {
                hitExternal.add(posix(pa.getPath()));
                if (rr.ruleId != null) {
                    matchedBits.add(rr.ruleId);
                }
            }
        }

        if (overall == PermissionLevel.ALLOW) {
            return null;
        }

        String hint = hitExternal.isEmpty() ? posix(accesses.get(0).getPath()) : hitExternal.get(0);
        String reason = overall == PermissionLevel.DENY
                ? "file_guard denied: " + hint
                : "file_guard requires approval: " + hint;
        String matched = matchedBits.isEmpty() ? "file_guard" : String.join("|", matchedBits);
        return PermissionResult.builder()
                .permission(overall)
                .reason(reason)
                .matchedRule(matched)
                .externalPaths(hitExternal)
                .build();
    }

    /**
     * Resolve the level for a single access against all rules.
     *
     * @param path   the access path
     * @param action the file-access axis
     * @return the resolved level and matched rule id
     * @since 0.1.15
     */
    private Resolve resolveOne(Path path, FileGuardAction action) {
        String pathPosix = posix(path);
        FileGuardPathRule bestPrefix = null;
        int bestLen = -1;
        List<PermissionLevel> globCandidates = new ArrayList<>();

        for (FileGuardPathRule rule : effective.getRules()) {
            if ("glob".equals(rule.getMatch())) {
                if (GlobMatcher.match(rule.getPath().replace("\\", "/"), pathPosix)) {
                    globCandidates.add(resolveLevelWithImplication(rule, action));
                }
                continue;
            }
            if (matchesPrefix(rule, pathPosix, path)) {
                int len = stripTrailingSlash(rule.getPath().replace("\\", "/")).length();
                if (bestPrefix == null || len > bestLen) {
                    bestPrefix = rule;
                    bestLen = len;
                }
            }
        }

        List<PermissionLevel> candidates = new ArrayList<>();
        String ruleId = null;
        if (bestPrefix != null) {
            candidates.add(resolveLevelWithImplication(bestPrefix, action));
            ruleId = "file_guard:prefix:" + bestPrefix.getPath();
        }
        candidates.addAll(globCandidates);
        if (bestPrefix == null && !globCandidates.isEmpty()) {
            ruleId = "file_guard:glob";
        }
        if (!candidates.isEmpty()) {
            return new Resolve(strictest(candidates), ruleId);
        }
        PermissionLevel def = effective.getDefaults().getOrDefault(action, PermissionLevel.ASK);
        return new Resolve(def, "file_guard:defaults");
    }

    private boolean matchesPrefix(FileGuardPathRule rule, String pathPosix, Path path) {
        String prefix = stripTrailingSlash(rule.getPath().replace("\\", "/"));
        boolean pathMatches = pathPosix.equals(prefix) || pathPosix.startsWith(prefix + "/");
        if (!pathMatches) {
            return false;
        }
        return PathMatcher.containsPath(prefix, path.toString())
                || pathPosix.equals(prefix)
                || pathPosix.startsWith(prefix + "/");
    }

    /**
     * Resolve-time backward implication: WRITE/EXEC also honors the rule's READ axis.
     *
     * @param rule   the matched path rule
     * @param action the file-access axis
     * @return the resolved level after applying the Read-implies safety check
     * @since 0.1.15
     */
    private PermissionLevel resolveLevelWithImplication(FileGuardPathRule rule, FileGuardAction action) {
        PermissionLevel base = rule.levelFor(action)
                .orElse(effective.getDefaults().getOrDefault(action, PermissionLevel.ASK));
        if (action == FileGuardAction.WRITE || action == FileGuardAction.EXEC) {
            PermissionLevel readLevel = rule.getRead();
            if (readLevel != null) {
                return strictest(base, readLevel);
            }
        }
        return base;
    }

    private static PermissionLevel strictest(List<PermissionLevel> levels) {
        if (levels.isEmpty()) {
            return PermissionLevel.ASK;
        }
        PermissionLevel result = PermissionLevel.ALLOW;
        for (PermissionLevel level : levels) {
            if (level == PermissionLevel.DENY) {
                return PermissionLevel.DENY;
            }
            if (level == PermissionLevel.ASK) {
                result = PermissionLevel.ASK;
            }
        }
        return result;
    }

    private static PermissionLevel strictest(PermissionLevel a, PermissionLevel b) {
        return strictest(List.of(a, b));
    }

    private static String posix(Path p) {
        return p.toString().replace("\\", "/");
    }

    private static String stripTrailingSlash(String s) {
        int end = s.length();
        while (end > 1 && s.charAt(end - 1) == '/') {
            end--;
        }
        return s.substring(0, end);
    }

    private record Resolve(PermissionLevel level, String ruleId) {
    }
}
