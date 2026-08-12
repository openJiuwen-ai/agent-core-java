/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.context_evolver;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.ContextUtils;
import com.openjiuwen.core.context.processor.compressor.MicroCompactProcessor;
import com.openjiuwen.core.context.processor.compressor.MicroCompactProcessorConfig;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.singleagent.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import examples.reac_agent.ExampleApiConfigLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Benchmark harness for {@link MicroCompactProcessor} + KV cache release.
 * <p>
 * Design goals (so ON vs OFF shows a measurable gap):
 * <ol>
 *   <li><b>Large tool results</b> — a local mock tool returns ~8 KB of
 *       content per call, so each round occupies many KV blocks.</li>
 *   <li><b>Multiple rounds per session</b> — MicroCompactProcessor clears
 *       the previous tool result before the next round, producing a message
 *       diff that triggers {@code KVCacheManager.release} when the model
 *       supports it.</li>
 *   <li><b>Concurrent sessions</b> — several workers run in parallel to
 *       keep the vLLM batch busy and the KV pool under pressure.</li>
 *   <li><b>Client-side metrics</b> — per-round wall time and a final
 *       summary let ON/OFF runs be compared directly.</li>
 * </ol>
 *
 * <p>Provider choice (from {@code apiconfig.json}) drives the experiment:
 * <ul>
 *   <li>{@code InferenceAffinity} — release ON (hits
 *       {@code /release_kv_cache} on vLLM).</li>
 *   <li>{@code OpenAI} — release OFF (control group).</li>
 * </ul>
 *
 * @since 2026-08-08
 */
final class MicroCompactProcessorKvCacheExampleSupport {

    private static final String AGENT_ID = "micro_compact_kv_cache_example";
    private static final String TOOL_ID = "micro_compact_kv_cache_weather_tool";
    private static final String TOOL_NAME = "WeatherReporter";
    private static final String CLEARED_MARKER = "[Old tool result content cleared]";

    private static final String SYSTEM_PROMPT = "You are a helpful assistant. "
            + "When the user asks about weather, call the weather tool first, "
            + "then answer concisely based on the tool result.";

    /** Number of concurrent worker sessions. Each has its own conversation id. */
    private static final int WORKER_COUNT = 16;

    /** Number of weather-query rounds each worker performs. */
    private static final int ROUNDS_PER_WORKER = 10;

    /** Approximate size (chars) of the mock tool result payload. */
    private static final int TOOL_RESULT_CHARS = 48 * 1024;

    private static final List<String> QUERY_POOL = List.of(
            "北京天气如何",
            "上海天气如何",
            "广州天气如何",
            "深圳天气如何",
            "杭州天气如何",
            "成都天气如何",
            "武汉天气如何",
            "西安天气如何",
            "南京天气如何",
            "重庆天气如何",
            "天津天气如何",
            "长沙天气如何",
            "苏州天气如何",
            "厦门天气如何",
            "青岛天气如何"
    );

    private MicroCompactProcessorKvCacheExampleSupport() {
    }

    static void run(String[] args) throws Exception {
        boolean sslVerify = ExampleApiConfigLoader.getSslVerify();
        System.setProperty("RESTFUL_SSL_VERIFY", String.valueOf(sslVerify));

        String provider = ExampleApiConfigLoader.getModelProvider();
        boolean releaseEnabled = provider.equalsIgnoreCase("InferenceAffinity");
        System.out.println();
        System.out.println("=====================================================");
        System.out.println("KV cache release benchmark");
        System.out.println("  provider            = " + provider);
        System.out.println("  model_name          = " + ExampleApiConfigLoader.getModelName());
        System.out.println("  api_base            = " + ExampleApiConfigLoader.getApiBase());
        System.out.println("  enableKvCacheRelease= " + releaseEnabled);
        System.out.println("  workers             = " + WORKER_COUNT);
        System.out.println("  rounds_per_worker   = " + ROUNDS_PER_WORKER);
        System.out.println("  tool_result_chars   = " + TOOL_RESULT_CHARS);
        System.out.println("=====================================================");

        Tool weatherTool = null;
        ReActAgent[] workerAgents = new ReActAgent[WORKER_COUNT];
        try {
            weatherTool = createWeatherTool();
            for (int w = 0; w < WORKER_COUNT; w++) {
                workerAgents[w] = createAgent(releaseEnabled);
                registerTool(workerAgents[w], weatherTool);
            }

            ThreadPoolExecutor pool = new ThreadPoolExecutor(
                    WORKER_COUNT,
                    WORKER_COUNT,
                    60L, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(WORKER_COUNT),
                    r -> {
                        Thread t = new Thread(r, "kv-demo-worker");
                        t.setDaemon(true);
                        return t;
                    },
                    new ThreadPoolExecutor.CallerRunsPolicy()
            );

            long overallStart = System.nanoTime();
            List<java.util.concurrent.Future<WorkerResult>> futures = new ArrayList<>(WORKER_COUNT);
            for (int w = 0; w < WORKER_COUNT; w++) {
                final int workerId = w;
                final ReActAgent agent = workerAgents[w];
                final String conversationId = "kv_demo_w" + workerId;
                futures.add(pool.submit(() -> runWorker(agent, workerId, conversationId)));
            }

            long totalRoundNanos = 0L;
            long maxRoundNanos = 0L;
            int completedRounds = 0;
            int failedRounds = 0;
            for (java.util.concurrent.Future<WorkerResult> future : futures) {
                WorkerResult wr;
                try {
                    wr = future.get();
                } catch (Exception e) {
                    System.out.println("worker failed: " + e.getMessage());
                    failedRounds += ROUNDS_PER_WORKER;
                    continue;
                }
                totalRoundNanos += wr.roundNanosSum;
                maxRoundNanos = Math.max(maxRoundNanos, wr.maxRoundNanos);
                completedRounds += wr.completedRounds;
                failedRounds += wr.failedRounds;
            }
            long overallNanos = System.nanoTime() - overallStart;

            pool.shutdown();
            try {
                pool.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            System.out.println();
            System.out.println("=====================================================");
            System.out.println("Benchmark summary (provider=" + provider
                    + ", release=" + releaseEnabled + ")");
            System.out.println("  workers             = " + WORKER_COUNT);
            System.out.println("  completed_rounds    = " + completedRounds);
            System.out.println("  failed_rounds        = " + failedRounds);
            System.out.printf("  total_wall_seconds   = %.3f%n",
                    overallNanos / 1_000_000_000.0);
            System.out.printf("  sum_round_seconds    = %.3f%n",
                    totalRoundNanos / 1_000_000_000.0);
            System.out.printf("  max_round_seconds    = %.3f%n",
                    maxRoundNanos / 1_000_000_000.0);
            System.out.printf("  avg_round_seconds    = %.3f%n",
                    completedRounds == 0 ? 0.0
                            : (totalRoundNanos / 1_000_000_000.0) / completedRounds);
            System.out.println("=====================================================");
            System.out.println("Compare with the other provider: rerun this demo");
            System.out.println("with MODEL_PROVIDER=OpenAI (release OFF) and");
            System.out.println("MODEL_PROVIDER=InferenceAffinity (release ON).");
            System.out.println("Watch vLLM log for:");
            System.out.println("  - Prefix cache hit rate");
            System.out.println("  - GPU KV cache usage");
            System.out.println("  - [jiuwen-kv] 3-release / 4-evict lines (ON only)");
        } finally {
            if (weatherTool != null) {
                Runner.resourceMgr().removeTool(
                        weatherTool.getCard().getId(), AGENT_ID, TagMatchStrategy.ALL, true);
            }
            for (int w = 0; w < WORKER_COUNT; w++) {
                Runner.release("kv_demo_w" + w);
            }
            Runner.stop();
        }
    }

    private static ReActAgent createAgent(boolean releaseEnabled) {
        AgentCard agentCard = AgentCard.builder()
                .id(AGENT_ID)
                .name(AGENT_ID)
                .description("MicroCompactProcessor + KV cache release benchmark")
                .build();

        ReActAgent agent = new ReActAgent(agentCard);

        MicroCompactProcessorConfig compressorConfig = MicroCompactProcessorConfig.builder()
                .triggerThreshold(1)
                .keepRecentPerTool(1)
                .compactableToolNames(List.of(TOOL_NAME))
                .build();

        ContextEngineConfig contextEngineConfig = ContextEngineConfig.builder()
                .maxContextMessageNum(200)
                .defaultWindowRoundNum(10)
                .enableReload(false)
                .enableKvCacheRelease(releaseEnabled)
                .build();

        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of("role", "system", "content", SYSTEM_PROMPT)))
                .maxIterations(3)
                .build()
                .configureModelClient(
                        ExampleApiConfigLoader.getModelProvider(),
                        ExampleApiConfigLoader.getApiKey(),
                        ExampleApiConfigLoader.getApiBase(),
                        ExampleApiConfigLoader.getModelName(),
                        ExampleApiConfigLoader.getSslVerify()
                )
                .configureContextProcessors(List.of(
                        new ContextEngine.ProcessorSpec("MicroCompactProcessor", compressorConfig)));
        config.setContextEngineConfig(contextEngineConfig);

        ModelRequestConfig requestConfig = config.getModelConfigObj();
        if (requestConfig != null) {
            requestConfig.setTemperature(0.6);
            requestConfig.setTopP(0.8);
            requestConfig.setMaxTokens(64);
        }

        agent.configure(config);
        return agent;
    }

    private static Tool createWeatherTool() {
        ToolCard card = ToolCard.builder()
                .id(TOOL_ID)
                .name(TOOL_NAME)
                .description("天气查询插件，输入 city 获取实时天气（mock 返回大 result 用于 KV 压力测试）")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "city", Map.of(
                                        "type", "string",
                                        "description", "城市名称，支持中文和英文"
                                )
                        ),
                        "required", List.of("city")
                ))
                .build();
        return new LocalFunction(card, inputs -> {
            String city = String.valueOf(inputs.getOrDefault("city", "unknown"));
            return buildMockWeatherResult(city, TOOL_RESULT_CHARS);
        });
    }

    private static String buildMockWeatherResult(String city, int targetChars) {
        StringBuilder sb = new StringBuilder(targetChars + 256);
        sb.append("城市:").append(city).append("; 天气:晴; 温度:23C; 湿度:55%; 风力:东南风3级; ");
        sb.append("空气质量:优; 紫外线指数:中等; 体感温度:25C; ");
        sb.append("未来三小时预报: 晴转多云, 偏南风2-3级, 最高气温25C; ");
        sb.append("detail_section_begin: ");
        int baseLen = sb.length();
        int i = 0;
        while (sb.length() < targetChars + baseLen) {
            sb.append("seg").append(i).append("=temp=").append(20 + (i % 10))
                    .append(",hum=").append(50 + (i % 20))
                    .append(",wind=").append(2 + (i % 5))
                    .append("; ");
            i++;
        }
        sb.append(" detail_section_end. summary_end.");
        return sb.toString();
    }

    private static void registerTool(ReActAgent agent, Tool tool) {
        Runner.resourceMgr().removeTool(tool.getCard().getId(), AGENT_ID, TagMatchStrategy.ALL, true);
        Runner.resourceMgr().addTool(tool, AGENT_ID);
        agent.getAbilityManager().add(tool.getCard());
    }

    private static WorkerResult runWorker(ReActAgent agent, int workerId, String conversationId) {
        WorkerResult wr = new WorkerResult();
        for (int round = 0; round < ROUNDS_PER_WORKER; round++) {
            String query = QUERY_POOL.get(round % QUERY_POOL.size());
            long startNanos = System.nanoTime();
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) Runner.runAgent(
                        agent,
                        Map.of("query", query, "conversation_id", conversationId),
                        null,
                        null
                );
                long elapsedNanos = System.nanoTime() - startNanos;
                wr.roundNanosSum += elapsedNanos;
                wr.maxRoundNanos = Math.max(wr.maxRoundNanos, elapsedNanos);
                wr.completedRounds++;
                System.out.printf("[w%d r%d] %.3fs output=%s%n",
                        workerId, round + 1,
                        elapsedNanos / 1_000_000_000.0,
                        abbreviate(String.valueOf(result.get("output")), 60));
                printContextSnapshot(agent, conversationId, workerId);
            } catch (Exception e) {
                wr.failedRounds++;
                System.out.printf("[w%d r%d] ERROR %s%n", workerId, round + 1, e.getMessage());
            }
        }
        return wr;
    }

    private static void printContextSnapshot(ReActAgent agent, String conversationId, int workerId) {
        ModelContext context = agent.getContextEngine().getContext(null, conversationId);
        if (context == null) {
            System.out.println("[w" + workerId + " context] no context found for session " + conversationId);
            return;
        }

        List<BaseMessage> messages = context.getMessages();
        int tokenSum = 0;
        int clearedCount = 0;
        int activeToolCount = 0;
        for (BaseMessage message : messages) {
            tokenSum += ContextUtils.estimateMessageTokens(message);
            if (message instanceof ToolMessage toolMessage) {
                String content = toolMessage.getContentAsString();
                if (CLEARED_MARKER.equals(content)) {
                    clearedCount++;
                } else {
                    activeToolCount++;
                }
            }
        }

        System.out.printf("[w%d context] messages=%d, tokens~=%d, active_tool=%d, cleared_tool=%d%n",
                workerId, messages.size(), tokenSum, activeToolCount, clearedCount);
    }

    private static String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static final class WorkerResult {
        private long roundNanosSum;
        private long maxRoundNanos;
        private int completedRounds;
        private int failedRounds;
    }
}
