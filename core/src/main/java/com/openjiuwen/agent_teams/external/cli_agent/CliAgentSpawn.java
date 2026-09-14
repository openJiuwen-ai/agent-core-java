/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.external.cli_agent;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.MemberRuntime;
import com.openjiuwen.agent_teams.external.ExternalCliRuntime;
import com.openjiuwen.agent_teams.external.ReinvokeCliRuntime;
import com.openjiuwen.agent_teams.external.TeamJoinDescriptor;
import com.openjiuwen.agent_teams.messager.MessagerTransportConfig;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Builds external CLI member runtimes from a team runtime context.
 *
 * <p>Mirrors Python's {@code descriptor_from_context},
 * {@code _register_mcp_out_of_band}, and {@code build_cli_runtime} in
 * {@code openjiuwen/agent_teams/external/cli_agent/spawn.py}.</p>
 */
public final class CliAgentSpawn {

    public static final String TEAM_JOIN_ENV = TeamJoinDescriptor.TEAM_JOIN_ENV;

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;
    private static final double DEFAULT_INACTIVITY_TIMEOUT_SECONDS = 180.0d;

    private CliAgentSpawn() {
    }

    public static TeamJoinDescriptor descriptorFromContext(TeamRuntimeContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        if (ctx.getMemberName() == null || ctx.getMemberName().isBlank()) {
            ErrorHelper.raiseError(
                    StatusCode.AGENT_TEAM_CONFIG_INVALID,
                    null,
                    null,
                    null,
                    Map.of("reason", "external CLI member requires a member_name in its runtime context")
            );
        }
        String teamName = ctx.getTeamSpec() == null ? "" : nullToEmpty(ctx.getTeamSpec().getTeamName());
        String language = ctx.getTeamSpec() == null || ctx.getTeamSpec().getLanguage() == null
                ? "cn"
                : ctx.getTeamSpec().getLanguage();
        MessagerTransportConfig transport = ctx.getMessagerConfig() == null
                ? new MessagerTransportConfig()
                : copyTransport(ctx.getMessagerConfig());
        if (transport.getDirectAddr() != null && !transport.getDirectAddr().isBlank()) {
            transport.setDirectAddr("tcp://127.0.0.1:*");
        }
        return new TeamJoinDescriptor(
                nullToEmpty(AgentTeamsContext.getSessionId()),
                teamName,
                nullToEmpty(ctx.getMemberName()),
                ctx.getRole() == null ? "teammate" : ctx.getRole().value(),
                language,
                ctx.getDbConfig(),
                transport
        );
    }

    public static CompletionStage<MemberRuntime> buildCliRuntime(TeamRuntimeContext ctx) {
        return buildCliRuntime(ctx, BuildOptions.defaults());
    }

    public static CompletionStage<MemberRuntime> buildCliRuntime(
            TeamRuntimeContext ctx,
            BuildOptions options
    ) {
        Objects.requireNonNull(ctx, "ctx");
        BuildOptions resolvedOptions = options == null ? BuildOptions.defaults() : options;
        if (ctx.getCliAgent() == null || ctx.getCliAgent().isBlank()) {
            ErrorHelper.raiseError(
                    StatusCode.AGENT_TEAM_CONFIG_INVALID,
                    null,
                    null,
                    null,
                    Map.of("reason", "build_cli_runtime called without ctx.cli_agent set")
            );
        }

        CliAgentAdapter adapter = CliAgentAdapter.buildAdapter(ctx.getCliAgent(), resolvedOptions.commandOverride());
        TeamJoinDescriptor descriptor = descriptorFromContext(ctx);
        Map<String, String> env = buildEnvironment(adapter, descriptor, resolvedOptions.extraEnv());

        List<String> systemPromptArgs = adapter.systemPromptArgs(nullToEmpty(resolvedOptions.systemPrompt()));
        List<String> mcpArgs = List.of();
        CompletionStage<Void> registration = CompletableFuture.completedFuture(null);
        if (resolvedOptions.injectMcp()) {
            mcpArgs = adapter.mcpLaunchArgs(
                    resolvedOptions.mcpServerName(),
                    resolvedOptions.mcpServerCommand()
            );
            if (mcpArgs.isEmpty()) {
                registration = registerMcpOutOfBand(
                        adapter,
                        resolvedOptions.mcpServerName(),
                        resolvedOptions.mcpServerCommand(),
                        env,
                        resolvedOptions.cwd(),
                        nullToEmpty(ctx.getMemberName())
                );
            }
        }
        List<String> launchExtraArgs = new ArrayList<>(mcpArgs);
        launchExtraArgs.addAll(systemPromptArgs);

        if (!adapter.supportsStdinInjection()) {
            List<String> finalLaunchExtraArgs = List.copyOf(launchExtraArgs);
            return registration.thenApply(ignored -> new ReinvokeCliRuntime(
                    nullToEmpty(ctx.getMemberName()),
                    adapter,
                    env,
                    resolvedOptions.cwd(),
                    UUID.randomUUID().toString(),
                    finalLaunchExtraArgs,
                    DEFAULT_INACTIVITY_TIMEOUT_SECONDS,
                    null
            ));
        }

        return registration.thenApply(ignored -> launchStreamingRuntime(
                ctx,
                adapter,
                env,
                resolvedOptions.cwd(),
                launchExtraArgs
        ));
    }

    static Map<String, String> buildEnvironment(
            CliAgentAdapter adapter,
            TeamJoinDescriptor descriptor,
            Map<String, String> extraEnv
    ) {
        Map<String, String> env = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            if (startsWithAny(entry.getKey(), adapter.envStripPrefixes())) {
                continue;
            }
            env.put(entry.getKey(), entry.getValue());
        }
        if (extraEnv != null) {
            env.putAll(extraEnv);
        }
        env.putAll(descriptor.toEnv());
        return env;
    }

    private static CompletionStage<Void> registerMcpOutOfBand(
            CliAgentAdapter adapter,
            String serverName,
            List<String> serverCommand,
            Map<String, String> env,
            String cwd,
            String memberName
    ) {
        List<String> registerCommand = adapter.mcpRegisterCommand(serverName, serverCommand);
        if (registerCommand == null) {
            TEAM_LOGGER.warning(
                    "[external-cli] {} cannot auto-inject the team MCP server (no launch flag or registration command); "
                            + "member {} will lack team tools unless registered out of band",
                    adapter.name(),
                    memberName
            );
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            TEAM_LOGGER.info("[external-cli] registering team MCP for member {} via {}", memberName, registerCommand);
            ProcessBuilder builder = new ProcessBuilder(registerCommand);
            builder.environment().putAll(env);
            if (cwd != null && !cwd.isBlank()) {
                builder.directory(new File(cwd));
            }
            try {
                Process process = builder.start();
                String stderr = readAll(process.getErrorStream());
                int returnCode = process.waitFor();
                if (returnCode != 0) {
                    TEAM_LOGGER.warning(
                            "[external-cli] team MCP registration for {} exited {}: {}",
                            adapter.name(),
                            returnCode,
                            tail(stderr, 500)
                    );
                }
            } catch (IOException | InterruptedException | RuntimeException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                TEAM_LOGGER.warning(
                        "[external-cli] team MCP registration for {} failed to launch: {}",
                        adapter.name(),
                        exception.getMessage()
                );
            }
        });
    }

    private static MemberRuntime launchStreamingRuntime(
            TeamRuntimeContext ctx,
            CliAgentAdapter adapter,
            Map<String, String> env,
            String cwd,
            List<String> launchExtraArgs
    ) {
        List<String> command = adapter.buildCommand(launchExtraArgs);
        TEAM_LOGGER.info("[external-cli] launching {} for member {}", command, ctx.getMemberName());
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectInput(ProcessBuilder.Redirect.PIPE);
        builder.redirectOutput(ProcessBuilder.Redirect.PIPE);
        builder.redirectError(ProcessBuilder.Redirect.PIPE);
        builder.environment().putAll(env);
        if (cwd != null && !cwd.isBlank()) {
            builder.directory(new File(cwd));
        }
        try {
            Process process = builder.start();
            return new ExternalCliRuntime(
                    nullToEmpty(ctx.getMemberName()),
                    adapter,
                    new StdinPipeInjector(process.getOutputStream()),
                    new StdoutLineIterator(process.getInputStream()),
                    process
            );
        } catch (IOException exception) {
            ErrorHelper.raiseError(
                    StatusCode.AGENT_TEAM_EXECUTION_ERROR,
                    null,
                    null,
                    exception,
                    Map.of("error_msg", "failed to start external CLI member '"
                            + nullToEmpty(ctx.getMemberName()) + "': " + exception.getMessage())
            );
            throw new IllegalStateException("unreachable");
        }
    }

    private static MessagerTransportConfig copyTransport(MessagerTransportConfig source) {
        MessagerTransportConfig copy = new MessagerTransportConfig();
        copy.setBackend(source.getBackend());
        copy.setTeamName(source.getTeamName());
        copy.setNodeId(source.getNodeId());
        copy.setDirectAddr(source.getDirectAddr());
        copy.setPubsubPublishAddr(source.getPubsubPublishAddr());
        copy.setPubsubSubscribeAddr(source.getPubsubSubscribeAddr());
        copy.setListenAddrs(source.getListenAddrs());
        copy.setBootstrapPeers(source.getBootstrapPeers());
        copy.setKnownPeers(source.getKnownPeers());
        copy.setRequestTimeout(source.getRequestTimeout());
        copy.setMetadata(source.getMetadata());
        return copy;
    }

    private static boolean startsWithAny(String value, List<String> prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String readAll(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String tail(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(value.length() - maxLength);
    }

    /**
     * Options controlling external CLI runtime construction.
     *
     * <p>Mirrors Python's keyword arguments in
     * {@code openjiuwen/agent_teams/external/cli_agent/spawn.py}.</p>
     */
    public record BuildOptions(
            String cwd,
            List<String> commandOverride,
            boolean injectMcp,
            String mcpServerName,
            List<String> mcpServerCommand,
            String systemPrompt,
            Map<String, String> extraEnv
    ) {
        public BuildOptions {
            commandOverride = commandOverride == null ? null : List.copyOf(commandOverride);
            mcpServerName = mcpServerName == null || mcpServerName.isBlank() ? "openjiuwen-team" : mcpServerName;
            mcpServerCommand = mcpServerCommand == null || mcpServerCommand.isEmpty()
                    ? List.of("openjiuwen-team-mcp")
                    : List.copyOf(mcpServerCommand);
            extraEnv = extraEnv == null ? Map.of() : Map.copyOf(extraEnv);
        }

        public static BuildOptions defaults() {
            return new BuildOptions(
                    null,
                    null,
                    true,
                    "openjiuwen-team",
                    List.of("openjiuwen-team-mcp"),
                    null,
                    Map.of()
            );
        }
    }

    /**
     * Iterator over decoded stdout lines from a launched CLI process.
     *
     * <p>Mirrors Python's {@code _aiter_stdout} helper in
     * {@code openjiuwen/agent_teams/external/cli_agent/spawn.py}.</p>
     */
    private static final class StdoutLineIterator implements java.util.Iterator<String> {
        private final BufferedReader reader;
        private String next;
        private boolean loaded;
        private boolean finished;

        private StdoutLineIterator(InputStream inputStream) {
            this.reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        }

        @Override
        public boolean hasNext() {
            if (finished) {
                return false;
            }
            if (loaded) {
                return next != null;
            }
            try {
                next = reader.readLine();
                loaded = true;
                if (next == null) {
                    finished = true;
                    reader.close();
                }
                return next != null;
            } catch (IOException exception) {
                finished = true;
                throw new IllegalStateException("failed to read external CLI stdout", exception);
            }
        }

        @Override
        public String next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            String current = next;
            next = null;
            loaded = false;
            return current;
        }
    }
}
