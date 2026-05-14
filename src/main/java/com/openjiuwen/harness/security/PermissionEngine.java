/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import com.openjiuwen.core.common.security.PathChecker;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Minimal configurable permission engine for Java harness tool checks.
 *
 * <p>Mirrors Python's permission evaluation flow in
 * {@code openjiuwen.harness.security.core},
 * {@code openjiuwen.harness.security.checker}, and
 * {@code openjiuwen.harness.security.tiered_policy}.
 */
public class PermissionEngine {

    private static final String BUILTIN_RULES_RESOURCE = "com/openjiuwen/harness/security/builtin_rules.yaml";
    private static volatile List<Map<String, Object>> builtinRulesCache;

    private Map<String, Object> config;

    public PermissionEngine(Map<String, Object> config) {
        this.config = config != null ? config : Map.of();
    }

    public void updateConfig(Map<String, Object> config) {
        this.config = config != null ? config : Map.of();
    }

    @SuppressWarnings("unchecked")
    public PermissionResult checkPermission(String toolName, Map<String, Object> toolArgs) {
        if (toolName == null || toolName.isBlank()) {
            return new PermissionResult(PermissionLevel.ALLOW, "empty_tool", "No tool name provided");
        }

        Object enabledObj = config.get("enabled");
        if (enabledObj instanceof Boolean enabled && !enabled) {
            return new PermissionResult(PermissionLevel.ALLOW, "disabled", "Permission system disabled");
        }

        Map<String, Object> tools = config.get("tools") instanceof Map<?, ?> map
                ? (Map<String, Object>) map : Map.of();
        Map<String, Object> defaults = config.get("defaults") instanceof Map<?, ?> defaultsMap
                ? (Map<String, Object>) defaultsMap : Map.of();

        PermissionResult baseline = resolveBaseline(toolName, tools, defaults);
        PermissionResult builtinRuleResult = resolveBuiltinRules(toolName, toolArgs);
        PermissionResult ruleResult = resolveRules(toolName, toolArgs);
        PermissionResult overrideResult = resolveApprovalOverrides(toolName, toolArgs);
        PermissionResult externalDirectoryResult = resolveExternalDirectory(toolName, toolArgs);
        PermissionResult shellStructureResult = resolveShellStructure(toolName, toolArgs);

        PermissionResult result = strictest(baseline, builtinRuleResult);
        result = strictest(result, ruleResult);
        if (overrideResult != null && overrideResult.getPermission() == PermissionLevel.ALLOW) {
            result = overrideResult;
        }
        result = strictest(result, externalDirectoryResult);
        result = strictest(result, shellStructureResult);

        PermissionLevel level = result.getPermission();
        if (level == PermissionLevel.ALLOW) {
            level = maybeEscalate(toolName, toolArgs, level);
            result = new PermissionResult(level, result.getMatchedRule(), result.getReason(), result.getExternalPaths());
        }
        return result;
    }

    private PermissionResult resolveBaseline(String toolName, Map<String, Object> tools, Map<String, Object> defaults) {
        Object rawRule = tools.get(toolName);
        String matchedRule = rawRule != null ? "tools." + toolName : "defaults.*";
        if (rawRule == null) {
            rawRule = defaults.get("*");
        }
        PermissionLevel level = parseLevel(rawRule);
        if (level == null) {
            level = PermissionLevel.ASK;
            matchedRule = "fallback(no_config)";
        }
        return new PermissionResult(level, matchedRule, reasonFor(level, matchedRule));
    }

    @SuppressWarnings("unchecked")
    private PermissionResult resolveBuiltinRules(String toolName, Map<String, Object> toolArgs) {
        return resolveRuleList(toolName, toolArgs, loadBuiltinRules(), "builtin");
    }

    @SuppressWarnings("unchecked")
    private PermissionResult resolveRules(String toolName, Map<String, Object> toolArgs) {
        Object rulesObj = config.get("rules");
        if (!(rulesObj instanceof List<?> rules)) {
            return null;
        }
        return resolveRuleList(toolName, toolArgs, (List<Map<String, Object>>) rules, "rules");
    }

    private PermissionResult resolveRuleList(
            String toolName,
            Map<String, Object> toolArgs,
            List<Map<String, Object>> rules,
            String namespace
    ) {
        List<PermissionResult> hits = new ArrayList<>();
        for (Map<String, Object> rule : rules) {
            if (rule == null) {
                continue;
            }
            if (!toolMatches(rule.get("tools"), toolName)) {
                continue;
            }
            if (!ruleMatches(rule, toolName, toolArgs)) {
                continue;
            }
            PermissionLevel level = parseRuleDecision(rule);
            if (level == null) {
                continue;
            }
            String ruleId = rule.get("id") != null ? String.valueOf(rule.get("id")) : "rules";
            hits.add(new PermissionResult(level, namespace + "." + ruleId, reasonFor(level, namespace + "." + ruleId)));
        }
        return collapseHits(hits);
    }

    @SuppressWarnings("unchecked")
    private PermissionResult resolveApprovalOverrides(String toolName, Map<String, Object> toolArgs) {
        Object overridesObj = config.get("approval_overrides");
        if (!(overridesObj instanceof List<?> overrides)) {
            return null;
        }
        for (Object entry : overrides) {
            if (!(entry instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> override = (Map<String, Object>) rawMap;
            if (!toolMatches(override.get("tools"), toolName)) {
                continue;
            }
            if (!ruleMatches(override, toolName, toolArgs)) {
                continue;
            }
            PermissionLevel level = parseLevel(override.get("action"));
            if (level == null) {
                continue;
            }
            String ruleId = override.get("id") != null ? String.valueOf(override.get("id")) : "approval_override";
            return new PermissionResult(level, "approval_overrides." + ruleId, reasonFor(level, ruleId));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private PermissionResult resolveExternalDirectory(String toolName, Map<String, Object> toolArgs) {
        Object externalObj = config.get("external_directory");
        if (!(externalObj instanceof Map<?, ?> rawMap)) {
            return null;
        }
        Map<String, Object> externalConfig = (Map<String, Object>) rawMap;
        List<String> candidatePaths = extractCandidatePaths(toolName, toolArgs);
        if (candidatePaths.isEmpty()) {
            return null;
        }
        List<String> externalPaths = new ArrayList<>();
        for (String path : candidatePaths) {
            if (path == null || path.isBlank()) {
                continue;
            }
            if (PathChecker.isSensitivePath(path) || isOutsideWorkspace(path, externalConfig)) {
                externalPaths.add(path.replace('\\', '/'));
            }
        }
        if (externalPaths.isEmpty()) {
            return null;
        }
        Object actionObj = externalConfig.getOrDefault("*", "ask");
        PermissionLevel level = parseLevel(actionObj);
        if (level == null) {
            level = PermissionLevel.ASK;
        }
        return new PermissionResult(level, "external_directory.*", reasonFor(level, "external_directory.*"), externalPaths);
    }

    private PermissionResult resolveShellStructure(String toolName, Map<String, Object> toolArgs) {
        if (!"bash".equals(toolName) && !"mcp_exec_command".equals(toolName) && !"create_terminal".equals(toolName)) {
            return null;
        }
        String command = stringValue(toolArgs.get("command"));
        if (command == null || command.isBlank()) {
            command = stringValue(toolArgs.get("cmd"));
        }
        ShellStructureAnalysis analysis = ShellStructureParser.analyze(command);
        String permissionMode = normalizePermissionMode(config.get("permission_mode"));

        if (analysis.getKind() == ShellStructureAnalysis.Kind.TOO_COMPLEX) {
            return new PermissionResult(PermissionLevel.ASK,
                    "shell_ast.too_complex",
                    "Approval required by shell structure: " + analysis.getReason());
        }
        if (analysis.getKind() == ShellStructureAnalysis.Kind.PARSE_UNAVAILABLE && analysis.hasRiskyStructure()) {
            return new PermissionResult(PermissionLevel.ASK,
                    "shell_ast.parse_unavailable",
                    "Approval required by shell structure: " + analysis.getReason());
        }
        if (analysis.hasRiskyStructure()) {
            PermissionLevel level = "strict".equals(permissionMode) ? PermissionLevel.ASK : PermissionLevel.ALLOW;
            if (analysis.getOperators().contains("<<") || analysis.getOperators().contains("<<<")) {
                level = PermissionLevel.ASK;
            }
            return new PermissionResult(level,
                    "shell_ast.structure_guard",
                    reasonFor(level, "shell_ast.structure_guard"));
        }
        return null;
    }

    private String normalizePermissionMode(Object modeObj) {
        String mode = modeObj != null ? String.valueOf(modeObj).trim().toLowerCase() : "normal";
        return ("strict".equals(mode) || "normal".equals(mode)) ? mode : "normal";
    }

    private boolean isOutsideWorkspace(String pathText, Map<String, Object> externalConfig) {
        Object workspaceRootObj = config.get("workspace_root");
        if (workspaceRootObj == null) {
            return true;
        }
        try {
            Path workspace = Path.of(String.valueOf(workspaceRootObj)).toAbsolutePath().normalize();
            Path candidate = Path.of(pathText).toAbsolutePath().normalize();
            if (candidate.startsWith(workspace)) {
                return false;
            }
            for (Map.Entry<String, Object> entry : externalConfig.entrySet()) {
                if ("*".equals(entry.getKey()) || !"allow".equalsIgnoreCase(String.valueOf(entry.getValue()))) {
                    continue;
                }
                Path allowed = Path.of(entry.getKey()).toAbsolutePath().normalize();
                if (candidate.startsWith(allowed)) {
                    return false;
                }
            }
            return true;
        } catch (Exception ex) {
            return true;
        }
    }

    private List<String> extractCandidatePaths(String toolName, Map<String, Object> toolArgs) {
        List<String> paths = new ArrayList<>();
        for (Map.Entry<String, Object> entry : toolArgs.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (key.contains("path") || key.contains("file") || key.contains("dir") || "workdir".equals(key)) {
                paths.add(String.valueOf(value));
            }
            if ("bash".equals(toolName) && "command".equals(key)) {
                paths.addAll(extractShellPaths(String.valueOf(value), toolArgs.get("workdir")));
            }
        }
        return paths;
    }

    private List<String> extractShellPaths(String command, Object workdirObj) {
        List<String> results = new ArrayList<>();
        if (command == null || command.isBlank()) {
            return results;
        }
        String[] tokens = command.split("\\s+");
        Path workdir = null;
        try {
            if (workdirObj != null && !String.valueOf(workdirObj).isBlank()) {
                workdir = Path.of(String.valueOf(workdirObj)).toAbsolutePath().normalize();
            }
        } catch (Exception ignored) {
            workdir = null;
        }
        for (int i = 1; i < tokens.length; i++) {
            String token = tokens[i].replace("\"", "").replace("'", "");
            if (token.isBlank() || token.startsWith("-")) {
                continue;
            }
            if (token.contains("/") || token.contains("\\") || token.startsWith(".")) {
                try {
                    Path candidate = Path.of(token);
                    if (!candidate.isAbsolute() && workdir != null) {
                        candidate = workdir.resolve(candidate).normalize();
                    }
                    results.add(candidate.toAbsolutePath().normalize().toString());
                } catch (Exception ignored) {
                    results.add(token);
                }
            }
        }
        return results;
    }

    private PermissionResult collapseHits(List<PermissionResult> hits) {
        if (hits == null || hits.isEmpty()) {
            return null;
        }
        PermissionResult result = hits.get(0);
        for (int i = 1; i < hits.size(); i++) {
            result = strictest(result, hits.get(i));
        }
        return result;
    }

    private PermissionResult strictest(PermissionResult left, PermissionResult right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        if (severity(right.getPermission()) < severity(left.getPermission())) {
            return right;
        }
        if (severity(right.getPermission()) > severity(left.getPermission())) {
            return left;
        }
        List<String> mergedExternalPaths = new ArrayList<>();
        mergedExternalPaths.addAll(left.getExternalPaths());
        for (String externalPath : right.getExternalPaths()) {
            if (!mergedExternalPaths.contains(externalPath)) {
                mergedExternalPaths.add(externalPath);
            }
        }
        return new PermissionResult(left.getPermission(), left.getMatchedRule() + "|" + right.getMatchedRule(),
                left.getReason(), mergedExternalPaths);
    }

    private int severity(PermissionLevel level) {
        if (level == PermissionLevel.DENY) {
            return 0;
        }
        if (level == PermissionLevel.ASK) {
            return 1;
        }
        return 2;
    }

    private boolean toolMatches(Object toolsObj, String toolName) {
        if (toolsObj instanceof List<?> list) {
            for (Object item : list) {
                if (toolName.equals(String.valueOf(item))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean ruleMatches(Map<String, Object> rule, String toolName, Map<String, Object> toolArgs) {
        String matchType = stringValue(rule.get("match_type"));
        String pattern = stringValue(rule.get("pattern"));
        if (matchType == null || pattern == null) {
            return false;
        }
        if ("command".equals(matchType)) {
            String command = stringValue(toolArgs.get("command"));
            return valueMatches(command, pattern);
        }
        if ("path".equals(matchType)) {
            for (String path : extractCandidatePaths(toolName, toolArgs)) {
                if (valueMatches(path, pattern)) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    private boolean valueMatches(String value, String pattern) {
        if (value == null || pattern == null) {
            return false;
        }
        if (pattern.startsWith("re:")) {
            return Pattern.compile(pattern.substring(3)).matcher(value).find();
        }
        String normalizedPattern = pattern.replace("*", ".*");
        return Pattern.compile(normalizedPattern).matcher(value).find();
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private String reasonFor(PermissionLevel level, String matchedRule) {
        return switch (level) {
            case ALLOW -> "Allowed by rule: " + matchedRule;
            case ASK -> "Approval required by rule: " + matchedRule;
            case DENY -> "Denied by rule: " + matchedRule;
        };
    }

    private PermissionLevel maybeEscalate(String toolName, Map<String, Object> toolArgs, PermissionLevel current) {
        if (current != PermissionLevel.ALLOW) {
            return current;
        }
        if ("bash".equals(toolName)) {
            String command = toolArgs != null && toolArgs.get("command") != null
                    ? String.valueOf(toolArgs.get("command")) : "";
            if (containsRisk(command)) {
                return PermissionLevel.ASK;
            }
        }
        if ("code".equals(toolName)) {
            String code = toolArgs != null && toolArgs.get("code") != null
                    ? String.valueOf(toolArgs.get("code")) : "";
            if (containsRisk(code)) {
                return PermissionLevel.ASK;
            }
        }
        return current;
    }

    private boolean containsRisk(String text) {
        String normalized = text == null ? "" : text.toLowerCase();
        List<String> patterns = List.of("rm -rf", "del /f /s /q", "format ", "mkfs", "diskpart", "shutdown ");
        for (String pattern : patterns) {
            if (normalized.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private PermissionLevel parseLevel(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim().toUpperCase();
        try {
            return PermissionLevel.valueOf(text);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private PermissionLevel parseRuleDecision(Map<String, Object> rule) {
        PermissionLevel explicit = parseLevel(rule.get("action"));
        if (explicit != null) {
            return explicit;
        }
        Object severity = rule.get("severity");
        return severityToDecision(severity != null ? String.valueOf(severity) : null,
                normalizePermissionMode(config.get("permission_mode")));
    }

    private PermissionLevel severityToDecision(String severity, String permissionMode) {
        String sev = severity != null ? severity.trim().toUpperCase() : "HIGH";
        String mode = normalizePermissionMode(permissionMode);
        return switch (sev) {
            case "LOW" -> PermissionLevel.ALLOW;
            case "MEDIUM" -> "strict".equals(mode) ? PermissionLevel.ASK : PermissionLevel.ALLOW;
            case "HIGH" -> PermissionLevel.ASK;
            case "CRITICAL" -> "strict".equals(mode) ? PermissionLevel.DENY : PermissionLevel.ASK;
            default -> PermissionLevel.ASK;
        };
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> loadBuiltinRules() {
        if (builtinRulesCache != null) {
            return builtinRulesCache;
        }
        synchronized (PermissionEngine.class) {
            if (builtinRulesCache != null) {
                return builtinRulesCache;
            }
            try (InputStream inputStream = PermissionEngine.class.getClassLoader().getResourceAsStream(BUILTIN_RULES_RESOURCE)) {
                if (inputStream == null) {
                    builtinRulesCache = List.of();
                    return builtinRulesCache;
                }
                Object loaded = new Yaml().load(inputStream);
                if (loaded instanceof Map<?, ?> root && root.get("rules") instanceof List<?> list) {
                    List<Map<String, Object>> rules = new ArrayList<>();
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> map) {
                            rules.add((Map<String, Object>) map);
                        }
                    }
                    builtinRulesCache = rules;
                    return builtinRulesCache;
                }
            } catch (Exception ignored) {
                builtinRulesCache = List.of();
                return builtinRulesCache;
            }
            builtinRulesCache = List.of();
            return builtinRulesCache;
        }
    }
}
