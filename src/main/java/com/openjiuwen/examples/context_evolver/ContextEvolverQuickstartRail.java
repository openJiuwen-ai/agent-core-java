/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.context_evolver;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.extensions.context_evolver.core.config.Config;
import com.openjiuwen.extensions.context_evolver.service.AddMemoryRequest;
import com.openjiuwen.extensions.context_evolver.service.TaskMemoryService;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.HarnessFactory;
import com.openjiuwen.harness.rails.evolution.ContextEvolutionRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ContextEvolutionRail quickstart example.
 *
 * <p>Mirrors Python's {@code examples.context_evolver.quickstart_rail}.</p>
 */
public final class ContextEvolverQuickstartRail {

    public static final String DIVIDER = "=".repeat(60);
    public static final String SUBDIV = "-".repeat(50);
    public static final String DEFAULT_API_KEY = "your_api_key_here";
    public static final String DEFAULT_USER_ID = "demo_user";
    public static final String SYSTEM_PROMPT = "You are a helpful assistant with access to a memory system. "
            + "When relevant memories are provided in your context, use them to inform your responses. "
            + "Always provide accurate, helpful answers based on both your knowledge and any retrieved memories.";
    public static final String DEBUGGING_MEMORY = "When debugging Python code, prefer the built-in debugger pdb over "
            + "print statements. Use pdb.set_trace() to pause execution and inspect variables interactively. "
            + "For async code use asyncio debug mode (PYTHONASYNCIODEBUG=1). Always check the full traceback before "
            + "adding any print statements.";
    public static final String TESTING_MEMORY = "When writing unit tests in Python, prefer pytest over unittest. "
            + "Use fixtures for reusable setup and teardown. Mock external dependencies with unittest.mock.patch "
            + "to keep tests fast and isolated. Run tests with pytest -v for verbose output and pytest --tb=short "
            + "to see concise tracebacks on failure.";

    private static final Logger LOGGER = LoggerFactory.getLogger(ContextEvolverQuickstartRail.class);

    private ContextEvolverQuickstartRail() {
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

    public static boolean hasConfiguredApiKey() {
        String apiKey = String.valueOf(Config.get("API_KEY", ""));
        return !apiKey.isBlank() && !DEFAULT_API_KEY.equals(apiKey);
    }

    public static ModelClientConfig buildModelClientConfig() {
        return ModelClientConfig.builder()
                .clientProvider(stringConfig("MODEL_PROVIDER", "OpenAI"))
                .apiKey(stringConfig("API_KEY", ""))
                .apiBase(stringConfig("API_BASE", "https://api.openai.com/v1"))
                .verifySsl(booleanConfig("LLM_SSL_VERIFY", false))
                .build();
    }

    public static ModelRequestConfig buildModelRequestConfig() {
        return ModelRequestConfig.builder()
                .modelName(stringConfig("MODEL_NAME", "gpt-4o"))
                .temperature(doubleConfig("LLM_TEMPERATURE", 0.7d))
                .seed(intConfig("LLM_SEED", 42))
                .build();
    }

    public static Model buildModel() {
        return new Model(buildModelClientConfig(), buildModelRequestConfig());
    }

    public static Path defaultRoot() {
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    public static String memoryPersistPath(Path root) {
        Path base = root != null ? root : defaultRoot();
        return base.resolve("memory_files").resolve("{algo_name}").resolve("{user_id}.json").toString();
    }

    public static TaskMemoryService buildMemoryService(Path root) {
        return new TaskMemoryService(
                null,
                null,
                null,
                "refcon",
                "refcon",
                null,
                "json",
                memoryPersistPath(root)
        );
    }

    public static AddMemoryRequest memoryRequest(String whenToUse, String content) {
        AddMemoryRequest request = new AddMemoryRequest();
        request.setWhenToUse(whenToUse);
        request.setContent(content);
        return request;
    }

    public static AddMemoryRequest pythonDebuggingMemoryRequest() {
        return memoryRequest("When asked how to debug Python code or find bugs", DEBUGGING_MEMORY);
    }

    public static AddMemoryRequest pythonUnitTestingMemoryRequest() {
        return memoryRequest("When asked how to write or structure Python unit tests", TESTING_MEMORY);
    }

    public static List<Map<String, Object>> seedDemoMemories(TaskMemoryService memoryService, String userId) {
        String effectiveUserId = userId != null && !userId.isBlank() ? userId : DEFAULT_USER_ID;
        List<Map<String, Object>> results = new ArrayList<>();
        results.add(memoryService.addMemory(effectiveUserId, pythonDebuggingMemoryRequest()).join());
        results.add(memoryService.addMemory(effectiveUserId, pythonUnitTestingMemoryRequest()).join());
        return results;
    }

    public static ContextEvolutionRail buildRail(String userId, TaskMemoryService memoryService) {
        return new ContextEvolutionRail(userId, memoryService);
    }

    public static AgentCard makeCard(String agentId, String name) {
        return AgentCard.builder()
                .id(agentId)
                .name(name)
                .description(name)
                .build();
    }

    public static DeepAgentConfig buildAgentConfig(ContextEvolutionRail rail) {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(makeCard("mem_agent", "Memory-augmented DeepAgent"));
        config.setModelClientConfig(buildModelClientConfig());
        config.setModelRequestConfig(buildModelRequestConfig());
        config.setSystemPrompt(SYSTEM_PROMPT);
        config.setMaxIterations(5);
        config.setRails(List.<AgentRail>of(rail));
        return config;
    }

    public static DeepAgent buildAgent(ContextEvolutionRail rail) {
        return HarnessFactory.createDeepAgent(buildAgentConfig(rail));
    }

    public static List<String> bannerLines(String title) {
        return List.of("", DIVIDER, title, DIVIDER);
    }

    public static List<String> sectionLines(String label) {
        return List.of("", "  " + label, "  " + SUBDIV);
    }

    public static Map<String, Object> invocationInput(String query) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("query", query);
        return input;
    }

    public static String sessionId(int index) {
        return "demo_session_" + index;
    }

    public static List<String> logResultLines(Map<String, Object> result) {
        Map<String, Object> safeResult = result != null ? result : Map.of();
        String output = stringValue(safeResult.get("output"));
        int memoriesUsed = intValue(safeResult.get("memories_used"), 0);
        List<String> outputLines = output.lines().toList();

        List<String> lines = new ArrayList<>();
        lines.add("  memories_used : " + memoriesUsed);
        lines.add("  Response      :");
        outputLines.stream().limit(6).forEach(line -> lines.add("    " + line));
        if (outputLines.size() > 6) {
            lines.add("    ... (truncated)");
        }
        return lines;
    }

    public static List<String> summaryLines(
            Map<String, Object> result1,
            Map<String, Object> result2,
            Map<String, Object> result3,
            int totalNodes
    ) {
        return List.of(
                "  Invoke 1 memories_used : " + memoriesUsed(result1),
                "  Invoke 2 memories_used : " + memoriesUsed(result2),
                "  Invoke 3 memories_used : " + memoriesUsed(result3),
                "  Total nodes in store   : " + totalNodes
        );
    }

    public static void main(String[] args) {
        applyDefaults();
        banner("ContextEvolutionRail - Abilities Demo");
        LOGGER.info("Demonstrates A1-A4 plus a WITHOUT vs. WITH comparison.");
        section("[Step 0] Checking configuration");

        if (!hasConfiguredApiKey()) {
            LOGGER.error("API key not configured. Edit API_KEY in defaults");
            return;
        }

        LOGGER.info("  API Base : {}", Config.get("API_BASE", "https://api.openai.com/v1"));
        LOGGER.info("  Model    : {}", Config.get("MODEL_NAME", "gpt-4o"));
        LOGGER.info("  Provider : {}", Config.get("MODEL_PROVIDER", "OpenAI"));

        Runner.start();
        try {
            buildModel();

            section("Creating TaskMemoryService and seeding a memory");
            TaskMemoryService memoryService = buildMemoryService(defaultRoot());
            seedDemoMemories(memoryService, DEFAULT_USER_ID);
            LOGGER.info("  Memory seeded (topic: Python debugging).");
            LOGGER.info("  Memory seeded (topic: Python unit testing).");

            banner("ContextEvolutionRail - Demo");
            ContextEvolutionRail hook = buildRail(DEFAULT_USER_ID, memoryService);
            section("rail (DeepAgent + ContextEvolutionRail, auto_summarize=True)");
            DeepAgent agent = buildAgent(hook);

            Map<String, Object> result1 = runInvoke(
                    agent,
                    1,
                    "How should I debug my Python code?"
            );
            LOGGER.info("  Nodes in store after invoke 1 : {}", memoryService.getVectorStore().getAll().size());

            Map<String, Object> result2 = runInvoke(
                    agent,
                    2,
                    "How do I write unit tests for my Python code?"
            );
            LOGGER.info("  Nodes in store after invoke 2 : {}", memoryService.getVectorStore().getAll().size());

            Map<String, Object> result3 = runInvoke(
                    agent,
                    3,
                    "My async Python code is throwing an unexpected exception. How do I investigate it?"
            );

            section("Memory growth summary");
            summaryLines(result1, result2, result3, memoryService.getVectorStore().getAll().size())
                    .forEach(LOGGER::info);
            banner("Demo Complete!");
        } finally {
            Runner.stop();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> runInvoke(DeepAgent agent, int index, String query) {
        section("[Invoke " + index + "] " + query);
        LOGGER.info("  Query: '{}'", query);
        Object raw = Runner.runAgent(agent, invocationInput(query), sessionId(index), null);
        Map<String, Object> result = raw instanceof Map<?, ?> map ? toStringKeyMap(map) : Map.of("output", raw);
        logResultLines(result).forEach(LOGGER::info);
        return result;
    }

    private static void banner(String title) {
        bannerLines(title).forEach(LOGGER::info);
    }

    private static void section(String label) {
        sectionLines(label).forEach(LOGGER::info);
    }

    private static Map<String, Object> toStringKeyMap(Map<?, ?> map) {
        Map<String, Object> converted = new LinkedHashMap<>();
        map.forEach((key, value) -> converted.put(String.valueOf(key), value));
        return converted;
    }

    private static int memoriesUsed(Map<String, Object> result) {
        return intValue(result != null ? result.get("memories_used") : null, 0);
    }

    private static String stringConfig(String key, String defaultValue) {
        return stringValue(Config.get(key, defaultValue));
    }

    private static double doubleConfig(String key, double defaultValue) {
        Object value = Config.get(key, defaultValue);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return defaultValue;
        }
    }

    private static int intConfig(String key, int defaultValue) {
        return intValue(Config.get(key, defaultValue), defaultValue);
    }

    private static boolean booleanConfig(String key, boolean defaultValue) {
        Object value = Config.get(key, defaultValue);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null ? Boolean.parseBoolean(String.valueOf(value)) : defaultValue;
    }

    private static int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }
}
