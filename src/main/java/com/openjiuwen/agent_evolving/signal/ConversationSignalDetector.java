/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.signal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.Protocols;
import com.openjiuwen.agent_evolving.trajectory.LLMCallDetail;
import com.openjiuwen.agent_evolving.trajectory.ToolCallDetail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts evolution signals from conversation messages or trajectories.
 *
 * <p>Mirrors Python's {@code ConversationSignalDetector} in
 * {@code openjiuwen/agent_evolving/signal/from_conv.py}.</p>
 */
public class ConversationSignalDetector {

    /**
     * Minimal async LLM boundary for passive user-feedback detection.
     *
     * <p>This remains intentionally narrow because the Python source accepts arbitrary
     * runtime-bound LLM objects.</p>
     */
    @FunctionalInterface
    public interface LlmInvoker {
        CompletionStage<Object> invoke(String model, List<Map<String, Object>> messages, int timeoutSeconds);
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger LOGGER = Logger.getLogger(ConversationSignalDetector.class.getName());

    private static final Pattern FAILURE_KEYWORDS = Pattern.compile(
            "error(?!\\s*=\\s*None)|exception|traceback|failed|failure|timeout|timed out"
                    + "|errno|connectionerror|oserror|valueerror|typeerror"
                    + "|\\u9519\\u8bef|\\u5f02\\u5e38|\\u5931\\u8d25|\\u8d85\\u65f6"
                    + "|no such file|permission denied|access denied"
                    + "|command not found|not recognized|module not found"
                    + "|econnrefused|econnreset|enoent|enotfound|npm err!",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern CORRECTION_PATTERN = Pattern.compile(
            "\\u4e0d\\u5bf9[\\uff0c,;:! ]?"
                    + "|\\u4e0d\\u662f\\u8fd9|\\u4e0d\\u662f\\u90a3"
                    + "|\\u5e94\\u8be5"
                    + "|\\u4f60\\u641e\\u9519\\u4e86"
                    + "|\\u91cd\\u65b0"
                    + "|\\u7406\\u89e3\\u9519"
                    + "|that('s| is) (wrong|incorrect|not right)"
                    + "|you'?re wrong"
                    + "|should (be|use|have)"
                    + "|actually[,\\uFF0C]?"
                    + "|no[,\\uFF0C]? (wait|actually)"
                    + "|correct(ion)?:"
                    + "|fix(ed)?:",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SKILL_MD_PATTERN = Pattern.compile("[/\\\\]+([^/\\\\]+)[/\\\\]+SKILL\\.md",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TOOL_SCHEMA_PATTERN = Pattern.compile("\\{'content': '---\\\\nname: [^\\n]+\\\\ndescription:");
    private static final Pattern COLLABORATION_FAILURE_PATTERN = Pattern.compile(
            "member.*failed|member.*error|member.*timeout"
                    + "|invoke.*exception|spawn.*failed"
                    + "|task.*error|task.*timeout"
                    + "|collaboration.*failed"
                    + "|\\u534f\\u4f5c.*\\u5931\\u8d25|\\u6210\\u5458.*\\u5f02\\u5e38|\\u4efb\\u52a1.*\\u8d85\\u65f6",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final String USER_FEEDBACK_PROMPT_CN =
            "\u5224\u65ad\u4ee5\u4e0b\u7528\u6237\u6d88\u606f\u662f\u5426\u5305\u542b\u5bf9\u5f53\u524d skill "
                    + "\u7684\u88ab\u52a8\u7ea0\u6b63\u6216\u53ef\u6c89\u6dc0\u7684\u6539\u8fdb\u53cd\u9988\u3002\\n"
                    + "\u53ea\u6709\u5f53\u7528\u6237\u6d88\u606f\u660e\u786e\u6307\u51fa agent \u7684\u7406\u89e3\u3001"
                    + "\u6b65\u9aa4\u3001\u987a\u5e8f\u6216\u5de5\u5177\u4f7f\u7528\u9700\u8981\u8c03\u6574\u65f6\uff0c"
                    + "\u624d\u8ba4\u4e3a\u503c\u5f97\u8f6c\u6210\u6f14\u8fdb\u4fe1\u53f7\u3002\\n\\n"
                    + "\u5f53\u524d skill\uff1a{skill_name}\\n"
                    + "\u6700\u8fd1\u7528\u6237\u6d88\u606f\uff1a{user_messages}\\n\\n"
                    + "\u8f93\u51fa JSON: {\"is_feedback\": true/false, \"excerpt\": \"str\"}\\n";
    private static final String USER_FEEDBACK_PROMPT_EN =
            "Determine whether the following user messages contain passive corrective feedback "
                    + "or reusable improvement guidance for the current skill.\\n"
                    + "Only treat the messages as an evolution signal when the user is clearly correcting "
                    + "the agent's understanding, ordering, steps, or tool usage.\\n\\n"
                    + "Current skill: {skill_name}\\n"
                    + "Recent user messages: {user_messages}\\n\\n"
                    + "Output JSON: {\"is_feedback\": true/false, \"excerpt\": \"str\"}\\n";

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
    private LlmInvoker llm;
    private String model = "";
    private String language = "cn";

    public ConversationSignalDetector() {
        this(Set.of());
    }

    public ConversationSignalDetector(Set<String> existingSkills) {
        this.existingSkills = existingSkills != null ? new LinkedHashSet<>(existingSkills) : Set.of();
    }

    public ConversationSignalDetector bindLlm(LlmInvoker llm, String model) {
        return bindLlm(llm, model, "cn");
    }

    public ConversationSignalDetector bindLlm(LlmInvoker llm, String model, String language) {
        this.llm = llm;
        this.model = model != null ? model : "";
        this.language = language != null && !language.isBlank() ? language : "cn";
        return this;
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

    public List<EvolutionSignal> detectTrajectorySignals(Trajectory trajectory) {
        if (trajectory == null) {
            return List.of();
        }
        return detect(trajectory);
    }

    public List<EvolutionSignal> detectTrajectorySignals(
            Trajectory trajectory,
            List<Map<String, Object>> messages
    ) {
        if (messages != null) {
            List<EvolutionSignal> signals = new ArrayList<>(detectFromMessages(messages));
            if (trajectory != null) {
                signals.addAll(detectCollaborationSignals(trajectory));
            }
            return deduplicate(signals);
        }
        if (trajectory == null) {
            return List.of();
        }
        return detect(trajectory);
    }

    public CompletionStage<List<EvolutionSignal>> detectUserMessageFeedback(Trajectory trajectory) {
        return detectUserIntent(trajectory).thenApply(signals -> toLegacyCorrectionSignals(signals));
    }

    public CompletionStage<List<EvolutionSignal>> detectUserMessageFeedback(List<Map<String, Object>> messages) {
        return detectUserIntent(messages).thenApply(signals -> toLegacyCorrectionSignals(signals));
    }

    public CompletionStage<List<EvolutionSignal>> detectUserIntent(Trajectory trajectory) {
        List<Object> messages = trajectory != null ? convertTrajectoryToMessages(trajectory) : List.of();
        return detectUserIntentInternal(messages);
    }

    public CompletionStage<List<EvolutionSignal>> detectUserIntent(List<Map<String, Object>> messages) {
        return detectUserIntentInternal(messages != null ? messages : List.of());
    }

    public static String makeSignalFingerprint(EvolutionSignal signal) {
        if (signal == null) {
            return "";
        }
        String excerpt = signal.getExcerpt() != null ? signal.getExcerpt() : "";
        String normalizedExcerpt = excerpt.length() > 200 ? excerpt.substring(0, 200) : excerpt;
        Map<String, Object> context = signal.getContext();
        Object toolName = context == null ? null : context.get("tool_name");
        return String.join("|",
                nullToEmpty(signal.getSignalType()),
                toolName == null ? "" : String.valueOf(toolName),
                nullToEmpty(signal.getSkillName()),
                normalizedExcerpt);
    }

    private CompletionStage<List<EvolutionSignal>> detectUserIntentInternal(List<?> messages) {
        List<String> userMessages = collectRecentUserMessages(messages);
        if (userMessages.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        String skillName = inferSkillFromMessages(messages);
        if (skillName == null || skillName.isBlank()) {
            return CompletableFuture.completedFuture(List.of());
        }

        if (llm == null || model.isBlank()) {
            return CompletableFuture.completedFuture(fallbackUserFeedbackSignals(userMessages, skillName));
        }

        String promptTemplate = "cn".equalsIgnoreCase(language) ? USER_FEEDBACK_PROMPT_CN : USER_FEEDBACK_PROMPT_EN;
        String prompt = promptTemplate
                .replace("{skill_name}", skillName)
                .replace("{user_messages}", truncate(String.join("\n", userMessages), 2000));
        List<Map<String, Object>> invokeMessages = List.of(Map.of("role", "user", "content", prompt));

        try {
            return toCompletableFuture(llm.invoke(model, invokeMessages, 30))
                    .handle((response, error) -> {
                        if (error != null) {
                            LOGGER.warning("[ConversationSignalDetector] user feedback detection failed: "
                                    + error.getMessage());
                            return fallbackUserFeedbackSignals(userMessages, skillName);
                        }
                        return parseUserFeedbackResponse(response, userMessages, skillName);
                    });
        } catch (RuntimeException exception) {
            LOGGER.warning("[ConversationSignalDetector] user feedback detection failed: " + exception.getMessage());
            return CompletableFuture.completedFuture(fallbackUserFeedbackSignals(userMessages, skillName));
        }
    }

    private List<EvolutionSignal> parseUserFeedbackResponse(
            Object response,
            List<String> userMessages,
            String skillName
    ) {
        String raw = responseToText(response);
        Map<String, Object> parsed = parseArgs(raw);
        if (parsed.isEmpty()) {
            return fallbackUserFeedbackSignals(userMessages, skillName);
        }
        Object isFeedback = parsed.get("is_feedback");
        if (!(isFeedback instanceof Boolean feedback)) {
            return fallbackUserFeedbackSignals(userMessages, skillName);
        }
        if (!feedback) {
            return List.of();
        }
        String excerpt = stringValue(parsed.getOrDefault("excerpt", userMessages.get(userMessages.size() - 1))).trim();
        return List.of(makeUserFeedbackSignal(excerpt, skillName));
    }

    private List<EvolutionSignal> toLegacyCorrectionSignals(List<EvolutionSignal> signals) {
        List<EvolutionSignal> legacySignals = new ArrayList<>();
        for (EvolutionSignal signal : signals) {
            legacySignals.add(EvolutionSignals.makeEvolutionSignal(
                    "user_correction",
                    "Examples",
                    signal.getExcerpt(),
                    null,
                    signal.getSkillName(),
                    null,
                    signal.getContext()
            ));
        }
        return legacySignals;
    }

    private List<String> collectRecentUserMessages(List<?> messages) {
        List<String> userMessages = new ArrayList<>();
        for (Object message : messages) {
            String role = stringValue(readField(message, "role", ""));
            String content = stringValue(readField(message, "content", "")).trim();
            if ("user".equals(role) && !content.isEmpty()) {
                userMessages.add(content);
            }
        }
        if (userMessages.size() <= 5) {
            return userMessages;
        }
        return new ArrayList<>(userMessages.subList(userMessages.size() - 5, userMessages.size()));
    }

    private List<Object> convertTrajectoryToMessages(Trajectory trajectory) {
        List<Object> messages = new ArrayList<>();
        Map<String, String> toolCallIdToName = new LinkedHashMap<>();

        for (TrajectoryStep step : safeSteps(trajectory)) {
            Object detail = step.getDetail();
            if ("llm".equals(step.getKind()) && detail instanceof LLMCallDetail llmDetail) {
                List<Object> rawMessages = llmDetail.getMessages() != null ? llmDetail.getMessages() : List.of();
                for (Object message : rawMessages) {
                    messages.add(message);
                    for (Object toolCall : readToolCalls(message)) {
                        String id = stringValue(readField(toolCall, "id", ""));
                        String name = stringValue(readField(toolCall, "name", ""));
                        if (!id.isEmpty() && !name.isEmpty()) {
                            toolCallIdToName.put(id, name);
                        }
                    }
                }
            } else if ("tool".equals(step.getKind()) && detail instanceof ToolCallDetail toolDetail) {
                String toolName = nullToEmpty(toolDetail.getToolName());
                String toolCallId = firstNonEmpty(
                        toolDetail.getToolCallId(),
                        stringValue(step.getMeta() == null ? null : step.getMeta().get("tool_call_id"))
                );
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

    private List<EvolutionSignal> detectFromMessages(List<?> messages) {
        List<EvolutionSignal> signals = new ArrayList<>();
        List<SkillRead> skillReadHistory = new ArrayList<>();
        Map<String, String> pendingScripts = new LinkedHashMap<>();
        Map<String, String> toolCallIdToName = new LinkedHashMap<>();

        for (int msgIdx = 0; msgIdx < messages.size(); msgIdx++) {
            Object message = messages.get(msgIdx);
            String role = stringValue(readField(message, "role", ""));
            String content = stringValue(readField(message, "content", ""));
            List<?> toolCalls = readToolCalls(message);

            if ("assistant".equals(role) && !toolCalls.isEmpty()) {
                int index = msgIdx;
                detectSkillFromToolCalls(toolCalls).ifPresent(skill -> skillReadHistory.add(new SkillRead(index, skill)));
                for (Object toolCall : toolCalls) {
                    String id = stringValue(readField(toolCall, "id", ""));
                    String name = stringValue(readField(toolCall, "name", ""));
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
                        stringValue(readField(message, "name", "")),
                        stringValue(readField(message, "tool_name", "")));
                String toolCallId = stringValue(readField(message, "tool_call_id", ""));
                if (toolName.isEmpty() && !toolCallId.isEmpty()) {
                    toolName = toolCallIdToName.getOrDefault(toolCallId, "");
                }
                String activeSkill = resolveActiveSkill(msgIdx, skillReadHistory);

                if (!toolCallId.isEmpty() && pendingScripts.containsKey(toolCallId)) {
                    boolean hasFailure = FAILURE_KEYWORDS.matcher(content).find();
                    if (!hasFailure) {
                        signals.add(signal("script_artifact", "Scripts",
                                truncate(pendingScripts.get(toolCallId), 600), toolName, activeSkill,
                                "passive_conversation", null));
                    }
                    pendingScripts.remove(toolCallId);
                }

                if (DATA_FETCH_TOOLS.contains(toolName.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                Matcher match = FAILURE_KEYWORDS.matcher(content);
                if (match.find() && !TOOL_SCHEMA_PATTERN.matcher(content).find()) {
                    signals.add(signal("execution_failure", "Troubleshooting",
                            extractAroundMatch(content, match), emptyToNull(toolName), activeSkill,
                            "passive_conversation", null));
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
                            "passive_collaboration",
                            Map.of("from_member", memberId, "to_member", toMember)));
                }
            }

            if ("claim_task".equals(toolName)) {
                String taskId = extractArg(callArgs, "task_id");
                if (!taskId.isEmpty()) {
                    signals.add(signal("collaboration_claim", "Collaboration",
                            "claim task " + taskId, toolName, activeSkill,
                            "passive_collaboration",
                            Map.of("member_id", memberId, "task_id", taskId)));
                }
            }

            if ("view_task".equals(toolName)) {
                signals.add(signal("collaboration_view", "Collaboration",
                        "view team task status", toolName, activeSkill,
                        "passive_collaboration",
                        Map.of("member_id", memberId)));
            }

            if (step.getMeta() != null && step.getMeta().containsKey("parent_invoke_id")) {
                String parentId = stringValue(step.getMeta().get("parent_invoke_id"));
                signals.add(signal("collaboration_receive", "Collaboration",
                        "receive context from " + parentId, emptyToNull(toolName), activeSkill,
                        "passive_collaboration",
                        Map.of("member_id", memberId, "parent_invoke_id", parentId)));
            }

            String result = detail.getCallResult() != null ? String.valueOf(detail.getCallResult()) : "";
            Matcher match = COLLABORATION_FAILURE_PATTERN.matcher(result);
            if (match.find()) {
                signals.add(signal("collaboration_failure", "Collaboration",
                        extractAroundMatch(result, match), toolName, activeSkill,
                        "passive_collaboration",
                        Map.of("member_id", memberId)));
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
            List<Object> rawMessages = llmDetail.getMessages() != null ? llmDetail.getMessages() : List.of();
            for (Object message : rawMessages) {
                int stepIndex = idx;
                detectSkillFromToolCalls(readToolCalls(message)).ifPresent(skill -> skillReads.add(new SkillRead(stepIndex, skill)));
            }
        }
        return skillReads;
    }

    private Optional<String> detectSkillFromToolCalls(List<?> toolCalls) {
        for (Object toolCall : toolCalls) {
            String name = stringValue(readField(toolCall, "name", "")).toLowerCase(Locale.ROOT);
            String arguments = stringifyArgs(readField(toolCall, "arguments", ""));
            String skillName = "";

            Matcher matcher = SKILL_MD_PATTERN.matcher(arguments);
            if (matcher.find() && isSkillMdReadTool(name)) {
                skillName = matcher.group(1);
            } else if ("skill_tool".equals(name)) {
                skillName = extractArg(arguments, "skill_name");
            }

            if (!skillName.isEmpty() && (existingSkills.isEmpty() || existingSkills.contains(skillName))) {
                return Optional.of(skillName);
            }
        }
        return Optional.empty();
    }

    private String inferSkillFromMessages(List<?> messages) {
        List<SkillRead> skillReadHistory = new ArrayList<>();
        for (int msgIdx = 0; msgIdx < messages.size(); msgIdx++) {
            Object message = messages.get(msgIdx);
            String role = stringValue(readField(message, "role", ""));
            List<?> toolCalls = readToolCalls(message);
            if ("assistant".equals(role) && !toolCalls.isEmpty()) {
                int index = msgIdx;
                detectSkillFromToolCalls(toolCalls).ifPresent(skill -> skillReadHistory.add(new SkillRead(index, skill)));
            }
        }
        return resolveActiveSkill(messages.size(), skillReadHistory);
    }

    private List<EvolutionSignal> fallbackUserFeedbackSignals(List<String> userMessages, String skillName) {
        for (int idx = userMessages.size() - 1; idx >= 0; idx--) {
            String message = userMessages.get(idx);
            if (CORRECTION_PATTERN.matcher(message).find()) {
                return List.of(makeUserFeedbackSignal(message, skillName));
            }
        }
        return List.of();
    }

    private EvolutionSignal makeUserFeedbackSignal(String excerpt, String skillName) {
        return EvolutionSignals.makeEvolutionSignal(
                Protocols.USER_INTENT_SIGNAL,
                "Instructions",
                truncate(excerpt, 600),
                null,
                skillName,
                "passive_conversation",
                null
        );
    }

    private static List<TrajectoryStep> safeSteps(Trajectory trajectory) {
        return trajectory != null && trajectory.getSteps() != null ? trajectory.getSteps() : List.of();
    }

    private static List<?> readToolCalls(Object message) {
        Object toolCalls = readField(message, "tool_calls", null);
        if (toolCalls == null) {
            toolCalls = readField(message, "toolCalls", List.of());
        }
        if (toolCalls instanceof List<?> list) {
            return list;
        }
        return List.of();
    }

    private static String extractCodeFromArgs(Object toolCall) {
        Map<String, Object> args = parseArgs(readField(toolCall, "arguments", ""));
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
            Pattern pattern = Pattern.compile(key + "[\"']?\\s*[:=]\\s*[\"']([^\"']+)[\"']");
            Matcher matcher = pattern.matcher(rawArgs);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "";
    }

    private static Map<String, Object> parseArgs(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return toStringKeyMap(map);
        }
        if (!(raw instanceof String text) || text.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(text, new TypeReference<Map<String, Object>>() {
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
                return MAPPER.writeValueAsString(toStringKeyMap(map));
            } catch (JsonProcessingException ignored) {
                return String.valueOf(raw);
            }
        }
        return raw != null ? String.valueOf(raw) : "";
    }

    private static String responseToText(Object response) {
        Object content = readField(response, "content", null);
        if (content != null) {
            return String.valueOf(content);
        }
        if (response instanceof Map<?, ?> map) {
            Object text = map.get("text");
            if (text != null) {
                return String.valueOf(text);
            }
        } else {
            Object text = readField(response, "text", null);
            if (text != null) {
                return String.valueOf(text);
            }
        }
        return response != null ? String.valueOf(response) : "";
    }

    private static boolean isTeamMemberContext(Trajectory trajectory) {
        Map<String, Object> meta = trajectory != null ? trajectory.getMeta() : null;
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
        for (int idx = skillReadHistory.size() - 1; idx >= 0; idx--) {
            SkillRead read = skillReadHistory.get(idx);
            if (read.index <= msgIdx) {
                return read.skillName;
            }
        }
        return null;
    }

    private static EvolutionSignal signal(
            String type,
            String section,
            String excerpt,
            String toolName,
            String skillName,
            String source,
            Map<String, Object> context
    ) {
        return EvolutionSignals.makeEvolutionSignal(
                type,
                section,
                excerpt,
                emptyToNull(toolName),
                emptyToNull(skillName),
                source,
                context
        );
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

    private static Object readField(Object source, String fieldName, Object defaultValue) {
        if (source == null) {
            return defaultValue;
        }
        if (source instanceof Map<?, ?> map) {
            Object value = map.get(fieldName);
            return value != null ? value : defaultValue;
        }

        String suffix = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        for (String methodName : List.of("get" + suffix, "is" + suffix, fieldName)) {
            Object value = invokeNoArg(source, methodName);
            if (value != null) {
                return value;
            }
        }

        Field field = findField(source.getClass(), fieldName);
        if (field != null) {
            try {
                field.setAccessible(true);
                Object value = field.get(source);
                return value != null ? value : defaultValue;
            } catch (ReflectiveOperationException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static Object invokeNoArg(Object source, String methodName) {
        if (source == null) {
            return null;
        }
        Method method = findMethod(source.getClass(), methodName);
        if (method == null) {
            return null;
        }
        try {
            method.setAccessible(true);
            return method.invoke(source);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String methodName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Map<String, Object> toStringKeyMap(Map<?, ?> rawMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static <T> CompletableFuture<T> toCompletableFuture(CompletionStage<T> stage) {
        if (stage instanceof CompletableFuture<T> future) {
            return future;
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        stage.whenComplete((value, error) -> {
            if (error != null) {
                future.completeExceptionally(error);
            } else {
                future.complete(value);
            }
        });
        return future;
    }

    private static boolean isSkillMdReadTool(String name) {
        return name == null || name.isEmpty() || name.contains("file") || name.contains("read");
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
