/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.humaneval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.processor.compressor.PromptTruncationProcessorConfig;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import examples.reac_agent.ExampleApiConfigLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the OpenAI HumanEval benchmark against a configured LLM through the
 * Java ReAct agent, then executes each generated completion with a local
 * Python interpreter and reports the pass rate.
 *
 * <p>This mirrors the Python HumanEval evaluation flow: send the function
 * signature + docstring to the model, ask it to return only the function
 * body, append the dataset assertions, and run {@code check(entry_point)}
 * via {@code python}.
 *
 * <p>Dataset path defaults to
 * {@code D:/jiuwen_projects/modelscope--humaneval/snapshots/master/HumanEval.jsonl};
 * override with {@code -Dopenjiuwen.humaneval.path=...}. Python executable
 * defaults to {@code python}; override with
 * {@code -Dopenjiuwen.humaneval.python=...} or the {@code HUMANEVAL_PYTHON}
 * environment variable.
 *
 * @since 2026-08-08
 */
public final class HumanEvalDemo {

    private static final Logger LOG = LoggerFactory.getLogger(HumanEvalDemo.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String AGENT_ID = "humaneval_agent_java";
    private static final String CONVERSATION_PREFIX = "humaneval_demo_w";
    private static final int WORKER_COUNT = 8;
    private static final String DEFAULT_PYTHON = "python";

    private static final String SYSTEM_PROMPT =
            "You are a Python code completion assistant. " +
            "Complete the function body for the given signature and docstring. " +
            "Return ONLY the function body (the indented statements that go inside the function). " +
            "Do NOT repeat the signature, do NOT include imports, " +
            "do NOT wrap the answer in markdown fences. " +
            "Return raw Python code only.";

    private static final int MAX_ITERATIONS = 1;
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_TOKENS = 2048;
    private static final double REQUEST_TIMEOUT_SECONDS = 600.0;

    private HumanEvalDemo() {
    }

    /**
     * Entry point: loads the dataset, runs each task, prints the pass rate.
     *
     * @param args unused
     * @throws JsonProcessingException if a result cannot be serialized
     * @throws IllegalStateException   if the API config or dataset is missing
     */
    public static void main(String[] args) throws JsonProcessingException {
        boolean sslVerify = ExampleApiConfigLoader.getSslVerify();
        System.setProperty("RESTFUL_SSL_VERIFY", String.valueOf(sslVerify));

        Path datasetPath = HumanEvalDataset.resolvePath();
        List<HumanEvalTask> tasks = HumanEvalDataset.load(datasetPath);
        int taskLimit = Integer.getInteger("openjiuwen.humaneval.limit", -1);
        if (taskLimit > 0 && tasks.size() > taskLimit) {
            tasks = new ArrayList<>(tasks.subList(0, taskLimit));
        }
        LOG.info("Loaded {} HumanEval tasks from {}", tasks.size(), datasetPath);

        Path tempDir = createTempDir();
        String python = resolvePython();
        HumanEvalExecutor executor = new HumanEvalExecutor(python, tempDir);

        int total = tasks.size();
        AtomicInteger passed = new AtomicInteger(0);
        AtomicInteger errored = new AtomicInteger(0);
        AtomicInteger done = new AtomicInteger(0);

        ReActAgent[] workerAgents = new ReActAgent[WORKER_COUNT];
        for (int w = 0; w < WORKER_COUNT; w++) {
            workerAgents[w] = createAgent();
        }

        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                WORKER_COUNT,
                WORKER_COUNT,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(total),
                r -> {
                    Thread t = new Thread(r, "humaneval-worker");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        List<java.util.concurrent.Future<TaskResult>> futures = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            final int index = i;
            final int workerId = i % WORKER_COUNT;
            final HumanEvalTask task = tasks.get(i);
            final ReActAgent agent = workerAgents[workerId];
            futures.add(pool.submit(() -> runOneTask(index, task, executor, agent, workerId)));
        }

        for (java.util.concurrent.Future<TaskResult> future : futures) {
            TaskResult tr;
            try {
                tr = future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                errored.incrementAndGet();
                continue;
            } catch (java.util.concurrent.ExecutionException e) {
                errored.incrementAndGet();
                int d = done.incrementAndGet();
                LOG.warn("[{}/{}] execution exception: {}", d, total, e.getCause() != null
                        ? e.getCause().getMessage() : e.getMessage());
                continue;
            }
            int d = done.incrementAndGet();
            if (tr.passed) {
                passed.incrementAndGet();
            } else if (tr.errored) {
                errored.incrementAndGet();
            }
            LOG.info("[{}/{}] {} passed={} (pass_rate={}/{})",
                    d, total, tr.taskId, tr.passed, passed.get(), d);
        }

        pool.shutdown();
        try {
            pool.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Runner.stop();

        int passedCount = passed.get();
        int erroredCount = errored.get();
        double passRate = total == 0 ? 0.0 : (passedCount * 100.0) / total;
        String formattedRate = String.format(java.util.Locale.ROOT, "%.2f", passRate);
        LOG.info("HumanEval finished. passed={} total={} errored={} pass_rate={}%",
                passedCount, total, erroredCount, formattedRate);
        LOG.info("Pass rate: {}/{} = {}%", passedCount, total, formattedRate);
    }

    private static final class TaskResult {
        final String taskId;
        final boolean passed;
        final boolean errored;

        TaskResult(String taskId, boolean passed, boolean errored) {
            this.taskId = taskId;
            this.passed = passed;
            this.errored = errored;
        }
    }

    private static TaskResult runOneTask(int index, HumanEvalTask task, HumanEvalExecutor executor,
            ReActAgent agent, int workerId) {
        String conversationId = CONVERSATION_PREFIX + workerId;
        String userQuery = buildUserQuery(task);
        String completion = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String candidate = generateCompletion(userQuery, conversationId, agent);
                if (candidate != null && !candidate.isBlank()) {
                    completion = candidate;
                    break;
                }
                LOG.warn("Task {} [{}] attempt {} returned empty, retrying",
                        index + 1, task.taskId(), attempt);
            } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
                LOG.warn("Task {} [{}] attempt {} failed: {}",
                        index + 1, task.taskId(), attempt, e.getMessage());
            }
        }
        if (completion == null) {
            LOG.warn("Task {} [{}] all {} attempts returned empty",
                    index + 1, task.taskId(), MAX_ATTEMPTS);
            return new TaskResult(task.taskId(), false, true);
        }
        try {
            boolean ok = executor.run(task, completion);
            return new TaskResult(task.taskId(), ok, false);
        } catch (RuntimeException e) {
            LOG.warn("Task {} [{}] execution failed: {}", index + 1, task.taskId(), e.getMessage());
            return new TaskResult(task.taskId(), false, true);
        }
    }

    private static String generateCompletion(String userQuery, String conversationId, ReActAgent agent)
            throws JsonProcessingException {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) Runner.runAgent(
                agent,
                Map.of(
                        "query", userQuery,
                        "conversation_id", conversationId
                ),
                null,
                null
        );
        Object output = result.get("output");
        if (output == null) {
            LOG.warn("Raw agent result for conversationId={}: {}", conversationId,
                    MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        } else {
            String raw = output.toString();
            LOG.info("Raw completion for {}: len={} first200=[{}]", conversationId, raw.length(),
                    raw.length() > 200 ? raw.substring(0, 200) : raw);
        }
        if (output == null) {
            throw new IllegalStateException("Agent returned no output. result="
                    + MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        }
        return output.toString();
    }

    private static ReActAgent createAgent() {
        AgentCard agentCard = AgentCard.builder()
                .id(AGENT_ID)
                .name(AGENT_ID)
                .description("HumanEval code completion agent")
                .build();

        ReActAgent agent = new ReActAgent(agentCard);
        PromptTruncationProcessorConfig truncationConfig = PromptTruncationProcessorConfig.builder()
                .maxContextTokens(200)
                .preserveHeadChars(80)
                .preserveTailChars(40)
                .build();
        ContextEngineConfig contextEngineConfig = ContextEngineConfig.builder()
                .maxContextMessageNum(200)
                .defaultWindowMessageNum(2)
                .defaultWindowRoundNum(10)
                .enableKvCacheRelease(true)
                .build();
        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of("role", "system", "content", SYSTEM_PROMPT)))
                .maxIterations(MAX_ITERATIONS)
                .contextEngineConfig(contextEngineConfig)
                .build()
                .configureModelClient(
                        ExampleApiConfigLoader.getModelProvider(),
                        ExampleApiConfigLoader.getApiKey(),
                        ExampleApiConfigLoader.getApiBase(),
                        ExampleApiConfigLoader.getModelName(),
                        ExampleApiConfigLoader.getSslVerify()
                )
                .configureContextProcessors(List.of(
                        new ContextEngine.ProcessorSpec("PromptTruncationProcessor", truncationConfig)));

        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(ExampleApiConfigLoader.getModelProvider())
                .apiKey(ExampleApiConfigLoader.getApiKey())
                .apiBase(ExampleApiConfigLoader.getApiBase())
                .verifySsl(ExampleApiConfigLoader.getSslVerify())
                .timeout(REQUEST_TIMEOUT_SECONDS)
                .build();
        config.setModelClientConfig(clientConfig);

        ModelRequestConfig requestConfig = config.getModelConfigObj();
        requestConfig.setTemperature(0.0);
        requestConfig.setTopP(1.0);
        requestConfig.setMaxTokens(MAX_TOKENS);

        agent.configure(config);
        LOG.info("createAgent contextEngineConfig: enableKvCacheRelease={}, defaultWindowMessageNum={}, "
                + "defaultWindowRoundNum={}, maxContextMessageNum={}",
                config.getContextEngineConfig().isEnableKvCacheRelease(),
                config.getContextEngineConfig().getDefaultWindowMessageNum(),
                config.getContextEngineConfig().getDefaultWindowRoundNum(),
                config.getContextEngineConfig().getMaxContextMessageNum());
        return agent;
    }

    private static String buildUserQuery(HumanEvalTask task) {
        return "Complete the following Python function. Return ONLY the function body, "
                + "no signature, no imports, no markdown fences, raw Python only.\n\n"
                + task.prompt();
    }

    private static String resolvePython() {
        String propertyValue = System.getProperty("openjiuwen.humaneval.python");
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        String envValue = System.getenv("HUMANEVAL_PYTHON");
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return DEFAULT_PYTHON;
    }

    private static Path createTempDir() {
        try {
            Path dir = Files.createTempDirectory("humaneval-");
            dir.toFile().deleteOnExit();
            return dir;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to create temp dir for HumanEval scripts", e);
        }
    }
}
