/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.singleagent.toolgroup;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.modelclients.OpenAiCompatibleModelClient;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.singleagent.skills.SkillManager;
import com.openjiuwen.core.singleagent.skills.repository.ClasspathSkillRepository;
import com.openjiuwen.core.singleagent.skills.repository.FileSystemSkillRepository;
import com.openjiuwen.core.singleagent.skills.repository.SkillRepository;
import com.openjiuwen.core.singleagent.skills.repository.WorkspaceSkillRepository;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Real-LLM benchmark for TC-PRF-03. This test is opt-in because it calls a remote model.
 *
 * <p>Required environment variables are {@code TC_LLM_API_KEY}, {@code TC_LLM_API_BASE}, and {@code
 * TC_LLM_MODEL}. Optional controls are {@code TC_PRF03_ROUNDS} (default 3) and {@code
 * TC_PRF03_CONCURRENCIES} (default 1,10,40).
 */
@Tag("issue196-w11")
@Tag("TC-PRF-03")
@Tag("perftest")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(1800)
class TCPRF03RealLlmPerformanceTest {

    private static final Path RAW_LOG = Path.of("target", "tcprf03_real_llm_raw.csv");
    private static final Path SUMMARY_LOG = Path.of("target", "tcprf03_real_llm_summary.txt");
    private static final int TOOL_COUNT = 20;
    private static final List<Integer> DEFAULT_CONCURRENCIES = List.of(1, 10, 40);
    private static final List<Task> TASKS =
            List.of(
                    new Task("Search for today's news headlines.", "tool_search"),
                    new Task("Draw a bar chart of sales data.", "tool_chart"),
                    new Task("Delete the temporary file at /tmp/old.log.", "tool_delete_file"),
                    new Task("Translate 'hello world' to French.", "tool_translate"),
                    new Task("Calculate the square root of 144.", "tool_calculator"),
                    new Task("Read the contents of /home/user/config.yaml.", "tool_read_file"),
                    new Task("Send an email to team@example.com.", "tool_send_email"),
                    new Task("List all files in the current directory.", "tool_list_files"),
                    new Task("Extract text from the PDF report.pdf.", "tool_extract_pdf"),
                    new Task(
                            "Create a new GitHub issue with title 'Bug report'.",
                            "tool_create_issue"));

    private static OpenAiCompatibleModelClient modelClient;
    private static String modelName;
    private static int rounds;
    private static List<Integer> concurrencyLevels;
    private static int maxParallelRequests;

    @TempDir private Path tempDir;

    @BeforeAll
    static void requireRealLlmEnvironment() {
        String apiKey = System.getenv("TC_LLM_API_KEY");
        String apiBase = System.getenv("TC_LLM_API_BASE");
        modelName = System.getenv("TC_LLM_MODEL");
        Assumptions.assumeTrue(
                apiKey != null && !apiKey.isBlank(), "TC_LLM_API_KEY is not configured");
        Assumptions.assumeTrue(
                apiBase != null && !apiBase.isBlank(), "TC_LLM_API_BASE is not configured");
        Assumptions.assumeTrue(
                modelName != null && !modelName.isBlank(), "TC_LLM_MODEL is not configured");
        rounds = integerEnvironment("TC_PRF03_ROUNDS", 3);
        concurrencyLevels = parseConcurrencyLevels(System.getenv("TC_PRF03_CONCURRENCIES"));
        maxParallelRequests = integerEnvironment("TC_PRF03_MAX_PARALLEL", 10);
        ModelClientConfig clientConfig =
                ModelClientConfig.builder()
                        .apiKey(apiKey)
                        .apiBase(apiBase)
                        .clientProvider("OpenAI")
                        .maxRetries(2)
                        .build();
        ModelRequestConfig requestConfig =
                ModelRequestConfig.builder()
                        .modelName(modelName)
                        .temperature(0.0F)
                        .topP(0.1F)
                        .build();
        modelClient = new OpenAiCompatibleModelClient(requestConfig, clientConfig);
    }

    @Test
    @Order(1)
    void skillRepositoryRefreshBenchmark() throws IOException {
        Path filesystemRoot = tempDir.resolve("filesystem-skills");
        Path workspaceRoot = tempDir.resolve("workspace-skills");
        writeSkills(filesystemRoot, "filesystem_skill", 6);
        writeSkills(workspaceRoot, "workspace_skill", 6);
        SkillRepository classpathRepository = new ClasspathSkillRepository("issue196/skills");
        List<RefreshSample> samples = new ArrayList<>();
        samples.add(measureRefresh("classpath", List.of(classpathRepository), 20));
        samples.add(
                measureRefresh(
                        "filesystem", List.of(new FileSystemSkillRepository(filesystemRoot)), 20));
        samples.add(
                measureRefresh(
                        "workspace",
                        List.of(new WorkspaceSkillRepository(workspaceRoot, "perf-user")),
                        20));
        samples.add(
                measureRefresh(
                        "three-tier",
                        List.of(
                                classpathRepository,
                                new FileSystemSkillRepository(filesystemRoot),
                                new WorkspaceSkillRepository(workspaceRoot, "perf-user")),
                        20));
        writeSummary("SKILL REPOSITORY REFRESH", refreshLines(samples));
        assertThat(samples).hasSize(4);
        assertThat(samples)
                .allSatisfy(
                        sample -> {
                            assertThat(sample.requestCount()).isEqualTo(20);
                            assertThat(sample.meanMicros()).isPositive();
                        });
    }

    @Test
    @Order(2)
    void realLlmTokenAndLatencyAcrossConcurrency() throws Exception {
        List<ToolInfo> fullTools = buildAllTools();
        List<ToolInfo> filteredTools = buildFilteredTools(fullTools);
        assertThat(filteredTools.size()).isLessThan(fullTools.size());
        List<Sample> samples = new ArrayList<>();
        List<String> summaryLines = new ArrayList<>();
        summaryLines.add("model=" + modelName);
        summaryLines.add("rounds=" + rounds);
        summaryLines.add("provider_max_parallel_requests=" + maxParallelRequests);
        summaryLines.add("task_count=" + TASKS.size());
        summaryLines.add("local_clock=" + LocalDateTime.now());
        summaryLines.add("context_stats.tool_tokens");
        summaryLines.add("full," + fullTools.size() + "," + contextToolTokens(fullTools));
        summaryLines.add(
                "filtered," + filteredTools.size() + "," + contextToolTokens(filteredTools));
        summaryLines.add("");
        summaryLines.add(
                "mode,concurrency,requests,prompt_tokens_avg,prompt_tokens_p50,prompt_tokens_p95,"
                    + "first_token_ms_avg,first_token_ms_p50,first_token_ms_p95,total_ms_avg,total_ms_p50,"
                    + "total_ms_p95,tool_call_rate");

        for (String mode : List.of("full", "filtered")) {
            List<ToolInfo> tools = "full".equals(mode) ? fullTools : filteredTools;
            for (Integer concurrency : concurrencyLevels) {
                for (int round = 1; round <= rounds; round++) {
                    samples.addAll(runRound(mode, tools, concurrency, round));
                }
                summaryLines.add(
                        scenarioSummary(
                                mode,
                                concurrency,
                                samplesFor(samples, mode, concurrency),
                                contextToolTokens(tools)));
            }
        }

        writeRawSamples(samples);
        summaryLines.add("");
        summaryLines.add("assertion,filtered_avg_prompt_tokens_lt_full");
        summaryLines.add("full_avg_prompt_tokens=" + averagePromptTokens(samples, "full"));
        summaryLines.add("filtered_avg_prompt_tokens=" + averagePromptTokens(samples, "filtered"));
        writeSummary("REAL LLM CONCURRENCY", summaryLines);
        assertThat(samples).isNotEmpty();
        assertThat(averagePromptTokens(samples, "filtered"))
                .isLessThan(averagePromptTokens(samples, "full"));
    }

    private List<Sample> runRound(String mode, List<ToolInfo> tools, int concurrency, int round)
            throws IOException, ExecutionException, InterruptedException, TimeoutException {
        List<Sample> samples = new ArrayList<>();
        AtomicInteger failures = new AtomicInteger();
        int providerParallelism = Math.min(concurrency, maxParallelRequests);
        ExecutorService executor =
                new ThreadPoolExecutor(
                        providerParallelism,
                        providerParallelism,
                        0L,
                        TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<>());
        try {
            List<Future<Sample>> futures = new ArrayList<>();
            for (int requestIndex = 0; requestIndex < concurrency; requestIndex++) {
                Task task = TASKS.get(requestIndex % TASKS.size());
                int requestNumber = requestIndex + 1;
                futures.add(
                        executor.submit(
                                (Callable<Sample>)
                                        () -> {
                                            try {
                                                return invokeStreaming(
                                                        task,
                                                        tools,
                                                        mode,
                                                        concurrency,
                                                        round,
                                                        requestNumber);
                                            } catch (IOException exception) {
                                                failures.incrementAndGet();
                                                throw exception;
                                            }
                                        }));
            }
            for (Future<Sample> future : futures) {
                samples.add(future.get(5, TimeUnit.MINUTES));
            }
        } finally {
            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
        assertThat(failures.get()).isZero();
        assertThat(samples).hasSize(concurrency);
        return samples;
    }

    private Sample invokeStreaming(
            Task task,
            List<ToolInfo> tools,
            String mode,
            int concurrency,
            int round,
            int requestIndex)
            throws Exception {
        List<BaseMessage> messages =
                List.of(
                        new SystemMessage(
                                "You are a tool routing benchmark. Select exactly one tool and do"
                                        + " not answer in prose."),
                        new UserMessage(task.prompt()));
        ModelInvokeOptions options =
                ModelInvokeOptions.builder()
                        .tools(
                                tools.stream()
                                        .map(TCPRF03RealLlmPerformanceTest::toOpenAiFormat)
                                        .toList())
                        .temperature(0.0F)
                        .topP(0.1F)
                        .maxTokens(128)
                        .timeout(120.0F)
                        .build();
        long start = System.nanoTime();
        long firstTokenNanos = 0L;
        AssistantMessageChunk merged = null;
        Iterator<AssistantMessageChunk> iterator = modelClient.stream(messages, options);
        try {
            while (iterator.hasNext()) {
                AssistantMessageChunk chunk = iterator.next();
                if (firstTokenNanos == 0L && isContentChunk(chunk)) {
                    firstTokenNanos = System.nanoTime() - start;
                }
                merged = merged == null ? chunk : merged.merge(chunk);
            }
        } finally {
            if (iterator instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
        long totalNanos = System.nanoTime() - start;
        UsageMetadata usage = merged == null ? null : merged.getUsageMetadata();
        return new Sample(
                mode,
                concurrency,
                round,
                requestIndex,
                task.expectedTool(),
                selectedTool(merged),
                usage == null ? 0 : usage.getInputTokens(),
                usage == null ? 0 : usage.getOutputTokens(),
                firstTokenNanos / 1_000_000L,
                totalNanos / 1_000_000L);
    }

    private static boolean isContentChunk(AssistantMessageChunk chunk) {
        return chunk != null
                && (isNonEmptyText(chunk.getContent())
                        || isNonEmptyText(chunk.getReasoningContent())
                        || (chunk.getToolCalls() != null && !chunk.getToolCalls().isEmpty()));
    }

    private static String selectedTool(AssistantMessageChunk merged) {
        if (merged == null || merged.getToolCalls() == null || merged.getToolCalls().isEmpty()) {
            return "";
        }
        return String.valueOf(merged.getToolCalls().get(0).getName());
    }

    private static boolean isNonEmptyText(Object value) {
        return value instanceof String text && !text.isEmpty();
    }

    private static int contextToolTokens(List<ToolInfo> tools) {
        SessionModelContext.ToolTokenCounterPort counter =
                new SessionModelContext.ToolTokenCounterPort() {
                    @Override
                    public int countTokens(List<BaseMessage> messages) {
                        return messages.size();
                    }

                    @Override
                    public int countTools(List<ToolInfo> toolInfos) {
                        return toolInfos.stream()
                                .mapToInt(TCPRF03RealLlmPerformanceTest::estimateToolTokens)
                                .sum();
                    }
                };
        SessionModelContext context =
                new SessionModelContext(
                        "tcprf03",
                        "tcprf03",
                        new ContextEngineConfig(),
                        List.of(),
                        List.of(),
                        counter);
        ContextWindow window =
                context.getContextWindow(List.of(), tools, null, null, Map.of())
                        .toCompletableFuture()
                        .join();
        return window.getStatistic().getToolTokens();
    }

    private static int estimateToolTokens(ToolInfo tool) {
        String parameters = String.valueOf(tool.getParameters());
        return (String.valueOf(tool.getName()).length()
                        + String.valueOf(tool.getDescription()).length()
                        + parameters.length())
                / 4;
    }

    private RefreshSample measureRefresh(
            String scenario, List<SkillRepository> repositories, int iterations) {
        SkillManager manager = new SkillManager("tcprf03-refresh");
        manager.setRepositories(repositories);
        for (int warmUp = 0; warmUp < 5; warmUp++) {
            manager.refreshIncrementally(List.of());
        }
        long[] elapsedMicros = new long[iterations];
        for (int index = 0; index < iterations; index++) {
            long start = System.nanoTime();
            manager.refreshIncrementally(List.of());
            elapsedMicros[index] = (System.nanoTime() - start) / 1_000L;
        }
        return new RefreshSample(
                scenario,
                iterations,
                manager.count(),
                mean(elapsedMicros),
                percentile(elapsedMicros, 0.50D),
                percentile(elapsedMicros, 0.95D));
    }

    private static void writeSkills(Path root, String prefix, int count) throws IOException {
        for (int index = 1; index <= count; index++) {
            Path skillDirectory = root.resolve(prefix + "_" + index);
            Files.createDirectories(skillDirectory);
            Files.writeString(
                    skillDirectory.resolve("SKILL.md"),
                    """
                    ---
                    name: %s_%d
                    description: Performance fixture skill %d.
                    ---
                    Skill body.
                    """
                            .formatted(prefix, index, index),
                    StandardCharsets.UTF_8);
        }
    }

    private static List<Sample> samplesFor(List<Sample> samples, String mode, int concurrency) {
        return samples.stream()
                .filter(sample -> sample.mode().equals(mode) && sample.concurrency() == concurrency)
                .toList();
    }

    private static double averagePromptTokens(List<Sample> samples, String mode) {
        return samples.stream()
                .filter(sample -> sample.mode().equals(mode))
                .mapToLong(Sample::promptTokens)
                .average()
                .orElse(0.0D);
    }

    private static String scenarioSummary(
            String mode, int concurrency, List<Sample> samples, int contextToolTokens) {
        long[] promptTokens = samples.stream().mapToLong(Sample::promptTokens).toArray();
        long[] firstToken = samples.stream().mapToLong(Sample::firstTokenMillis).toArray();
        long[] total = samples.stream().mapToLong(Sample::totalMillis).toArray();
        long toolCalls =
                samples.stream().filter(sample -> !sample.selectedTool().isBlank()).count();
        return "%s,%d,%d,%.1f,%d,%d,%.1f,%d,%d,%.1f,%d,%d,%d%%"
                .formatted(
                        mode,
                        concurrency,
                        samples.size(),
                        mean(promptTokens),
                        percentile(promptTokens, 0.50D),
                        percentile(promptTokens, 0.95D),
                        mean(firstToken),
                        percentile(firstToken, 0.50D),
                        percentile(firstToken, 0.95D),
                        mean(total),
                        percentile(total, 0.50D),
                        percentile(total, 0.95D),
                        BigDecimal.valueOf(toolCalls)
                                .multiply(BigDecimal.valueOf(100))
                                .divide(BigDecimal.valueOf(samples.size()), 0, RoundingMode.HALF_UP)
                                .intValue());
    }

    private static List<String> refreshLines(List<RefreshSample> samples) {
        List<String> lines =
                new ArrayList<>(
                        List.of("scenario,skills,iterations,mean_micros,p50_micros,p95_micros"));
        samples.forEach(
                sample ->
                        lines.add(
                                "%s,%d,%d,%.1f,%d,%d"
                                        .formatted(
                                                sample.scenario(),
                                                sample.skills(),
                                                sample.requestCount(),
                                                sample.meanMicros(),
                                                sample.p50Micros(),
                                                sample.p95Micros())));
        return lines;
    }

    private static void writeRawSamples(List<Sample> samples) throws IOException {
        try (PrintWriter writer =
                new PrintWriter(
                        Files.newBufferedWriter(
                                RAW_LOG,
                                StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING))) {
            writer.println(
                    "mode,concurrency,round,request,expected_tool,selected_tool,prompt_tokens,"
                            + "completion_tokens,first_token_ms,total_ms");
            samples.forEach(
                    sample ->
                            writer.println(
                                    "%s,%d,%d,%d,%s,%s,%d,%d,%d,%d"
                                            .formatted(
                                                    sample.mode(),
                                                    sample.concurrency(),
                                                    sample.round(),
                                                    sample.requestIndex(),
                                                    sample.expectedTool(),
                                                    sample.selectedTool(),
                                                    sample.promptTokens(),
                                                    sample.completionTokens(),
                                                    sample.firstTokenMillis(),
                                                    sample.totalMillis())));
        }
    }

    private static void writeSummary(String title, List<String> lines) throws IOException {
        Files.createDirectories(SUMMARY_LOG.getParent());
        try (PrintWriter writer =
                new PrintWriter(
                        Files.newBufferedWriter(
                                SUMMARY_LOG,
                                StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.APPEND))) {
            writer.println("=== " + title + " ===");
            lines.forEach(writer::println);
            writer.println();
        }
    }

    private static List<Integer> parseConcurrencyLevels(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_CONCURRENCIES;
        }
        List<Integer> levels =
                Arrays.stream(value.split(","))
                        .map(String::trim)
                        .filter(part -> !part.isEmpty())
                        .map(Integer::valueOf)
                        .distinct()
                        .toList();
        Assumptions.assumeTrue(!levels.isEmpty(), "TC_PRF03_CONCURRENCIES is empty");
        return levels;
    }

    private static int integerEnvironment(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        int parsed = Integer.parseInt(value);
        if (parsed <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return parsed;
    }

    private static double mean(long[] values) {
        return Arrays.stream(values).average().orElse(0.0D);
    }

    private static long percentile(long[] values, double percentile) {
        if (values.length == 0) {
            return 0L;
        }
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        int index = (int) Math.ceil(percentile * sorted.length) - 1;
        return sorted[Math.min(Math.max(index, 0), sorted.length - 1)];
    }

    private static Map<String, Object> toOpenAiFormat(ToolInfo toolInfo) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", toolInfo.getName());
        function.put("description", toolInfo.getDescription());
        function.put("parameters", toolInfo.getParameters());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "function");
        result.put("function", function);
        return result;
    }

    private static List<ToolInfo> buildAllTools() {
        List<ToolInfo> tools = new ArrayList<>();
        tools.add(toolInfo("tool_search", "Search the web for information."));
        tools.add(toolInfo("tool_chart", "Draw charts and graphs from data."));
        tools.add(toolInfo("tool_delete_file", "Delete a file from the filesystem."));
        tools.add(toolInfo("tool_translate", "Translate text between languages."));
        tools.add(toolInfo("tool_calculator", "Perform mathematical calculations."));
        tools.add(toolInfo("tool_read_file", "Read the contents of a file."));
        tools.add(toolInfo("tool_send_email", "Send an email to a recipient."));
        tools.add(toolInfo("tool_list_files", "List files in a directory."));
        tools.add(toolInfo("tool_extract_pdf", "Extract text from PDF documents."));
        tools.add(toolInfo("tool_create_issue", "Create a new GitHub issue."));
        for (int index = 10; index < TOOL_COUNT; index++) {
            tools.add(toolInfo("tool_" + index, "Generic tool " + index + " for padding."));
        }
        return tools;
    }

    private static List<ToolInfo> buildFilteredTools(List<ToolInfo> allTools) {
        List<String> expected = TASKS.stream().map(Task::expectedTool).toList();
        return allTools.stream()
                .filter(
                        tool ->
                                expected.contains(tool.getName())
                                        || "tool_10".equals(tool.getName())
                                        || "tool_11".equals(tool.getName()))
                .toList();
    }

    private static ToolInfo toolInfo(String name, String description) {
        return ToolInfo.builder()
                .type("function")
                .name(name)
                .description(description)
                .parameters(Map.of("type", "object", "properties", Map.of(), "required", List.of()))
                .build();
    }

    private record Task(String prompt, String expectedTool) {}

    private record Sample(
            String mode,
            int concurrency,
            int round,
            int requestIndex,
            String expectedTool,
            String selectedTool,
            long promptTokens,
            long completionTokens,
            long firstTokenMillis,
            long totalMillis) {}

    private record RefreshSample(
            String scenario,
            int requestCount,
            int skills,
            double meanMicros,
            long p50Micros,
            long p95Micros) {}
}
