/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Mirrors Python's {@code patterns} in
 * {@code openjiuwen/harness/security/patterns.py}.
 */
public final class PermissionPatterns {

    private static final String WILDCARD_CHARS = "[-a-zA-Z0-9 \\._/:\"']";
    private static final Set<String> SHELL_APPROVAL_TOOLS = Set.of("bash", "mcp_exec_command", "create_terminal");
    private static final Set<String> PATH_APPROVAL_TOOLS = Set.of(
            "read_file", "write_file", "edit_file",
            "read_text_file", "write_text_file",
            "write", "read",
            "glob_file_search", "glob", "list_dir", "list_files",
            "grep", "search_replace"
    );
    private static final List<String> PATH_APPROVAL_KEYS = List.of(
            "path", "file_path", "target_file", "file", "old_path", "new_path",
            "source_path", "dest_path", "directory", "dir"
    );

    private PermissionPatterns() {
    }

    /**
     * Java adaptation of Python's tuple return for merge helpers.
     */
    public record PermissionsMergeResult(Map<String, Object> permissions, boolean changed) {
    }

    private record ApprovalOverrideSignature(
            String toolName,
            List<String> tools,
            String matchType,
            String existingMatchType,
            String pattern,
            String existingPattern,
            String existingAction
    ) {
    }

    public static boolean matchWildcard(String value, String pattern) {
        if (pattern == null || pattern.isBlank() || value == null || value.isBlank()) {
            return false;
        }
        String normalizedValue = value.replace("\\", "/");
        String normalizedPattern = pattern.replace("\\", "/");
        Set<Character> toEscape = Set.of('.', '+', '^', '$', '{', '}', '(', ')', '|', '[', ']', '\\');
        StringBuilder escaped = new StringBuilder();
        for (char ch : normalizedPattern.toCharArray()) {
            if (toEscape.contains(ch)) {
                escaped.append('\\');
            }
            escaped.append(ch);
        }
        String regex = escaped.toString().replace("?", WILDCARD_CHARS);
        if (regex.endsWith(" *")) {
            regex = regex.substring(0, regex.length() - 2) + "( " + WILDCARD_CHARS + "*)?";
        } else {
            regex = regex.replace("*", WILDCARD_CHARS + "*");
        }
        try {
            return Pattern.compile("^" + regex + "$", Pattern.CASE_INSENSITIVE).matcher(normalizedValue).matches();
        } catch (PatternSyntaxException ignored) {
            return false;
        }
    }

    public static String buildCommandAllowPattern(String command) {
        if (command == null || command.isBlank()) {
            return "";
        }
        return command.strip() + " *";
    }

    public static boolean containsPath(String parent, String child) {
        return containsPath(Path.of(parent), Path.of(child));
    }

    public static boolean containsPath(Path parent, Path child) {
        try {
            Path resolvedParent = parent.toAbsolutePath().normalize();
            Path resolvedChild = child.toAbsolutePath().normalize();
            Path relative = resolvedParent.relativize(resolvedChild);
            String text = relative.toString();
            return !text.startsWith("..") && !text.equals("..");
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean writePermissionsSectionToAgentConfigYaml(
            Path configYamlPath,
            Map<String, Object> permissions
    ) {
        if (configYamlPath == null) {
            return false;
        }
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            if (Files.isRegularFile(configYamlPath)) {
                Yaml yaml = new Yaml();
                try (Reader reader = Files.newBufferedReader(configYamlPath)) {
                    Object loaded = yaml.load(reader);
                    if (loaded instanceof Map<?, ?> map) {
                        data.putAll(castMap(map));
                    }
                }
            }
            data.put("permissions", deepCopyMap(permissions));
            Yaml yaml = new Yaml();
            try (Writer writer = Files.newBufferedWriter(configYamlPath)) {
                yaml.dump(data, writer);
            }
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    public static PermissionsMergeResult mergeExternalDirectoryAllowIntoPermissions(
            Map<String, Object> permissions,
            List<String> paths
    ) {
        Map<String, Object> merged = deepCopyMap(permissions);
        if (paths == null || paths.isEmpty()) {
            return new PermissionsMergeResult(merged, false);
        }
        Map<String, Object> externalDirectory = castMap(merged.get("external_directory"));
        if (externalDirectory.isEmpty()) {
            externalDirectory.put("*", "ask");
            merged.put("external_directory", externalDirectory);
        }
        boolean wroteAny = false;
        for (String pathText : paths) {
            if (pathText == null || pathText.isBlank()) {
                continue;
            }
            String normalized = pathText.replace("\\", "/").replaceAll("/+$", "");
            Path candidate = Path.of(normalized);
            String parent = candidate.getParent() != null
                    ? candidate.getParent().toString().replace("\\", "/")
                    : normalized;
            String key = (parent == null || parent.isBlank() || ".".equals(parent)) ? normalized : parent;
            if (!"allow".equals(externalDirectory.get(key))) {
                externalDirectory.put(key, "allow");
                wroteAny = true;
            }
        }
        return new PermissionsMergeResult(merged, wroteAny);
    }

    public static PermissionsMergeResult mergePermissionAllowRuleIntoPermissions(
            Map<String, Object> permissions,
            String toolName,
            Map<String, Object> toolArgs
    ) {
        Map<String, Object> merged = deepCopyMap(permissions);
        PermissionLevel currentPermission = evaluateCurrentPermission(merged, toolName, toolArgs);
        if (currentPermission != PermissionLevel.ASK) {
            return new PermissionsMergeResult(merged, false);
        }

        ShellAstParseResult shellAstResult = SHELL_APPROVAL_TOOLS.contains(toolName)
                ? ShellAst.parseShellForPermission(stringArg(toolArgs, "command", "cmd"))
                : null;
        List<PermissionSuggestion> suggestions =
                PermissionSuggestions.buildPermissionSuggestions(toolName, toolArgs, shellAstResult);

        if (persistTieredApprovalOverrideSuggestions(merged, suggestions)) {
            return new PermissionsMergeResult(merged, true);
        }
        if (!SHELL_APPROVAL_TOOLS.contains(toolName)
                && !PATH_APPROVAL_TOOLS.contains(toolName)
                && persistTieredToolAllow(merged, toolName)) {
            return new PermissionsMergeResult(merged, true);
        }
        return new PermissionsMergeResult(merged, false);
    }

    public static Map<String, Object> persistCliTrustedDirectory(
            String rawPath,
            Path configYamlPath,
            Map<String, Object> bootstrapPermissions
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (rawPath == null || rawPath.isBlank()) {
            result.put("ok", false);
            result.put("error", "path is empty");
            return result;
        }
        try {
            Path resolved = Path.of(rawPath.strip()).toAbsolutePath().normalize();
            String normalized = resolved.toString().replace("\\", "/").replaceAll("/+$", "");
            Map<String, Object> data = loadAgentConfigForPersist(configYamlPath, bootstrapPermissions);
            if (data == null) {
                result.put("ok", false);
                result.put("error", "cannot bootstrap yaml");
                return result;
            }

            Map<String, Object> permissions = castMap(data.get("permissions"));
            if (permissions.isEmpty()) {
                data.put("permissions", permissions);
            }
            Map<String, Object> externalDirectory = castMap(permissions.get("external_directory"));
            if (externalDirectory.isEmpty()) {
                externalDirectory.put("*", "ask");
                permissions.put("external_directory", externalDirectory);
            }
            externalDirectory.put(normalized, "allow");

            List<Object> overrides = castList(permissions, "approval_overrides");
            if (overrides.isEmpty()) {
                permissions.put("approval_overrides", overrides);
            }

            String pathPattern = "re:^" + Pattern.quote(normalized).replace("\\Q", "").replace("\\E", "") + "(?:$|/)";
            String shellPattern = "re:.*" + Pattern.quote(normalized).replace("\\Q", "").replace("\\E", "") + ".*";
            appendOverrideIfMissing(overrides, "cli_trusted_path_" + safeId(normalized),
                    new String[]{
                            "read_file", "write_file", "edit_file",
                            "read_text_file", "write_text_file",
                            "write", "read",
                            "glob_file_search", "glob", "list_dir", "list_files",
                            "grep", "search_replace"
                    }, "path", pathPattern);
            appendOverrideIfMissing(overrides, "cli_trusted_shell_" + safeId(normalized),
                    new String[]{"bash", "mcp_exec_command", "create_terminal"},
                    "command", shellPattern);

            if (!writePermissionsSectionToAgentConfigYaml(configYamlPath, permissions)) {
                result.put("ok", false);
                result.put("error", "failed to write permissions yaml");
                return result;
            }
            result.put("ok", true);
            result.put("normalized", normalized);
            result.put("path_pattern", pathPattern);
            result.put("shell_pattern", shellPattern);
            result.put("tiered_overrides", true);
            return result;
        } catch (Exception ex) {
            result.put("ok", false);
            result.put("error", ex.getMessage());
            return result;
        }
    }

    public static final class PatternMatcher {
        public boolean match(String pattern, String value) {
            return matchWildcard(value, pattern);
        }

        public boolean matchAny(List<String> patterns, String value) {
            if (patterns == null) {
                return false;
            }
            return patterns.stream().filter(Objects::nonNull).anyMatch(pattern -> match(pattern, value));
        }
    }

    public static final class PathMatcher {
        private final PatternMatcher matcher = new PatternMatcher();

        public boolean matchPath(String pattern, Path path) {
            String normalizedPath = path.toString().replace("\\", "/");
            String normalizedPattern = pattern.replace("\\", "/");
            if (matcher.match(normalizedPattern, normalizedPath)) {
                return true;
            }
            Path current = path;
            while (current != null) {
                String text = current.toString().replace("\\", "/");
                if (matcher.match(normalizedPattern, text)
                        || matcher.match(normalizedPattern, text + "/")
                        || matcher.match(normalizedPattern, text + "/*")) {
                    return true;
                }
                current = current.getParent();
            }
            return false;
        }

        public boolean matchPathAny(List<String> patterns, Path path) {
            if (patterns == null) {
                return false;
            }
            return patterns.stream().filter(Objects::nonNull).anyMatch(pattern -> matchPath(pattern, path));
        }
    }

    public static final class URLMatcher {
        private final PatternMatcher matcher = new PatternMatcher();

        public boolean matchUrl(String pattern, String url) {
            if (url == null || url.isBlank()) {
                return false;
            }
            if (matcher.match(pattern, url)) {
                return true;
            }
            try {
                java.net.URI parsed = java.net.URI.create(url);
                String host = parsed.getHost();
                String authority = parsed.getAuthority();
                String base = parsed.getScheme() + "://" + authority;
                return matcher.match(pattern, host == null ? "" : host)
                        || matcher.match(pattern, authority == null ? "" : authority)
                        || matcher.match(pattern, base)
                        || matcher.match(pattern, base + "/*");
            } catch (Exception ignored) {
                return false;
            }
        }

        public boolean matchUrlAny(List<String> patterns, String url) {
            if (patterns == null) {
                return false;
            }
            return patterns.stream().filter(Objects::nonNull).anyMatch(pattern -> matchUrl(pattern, url));
        }
    }

    public static final class CommandMatcher {
        private final PatternMatcher matcher = new PatternMatcher();

        public boolean matchCommand(String pattern, String command) {
            if (command == null || command.isBlank()) {
                return false;
            }
            return matcher.match(pattern, command);
        }

        public boolean matchCommandAny(List<String> patterns, String command) {
            if (patterns == null) {
                return false;
            }
            return patterns.stream().filter(Objects::nonNull).anyMatch(pattern -> matchCommand(pattern, command));
        }
    }

    private static PermissionLevel evaluateCurrentPermission(
            Map<String, Object> permissions,
            String toolName,
            Map<String, Object> toolArgs
    ) {
        Map<String, Object> tools = castMap(permissions.get("tools"));
        Object raw = tools.get(toolName);
        if (raw == null) {
            Map<String, Object> defaults = castMap(permissions.get("defaults"));
            raw = defaults.get("*");
        }
        return raw == null ? PermissionLevel.ASK : PermissionLevel.fromValue(String.valueOf(raw));
    }

    private static boolean persistTieredApprovalOverrideSuggestions(
            Map<String, Object> permissions,
            List<PermissionSuggestion> suggestions
    ) {
        if (suggestions == null || suggestions.isEmpty()) {
            return false;
        }
        List<Object> overrides = castList(permissions, "approval_overrides");
        permissions.put("approval_overrides", overrides);
        boolean persistedAny = false;
        for (PermissionSuggestion suggestion : suggestions) {
            if (suggestion == null || suggestion.tools() == null) {
                continue;
            }
            for (String tool : suggestion.tools()) {
                if (ensureSingleAllowOverride(overrides, tool, suggestion.matchType(), suggestion.pattern(),
                        suggestion.action())) {
                    persistedAny = true;
                }
            }
        }
        return persistedAny;
    }

    private static boolean ensureSingleAllowOverride(
            List<Object> overrides,
            String toolName,
            String matchType,
            String pattern,
            String action
    ) {
        for (Object entry : overrides) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            List<String> tools = toStringList(map.get("tools"));
            ApprovalOverrideSignature signature = new ApprovalOverrideSignature(
                    toolName,
                    tools,
                    matchType,
                    map.get("match_type") == null ? null : String.valueOf(map.get("match_type")),
                    pattern,
                    map.get("pattern") == null ? null : String.valueOf(map.get("pattern")),
                    map.get("action") == null ? "" : String.valueOf(map.get("action")).strip().toLowerCase()
            );
            if (isSameAllowOverride(signature)) {
                return true;
            }
        }

        Map<String, Object> created = new LinkedHashMap<>();
        created.put("id", buildApprovalOverrideId(toolName, matchType, pattern));
        created.put("tools", List.of(toolName));
        created.put("match_type", matchType);
        created.put("pattern", pattern);
        created.put("action", action);
        overrides.add(created);
        return true;
    }

    private static boolean isSameAllowOverride(ApprovalOverrideSignature signature) {
        if (!signature.tools().contains(signature.toolName())) {
            return false;
        }
        if (!Objects.equals(signature.matchType(), signature.existingMatchType())) {
            return false;
        }
        if (!Objects.equals(signature.pattern(), signature.existingPattern())) {
            return false;
        }
        return "allow".equals(signature.existingAction());
    }

    private static String buildApprovalOverrideId(String toolName, String matchType, String pattern) {
        String raw = "user_allow_" + toolName + "_" + matchType + "_" + pattern;
        String collapsed = raw.replaceAll("[^a-zA-Z0-9]+", "_").replaceAll("^_+|_+$", "").toLowerCase();
        return collapsed.isEmpty() ? "user_allow_override" : collapsed.substring(0, Math.min(collapsed.length(), 120));
    }

    private static boolean persistTieredToolAllow(Map<String, Object> permissions, String toolName) {
        Map<String, Object> tools = castMap(permissions.get("tools"));
        permissions.put("tools", tools);
        if ("allow".equals(tools.get(toolName))) {
            return false;
        }
        tools.put(toolName, "allow");
        return true;
    }

    private static Map<String, Object> loadAgentConfigForPersist(
            Path configYamlPath,
            Map<String, Object> bootstrapPermissions
    ) throws IOException {
        if (configYamlPath == null) {
            return null;
        }
        if (Files.isRegularFile(configYamlPath)) {
            Yaml yaml = new Yaml();
            try (Reader reader = Files.newBufferedReader(configYamlPath)) {
                Object loaded = yaml.load(reader);
                return loaded instanceof Map<?, ?> map ? castMap(map) : new LinkedHashMap<>();
            }
        }
        if (bootstrapPermissions == null || bootstrapPermissions.isEmpty()) {
            return null;
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("permissions", deepCopyMap(bootstrapPermissions));
        return root;
    }

    private static void appendOverrideIfMissing(
            List<Object> overrides,
            String id,
            String[] tools,
            String matchType,
            String pattern
    ) {
        for (Object entry : overrides) {
            if (entry instanceof Map<?, ?> map && id.equals(map.get("id"))) {
                return;
            }
        }
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("id", id);
        created.put("tools", List.of(tools));
        created.put("match_type", matchType);
        created.put("pattern", pattern);
        created.put("action", "allow");
        overrides.add(created);
    }

    private static String safeId(String input) {
        return Integer.toHexString(input.hashCode());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object input) {
        if (!(input instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                converted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return converted;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> castList(Map<String, Object> owner, String key) {
        Object value = owner.get(key);
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>();
    }

    private static List<String> toStringList(Object value) {
        if (value instanceof String stringValue) {
            return List.of(stringValue);
        }
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (source == null) {
            return copy;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nestedMap) {
                copy.put(entry.getKey(), deepCopyMap(castMap(nestedMap)));
            } else if (value instanceof List<?> list) {
                copy.put(entry.getKey(), deepCopyList(list));
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }

    private static List<Object> deepCopyList(List<?> source) {
        List<Object> copy = new ArrayList<>();
        for (Object item : source) {
            if (item instanceof Map<?, ?> map) {
                copy.add(deepCopyMap(castMap(map)));
            } else if (item instanceof List<?> list) {
                copy.add(deepCopyList(list));
            } else {
                copy.add(item);
            }
        }
        return copy;
    }

    private static String stringArg(Map<String, Object> toolArgs, String... keys) {
        if (toolArgs == null) {
            return "";
        }
        for (String key : keys) {
            Object value = toolArgs.get(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return "";
    }
}
