/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Pattern matching and permission persistence utilities.
 *
 * <p>Wildcard pattern matching with shell-safe character restrictions:
 * - * → restricted character class (excludes shell metacharacters for injection prevention)
 * - ? → restricted character class (exactly one)
 * - Regex metacharacters are escaped
 * - " *" ending → (restricted*)? to allow "ls *" matching "ls" or "ls -la"
 * - Full string anchoring ^...$ prevents injection
 *
 * <p>Mirrors Python's {@code patterns} module in
 * {@code openjiuwen.harness.security.patterns}.
 */
public final class PermissionPatterns {

    private static final Logger LOG = LoggerFactory.getLogger(PermissionPatterns.class);

    // Restricted character class: only allows common path/command characters, excludes shell metacharacters
    private static final String WILDCARD_CHARS = "[-a-zA-Z0-9 \\._/:\"']";

    private PermissionPatterns() {
    }

    /**
     * Match wildcard pattern against value.
     *
     * <p>Mirrors Python's {@code match_wildcard} function.
     */
    public static boolean matchWildcard(String value, String pattern) {
        if (pattern == null || pattern.isEmpty() || value == null || value.isEmpty()) {
            return false;
        }
        String val = value.replace("\\", "/");
        String pat = pattern.replace("\\", "/");

        // Escape regex special characters (except * and ?)
        Set<Character> toEscape = Set.of('.', '+', '^', '$', '{', '}', '(', ')', '|', '[', ']');
        StringBuilder escaped = new StringBuilder();
        for (char c : pat.toCharArray()) {
            if (toEscape.contains(c)) {
                escaped.append('\\').append(c);
            } else {
                escaped.append(c);
            }
        }

        // Replace ? with restricted character class
        String regexPattern = escaped.toString().replace("?", WILDCARD_CHARS);

        // Handle * replacement
        if (regexPattern.endsWith(" *")) {
            regexPattern = regexPattern.substring(0, regexPattern.length() - 2)
                    + "( " + WILDCARD_CHARS + "*)?";
        } else {
            regexPattern = regexPattern.replace("*", WILDCARD_CHARS + "*");
        }

        // Full string anchoring
        regexPattern = "^" + regexPattern + "$";

        try {
            Pattern compiled = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);
            return compiled.matcher(val).matches();
        } catch (java.util.regex.PatternSyntaxException e) {
            LOG.warn("[PermissionPatterns] Invalid pattern: {}", pattern);
            return false;
        }
    }

    /**
     * Check if child path is under parent path (with traversal protection).
     *
     * <p>Mirrors Python's {@code contains_path} function.
     */
    public static boolean containsPath(Path parent, Path child) {
        try {
            Path resolvedParent = parent.toAbsolutePath().normalize();
            Path resolvedChild = child.toAbsolutePath().normalize();
            Path relative = resolvedParent.relativize(resolvedChild);
            String relStr = relative.toString();
            return !relStr.startsWith("..") && !relStr.equals("..");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Merge external directory allow rules into permissions.
     *
     * <p>Returns (mergedPermissions, wroteAny) pair.
     *
     * <p>Mirrors Python's {@code merge_external_directory_allow_into_permissions} function.
     */
    public static Map<String, Object> mergeExternalDirectoryAllowIntoPermissions(
            Map<String, Object> permissions,
            List<String> paths) {
        if (paths == null || paths.isEmpty()) {
            return permissions;
        }

        Map<String, Object> perms = deepCopyMap(permissions);
        Object extCfgObj = perms.get("external_directory");
        Map<String, Object> extCfg;
        if (extCfgObj instanceof Map) {
            extCfg = (Map<String, Object>) extCfgObj;
        } else {
            extCfg = new LinkedHashMap<>();
            extCfg.put("*", "ask");
            perms.put("external_directory", extCfg);
        }

        boolean wrote = false;
        for (String pathStr : paths) {
            String pathNorm = pathStr.replace("\\", "/").replaceAll("/+$", "");
            Path pathObj = Path.of(pathNorm);
            String parent = pathObj.getParent() != null
                    ? pathObj.getParent().toString().replace("\\", "/")
                    : pathNorm;
            String key = (parent != null && !parent.isEmpty() && !parent.equals("."))
                    ? parent : pathNorm;

            if (!"allow".equals(extCfg.get(key))) {
                extCfg.put(key, "allow");
                wrote = true;
                LOG.info("[PermissionPatterns] permission.merge.external path={} action=allow", key);
            }
        }

        return perms;
    }

    /**
     * Write permissions section to agent config YAML.
     *
     * <p>Mirrors Python's {@code write_permissions_section_to_agent_config_yaml} function.
     */
    public static boolean writePermissionsSectionToAgentConfigYaml(
            Path configYamlPath,
            Map<String, Object> permissions) {
        if (configYamlPath == null) {
            LOG.warn("[PermissionPatterns] permission.write_yaml.abort reason=no_config_yaml_path");
            return false;
        }

        Path cfgPath = configYamlPath.toAbsolutePath().normalize();
        if (!Files.exists(cfgPath.getParent())) {
            LOG.warn("[PermissionPatterns] permission.write_yaml.abort reason=parent_dir_not_exists path={}", cfgPath);
            return false;
        }

        try {
            Map<String, Object> data;
            if (Files.exists(cfgPath)) {
                Yaml yaml = new Yaml();
                try (var reader = Files.newBufferedReader(cfgPath)) {
                    Object loaded = yaml.load(reader);
                    if (loaded instanceof Map) {
                        data = (Map<String, Object>) loaded;
                    } else {
                        data = new LinkedHashMap<>();
                    }
                }
            } else {
                data = new LinkedHashMap<>();
            }

            data.put("permissions", deepCopyMap(permissions));

            Yaml yaml = new Yaml();
            try (var writer = Files.newBufferedWriter(cfgPath)) {
                yaml.dump(data, writer);
            }

            LOG.info("[PermissionPatterns] permission.write_yaml.ok path={}", cfgPath);
            return true;
        } catch (IOException e) {
            LOG.error("[PermissionPatterns] permission.write_yaml.failed path={}", cfgPath, e);
            return false;
        }
    }

    /**
     * Deep copy a map (for mutation safety).
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        if (source == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                copy.put(entry.getKey(), deepCopyMap((Map<String, Object>) value));
            } else if (value instanceof List) {
                copy.put(entry.getKey(), new ArrayList<>((List<?>) value));
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }

    /**
     * Build command allow pattern for wildcard matching.
     *
     * <p>Examples:
     * - "start chrome" → "start chrome *"
     * - "npm install" → "npm install *"
     */
    public static String buildCommandAllowPattern(String cmd) {
        if (cmd == null || cmd.isBlank()) {
            return "";
        }
        return cmd.strip() + " *";
    }

    /**
     * Pattern matcher utility class.
     */
    public static class PatternMatcher {
        public boolean match(String pattern, String value) {
            return matchWildcard(value, pattern);
        }

        public boolean matchAny(List<String> patterns, String value) {
            return patterns.stream().anyMatch(p -> match(p, value));
        }
    }

    /**
     * Path matcher utility class.
     */
    public static class PathMatcher {
        private final PatternMatcher pm = new PatternMatcher();

        public boolean matchPath(String pattern, Path path) {
            String normalizedPath = path.toString().replace("\\", "/");
            String normalizedPattern = pattern.replace("\\", "/");

            if (pm.match(normalizedPattern, normalizedPath)) {
                return true;
            }

            // Try matching parent directories
            Path current = path;
            while (current != null) {
                String parentStr = current.toString().replace("\\", "/");
                if (pm.match(normalizedPattern, parentStr)) return true;
                if (pm.match(normalizedPattern, parentStr + "/")) return true;
                if (pm.match(normalizedPattern, parentStr + "/*")) return true;
                current = current.getParent();
            }
            return false;
        }

        public boolean matchPathAny(List<String> patterns, Path path) {
            return patterns.stream().anyMatch(p -> matchPath(p, path));
        }
    }

    /**
     * Command matcher utility class.
     */
    public static class CommandMatcher {
        private final PatternMatcher pm = new PatternMatcher();

        public boolean matchCommand(String pattern, String command) {
            if (command == null || command.isEmpty()) {
                return false;
            }
            return pm.match(pattern, command);
        }

        public boolean matchCommandAny(List<String> patterns, String command) {
            return patterns.stream().anyMatch(p -> matchCommand(p, command));
        }
    }
}