/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.agent_teams;

import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared utilities for agent-team E2E examples.
 *
 * <p>Mirrors Python's {@code examples.agent_teams._e2e_utils}.</p>
 */
public final class E2eUtils {
    public static final String CHUNK_LLM_OUTPUT = "llm_output";
    public static final String CHUNK_LLM_REASONING = "llm_reasoning";
    public static final String CHUNK_ANSWER = "answer";
    public static final String CHUNK_TOOL_CALL = "tool_call";
    public static final String CHUNK_TOOL_RESULT = "tool_result";
    public static final String CHUNK_MESSAGE = "message";
    public static final String CHUNK_INTERACTION = "__interaction__";

    public static final String COLOR_RESET = "\033[0m";
    public static final String COLOR_DIM = "\033[2m";
    public static final String COLOR_GREEN = "\033[92m";
    public static final String COLOR_CYAN = "\033[96m";
    public static final String COLOR_YELLOW = "\033[93m";

    private static final Logger LOGGER = LoggerFactory.getLogger(E2eUtils.class);
    private static final Pattern ENV_VAR_RE = Pattern.compile("\\$\\{(\\w+)}");

    private E2eUtils() {
    }

    public static Object expandEnvVars(Object value) {
        if (value instanceof String text) {
            return expandString(text);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> expanded = new LinkedHashMap<>();
            map.forEach((key, item) -> expanded.put(String.valueOf(key), expandEnvVars(item)));
            return expanded;
        }
        if (value instanceof List<?> list) {
            List<Object> expanded = new ArrayList<>(list.size());
            for (Object item : list) {
                expanded.add(expandEnvVars(item));
            }
            return expanded;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> loadTeamConfig(Path path) throws IOException {
        Object raw;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            raw = new Yaml().load(reader);
        }
        if (raw == null) {
            return new LinkedHashMap<>();
        }
        Object expanded = expandEnvVars(raw);
        if (!(expanded instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("team config must be a YAML mapping: " + path);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    public static CompletableFuture<String> ainput(String prompt) {
        return ainput(prompt, new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)), System.out);
    }

    public static CompletableFuture<String> ainput(String prompt, BufferedReader reader, PrintStream out) {
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(out, "out");
        out.print(prompt != null ? prompt : "> ");
        out.flush();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return reader.readLine();
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });
    }

    public static void consumeStream(TeamAgent leader, String query, String sessionId) {
        LOGGER.info("Starting leader stream with query: {}", query);
        Iterator<Object> chunks = Runner.runAgentStreaming(
                leader,
                Map.of("query", query),
                sessionId,
                null,
                List.of(StreamMode.OUTPUT)
        );
        consumeStream(chunks, System.out);
        LOGGER.info("Leader stream finished.");
    }

    public static void consumeStream(Iterator<?> chunks, PrintStream out) {
        Objects.requireNonNull(chunks, "chunks");
        Objects.requireNonNull(out, "out");

        String currentType = "";
        List<String> buffer = new ArrayList<>();
        boolean hasLlmOutput = false;

        while (chunks.hasNext()) {
            Object chunk = chunks.next();
            String chunkType = typeOf(chunk);
            Object payload = payloadOf(chunk);

            if (CHUNK_TOOL_CALL.equals(chunkType)) {
                flushBuffer(currentType, buffer, out);
                currentType = "";
                buffer = new ArrayList<>();
                Map<?, ?> map = payload instanceof Map<?, ?> payloadMap ? payloadMap : Map.of();
                String toolName = stringValue(map.containsKey("tool_name") ? map.get("tool_name") : "");
                String toolArgs = stringValue(map.containsKey("tool_args") ? map.get("tool_args") : "");
                write(out, COLOR_CYAN + "[Tool] " + toolName + COLOR_RESET);
                if (!toolArgs.isEmpty()) {
                    write(out, COLOR_DIM + "(" + toolArgs + ")" + COLOR_RESET);
                }
                write(out, "\n");
                continue;
            }

            if (CHUNK_TOOL_RESULT.equals(chunkType)) {
                Map<?, ?> map = payload instanceof Map<?, ?> payloadMap ? payloadMap : null;
                Object rawResult = map != null ? map.get("tool_result") : payload;
                String preview = stringValue(rawResult);
                if (preview.length() > 200) {
                    preview = preview.substring(0, 200);
                }
                write(out, COLOR_DIM + "  [Result] " + preview + COLOR_RESET + "\n\n");
                continue;
            }

            if (CHUNK_MESSAGE.equals(chunkType)) {
                flushBuffer(currentType, buffer, out);
                currentType = "";
                buffer = new ArrayList<>();
                write(out, COLOR_DIM + "  [Message] " + extractContent(payload) + COLOR_RESET + "\n");
                continue;
            }

            if (CHUNK_INTERACTION.equals(chunkType)) {
                flushBuffer(currentType, buffer, out);
                currentType = "";
                buffer = new ArrayList<>();
                write(out, COLOR_YELLOW + "[Interaction] " + stringValue(payload) + COLOR_RESET + "\n");
                continue;
            }

            if (CHUNK_ANSWER.equals(chunkType) && hasLlmOutput) {
                continue;
            }

            if (!chunkType.equals(currentType)) {
                flushBuffer(currentType, buffer, out);
                currentType = chunkType;
                buffer = new ArrayList<>();
            }

            if (CHUNK_LLM_OUTPUT.equals(chunkType)) {
                hasLlmOutput = true;
            }

            buffer.add(extractContent(payload));
        }

        flushBuffer(currentType, buffer, out);
    }

    public static void flushBuffer(String chunkType, List<String> buffer, PrintStream out) {
        if (buffer == null || buffer.isEmpty()) {
            return;
        }
        String text = String.join("", buffer);
        if (text.trim().isEmpty()) {
            return;
        }
        if (CHUNK_LLM_REASONING.equals(chunkType)) {
            write(out, COLOR_DIM + "[Reasoning] " + text + COLOR_RESET + "\n");
        } else if (CHUNK_LLM_OUTPUT.equals(chunkType)) {
            write(out, COLOR_GREEN + "[Output] " + COLOR_RESET + text + "\n");
        } else if (CHUNK_ANSWER.equals(chunkType)) {
            write(out, COLOR_YELLOW + "[Answer] " + COLOR_RESET + text + "\n");
        } else {
            write(out, "[" + chunkType + "] " + text + "\n");
        }
    }

    public static String extractContent(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            Object content = map.get("content");
            if (content != null && !String.valueOf(content).isEmpty()) {
                return String.valueOf(content);
            }
            Object output = map.get("output");
            return output != null ? String.valueOf(output) : "";
        }
        if (payload instanceof String text) {
            return text;
        }
        return payload != null ? String.valueOf(payload) : "None";
    }

    public static void runInteractive(
            TeamAgent leader,
            Map<String, Object> runtimeConfig,
            String defaultSessionId,
            String defaultInitialQuery
    ) {
        Map<String, Object> config = runtimeConfig != null ? runtimeConfig : Map.of();
        String sessionId = stringOrDefault(config.get("session_id"), defaultSessionId);
        String initialQuery = stringOrDefault(config.get("initial_query"), defaultInitialQuery);

        CompletableFuture<Void> streamTask = CompletableFuture.runAsync(() -> consumeStream(leader, initialQuery, sessionId));
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        try {
            while (true) {
                String userInput = ainput("\n[You] > ", reader, System.out).join();
                if (userInput == null) {
                    break;
                }
                String stripped = userInput.strip();
                if ("exit".equalsIgnoreCase(stripped) || "quit".equalsIgnoreCase(stripped)) {
                    System.out.println("Exiting...");
                    break;
                }
                if (stripped.isEmpty()) {
                    continue;
                }
                leader.receiveUserInput(userInput);
                System.out.println("[System] Input sent to leader: " + userInput);
            }
        } catch (CompletionException e) {
            throw e;
        } finally {
            if (!streamTask.isDone()) {
                streamTask.cancel(true);
            }
        }
    }

    private static String expandString(String value) {
        Matcher matcher = ENV_VAR_RE.matcher(value);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = System.getenv(name);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement != null ? replacement : matcher.group(0)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static void write(PrintStream out, String text) {
        out.print(text);
        out.flush();
    }

    private static String typeOf(Object chunk) {
        if (chunk instanceof OutputSchema output) {
            return output.getType() != null ? output.getType() : "";
        }
        if (chunk instanceof Map<?, ?> map) {
            Object type = map.get("type");
            return type != null ? String.valueOf(type) : "";
        }
        Object reflected = callNoArg(chunk, "getType");
        return reflected != null ? String.valueOf(reflected) : "";
    }

    private static Object payloadOf(Object chunk) {
        if (chunk instanceof OutputSchema output) {
            return output.getPayload();
        }
        if (chunk instanceof Map<?, ?> map) {
            return map.get("payload");
        }
        return callNoArg(chunk, "getPayload");
    }

    private static Object callNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to call " + methodName + " on " + target.getClass().getName(), e);
        }
    }

    private static String stringOrDefault(Object value, String fallback) {
        String text = value != null ? String.valueOf(value) : null;
        return text != null && !text.isBlank() ? text : fallback;
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }
}
