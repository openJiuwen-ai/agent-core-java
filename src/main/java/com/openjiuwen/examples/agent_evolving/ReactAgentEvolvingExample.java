/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.agent_evolving;

import com.openjiuwen.agent_evolving.TuneConstant;
import com.openjiuwen.agent_evolving.dataset.Case;
import com.openjiuwen.agent_evolving.dataset.CaseLoader;
import com.openjiuwen.agent_evolving.evaluator.DefaultEvaluator;
import com.openjiuwen.agent_evolving.optimizer.llm_call.InstructionOptimizer;
import com.openjiuwen.agent_evolving.trainer.Trainer;
import com.openjiuwen.agent_evolving.updater.SingleDimUpdater;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.ReActAgentEvolve;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Self-Evolving ReAct Agent Example.
 *
 * <p>This example demonstrates how to use ReActAgentEvolve with agent_evolving for
 * self-evolving training, including:</p>
 * <ul>
 *   <li>Creating ReActAgentEvolve with configurable LLM client</li>
 *   <li>Preparing training and validation datasets</li>
 *   <li>Configuring evaluator and instruction optimizer</li>
 *   <li>Training with checkpoint save/resume capability</li>
 *   <li>Testing the evolved agent with inference</li>
 * </ul>
 *
 * <p>Prerequisites:</p>
 * <ul>
 *   <li>Configure LLM API credentials via environment variables</li>
 * </ul>
 *
 * <p>Mirrors Python's {@code react_agent_evolving.py} in {@code examples/agent_evolving}.</p>
 */
public class ReactAgentEvolvingExample {

    // Configuration from environment (modify according to your environment)
    private static final String MODEL_PROVIDER = getenvOrDefault("MODEL_PROVIDER", "your model provider");
    private static final String API_BASE = getenvOrDefault("API_BASE", "your api base");
    private static final String API_KEY = getenvOrDefault("API_KEY", "your api key");
    private static final String MODEL_NAME = getenvOrDefault("MODEL_NAME", "your model name");
    private static final double MODEL_TEMPERATURE = Double.parseDouble(getenvOrDefault("MODEL_TEMPERATURE", "0.3"));
    private static final double MODEL_TOP_P = Double.parseDouble(getenvOrDefault("MODEL_TOP_P", "0.9"));
    private static final int MODEL_TIMEOUT = Integer.parseInt(getenvOrDefault("MODEL_TIMEOUT", "120"));

    /**
     * Create a ReActAgentEvolve instance.
     *
     * @param systemPrompt Initial system prompt for instruction optimization
     * @param agentId      Unique agent identifier
     * @return ReActAgentEvolve instance
     */
    public static ReActAgentEvolve createReactAgent(String systemPrompt, String agentId) {
        AgentCard agentCard = AgentCard.builder()
                .id(agentId)
                .name(titleCase(agentId))
                .description("A self-evolving agent with instruction optimization")
                .build();

        // Configure prompt template
        List<Map<String, String>> promptTemplate = new ArrayList<>();
        Map<String, String> systemMsg = new LinkedHashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        promptTemplate.add(systemMsg);

        Map<String, String> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", "{{query}}");
        promptTemplate.add(userMsg);

        ReActAgentEvolve agent = new ReActAgentEvolve(agentCard);

        ReActAgentConfig config = new ReActAgentConfig();
        config.configureModelClient(MODEL_PROVIDER, API_KEY, API_BASE, MODEL_NAME, false);
        config.configurePromptTemplate(promptTemplate);
        config.configureMaxIterations(TuneConstant.DEFAULT_ITERATION_NUM);
        agent.configure(config);

        return agent;
    }

    /**
     * Create QA dataset for training and validation.
     *
     * <p>The optimizer will use these cases to learn and improve the system prompt
     * through instruction optimization.</p>
     *
     * @return CaseLoader with sample QA pairs
     */
    public static CaseLoader createQaCases() {
        List<Case> cases = new ArrayList<>();

        cases.add(Case.builder()
                .inputs(Map.of("query", "什么是机器学习？"))
                .label(Map.of("answer", "机器学习是人工智能的一个分支，通过算法从数据中学习规律。"))
                .build());

        cases.add(Case.builder()
                .inputs(Map.of("query", "Python 如何读取文件？"))
                .label(Map.of("answer", "使用 open() 函数，例如：with open('file.txt', 'r') as f: content = f.read()"))
                .build());

        cases.add(Case.builder()
                .inputs(Map.of("query", "水的化学式是什么？"))
                .label(Map.of("answer", "水的化学式是 H₂O，由两个氢原子和一个氧原子组成。"))
                .build());

        cases.add(Case.builder()
                .inputs(Map.of("query", "光速大约是多少？"))
                .label(Map.of("answer", "光速在真空中约为每秒 30 万公里，即 3×10⁸ 米/秒。"))
                .build());

        cases.add(Case.builder()
                .inputs(Map.of("query", "地球的直径是多少？"))
                .label(Map.of("answer", "地球的平均直径约为 12,742 公里。"))
                .build());

        return new CaseLoader(cases);
    }

    /**
     * Test evolved agent with sample queries.
     *
     * @param agent      ReActAgentEvolve instance (with optimized system prompt)
     * @param testQueries List of query dictionaries
     */
    public static void testAgent(ReActAgentEvolve agent, List<Map<String, Object>> testQueries) {
        Loggers.AGENT.info("\n[test] Testing evolved agent with optimized prompt...");
        for (Map<String, Object> query : testQueries) {
            try {
                CompletableFuture<Map<String, Object>> future = CompletableFuture.supplyAsync(() -> {
                    Object conversationId = query.getOrDefault("conversation_id", "default_session");
                    Object invokeResult = agent.invoke(query, new AgentSessionApi(String.valueOf(conversationId)));
                    return normalizeInvokeResult(invokeResult);
                });
                Map<String, Object> result = future.get();
                Loggers.AGENT.info("\n[query] {}", query.get("query"));
                Loggers.AGENT.info("[answer] {}", result.getOrDefault("output", result));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Loggers.AGENT.error("[error] Failed to invoke agent: {}", e.getMessage());
            } catch (ExecutionException e) {
                Loggers.AGENT.error("[error] Failed to invoke agent: {}", e.getMessage());
            }
        }
    }

    /**
     * Run the complete self-evolving agent workflow.
     */
    public static void main(String[] args) {
        // =========================================================
        // 1. Create ReActAgentEvolve with initial system prompt
        // =========================================================
        // The optimizer will improve this prompt based on training feedback
        String initialPrompt = "你是一个 helpful 的 AI 助手。\n" +
                "请直接回答用户的问题，如果需要可以使用工具来辅助回答。";

        ReActAgentEvolve agent = createReactAgent(initialPrompt, "react_agent_evolving");

        Loggers.AGENT.info("[agent] ReActAgentEvolve created with ID: {}", agent.getCard().getId());

        // =========================================================
        // 2. Prepare Dataset
        // =========================================================
        CaseLoader[] split = createQaCases().split(0.6, 42);
        CaseLoader trainLoader = split[0];
        CaseLoader valLoader = split[1];
        Loggers.AGENT.info("[data] train: {}, val: {}", trainLoader.size(), valLoader.size());

        // =========================================================
        // 3. Configure Model, Evaluator, and Instruction Optimizer
        // =========================================================
        ModelRequestConfig modelConfig = ModelRequestConfig.builder()
                .modelName(MODEL_NAME)
                .temperature(MODEL_TEMPERATURE)
                .maxTokens(1000)
                .topP(MODEL_TOP_P)
                .build();

        ModelClientConfig modelClientConfig = ModelClientConfig.builder()
                .clientProvider(MODEL_PROVIDER)
                .apiKey(API_KEY)
                .apiBase(API_BASE)
                .timeout(MODEL_TIMEOUT)
                .verifySsl(false)
                .build();

        // Evaluator scores model outputs against expected answers
        DefaultEvaluator evaluator = new DefaultEvaluator(modelConfig, modelClientConfig, "");

        // InstructionOptimizer improves system_prompt and user_prompt based on gradients
        InstructionOptimizer optimizer = new InstructionOptimizer(modelConfig, modelClientConfig);

        // =========================================================
        // 4. Configure Updater and Trainer
        // =========================================================
        Path ckptDir = Paths.get(System.getProperty("user.dir"), ".checkpoints");
        new File(ckptDir.toString()).mkdirs();

        // SingleDimUpdater wraps optimizer to generate Updates from trajectories
        SingleDimUpdater updater = new SingleDimUpdater(optimizer);

        Trainer trainer = new Trainer.Builder()
                .updater(updater)
                .evaluator(evaluator)
                .numParallel(2)
                .earlyStopScore(0.95)
                .checkpointDir(ckptDir.toString())
                .resumeFrom(Paths.get(ckptDir.toString(), "latest.json").toString())
                .checkpointEveryNEpochs(1)
                .checkpointOnImprove(true)
                .build();

        // =========================================================
        // 5. Train (with checkpoint/resume)
        // =========================================================
        Loggers.AGENT.info("\n[info] Starting self-evolving training with instruction optimization...");
        Object evolvedAgent = trainer.train(agent, trainLoader, valLoader, 3, new LinkedHashMap<>());
        Loggers.AGENT.info("[done] Training finished. Checkpoints saved.");

        // =========================================================
        // 6. Test Inference
        // =========================================================
        List<Map<String, Object>> testQueries = new ArrayList<>();
        testQueries.add(Map.of("query", "请介绍一下机器学习的基本概念。"));
        testQueries.add(Map.of("query", "Python 怎么写文件？"));

        testAgent((ReActAgentEvolve) evolvedAgent, testQueries);
    }

    // Helper methods

    private static String getenvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }

    private static String titleCase(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private static Map<String, Object> normalizeInvokeResult(Object result) {
        if (result instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return normalized;
        }
        return Map.of("output", result);
    }
}
