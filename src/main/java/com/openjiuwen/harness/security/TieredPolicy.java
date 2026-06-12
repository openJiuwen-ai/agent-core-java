/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
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
 * Mirrors Python's {@code tiered_policy} in
 * {@code openjiuwen/harness/security/tiered_policy.py}.
 */
public final class TieredPolicy {

    public static final Set<String> SHELL_TOOLS = Set.of("bash", "mcp_exec_command", "create_terminal");
    public static final Set<String> PATH_TOOLS = Set.of(
            "read_file", "write_file", "edit_file",
            "read_text_file", "write_text_file",
            "write", "read",
            "glob_file_search", "glob", "list_dir", "list_files",
            "grep", "search_replace"
    );
    public static final Set<String> NETWORK_TOOLS = Set.of("mcp_fetch_webpage", "mcp_free_search", "mcp_paid_search");
    public static final Set<String> PATH_ARG_KEYS = Set.of(
            "path", "file_path", "target_file", "file", "old_path", "new_path",
            "source_path", "dest_path", "directory", "dir"
    );

    private static final String MATCHED_RULE_PREFIX = "tiered_policy";
    private static final String APPROVAL_OVERRIDES_PREFIX = MATCHED_RULE_PREFIX + ":approval_overrides";
    private static final PermissionPatterns.PathMatcher TIERED_PATH_MATCHER = new PermissionPatterns.PathMatcher();
    private static volatile List<Map<String, Object>> builtinRulesCache;

    private TieredPolicy() {
    }

    /**
     * Java adaptation of Python's tuple return.
     */
    public record PermissionDecision(PermissionLevel permission, String matchedRule) {
    }

    private record TieredInvocationContext(
            String mode,
            List<Map<String, Object>> builtinRules,
            List<Map<String, Object>> rules,
            List<Map<String, Object>> approvalOverrides,
            PermissionLevel baselineLevel,
            String baselineRule,
            Map<String, Object> defaultsConfig
    ) {
    }

    public static Path getPackageBuiltinRulesPath() {
        URL resource = TieredPolicy.class.getClassLoader()
                .getResource("com/openjiuwen/harness/security/builtin_rules.yaml");
        if (resource == null) {
            return Path.of("src/main/resources/com/openjiuwen/harness/security/builtin_rules.yaml");
        }
        try {
            return Path.of(resource.toURI());
        } catch (URISyntaxException ex) {
            return Path.of(resource.getPath());
        }
    }

    public static List<Map<String, Object>> getBuiltinSecurityRules() {
        List<Map<String, Object>> cached = builtinRulesCache;
        if (cached != null) {
            return cached;
        }
        synchronized (TieredPolicy.class) {
            if (builtinRulesCache != null) {
                return builtinRulesCache;
            }
            try (InputStream stream = TieredPolicy.class.getClassLoader()
                    .getResourceAsStream("com/openjiuwen/harness/security/builtin_rules.yaml")) {
                if (stream == null) {
                    builtinRulesCache = List.of();
                    return builtinRulesCache;
                }
                Object loaded = new Yaml().load(stream);
                if (loaded instanceof Map<?, ?> map && map.get("rules") instanceof List<?> rules) {
                    builtinRulesCache = toMapList(rules);
                } else {
                    builtinRulesCache = List.of();
                }
            } catch (Exception ignored) {
                builtinRulesCache = List.of();
            }
            return builtinRulesCache;
        }
    }

    public static PermissionLevel strictest(PermissionLevel... levels) {
        if (levels == null || levels.length == 0) {
            return PermissionLevel.ASK;
        }
        PermissionLevel current = PermissionLevel.ALLOW;
        for (PermissionLevel level : levels) {
            if (level == PermissionLevel.DENY) {
                return PermissionLevel.DENY;
            }
            if (level == PermissionLevel.ASK) {
                current = PermissionLevel.ASK;
            }
        }
        return current;
    }

    public static PermissionLevel severityToDecision(String severity, String permissionMode) {
        String normalizedSeverity = severity == null ? "HIGH" : severity.strip().toUpperCase();
        String mode = normalizeMode(permissionMode);
        return switch (normalizedSeverity) {
            case "LOW" -> PermissionLevel.ALLOW;
            case "MEDIUM" -> "strict".equals(mode) ? PermissionLevel.ASK : PermissionLevel.ALLOW;
            case "HIGH" -> PermissionLevel.ASK;
            case "CRITICAL" -> "strict".equals(mode) ? PermissionLevel.DENY : PermissionLevel.ASK;
            default -> PermissionLevel.ASK;
        };
    }

    public static boolean ruleToolsCategoryConsistent(List<String> tools) {
        Set<String> categories = new LinkedHashSet<>();
        for (String tool : tools) {
            String category = toolCategory(tool);
            if (category == null) {
                return false;
            }
            categories.add(category);
            if (categories.size() > 1) {
                return false;
            }
        }
        return !categories.isEmpty();
    }

    public static List<String> iterPathStrings(String toolName, Map<String, Object> toolArgs) {
        List<String> paths = new ArrayList<>();
        if (toolArgs == null) {
            return paths;
        }
        for (Map.Entry<String, Object> entry : toolArgs.entrySet()) {
            if (!(entry.getValue() instanceof String value) || value.isBlank()) {
                continue;
            }
            if (toolArgValueLooksLikePath(entry.getKey(), value)) {
                paths.add(value.trim());
            }
        }
        return paths;
    }

    public static boolean tieredPolicyRuleMatches(
            String toolName,
            String pattern,
            Map<String, Object> toolArgs,
            List<String> ruleTools
    ) {
        if (ruleTools == null || ruleTools.isEmpty()) {
            return false;
        }
        String category = toolCategory(ruleTools.get(0));
        if ("shell".equals(category)) {
            return shellPatternMatches(pattern, commandText(toolArgs));
        }
        if ("path".equals(category)) {
            for (String path : iterPathStrings(toolName, toolArgs)) {
                if (pathPatternMatches(pattern, path)) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    public static PermissionDecision evaluateTieredPolicy(
            Map<String, Object> permissionConfig,
            String toolName,
            Map<String, Object> toolArgs
    ) {
        Map<String, Object> config = permissionConfig == null ? Map.of() : permissionConfig;
        Map<String, Object> toolsConfig = castMap(config.get("tools"));
        Map<String, Object> defaultsConfig = castMap(config.get("defaults"));
        List<Map<String, Object>> rules = toMapList(castList(config.get("rules")));
        List<Map<String, Object>> approvalOverrides = toMapList(castList(config.get("approval_overrides")));
        String mode = normalizeMode(config.get("permission_mode") == null ? null : String.valueOf(config.get("permission_mode")));

        PermissionDecision baseline = baselineLevel(toolsConfig, defaultsConfig, toolName);
        if (baseline.permission() == PermissionLevel.DENY) {
            return baseline;
        }

        ShellAstParseResult shellParse = SHELL_TOOLS.contains(toolName)
                ? ShellAst.parseShellForPermission(commandText(toolArgs))
                : null;
        PermissionDecision shellFloor = shellAstFloor(shellParse);
        TieredInvocationContext context = new TieredInvocationContext(
                mode,
                getBuiltinSecurityRules(),
                rules,
                approvalOverrides,
                baseline.permission(),
                baseline.matchedRule(),
                defaultsConfig
        );

        PermissionDecision result;
        if (shellParse != null && "simple".equals(shellParse.getKind()) && shellParse.getSubcommands().size() > 1) {
            List<PermissionDecision> subResults = new ArrayList<>();
            List<String> contributing = new ArrayList<>();
            for (ShellSubcommand subcommand : shellParse.getSubcommands()) {
                Map<String, Object> subArgs = withShellCommand(toolArgs, subcommand.getText());
                PermissionDecision subDecision = evaluateSingleInvocation(toolName, subArgs, context);
                subResults.add(subDecision);
                contributing.add(subcommand.getText() + "=>" + subDecision.matchedRule());
                if (subDecision.permission() == PermissionLevel.DENY) {
                    break;
                }
            }
            PermissionLevel aggregateLevel = strictest(subResults.stream().map(PermissionDecision::permission)
                    .toArray(PermissionLevel[]::new));
            String aggregateRule = MATCHED_RULE_PREFIX + ":shell_subcommands:" + String.join("+", contributing);
            result = new PermissionDecision(aggregateLevel, aggregateRule);
        } else {
            result = evaluateSingleInvocation(toolName, toolArgs, context);
        }
        return applyShellAstFloor(result, shellFloor);
    }

    public static PermissionLevel maybeEscalateShellOperators(
            String toolName,
            Map<String, Object> toolArgs,
            PermissionLevel permission
    ) {
        if (permission != PermissionLevel.ALLOW || !SHELL_TOOLS.contains(toolName)) {
            return permission;
        }
        String command = commandText(toolArgs);
        if (command != null && command.matches(".*([;&|`<>]|\\$\\(|\\$\\{|\\R).*")) {
            return PermissionLevel.ASK;
        }
        return permission;
    }

    public static boolean matchedRuleUsesApprovalOverride(String matchedRule) {
        return matchedRule != null && matchedRule.startsWith(APPROVAL_OVERRIDES_PREFIX);
    }

    private static PermissionDecision evaluateSingleInvocation(
            String toolName,
            Map<String, Object> toolArgs,
            TieredInvocationContext context
    ) {
        List<PermissionDecision> builtinHits = collectRuleHits(
                context.builtinRules(), toolName, toolArgs, context.mode(), "builtin");
        PermissionDecision denyBuiltin = firstDecisionOfLevel(builtinHits, PermissionLevel.DENY);
        if (denyBuiltin != null) {
            return denyBuiltin;
        }

        List<PermissionDecision> userHits = collectRuleHits(
                context.rules(), toolName, toolArgs, context.mode(), "rules");
        PermissionDecision denyUser = firstDecisionOfLevel(userHits, PermissionLevel.DENY);
        if (denyUser != null) {
            return denyUser;
        }

        List<String> overrideHits = collectApprovalOverrideHits(context.approvalOverrides(), toolName, toolArgs);
        if (!overrideHits.isEmpty()) {
            return new PermissionDecision(PermissionLevel.ALLOW,
                    APPROVAL_OVERRIDES_PREFIX + ":" + String.join("+", overrideHits));
        }

        if (!builtinHits.isEmpty()) {
            return collapseHits("builtin", builtinHits);
        }
        if (!userHits.isEmpty()) {
            return collapseHits("rules", userHits);
        }
        if (context.baselineLevel() != null) {
            return new PermissionDecision(context.baselineLevel(), context.baselineRule());
        }
        Object fallback = context.defaultsConfig().get("*");
        if (fallback instanceof String value) {
            return new PermissionDecision(PermissionLevel.fromValue(value), MATCHED_RULE_PREFIX + ":defaults.*");
        }
        return new PermissionDecision(PermissionLevel.ASK, MATCHED_RULE_PREFIX + ":fallback(no_config)");
    }

    private static List<PermissionDecision> collectRuleHits(
            List<Map<String, Object>> rules,
            String toolName,
            Map<String, Object> toolArgs,
            String mode,
            String labelNamespace
    ) {
        List<PermissionDecision> hits = new ArrayList<>();
        for (Map<String, Object> rule : rules) {
            List<String> tools = toStringList(rule.get("tools"));
            if (!tools.contains(toolName) || !ruleToolsCategoryConsistent(tools)) {
                continue;
            }
            String pattern = rule.get("pattern") == null ? null : String.valueOf(rule.get("pattern"));
            if (pattern == null || pattern.isBlank() || !tieredPolicyRuleMatches(toolName, pattern, toolArgs, tools)) {
                continue;
            }
            PermissionLevel level = rule.get("action") instanceof String action
                    ? PermissionLevel.fromValue(action)
                    : severityToDecision(
                            rule.get("severity") == null ? "HIGH" : String.valueOf(rule.get("severity")),
                            mode
                    );
            String id = rule.get("id") == null ? "?" : String.valueOf(rule.get("id"));
            hits.add(new PermissionDecision(level, labelNamespace + "[" + id + "]"));
        }
        return hits;
    }

    private static List<String> collectApprovalOverrideHits(
            List<Map<String, Object>> approvalOverrides,
            String toolName,
            Map<String, Object> toolArgs
    ) {
        List<String> hits = new ArrayList<>();
        for (Map<String, Object> rule : approvalOverrides) {
            if (!"allow".equalsIgnoreCase(String.valueOf(rule.getOrDefault("action", "")))) {
                continue;
            }
            List<String> tools = toStringList(rule.get("tools"));
            if (!tools.contains(toolName) || !ruleToolsCategoryConsistent(tools)) {
                continue;
            }
            String pattern = rule.get("pattern") == null ? null : String.valueOf(rule.get("pattern"));
            if (pattern == null || !tieredPolicyRuleMatches(toolName, pattern, toolArgs, tools)) {
                continue;
            }
            String id = rule.get("id") == null ? "?" : String.valueOf(rule.get("id"));
            hits.add("approval_overrides[" + id + "]");
        }
        return hits;
    }

    private static PermissionDecision baselineLevel(
            Map<String, Object> toolsConfig,
            Map<String, Object> defaultsConfig,
            String toolName
    ) {
        Object toolLevel = toolsConfig.get(toolName);
        if (toolLevel instanceof String value) {
            return new PermissionDecision(PermissionLevel.fromValue(value), MATCHED_RULE_PREFIX + ":tools." + toolName);
        }
        Object fallback = defaultsConfig.get("*");
        if (fallback instanceof String value) {
            return new PermissionDecision(PermissionLevel.fromValue(value), MATCHED_RULE_PREFIX + ":defaults.*");
        }
        return new PermissionDecision(null, null);
    }

    private static PermissionDecision collapseHits(String prefix, List<PermissionDecision> hits) {
        PermissionLevel finalLevel = strictest(hits.stream().map(PermissionDecision::permission)
                .toArray(PermissionLevel[]::new));
        List<String> contributing = new ArrayList<>();
        for (PermissionDecision hit : hits) {
            if (hit.permission() == finalLevel) {
                contributing.add(hit.matchedRule());
            }
        }
        return new PermissionDecision(finalLevel,
                MATCHED_RULE_PREFIX + ":" + prefix + ":" + String.join("+", contributing));
    }

    private static PermissionDecision shellAstFloor(ShellAstParseResult shellParse) {
        if (shellParse == null) {
            return null;
        }
        ShellStructureFlags flags = shellParse.getFlags();
        if ("too_complex".equals(shellParse.getKind())) {
            return new PermissionDecision(PermissionLevel.ASK,
                    MATCHED_RULE_PREFIX + ":shell_ast:too_complex:" + safeReason(shellParse.getReason()));
        }
        if ("parse_unavailable".equals(shellParse.getKind()) && flags.hasRiskyStructure()) {
            return new PermissionDecision(PermissionLevel.ASK,
                    MATCHED_RULE_PREFIX + ":shell_ast:parse_unavailable:" + safeReason(shellParse.getReason()));
        }
        if (flags.hasInputRedirection() || flags.hasOutputRedirection()
                || flags.hasCommandSubstitution() || flags.hasProcessSubstitution() || flags.hasHeredoc()) {
            return new PermissionDecision(PermissionLevel.ASK, MATCHED_RULE_PREFIX + ":shell_ast:structure_guard");
        }
        return null;
    }

    private static PermissionDecision applyShellAstFloor(
            PermissionDecision result,
            PermissionDecision shellFloor
    ) {
        if (shellFloor == null) {
            return result;
        }
        PermissionLevel finalLevel = strictest(result.permission(), shellFloor.permission());
        if (finalLevel == result.permission()) {
            return result;
        }
        String matchedRule = shellFloor.matchedRule() + "|" + result.matchedRule();
        return new PermissionDecision(finalLevel, matchedRule);
    }

    private static PermissionDecision firstDecisionOfLevel(List<PermissionDecision> hits, PermissionLevel level) {
        for (PermissionDecision hit : hits) {
            if (hit.permission() == level) {
                return hit;
            }
        }
        return null;
    }

    private static boolean shellPatternMatches(String pattern, String command) {
        if (pattern == null || pattern.isBlank() || command == null || command.isBlank()) {
            return false;
        }
        String normalizedPattern = pattern.strip();
        if (normalizedPattern.startsWith("re:")) {
            String expr = normalizedPattern.substring(3).trim();
            try {
                return Pattern.compile(expr, Pattern.CASE_INSENSITIVE).matcher(command).find()
                        || Pattern.compile(expr, Pattern.CASE_INSENSITIVE).matcher(command.replace("\\", "/")).find();
            } catch (PatternSyntaxException ignored) {
                return false;
            }
        }
        if (normalizedPattern.contains("*") || normalizedPattern.contains("?") || normalizedPattern.contains("[")) {
            return PermissionPatterns.matchWildcard(command, normalizedPattern);
        }
        return Objects.equals(command, normalizedPattern);
    }

    private static boolean pathPatternMatches(String pattern, String value) {
        if (pattern == null || value == null || value.isBlank()) {
            return false;
        }
        if (pattern.startsWith("re:")) {
            try {
                return Pattern.compile(pattern.substring(3).trim(), Pattern.CASE_INSENSITIVE)
                        .matcher(value.replace("\\", "/"))
                        .find();
            } catch (PatternSyntaxException ignored) {
                return false;
            }
        }
        return TIERED_PATH_MATCHER.matchPath(pattern, Path.of(value));
    }

    private static boolean toolArgValueLooksLikePath(String argKey, String value) {
        if (PATH_ARG_KEYS.contains(argKey)) {
            return true;
        }
        return value.contains("/") || value.contains("\\") || (value.length() > 1 && value.charAt(1) == ':');
    }

    private static String toolCategory(String toolName) {
        if (SHELL_TOOLS.contains(toolName)) {
            return "shell";
        }
        if (PATH_TOOLS.contains(toolName)) {
            return "path";
        }
        if (NETWORK_TOOLS.contains(toolName)) {
            return "network";
        }
        return null;
    }

    private static Map<String, Object> withShellCommand(Map<String, Object> toolArgs, String command) {
        Map<String, Object> updated = new LinkedHashMap<>();
        if (toolArgs != null) {
            updated.putAll(toolArgs);
        }
        updated.put("command", command);
        if (updated.containsKey("cmd")) {
            updated.put("cmd", command);
        }
        return updated;
    }

    private static String commandText(Map<String, Object> toolArgs) {
        if (toolArgs == null) {
            return "";
        }
        Object command = toolArgs.get("command");
        if (command == null) {
            command = toolArgs.get("cmd");
        }
        return command == null ? "" : String.valueOf(command).trim();
    }

    private static String normalizeMode(String mode) {
        if ("strict".equalsIgnoreCase(mode)) {
            return "strict";
        }
        return "normal";
    }

    private static String safeReason(String reason) {
        return reason == null || reason.isBlank() ? "unknown" : reason;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
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

    private static List<Object> castList(Object value) {
        return value instanceof List<?> list ? new ArrayList<>(list) : List.of();
    }

    private static List<Map<String, Object>> toMapList(List<?> source) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : source) {
            result.add(castMap(item));
        }
        return result;
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
}
