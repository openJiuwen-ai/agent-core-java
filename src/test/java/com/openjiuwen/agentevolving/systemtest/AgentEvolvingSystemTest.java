
package com.openjiuwen.agentevolving.systemtest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.openjiuwen.agentevolving.checkpointing.FileCheckpointStore;
import com.openjiuwen.agentevolving.dataset.Case;
import com.openjiuwen.agentevolving.dataset.CaseLoader;
import com.openjiuwen.agentevolving.dataset.EvaluatedCase;
import com.openjiuwen.agentevolving.evaluator.DefaultEvaluator;
import com.openjiuwen.agentevolving.optimizer.llm_call.InstructionOptimizer;
import com.openjiuwen.agentevolving.trainer.Callbacks;
import com.openjiuwen.agentevolving.trainer.Progress;
import com.openjiuwen.agentevolving.trainer.Trainer;
import com.openjiuwen.agentevolving.updater.SingleDimUpdater;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.agents.ReActAgentEvolve;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag("system-test")
class AgentEvolvingSystemTest {
    @TempDir
    Path tempDir;

    @Test
    void reactAgentEvolveTrainingSupportsCallbacksCheckpointAndResume() throws Exception {
        assumeTrue(hasRemoteConfig(), "Requires API_BASE/API_KEY/MODEL_PROVIDER/MODEL_NAME");

        ModelRequestConfig modelConfig =
            ModelRequestConfig.builder().modelName(env("MODEL_NAME")).temperature(0.2).topP(0.9).maxTokens(256).build();
        ModelClientConfig clientConfig = ModelClientConfig.builder().clientProvider(env("MODEL_PROVIDER"))
                .apiKey(env("API_KEY")).apiBase(env("API_BASE")).timeout(120.0).maxRetries(1)
                .verifySsl(Boolean.parseBoolean(System.getenv().getOrDefault("LLM_SSL_VERIFY", "false"))).build();

        CaseLoader[] split = cases().split(0.67, 7);
        CaseLoader trainCases = split[0];
        CaseLoader valCases = split[1].isEmpty() ? split[0] : split[1];

        TrainingMonitor firstMonitor = new TrainingMonitor();
        ReActAgentEvolve firstAgent = createAgent("agent-evolve-first");
        Trainer firstTrainer =
            new Trainer.Builder().updater(new SingleDimUpdater(new InstructionOptimizer(modelConfig, clientConfig)))
                    .evaluator(new DefaultEvaluator(modelConfig, clientConfig)).callbacks(firstMonitor).numParallel(1)
                    .checkpointDir(tempDir.toString()).checkpointEveryNEpochs(1).checkpointOnImprove(true).build();

        Object firstResult = firstTrainer.train(firstAgent, trainCases, valCases, 1, Map.of());

        assertSame(firstAgent, firstResult);
        assertTrue(firstMonitor.trainBeginCalled);
        assertTrue(firstMonitor.trainEndCalled);

        Path checkpointPath = tempDir.resolve("latest.json");
        assertTrue(Files.exists(checkpointPath));
        assertNotNull(new FileCheckpointStore(tempDir.toString()).loadCheckpoint(checkpointPath.toString()));

        TrainingMonitor resumeMonitor = new TrainingMonitor();
        ReActAgentEvolve resumedAgent = createAgent("agent-evolve-resume");
        Trainer resumedTrainer =
            new Trainer.Builder().updater(new SingleDimUpdater(new InstructionOptimizer(modelConfig, clientConfig)))
                    .evaluator(new DefaultEvaluator(modelConfig, clientConfig)).callbacks(resumeMonitor).numParallel(1)
                    .checkpointDir(tempDir.toString()).resumeFrom(checkpointPath.toString()).checkpointEveryNEpochs(1)
                    .checkpointOnImprove(true).build();

        Object resumedResult = resumedTrainer.train(resumedAgent, trainCases, valCases, 2, Map.of());

        assertSame(resumedAgent, resumedResult);
        assertTrue(resumeMonitor.trainBeginCalled);
        assertTrue(resumeMonitor.trainEndCalled);
        assertTrue(resumeMonitor.startEpoch > 0);

        @SuppressWarnings("unchecked")
        Map<String, Object> inference =
            (Map<String, Object>) resumedAgent.invoke(Map.of("query", "Reply with one short sentence about testing."),
                    new AgentSession(UUID.randomUUID().toString()));
        assertNotNull(inference);
        assertTrue(inference.containsKey("output"));
        assertFalse(String.valueOf(inference.get("output")).isBlank());
    }

    private static ReActAgentEvolve createAgent(String prefix) {
        String agentId = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        ReActAgentEvolve agent = new ReActAgentEvolve(
                AgentCard.builder().id(agentId).name(agentId).description("agent evolving system test").build());

        ReActAgentConfig config = ReActAgentConfig.builder().maxIterations(2).build()
                .configureModelClient(env("MODEL_PROVIDER"), env("API_KEY"), env("API_BASE"), env("MODEL_NAME"),
                        Boolean.parseBoolean(System.getenv().getOrDefault("LLM_SSL_VERIFY", "false")))
                .configurePromptTemplate(List.of(Map.of("role", "system", "content", "You are a concise assistant."),
                        Map.of("role", "user", "content", "{{query}}")));

        if (config.getModelConfigObj() != null) {
            config.getModelConfigObj().setTemperature(0.2);
            config.getModelConfigObj().setTopP(0.9);
            config.getModelConfigObj().setMaxTokens(256);
        }
        agent.configure(config);
        return agent;
    }

    private static CaseLoader cases() {
        return new CaseLoader(List.of(
                new Case(Map.of("query", "What is Python?"), Map.of("answer", "Python is a programming language."),
                        "case_1"),
                new Case(Map.of("query", "What is unit testing?"),
                        Map.of("answer", "Unit testing validates small isolated pieces of code."), "case_2"),
                new Case(Map.of("query", "What does checkpointing do?"),
                        Map.of("answer", "Checkpointing saves progress so a run can resume later."), "case_3")));
    }

    private static boolean hasRemoteConfig() {
        return isNotBlank(env("API_BASE")) && isNotBlank(env("API_KEY")) && isNotBlank(env("MODEL_PROVIDER"))
                && isNotBlank(env("MODEL_NAME"));
    }

    private static String env(String key) {
        return System.getenv(key);
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static final class TrainingMonitor extends Callbacks {
        private boolean trainBeginCalled;
        private boolean trainEndCalled;
        private int startEpoch;

        @Override
        public void onTrainBegin(Object agent, Progress progress, List<EvaluatedCase> evalInfo) {
            trainBeginCalled = true;
            startEpoch = progress.getStartEpoch();
        }

        @Override
        public void onTrainEnd(Object agent, Progress progress, List<EvaluatedCase> evalInfo) {
            trainEndCalled = true;
        }
    }
}
