/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.agent_teams;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.core.common.logging.defaults.LoggingDefaults;
import com.openjiuwen.core.runner.Runner;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent Team E2E interactive CLI example.
 *
 * <p>Mirrors Python's {@code examples.agent_teams.agent_team_e2e}.</p>
 */
public final class AgentTeamE2e {
    public static final String DEFAULT_SESSION_ID = "agent_team_session";
    public static final String LOG_CONFIG_FILE = "logging.yaml";
    public static final String TEAM_CONFIG_FILE = "config.yaml";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private AgentTeamE2e() {
    }

    public static Path exampleDir() {
        String override = System.getProperty("openjiuwen.agent_teams.example_dir");
        if (override == null || override.isBlank()) {
            override = System.getenv("OPENJIUWEN_AGENT_TEAMS_EXAMPLE_DIR");
        }
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        return Path.of("examples", "agent_teams").toAbsolutePath().normalize();
    }

    public static Path logConfigPath(Path exampleDir) {
        return exampleDir.resolve(LOG_CONFIG_FILE);
    }

    public static Path teamConfigPath(Path exampleDir) {
        return exampleDir.resolve(TEAM_CONFIG_FILE);
    }

    public static void configureExampleLogging(Path logConfigPath) {
        if (logConfigPath != null && Files.isRegularFile(logConfigPath)) {
            LoggingDefaults.configureLog(logConfigPath.toString());
        } else {
            LoggingDefaults.reset();
        }
    }

    public static void applyDefaultEnvironment() {
        setDefaultProperty("LLM_SSL_VERIFY", "false");
        setDefaultProperty("IS_SENSITIVE", "false");
    }

    public static LoadedConfig loadConfig(Path teamConfigPath) throws Exception {
        Map<String, Object> cfg = new LinkedHashMap<>(E2eUtils.loadTeamConfig(teamConfigPath));
        Object runtime = cfg.remove("runtime");
        Map<String, Object> runtimeConfig = runtime instanceof Map<?, ?> map ? toStringKeyMap(map) : new LinkedHashMap<>();
        return new LoadedConfig(cfg, runtimeConfig);
    }

    public static TeamAgentSpec buildSpec(Map<String, Object> cfg) {
        return MAPPER.convertValue(cfg != null ? cfg : Map.of(), TeamAgentSpec.class);
    }

    public static List<String> bannerLines() {
        return List.of(
                "=".repeat(60),
                "Agent Team E2E - Interactive CLI",
                "Type your message and press Enter to interact with the leader.",
                "Type 'exit' or 'quit' to stop.",
                "=".repeat(60)
        );
    }

    public static void printBanner(PrintStream out) {
        for (String line : bannerLines()) {
            out.println(line);
        }
    }

    public static void main(String[] args) throws Exception {
        Path dir = args != null && args.length > 0 && args[0] != null && !args[0].isBlank()
                ? Path.of(args[0]).toAbsolutePath().normalize()
                : exampleDir();
        configureExampleLogging(logConfigPath(dir));
        applyDefaultEnvironment();

        LoadedConfig loaded = loadConfig(teamConfigPath(dir));
        TeamAgentSpec spec = buildSpec(loaded.teamConfig());
        TeamAgent leader = spec.build();

        Runner.start();
        try {
            printBanner(System.out);
            E2eUtils.runInteractive(leader, loaded.runtimeConfig(), DEFAULT_SESSION_ID, "hello");
        } finally {
            Runner.stop();
            System.out.println("Done.");
        }
    }

    private static void setDefaultProperty(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }

    private static Map<String, Object> toStringKeyMap(Map<?, ?> map) {
        Map<String, Object> converted = new LinkedHashMap<>();
        map.forEach((key, value) -> converted.put(String.valueOf(key), value));
        return converted;
    }

    public record LoadedConfig(Map<String, Object> teamConfig, Map<String, Object> runtimeConfig) {
        public LoadedConfig {
            teamConfig = teamConfig != null ? new LinkedHashMap<>(teamConfig) : new LinkedHashMap<>();
            runtimeConfig = runtimeConfig != null ? new LinkedHashMap<>(runtimeConfig) : new LinkedHashMap<>();
        }
    }
}
