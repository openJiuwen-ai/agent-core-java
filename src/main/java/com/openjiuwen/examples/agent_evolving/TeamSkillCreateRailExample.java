/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.agent_evolving;

import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.spawn.SpawnContext;
import com.openjiuwen.agent_teams.tools.AgentTeamsToolRegistry;
import com.openjiuwen.agent_teams.tools.TeamBackend;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.HarnessFactory;
import com.openjiuwen.harness.rails.skills.TeamSkillCreateRail;
import com.openjiuwen.harness.workspace.Workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Real TeamSkillCreateRail example with DeepAgent.
 *
 * <p>Mirrors Python's {@code examples.agent_evolving.team_skill_create_rail_example}.</p>
 */
public final class TeamSkillCreateRailExample {

    public static final String DEFAULT_QUERY = String.join("",
            "Please act as the team leader for an AI industry weekly report. ",
            "You must call build_team, spawn_member at least twice for researcher and writer, ",
            "create tasks for both members, and then summarize the team structure and responsibilities. ",
            "Do not only provide a verbal plan; actually call the tools.");

    private TeamSkillCreateRailExample() {
    }

    public static void configureExampleLogging() {
        // Java logging is configured by the host application.
    }

    public static Map<String, String> loadEnvIfPresent() {
        Map<String, String> loaded = new LinkedHashMap<>();
        List<Path> candidates = List.of(
                Path.of(".env").toAbsolutePath().normalize(),
                Path.of("..", ".env").toAbsolutePath().normalize()
        );
        for (Path envFile : candidates) {
            if (Files.exists(envFile)) {
                loaded.putAll(readEnvFile(envFile));
            }
        }
        return loaded;
    }

    public static ModelSettings buildModelFromEnv() {
        Map<String, String> envFileValues = loadEnvIfPresent();
        String apiKey = envOrLoaded("API_KEY", "", envFileValues);
        String apiBase = envOrLoaded("API_BASE", "", envFileValues);
        String modelName = envOrLoaded("MODEL_NAME", "", envFileValues);
        String provider = envOrLoaded("MODEL_PROVIDER", "OpenAI", envFileValues);
        int timeout = Integer.parseInt(envOrLoaded("MODEL_TIMEOUT", "120", envFileValues));

        List<String> missing = new ArrayList<>();
        if (apiKey.isBlank()) {
            missing.add("API_KEY");
        }
        if (apiBase.isBlank()) {
            missing.add("API_BASE");
        }
        if (modelName.isBlank()) {
            missing.add("MODEL_NAME");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing required environment variables: "
                    + String.join(", ", missing) + ".");
        }

        ModelClientConfig modelClientConfig = ModelClientConfig.builder()
                .clientProvider(provider)
                .apiKey(apiKey)
                .apiBase(apiBase)
                .timeout(timeout)
                .verifySsl(false)
                .build();
        ModelRequestConfig modelRequestConfig = ModelRequestConfig.builder()
                .modelName(modelName)
                .temperature(0.2)
                .topP(0.9)
                .build();
        return new ModelSettings(new Model(modelClientConfig, modelRequestConfig), modelClientConfig, modelRequestConfig);
    }

    public static Path prepareWorkspace(String workspace) throws IOException {
        Path root = workspace != null && !workspace.isBlank()
                ? Path.of(workspace).toAbsolutePath().normalize()
                : Files.createTempDirectory("team_skill_example_create_").toAbsolutePath().normalize();
        Files.createDirectories(root);
        Files.createDirectories(root.resolve("skills"));
        return root;
    }

    public static String buildSessionId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    public static LeaderTeamContext leaderTeamToolsContext(
            Path workspace,
            String sessionId,
            String teamName,
            String memberName,
            String lang
    ) {
        String effectiveMemberName = memberName != null && !memberName.isBlank() ? memberName : "leader";
        SpawnContext.setSessionId(sessionId);
        TeamBackend backend = new TeamBackend(
                teamName,
                effectiveMemberName,
                true,
                MemberMode.BUILD_MODE,
                List.of()
        );
        List<Tool> tools = AgentTeamsToolRegistry.createTeamTools(backend, TeamRole.LEADER, "build_mode");
        return new LeaderTeamContext(workspace, backend, tools, sessionId, lang != null ? lang : "cn");
    }

    public static ParsedArgs parseArgs(String[] args) {
        String workspace = null;
        String query = DEFAULT_QUERY;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--workspace".equals(arg) && i + 1 < args.length) {
                workspace = args[++i];
            } else if ("--query".equals(arg) && i + 1 < args.length) {
                query = args[++i];
            }
        }
        return new ParsedArgs(workspace, query);
    }

    public static void main(String[] args) throws Exception {
        ParsedArgs parsedArgs = parseArgs(args);
        configureExampleLogging();

        ModelSettings modelSettings = buildModelFromEnv();
        Path workspace = prepareWorkspace(parsedArgs.workspace());
        String sessionId = buildSessionId("team_skill_create");
        String teamName = "team_skill_create_demo_" + sessionId.substring(sessionId.lastIndexOf('_') + 1);

        Runner.start();
        try (LeaderTeamContext context = leaderTeamToolsContext(workspace, sessionId, teamName, "leader", "cn")) {
            TeamSkillCreateRail createRail = new TeamSkillCreateRail();
            DeepAgentConfig config = new DeepAgentConfig();
            config.setCard(AgentCard.builder()
                    .id("team_skill_create_example")
                    .name("Team Skill Create Example")
                    .description("DeepAgent example for team skill creation")
                    .build());
            config.setModelClientConfig(modelSettings.modelClientConfig());
            config.setModelRequestConfig(modelSettings.modelRequestConfig());
            config.setSystemPrompt("You are a strict team leader. When the user asks for team collaboration, "
                    + "use team tools to create a real team and tasks.");
            config.setTools(context.toolCards());
            config.setRails(List.<AgentRail>of(createRail));
            config.setMaxIterations(6);
            config.setWorkspace(new Workspace(workspace.toString(), context.lang()));

            DeepAgent agent = HarnessFactory.createDeepAgent(config);
            AgentSessionApi session = AgentSessionApi.create(sessionId, Map.of("team_name", teamName), agent.getCard());
            Object result = Runner.runAgent(agent, Map.of("query", parsedArgs.query()), session, null);

            System.out.println("workspace: " + workspace);
            System.out.println("team name: " + teamName);
            System.out.println("team skill create rail configured: true");
            System.out.println("final output: " + normalizeOutput(result));
        } finally {
            Runner.stop();
        }
    }

    private static Map<String, String> readEnvFile(Path envFile) {
        Map<String, String> values = new LinkedHashMap<>();
        try {
            for (String rawLine : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }
                int split = line.indexOf('=');
                String key = line.substring(0, split).trim();
                String value = stripQuotes(line.substring(split + 1).trim());
                values.putIfAbsent(key, value);
            }
        } catch (IOException ignored) {
            // Best effort, matching Python's optional .env loading.
        }
        return values;
    }

    private static String envOrLoaded(String key, String defaultValue, Map<String, String> loaded) {
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return loaded.getOrDefault(key, defaultValue);
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static Object normalizeOutput(Object result) {
        if (result instanceof Map<?, ?> map && map.containsKey("output")) {
            return map.get("output");
        }
        return result;
    }

    public static final class LeaderTeamContext implements AutoCloseable {
        private final Path workspace;
        private final TeamBackend backend;
        private final List<Tool> tools;
        private final String sessionId;
        private final String lang;

        private LeaderTeamContext(Path workspace, TeamBackend backend, List<Tool> tools, String sessionId, String lang) {
            this.workspace = workspace;
            this.backend = backend;
            this.tools = List.copyOf(tools);
            this.sessionId = sessionId;
            this.lang = lang;
        }

        public Path workspace() {
            return workspace;
        }

        public TeamBackend backend() {
            return backend;
        }

        public List<Tool> tools() {
            return tools;
        }

        public List<ToolCard> toolCards() {
            return tools.stream().map(Tool::getCard).toList();
        }

        public String sessionId() {
            return sessionId;
        }

        public String lang() {
            return lang;
        }

        @Override
        public void close() {
            backend.getMessager().stop();
            SpawnContext.resetSessionId();
        }
    }

    public static final class ModelSettings {
        private final Model model;
        private final ModelClientConfig modelClientConfig;
        private final ModelRequestConfig modelRequestConfig;

        private ModelSettings(Model model, ModelClientConfig modelClientConfig, ModelRequestConfig modelRequestConfig) {
            this.model = model;
            this.modelClientConfig = modelClientConfig;
            this.modelRequestConfig = modelRequestConfig;
        }

        public Model model() {
            return model;
        }

        public ModelClientConfig modelClientConfig() {
            return modelClientConfig;
        }

        public ModelRequestConfig modelRequestConfig() {
            return modelRequestConfig;
        }
    }

    public static final class ParsedArgs {
        private final String workspace;
        private final String query;

        private ParsedArgs(String workspace, String query) {
            this.workspace = workspace;
            this.query = query;
        }

        public String workspace() {
            return workspace;
        }

        public String query() {
            return query;
        }
    }
}
