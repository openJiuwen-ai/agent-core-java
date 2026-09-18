/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.monitor;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.StreamController.TeamOutputChunk;
import com.openjiuwen.core.session.stream.OutputSchema;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Aggregates team stream chunks into diagnostic log records.
 *
 * <p>Mirrors Python's {@code TeamStreamLogger} and module helpers in
 * {@code openjiuwen/agent_teams/monitor/stream_logger.py}.</p>
 */
public class TeamStreamLogger {

    private static final String CHUNK_LLM_OUTPUT = "llm_output";
    private static final String CHUNK_LLM_REASONING = "llm_reasoning";
    private static final String CHUNK_ANSWER = "answer";
    private static final String CHUNK_INTERACTION = "__interaction__";
    private static final String CHUNK_MESSAGE = "message";
    private static final String CHUNK_TOOL_CALL = "tool_call";
    private static final String CHUNK_TOOL_RESULT = "tool_result";
    private static final String CHUNK_TOOL_UPDATE = "tool_update";
    private static final String CHUNK_TODO_UPDATED = "todo.updated";
    private static final String CHUNK_CONTROLLER_OUTPUT = "controller_output";
    private static final String RUNTIME_READY_EVENT = "team.runtime_ready";
    private static final Set<String> ACCUMULATING_TYPES = Set.of(CHUNK_LLM_OUTPUT, CHUNK_LLM_REASONING);
    private static final int TOOL_RESULT_CAP = 2000;
    private static final int TOOL_ARGS_CAP = 500;
    private static final int GENERIC_CAP = 2000;
    private static final String UNKNOWN = "<unknown>";
    private static final String TRUNCATED = "\u9225?(truncated)";
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT);
    private static final Map<String, String> CATEGORY_LEVEL = categoryLevels();

    private final Path path;
    private BufferedWriter writer;
    private final Map<SourceKey, Run> runs = new LinkedHashMap<>();
    private final Set<SourceKey> llmOutputSeen = new HashSet<>();
    private int chunkCount;

    public TeamStreamLogger(String filePath) throws IOException {
        this(Path.of(filePath));
    }

    public TeamStreamLogger(Path filePath) throws IOException {
        this.path = Objects.requireNonNull(filePath, "filePath");
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        this.writer = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    public void feed(Object chunk) {
        try {
            feedInternal(chunk);
        } catch (Exception exc) {
            safeWrite("[WARN] stream logger feed error: " + exc);
        }
    }

    public void flush() {
        try {
            for (SourceKey key : List.copyOf(runs.keySet())) {
                flushKey(key);
            }
            if (chunkCount > 0) {
                safeWrite("[INFO] stream end, " + chunkCount + " chunks");
            }
        } catch (Exception exc) {
            safeWrite("[WARN] stream logger flush error: " + exc);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                    // Python suppresses close failures in the diagnostic logger.
                }
                writer = null;
            }
        }
    }

    public boolean isClosed() {
        return writer == null;
    }

    public Path getPath() {
        return path;
    }

    private void feedInternal(Object rawChunk) {
        chunkCount += 1;
        if (!(rawChunk instanceof TeamOutputChunk chunk)) {
            return;
        }
        String ctype = chunk.getType() == null ? "" : chunk.getType();
        Object payload = chunk.getPayload();
        String member = chunk.getSourceMember();
        String role = renderRole(chunk.getRole());
        SourceKey key = new SourceKey(member, role);

        if (CHUNK_ANSWER.equals(ctype) && llmOutputSeen.contains(key)) {
            return;
        }

        String category = classify(ctype, payload);
        if (ACCUMULATING_TYPES.contains(ctype)) {
            String content = extractContent(payload);
            if (content.isEmpty()) {
                return;
            }
            Run run = runs.get(key);
            if (run != null && !run.category().equals(category)) {
                flushKey(key);
                run = null;
            }
            if (run == null) {
                run = new Run(category);
                runs.put(key, run);
            }
            run.buffer().add(content);
            if (CHUNK_LLM_OUTPUT.equals(ctype)) {
                llmOutputSeen.add(key);
            }
            return;
        }

        flushKey(key);
        emit(category, member, role, discreteSummary(category, payload));
    }

    private static String classify(String ctype, Object payload) {
        return switch (ctype) {
            case CHUNK_LLM_OUTPUT, CHUNK_ANSWER -> "text";
            case CHUNK_LLM_REASONING -> "reasoning";
            case CHUNK_TOOL_CALL -> "tool_call";
            case CHUNK_TOOL_RESULT -> "tool_result";
            case CHUNK_TOOL_UPDATE -> "tool_update";
            case CHUNK_INTERACTION -> "interaction";
            case CHUNK_CONTROLLER_OUTPUT -> "controller_output";
            case CHUNK_MESSAGE -> isRuntimeReady(payload) ? "runtime_ready" : "message";
            case CHUNK_TODO_UPDATED -> "todo";
            default -> "other";
        };
    }

    private static String discreteSummary(String category, Object payload) {
        return switch (category) {
            case "tool_call" -> toolCallSummary(payload);
            case "tool_result" -> toolResultSummary(payload);
            case "tool_update" -> toolUpdateSummary(payload);
            case "controller_output" -> controllerOutputSummary(payload);
            case "runtime_ready" -> runtimeReadySummary(payload);
            case "interaction" -> interactionSummary(payload);
            default -> genericSummary(payload);
        };
    }

    private static boolean isRuntimeReady(Object payload) {
        return payload instanceof Map<?, ?> map && RUNTIME_READY_EVENT.equals(map.get("event_type"));
    }

    private static String extractContent(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            Object content = map.get("content");
            if (content != null && !String.valueOf(content).isEmpty()) {
                return String.valueOf(content);
            }
            Object output = map.get("output");
            return output == null ? "" : String.valueOf(output);
        }
        if (payload instanceof String text) {
            return text;
        }
        return String.valueOf(payload);
    }

    private static String toolCallSummary(Object payload) {
        if (!(payload instanceof Map<?, ?> map)) {
            return cap(String.valueOf(payload), GENERIC_CAP);
        }
        Object name = map.get("tool_name");
        Object argsRaw = map.get("tool_args");
        if (isBlankObject(name) && isBlankObject(argsRaw)) {
            return cap(String.valueOf(payload), GENERIC_CAP);
        }
        return "tool_name=" + stringValue(name) + " tool_args=" + cap(stringValue(argsRaw), TOOL_ARGS_CAP);
    }

    private static String toolResultSummary(Object payload) {
        if (!(payload instanceof Map<?, ?> map)) {
            return cap(String.valueOf(payload), GENERIC_CAP);
        }
        Object name = map.get("tool_name");
        Object argsRaw = map.get("tool_args");
        Object resultRaw = map.get("tool_result");
        if (isBlankObject(name) && isBlankObject(argsRaw) && isBlankObject(resultRaw)) {
            return cap(String.valueOf(payload), GENERIC_CAP);
        }
        return "tool_name=" + stringValue(name)
                + " tool_args=" + cap(stringValue(argsRaw), TOOL_ARGS_CAP)
                + "\nresult: " + cap(stringValue(resultRaw), TOOL_RESULT_CAP);
    }

    private static String toolUpdateSummary(Object payload) {
        if (!(payload instanceof Map<?, ?> map)) {
            return cap(String.valueOf(payload), GENERIC_CAP);
        }
        Object update = map.get("tool_update");
        if (!(update instanceof Map<?, ?> updateMap)) {
            return cap(String.valueOf(payload), GENERIC_CAP);
        }
        return "tool_name=" + stringValue(updateMap.get("tool_name"))
                + " status=" + stringValue(updateMap.get("status"))
                + " tool_call_id=" + stringValue(updateMap.get("tool_call_id"))
                + " arguments=" + cap(stringValue(updateMap.get("arguments")), TOOL_ARGS_CAP);
    }

    private static String controllerOutputSummary(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            String payloadType = stringValue(map.get("type")).toLowerCase(Locale.ROOT);
            if (payloadType.contains("task_failed")) {
                Object data = map.get("data");
                List<String> texts = new ArrayList<>();
                if (data instanceof List<?> items) {
                    for (Object item : items) {
                        if (item instanceof Map<?, ?> itemMap) {
                            String text = stringValue(itemMap.get("text")).strip();
                            if (!text.isEmpty()) {
                                texts.add(text);
                            }
                        }
                    }
                }
                if (!texts.isEmpty()) {
                    return String.join("\n", texts);
                }
            }
        }
        return cap(String.valueOf(payload), GENERIC_CAP);
    }

    private static String runtimeReadySummary(Object payload) {
        if (!(payload instanceof Map<?, ?> map)) {
            return cap(String.valueOf(payload), GENERIC_CAP);
        }
        return "team=" + map.get("team_name")
                + " session=" + map.get("session_id")
                + " activation=" + map.get("activation_kind");
    }

    private static String interactionSummary(Object payload) {
        Object id = "unknown";
        if (payload instanceof Map<?, ?> map) {
            Object rawId = map.get("interaction_id");
            id = rawId == null ? "unknown" : rawId;
        } else if (payload != null) {
            id = reflectId(payload);
        }
        return "interaction_id=" + id + "\n" + cap(String.valueOf(payload), GENERIC_CAP);
    }

    private static Object reflectId(Object payload) {
        try {
            Method getId = payload.getClass().getMethod("getId");
            return getId.invoke(payload);
        } catch (ReflectiveOperationException ignored) {
            try {
                Field id = payload.getClass().getDeclaredField("id");
                id.setAccessible(true);
                return id.get(payload);
            } catch (ReflectiveOperationException ignoredAgain) {
                return "unknown";
            }
        }
    }

    private static String genericSummary(Object payload) {
        String content = extractContent(payload);
        return content.isEmpty() ? cap(String.valueOf(payload), GENERIC_CAP) : content;
    }

    private void flushKey(SourceKey key) {
        Run run = runs.remove(key);
        if (run == null || run.buffer().isEmpty()) {
            return;
        }
        emit(run.category(), key.member(), key.role(), String.join("", run.buffer()));
    }

    private void emit(String category, String member, String role, String content) {
        if (content == null || content.isEmpty()) {
            return;
        }
        String level = CATEGORY_LEVEL.getOrDefault(category, "INFO");
        StringBuilder body = new StringBuilder();
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                body.append('\n');
            }
            body.append("  | ").append(lines[i]);
        }
        String header = "[" + level + "] member=" + (member == null || member.isBlank() ? UNKNOWN : member)
                + " role=" + (role == null || role.isBlank() ? UNKNOWN : role)
                + " category=" + category;
        safeWrite(header + "\n" + body);
    }

    private void safeWrite(String body) {
        if (writer == null) {
            return;
        }
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        try {
            writer.write(timestamp + " " + body + "\n");
            writer.flush();
        } catch (IOException ignored) {
            // Diagnostic logging must never break the observed stream.
        }
    }

    private static String renderRole(TeamRole role) {
        return role == null ? null : role.value();
    }

    private static boolean isBlankObject(Object value) {
        return value == null || String.valueOf(value).isEmpty();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String cap(String text, int limit) {
        if (text == null || text.length() <= limit) {
            return text == null ? "" : text;
        }
        return text.substring(0, limit) + TRUNCATED;
    }

    private static Map<String, String> categoryLevels() {
        Map<String, String> levels = new HashMap<>();
        levels.put("text", "INFO");
        levels.put("reasoning", "DEBUG");
        levels.put("tool_call", "DEBUG");
        levels.put("tool_result", "DEBUG");
        levels.put("tool_update", "DEBUG");
        levels.put("interaction", "WARN");
        levels.put("controller_output", "WARN");
        levels.put("runtime_ready", "INFO");
        levels.put("message", "INFO");
        levels.put("todo", "INFO");
        levels.put("other", "INFO");
        return levels;
    }

    /**
     * Pending source/category accumulation run.
     *
     * <p>Mirrors Python's {@code _Run} in
     * {@code openjiuwen/agent_teams/monitor/stream_logger.py}.</p>
     */
    public static final class Run {
        private final String category;
        private final List<String> buffer = new ArrayList<>();

        private Run(String category) {
            this.category = category;
        }

        public String category() {
            return category;
        }

        public List<String> buffer() {
            return buffer;
        }
    }

    private record SourceKey(String member, String role) {
    }
}
