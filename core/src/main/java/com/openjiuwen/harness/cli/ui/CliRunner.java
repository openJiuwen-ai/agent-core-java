/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.openjiuwen.harness.cli.agent.AgentBackend;
import com.openjiuwen.harness.cli.agent.CliAgentFactory;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;

/**
 * Non-interactive CLI run mode.
 *
 * <p>Mirrors Python's module functions in
 * {@code openjiuwen/harness/cli/ui/runner.py}.</p>
 */
public class CliRunner {
    public static final String OUTPUT_TEXT = "text";
    public static final String OUTPUT_JSON = "json";
    public static final String OUTPUT_STREAM_JSON = "stream-json";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final BackendFactory backendFactory;
    private final PrintStream terminal;
    private final PrintStream errorConsole;

    public CliRunner() {
        this(CliAgentFactory::createBackend, System.out, System.err);
    }

    public CliRunner(BackendFactory backendFactory, PrintStream terminal, PrintStream errorConsole) {
        this.backendFactory = backendFactory == null ? CliAgentFactory::createBackend : backendFactory;
        this.terminal = terminal == null ? System.out : terminal;
        this.errorConsole = errorConsole == null ? System.err : errorConsole;
    }

    public int runOnce(Map<String, Object> config, String prompt) {
        return runOnce(config, prompt, OUTPUT_TEXT);
    }

    public int runOnce(Map<String, Object> config, String prompt, String outputFormat) {
        AgentBackend backend = backendFactory.create(config);
        try {
            backend.start().toCompletableFuture().join();
            Iterator<Object> stream = backend.runStreaming(prompt, null).toCompletableFuture().join();
            return switch (outputFormat == null ? OUTPUT_TEXT : outputFormat) {
                case OUTPUT_TEXT -> outputText(stream);
                case OUTPUT_JSON -> outputJson(stream, config);
                case OUTPUT_STREAM_JSON -> outputStreamJson(stream);
                default -> {
                    printError(new IllegalArgumentException("Unknown output format: " + outputFormat));
                    yield 1;
                }
            };
        } catch (RuntimeException exception) {
            printError(unwrap(exception));
            return 1;
        } finally {
            try {
                backend.stop().toCompletableFuture().join();
            } catch (RuntimeException ignored) {
                // Python finally awaits stop; any original user-facing error has already been rendered.
            }
        }
    }

    int outputText(Iterator<Object> stream) {
        new CliRenderer().renderStream(stream, terminal, errorConsole);
        return 0;
    }

    int outputJson(Iterator<Object> stream, Map<String, Object> config) {
        StringBuilder resultText = new StringBuilder();
        int chunkCount = 0;
        boolean hasLlmOutput = false;
        while (stream.hasNext()) {
            Object chunk = stream.next();
            chunkCount += 1;
            String chunkType = stringValue(readMember(chunk, "type"));
            if (CliRenderer.CHUNK_LLM_OUTPUT.equals(chunkType)) {
                hasLlmOutput = true;
                resultText.append(extractContent(readMember(chunk, "payload")));
            } else if (CliRenderer.CHUNK_ANSWER.equals(chunkType) && !hasLlmOutput) {
                resultText.append(extractContent(readMember(chunk, "payload")));
            }
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("result", resultText.toString());
        output.put("chunks", chunkCount);
        output.put("model", config == null ? null : config.get("model"));
        terminal.print(writeJson(output, true));
        terminal.print(System.lineSeparator());
        return 0;
    }

    int outputStreamJson(Iterator<Object> stream) {
        while (stream.hasNext()) {
            Object chunk = stream.next();
            Object payload = readMember(chunk, "payload");
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("type", readMember(chunk, "type"));
            line.put("index", readMember(chunk, "index"));
            line.put("payload", payload instanceof String || payload instanceof Map<?, ?> ? payload : String.valueOf(payload));
            terminal.print(writeJson(line, false));
            terminal.print(System.lineSeparator());
        }
        return 0;
    }

    void printError(Throwable error) {
        String message = error == null ? "" : String.valueOf(error.getMessage());
        String normalized = message.toLowerCase();
        String hint;
        if (normalized.contains("rate_limit") || normalized.contains("429")) {
            hint = "Rate limited. Please try again later.";
        } else if (normalized.contains("authentication") || normalized.contains("401")) {
            hint = "API Key invalid. Check OPENJIUWEN_API_KEY.";
        } else if (normalized.contains("too long") || normalized.contains("context_length")) {
            hint = "Context too long. Use /compact to trim history.";
        } else if (normalized.contains("timeout")) {
            hint = "Request timed out. Check your network.";
        } else {
            hint = "Error: " + message;
        }
        errorConsole.println("[red]✗ " + hint + "[/red]");
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return unwrap(completionException.getCause());
        }
        return throwable;
    }

    private static String extractContent(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            Object content = map.get("content");
            if (content != null && !String.valueOf(content).isBlank()) {
                return String.valueOf(content);
            }
            Object output = map.get("output");
            return output == null ? "" : String.valueOf(output);
        }
        return payload == null ? "" : String.valueOf(payload);
    }

    private static Object readMember(Object target, String name) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            return map.get(name);
        }
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            // Fall through to getter lookup.
        }
        try {
            String methodName = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String writeJson(Map<String, Object> value, boolean pretty) {
        try {
            return pretty
                    ? MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value)
                    : MAPPER.writer().without(SerializationFeature.INDENT_OUTPUT).writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize CLI output", exception);
        }
    }

    @FunctionalInterface
    public interface BackendFactory {
        AgentBackend create(Map<String, Object> config);
    }
}
