/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.agent_evolving;

import com.openjiuwen.agent_evolving.TuneConstant;
import com.openjiuwen.agent_evolving.dataset.Case;
import com.openjiuwen.agent_evolving.dataset.CaseLoader;
import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.agent_evolving.evaluator.DefaultEvaluator;
import com.openjiuwen.agent_evolving.optimizer.llm_call.InstructionOptimizer;
import com.openjiuwen.agent_evolving.trainer.Callbacks;
import com.openjiuwen.agent_evolving.trainer.Progress;
import com.openjiuwen.agent_evolving.trainer.Trainer;
import com.openjiuwen.agent_evolving.updater.SingleDimUpdater;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.singleagent.ReActAgentEvolve;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Agent evolving system tests.
 *
 * <p>Mirrors Python's {@code test_react_agent_evolve.py} in
 * {@code tests/system_tests/agent_evolving}.
 */
@Tag("system-test")
class ReActAgentEvolveSystemTest {

    @Test
    void testAgentCreation() {
        assumeTrue(hasLlmConfig(), "Requires LLM API configuration");
        Runner.start();
        try {
            ReActAgentEvolve agent = createReactAgent("test_agent");

            assertTrue("test_agent".equals(agent.getCard().getId()));
            Object result = agent.invoke(
                    Map.of("query", "What is Python?"),
                    new AgentSession("react_agent_creation_" + UUID.randomUUID())
            );
            assertNotNull(result);
        } finally {
            Runner.stop();
        }
    }

    @Test
    void testEndToEndTraining(@TempDir Path tmpDir) {
        assumeTrue(hasLlmConfig(), "Requires LLM API configuration");
        Runner.start();
        try {
            ReActAgentEvolve agent = createReactAgent("train_demo");
            CaseLoader[] split = createSimpleQaCases().split(0.6, 0);

            Trainer trainer = new Trainer.Builder()
                    .updater(createUpdater())
                    .evaluator(createEvaluator())
                    .numParallel(2)
                    .earlyStopScore(0.95)
                    .checkpointDir(tmpDir.toString())
                    .checkpointEveryNEpochs(1)
                    .build();

            Object evolved = trainer.train(agent, split[0], split[1], 3, Map.of());

            assertNotNull(evolved);
        } finally {
            Runner.stop();
        }
    }

    @Test
    void testTrainingWithCallbacks() {
        assumeTrue(hasLlmConfig(), "Requires LLM API configuration");
        Runner.start();
        try {
            ReActAgentEvolve agent = createReactAgent("callback_demo");
            CaseLoader[] split = createSimpleQaCases().split(0.6, 0);
            TrainingMonitor monitor = new TrainingMonitor();

            Trainer trainer = new Trainer.Builder()
                    .updater(createUpdater())
                    .evaluator(createEvaluator())
                    .numParallel(2)
                    .earlyStopScore(0.95)
                    .callbacks(monitor)
                    .build();

            trainer.train(agent, split[0], split[1], 2, Map.of());

            assertTrue(monitor.beginCalled);
            assertTrue(monitor.endCalled);
            assertTrue(monitor.bestScore >= 0.0);
        } finally {
            Runner.stop();
        }
    }

    @Test
    void testEvolvedAgentInference() {
        assumeTrue(hasLlmConfig(), "Requires LLM API configuration");
        Runner.start();
        try {
            ReActAgentEvolve agent = createReactAgent("inference_demo");
            CaseLoader[] split = createSimpleQaCases().split(0.6, 0);

            Trainer trainer = new Trainer.Builder()
                    .updater(createUpdater())
                    .evaluator(createEvaluator())
                    .numParallel(2)
                    .earlyStopScore(0.95)
                    .build();

            Object evolved = trainer.train(agent, split[0], split[1], 2, Map.of());
            assertNotNull(evolved);

            for (String query : List.of("Please introduce machine learning.", "Python how to write file?")) {
                Object result = agent.invoke(
                        Map.of("query", query),
                        new AgentSession("react_agent_inference_" + UUID.randomUUID())
                );
                assertNotNull(result);
            }
        } finally {
            Runner.stop();
        }
    }

    @Test
    void testCheckpointSaveAndResume(@TempDir Path tmpDir) throws Exception {
        assumeTrue(hasLlmConfig(), "Requires LLM API configuration");
        Runner.start();
        try {
            ReActAgentEvolve agent = createReactAgent("checkpoint_demo");
            CaseLoader[] split = createSimpleQaCasesForCheckpoint().split(0.6, 0);

            Trainer trainer = new Trainer.Builder()
                    .updater(createUpdater())
                    .evaluator(createEvaluator())
                    .numParallel(2)
                    .earlyStopScore(0.95)
                    .checkpointDir(tmpDir.toString())
                    .checkpointEveryNEpochs(1)
                    .checkpointOnImprove(true)
                    .build();

            trainer.train(agent, split[0], split[1], 2, Map.of());

            List<Path> checkpointFiles = Files.list(tmpDir).toList();
            assertFalse(checkpointFiles.isEmpty());

            ReActAgentEvolve agent2 = createReactAgent("checkpoint_demo_2");
            Trainer trainer2 = new Trainer.Builder()
                    .updater(createUpdater())
                    .evaluator(createEvaluator())
                    .numParallel(2)
                    .earlyStopScore(0.95)
                    .checkpointDir(tmpDir.toString())
                    .resumeFrom(checkpointFiles.getFirst().toString())
                    .checkpointEveryNEpochs(1)
                    .checkpointOnImprove(true)
                    .build();

            Object evolved = trainer2.train(agent2, split[0], split[1], 2, Map.of());

            assertNotNull(evolved);
        } finally {
            Runner.stop();
        }
    }

    private static DefaultEvaluator createEvaluator() {
        return new DefaultEvaluator(createModelConfig(), createModelClientConfig(), "");
    }

    private static InstructionOptimizer createOptimizer() {
        return new InstructionOptimizer(createModelConfig(), createModelClientConfig());
    }

    private static SingleDimUpdater createUpdater() {
        return new SingleDimUpdater(createOptimizer());
    }

    private static ReActAgentEvolve createReactAgent(String agentId) {
        AgentCard card = AgentCard.builder()
                .id(agentId)
                .name(agentId)
                .description(agentId + " for testing")
                .build();

        ReActAgentConfig config = ReActAgentConfig.builder().build()
                .configureModelClient(
                        modelProvider(),
                        env("API_KEY"),
                        env("API_BASE"),
                        env("MODEL_NAME"),
                        false
                )
                .configurePromptTemplate(List.of(
                        Map.of("role", "system", "content", "You are a helpful AI assistant."),
                        Map.of("role", "user", "content", "{{query}}")
                ))
                .configureMaxIterations(TuneConstant.DEFAULT_ITERATION_NUM);

        if (config.getModelConfigObj() != null) {
            config.getModelConfigObj().setTemperature(modelTemperature());
            config.getModelConfigObj().setTopP(0.9);
            config.getModelConfigObj().setMaxTokens(1000);
        }

        ReActAgentEvolve agent = new ReActAgentEvolve(card);
        agent.configure(config);
        return agent;
    }

    private static CaseLoader createSimpleQaCases() {
        return new CaseLoader(List.of(
                caseData("What is machine learning?", "Machine learning is a branch of AI."),
                caseData("How does Python read a file?", "Use the open() function."),
                caseData("What is the chemical formula of water?", "H2O."),
                caseData("What is the speed of light?", "About 3x10^8 meters per second."),
                caseData("What is Earth's diameter?", "About 12,742 kilometers.")
        ));
    }

    private static CaseLoader createSimpleQaCasesForCheckpoint() {
        return new CaseLoader(List.of(
                caseData("Question 1", "Answer 1"),
                caseData("Question 2", "Answer 2"),
                caseData("Question 3", "Answer 3")
        ));
    }

    private static Case caseData(String query, String answer) {
        return new Case(Map.of("query", query), Map.of("answer", answer));
    }

    private static ModelRequestConfig createModelConfig() {
        return ModelRequestConfig.builder()
                .modelName(env("MODEL_NAME"))
                .temperature(modelTemperature())
                .maxTokens(1000)
                .topP(0.9)
                .build();
    }

    private static ModelClientConfig createModelClientConfig() {
        return ModelClientConfig.builder()
                .clientProvider(modelProvider())
                .apiKey(env("API_KEY"))
                .apiBase(env("API_BASE"))
                .timeout(modelTimeout())
                .verifySsl(false)
                .build();
    }

    private static boolean hasLlmConfig() {
        return hasText(env("API_BASE"))
                && hasText(env("API_KEY"))
                && hasText(env("MODEL_NAME"));
    }

    private static String modelProvider() {
        return hasText(env("MODEL_PROVIDER")) ? env("MODEL_PROVIDER") : "openai";
    }

    private static double modelTemperature() {
        return parseDouble(env("MODEL_TEMPERATURE"), 0.3);
    }

    private static double modelTimeout() {
        return parseDouble(env("MODEL_TIMEOUT"), 120.0);
    }

    private static String env(String key) {
        return System.getenv().getOrDefault(key, "");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static double parseDouble(String value, double defaultValue) {
        if (!hasText(value)) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static final class TrainingMonitor extends Callbacks {
        private boolean beginCalled;
        private boolean endCalled;
        private double bestScore;
        private final List<Double> scoreHistory = new ArrayList<>();

        @Override
        public void onTrainBegin(Object agent, Progress progress, List<EvaluatedCase> evalInfo) {
            beginCalled = true;
        }

        @Override
        public void onTrainEpochEnd(Object agent, Progress progress, List<EvaluatedCase> evalInfo) {
            scoreHistory.add(progress.getCurrentEpochScore());
            bestScore = Math.max(bestScore, progress.getBestScore());
        }

        @Override
        public void onTrainEnd(Object agent, Progress progress, List<EvaluatedCase> evalInfo) {
            endCalled = true;
        }
    }
}
