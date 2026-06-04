/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.agent_teams;

import com.openjiuwen.agent_teams.agent.ModelPoolEntry;
import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.core.runner.Runner;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Agent Team model-pool E2E interactive CLI example.
 *
 * <p>Mirrors Python's {@code examples.agent_teams.agent_team_model_pool_e2e}.</p>
 */
public final class AgentTeamModelPoolE2e {
    public static final String DEFAULT_SESSION_ID = "model_pool_session";
    public static final String TEAM_CONFIG_FILE = "config_model_pool.yaml";

    private AgentTeamModelPoolE2e() {
    }

    public static Path teamConfigPath(Path exampleDir) {
        return exampleDir.resolve(TEAM_CONFIG_FILE);
    }

    public static void printIntro(TeamAgentSpec spec, PrintStream out) {
        out.println("=".repeat(70));
        out.println("Agent Team Model Pool E2E");
        out.println("=".repeat(70));
        out.println();
        out.println("  team_name   : " + spec.getTeamName());
        out.println("  agent roles : " + spec.getAgents().keySet() + "  (teammates spawned dynamically)");
        out.println();
        out.println("Model pool:");
        printPoolSummary(spec, out);
    }

    public static void printPoolSummary(TeamAgentSpec spec, PrintStream out) {
        List<ModelPoolEntry> pool = spec.getModelPool();
        if (pool.isEmpty()) {
            out.println("  (no model pool configured - members use per-agent model)");
            return;
        }

        out.println("  strategy : " + spec.getModelPoolStrategy());
        out.println("  entries  : " + pool.size());
        out.println();
        out.printf("  %-3s  %-20s  %-14s  %-40s  %s%n", "#", "model_name", "provider", "api_base_url", "description");
        out.printf("  %-3s  %-20s  %-14s  %-40s  %-20s%n",
                "-".repeat(3), "-".repeat(20), "-".repeat(14), "-".repeat(40), "-".repeat(20));
        for (int index = 0; index < pool.size(); index++) {
            printPoolEntry(index, pool.get(index), out);
        }
        out.println();
    }

    public static void printPoolEntry(int index, ModelPoolEntry entry, PrintStream out) {
        String apiKey = entry.getApiKey() != null ? entry.getApiKey() : "";
        String apiKeyHint = apiKey.length() > 8 ? apiKey.substring(0, 8) + "..." : apiKey;
        out.printf("  %-3d  %-20s  %-14s  %-40s  %s%n",
                index,
                value(entry.getModelName()),
                value(entry.getApiProvider()),
                value(entry.getApiBaseUrl()),
                value(entry.getDescription()));
        out.println("       api_key: " + apiKeyHint);
    }

    public static List<String> interactiveBannerLines() {
        return List.of(
                "=".repeat(70),
                "Interactive CLI - type your message and press Enter.",
                "Type 'exit' or 'quit' to stop.",
                "=".repeat(70)
        );
    }

    public static void printInteractiveBanner(PrintStream out) {
        for (String line : interactiveBannerLines()) {
            out.println(line);
        }
    }

    public static void main(String[] args) throws Exception {
        Path dir = args != null && args.length > 0 && args[0] != null && !args[0].isBlank()
                ? Path.of(args[0]).toAbsolutePath().normalize()
                : AgentTeamE2e.exampleDir();
        AgentTeamE2e.configureExampleLogging(AgentTeamE2e.logConfigPath(dir));
        AgentTeamE2e.applyDefaultEnvironment();

        AgentTeamE2e.LoadedConfig loaded = AgentTeamE2e.loadConfig(teamConfigPath(dir));
        TeamAgentSpec spec = AgentTeamE2e.buildSpec(loaded.teamConfig());
        printIntro(spec, System.out);

        TeamAgent leader = spec.build();
        Runner.start();
        try {
            printInteractiveBanner(System.out);
            E2eUtils.runInteractive(leader, loaded.runtimeConfig(), DEFAULT_SESSION_ID, "hello");
        } finally {
            Runner.stop();
            System.out.println("Done.");
        }
    }

    private static String value(String value) {
        return value != null ? value : "";
    }
}
