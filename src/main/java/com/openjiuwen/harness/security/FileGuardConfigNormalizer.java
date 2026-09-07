/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiles the raw {@code permissions} map into an {@link EffectiveFileGuardConfig}.
 *
 * <p>Mirrors Python {@code file_guard.normalize_path_guard_config}. It reads
 * {@code permissions.file_guard}; the native branch ({@code defaults} + {@code paths[]})
 * and the legacy branch (projecting {@code external_directory} into path rules) both
 * produce a flat rule list. {@code workspace} and {@code trusted_dirs} are projected
 * to allow-prefix rules. When the layer is disabled or absent the method returns
 * {@code null} so {@link FileGuardChecker#build} can skip Pipeline B entirely.
 *
 * <p>Deviation from Python: path normalization is lexical (separator collapse) rather
 * than filesystem {@code resolve()}, because the Java port must keep literal posix
 * paths such as {@code "/etc/hosts"} stable across operating systems and must not
 * require the target to exist on disk.
 */
public final class FileGuardConfigNormalizer {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileGuardConfigNormalizer.class);

    private static final Pattern ENV_BRACE = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private static final Pattern ENV_PLAIN = Pattern.compile("\\$([A-Za-z_][A-Za-z0-9_]*)");

    private FileGuardConfigNormalizer() {
    }

    /**
     * Compile the permissions map into an effective file-guard config.
     *
     * @param permissions   raw permissions map (may be {@code null})
     * @param workspaceRoot runtime workspace root (may be {@code null})
     * @param trustedDirs   trusted directories projected to allow-prefix rules
     * @return the compiled config, or {@code null} when the file-guard layer is disabled
     */
    public static EffectiveFileGuardConfig normalize(Map<String, Object> permissions,
            Path workspaceRoot, List<String> trustedDirs) {
        Map<String, Object> perms = permissions != null ? permissions : Map.of();
        Object fgRaw = perms.get("file_guard");
        Map<String, Object> fg = fgRaw instanceof Map<?, ?> ? toStringKeyMap(fgRaw) : new LinkedHashMap<>();
        List<String> trusted = trustedDirs != null ? trustedDirs : List.of();
        Object ext = perms.get("external_directory");
        boolean hasExt = (ext instanceof Map<?, ?> m && !m.isEmpty())
                || (ext instanceof String s && !s.isBlank());
        boolean hasTrusted = !trusted.isEmpty();

        Optional<Boolean> explicitOpt = explicitEnabled(fg);
        boolean isEnabled;
        if (explicitOpt.isPresent()) {
            isEnabled = explicitOpt.get();
        } else if (hasExt || hasTrusted) {
            isEnabled = true;
        } else {
            isEnabled = false;
        }
        if (!isEnabled) {
            return null;
        }

        boolean isNativeMode = hasNativeFileGuard(fg, perms);
        if (isNativeMode) {
            return normalizeNative(fg, ext, workspaceRoot, trusted);
        }
        return normalizeLegacy(ext, workspaceRoot, trusted, getList(fg, "paths"));
    }

    // ---------- native branch ----------

    private static EffectiveFileGuardConfig normalizeNative(Map<String, Object> fg, Object ext,
            Path workspaceRoot, List<String> trustedDirs) {
        Map<String, Object> rawDefaults = fg.get("defaults") instanceof Map<?, ?>
                ? toStringKeyMap(fg.get("defaults"))
                : new LinkedHashMap<>();
        boolean hasRawAxis = hasAxisDict(rawDefaults);
        Map<FileGuardAction, PermissionLevel> defaults = new EnumMap<>(FileGuardAction.class);
        if (!hasRawAxis && ext instanceof Map<?, ?>) {
            Object star = toStringKeyMap(ext).getOrDefault("*", "ask");
            fillAxisFromStar(defaults, star);
        } else if (!hasRawAxis && ext instanceof String s && !s.isBlank()) {
            fillAxisFromStar(defaults, s);
        } else {
            defaults.put(FileGuardAction.READ, parseLevel(rawDefaults.get("read"), PermissionLevel.ASK));
            defaults.put(FileGuardAction.WRITE, parseLevel(rawDefaults.get("write"), PermissionLevel.ASK));
            defaults.put(FileGuardAction.EXEC, parseLevel(rawDefaults.get("exec"), PermissionLevel.ASK));
        }

        List<FileGuardPathRule> rules = new ArrayList<>();
        compileNativePaths(fg, rules);
        compileWorkspaceAxisRule(fg.get("workspace"), workspaceRoot).ifPresent(rules::add);
        for (String td : trustedDirs) {
            compilePathEntry(td, "allow", "allow", "ask", FileGuardPathRule.MATCH_PREFIX).ifPresent(rules::add);
        }
        migrateExternalDirectory(ext, rules);

        return new EffectiveFileGuardConfig(defaults, rules, workspaceRoot, new ArrayList<>(trustedDirs));
    }

    private static void compileNativePaths(Map<String, Object> fg, List<FileGuardPathRule> rules) {
        Object rawPaths = fg.get("paths");
        if (!(rawPaths instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> entry = toStringKeyMap(item);
            Object pathV = entry.get("path");
            if (!(pathV instanceof String pvs) || pvs.isBlank()) {
                continue;
            }
            Object matchV = entry.getOrDefault("match", FileGuardPathRule.MATCH_PREFIX);
            String match = FileGuardPathRule.MATCH_GLOB.equals(String.valueOf(matchV))
                    ? FileGuardPathRule.MATCH_GLOB : FileGuardPathRule.MATCH_PREFIX;
            compilePathEntry(pvs, entry.get("read"), entry.get("write"), entry.get("exec"), match)
                    .ifPresent(rules::add);
        }
    }

    private static void migrateExternalDirectory(Object ext, List<FileGuardPathRule> rules) {
        if (!(ext instanceof Map<?, ?>)) {
            return;
        }
        Set<String> existing = new HashSet<>();
        for (FileGuardPathRule r : rules) {
            if (FileGuardPathRule.MATCH_PREFIX.equals(r.getMatch())) {
                existing.add(r.getPath().replace("\\", "/"));
            }
        }
        for (Map.Entry<String, Object> kv : toStringKeyMap(ext).entrySet()) {
            String key = kv.getKey();
            Object actionObj = kv.getValue();
            if ("*".equals(key) || !(actionObj instanceof String action)) {
                continue;
            }
            if (!isLevelName(action)) {
                continue;
            }
            String keyNorm = posixNorm(expandRaw(key));
            if (existing.contains(keyNorm)) {
                continue;
            }
            Optional<FileGuardPathRule> ruleOpt = "allow".equals(action)
                    ? compilePathEntry(key, "allow", "allow", "ask", FileGuardPathRule.MATCH_PREFIX)
                    : compilePathEntry(key, action, action, action, FileGuardPathRule.MATCH_PREFIX);
            ruleOpt.ifPresent(rule -> {
                rules.add(rule);
                LOGGER.warn("[file_guard] migrate.external_directory key={} action={} -> file_guard.paths",
                        key, action);
            });
        }
    }

    // ---------- legacy branch ----------

    private static EffectiveFileGuardConfig normalizeLegacy(Object ext, Path workspaceRoot,
            List<String> trustedDirs, List<Map<String, Object>> fileGuardPaths) {
        Object starAction = "ask";
        List<Map.Entry<String, String>> allowPrefixes = new ArrayList<>();
        if (ext instanceof String s && !s.isBlank()) {
            starAction = s;
        } else if (ext instanceof Map<?, ?>) {
            Map<String, Object> extStr = toStringKeyMap(ext);
            starAction = extStr.getOrDefault("*", "ask");
            for (Map.Entry<String, Object> kv : extStr.entrySet()) {
                String key = kv.getKey();
                Object action = kv.getValue();
                if ("*".equals(key)) {
                    continue;
                }
                if (!(action instanceof String a) || !isLevelName(a)) {
                    continue;
                }
                allowPrefixes.add(Map.entry(key, a));
            }
        }

        Map<FileGuardAction, PermissionLevel> defaults = new EnumMap<>(FileGuardAction.class);
        fillAxisFromStar(defaults, starAction);

        List<FileGuardPathRule> rules = buildLegacyRules(workspaceRoot, trustedDirs,
                allowPrefixes, fileGuardPaths);

        return new EffectiveFileGuardConfig(defaults, rules, workspaceRoot, new ArrayList<>(trustedDirs));
    }

    private static List<FileGuardPathRule> buildLegacyRules(Path workspaceRoot, List<String> trustedDirs,
            List<Map.Entry<String, String>> allowPrefixes, List<Map<String, Object>> fileGuardPaths) {
        List<FileGuardPathRule> rules = new ArrayList<>();
        if (workspaceRoot != null) {
            compilePathEntry(workspaceRoot.toString(), "allow", "allow", "allow", FileGuardPathRule.MATCH_PREFIX)
                    .ifPresent(rules::add);
        }
        for (String td : trustedDirs) {
            compilePathEntry(td, "allow", "allow", "ask", FileGuardPathRule.MATCH_PREFIX).ifPresent(rules::add);
        }
        for (Map.Entry<String, String> kv : allowPrefixes) {
            Optional<FileGuardPathRule> ruleOpt = "allow".equals(kv.getValue())
                    ? compilePathEntry(kv.getKey(), "allow", "allow", "ask", FileGuardPathRule.MATCH_PREFIX)
                    : compilePathEntry(kv.getKey(), kv.getValue(), kv.getValue(), kv.getValue(),
                            FileGuardPathRule.MATCH_PREFIX);
            ruleOpt.ifPresent(rules::add);
        }
        if (fileGuardPaths != null) {
            for (Map<String, Object> item : fileGuardPaths) {
                if (FileGuardPathRule.MATCH_GLOB.equals(String.valueOf(item.get("match")))) {
                    continue;
                }
                Object pathV = item.get("path");
                if (!(pathV instanceof String pvs) || pvs.isBlank()) {
                    continue;
                }
                compilePathEntry(pvs,
                        item.getOrDefault("read", "allow"),
                        item.getOrDefault("write", "allow"),
                        item.getOrDefault("exec", "ask"),
                        FileGuardPathRule.MATCH_PREFIX).ifPresent(rules::add);
            }
        }
        return rules;
    }

    // ---------- path entry compilation ----------

    private static Optional<FileGuardPathRule> compilePathEntry(String pathRaw, Object read, Object write,
            Object exec, String match) {
        String pathS = pathRaw.trim();
        if (pathS.isEmpty() || "*".equals(pathS)) {
            return Optional.empty();
        }
        PermissionLevel r = read != null ? parseLevel(read, PermissionLevel.ASK) : PermissionLevel.ASK;
        PermissionLevel w = write != null ? parseLevel(write, PermissionLevel.ASK) : PermissionLevel.ASK;
        PermissionLevel e = exec != null ? parseLevel(exec, PermissionLevel.ASK) : PermissionLevel.ASK;
        PermissionLevel[] impl = applyImplications(r, w, e, pathS);
        r = impl[0];
        w = impl[1];
        e = impl[2];
        if (FileGuardPathRule.MATCH_PREFIX.equals(match)) {
            String norm = pathS.replace("\\", "/");
            if (!norm.contains("/")) {
                return Optional.empty();
            }
            pathS = posixNorm(expandRaw(pathS));
            if (!pathS.replaceAll("/+$", "").contains("/")) {
                return Optional.empty();
            }
        }
        return Optional.of(new FileGuardPathRule(pathS, r, w, e, match));
    }

    private static Optional<FileGuardPathRule> compileWorkspaceAxisRule(Object workspaceCfg, Path workspaceRoot) {
        if (!hasAxisDict(workspaceCfg)) {
            return Optional.empty();
        }
        if (workspaceRoot == null) {
            LOGGER.warn("[file_guard] workspace.rule_skipped reason=no_workspace_root "
                    + "(file_guard.workspace is set but workspace_root was not resolved)");
            return Optional.empty();
        }
        if (!(workspaceCfg instanceof Map<?, ?> wsMap)) {
            return Optional.empty();
        }
        Map<String, Object> ws = toStringKeyMap(wsMap);
        return compilePathEntry(workspaceRoot.toString(),
                ws.get("read"), ws.get("write"), ws.get("exec"),
                FileGuardPathRule.MATCH_PREFIX);
    }

    /**
     * Write/Exec&#x21d2;Read forward implication (compile time).
     *
     * <p>When {@code write} or {@code exec} is ALLOW and {@code read} is not DENY, the
     * read axis is elevated to ALLOW (writing/executing implies reading). An explicit
     * {@code read=deny} wins and is preserved with a warning, mirroring Python
     * {@code _apply_implications}.
     *
     * @param read      the read axis level
     * @param write     the write axis level
     * @param exec      the exec axis level
     * @param pathLabel the path being compiled (for diagnostics)
     * @return the implied {@code [read, write, exec]} levels
     */
    private static PermissionLevel[] applyImplications(PermissionLevel read, PermissionLevel write,
            PermissionLevel exec, String pathLabel) {
        if (write == PermissionLevel.ALLOW || exec == PermissionLevel.ALLOW) {
            if (read == PermissionLevel.DENY) {
                LOGGER.warn("[file_guard] implication.conflict path={} write={} exec={} read=deny "
                        + "(explicit deny wins over Write/Exec=>Read)", pathLabel, write, exec);
                return new PermissionLevel[]{read, write, exec};
            }
            return new PermissionLevel[]{PermissionLevel.ALLOW, write, exec};
        }
        return new PermissionLevel[]{read, write, exec};
    }

    // ---------- helpers ----------

    private static boolean hasNativeFileGuard(Map<String, Object> fg, Map<String, Object> perms) {
        if (hasAxisDict(fg.get("defaults")) || hasAxisDict(fg.get("workspace"))) {
            return true;
        }
        Object paths = fg.get("paths");
        if (!(paths instanceof List<?> list) || list.isEmpty()) {
            return false;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> pm
                    && FileGuardPathRule.MATCH_GLOB.equals(String.valueOf(pm.get("match")))) {
                return true;
            }
        }
        Object ext = perms.get("external_directory");
        boolean hasExt = (ext instanceof Map<?, ?> m && !m.isEmpty())
                || (ext instanceof String s && !s.isBlank());
        if (hasExt) {
            return false;
        }
        return true;
    }

    private static boolean hasAxisDict(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return false;
        }
        return map.containsKey("read") || map.containsKey("write") || map.containsKey("exec");
    }

    private static Optional<Boolean> explicitEnabled(Map<String, Object> fg) {
        if (!fg.containsKey("enabled")) {
            return Optional.empty();
        }
        Object v = fg.get("enabled");
        if (v == null) {
            return Optional.of(false);
        }
        if (v instanceof Boolean) {
            return Optional.of((Boolean) v);
        }
        return Optional.of(Boolean.parseBoolean(String.valueOf(v).trim()));
    }

    private static void fillAxisFromStar(Map<FileGuardAction, PermissionLevel> out, Object action) {
        PermissionLevel level = parseLevel(action, PermissionLevel.ASK);
        out.put(FileGuardAction.READ, level);
        out.put(FileGuardAction.WRITE, level);
        out.put(FileGuardAction.EXEC, level);
    }

    private static PermissionLevel parseLevel(Object raw, PermissionLevel def) {
        if (raw == null) {
            return def;
        }
        if (raw instanceof PermissionLevel pl) {
            return pl;
        }
        String v = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "allow" -> PermissionLevel.ALLOW;
            case "ask" -> PermissionLevel.ASK;
            case "deny" -> PermissionLevel.DENY;
            default -> def;
        };
    }

    private static boolean isLevelName(Object raw) {
        if (!(raw instanceof String s)) {
            return false;
        }
        String v = s.trim().toLowerCase(Locale.ROOT);
        return "allow".equals(v) || "ask".equals(v) || "deny".equals(v);
    }

    private static String posixNorm(String p) {
        String s = p.replace("\\", "/");
        s = s.replaceAll("/+", "/");
        if (s.length() > 1 && s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String expandRaw(String raw) {
        String s = raw.trim();
        if (s.startsWith("~")) {
            String home = System.getProperty("user.home", "");
            if ("~".equals(s)) {
                s = home;
            } else if (s.startsWith("~/")) {
                s = home + s.substring(1);
            }
        }
        s = expandVars(s);
        return s;
    }

    private static String expandVars(String s) {
        if (s.indexOf('$') < 0) {
            return s;
        }
        String expanded = replaceEnv(s, ENV_BRACE);
        expanded = replaceEnv(expanded, ENV_PLAIN);
        return expanded;
    }

    private static String replaceEnv(String s, Pattern pattern) {
        Matcher m = pattern.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String val = System.getenv(name);
            m.appendReplacement(sb, Matcher.quoteReplacement(val != null ? val : ""));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static Map<String, Object> toStringKeyMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return out;
    }

    private static List<Map<String, Object>> getList(Map<String, Object> fg, String key) {
        Object v = fg.get(key);
        if (!(v instanceof List<?> list)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?>) {
                out.add(toStringKeyMap(item));
            }
        }
        return out;
    }
}
