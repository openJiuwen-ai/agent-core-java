/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security.fileguard;

import com.openjiuwen.harness.security.PermissionLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 *
 * @since 0.1.15
 */
public final class FileGuardConfigNormalizer {

    private static final Logger logger = LoggerFactory.getLogger(FileGuardConfigNormalizer.class);

    private static final Pattern ENV_BRACE = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private static final Pattern ENV_PLAIN = Pattern.compile("\\$([A-Za-z_][A-Za-z0-9_]*)");

    private FileGuardConfigNormalizer() {
    }

    /**
     * Compile the permissions map into an effective file-guard config.
     *
     * @param permissions  raw permissions map (may be {@code null})
     * @param workspaceRoot runtime workspace root (may be {@code null})
     * @param trustedDirs  trusted directories projected to allow-prefix rules
     * @return the compiled config, or {@code null} when the file-guard layer is disabled
     * @since 0.1.15
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

        Boolean explicit = explicitEnabled(fg);
        boolean enabled;
        if (explicit != null) {
            enabled = explicit;
        } else if (hasExt || hasTrusted) {
            enabled = true;
        } else {
            enabled = false;
        }
        if (!enabled) {
            return null;
        }

        Path ws = workspaceRoot;
        boolean nativeMode = hasNativeFileGuard(fg, perms);
        if (nativeMode) {
            return normalizeNative(fg, ext, ws, trusted);
        }
        return normalizeLegacy(ext, ws, trusted, getList(fg, "paths"));
    }

    // ---------- native branch ----------

    private static EffectiveFileGuardConfig normalizeNative(Map<String, Object> fg, Object ext,
                                                            Path workspaceRoot, List<String> trustedDirs) {
        Map<String, Object> rawDefaults = fg.get("defaults") instanceof Map<?, ?> ? toStringKeyMap(fg.get("defaults")) : new LinkedHashMap<>();
        boolean rawHasAxis = hasAxisDict(rawDefaults);
        Map<FileGuardAction, PermissionLevel> defaults = new EnumMap<>(FileGuardAction.class);
        if (!rawHasAxis && ext instanceof Map<?, ?>) {
            Object star = toStringKeyMap(ext).getOrDefault("*", "ask");
            fillAxisFromStar(defaults, star);
        } else if (!rawHasAxis && ext instanceof String s && !s.isBlank()) {
            fillAxisFromStar(defaults, s);
        } else {
            defaults.put(FileGuardAction.READ, parseLevel(rawDefaults.get("read"), PermissionLevel.ASK));
            defaults.put(FileGuardAction.WRITE, parseLevel(rawDefaults.get("write"), PermissionLevel.ASK));
            defaults.put(FileGuardAction.EXEC, parseLevel(rawDefaults.get("exec"), PermissionLevel.ASK));
        }

        List<FileGuardPathRule> rules = new ArrayList<>();
        Object rawPaths = fg.get("paths");
        if (rawPaths instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> im)) {
                    continue;
                }
                Map<String, Object> entry = toStringKeyMap(item);
                Object pathV = entry.get("path");
                if (!(pathV instanceof String pvs) || pvs.isBlank()) {
                    continue;
                }
                Object matchV = entry.getOrDefault("match", "prefix");
                String match = "glob".equals(String.valueOf(matchV)) ? "glob" : "prefix";
                FileGuardPathRule rule = compilePathEntry(pvs,
                        entry.get("read"), entry.get("write"), entry.get("exec"),
                        match, PermissionLevel.ASK);
                if (rule != null) {
                    rules.add(rule);
                }
            }
        }

        FileGuardPathRule wsRule = compileWorkspaceAxisRule(fg.get("workspace"), workspaceRoot);
        if (wsRule != null) {
            rules.add(wsRule);
        }

        for (String td : trustedDirs) {
            FileGuardPathRule rule = compilePathEntry(td,
                    "allow", "allow", "ask", "prefix", PermissionLevel.ASK);
            if (rule != null) {
                rules.add(rule);
            }
        }

        if (ext instanceof Map<?, ?> extMap) {
            java.util.Set<String> existing = new java.util.HashSet<>();
            for (FileGuardPathRule r : rules) {
                if ("prefix".equals(r.getMatch())) {
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
                FileGuardPathRule rule;
                if ("allow".equals(action)) {
                    rule = compilePathEntry(key, "allow", "allow", "ask", "prefix", PermissionLevel.ASK);
                } else {
                    rule = compilePathEntry(key, action, action, action, "prefix", PermissionLevel.ASK);
                }
                if (rule != null) {
                    rules.add(rule);
                    logger.warn("[file_guard] migrate.external_directory key={} action={} -> file_guard.paths",
                            key, action);
                }
            }
        }

        return EffectiveFileGuardConfig.builder()
                .defaults(defaults)
                .rules(rules)
                .workspaceRoot(workspaceRoot)
                .trustedDirs(new ArrayList<>(trustedDirs))
                .build();
    }

    // ---------- legacy branch ----------

    private static EffectiveFileGuardConfig normalizeLegacy(Object ext, Path workspaceRoot,
                                                            List<String> trustedDirs,
                                                            List<Map<String, Object>> fileGuardPaths) {
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

        List<FileGuardPathRule> rules = new ArrayList<>();
        if (workspaceRoot != null) {
            FileGuardPathRule wsRule = compilePathEntry(workspaceRoot.toString(),
                    "allow", "allow", "allow", "prefix", PermissionLevel.ASK);
            if (wsRule != null) {
                rules.add(wsRule);
            }
        }
        for (String td : trustedDirs) {
            FileGuardPathRule rule = compilePathEntry(td,
                    "allow", "allow", "ask", "prefix", PermissionLevel.ASK);
            if (rule != null) {
                rules.add(rule);
            }
        }
        for (Map.Entry<String, String> kv : allowPrefixes) {
            FileGuardPathRule rule;
            if ("allow".equals(kv.getValue())) {
                rule = compilePathEntry(kv.getKey(), "allow", "allow", "ask", "prefix", PermissionLevel.ASK);
            } else {
                rule = compilePathEntry(kv.getKey(), kv.getValue(), kv.getValue(), kv.getValue(),
                        "prefix", PermissionLevel.ASK);
            }
            if (rule != null) {
                rules.add(rule);
            }
        }
        if (fileGuardPaths != null) {
            for (Map<String, Object> item : fileGuardPaths) {
                if ("glob".equals(String.valueOf(item.get("match")))) {
                    continue;
                }
                Object pathV = item.get("path");
                if (!(pathV instanceof String pvs) || pvs.isBlank()) {
                    continue;
                }
                FileGuardPathRule rule = compilePathEntry(pvs,
                        item.getOrDefault("read", "allow"),
                        item.getOrDefault("write", "allow"),
                        item.getOrDefault("exec", "ask"),
                        "prefix", PermissionLevel.ASK);
                if (rule != null) {
                    rules.add(rule);
                }
            }
        }

        return EffectiveFileGuardConfig.builder()
                .defaults(defaults)
                .rules(rules)
                .workspaceRoot(workspaceRoot)
                .trustedDirs(new ArrayList<>(trustedDirs))
                .build();
    }

    // ---------- path entry compilation ----------

    private static FileGuardPathRule compilePathEntry(String pathRaw, Object read, Object write,
                                                     Object exec, String match,
                                                     PermissionLevel defaultLevel) {
        String pathS = pathRaw.trim();
        if (pathS.isEmpty() || "*".equals(pathS)) {
            return null;
        }
        PermissionLevel r = read != null ? parseLevel(read, defaultLevel) : defaultLevel;
        PermissionLevel w = write != null ? parseLevel(write, defaultLevel) : defaultLevel;
        PermissionLevel e = exec != null ? parseLevel(exec, defaultLevel) : defaultLevel;
        PermissionLevel[] impl = applyImplications(r, w, e, pathS);
        r = impl[0];
        w = impl[1];
        e = impl[2];
        if ("prefix".equals(match)) {
            String norm = pathS.replace("\\", "/");
            if (!norm.contains("/")) {
                return null;
            }
            pathS = posixNorm(expandRaw(pathS));
            if (!pathS.replaceAll("/+$", "").contains("/")) {
                return null;
            }
        }
        return FileGuardPathRule.builder()
                .path(pathS)
                .read(r)
                .write(w)
                .exec(e)
                .match(match)
                .build();
    }

    private static FileGuardPathRule compileWorkspaceAxisRule(Object workspaceCfg, Path workspaceRoot) {
        if (!hasAxisDict(workspaceCfg)) {
            return null;
        }
        if (workspaceRoot == null) {
            logger.warn("[file_guard] workspace.rule_skipped reason=no_workspace_root "
                    + "(file_guard.workspace is set but workspace_root was not resolved)");
            return null;
        }
        if (!(workspaceCfg instanceof Map<?, ?> wsMap)) {
            return null;
        }
        Map<String, Object> ws = toStringKeyMap(wsMap);
        return compilePathEntry(workspaceRoot.toString(),
                ws.get("read"), ws.get("write"), ws.get("exec"),
                "prefix", PermissionLevel.ASK);
    }

    /**
     * Write/Exec&#x21d2;Read forward implication (compile time).
     *
     * <p>When {@code write} or {@code exec} is ALLOW and {@code read} is not DENY, the
     * read axis is elevated to ALLOW (writing/executing implies reading). An explicit
     * {@code read=deny} wins and is preserved with a warning, mirroring Python
     * {@code _apply_implications}.
     */
    private static PermissionLevel[] applyImplications(PermissionLevel read, PermissionLevel write,
                                                      PermissionLevel exec, String pathLabel) {
        if (write == PermissionLevel.ALLOW || exec == PermissionLevel.ALLOW) {
            if (read == PermissionLevel.DENY) {
                logger.warn("[file_guard] implication.conflict path={} write={} exec={} read=deny "
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
            if (item instanceof Map<?, ?> pm && "glob".equals(pm.get("match"))) {
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

    private static Boolean explicitEnabled(Map<String, Object> fg) {
        if (!fg.containsKey("enabled")) {
            return null;
        }
        Object v = fg.get("enabled");
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(v).trim());
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
            if (s.equals("~")) {
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
        s = replaceEnv(s, ENV_BRACE, 1);
        s = replaceEnv(s, ENV_PLAIN, 1);
        return s;
    }

    private static String replaceEnv(String s, Pattern pattern, int nameGroup) {
        Matcher m = pattern.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String name = m.group(nameGroup);
            String val = System.getenv(name);
            m.appendReplacement(sb, Matcher.quoteReplacement(val != null ? val : ""));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> getList(Map<String, Object> fg, String key) {
        Object v = fg.get(key);
        if (!(v instanceof List<?> list)) {
            return null;
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
