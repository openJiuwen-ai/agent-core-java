/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.agent_teams;

import com.openjiuwen.agentteams.agent.TeamAgent;
import com.openjiuwen.agentteams.factory.TeamFactory;
import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.team.ModelPoolEntry;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.core.runner.Runner;
import examples.utils.SharedExampleApiConfigLoader;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared implementation for the Java agent team E2E example.
 *
 * This aligns with Python's agent_team_e2e.py functionality:
 * - Creates a team with leader and teammates
 * - Runs interactive CLI loop
 * - Supports streaming output
 */
@SuppressWarnings("unchecked")
final class AgentTeamE2eExampleSupport {

    private static final String TEAM_NAME = "my_project_team_java";
    private static final String LEADER_NAME = "team_leader";
    private static final String SESSION_ID = "agent_team_session_java";

    private static final String COLOR_RESET = "\u001B[0m";
    private static final String COLOR_DIM = "\u001B[2m";
    private static final String COLOR_GREEN = "\u001B[92m";
    private static final String COLOR_CYAN = "\u001B[96m";
    private static final String COLOR_YELLOW = "\u001B[93m";

    private AgentTeamE2eExampleSupport() {
    }

    static void run(String[] args) throws Exception {
        String sessionId = args.length > 0 ? args[0] : SESSION_ID;
        String initialQuery = args.length > 1 ? args[1] : "hello";

        TeamAgentSpec spec = buildTeamSpec();
        TeamAgent leader = TeamFactory.createAgentTeam(spec);

        Runner.start();

        try {
            printBanner(sessionId);
            runInteractive(leader, sessionId, initialQuery);
        } finally {
            leader.close();
            Runner.stop();
            System.out.println("Done.");
        }
    }

    private static TeamAgentSpec buildTeamSpec() {
        String apiBase = SharedExampleApiConfigLoader.getApiBase();
        String apiKey = SharedExampleApiConfigLoader.getApiKey();
        String modelName = SharedExampleApiConfigLoader.getModelName();
        String provider = SharedExampleApiConfigLoader.getModelProvider();

        ModelPoolEntry modelPoolEntry = ModelPoolEntry.builder()
                .modelId(UUID.randomUUID().toString())
                .provider(provider)
                .modelName(modelName)
                .apiKey(apiKey)
                .apiBaseUrl(apiBase)
                .description("Primary model for team")
                .weight(1)
                .metadata(buildModelMetadata())
                .build();

        List<ModelPoolEntry> modelPool = new ArrayList<>();
        modelPool.add(modelPoolEntry);

        TeamMemberSpec leaderSpec = TeamMemberSpec.builder()
                .name(LEADER_NAME)
                .role(TeamRole.LEADER)
                .description("Team leader that coordinates tasks among team members")
                .modelName(modelName)
                .build();

        List<TeamMemberSpec> members = new ArrayList<>();
        members.add(leaderSpec);

        return TeamAgentSpec.builder()
                .name(TEAM_NAME)
                .description("Java agent team E2E example")
                .members(members)
                .modelPool(modelPool)
                .modelPoolStrategy("round_robin")
                .lifecycle("temporary")
                .teammateMode("build_mode")
                .spawnMode("inprocess")
                .transport("inprocess")
                .storage("sqlite")
                .language("cn")
                .build();
    }

    private static Map<String, Object> buildModelMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();

        Map<String, Object> clientMeta = new LinkedHashMap<>();
        clientMeta.put("timeout", 120);
        clientMeta.put("verify_ssl", false);
        clientMeta.put("rate_limit", 10.0);

        Map<String, Object> requestMeta = new LinkedHashMap<>();
        requestMeta.put("temperature", 0.2);
        requestMeta.put("top_p", 0.9);

        metadata.put("client", clientMeta);
        metadata.put("request", requestMeta);

        return metadata;
    }

    private static void runInteractive(TeamAgent leader, String sessionId, String initialQuery) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", initialQuery);

        System.out.println("Starting leader stream with query: " + initialQuery);

        Iterator<Object> stream = leader.stream(inputs, sessionId);

        consumeStream(stream);

        while (true) {
            System.out.println();
            System.out.print("[You] > ");
            System.out.flush();

            String userInput = reader.readLine();
            if (userInput == null) {
                System.out.println();
                System.out.println("Input stream ended, exiting.");
                break;
            }

            userInput = userInput.trim();
            if (userInput.isEmpty()) {
                continue;
            }

            if ("exit".equalsIgnoreCase(userInput) || "quit".equalsIgnoreCase(userInput)) {
                System.out.println("Exiting...");
                break;
            }

            leader.interact(userInput);
            System.out.println("[System] Input sent to leader: " + userInput);

            // Consume any new stream output
            tryConsumeNewOutput(leader, inputs, sessionId);
        }
    }

    private static void consumeStream(Iterator<Object> stream) {
        String currentType = "";
        StringBuilder buffer = new StringBuilder();
        boolean hasLlmOutput = false;

        while (stream.hasNext()) {
            Object chunk = stream.next();
            String chunkType = extractChunkType(chunk);
            Object payload = extractPayload(chunk);

            if ("tool_call".equals(chunkType)) {
                flushBuffer(currentType, buffer);
                currentType = "";
                buffer.setLength(0);
                String toolName = extractStringField(payload, "tool_name");
                String toolArgs = extractStringField(payload, "tool_args");
                System.out.println(COLOR_CYAN + "● " + toolName + COLOR_RESET);
                if (toolArgs != null && !toolArgs.isEmpty()) {
                    System.out.println(COLOR_DIM + "(" + toolArgs + ")" + COLOR_RESET);
                }
                continue;
            }

            if ("tool_result".equals(chunkType)) {
                String toolResult = extractStringField(payload, "tool_result");
                if (toolResult == null) {
                    toolResult = String.valueOf(payload);
                }
                String preview = toolResult.length() > 200 ? toolResult.substring(0, 200) : toolResult;
                System.out.println(COLOR_DIM + "  ⎿ " + preview + COLOR_RESET);
                System.out.println();
                continue;
            }

            if ("message".equals(chunkType)) {
                flushBuffer(currentType, buffer);
                currentType = "";
                buffer.setLength(0);
                String content = extractContent(payload);
                System.out.println(COLOR_DIM + "  ⚙ " + content + COLOR_RESET);
                continue;
            }

            if ("__interaction__".equals(chunkType)) {
                flushBuffer(currentType, buffer);
                currentType = "";
                buffer.setLength(0);
                System.out.println(COLOR_YELLOW + "[Interaction] " + payload + COLOR_RESET);
                continue;
            }

            if ("answer".equals(chunkType) && hasLlmOutput) {
                continue;
            }

            if (!chunkType.equals(currentType)) {
                flushBuffer(currentType, buffer);
                currentType = chunkType;
                buffer.setLength(0);
            }

            if ("llm_output".equals(chunkType)) {
                hasLlmOutput = true;
            }

            String content = extractContent(payload);
            if (content != null && !content.isEmpty()) {
                buffer.append(content);
            }
        }

        flushBuffer(currentType, buffer);
        System.out.println();
        System.out.println("Leader stream finished.");
    }

    private static void tryConsumeNewOutput(TeamAgent leader, Map<String, Object> inputs, String sessionId) {
        // In a real implementation, this would check for new streaming output
        // For now, we create a new stream if needed
        try {
            Iterator<Object> newStream = leader.stream(inputs, sessionId);
            if (newStream.hasNext()) {
                consumeStream(newStream);
            }
        } catch (Exception e) {
            // Stream may not have new output, which is fine
        }
    }

    private static String extractChunkType(Object chunk) {
        if (chunk instanceof Map<?, ?> map) {
            Object type = map.get("type");
            return type != null ? String.valueOf(type) : "";
        }
        return "";
    }

    private static Object extractPayload(Object chunk) {
        if (chunk instanceof Map<?, ?> map) {
            return map.get("payload");
        }
        return null;
    }

    private static String extractContent(Object payload) {
        if (payload == null) {
            return "";
        }
        if (payload instanceof String text) {
            return text;
        }
        if (payload instanceof Map<?, ?> map) {
            Object content = map.get("content");
            if (content != null) {
                return String.valueOf(content);
            }
            Object output = map.get("output");
            if (output != null) {
                return String.valueOf(output);
            }
        }
        return String.valueOf(payload);
    }

    private static String extractStringField(Object payload, String fieldName) {
        if (payload instanceof Map<?, ?> map) {
            Object value = map.get(fieldName);
            return value != null ? String.valueOf(value) : null;
        }
        return null;
    }

    private static void flushBuffer(String chunkType, StringBuilder buffer) {
        String text = buffer.toString();
        if (text.isEmpty() || text.isBlank()) {
            return;
        }

        switch (chunkType) {
            case "llm_reasoning" ->
                    System.out.println(COLOR_DIM + "[Reasoning] " + text + COLOR_RESET);
            case "llm_output" ->
                    System.out.println(COLOR_GREEN + "[Output] " + COLOR_RESET + text);
            case "answer" ->
                    System.out.println(COLOR_YELLOW + "[Answer] " + COLOR_RESET + text);
            default -> {
                if (!chunkType.isEmpty()) {
                    System.out.println("[" + chunkType + "] " + text);
                }
            }
        }
    }

    private static void printBanner(String sessionId) {
        System.out.println("============================================================");
        System.out.println("Agent Team E2E — Interactive CLI (Java)");
        System.out.println("Type your message and press Enter to interact with the leader.");
        System.out.println("Type 'exit' or 'quit' to stop.");
        System.out.println("Session ID: " + sessionId);
        System.out.println("============================================================");
        System.out.println();
    }
}