/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.agent_teams;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.stream.OutputSchema;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Interactive E2E example for a Runner-owned TeamAgentSpec runtime.
 *
 * <p>Mirrors Python's {@code examples.agent_teams.agent_team_runner_owner_e2e}.</p>
 */
public final class AgentTeamRunnerOwnerE2e {
    public static final String DEFAULT_SESSION_ID = "agent_team_owner_session";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private AgentTeamRunnerOwnerE2e() {
    }

    public static LoadedSpec loadTeamSpec(Path path) throws Exception {
        AgentTeamE2e.LoadedConfig loaded = AgentTeamE2e.loadConfig(path);
        return new LoadedSpec(AgentTeamE2e.buildSpec(loaded.teamConfig()), loaded.runtimeConfig());
    }

    public static Map<String, TeamAgentSpec> initialSpecs(TeamAgentSpec baseSpec, Map<String, Object> runtimeConfig) {
        String baseName = baseSpec.getTeamName();
        String altName = stringOrDefault(runtimeConfig != null ? runtimeConfig.get("alt_team_name") : null,
                baseName + "_alt");
        Map<String, TeamAgentSpec> specs = new LinkedHashMap<>();
        specs.put(baseName, baseSpec);
        specs.put(altName, copySpecWithTeamName(baseSpec, altName));
        return specs;
    }

    public static void printHelp(PrintStream out) {
        out.println("Commands:");
        out.println("  <text>                    send user input to the active team runtime");
        out.println("  :switch <sid> <query>     same session -> interact; new session -> switch with runtime_ready");
        out.println("  :switch-team <team> <sid> <query>  switch across team_name and wait for runtime_ready");
        out.println("  :pause                    pause the active team runtime");
        out.println("  :quit                     stop the stream and exit");
    }

    public static void main(String[] args) throws Exception {
        Path dir = args != null && args.length > 0 && args[0] != null && !args[0].isBlank()
                ? Path.of(args[0]).toAbsolutePath().normalize()
                : AgentTeamE2e.exampleDir();
        AgentTeamE2e.configureExampleLogging(AgentTeamE2e.logConfigPath(dir));
        AgentTeamE2e.applyDefaultEnvironment();

        LoadedSpec loaded = loadTeamSpec(AgentTeamE2e.teamConfigPath(dir));
        Map<String, TeamAgentSpec> specs = initialSpecs(loaded.baseSpec(), loaded.runtimeConfig());
        String initialSession = stringOrDefault(loaded.runtimeConfig().get("session_id"), DEFAULT_SESSION_ID);
        String initialQuery = stringOrDefault(loaded.runtimeConfig().get("initial_query"), "hello");
        String initialTeamName = stringOrDefault(loaded.runtimeConfig().get("team_name"), loaded.baseSpec().getTeamName());

        TeamStreamCli cli = new TeamStreamCli(loaded.baseSpec(), specs, RunnerGateway.INSTANCE, System.out);

        Runner.start();
        try {
            printHelp(System.out);
            System.out.println("[system] available teams: " + String.join(", ", specs.keySet()));
            Map<String, Object> firstAck = cli.startSession(initialTeamName, initialSession, initialQuery);
            System.out.println("[system] active team=" + initialTeamName + " session=" + initialSession + " ack=" + firstAck);
            runConsoleLoop(cli);
        } finally {
            cli.stopStream();
            Runner.stop();
        }
    }

    private static void runConsoleLoop(TeamStreamCli cli) {
        while (true) {
            String raw = E2eUtils.ainput("\n[you] > ").join();
            if (raw == null) {
                return;
            }
            raw = raw.strip();
            if (raw.isEmpty()) {
                continue;
            }
            if (":quit".equals(raw)) {
                return;
            }
            if (":pause".equals(raw)) {
                boolean paused = cli.pause();
                System.out.println("[system] pause requested: ok=" + paused
                        + " active_team=" + cli.currentTeamName()
                        + " active_session=" + cli.currentSessionId());
                continue;
            }
            if (raw.startsWith(":switch-team ")) {
                String[] parts = raw.split(" ", 4);
                if (parts.length < 4) {
                    System.out.println("[system] usage: :switch-team <team_name> <session_id> <query>");
                    continue;
                }
                RouteResult result = cli.routeUserRequest(parts[1].strip(), parts[2].strip(), parts[3].strip());
                printRouteResult("team switch", parts[1].strip(), parts[2].strip(), result);
                continue;
            }
            if (raw.startsWith(":switch ")) {
                String[] parts = raw.split(" ", 3);
                if (parts.length < 3) {
                    System.out.println("[system] usage: :switch <session_id> <query>");
                    continue;
                }
                if (cli.currentTeamName() == null) {
                    System.out.println("[system] no active team");
                    continue;
                }
                RouteResult result = cli.routeUserRequest(cli.currentTeamName(), parts[1].strip(), parts[2].strip());
                printRouteResult("switch", cli.currentTeamName(), parts[1].strip(), result);
                continue;
            }

            boolean delivered = cli.interact(raw);
            System.out.println("[system] input delivered=" + delivered
                    + " team=" + cli.currentTeamName()
                    + " session=" + cli.currentSessionId());
        }
    }

    private static void printRouteResult(String label, String teamName, String sessionId, RouteResult result) {
        if ("interact".equals(result.action())) {
            System.out.println("[system] same-session follow-up routed to interact: " + result.result());
        } else if ("switch_committed".equals(result.action())) {
            System.out.println("[system] " + label + " committed: active team=" + teamName
                    + " session=" + sessionId + " ack=" + result.result());
        } else {
            System.out.println("[system] " + label + " rolled back: " + result.result());
        }
    }

    private static TeamAgentSpec copySpecWithTeamName(TeamAgentSpec spec, String teamName) {
        TeamAgentSpec copy = MAPPER.convertValue(spec, TeamAgentSpec.class);
        copy.setTeamName(teamName);
        return copy;
    }

    private static String stringOrDefault(Object value, String fallback) {
        String text = value != null ? String.valueOf(value) : null;
        return text != null && !text.isBlank() ? text : fallback;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payloadOf(Object chunk) {
        Object payload = null;
        if (chunk instanceof OutputSchema output) {
            payload = output.getPayload();
        } else if (chunk instanceof Map<?, ?> map) {
            payload = map.get("payload");
        }
        if (payload instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            map.forEach((key, value) -> converted.put(String.valueOf(key), value));
            return converted;
        }
        return new LinkedHashMap<>();
    }

    public interface RuntimeGateway {
        Iterator<Object> runAgentTeamStreaming(TeamAgentSpec spec, Map<String, Object> inputs, String sessionId);

        boolean interactAgentTeam(String userInput, String teamName, String sessionId);

        boolean pauseAgentTeam(String teamName, String sessionId);
    }

    public enum RunnerGateway implements RuntimeGateway {
        INSTANCE;

        @Override
        public Iterator<Object> runAgentTeamStreaming(TeamAgentSpec spec, Map<String, Object> inputs, String sessionId) {
            return Runner.runAgentTeamStreaming(spec, inputs, sessionId);
        }

        @Override
        public boolean interactAgentTeam(String userInput, String teamName, String sessionId) {
            return Runner.interactAgentTeam(userInput, teamName, sessionId);
        }

        @Override
        public boolean pauseAgentTeam(String teamName, String sessionId) {
            return Runner.pauseAgentTeam(teamName, sessionId);
        }
    }

    public static final class TeamStreamCli {
        private final TeamAgentSpec baseSpec;
        private final Map<String, TeamAgentSpec> specs;
        private final RuntimeGateway gateway;
        private final PrintStream out;
        private StreamHandle streamHandle;
        private String activeTeamName;
        private String activeSessionId;
        private String pendingTeamName;
        private String pendingSessionId;

        public TeamStreamCli(
                TeamAgentSpec baseSpec,
                Map<String, TeamAgentSpec> specs,
                RuntimeGateway gateway,
                PrintStream out
        ) {
            this.baseSpec = baseSpec;
            this.specs = new LinkedHashMap<>(specs);
            this.gateway = gateway;
            this.out = out;
        }

        public String currentSessionId() {
            return activeSessionId;
        }

        public String currentTeamName() {
            return activeTeamName;
        }

        public Map<String, Object> startSession(String teamName, String sessionId, String query) {
            pendingTeamName = teamName;
            pendingSessionId = sessionId;
            StreamHandle handle = restartStream(teamName, sessionId, query);
            Map<String, Object> ack = awaitRuntimeReady(handle);
            activeTeamName = teamName;
            activeSessionId = sessionId;
            pendingTeamName = null;
            pendingSessionId = null;
            return ack;
        }

        public SwitchResult switchSession(String teamName, String sessionId, String query) {
            String previousTeam = activeTeamName;
            String previousSession = activeSessionId;
            pendingTeamName = teamName;
            pendingSessionId = sessionId;
            try {
                StreamHandle handle = restartStream(teamName, sessionId, query);
                Map<String, Object> ack = awaitRuntimeReady(handle);
                activeTeamName = teamName;
                activeSessionId = sessionId;
                pendingTeamName = null;
                pendingSessionId = null;
                return new SwitchResult(true, ack);
            } catch (RuntimeException exc) {
                pendingTeamName = null;
                pendingSessionId = null;
                activeTeamName = previousTeam;
                activeSessionId = previousSession;
                String rollbackTo = previousSession != null ? previousSession : "none";
                return new SwitchResult(false, "switch failed before ack: " + exc.getMessage()
                        + "; rollback active_team=" + (previousTeam != null ? previousTeam : "none")
                        + " active_session=" + rollbackTo);
            }
        }

        public RouteResult routeUserRequest(String teamName, String sessionId, String query) {
            if (teamName.equals(activeTeamName) && sessionId.equals(activeSessionId)) {
                boolean delivered = interact(query);
                return new RouteResult("interact", "delivered=" + delivered
                        + " active_team=" + activeTeamName
                        + " active_session=" + activeSessionId);
            }
            SwitchResult switched = switchSession(teamName, sessionId, query);
            return new RouteResult(switched.committed() ? "switch_committed" : "switch_rolled_back", switched.result());
        }

        public boolean interact(String userInput) {
            if (activeTeamName == null || activeSessionId == null) {
                return false;
            }
            return gateway.interactAgentTeam(userInput, activeTeamName, activeSessionId);
        }

        public boolean pause() {
            if (activeTeamName == null || activeSessionId == null) {
                return false;
            }
            return gateway.pauseAgentTeam(activeTeamName, activeSessionId);
        }

        public void stopStream() {
            StreamHandle handle = streamHandle;
            streamHandle = null;
            activeTeamName = null;
            activeSessionId = null;
            pendingTeamName = null;
            pendingSessionId = null;
            if (handle != null) {
                stopHandle(handle);
            }
        }

        private Map<String, Object> awaitRuntimeReady(StreamHandle handle) {
            try {
                return handle.runtimeReady().get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        private StreamHandle restartStream(String teamName, String sessionId, String query) {
            StreamHandle oldHandle = streamHandle;
            if (oldHandle != null) {
                boolean paused = gateway.pauseAgentTeam(oldHandle.teamName(), oldHandle.sessionId());
                out.println("[system] pause old runtime before switch: active_team=" + oldHandle.teamName()
                        + " active_session=" + oldHandle.sessionId() + " paused=" + paused);
                out.println("[system] stopping old stream before switch: active_team=" + oldHandle.teamName()
                        + " active_session=" + oldHandle.sessionId()
                        + " -> pending_team=" + teamName + " pending_session=" + sessionId);
                stopHandle(oldHandle);
            }

            TeamAgentSpec spec = getOrCreateSpec(teamName);
            CompletableFuture<Map<String, Object>> runtimeReady = new CompletableFuture<>();
            CompletableFuture<Void> task = CompletableFuture.runAsync(
                    () -> consumeStream(spec, sessionId, query, runtimeReady));
            StreamHandle handle = new StreamHandle(teamName, sessionId, query, runtimeReady, task);
            streamHandle = handle;
            out.println("[system] started new stream: active_team=" + activeTeamName
                    + " active_session=" + activeSessionId
                    + " pending_team=" + pendingTeamName
                    + " pending_session=" + pendingSessionId);
            return handle;
        }

        private void stopHandle(StreamHandle handle) {
            handle.task().cancel(true);
        }

        private TeamAgentSpec getOrCreateSpec(String teamName) {
            TeamAgentSpec spec = specs.get(teamName);
            if (spec != null) {
                return spec;
            }
            TeamAgentSpec created = copySpecWithTeamName(baseSpec, teamName);
            specs.put(teamName, created);
            return created;
        }

        private void consumeStream(
                TeamAgentSpec spec,
                String sessionId,
                String query,
                CompletableFuture<Map<String, Object>> runtimeReady
        ) {
            try {
                Iterator<Object> chunks = gateway.runAgentTeamStreaming(spec, Map.of("query", query), sessionId);
                while (!Thread.currentThread().isInterrupted() && chunks.hasNext()) {
                    Map<String, Object> payload = payloadOf(chunks.next());
                    if ("team.runtime_ready".equals(payload.get("event_type")) && !runtimeReady.isDone()) {
                        out.println("[ack] session=" + sessionId + " payload=" + payload);
                        runtimeReady.complete(payload);
                        continue;
                    }
                    out.println("[stream] session=" + sessionId + " payload=" + payload);
                }
            } catch (RuntimeException exc) {
                if (!runtimeReady.isDone()) {
                    runtimeReady.completeExceptionally(exc);
                }
                out.println("[error] session=" + sessionId + " error=" + exc.getMessage());
                throw exc;
            }
        }
    }

    public record LoadedSpec(TeamAgentSpec baseSpec, Map<String, Object> runtimeConfig) {
        public LoadedSpec {
            runtimeConfig = runtimeConfig != null ? new LinkedHashMap<>(runtimeConfig) : new LinkedHashMap<>();
        }
    }

    public record StreamHandle(
            String teamName,
            String sessionId,
            String query,
            CompletableFuture<Map<String, Object>> runtimeReady,
            CompletableFuture<Void> task
    ) {
    }

    public record SwitchResult(boolean committed, Object result) {
    }

    public record RouteResult(String action, Object result) {
    }
}
