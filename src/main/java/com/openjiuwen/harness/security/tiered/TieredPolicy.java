/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security.tiered;

import com.openjiuwen.harness.security.PermissionLevel;
import com.openjiuwen.harness.security.PermissionResult;
import com.openjiuwen.harness.security.shellast.ShellAst;
import com.openjiuwen.harness.security.shellast.ShellAstParseResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tiered tool permission policy (Pipeline A).
 *
 * <p>Mirrors Python {@code openjiuwen.harness.security.tiered_policy.evaluate_tiered_policy}.
 * Evaluates parameter-level rules in strict precedence: built-in rules &gt; user rules &gt;
 * approval overrides &gt; baseline ({@code tools.<name>}) &gt; {@code defaults.*}. A whole-tool
 * {@code deny} short-circuits. For shell tools, a conservative AST floor upgrades risky or
 * unparseable structures to ASK, and simple subcommands are aggregated with {@code strictest}.
 * Path-class rules are skipped here (handled by {@code file_guard}, Pipeline B).
 *
 * @since 0.1.15
 */
public final class TieredPolicy {

    private static final String MR = "tiered_policy";
    private static final String APPROVAL_OVERRIDES_PREFIX = MR + ":approval_overrides";

    private TieredPolicy() {
    }

    /**
     * Evaluate the tiered policy for a tool call.
     *
     * @param config   permissions config map
     * @param toolName tool name
     * @param toolArgs tool arguments
     * @return permission result with level and matched-rule summary
     * @since 0.1.15
     */
    public static PermissionResult evaluate(Map<String, Object> config, String toolName,
            Map<String, Object> toolArgs) {
        Map<String, Object> cfg = config != null ? config : Map.of();
        Map<String, Object> args = toolArgs != null ? toolArgs : Map.of();
        String mode = strOr(cfg.get("permission_mode"), "normal").trim().toLowerCase(Locale.ROOT);
        if (!"strict".equals(mode)) {
            mode = "normal";
        }

        Map<String, Object> toolsCfg = asMap(cfg.get("tools"));
        Map<String, Object> defaultsCfg = asMap(cfg.get("defaults"));
        List<Map<String, Object>> rules = asListOfMaps(cfg.get("rules"));
        List<Map<String, Object>> approvalOverrides = asListOfMaps(cfg.get("approval_overrides"));

        Pair baseline = baselineLevel(toolsCfg, toolName);
        if (baseline.level == PermissionLevel.DENY) {
            return result(PermissionLevel.DENY, baseline.rule);
        }

        ShellAstParseResult shellParse = null;
        if ("shell".equals(ToolCategory.of(toolName))) {
            shellParse = ShellAst.parse(commandText(args));
        }
        Pair floor = shellAstFloor(shellParse);

        InvocationContext ctx = new InvocationContext(mode, BuiltinRules.get(), rules,
                approvalOverrides, baseline.level, baseline.rule, defaultsCfg);

        Pair decision;
        if ("shell".equals(ToolCategory.of(toolName))
                && shellParse != null && "simple".equals(shellParse.getKind())) {
            decision = aggregateSubcommands(shellParse, toolName, args, ctx);
        } else {
            decision = evaluateSingleInvocation(toolName, args, ctx);
        }
        decision = applyFloor(decision, floor);
        return result(decision.level, decision.rule);
    }

    // ---------- single invocation ----------

    private static Pair evaluateSingleInvocation(String toolName, Map<String, Object> toolArgs,
            InvocationContext ctx) {
        List<Hit> builtinHits =
                collectParamRuleHits(ctx.builtinRules, toolName, toolArgs, ctx.mode, "builtin");
        if (anyDeny(builtinHits)) {
            return finalizeHits(builtinHits, "builtin");
        }
        List<Hit> userHits =
                collectParamRuleHits(ctx.rules, toolName, toolArgs, ctx.mode, "rules");
        if (anyDeny(userHits)) {
            return finalizeHits(userHits, "rules");
        }
        List<String> overrideHits =
                collectApprovalOverrideHits(ctx.approvalOverrides, toolName, toolArgs);
        if (!overrideHits.isEmpty()) {
            return new Pair(PermissionLevel.ALLOW,
                    APPROVAL_OVERRIDES_PREFIX + ":" + String.join("+", sortedDistinct(overrideHits)));
        }
        if (!builtinHits.isEmpty()) {
            return finalizeHits(builtinHits, "builtin");
        }
        if (!userHits.isEmpty()) {
            return finalizeHits(userHits, "rules");
        }
        if (ctx.baselineLevel != null) {
            return new Pair(ctx.baselineLevel, ctx.baselineRule != null ? ctx.baselineRule : MR + ":tools");
        }
        Object star = defaultsCfgStar(ctx.defaultsCfg);
        if (star instanceof String s && !s.isBlank()) {
            return new Pair(PermissionLevel.fromValue(s), MR + ":defaults.*");
        }
        return new Pair(PermissionLevel.ASK, MR + ":fallback(no_config)");
    }

    private static List<Hit> collectParamRuleHits(List<Map<String, Object>> rules, String toolName,
            Map<String, Object> toolArgs, String mode, String labelNs) {
        List<Hit> hits = new ArrayList<>();
        for (Map<String, Object> rule : rules) {
            List<String> ruleTools = toStringList(rule.get("tools"));
            if (!ruleTools.contains(toolName)) {
                continue;
            }
            if (!ToolCategory.ruleToolsCategoryConsistent(ruleTools)) {
                continue;
            }
            if (!ruleTools.isEmpty()
                    && "path".equals(ToolCategory.of(ruleTools.get(0)))) {
                continue;
            }
            Object patternObj = rule.get("pattern");
            if (!(patternObj instanceof String pattern) || pattern.isBlank()) {
                continue;
            }
            if (!ruleMatches(toolName, pattern, toolArgs, ruleTools)) {
                continue;
            }
            PermissionLevel level;
            Object action = rule.get("action");
            if (action instanceof String a && !a.isBlank()) {
                level = PermissionLevel.fromValue(a);
            } else {
                level = SeverityMapping.severityToDecision(strOr(rule.get("severity"), "HIGH"), mode);
            }
            String id = strOr(rule.get("id"), "");
            String label = id.isEmpty() ? labelNs + "[?]" : labelNs + "[" + id + "]";
            hits.add(new Hit(level, label));
        }
        return hits;
    }

    private static List<String> collectApprovalOverrideHits(List<Map<String, Object>> rules,
            String toolName, Map<String, Object> toolArgs) {
        List<String> hits = new ArrayList<>();
        for (Map<String, Object> rule : rules) {
            String action = strOr(rule.get("action"), "").toLowerCase(Locale.ROOT);
            if (!"allow".equals(action)) {
                continue;
            }
            String matchType = strOr(rule.get("match_type"), "").toLowerCase(Locale.ROOT);
            if ("path".equals(matchType)) {
                continue;
            }
            List<String> ruleTools = toStringList(rule.get("tools"));
            if (!ruleTools.contains(toolName)) {
                continue;
            }
            if (!ToolCategory.ruleToolsCategoryConsistent(ruleTools)) {
                continue;
            }
            if (!ruleTools.isEmpty()
                    && "path".equals(ToolCategory.of(ruleTools.get(0)))) {
                continue;
            }
            Object patternObj = rule.get("pattern");
            if (!(patternObj instanceof String pattern) || pattern.isBlank()) {
                continue;
            }
            if (!ruleMatches(toolName, pattern, toolArgs, ruleTools)) {
                continue;
            }
            String id = strOr(rule.get("id"), "");
            hits.add(id.isEmpty() ? "approval_overrides[?]" : "approval_overrides[" + id + "]");
        }
        return hits;
    }

    private static boolean ruleMatches(String toolName, String pattern,
            Map<String, Object> toolArgs, List<String> ruleTools) {
        if (ruleTools.isEmpty()) {
            return false;
        }
        String category = ToolCategory.of(ruleTools.get(0));
        if ("shell".equals(category)) {
            return RuleMatcher.shellMatches(pattern, commandText(toolArgs));
        }
        return false;
    }

    private static Pair finalizeHits(List<Hit> hits, String prefix) {
        if (hits.stream().anyMatch(h -> h.level == PermissionLevel.DENY)) {
            List<String> contributing = sortedDistinct(hits.stream()
                    .filter(h -> h.level == PermissionLevel.DENY)
                    .map(h -> h.label)
                    .toList());
            return new Pair(PermissionLevel.DENY, MR + ":" + prefix + ":deny:" + String.join("+", contributing));
        }
        PermissionLevel finalLevel = strictest(hits.stream().map(h -> h.level).toList());
        List<String> contributing = sortedDistinct(hits.stream()
                .filter(h -> h.level == finalLevel)
                .map(h -> h.label)
                .toList());
        String matched = contributing.isEmpty()
                ? MR + ":" + prefix
                : MR + ":" + prefix + ":" + String.join("+", contributing);
        return new Pair(finalLevel, matched);
    }

    // ---------- shell AST floor + subcommands ----------

    private static Pair shellAstFloor(ShellAstParseResult shellParse) {
        if (shellParse == null) {
            return null;
        }
        var flags = shellParse.getFlags();
        if ("too_complex".equals(shellParse.getKind())) {
            return new Pair(PermissionLevel.ASK,
                    MR + ":shell_ast:too_complex:" + orDefault(shellParse.getReason(), "unsupported_complex_structure"));
        }
        if ("parse_unavailable".equals(shellParse.getKind()) && flags.hasRiskyStructure()) {
            return new Pair(PermissionLevel.ASK,
                    MR + ":shell_ast:parse_unavailable:" + orDefault(shellParse.getReason(), "conservative_fallback"));
        }
        if (flags.isInputRedirection() || flags.isOutputRedirection()
                || flags.isCommandSubstitution() || flags.isProcessSubstitution()
                || flags.isHeredoc()) {
            return new Pair(PermissionLevel.ASK, MR + ":shell_ast:structure_guard");
        }
        return null;
    }

    private static Pair aggregateSubcommands(ShellAstParseResult shellParse, String toolName,
            Map<String, Object> toolArgs, InvocationContext ctx) {
        List<Pair> subDecisions = new ArrayList<>();
        List<String> subTexts = new ArrayList<>();
        for (var sub : shellParse.getSubcommands()) {
            if (sub.getText() == null || sub.getText().isBlank()) {
                continue;
            }
            Map<String, Object> subArgs = withCommand(toolArgs, sub.getText());
            Pair subDecision = evaluateSingleInvocation(toolName, subArgs, ctx);
            subDecisions.add(subDecision);
            subTexts.add(sub.getText());
            if (subDecision.level == PermissionLevel.DENY) {
                break;
            }
        }
        if (subDecisions.isEmpty()) {
            return new Pair(PermissionLevel.ASK, MR + ":shell_subcommands:fallback");
        }
        if (subDecisions.size() == 1) {
            return subDecisions.get(0);
        }
        PermissionLevel finalLevel = strictest(subDecisions.stream().map(p -> p.level).toList());
        List<String> contributing = new ArrayList<>();
        for (int i = 0; i < subDecisions.size(); i++) {
            if (subDecisions.get(i).level == finalLevel) {
                contributing.add(subTexts.get(i) + "=>" + subDecisions.get(i).rule);
            }
        }
        String matched = contributing.isEmpty()
                ? MR + ":shell_subcommands"
                : MR + ":shell_subcommands:" + String.join("+", sortedDistinct(contributing));
        return new Pair(finalLevel, matched);
    }

    private static Map<String, Object> withCommand(Map<String, Object> toolArgs, String command) {
        Map<String, Object> copy = new LinkedHashMap<>(toolArgs != null ? toolArgs : Map.of());
        copy.put("command", command);
        return copy;
    }

    private static Pair applyFloor(Pair decision, Pair floor) {
        if (floor == null) {
            return decision;
        }
        PermissionLevel merged = strictest(List.of(decision.level, floor.level));
        if (merged == decision.level) {
            return decision;
        }
        String rule = (decision.rule != null && floor.rule != null)
                ? floor.rule + "|" + decision.rule
                : (floor.rule != null ? floor.rule : decision.rule);
        return new Pair(merged, rule);
    }

    // ---------- helpers ----------

    private static Pair baselineLevel(Map<String, Object> toolsCfg, String toolName) {
        if (!toolsCfg.containsKey(toolName)) {
            return new Pair(null, null);
        }
        Object raw = toolsCfg.get(toolName);
        if (raw instanceof String s) {
            return new Pair(PermissionLevel.fromValue(s), "tools." + toolName);
        }
        if (raw instanceof Map<?, ?> m && m.get("*") instanceof String star) {
            return new Pair(PermissionLevel.fromValue(star), "tools." + toolName + ".*");
        }
        return new Pair(null, null);
    }

    private static String commandText(Map<String, Object> toolArgs) {
        Object cmd = toolArgs.get("command");
        if (cmd == null || cmd.toString().isBlank()) {
            cmd = toolArgs.get("cmd");
        }
        return cmd == null ? "" : cmd.toString().trim();
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

    private static boolean anyDeny(List<Hit> hits) {
        return hits.stream().anyMatch(h -> h.level == PermissionLevel.DENY);
    }

    private static List<String> sortedDistinct(List<String> in) {
        return in.stream().distinct().sorted().toList();
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static String strOr(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String s = String.valueOf(value);
        return s;
    }

    private static Object defaultsCfgStar(Map<String, Object> defaultsCfg) {
        return defaultsCfg.get("*");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asListOfMaps(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    out.add((Map<String, Object>) map);
                }
            }
            return out;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    out.add(String.valueOf(item));
                }
            }
            return out;
        }
        if (value instanceof String s) {
            return List.of(s);
        }
        return List.of();
    }

    private static PermissionResult result(PermissionLevel level, String matchedRule) {
        String reason;
        if (level == PermissionLevel.ALLOW) {
            reason = "Allowed by rule: " + matchedRule;
        } else if (level == PermissionLevel.DENY) {
            reason = "Denied by rule: " + matchedRule;
        } else {
            reason = "Approval required (rule: " + matchedRule + ")";
        }
        return PermissionResult.builder()
                .permission(level == null ? PermissionLevel.ASK : level)
                .matchedRule(matchedRule)
                .reason(reason)
                .build();
    }

    private record Hit(PermissionLevel level, String label) {
    }

    private record Pair(PermissionLevel level, String rule) {
    }

    private static final class InvocationContext {
        final String mode;
        final List<Map<String, Object>> builtinRules;
        final List<Map<String, Object>> rules;
        final List<Map<String, Object>> approvalOverrides;
        final PermissionLevel baselineLevel;
        final String baselineRule;
        final Map<String, Object> defaultsCfg;

        InvocationContext(String mode, List<Map<String, Object>> builtinRules,
                List<Map<String, Object>> rules, List<Map<String, Object>> approvalOverrides,
                PermissionLevel baselineLevel, String baselineRule,
                Map<String, Object> defaultsCfg) {
            this.mode = mode;
            this.builtinRules = builtinRules;
            this.rules = rules;
            this.approvalOverrides = approvalOverrides;
            this.baselineLevel = baselineLevel;
            this.baselineRule = baselineRule;
            this.defaultsCfg = defaultsCfg;
        }
    }
}
