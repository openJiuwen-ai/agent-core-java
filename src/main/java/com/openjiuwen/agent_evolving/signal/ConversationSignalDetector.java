/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.signal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.trajectory.LLMCallDetail;
import com.openjiuwen.agent_evolving.trajectory.ToolCallDetail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts evolution signals from conversation messages or trajectories.
 *
 * <p>Mirrors Python's {@code ConversationSignalDetector} in
 * {@code openjiuwen.agent_evolving.signal.from_conv}.</p>
 */
public class ConversationSignalDetector {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern FAILURE_KEYWORDS = Pattern.compile(
            "error(?!\\s*=\\s*None)|exception|traceback|failed|failure|timeout|timed out"
                    + "|errno|connectionerror|oserror|valueerror|typeerror"
                    + "|no such file|permission denied|access denied"
                    + "|command not found|not recognized|module not found"
                    + "|econnrefused|econnreset|enoent|enotfound|npm err!",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern CORRECTION_PATTERN = Pattern.compile(
            "that('s| is) (wrong|incorrect|not right)"
                    + "|you'?re wrong"
                    + "|should (be|use|have)"
                    + "|actually[,\\uFF0C]?"
                    + "|no[,\\uFF0C]? (wait|actually)"
                    + "|correct(ion)?:"
                    + "|fix(ed)?:"
                    + "|not correct"
                    + "|wrong method"
                    + "|\\u4e0d\\u5bf9"
                    + "|\\u5e94\\u8be5"
                    + "|\\u7ea0\\u6b63"
                    + "|\\u91cd\\u65b0",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SKILL_MD_PATTERN = Pattern.compile("[/\\\\]+([^/\\\\]+)[/\\\\]+SKILL\\.md",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TOOL_SCHEMA_PATTERN = Pattern.compile("\\{'content': '---\\\\nname: [^\\n]+\\\\ndescription:");
    private static final Pattern COLLABORATION_FAILURE_PATTERN = Pattern.compile(
            "member.*failed|member.*error|member.*timeout"
                    + "|invoke.*exception|spawn.*failed"
                    + "|task.*error|task.*timeout"
                    + "|collaboration.*failed",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Set<String> DATA_FETCH_TOOLS = Set.of(
            "mcp_fetch_webpage", "fetch_webpage", "web_fetch",
            "search", "web_search", "google_search", "bing_search",
            "view_file", "read_file", "cat_file",
            "list_directory", "ls", "get_url", "curl", "wget");
    private static final Set<String> CODE_EXEC_TOOLS = Set.of(
            "code", "bash", "execute_python_code", "run_python", "exec_code",
            "execute_code", "python_exec", "run_code");
    private static final List<String> EXEC_CONTENT_KEYS = List.of(
            "code", "code_block", "script", "source", "python_code", "command", "cmd", "shell_command");

    private final Set<String> existingSkills;

    public ConversationSignalDetector() {
        this(Set.of());
    }

    public ConversationSignalDetector(Set<String> existingSkills) {
        this.existingSkills = existingSkills != null ? new LinkedHashSet<>(existingSkills) : Set.of();
    }

    public List<EvolutionSignal> detect(Trajectory trajectory) {
        List<EvolutionSignal> signals = new ArrayList<>();
        if (trajectory == null) {
            return signals;
        }
        signals.addAll(detectFromMessages(convertTrajectoryToMessages(trajectory)));
        signals.addAll(detectCollaborationSignals(trajectory));
        return deduplicate(signals);
    }

    public List<EvolutionSignal> detect(List<Map<String, Object>> messages) {
        return deduplicate(detectFromMessages(messages != null ? messages : List.of()));
    }

    public static String makeSignalFingerprint(EvolutionSignal signal) {
        if (signal == null) {
            return "";
        }
        String excerpt = signal.getExcerpt() != null ? signal.getExcerpt() : "";
        String normalizedExcerpt = excerpt.length() > 200 ? excerpt.substring(0, 200) : excerpt;
        return String.join("|",
                nullToEmpty(signal.getSignalType()),
                nullToEmpty(signal.getToolName()),
                nullToEmpty(signal.getSkillName()),
                normalizedExcerpt);
    }

    private List<Map<String, Object>> convertTrajectoryToMessages(Trajectory trajectory) {
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, String> toolCallIdToName = new LinkedHashMap<>();

        for (TrajectoryStep step : safeSteps(trajectory)) {
            Object detail = step.getDetail();
            if ("llm".equals(step.getKind()) && detail instanceof LLMCallDetail llmDetail) {
                for (Map<String, Object> message : llmDetail.getMessages()) {
                    messages.add(message);
                    for (Map<String, Object> toolCall : asMapList(message.get("tool_calls"))) {
                        String id = stringValue(toolCall.get("id"));
                        String name = stringValue(toolCall.get("name"));
                        if (!id.isEmpty() && !name.isEmpty()) {
                            toolCallIdToName.put(id, name);
                        }
                    }
                }
            } else if ("tool".equals(step.getKind()) && detail instanceof ToolCallDetail toolDetail) {
                String toolName = nullToEmpty(toolDetail.getToolName());
                String toolCallId = firstNonEmpty(toolDetail.getToolCallId(), stringValue(step.getMeta().get("tool_call_id")));
                if (toolName.isEmpty() && !toolCallId.isEmpty()) {
                    toolName = toolCallIdToName.getOrDefault(toolCallId, "");
                }

                Map<String, Object> toolMessage = new LinkedHashMap<>();
                toolMessage.put("role", "tool");
                toolMessage.put("content", toolDetail.getCallResult() != null ? String.valueOf(toolDetail.getCallResult()) : "");
                if (!toolCallId.isEmpty()) {
                    toolMessage.put("tool_call_id", toolCallId);
                }
                if (!toolName.isEmpty()) {
                    toolMessage.put("name", toolName);
                }
                messages.add(toolMessage);
            }
        }
        return messages;
    }

    private List<EvolutionSignal> detectFromMessages(List<Map<String, Object>> messages) {
        List<EvolutionSignal> signals = new ArrayList<>();
        List<SkillRead> skillReadHistory = new ArrayList<>();
        Map<String, String> pendingScripts = new LinkedHashMap<>();
        Map<String, String> toolCallIdToName = new LinkedHashMap<>();

        for (int msgIdx = 0; msgIdx < messages.size(); msgIdx++) {
            Map<String, Object> message = messages.get(msgIdx);
            String role = stringValue(message.get("role"));
            String content = stringValue(message.get("content"));
            List<Map<String, Object>> toolCalls = asMapList(message.get("tool_calls"));

            if ("assistant".equals(role) && !toolCalls.isEmpty()) {
                int messageIndex = msgIdx;
                detectSkillFromToolCalls(toolCalls).ifPresent(skill -> skillReadHistory.add(new SkillRead(messageIndex, skill)));
                for (Map<String, Object> toolCall : toolCalls) {
                    String id = stringValue(toolCall.get("id"));
                    String name = stringValue(toolCall.get("name"));
                    if (!id.isEmpty() && !name.isEmpty()) {
                        toolCallIdToName.put(id, name);
                    }
                    if (CODE_EXEC_TOOLS.contains(name.toLowerCase(Locale.ROOT))) {
                        String code = extractCodeFromArgs(toolCall);
                        if (!code.isEmpty() && !id.isEmpty()) {
                            pendingScripts.put(id, code);
                        }
                    }
                }
            }

            if ("tool".equals(role) || "function".equals(role)) {
                String toolName = firstNonEmpty(
                        stringValue(message.get("name")),
                        stringValue(message.get("tool_name")));
                String toolCallId = stringValue(message.get("tool_call_id"));
                if (toolName.isEmpty() && !toolCallId.isEmpty()) {
                    toolName = toolCallIdToName.getOrDefault(toolCallId, "");
                }
                String activeSkill = resolveActiveSkill(msgIdx, skillReadHistory);

                if (!toolCallId.isEmpty() && pendingScripts.containsKey(toolCallId)) {
                    boolean hasFailure = FAILURE_KEYWORDS.matcher(content).find();
                    if (!hasFailure) {
                        signals.add(signal("script_artifact", "Scripts",
                                truncate(pendingScripts.get(toolCallId), 600), toolName, activeSkill, null));
                    }
                    pendingScripts.remove(toolCallId);
                }

                if (DATA_FETCH_TOOLS.contains(toolName.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                Matcher match = FAILURE_KEYWORDS.matcher(content);
                if (match.find() && !TOOL_SCHEMA_PATTERN.matcher(content).find()) {
                    signals.add(signal("execution_failure", "Troubleshooting",
                            extractAroundMatch(content, match), emptyToNull(toolName), activeSkill, null));
                }
            } else if ("user".equals(role)) {
                Matcher match = CORRECTION_PATTERN.matcher(content);
                if (match.find()) {
                    String activeSkill = resolveActiveSkill(msgIdx, skillReadHistory);
                    signals.add(signal("user_correction", "Examples",
                            extractAroundMatch(content, match), null, activeSkill, null));
                }
            }
        }
        return signals;
    }

    private List<EvolutionSignal> detectCollaborationSignals(Trajectory trajectory) {
        if (!isTeamMemberContext(trajectory)) {
            return List.of();
        }

        List<EvolutionSignal> signals = new ArrayList<>();
        Map<String, Object> meta = trajectory.getMeta() != null ? trajectory.getMeta() : Map.of();
        String memberId = stringValue(meta.getOrDefault("member_id", "unknown"));
        List<SkillRead> skillReadHistory = buildSkillReadHistory(trajectory);
        List<TrajectoryStep> steps = safeSteps(trajectory);

        for (int idx = 0; idx < steps.size(); idx++) {
            TrajectoryStep step = steps.get(idx);
            if (!"tool".equals(step.getKind()) || !(step.getDetail() instanceof ToolCallDetail detail)) {
                continue;
            }
            String toolName = nullToEmpty(detail.getToolName()).toLowerCase(Locale.ROOT);
            String activeSkill = resolveActiveSkill(idx, skillReadHistory);
            String callArgs = stringifyArgs(detail.getCallArgs());

            if ("send_message".equals(toolName)) {
                String toMember = extractArg(callArgs, "to_member_name", "to");
                if (!toMember.isEmpty() && !Objects.equals(toMember, memberId)) {
                    signals.add(signal("collaboration_send", "Collaboration",
                            "send message to member " + toMember, toolName, activeSkill,
                            Map.of("from_member", memberId, "to_member", toMember)));
                }
            }

            if ("claim_task".equals(toolName)) {
                String taskId = extractArg(callArgs, "task_id");
                if (!taskId.isEmpty()) {
                    signals.add(signal("collaboration_claim", "Collaboration",
                            "claim task " + taskId, toolName, activeSkill,
                            Map.of("member_id", memberId, "task_id", taskId)));
                }
            }

            if ("view_task".equals(toolName)) {
                signals.add(signal("collaboration_view", "Collaboration",
                        "view team task status", toolName, activeSkill, Map.of("member_id", memberId)));
            }

            if (step.getMeta() != null && step.getMeta().containsKey("parent_invoke_id")) {
                String parentId = stringValue(step.getMeta().get("parent_invoke_id"));
                signals.add(signal("collaboration_receive", "Collaboration",
                        "receive context from " + parentId, null, activeSkill,
                        Map.of("member_id", memberId, "parent_invoke_id", parentId)));
            }

            String result = detail.getCallResult() != null ? String.valueOf(detail.getCallResult()) : "";
            Matcher match = COLLABORATION_FAILURE_PATTERN.matcher(result);
            if (match.find()) {
                signals.add(signal("collaboration_failure", "Collaboration",
                        extractAroundMatch(result, match), toolName, activeSkill, Map.of("member_id", memberId)));
            }
        }

        return signals;
    }

    private List<SkillRead> buildSkillReadHistory(Trajectory trajectory) {
        List<SkillRead> skillReads = new ArrayList<>();
        List<TrajectoryStep> steps = safeSteps(trajectory);
        for (int idx = 0; idx < steps.size(); idx++) {
            Object detail = steps.get(idx).getDetail();
            if (!"llm".equals(steps.get(idx).getKind()) || !(detail instanceof LLMCallDetail llmDetail)) {
                continue;
            }
            for (Map<String, Object> message : llmDetail.getMessages()) {
                List<Map<String, Object>> toolCalls = asMapList(message.get("tool_calls"));
                int stepIndex = idx;
                detectSkillFromToolCalls(toolCalls).ifPresent(skill -> skillReads.add(new SkillRead(stepIndex, skill)));
            }
        }
        return skillReads;
    }

    private Optional<String> detectSkillFromToolCalls(List<Map<String, Object>> toolCalls) {
        for (Map<String, Object> toolCall : toolCalls) {
            String name = stringValue(toolCall.get("name")).toLowerCase(Locale.ROOT);
            String arguments = stringValue(toolCall.get("arguments"));
            String skillName = "";

            if (name.contains("file") || name.contains("read")) {
                Matcher matcher = SKILL_MD_PATTERN.matcher(arguments);
                if (matcher.find()) {
                    skillName = matcher.group(1);
                }
            } else if ("skill_tool".equals(name)) {
                skillName = extractArg(arguments, "skill_name");
            }

            if (!skillName.isEmpty() && (existingSkills.isEmpty() || existingSkills.contains(skillName))) {
                return Optional.of(skillName);
            }
        }
        return Optional.empty();
    }

    private static List<TrajectoryStep> safeSteps(Trajectory trajectory) {
        return trajectory.getSteps() != null ? trajectory.getSteps() : List.of();
    }

    private static List<Map<String, Object>> asMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> converted = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    converted.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                result.add(converted);
            }
        }
        return result;
    }

    private static String extractCodeFromArgs(Map<String, Object> toolCall) {
        Object raw = toolCall.get("arguments");
        Map<String, Object> args = parseArgs(raw);
        if (args.isEmpty()) {
            return "";
        }
        for (String key : EXEC_CONTENT_KEYS) {
            Object value = args.get(key);
            if (value instanceof String text && text.strip().length() > 20) {
                return text;
            }
        }
        return "";
    }

    private static String extractArg(String rawArgs, String... keys) {
        Map<String, Object> parsed = parseArgs(rawArgs);
        for (String key : keys) {
            Object value = parsed.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        for (String key : keys) {
            Pattern pattern = Pattern.compile(key + "[\"']?\\s*[:=]\\s*[\"']?([^,\"'}\\s]+)[\"']?");
            Matcher matcher = pattern.matcher(rawArgs);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "";
    }

    private static Map<String, Object> parseArgs(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        if (!(raw instanceof String text) || text.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(text, new TypeReference<>() {
            });
        } catch (JsonProcessingException ignored) {
            return Map.of();
        }
    }

    private static String stringifyArgs(Object raw) {
        if (raw instanceof String text) {
            return text;
        }
        if (raw instanceof Map<?, ?> map) {
            try {
                return MAPPER.writeValueAsString(map);
            } catch (JsonProcessingException ignored) {
                return String.valueOf(raw);
            }
        }
        return raw != null ? String.valueOf(raw) : "";
    }

    private static boolean isTeamMemberContext(Trajectory trajectory) {
        Map<String, Object> meta = trajectory.getMeta();
        if (meta == null || meta.isEmpty()) {
            return false;
        }
        if (meta.containsKey("member_id") && !"standalone".equals(meta.get("source"))) {
            return true;
        }
        return meta.containsKey("parent_invoke_id")
                || meta.containsKey("source_invoke_id")
                || meta.containsKey("target_invoke_id")
                || meta.containsKey("from_member")
                || meta.containsKey("to_member");
    }

    private static String resolveActiveSkill(int msgIdx, List<SkillRead> skillReadHistory) {
        for (int i = skillReadHistory.size() - 1; i >= 0; i--) {
            SkillRead read = skillReadHistory.get(i);
            if (read.index <= msgIdx) {
                return read.skillName;
            }
        }
        return null;
    }

    private static EvolutionSignal signal(String type, String section, String excerpt,
                                          String toolName, String skillName, Map<String, Object> context) {
        return EvolutionSignal.builder()
                .signalType(type)
                .evolutionType(EvolutionCategory.SKILL_EXPERIENCE)
                .section(section)
                .excerpt(excerpt)
                .toolName(emptyToNull(toolName))
                .skillName(emptyToNull(skillName))
                .context(context)
                .build();
    }

    private static List<EvolutionSignal> deduplicate(List<EvolutionSignal> signals) {
        List<EvolutionSignal> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (EvolutionSignal signal : signals) {
            String key = makeSignalFingerprint(signal);
            if (seen.add(key)) {
                result.add(signal);
            }
        }
        return result;
    }

    private static String extractAroundMatch(String content, Matcher match) {
        int start = Math.max(0, match.start() - 300);
        int end = Math.min(content.length(), match.end() + 300);
        return content.substring(start, end);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private record SkillRead(int index, String skillName) {
    }
}
