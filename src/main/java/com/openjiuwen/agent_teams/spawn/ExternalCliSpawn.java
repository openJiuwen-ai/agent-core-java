/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.spawn;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.MemberRuntime;
import com.openjiuwen.agent_teams.agent.SpawnManager;
import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.external.ExternalCliRuntime;
import com.openjiuwen.agent_teams.external.ReinvokeCliRuntime;
import com.openjiuwen.agent_teams.external.cli_agent.CliAgentAdapter;
import com.openjiuwen.agent_teams.external.cli_agent.CliAgentSpawn;
import com.openjiuwen.agent_teams.prompts.TeamPromptSections;
import com.openjiuwen.agent_teams.rails.TeamPolicyRail;
import com.openjiuwen.agent_teams.schema.ExternalCliAgentSpec;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Spawns an external CLI agent as an in-process team member.
 *
 * <p>Mirrors Python's {@code _build_member_system_prompt} and
 * {@code external_cli_spawn} in
 * {@code openjiuwen/agent_teams/spawn/external_cli_spawn.py}.</p>
 */
public final class ExternalCliSpawn {

    public static final String DEFAULT_JOIN_PROMPT = "You have joined the team. Call read_inbox once now. "
            + "If you already have an assigned task, complete it fully: claim_task, do the work, then "
            + "complete_task and send_message to report. If there is no task yet, just acknowledge briefly "
            + "and END YOUR TURN now \u2014 do NOT wait, poll, or loop; the team will message you when "
            + "there is work.";

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;
    private static final String DEFAULT_MCP_SERVER_NAME = "openjiuwen-team";
    private static final List<String> DEFAULT_MCP_SERVER_COMMAND = List.of("openjiuwen-team-mcp");
    private static final CliRuntimeBuilder DEFAULT_RUNTIME_BUILDER = CliAgentSpawn::buildCliRuntime;
    private static final MemberRunner DEFAULT_MEMBER_RUNNER =
            (teammate, inputs, member, sessionId) -> teammate.startAgent(inputs);
    private static final RosterLookup DEFAULT_ROSTER_LOOKUP = new ReflectiveRosterLookup();

    private ExternalCliSpawn() {
    }

    public static CompletionStage<String> buildMemberSystemPrompt(
            TeamAgent teamAgent,
            TeamAgentSpec spec,
            TeamRuntimeContext ctx,
            String memberName
    ) {
        return buildMemberSystemPrompt(teamAgent, spec, ctx, memberName, DEFAULT_ROSTER_LOOKUP);
    }

    static CompletionStage<String> buildMemberSystemPrompt(
            TeamAgent teamAgent,
            TeamAgentSpec spec,
            TeamRuntimeContext ctx,
            String memberName,
            RosterLookup rosterLookup
    ) {
        Objects.requireNonNull(teamAgent, "teamAgent");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(ctx, "ctx");
        RosterLookup effectiveRosterLookup = rosterLookup == null ? DEFAULT_ROSTER_LOOKUP : rosterLookup;
        AgentConfigurator.ConfiguredTeamBackend backend = teamAgent.getTeamBackend();
        Collection<String> bridgeNames = sorted(effectiveRosterLookup.bridgeAgentNames(backend));
        return effectiveRosterLookup.humanAgentNames(backend).thenApply(humanNames -> {
            String language = ctx.getTeamSpec() == null || ctx.getTeamSpec().getLanguage() == null
                    ? "cn"
                    : ctx.getTeamSpec().getLanguage();
            String prompt = TeamPromptSections.buildTeamMemberSystemPrompt(
                    ctx.getRole(),
                    ctx.getPersona(),
                    memberName,
                    spec.getLifecycle(),
                    spec.getTeammateMode(),
                    AgentConfigurator.resolveTeamMode(spec),
                    null,
                    language,
                    sorted(humanNames),
                    spec.isExposeHumanAgentsToTeammates(),
                    bridgeNames
            );
            return prompt == null || prompt.isBlank() ? null : prompt;
        });
    }

    public static CompletionStage<SpawnManager.InProcessSpawnHandle> externalCliSpawn(
            TeamAgent teamAgent,
            TeamRuntimeContext ctx
    ) {
        return externalCliSpawn(teamAgent, ctx, null, null);
    }

    public static CompletionStage<SpawnManager.InProcessSpawnHandle> externalCliSpawn(
            TeamAgent teamAgent,
            TeamRuntimeContext ctx,
            String initialMessage,
            String sessionId
    ) {
        return externalCliSpawn(
                teamAgent,
                ctx,
                initialMessage,
                sessionId,
                DEFAULT_RUNTIME_BUILDER,
                DEFAULT_MEMBER_RUNNER,
                DEFAULT_ROSTER_LOOKUP
        );
    }

    static CompletionStage<SpawnManager.InProcessSpawnHandle> externalCliSpawn(
            TeamAgent teamAgent,
            TeamRuntimeContext ctx,
            String initialMessage,
            String sessionId,
            CliRuntimeBuilder runtimeBuilder,
            MemberRunner memberRunner,
            RosterLookup rosterLookup
    ) {
        Objects.requireNonNull(teamAgent, "teamAgent");
        Objects.requireNonNull(ctx, "ctx");
        CliRuntimeBuilder effectiveRuntimeBuilder = runtimeBuilder == null ? DEFAULT_RUNTIME_BUILDER : runtimeBuilder;
        MemberRunner effectiveMemberRunner = memberRunner == null ? DEFAULT_MEMBER_RUNNER : memberRunner;
        TeamAgentSpec spec = Objects.requireNonNull(teamAgent.getSpec(), "teamAgent.spec");
        String memberName = ctx.getMemberName();

        return buildMemberSystemPrompt(teamAgent, spec, ctx, memberName, rosterLookup).thenCompose(systemPrompt -> {
            CliAgentAdapter adapter = ctx.getCliAgent() == null || ctx.getCliAgent().isBlank()
                    ? null
                    : CliAgentAdapter.buildAdapter(ctx.getCliAgent());
            CliAgentSpawn.BuildOptions buildOptions = buildOptions(spec, ctx, systemPrompt);
            return effectiveRuntimeBuilder.build(ctx, buildOptions).thenApply(runtime -> {
                TeamAgent teammate = createTeammate(spec, ctx, memberName, runtime);
                String query = buildInitialQuery(initialMessage, systemPrompt, adapter);
                Map<String, Object> inputs = Map.of("query", query);
                CompletableFuture<Void> task = startMemberTask(
                        teammate,
                        runtime,
                        inputs,
                        sessionId,
                        effectiveMemberRunner,
                        memberName
                );
                SpawnedExternalCliHandle handle = new SpawnedExternalCliHandle(
                        "extcli-" + memberName,
                        task,
                        teammate,
                        runtime
                );
                TEAM_LOGGER.info("[external-cli] spawned member {} as {}", memberName, handle.getProcessId());
                return handle;
            });
        });
    }

    public static CompletionStage<SpawnManager.SpawnHandle> spawn(SpawnManager.SpawnRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.kind() != SpawnManager.SpawnKind.EXTERNAL_CLI) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "ExternalCliSpawn can only handle EXTERNAL_CLI spawn requests"));
        }
        if (!(request.teamAgent() instanceof TeamAgent teamAgent)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "ExternalCliSpawn requires a TeamAgent request.teamAgent"));
        }
        String sessionId = request.session() == null ? null : String.valueOf(request.session());
        return externalCliSpawn(teamAgent, request.context(), request.initialMessage(), sessionId)
                .thenApply(handle -> handle);
    }

    static String buildInitialQuery(String initialMessage, String systemPrompt, CliAgentAdapter adapter) {
        String baseQuery = initialMessage == null ? DEFAULT_JOIN_PROMPT : initialMessage;
        if (systemPrompt != null && adapter != null && !adapter.injectsSystemPromptViaArg()) {
            return systemPrompt + "\n\n---\n\n" + baseQuery;
        }
        return baseQuery;
    }

    private static CliAgentSpawn.BuildOptions buildOptions(
            TeamAgentSpec spec,
            TeamRuntimeContext ctx,
            String systemPrompt
    ) {
        LaunchConfig launchConfig = findLaunchConfig(spec, ctx.getCliAgent());
        if (launchConfig == null) {
            return new CliAgentSpawn.BuildOptions(
                    null,
                    null,
                    true,
                    DEFAULT_MCP_SERVER_NAME,
                    DEFAULT_MCP_SERVER_COMMAND,
                    systemPrompt,
                    Map.of()
            );
        }
        return new CliAgentSpawn.BuildOptions(
                launchConfig.cwd(),
                launchConfig.command(),
                launchConfig.injectMcp(),
                DEFAULT_MCP_SERVER_NAME,
                launchConfig.mcpServerCommand(),
                systemPrompt,
                launchConfig.env()
        );
    }

    private static LaunchConfig findLaunchConfig(TeamAgentSpec spec, String cliAgent) {
        if (cliAgent == null) {
            return null;
        }
        for (Object entry : spec.getExternalCliAgents()) {
            LaunchConfig config = launchConfig(entry);
            if (config != null && cliAgent.equals(config.cliAgent())) {
                return config;
            }
        }
        return null;
    }

    private static TeamAgent createTeammate(
            TeamAgentSpec spec,
            TeamRuntimeContext ctx,
            String memberName,
            MemberRuntime runtime
    ) {
        String teamName = ctx.getTeamSpec() == null || ctx.getTeamSpec().getTeamName() == null
                ? spec.getTeamName()
                : ctx.getTeamSpec().getTeamName();
        String cardId = memberName == null ? "unknown" : teamName + "_" + memberName;
        String cardName = memberName == null ? "unknown" : memberName;
        String description = ctx.getPersona() == null || ctx.getPersona().isEmpty()
                ? "External CLI member"
                : "External CLI member: " + ctx.getPersona();
        TeamAgent teammate = new TeamAgent(new AgentCard(cardId, cardName, description));
        teammate.configure(spec, ctx, runtime);
        return teammate;
    }

    private static CompletableFuture<Void> startMemberTask(
            TeamAgent teammate,
            MemberRuntime runtime,
            Map<String, Object> inputs,
            String sessionId,
            MemberRunner memberRunner,
            String memberName
    ) {
        CompletionStage<Void> runStage;
        AgentTeamsContext.SessionIdToken token = null;
        try {
            if (sessionId != null && !sessionId.isBlank()) {
                token = AgentTeamsContext.setSessionId(sessionId);
            }
            TEAM_LOGGER.info("[external-cli] member {} started", memberName);
            runStage = memberRunner.run(teammate, inputs, true, sessionId);
        } catch (Throwable throwable) {
            runStage = CompletableFuture.failedFuture(throwable);
        } finally {
            AgentTeamsContext.resetSessionId(token);
        }
        return runStage.handle((ignored, error) -> error)
                .thenCompose(error -> closeRuntime(runtime).handle((ignored, closeError) -> {
                    if (error != null) {
                        TEAM_LOGGER.error("[external-cli] member {} crashed", memberName, error);
                        throw asCompletionException(error);
                    }
                    if (closeError != null) {
                        throw asCompletionException(closeError);
                    }
                    return (Void) null;
                }))
                .toCompletableFuture();
    }

    private static CompletionStage<Void> closeRuntime(MemberRuntime runtime) {
        if (runtime == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (runtime instanceof ExternalCliRuntime externalCliRuntime) {
            return externalCliRuntime.aclose();
        }
        if (runtime instanceof ReinvokeCliRuntime reinvokeCliRuntime) {
            return reinvokeCliRuntime.aclose();
        }
        try {
            Method method = runtime.getClass().getMethod("aclose");
            Object result = method.invoke(runtime);
            if (result instanceof CompletionStage<?> stage) {
                return stage.thenApply(ignored -> null);
            }
        } catch (NoSuchMethodException ignored) {
            return runtime.abort();
        } catch (IllegalAccessException | InvocationTargetException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        return runtime.abort();
    }

    private static CompletionException asCompletionException(Throwable throwable) {
        if (throwable instanceof CompletionException completionException) {
            return completionException;
        }
        return new CompletionException(throwable);
    }

    private static List<String> sorted(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private static LaunchConfig launchConfig(Object entry) {
        if (entry == null) {
            return null;
        }
        if (entry instanceof ExternalCliAgentSpec spec) {
            return new LaunchConfig(
                    spec.getCliAgent(),
                    spec.getCommand(),
                    spec.getCwd(),
                    spec.isInjectMcp(),
                    spec.getMcpServerCommand(),
                    spec.getEnv()
            );
        }
        if (entry instanceof Map<?, ?> map) {
            return new LaunchConfig(
                    stringValue(firstMapValue(map, "cli_agent", "cliAgent")),
                    stringList(firstMapValue(map, "command")),
                    stringValue(firstMapValue(map, "cwd")),
                    booleanValue(firstMapValue(map, "inject_mcp", "injectMcp"), true),
                    defaultMcpCommand(stringList(firstMapValue(map, "mcp_server_command", "mcpServerCommand"))),
                    stringMap(firstMapValue(map, "env"))
            );
        }
        return new LaunchConfig(
                stringValue(readProperty(entry, "getCliAgent")),
                stringList(readProperty(entry, "getCommand")),
                stringValue(readProperty(entry, "getCwd")),
                booleanValue(readProperty(entry, "isInjectMcp"), true),
                defaultMcpCommand(stringList(readProperty(entry, "getMcpServerCommand"))),
                stringMap(readProperty(entry, "getEnv"))
        );
    }

    private static Object firstMapValue(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private static Object readProperty(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static List<String> stringList(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Collection<?> collection) {
            List<String> values = new ArrayList<>();
            for (Object item : collection) {
                if (item != null) {
                    values.add(String.valueOf(item));
                }
            }
            return values;
        }
        return List.of(String.valueOf(value));
    }

    private static List<String> defaultMcpCommand(List<String> value) {
        return value == null || value.isEmpty() ? DEFAULT_MCP_SERVER_COMMAND : value;
    }

    private static Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                values.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return values;
    }

    private static Collection<String> bridgeNamesFromBackend(Object backend) {
        if (backend instanceof TeamPolicyRail.TeamBackend teamBackend) {
            return teamBackend.bridgeAgentNames();
        }
        Object value = readProperty(backend, "bridgeAgentNames");
        return value instanceof Collection<?> collection ? stringList(collection) : List.of();
    }

    private static CompletionStage<List<String>> humanNamesFromBackend(Object backend) {
        if (backend instanceof TeamPolicyRail.TeamBackend teamBackend) {
            return teamBackend.humanAgentNames();
        }
        Object value = readProperty(backend, "humanAgentNames");
        if (value instanceof CompletionStage<?> stage) {
            return stage.thenApply(result -> result instanceof Collection<?> collection
                    ? stringList(collection)
                    : List.of());
        }
        return CompletableFuture.completedFuture(List.of());
    }

    /**
     * Runtime builder boundary for tests and production CLI runtime construction.
     *
     * <p>Mirrors Python's {@code build_cli_runtime} dependency in
     * {@code openjiuwen/agent_teams/spawn/external_cli_spawn.py}.</p>
     */
    @FunctionalInterface
    public interface CliRuntimeBuilder {
        CompletionStage<MemberRuntime> build(TeamRuntimeContext ctx, CliAgentSpawn.BuildOptions options);
    }

    /**
     * Member runner boundary around the Java equivalent of {@code Runner.run_agent_team}.
     *
     * <p>Mirrors Python's {@code Runner.run_agent_team(..., member=True, session=...)} call in
     * {@code openjiuwen/agent_teams/spawn/external_cli_spawn.py}.</p>
     */
    @FunctionalInterface
    public interface MemberRunner {
        CompletionStage<Void> run(
                TeamAgent teammate,
                Map<String, Object> inputs,
                boolean member,
                String sessionId
        );
    }

    /**
     * Roster lookup boundary for building external CLI team prompt sections.
     *
     * <p>Mirrors Python's backend {@code human_agent_names()} and
     * {@code bridge_agent_names()} calls in
     * {@code openjiuwen/agent_teams/spawn/external_cli_spawn.py}.</p>
     */
    public interface RosterLookup {
        Collection<String> bridgeAgentNames(AgentConfigurator.ConfiguredTeamBackend backend);

        CompletionStage<List<String>> humanAgentNames(AgentConfigurator.ConfiguredTeamBackend backend);
    }

    /**
     * Spawn handle for an external CLI-backed in-process teammate.
     *
     * <p>Mirrors Python's {@code InProcessSpawnHandle} returned by
     * {@code openjiuwen/agent_teams/spawn/external_cli_spawn.py}.</p>
     */
    public static final class SpawnedExternalCliHandle implements SpawnManager.InProcessSpawnHandle {
        private final String processId;
        private final CompletableFuture<Void> task;
        private final TeamAgent agentRef;
        private final MemberRuntime runtime;
        private Supplier<CompletionStage<Void>> onUnhealthy;
        private SpawnManager.ChunkObserver chunkForward;

        public SpawnedExternalCliHandle(
                String processId,
                CompletableFuture<Void> task,
                TeamAgent agentRef,
                MemberRuntime runtime
        ) {
            this.processId = Objects.requireNonNull(processId, "processId");
            this.task = Objects.requireNonNull(task, "task");
            this.agentRef = Objects.requireNonNull(agentRef, "agentRef");
            this.runtime = Objects.requireNonNull(runtime, "runtime");
        }

        public String getProcessId() {
            return processId;
        }

        public CompletableFuture<Void> getTask() {
            return task;
        }

        public MemberRuntime getRuntime() {
            return runtime;
        }

        public Supplier<CompletionStage<Void>> getOnUnhealthy() {
            return onUnhealthy;
        }

        @Override
        public CompletionStage<Void> stopHealthCheck() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> forceKill() {
            task.cancel(true);
            CompletionStage<Void> cancelAgent = agentRef.cancelAgent().exceptionally(error -> null);
            CompletionStage<Void> abortRuntime = runtime.abort().exceptionally(error -> null);
            CompletionStage<Void> closeRuntime = ExternalCliSpawn.closeRuntime(runtime).exceptionally(error -> null);
            return CompletableFuture.allOf(
                    cancelAgent.toCompletableFuture(),
                    abortRuntime.toCompletableFuture(),
                    closeRuntime.toCompletableFuture()
            );
        }

        @Override
        public boolean isAlive() {
            return !task.isDone();
        }

        @Override
        public void setOnUnhealthy(Supplier<CompletionStage<Void>> callback) {
            this.onUnhealthy = callback;
        }

        @Override
        public Object getAgentRef() {
            return agentRef;
        }

        @Override
        public SpawnManager.ChunkObserver getChunkForward() {
            return chunkForward;
        }

        @Override
        public void setChunkForward(SpawnManager.ChunkObserver chunkForward) {
            this.chunkForward = chunkForward;
        }
    }

    /**
     * Static external CLI launch config resolved from the team spec.
     *
     * <p>Mirrors Python's static config lookup in
     * {@code openjiuwen/agent_teams/spawn/external_cli_spawn.py}.</p>
     */
    private record LaunchConfig(
            String cliAgent,
            List<String> command,
            String cwd,
            boolean injectMcp,
            List<String> mcpServerCommand,
            Map<String, String> env
    ) {
        private LaunchConfig {
            command = command == null || command.isEmpty() ? null : List.copyOf(command);
            mcpServerCommand = defaultMcpCommand(mcpServerCommand);
            env = env == null ? Map.of() : Map.copyOf(env);
        }
    }

    /**
     * Default roster lookup used while composing the external CLI member prompt.
     *
     * <p>Mirrors Python's backend roster calls in
     * {@code openjiuwen/agent_teams/spawn/external_cli_spawn.py}.</p>
     */
    private static final class ReflectiveRosterLookup implements RosterLookup {
        @Override
        public Collection<String> bridgeAgentNames(AgentConfigurator.ConfiguredTeamBackend backend) {
            return bridgeNamesFromBackend(backend);
        }

        @Override
        public CompletionStage<List<String>> humanAgentNames(AgentConfigurator.ConfiguredTeamBackend backend) {
            return humanNamesFromBackend(backend);
        }
    }
}
