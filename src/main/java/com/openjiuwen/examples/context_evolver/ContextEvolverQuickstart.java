/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.context_evolver;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.extensions.context_evolver.ContextEvolvingReActAgent;
import com.openjiuwen.extensions.context_evolver.core.config.Config;
import com.openjiuwen.extensions.context_evolver.service.TaskMemoryService;
import com.openjiuwen.extensions.context_evolver.tool.WikipediaTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ContextEvolvingReActAgent HotpotQA quickstart example.
 *
 * <p>Mirrors Python's {@code examples.context_evolver.quickstart}.</p>
 */
public final class ContextEvolverQuickstart {
    public static final String DIVIDER = "=".repeat(60);
    public static final String SUBDIV = "-".repeat(50);
    public static final String DEFAULT_API_KEY = "your_api_key_here";
    public static final String DEFAULT_QUESTION =
            "Which magazine was started first Arthur's Magazine or First for Women?";

    private static final Logger LOGGER = LoggerFactory.getLogger(ContextEvolverQuickstart.class);

    private ContextEvolverQuickstart() {
    }

    public static Map<String, Object> defaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("API_KEY", DEFAULT_API_KEY);
        defaults.put("API_BASE", "https://api.openai.com/v1");
        defaults.put("MODEL_NAME", "gpt-5.2");
        defaults.put("MODEL_PROVIDER", "OpenAI");
        defaults.put("EMBEDDING_MODEL", "text-embedding-3-small");
        defaults.put("EMBEDDING_DIMENSIONS", 2560);
        defaults.put("LLM_TEMPERATURE", 0.7d);
        defaults.put("LLM_SEED", 42);
        defaults.put("LLM_SSL_VERIFY", false);
        return defaults;
    }

    public static void applyDefaults() {
        defaults().forEach(Config::setValue);
    }

    public static void configureAlgorithmsForRefcon() {
        Config.setValue("RETRIEVAL_ALGO", "REFCON");
        Config.setValue("SUMMARY_ALGO", "REFCON");
        Config.setValue("MANAGEMENT_ALGO", "REFCON");
    }

    public static boolean hasConfiguredApiKey() {
        String apiKey = String.valueOf(Config.get("API_KEY", ""));
        return !apiKey.isBlank() && !DEFAULT_API_KEY.equals(apiKey);
    }

    public static AgentCard buildHotpotCard() {
        return AgentCard.builder()
                .id("demo-agent-refcon")
                .name("Demo Agent REFCON")
                .description("Agent for HotpotQA quickstart demo using REFCON sequential")
                .build();
    }

    public static ContextEvolvingReActAgent buildAgent(TaskMemoryService memoryService, String memoryDir) {
        return new ContextEvolvingReActAgent(
                buildHotpotCard(),
                "demo_user_hotpot_refcon",
                memoryService,
                true,
                memoryDir
        );
    }

    public static ReActAgentConfig buildAgentConfig() {
        String apiKey = String.valueOf(Config.get("API_KEY", ""));
        String apiBase = String.valueOf(Config.get("API_BASE", "https://api.openai.com/v1"));
        String modelName = String.valueOf(Config.get("MODEL_NAME", "gpt-5.2"));
        String modelProvider = String.valueOf(Config.get("MODEL_PROVIDER", "OpenAI"));
        boolean verifySsl = Boolean.parseBoolean(String.valueOf(Config.get("LLM_SSL_VERIFY", false)));

        return new ReActAgentConfig()
                .configureModelClient(modelProvider, apiKey, apiBase, modelName, verifySsl)
                .configureMaxIterations(5);
    }

    public static void configureAgent(ContextEvolvingReActAgent agent, Tool wikipediaTool) {
        agent.configure(buildAgentConfig());
        agent.addTool(wikipediaTool != null ? wikipediaTool : WikipediaTool.createWikipediaTool());
    }

    public static List<String> bannerLines(String title) {
        return List.of("", DIVIDER, title, DIVIDER);
    }

    public static List<String> sectionLines(String label) {
        return List.of("", "  " + label, "  " + SUBDIV);
    }

    public static Map<String, Object> invocationInput(String question) {
        return new LinkedHashMap<>(Map.of("query", question));
    }

    public static List<String> summarizeResult(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return List.of("  No summary result returned.");
        }
        return result.entrySet().stream()
                .map(entry -> "    " + padRight(entry.getKey(), 20) + ": " + truncate(String.valueOf(entry.getValue()), 120))
                .toList();
    }

    public static void main(String[] args) {
        applyDefaults();
        banner("ContextEvolvingReActAgent - HotpotQA RefCon Demo");
        section("[Step 0] Checking configuration");

        if (!hasConfiguredApiKey()) {
            LOGGER.error("API key not configured. Edit API_KEY in defaults");
            return;
        }

        LOGGER.info("  API Base : {}", Config.get("API_BASE", "https://api.openai.com/v1"));
        LOGGER.info("  Model    : {}", Config.get("MODEL_NAME", "gpt-5.2"));
        LOGGER.info("  Provider : {}", Config.get("MODEL_PROVIDER", "OpenAI"));

        banner("TRAJECTORIES GENERATION - HotpotQA (REFCON Sequential)");
        LOGGER.info("  Using Algorithm: REFCON");
        configureAlgorithmsForRefcon();

        ContextEvolvingReActAgent agent = buildAgent(new TaskMemoryService(), "memory_files");
        configureAgent(agent, WikipediaTool.createWikipediaTool());

        section("[Invoke] " + DEFAULT_QUESTION);
        Object raw = agent.invoke(invocationInput(DEFAULT_QUESTION), null);
        if (raw instanceof Map<?, ?> map) {
            LOGGER.info("  Summary result :");
            summarizeResult(toStringKeyMap(map)).forEach(LOGGER::info);
        } else if (raw != null) {
            LOGGER.info("  Summary result : {}", raw);
        } else {
            LOGGER.info("  No summary result returned.");
        }

        banner("Demo Complete!");
    }

    private static void banner(String title) {
        bannerLines(title).forEach(LOGGER::info);
    }

    private static void section(String label) {
        sectionLines(label).forEach(LOGGER::info);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "null";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static String padRight(String value, int width) {
        String text = value != null ? value : "";
        return text.length() >= width ? text : text + " ".repeat(width - text.length());
    }

    private static Map<String, Object> toStringKeyMap(Map<?, ?> map) {
        Map<String, Object> converted = new LinkedHashMap<>();
        map.forEach((key, value) -> converted.put(String.valueOf(key), value));
        return converted;
    }
}
