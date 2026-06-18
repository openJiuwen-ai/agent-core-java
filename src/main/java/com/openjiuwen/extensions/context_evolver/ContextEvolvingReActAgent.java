/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.single_agent.agents.ReActAgent;
import com.openjiuwen.core.single_agent.agents.ReActAgentConfig;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import com.openjiuwen.extensions.context_evolver.core.config.Config;
import com.openjiuwen.extensions.context_evolver.service.TaskMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * ReActAgent with integrated memory retrieval capabilities.
 *
 * <p>Mirrors Python's {@code ContextEvolvingReActAgent} in
 * {@code openjiuwen/extensions/context_evolver/context_evolving_react_agent.py}.</p>
 */
public class ContextEvolvingReActAgent extends ReActAgent {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContextEvolvingReActAgent.class);
    private static final String DEFAULT_PERSIST_PATH = "./memories/{algo_name}/{user_id}.json";
    private static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant with access to a memory system. "
            + "When relevant memories are provided in your context, use them to inform "
            + "your responses. Always provide accurate, helpful answers based on both "
            + "your knowledge and any retrieved memories.";
    private static final String SELF_REFINE_PROMPT = "Let's carefully re-examine the previous trajectory, including "
            + "your reasoning steps and action taken. Pay special attention to whether you used the best "
            + "search sequence and whether you used the tool correctly. If you find inconsistencies, "
            + "correct them. If everything seems correct, make it more efficient. Now, solve the same "
            + "problem again from scratch.\n\n";
    private static final String SELF_DIVERSITY_PROMPT = "Let's carefully re-examine the previous trajectory, including "
            + "your reasoning steps and action taken. The solution might be correct or wrong. Now, solve the "
            + "same problem again from scratch using DIFFERENT reasoning approach. Focus on exploring "
            + "alternative strategies.\n\n";

    private final String userId;
    private final TaskMemoryService memoryService;
    private final boolean injectMemoriesInContext;
    private final boolean autoSummarize;
    private final String autoSummarizeMattsMode;

    private String lastRetrievedQuery;
    private Map<String, Object> lastRetrievalResult;

    public ContextEvolvingReActAgent(AgentCard card, String userId) {
        this(card, userId, null);
    }

    public ContextEvolvingReActAgent(AgentCard card, String userId, TaskMemoryService memoryService) {
        this(card, userId, memoryService, true);
    }

    public ContextEvolvingReActAgent(
            AgentCard card,
            String userId,
            TaskMemoryService memoryService,
            boolean injectMemoriesInContext
    ) {
        this(card, userId, memoryService, injectMemoriesInContext, null, DEFAULT_PERSIST_PATH,
                "localhost", 19530, "vector_nodes", false, "none");
    }

    public ContextEvolvingReActAgent(
            AgentCard card,
            String userId,
            TaskMemoryService memoryService,
            boolean injectMemoriesInContext,
            String persistType,
            String persistPath,
            String milvusHost,
            int milvusPort,
            String milvusCollection,
            boolean autoSummarize,
            String autoSummarizeMattsMode
    ) {
        super(card);
        this.userId = userId;
        this.injectMemoriesInContext = injectMemoriesInContext;
        this.autoSummarize = autoSummarize;
        this.autoSummarizeMattsMode = autoSummarizeMattsMode != null ? autoSummarizeMattsMode : "none";
        this.memoryService = memoryService != null
                ? memoryService
                : createMemoryService(persistType, persistPath, milvusHost, milvusPort, milvusCollection);
        this.memoryService.loadMemories(userId);
        autoConfigure();
        LOGGER.info("ContextEvolvingReActAgent initialized for user={}, inject_in_context={}, "
                + "auto_summarize={}, persist_type={}", userId, injectMemoriesInContext, autoSummarize, persistType);
    }

    private static TaskMemoryService createMemoryService(
            String persistType,
            String persistPath,
            String milvusHost,
            int milvusPort,
            String milvusCollection
    ) {
        if (persistType != null) {
            Config.setValue("MILVUS_HOST", milvusHost != null ? milvusHost : "localhost");
            Config.setValue("MILVUS_PORT", milvusPort);
            Config.setValue("MILVUS_COLLECTION", milvusCollection != null ? milvusCollection : "vector_nodes");
        }
        return new TaskMemoryService(null, null, null, null, null, null,
                persistType, persistPath != null ? persistPath : DEFAULT_PERSIST_PATH);
    }

    private void autoConfigure() {
        String apiKey = configString("API_KEY", "");
        if (apiKey.isEmpty()) {
            return;
        }
        ReActAgentConfig config = new ReActAgentConfig();
        config.configureModelClient(
                configString("MODEL_PROVIDER", "OpenAI"),
                apiKey,
                configString("API_BASE", "https://api.openai.com/v1"),
                configString("MODEL_NAME", "gpt-4"),
                false
        );
        config.configurePromptTemplate(List.of(Map.of("role", "system", "content", DEFAULT_SYSTEM_PROMPT)));
        config.configureMaxIterations(5);
        configure(config);
    }

    public void _auto_configure() {
        autoConfigure();
    }

    public CompletionStage<Object> invokeWithMemory(Object inputs, AgentSessionApi session) {
        NormalizedInput normalizedInput = normalizeInput(inputs);
        String retrievalQuery = normalizedInput.values().containsKey("retrieval_query")
                ? stringOrNull(normalizedInput.values().get("retrieval_query"))
                : normalizedInput.query();
        LOGGER.debug("Retrieving memories for query: {}", retrievalQuery);

        return retrieveMemory(retrievalQuery).thenCompose(memoryLookup -> {
            Map<String, Object> augmentedInput = new LinkedHashMap<>(normalizedInput.values());
            if (memoryLookup.memoriesUsed() > 0 && !memoryLookup.memoryString().isEmpty()) {
                if (injectMemoriesInContext) {
                    String memoryContext = "Some Related Experience to help you complete the task:\n"
                            + memoryLookup.memoryString() + "\n";
                    augmentedInput.put("query", memoryContext + "\n\n" + normalizedInput.query());
                } else {
                    augmentedInput.put("memory_context", memoryLookup.memoryString());
                    augmentedInput.put("memories_used", memoryLookup.memoriesUsed());
                }
            }
            return invokeBase(augmentedInput, session).thenApply(result -> attachMemoriesUsed(result,
                    memoryLookup.memoriesUsed()));
        });
    }

    public CompletionStage<Object> _invoke_with_memory(Object inputs, AgentSessionApi session) {
        return invokeWithMemory(inputs, session);
    }

    @Override
    public CompletionStage<Object> invoke(Object inputs, AgentSessionApi session) {
        NormalizedInput normalizedInput = normalizeInput(inputs);
        if (normalizedInput.query().isEmpty()) {
            LOGGER.warn("No query provided in inputs");
            return invokeBase(inputs, session);
        }

        Object mattsModeValue = normalizedInput.values().get("matts_mode");
        if (mattsModeValue != null) {
            return runTrials(
                    normalizedInput.query(),
                    Objects.toString(normalizedInput.values().getOrDefault("ground_truth", ""), ""),
                    parseInteger(normalizedInput.values().get("matts_k")),
                    String.valueOf(mattsModeValue)
            );
        }

        return invokeWithMemory(inputs, session);
    }

    /**
     * Isolates the parent ReAct call so tests can observe this class without real LLM calls.
     */
    protected CompletionStage<Object> invokeBase(Object inputs, AgentSessionApi session) {
        return super.invoke(inputs, session);
    }

    private CompletionStage<Object> runTrials(String question, String groundTruth, Integer mattsK, String mattsMode) {
        int trialCount;
        boolean selfRefine;
        if ("none".equals(mattsMode)) {
            trialCount = 1;
            selfRefine = false;
        } else {
            trialCount = mattsK != null ? mattsK : configInt("MATTS_DEFAULT_K", 3);
            selfRefine = "sequential".equals(mattsMode) || "combined".equals(mattsMode);
        }

        List<TrialOutput> outputs = new ArrayList<>();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (int index = 0; index < trialCount; index++) {
            int runIndex = index;
            chain = chain.thenCompose(ignored -> {
                Map<String, Object> trialInput = buildTrialInput(question, outputs, runIndex, selfRefine);
                return invokeWithMemory(trialInput, null).handle((result, error) -> {
                    outputs.add(error == null
                            ? evaluateTrial(question, groundTruth, result)
                            : new TrialOutput(null, null, 0));
                    return null;
                });
            });
        }

        return chain.thenCompose(ignored -> summarizeTrialOutputs(question, mattsMode, outputs))
                .thenApply(result -> result);
    }

    private Map<String, Object> buildTrialInput(
            String question,
            List<TrialOutput> previousOutputs,
            int runIndex,
            boolean selfRefine
    ) {
        String currentQuery;
        List<String> previousTrajectories = previousOutputs.stream()
                .map(TrialOutput::trajectory)
                .filter(Objects::nonNull)
                .toList();
        if (selfRefine && runIndex > 0 && !previousTrajectories.isEmpty()) {
            String previousTrajectory = previousTrajectories.get(previousTrajectories.size() - 1);
            String prompt = "refine".equals(configString("COMBINED_MATTS_PROMPT", "refine"))
                    ? SELF_REFINE_PROMPT
                    : SELF_DIVERSITY_PROMPT;
            currentQuery = "Previous attempt:\n" + previousTrajectory + "\n\n"
                    + prompt + "Question: " + question;
        } else {
            currentQuery = "Question: " + question;
        }

        Map<String, Object> trialInput = new LinkedHashMap<>();
        trialInput.put("query", currentQuery);
        if (selfRefine) {
            trialInput.put("retrieval_query", question);
        }
        return trialInput;
    }

    private TrialOutput evaluateTrial(String question, String groundTruth, Object result) {
        String output = outputFromResult(result);
        boolean success = true;
        if (groundTruth != null && !groundTruth.isEmpty()) {
            success = output.toLowerCase(Locale.ROOT).contains(groundTruth.toLowerCase(Locale.ROOT));
        }
        String feedback = success ? "success" : "failure";
        int score = success ? 1 : 0;
        String trajectory = "USER: " + question + "\nASSISTANT: " + output;
        return new TrialOutput(trajectory, feedback, score);
    }

    private CompletionStage<Map<String, Object>> summarizeTrialOutputs(
            String question,
            String mattsMode,
            List<TrialOutput> outputs
    ) {
        List<Object> trajectories = new ArrayList<>();
        List<Boolean> labels = new ArrayList<>();
        List<Integer> scores = new ArrayList<>();
        for (TrialOutput output : outputs) {
            trajectories.add(output.trajectory());
            labels.add(output.score() == 1);
            scores.add(output.score());
        }

        if ("sequential".equals(mattsMode) && !trajectories.isEmpty()) {
            int last = trajectories.size() - 1;
            trajectories = new ArrayList<>(List.of(trajectories.get(last)));
            labels = new ArrayList<>(List.of(labels.get(last)));
            scores = new ArrayList<>(List.of(scores.get(last)));
        }

        return memoryService.summarize(userId, mattsMode, question, trajectories, labels, scores);
    }

    private CompletionStage<MemoryLookup> retrieveMemory(String retrievalQuery) {
        if (Objects.equals(lastRetrievedQuery, retrievalQuery)
                && lastRetrievalResult != null
                && !lastRetrievalResult.isEmpty()) {
            LOGGER.info("Reusing cached memory retrieval result");
            return CompletableFuture.completedFuture(memoryLookup(lastRetrievalResult));
        }

        return memoryService.retrieve(userId, retrievalQuery).handle((memoryResult, error) -> {
            if (error != null) {
                LOGGER.error("Failed to retrieve memories: {}", error.getMessage());
                return new MemoryLookup("", 0);
            }
            Map<String, Object> safeResult = memoryResult != null
                    ? new LinkedHashMap<>(memoryResult)
                    : new LinkedHashMap<>();
            lastRetrievedQuery = retrievalQuery;
            lastRetrievalResult = safeResult;
            MemoryLookup lookup = memoryLookup(safeResult);
            LOGGER.info("Retrieved {} memories for query", lookup.memoriesUsed());
            return lookup;
        });
    }

    private static MemoryLookup memoryLookup(Map<String, Object> memoryResult) {
        String memoryString = Objects.toString(memoryResult.getOrDefault("memory_string", ""), "");
        Object retrieved = memoryResult.get("retrieved_memory");
        int memoriesUsed = retrieved instanceof Collection<?> collection ? collection.size() : 0;
        return new MemoryLookup(memoryString, memoriesUsed);
    }

    private static Object attachMemoriesUsed(Object result, int memoriesUsed) {
        if (!(result instanceof Map<?, ?> resultMap)) {
            return result;
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        resultMap.forEach((key, value) -> copy.put(String.valueOf(key), value));
        copy.put("memories_used", memoriesUsed);
        return copy;
    }

    private static NormalizedInput normalizeInput(Object inputs) {
        Map<String, Object> values = new LinkedHashMap<>();
        String query;
        if (inputs instanceof Map<?, ?> inputMap) {
            inputMap.forEach((key, value) -> values.put(String.valueOf(key), value));
            query = Objects.toString(values.getOrDefault("query", ""), "");
        } else if (inputs instanceof String text) {
            query = text;
            values.put("query", query);
        } else {
            query = "";
            values.put("query", inputs);
        }
        return new NormalizedInput(values, query);
    }

    private static String outputFromResult(Object result) {
        if (!(result instanceof Map<?, ?> map)) {
            return "";
        }
        Object output = map.containsKey("output") ? map.get("output") : "";
        return Objects.toString(output, "");
    }

    private static String configString(String key, String defaultValue) {
        Object value = Config.get(key, defaultValue);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static int configInt(String key, int defaultValue) {
        Object value = Config.get(key, defaultValue);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return defaultValue;
        }
    }

    private static Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public void addTool(Tool tool) {
        if (tool == null) {
            throw new IllegalArgumentException("tool is required");
        }
        getAbilityManager().add(tool.getCard());
        Runner.resourceMgr().addTool(tool);
    }

    public void add_tool(Tool tool) {
        addTool(tool);
    }

    public void addTools(Collection<? extends Tool> tools) {
        if (tools == null) {
            return;
        }
        for (Tool tool : tools) {
            addTool(tool);
        }
    }

    public void add_tools(Collection<? extends Tool> tools) {
        addTools(tools);
    }

    public String getUserId() {
        return userId;
    }

    public TaskMemoryService getMemoryService() {
        return memoryService;
    }

    public boolean isInjectMemoriesInContext() {
        return injectMemoriesInContext;
    }

    public boolean isAutoSummarize() {
        return autoSummarize;
    }

    public String getAutoSummarizeMattsMode() {
        return autoSummarizeMattsMode;
    }

    public static ReActAgentConfig createMemoryAgentConfig(MemoryAgentConfigInput params) {
        Objects.requireNonNull(params, "params");
        String systemPrompt = params.getSystemPrompt() != null ? params.getSystemPrompt() : DEFAULT_SYSTEM_PROMPT;
        ReActAgentConfig config = new ReActAgentConfig();
        config.configureModelClient(
                params.getModelProvider(),
                params.getApiKey(),
                params.getApiBase(),
                params.getModelName(),
                false
        );
        config.configurePromptTemplate(List.of(Map.of("role", "system", "content", systemPrompt)));
        config.configureMaxIterations(params.getMaxIterations());
        return config;
    }

    public static ReActAgentConfig create_memory_agent_config(MemoryAgentConfigInput params) {
        return createMemoryAgentConfig(params);
    }

    private record NormalizedInput(Map<String, Object> values, String query) {
    }

    private record MemoryLookup(String memoryString, int memoriesUsed) {
    }

    private record TrialOutput(String trajectory, String feedback, int score) {
    }
}
