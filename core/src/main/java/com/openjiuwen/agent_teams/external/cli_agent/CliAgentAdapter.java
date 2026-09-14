/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.external.cli_agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Mirrors Python's {@code CliAgentAdapter}, built-in adapter registry, and helper functions in
 * {@code openjiuwen/agent_teams/external/cli_agent/adapters.py}.
 */
public record CliAgentAdapter(
        String name,
        List<String> command,
        String inputFormat,
        String completion,
        boolean structuredOutput,
        boolean supportsStdinInjection,
        String promptFlag,
        String sessionFlag,
        String resumeFlag,
        List<String> continueArgs,
        String mcpInject,
        String systemPromptInject,
        List<String> envStripPrefixes
) {

    public static final String INPUT_TEXT = "text";
    public static final String INPUT_CLAUDE_STREAM_JSON = "claude_stream_json";
    public static final String INPUT_STREAM_JSON = INPUT_CLAUDE_STREAM_JSON;
    public static final String INPUT_CODEX_PROTO = "codex_proto";

    public static final String MCP_INJECT_NONE = "none";
    public static final String MCP_INJECT_CLAUDE_FLAG = "claude_flag";
    public static final String MCP_INJECT_CODEX_OVERRIDE = "codex_override";
    public static final String MCP_INJECT_GEMINI_ADD = "gemini_add";
    public static final String MCP_INJECT_HERMES_ADD = "hermes_add";

    public static final String SYSTEM_PROMPT_NONE = "none";
    public static final String SYSTEM_PROMPT_CLAUDE_APPEND = "claude_append";
    public static final String SYSTEM_PROMPT_CODEX_DEVELOPER = "codex_developer";

    public static final String COMPLETION_NONE = "none";
    public static final String COMPLETION_RESULT_JSON = "result_json";
    public static final String COMPLETION_CODEX_TASK_COMPLETE = "codex_task_complete";
    public static final String COMPLETION_CODEX_JSON = "codex_json";
    public static final String COMPLETION_MARKER_PREFIX = "marker:";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int SUMMARY_LIMIT = 500;
    private static final Set<String> LIFECYCLE_EVENT_TYPES = Set.of(
            "result",
            "turn.started",
            "turn.completed",
            "thread.started",
            "session_init",
            "system",
            "task_started",
            "task_complete"
    );
    private static final Map<String, CliAgentAdapter> BUILTIN = createBuiltinAdapters();

    public CliAgentAdapter {
        name = Objects.requireNonNull(name, "name");
        command = List.copyOf(Objects.requireNonNull(command, "command"));
        inputFormat = inputFormat == null ? INPUT_TEXT : inputFormat;
        completion = completion == null ? COMPLETION_NONE : completion;
        continueArgs = continueArgs == null ? List.of() : List.copyOf(continueArgs);
        mcpInject = mcpInject == null ? MCP_INJECT_NONE : mcpInject;
        systemPromptInject = systemPromptInject == null ? SYSTEM_PROMPT_NONE : systemPromptInject;
        envStripPrefixes = envStripPrefixes == null ? List.of() : List.copyOf(envStripPrefixes);
    }

    public CliAgentAdapter(String name, List<String> command) {
        this(name, command, INPUT_TEXT, COMPLETION_NONE, false, true, null, null, null, List.of(),
                MCP_INJECT_NONE, SYSTEM_PROMPT_NONE, List.of());
    }

    public List<String> buildCommand() {
        return buildCommand(List.of());
    }

    public List<String> buildCommand(List<String> extraArgs) {
        List<String> argv = new ArrayList<>(command);
        argv.addAll(extraArgs);
        return argv;
    }

    public List<String> buildTurnCommand(String prompt, String sessionId, boolean firstTurn) {
        return buildTurnCommand(prompt, sessionId, firstTurn, List.of());
    }

    public List<String> buildTurnCommand(String prompt, String sessionId, boolean firstTurn, List<String> extraArgs) {
        List<String> argv = new ArrayList<>(command);
        if (sessionId != null && !sessionId.isEmpty()) {
            if (!firstTurn && resumeFlag != null) {
                argv.add(resumeFlag);
                argv.add(sessionId);
            } else if (sessionFlag != null) {
                argv.add(sessionFlag);
                argv.add(sessionId);
            }
        }
        if (!firstTurn && !continueArgs.isEmpty()) {
            argv.addAll(continueArgs);
        }
        argv.addAll(extraArgs);
        if (promptFlag != null) {
            argv.add(promptFlag);
            argv.add(prompt);
        } else {
            argv.add(prompt);
        }
        return argv;
    }

    public String formatInput(String text) {
        try {
            if (INPUT_CLAUDE_STREAM_JSON.equals(inputFormat)) {
                return OBJECT_MAPPER.writeValueAsString(Map.of(
                        "type", "user",
                        "message", Map.of("role", "user", "content", text)
                ));
            }
            if (INPUT_CODEX_PROTO.equals(inputFormat)) {
                return OBJECT_MAPPER.writeValueAsString(Map.of(
                        "id", java.util.UUID.randomUUID().toString().replace("-", ""),
                        "op", Map.of(
                                "type", "user_input",
                                "items", List.of(Map.of("type", "text", "text", text))
                        )
                ));
            }
            return text;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to encode adapter input", exception);
        }
    }

    public boolean isTurnComplete(String line) {
        if (COMPLETION_RESULT_JSON.equals(completion)) {
            return jsonFieldEquals(line, List.of("type"), "result");
        }
        if (COMPLETION_CODEX_TASK_COMPLETE.equals(completion)) {
            return jsonFieldEquals(line, List.of("msg", "type"), "task_complete")
                    || jsonFieldEquals(line, List.of("type"), "task_complete");
        }
        if (COMPLETION_CODEX_JSON.equals(completion)) {
            return jsonFieldEquals(line, List.of("type"), "turn.completed");
        }
        if (completion.startsWith(COMPLETION_MARKER_PREFIX)) {
            String marker = completion.substring(COMPLETION_MARKER_PREFIX.length());
            return !marker.isEmpty() && line.contains(marker);
        }
        return false;
    }

    public String summarizeOutputLine(String line) {
        String text = line == null ? "" : line.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (!structuredOutput) {
            return text;
        }
        if (!text.startsWith("{")) {
            return null;
        }
        try {
            Map<String, Object> event = OBJECT_MAPPER.readValue(text, new TypeReference<>() {
            });
            return summarizeEvent(event);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    public List<String> mcpLaunchArgs(String serverName, List<String> serverCommand) {
        if (serverCommand == null || serverCommand.isEmpty()) {
            return List.of();
        }
        String binary = serverCommand.get(0);
        List<String> args = serverCommand.subList(1, serverCommand.size());
        if (MCP_INJECT_CLAUDE_FLAG.equals(mcpInject)) {
            try {
                Map<String, Object> config = Map.of(
                        "mcpServers",
                        Map.of(serverName, Map.of("command", binary, "args", args))
                );
                return List.of("--mcp-config", OBJECT_MAPPER.writeValueAsString(config));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("failed to encode MCP config", exception);
            }
        }
        if (MCP_INJECT_CODEX_OVERRIDE.equals(mcpInject)) {
            String key = serverName.replace("-", "_");
            List<String> argv = new ArrayList<>();
            argv.add("-c");
            argv.add("mcp_servers." + key + ".command=" + jsonString(binary));
            if (!args.isEmpty()) {
                argv.add("-c");
                argv.add("mcp_servers." + key + ".args=" + jsonString(args));
            }
            return argv;
        }
        return List.of();
    }

    public List<String> mcpRegisterCommand(String serverName, List<String> serverCommand) {
        if (serverCommand == null || serverCommand.isEmpty()) {
            return null;
        }
        String binary = serverCommand.get(0);
        List<String> args = serverCommand.subList(1, serverCommand.size());
        String cli = command.isEmpty() ? name : command.get(0);
        if (MCP_INJECT_GEMINI_ADD.equals(mcpInject)) {
            List<String> argv = new ArrayList<>(List.of(cli, "mcp", "add", serverName, binary));
            argv.addAll(args);
            return argv;
        }
        if (MCP_INJECT_HERMES_ADD.equals(mcpInject)) {
            return List.of(cli, "mcp", "add", serverName, "--command", binary);
        }
        return null;
    }

    public List<String> systemPromptArgs(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        if (SYSTEM_PROMPT_CLAUDE_APPEND.equals(systemPromptInject)) {
            return List.of("--append-system-prompt", text);
        }
        if (SYSTEM_PROMPT_CODEX_DEVELOPER.equals(systemPromptInject)) {
            return List.of("-c", "developer_instructions=" + jsonString(text));
        }
        return List.of();
    }

    public boolean injectsSystemPromptViaArg() {
        return !SYSTEM_PROMPT_NONE.equals(systemPromptInject);
    }

    public CliAgentAdapter withCommand(List<String> commandOverride) {
        return new CliAgentAdapter(
                name,
                commandOverride,
                inputFormat,
                completion,
                structuredOutput,
                supportsStdinInjection,
                promptFlag,
                sessionFlag,
                resumeFlag,
                continueArgs,
                mcpInject,
                systemPromptInject,
                envStripPrefixes
        );
    }

    public static List<String> availableAdapters() {
        return new ArrayList<>(BUILTIN.keySet());
    }

    public static CliAgentAdapter buildAdapter(String name) {
        return buildAdapter(name, null);
    }

    public static CliAgentAdapter buildAdapter(String name, List<String> commandOverride) {
        CliAgentAdapter adapter = BUILTIN.get(name);
        if (adapter == null) {
            ErrorHelper.raiseError(
                    StatusCode.AGENT_TEAM_CONFIG_INVALID,
                    null,
                    null,
                    null,
                    Map.of("reason", "unknown cli agent adapter '" + name + "'; known: " + String.join(", ", availableAdapters()))
            );
        }
        return commandOverride == null ? adapter : adapter.withCommand(commandOverride);
    }

    private static String summarizeEvent(Map<String, Object> event) {
        Object type = event.get("type");
        if (type instanceof String typeString && LIFECYCLE_EVENT_TYPES.contains(typeString)) {
            return null;
        }
        Object message = event.get("message");
        if (message instanceof Map<?, ?> messageMap) {
            String summary = summarizeContentBlocks(messageMap.get("content"));
            if (summary != null) {
                return summary;
            }
        }
        Object item = event.get("item");
        if (item instanceof Map<?, ?> itemMap) {
            for (String key : List.of("text", "command", "aggregated_output")) {
                Object value = itemMap.get(key);
                if (value instanceof String string && !string.isBlank()) {
                    return truncate(string.trim());
                }
            }
        }
        for (String key : List.of("text", "content", "delta")) {
            Object value = event.get(key);
            if (value instanceof String string && !string.isBlank()) {
                return truncate(string.trim());
            }
        }
        return null;
    }

    private static String summarizeContentBlocks(Object content) {
        if (!(content instanceof Collection<?> blocks)) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (Object block : blocks) {
            if (!(block instanceof Map<?, ?> blockMap)) {
                continue;
            }
            Object blockType = blockMap.get("type");
            if ("text".equals(blockType) && blockMap.get("text") instanceof String text) {
                parts.add(text);
            } else if ("tool_use".equals(blockType) && blockMap.get("name") instanceof String name) {
                parts.add("→ " + name);
            }
        }
        String joined = String.join(" ", parts).trim();
        return joined.isEmpty() ? null : truncate(joined);
    }

    private static boolean jsonFieldEquals(String line, List<String> path, String expected) {
        String stripped = line == null ? "" : line.trim();
        if (!stripped.startsWith("{")) {
            return false;
        }
        try {
            Object node = OBJECT_MAPPER.readValue(stripped, Object.class);
            for (String key : path) {
                if (!(node instanceof Map<?, ?> map)) {
                    return false;
                }
                node = map.get(key);
            }
            return expected.equals(node);
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    private static String jsonString(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to encode json literal", exception);
        }
    }

    private static String truncate(String value) {
        return value.length() <= SUMMARY_LIMIT ? value : value.substring(0, SUMMARY_LIMIT);
    }

    private static Map<String, CliAgentAdapter> createBuiltinAdapters() {
        Map<String, CliAgentAdapter> builtins = new LinkedHashMap<>();
        builtins.put("claude", new CliAgentAdapter(
                "claude",
                List.of("claude", "--print", "--input-format", "stream-json", "--output-format", "stream-json", "--verbose", "--dangerously-skip-permissions"),
                INPUT_CLAUDE_STREAM_JSON,
                COMPLETION_RESULT_JSON,
                true,
                true,
                null,
                null,
                null,
                List.of(),
                MCP_INJECT_CLAUDE_FLAG,
                SYSTEM_PROMPT_CLAUDE_APPEND,
                List.of("CLAUDECODE", "CLAUDE_CODE_")
        ));
        builtins.put("codex", new CliAgentAdapter(
                "codex",
                List.of("codex", "exec", "--json", "--dangerously-bypass-approvals-and-sandbox", "--skip-git-repo-check"),
                INPUT_TEXT,
                COMPLETION_CODEX_JSON,
                true,
                false,
                null,
                null,
                null,
                List.of(),
                MCP_INJECT_CODEX_OVERRIDE,
                SYSTEM_PROMPT_CODEX_DEVELOPER,
                List.of()
        ));
        builtins.put("gemini", new CliAgentAdapter(
                "gemini",
                List.of("gemini", "-o", "stream-json", "-y"),
                INPUT_TEXT,
                COMPLETION_NONE,
                true,
                false,
                "-p",
                "--session-id",
                "--resume",
                List.of(),
                MCP_INJECT_GEMINI_ADD,
                SYSTEM_PROMPT_NONE,
                List.of()
        ));
        builtins.put("openclaw", new CliAgentAdapter(
                "openclaw",
                List.of("openclaw", "--local"),
                INPUT_TEXT,
                COMPLETION_NONE,
                false,
                false,
                "--message",
                "--session-id",
                null,
                List.of(),
                MCP_INJECT_NONE,
                SYSTEM_PROMPT_NONE,
                List.of()
        ));
        builtins.put("hermes", new CliAgentAdapter(
                "hermes",
                List.of("hermes", "-z", "--yolo"),
                INPUT_TEXT,
                COMPLETION_NONE,
                false,
                false,
                null,
                null,
                null,
                List.of("--continue"),
                MCP_INJECT_HERMES_ADD,
                SYSTEM_PROMPT_NONE,
                List.of()
        ));
        builtins.put("generic", new CliAgentAdapter(
                "generic",
                List.of(),
                INPUT_TEXT,
                COMPLETION_MARKER_PREFIX + "<<END_OF_TURN>>",
                false,
                true,
                null,
                null,
                null,
                List.of(),
                MCP_INJECT_NONE,
                SYSTEM_PROMPT_NONE,
                List.of()
        ));
        return Map.copyOf(builtins);
    }
}
