/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.signal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agentevolving.trajectory.LLMCallDetail;
import com.openjiuwen.agentevolving.trajectory.ToolCallDetail;
import com.openjiuwen.agentevolving.trajectory.Trajectory;
import com.openjiuwen.agentevolving.trajectory.TrajectoryStep;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Team-domain signal helper functions.
 *
 * <p>Mirrors Python's module-level helpers in
 * {@code openjiuwen/agent_evolving/signal/team.py}.</p>
 */
public final class TeamSignals {

    public static final String TEAM_TRAJECTORY_ISSUES_KEY = "trajectory_issues";
    public static final String TEAM_SKILL_CONTENT_KEY = "skill_content";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern JSON_BLOCK_RE = Pattern.compile("```(?:json)?\\s*\\n([\\s\\S]*?)```");
    private static final int TOOL_BUDGET = 20_000;
    private static final int LLM_BUDGET = 10_000;
    private static final Set<String> KEY_TOOLS = Set.of(
            "spawn_member",
            "create_task",
            "build_team",
            "view_task",
            "send_message"
    );

    private TeamSignals() {
    }

    public static Object parseTeamModelJson(String raw) {
        if (raw == null || raw.isEmpty()) {
            Object none = null;
            return none;
        }

        List<String> candidates = new ArrayList<>();
        Matcher match = JSON_BLOCK_RE.matcher(raw);
        if (match.find()) {
            candidates.add(match.group(1).strip());
        }
        candidates.add(raw.strip());
        candidates.add(fixJsonText(raw));

        String balancedObject = extractBalancedJson(raw, '{', '}');
        if (balancedObject != null) {
            candidates.add(balancedObject);
            candidates.add(fixJsonText(balancedObject));
        }

        String balancedArray = extractBalancedJson(raw, '[', ']');
        if (balancedArray != null) {
            candidates.add(balancedArray);
            candidates.add(fixJsonText(balancedArray));
        }

        Set<String> seen = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty() || seen.contains(candidate)) {
                continue;
            }
            seen.add(candidate);
            Object parsed = tryParseJson(candidate);
            if (parsed instanceof Map<?, ?> || parsed instanceof List<?>) {
                return parsed;
            }
        }

        String head = raw.substring(0, Math.min(600, raw.length())).replace("\n", "\\n");
        Loggers.AGENT.warning("[TeamSignal] JSON parse failed (raw_len={} head={})", raw.length(), head);
        Object none = null;
        return none;
    }

    public static String buildTeamTrajectorySummary(Trajectory trajectory) {
        List<String> toolLines = new ArrayList<>();
        List<String> llmLines = new ArrayList<>();
        int toolCount = 0;
        int llmCount = 0;

        List<TrajectoryStep> steps = trajectory == null || trajectory.getSteps() == null
                ? List.of()
                : trajectory.getSteps();
        for (TrajectoryStep step : steps) {
            if (step == null || step.getDetail() == null) {
                continue;
            }
            if ("tool".equals(step.getKind())) {
                toolCount += 1;
                String toolName = toolName(step.getDetail());
                boolean keyTool = KEY_TOOLS.contains(toolName);
                String args = limit(String.valueOf(callArgs(step.getDetail())), keyTool ? 500 : 150);
                String result = limit(String.valueOf(callResult(step.getDetail())), keyTool ? 500 : 200);
                toolLines.add("[Tool:" + toolName + "] args=" + args + " result=" + result);
            } else if ("llm".equals(step.getKind())) {
                llmCount += 1;
                Object response = llmResponse(step.getDetail());
                if (response != null) {
                    llmLines.add("[LLM] " + limit(String.valueOf(response), 300));
                }
            }
        }

        String toolSection = String.join("\n", toolLines);
        if (toolSection.length() > TOOL_BUDGET) {
            toolSection = toolSection.substring(0, TOOL_BUDGET) + "\n... (tool section truncated)";
        }

        String llmSection = String.join("\n", llmLines);
        if (llmSection.length() > LLM_BUDGET) {
            llmSection = llmSection.substring(0, LLM_BUDGET) + "\n... (LLM section truncated)";
        }

        String summary = "### Tool Calls (" + toolCount + ")\n" + toolSection
                + "\n\n### LLM Responses (" + llmCount + ")\n" + llmSection;
        Loggers.AGENT.info(
                "[TeamSignal] trajectory summary: {} LLM steps, {} tool steps, tool_section_len={}, "
                        + "llm_section_len={}, total_len={}",
                llmCount,
                toolCount,
                toolSection.length(),
                llmSection.length(),
                summary.length());
        return summary;
    }

    public static EvolutionSignal makeTeamUserIntentSignal(String skillName, String userIntent) {
        return EvolutionSignals.makeEvolutionSignal(
                TeamSignalType.USER_INTENT.getValue(),
                "Instructions",
                userIntent,
                null,
                skillName,
                "explicit_request",
                null);
    }

    public static EvolutionSignal makeTeamTrajectorySignal(
            String skillName,
            String skillContent,
            List<Map<String, String>> trajectoryIssues
    ) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put(TEAM_TRAJECTORY_ISSUES_KEY, trajectoryIssues == null ? List.of() : List.copyOf(trajectoryIssues));
        context.put(TEAM_SKILL_CONTENT_KEY, skillContent);
        return EvolutionSignals.makeEvolutionSignal(
                TeamSignalType.TRAJECTORY_ISSUE.getValue(),
                "",
                "Detected team skill trajectory issues requiring evolution.",
                null,
                skillName,
                "passive_trajectory",
                context);
    }

    public static List<Map<String, String>> getTeamTrajectoryIssues(EvolutionSignal signal) {
        Map<String, Object> context = signal == null || signal.getContext() == null ? Map.of() : signal.getContext();
        Object issues = context.get(TEAM_TRAJECTORY_ISSUES_KEY);
        if (!(issues instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, String>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, String> normalized = new LinkedHashMap<>();
                map.forEach((key, value) -> normalized.put(String.valueOf(key), value == null ? null : String.valueOf(value)));
                result.add(normalized);
            }
        }
        return result;
    }

    public static String getTeamSignalSkillContent(EvolutionSignal signal) {
        Map<String, Object> context = signal == null || signal.getContext() == null ? Map.of() : signal.getContext();
        Object skillContent = context.get(TEAM_SKILL_CONTENT_KEY);
        return skillContent != null ? String.valueOf(skillContent) : null;
    }

    static String extractRolesSummary(String teamSkillContent) {
        if (teamSkillContent == null || teamSkillContent.isEmpty()) {
            return "";
        }

        String[] lines = teamSkillContent.split("\\R");
        List<String> roleLines = new ArrayList<>();
        boolean inRoles = false;
        for (String line : lines) {
            String stripped = line.strip();
            String lowered = stripped.toLowerCase();
            if (lowered.startsWith("roles:")) {
                String value = stripped.substring(stripped.indexOf(':') + 1).strip();
                if (!value.isEmpty()) {
                    roleLines.add(value);
                }
                inRoles = true;
                continue;
            }
            if (inRoles) {
                if (stripped.isEmpty()) {
                    continue;
                }
                if (stripped.startsWith("-") || line.startsWith(" ") || line.startsWith("\t")) {
                    roleLines.add(stripped);
                    continue;
                }
                break;
            }
        }

        if (roleLines.isEmpty()) {
            for (String line : lines) {
                String stripped = line.strip();
                String lowered = stripped.toLowerCase();
                if (lowered.startsWith("role:") || lowered.startsWith("角色：") || lowered.startsWith("角色:")) {
                    roleLines.add(stripped);
                }
                if (roleLines.size() >= 5) {
                    break;
                }
            }
        }
        return limit(String.join("\n", roleLines), 500);
    }

    static Map<String, String> normalizeIssue(Object item) {
        if (!(item instanceof Map<?, ?> map)) {
            Map<String, String> none = null;
            return none;
        }

        Object rawSeverity = map.containsKey("severity") ? map.get("severity") : "medium";
        String severity = stringValue(rawSeverity);
        if (!List.of("low", "medium", "high").contains(severity)) {
            severity = "medium";
        }
        Map<String, String> result = new LinkedHashMap<>();
        result.put("issue_type", defaultString(map.get("issue_type"), "unknown"));
        result.put("description", defaultString(map.get("description"), ""));
        result.put("affected_role", defaultString(map.get("affected_role"), ""));
        result.put("severity", severity);
        return result;
    }

    static String limit(String value, int maxChars) {
        String text = value == null ? "" : value;
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }

    private static Object tryParseJson(String text) {
        try {
            return MAPPER.readValue(text, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            Object none = null;
            return none;
        }
    }

    private static String fixJsonText(String text) {
        String value = text == null ? "" : text.strip();
        value = value.replaceAll("(?m)^```(?:json)?\\s*", "");
        value = value.replaceAll("(?m)```\\s*$", "");
        value = value.replaceAll("//[^\\n]*", "");
        value = value.replaceAll(",\\s*([}\\]])", "$1");
        return value.strip();
    }

    private static String extractBalancedJson(String text, char opener, char closer) {
        if (text == null) {
            String none = null;
            return none;
        }
        int start = text.indexOf(opener);
        if (start < 0) {
            String none = null;
            return none;
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (ch == '\\') {
                    escape = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == opener) {
                depth += 1;
            } else if (ch == closer) {
                depth -= 1;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        String none = null;
        return none;
    }

    private static String toolName(Object detail) {
        if (detail instanceof ToolCallDetail toolCallDetail && toolCallDetail.getToolName() != null) {
            return toolCallDetail.getToolName();
        }
        if (detail instanceof Map<?, ?> map && map.get("tool_name") != null) {
            return String.valueOf(map.get("tool_name"));
        }
        return "unknown";
    }

    private static Object callArgs(Object detail) {
        if (detail instanceof ToolCallDetail toolCallDetail) {
            return toolCallDetail.getCallArgs();
        }
        if (detail instanceof Map<?, ?> map) {
            return map.get("call_args");
        }
        return "";
    }

    private static Object callResult(Object detail) {
        if (detail instanceof ToolCallDetail toolCallDetail) {
            return toolCallDetail.getCallResult();
        }
        if (detail instanceof Map<?, ?> map) {
            return map.get("call_result");
        }
        return "";
    }

    private static Object llmResponse(Object detail) {
        if (detail instanceof LLMCallDetail llmCallDetail) {
            return llmCallDetail.getResponse();
        }
        if (detail instanceof Map<?, ?> map) {
            return map.get("response");
        }
        Object none = null;
        return none;
    }

    private static String defaultString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String resolved = String.valueOf(value);
        return resolved.isEmpty() ? fallback : resolved;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
