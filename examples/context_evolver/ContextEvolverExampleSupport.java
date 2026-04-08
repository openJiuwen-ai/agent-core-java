/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package examples.context_evolver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.extensions.context_evolver.ContextEvolvingReActAgent;
import com.openjiuwen.extensions.context_evolver.MemoryAgentConfigInput;
import com.openjiuwen.extensions.context_evolver.SummarizeTrajectoriesInput;
import com.openjiuwen.extensions.context_evolver.core.config.Config;
import com.openjiuwen.extensions.context_evolver.core.context.ServiceContext;
import com.openjiuwen.extensions.context_evolver.service.AddMemoryRequest;
import com.openjiuwen.extensions.context_evolver.service.TaskMemoryService;
import com.openjiuwen.extensions.context_evolver.tool.WikipediaTool;
import examples.utils.SharedExampleApiConfigLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class ContextEvolverExampleSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SECTION_DIVIDER = "=".repeat(72);
    private static final String SUBSECTION_DIVIDER = "-".repeat(72);

    private static final String QUICKSTART_AGENT_ID = "context_evolver_java_example";
    private static final String HOTPOT_AGENT_ID = "context_evolver_hotpot_java_example";
    private static final String DEMO_USER_ID = "demo_user";
    private static final String HOTPOT_USER_ID = "demo_user_hotpot";
    private static final String DEFAULT_QUERY = "What are some Python best practices?";
    private static final String LEARNING_QUERY = "How do I write a Python function with type hints?";
    private static final int QUICKSTART_MAX_ITERATIONS = 5;
    private static final int HOTPOT_MAX_ITERATIONS = 6;
    private static final int DEFAULT_MATTS_K = resolveIntSetting(
            "openjiuwen.example.contextEvolver.mattsK",
            "CONTEXT_EVOLVER_MATTS_K",
            3
    );

    private static final String QUICKSTART_SYSTEM_PROMPT = "You are a helpful programming assistant. "
            + "Use any provided memory context to give better answers, and keep the response concise and actionable.";

    private static final String HOTPOT_SYSTEM_PROMPT = "You are a factual research assistant. "
            + "For knowledge questions, call wikipedia_search before answering and rely on the tool result. "
            + "Summarize the evidence clearly after the tool returns.";

    private static final List<HotpotQuestion> HOTPOT_QUESTIONS = List.of(
            new HotpotQuestion(
                    "Which magazine was started first Arthur's Magazine or First for Women?",
                    "Arthur's Magazine"
            ),
            new HotpotQuestion(
                    "Which tennis player won more Grand Slam titles, Henri Leconte or Jonathan Stark?",
                    "Jonathan Stark"
            ),
            new HotpotQuestion(
                    "Were Pavel Urysohn and Leonid Levin known for the same type of work?",
                    "no"
            )
    );

    private static final Path EXAMPLE_ROOT = detectExampleRoot();
    private static final Path OUTPUT_DIR = resolvePathSetting(
            "openjiuwen.example.contextEvolver.outputDir",
            "CONTEXT_EVOLVER_OUTPUT_DIR",
            EXAMPLE_ROOT.resolve("output")
    );
    private static final Path MEMORY_DIR = OUTPUT_DIR.resolve("memory_files");
    private static final Path LOG_FILE = OUTPUT_DIR.resolve("quickstart.log");

    private ContextEvolverExampleSupport() {
    }

    static void run(String[] args) throws Exception {
        Map<String, Object> configSnapshot = Config.snapshot();
        try {
            ensureRuntimeDirectories();
            resetLogFile();

            Map<String, String> apiConfig = SharedExampleApiConfigLoader.load();
            configureBaseSettings(apiConfig);

            section("ContextEvolvingReActAgent Quick Start");
            printConfigSummary(apiConfig);

            runCoreQuickstart(apiConfig, resolvePrimaryQuery(args));
            runHotpotDemo(apiConfig);
            printNextSteps();
        } finally {
            ServiceContext.getInstance().clear();
            Config.restore(configSnapshot);
            Runner.stop();
        }
    }

    private static void runCoreQuickstart(Map<String, String> apiConfig, String query) throws Exception {
        String retrievalAlgo = Config.getString("RETRIEVAL_ALGO", "REME");
        String summaryAlgo = Config.getString("SUMMARY_ALGO", "REME");

        subsection("[Step 1] Checking configuration");
        keyValue("API Base", SharedExampleApiConfigLoader.getApiBase());
        keyValue("Model", SharedExampleApiConfigLoader.getModelName());
        keyValue("Embedding", resolveEmbeddingModel(apiConfig));
        keyValue("Output Dir", describePath(OUTPUT_DIR));
        line("Configuration OK.");

        subsection("[Step 2] Creating TaskMemoryService");
        TaskMemoryService memoryService = createMemoryService(apiConfig, retrievalAlgo, summaryAlgo);
        keyValue("Retrieval Algo", memoryService.getRetrievalAlgorithm());
        keyValue("Summary Algo", memoryService.getSummaryAlgorithm());
        line("TaskMemoryService created.");

        subsection("[Step 3] Adding memories to knowledge base");
        AddMemoryRequest memoryRequest = buildSeedMemoryRequest(memoryService.getSummaryAlgorithm());
        Map<String, Object> addResult = memoryService.addMemory(DEMO_USER_ID, memoryRequest).join();
        keyValue("Add Status", addResult.get("status"));
        keyValue("Memory Id", addResult.get("memory_id"));
        line("Added a seed memory using the %s schema.", memoryService.getSummaryAlgorithm());

        subsection("[Step 4] Creating ContextEvolvingReActAgent");
        Path quickstartMemoryFile = memoryFileFor(DEMO_USER_ID);
        if (Files.isRegularFile(quickstartMemoryFile)) {
            keyValue("Existing Memory", describePath(quickstartMemoryFile));
        }
        ContextEvolvingReActAgent agent = createAgent(
                QUICKSTART_AGENT_ID,
                "Context Evolver Java Example",
                "A memory-augmented assistant for the Java quickstart example.",
                DEMO_USER_ID,
                memoryService,
                QUICKSTART_SYSTEM_PROMPT,
                QUICKSTART_MAX_ITERATIONS
        );
        line("Agent created and configured.");

        subsection("[Step 5] Querying the agent");
        keyValue("Query", query);
        AgentSession querySession = new AgentSession("context-evolver-query-" + UUID.randomUUID());
        Map<String, Object> firstResult = invokeAgent(agent, query, querySession);
        keyValue("Memories Used", firstResult.getOrDefault("memories_used", 0));
        line("Response:");
        line(String.valueOf(firstResult.getOrDefault("output", "No output")));

        subsection("[Step 6] Summarizing trajectories");
        AgentSession learningSession = new AgentSession("context-evolver-learning-" + UUID.randomUUID());
        Map<String, Object> secondResult = invokeAgent(agent, LEARNING_QUERY, learningSession);
        String secondOutput = String.valueOf(secondResult.getOrDefault("output", "No output"));
        String trajectory = extractTrajectory(agent, learningSession, LEARNING_QUERY, secondOutput);
        Map<String, Object> summaryResult = agent.summarizeTrajectories(
                new SummarizeTrajectoriesInput(
                        LEARNING_QUERY,
                        trajectory,
                        "none",
                        "helpful"
                )
        ).join();

        appendLog("[Step 6] Query: " + LEARNING_QUERY);
        appendLog("[Step 6] Trajectory:\n" + trajectory);
        appendLog("[Step 6] Summary:\n" + toPrettyJson(summaryResult));

        keyValue("Memories Extracted", extractMemoryCount(summaryResult));
        keyValue("Memory File", describePath(quickstartMemoryFile));
        keyValue("Learn Query", LEARNING_QUERY);
        line("Trajectory summarization completed.");
    }

    private static void runHotpotDemo(Map<String, String> apiConfig) throws Exception {
        subsection("[Step 7] Retrieve-Generate-Summarize Loop (HotpotQA Example)");
        keyValue("Algorithm", "ReasoningBank");
        keyValue("MATTS Mode", "parallel");
        keyValue("MATTS K", DEFAULT_MATTS_K);
        keyValue("Log File", describePath(LOG_FILE));

        TaskMemoryService memoryService = createMemoryService(apiConfig, "RB", "RB");
        ContextEvolvingReActAgent agent = createAgent(
                HOTPOT_AGENT_ID,
                "Context Evolver HotpotQA Example",
                "A Wikipedia-backed Context Evolver demo for multi-hop QA.",
                HOTPOT_USER_ID,
                memoryService,
                HOTPOT_SYSTEM_PROMPT,
                HOTPOT_MAX_ITERATIONS
        );
        agent.addTool(WikipediaTool.createWikipediaTool());

        try {
            for (int index = 0; index < HOTPOT_QUESTIONS.size(); index++) {
                HotpotQuestion item = HOTPOT_QUESTIONS.get(index);
                line("Processing Q%d: %s", index + 1, item.question);

                List<String> trajectories = new ArrayList<>();
                for (int runId = 0; runId < DEFAULT_MATTS_K; runId++) {
                    AgentSession session = new AgentSession("context-evolver-hotpot-" + UUID.randomUUID());
                    Map<String, Object> result = invokeAgent(agent, item.question, session);
                    String output = String.valueOf(result.getOrDefault("output", "No output"));
                    boolean correct = output.toLowerCase(Locale.ROOT)
                            .contains(item.answer.toLowerCase(Locale.ROOT));
                    String trajectory = extractTrajectory(agent, session, item.question, output);
                    trajectories.add(trajectory);

                    line("  Trial %d/%d: %s", runId + 1, DEFAULT_MATTS_K, correct ? "SUCCESS" : "FAILURE");
                    line("    Output: %s", abbreviate(output, 160));

                    appendLog("[Hotpot] Question " + (index + 1) + " trial " + (runId + 1));
                    appendLog("[Hotpot] Ground Truth: " + item.answer);
                    appendLog("[Hotpot] Correct: " + correct);
                    appendLog("[Hotpot] Trajectory:\n" + trajectory);
                    appendLog("");
                }

                Map<String, Object> summaryResult = agent.summarizeTrajectories(
                        new SummarizeTrajectoriesInput(item.question, trajectories, "parallel")
                ).join();

                keyValue("  Memories Extracted", extractMemoryCount(summaryResult));
                appendLog("[Hotpot] Summary for Q" + (index + 1) + ":\n" + toPrettyJson(summaryResult));
                appendLog("");
            }

            keyValue("Hotpot Memory", describePath(memoryFileFor(HOTPOT_USER_ID)));
        } finally {
            removeTool("wikipedia_search", HOTPOT_AGENT_ID);
        }
    }

    private static void printConfigSummary(Map<String, String> apiConfig) {
        keyValue("Provider", SharedExampleApiConfigLoader.getModelProvider());
        keyValue("Model", SharedExampleApiConfigLoader.getModelName());
        keyValue("API Base", SharedExampleApiConfigLoader.getApiBase());
        keyValue("SSL Verify", SharedExampleApiConfigLoader.getSslVerify());
        keyValue("Embedding", resolveEmbeddingModel(apiConfig));
        keyValue("Initial Retrieval", Config.getString("RETRIEVAL_ALGO", "REME"));
        keyValue("Initial Summary", Config.getString("SUMMARY_ALGO", "REME"));
        keyValue("Memory Dir", describePath(MEMORY_DIR));
        keyValue("MATTS K", DEFAULT_MATTS_K);
    }

    private static void printNextSteps() {
        line();
        line(SECTION_DIVIDER);
        line("Quick start complete.");
        line(SECTION_DIVIDER);
        line("1. Add more seed memories with TaskMemoryService.addMemory(...)");
        line("2. Query the agent with a custom prompt by passing arguments to the example entry point");
        line("3. Inspect %s for trajectories and summarization output", describePath(LOG_FILE));
        line("4. Re-run the example to observe persisted memory reuse from %s", describePath(MEMORY_DIR));
    }

    private static void configureBaseSettings(Map<String, String> apiConfig) {
        Config.setValue("API_KEY", SharedExampleApiConfigLoader.getApiKey());
        Config.setValue("API_BASE", SharedExampleApiConfigLoader.getApiBase());
        Config.setValue("MODEL_PROVIDER", SharedExampleApiConfigLoader.getModelProvider());
        Config.setValue("MODEL_NAME", SharedExampleApiConfigLoader.getModelName());
        Config.setValue("LLM_SSL_VERIFY", SharedExampleApiConfigLoader.getSslVerify());
        Config.setValue("EMBEDDING_MODEL", resolveEmbeddingModel(apiConfig));
        Config.setValue("EMBEDDING_DIMENSIONS", resolveEmbeddingDimensions(apiConfig));
    }

    private static TaskMemoryService createMemoryService(
            Map<String, String> apiConfig,
            String retrievalAlgo,
            String summaryAlgo) {
        ServiceContext.getInstance().clear();
        Config.setValue("RETRIEVAL_ALGO", retrievalAlgo);
        Config.setValue("SUMMARY_ALGO", summaryAlgo);
        Config.setValue("MANAGEMENT_ALGO", summaryAlgo);

        return new TaskMemoryService(
                SharedExampleApiConfigLoader.getModelName(),
                resolveEmbeddingModel(apiConfig),
                SharedExampleApiConfigLoader.getApiKey(),
                retrievalAlgo,
                summaryAlgo
        );
    }

    private static ContextEvolvingReActAgent createAgent(
            String agentId,
            String agentName,
            String description,
            String userId,
            TaskMemoryService memoryService,
            String systemPrompt,
            int maxIterations) {
        AgentCard agentCard = AgentCard.builder()
                .id(agentId)
                .name(agentName)
                .description(description)
                .build();

        ContextEvolvingReActAgent agent = new ContextEvolvingReActAgent(
                agentCard,
                userId,
                memoryService,
                true,
                MEMORY_DIR.toString()
        );

        ReActAgentConfig config = ContextEvolvingReActAgent.createMemoryAgentConfig(
                new MemoryAgentConfigInput(
                        SharedExampleApiConfigLoader.getModelProvider(),
                        SharedExampleApiConfigLoader.getApiKey(),
                        SharedExampleApiConfigLoader.getApiBase(),
                        SharedExampleApiConfigLoader.getModelName(),
                        systemPrompt,
                        maxIterations
                )
        );

        config.configureModelClient(
                SharedExampleApiConfigLoader.getModelProvider(),
                SharedExampleApiConfigLoader.getApiKey(),
                SharedExampleApiConfigLoader.getApiBase(),
                SharedExampleApiConfigLoader.getModelName(),
                SharedExampleApiConfigLoader.getSslVerify()
        ).configureContextEngine(null, null, false);

        ModelRequestConfig requestConfig = config.getModelConfigObj();
        if (requestConfig != null) {
            requestConfig.setTemperature(agentId.equals(HOTPOT_AGENT_ID) ? 0.2 : 0.6);
            requestConfig.setTopP(agentId.equals(HOTPOT_AGENT_ID) ? 0.8 : 0.9);
            requestConfig.setMaxTokens(agentId.equals(HOTPOT_AGENT_ID) ? 600 : 400);
        }

        agent.configure(config);
        return agent;
    }

    private static AddMemoryRequest buildSeedMemoryRequest(String summaryAlgorithm) {
        AddMemoryRequest request = new AddMemoryRequest();

        if ("ReasoningBank".equals(summaryAlgorithm)) {
            request.setTitle("Python Best Practices");
            request.setDescription("Guidelines for writing clean Python code");
            request.setContent(
                    "Use meaningful variable names. Follow PEP 8 style guide. "
                            + "Write docstrings for functions. Use type hints for clarity. "
                            + "Prefer list comprehensions over loops when appropriate."
            );
            return request;
        }

        if ("ReMe".equals(summaryAlgorithm) || "Our".equals(summaryAlgorithm)) {
            request.setWhenToUse(
                    "When writing Python code and you need best practices for clean, maintainable code"
            );
            request.setContent(
                    "Use meaningful variable names. Follow PEP 8 style guide. "
                            + "Write docstrings for functions. Use type hints for clarity. "
                            + "Prefer list comprehensions over loops when appropriate."
            );
            return request;
        }

        request.setSection("python");
        request.setContent(
                "Use meaningful variable names. Follow PEP 8 style guide. "
                        + "Write docstrings for functions. Use type hints for clarity. "
                        + "Prefer list comprehensions over loops when appropriate."
        );
        return request;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invokeAgent(
            ContextEvolvingReActAgent agent,
            String query,
            AgentSession session) {
        return (Map<String, Object>) agent.invoke(Map.of("query", query), session);
    }

    private static String extractTrajectory(
            ContextEvolvingReActAgent agent,
            AgentSession session,
            String query,
            String output) {
        ModelContext context = agent.getContextEngine().getContext("default_context_id", session.getSessionId());
        if (context == null) {
            context = agent.getContextEngine().getContext(null, session.getSessionId());
        }
        if (context == null) {
            return "USER: " + query + System.lineSeparator() + "ASSISTANT: " + output;
        }

        List<Object> messages = new ArrayList<>();
        messages.addAll(context.getMessages());
        String formatted = agent.formatTrajectory(messages);
        return formatted == null || formatted.isBlank()
                ? "USER: " + query + System.lineSeparator() + "ASSISTANT: " + output
                : formatted;
    }

    private static int extractMemoryCount(Map<String, Object> summaryResult) {
        if (summaryResult == null) {
            return 0;
        }
        Object memories = summaryResult.get("memory");
        if (memories instanceof List<?> list) {
            return list.size();
        }
        return 0;
    }

    private static Path memoryFileFor(String userId) {
        String summaryAlgo = Config.getString("SUMMARY_ALGO", "RB");
        return MEMORY_DIR.resolve("memory_" + summaryAlgo + "_" + userId + ".json");
    }

    private static String resolvePrimaryQuery(String[] args) {
        if (args == null || args.length == 0) {
            return DEFAULT_QUERY;
        }
        String joined = String.join(" ", args).trim();
        return joined.isEmpty() ? DEFAULT_QUERY : joined;
    }

    private static String resolveEmbeddingModel(Map<String, String> apiConfig) {
        String configured = firstNonBlank(
                apiConfig.get("EMBEDDING_MODEL"),
                apiConfig.get("MODEL_NAME_EMBEDDING")
        );
        return configured != null ? configured : "text-embedding-3-small";
    }

    private static String resolveEmbeddingDimensions(Map<String, String> apiConfig) {
        String configured = firstNonBlank(apiConfig.get("EMBEDDING_DIMENSIONS"));
        return configured != null ? configured : "2560";
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static void removeTool(String toolId, String agentId) {
        try {
            Runner.resourceMgr().removeTool(toolId, agentId, TagMatchStrategy.ALL, true);
        } catch (RuntimeException ignored) {
            // Resource manager cleanup is best-effort for examples.
        }
    }

    private static void ensureRuntimeDirectories() throws IOException {
        Files.createDirectories(OUTPUT_DIR);
        Files.createDirectories(MEMORY_DIR);
    }

    private static void resetLogFile() throws IOException {
        Files.writeString(
                LOG_FILE,
                "Context Evolver quickstart log" + System.lineSeparator() + SECTION_DIVIDER + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    private static void appendLog(String content) throws IOException {
        Files.writeString(
                LOG_FILE,
                content + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    private static String toPrettyJson(Object value) throws IOException {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }

    private static String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static void section(String title) {
        line(SECTION_DIVIDER);
        line(title);
        line(SECTION_DIVIDER);
    }

    private static void subsection(String title) {
        line();
        line(SUBSECTION_DIVIDER);
        line(title);
        line(SUBSECTION_DIVIDER);
    }

    private static void keyValue(String label, Object value) {
        line("%-18s %s", label + ":", value);
    }

    private static void line() {
        System.out.println();
    }

    private static void line(String format, Object... args) {
        if (args == null || args.length == 0) {
            System.out.println(format);
            return;
        }
        System.out.println(String.format(Locale.ROOT, format, args));
    }

    private static Path detectExampleRoot() {
        Path currentDirectory = Path.of("").toAbsolutePath().normalize();
        List<Path> candidates = List.of(
                currentDirectory.resolve("examples").resolve("context_evolver"),
                currentDirectory.resolve("agent-core-java-myfork").resolve("examples").resolve("context_evolver"),
                currentDirectory
        );
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (isContextEvolverDirectory(normalized)) {
                return normalized;
            }
        }
        return candidates.get(0).toAbsolutePath().normalize();
    }

    private static boolean isContextEvolverDirectory(Path path) {
        if (!Files.isDirectory(path)) {
            return false;
        }
        Path fileName = path.getFileName();
        return fileName != null && "context_evolver".equalsIgnoreCase(fileName.toString());
    }

    private static Path resolvePathSetting(String propertyKey, String envKey, Path defaultValue) {
        String configuredValue = resolveOptionalStringSetting(propertyKey, envKey);
        if (configuredValue == null) {
            return defaultValue.toAbsolutePath().normalize();
        }
        return Path.of(configuredValue).toAbsolutePath().normalize();
    }

    private static int resolveIntSetting(String propertyKey, String envKey, int defaultValue) {
        String configuredValue = resolveOptionalStringSetting(propertyKey, envKey);
        if (configuredValue == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(configuredValue.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static String resolveOptionalStringSetting(String propertyKey, String envKey) {
        String propertyValue = System.getProperty(propertyKey);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue.trim();
        }
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }
        return null;
    }

    private static String describePath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(EXAMPLE_ROOT)) {
            String relativePath = EXAMPLE_ROOT.relativize(normalized).toString().replace('\\', '/');
            return relativePath.isEmpty() ? "." : relativePath;
        }
        return normalized.toString().replace('\\', '/');
    }

    private static final class HotpotQuestion {
        private final String question;
        private final String answer;

        private HotpotQuestion(String question, String answer) {
            this.question = question;
            this.answer = answer;
        }
    }
}