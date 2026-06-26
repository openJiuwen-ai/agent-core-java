/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.trainer;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.operator.legacy.llm_call.LLMCall;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.single_agent.legacy.LegacyBaseAgent;
import com.openjiuwen.dev_tools.tune.Case;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;
import com.openjiuwen.dev_tools.tune.TuneUtils;
import com.openjiuwen.dev_tools.tune.dataset.CaseLoader;
import com.openjiuwen.dev_tools.tune.evaluator.BaseEvaluator;
import com.openjiuwen.dev_tools.tune.optimizer.BaseOptimizer;
import com.openjiuwen.dev_tools.tune.optimizer.TextualParameter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code Trainer} and {@code ParameterSearcher} in
 * {@code openjiuwen/dev_tools/tune/trainer/trainer.py}.
 */
class TrainerTest {

    @Test
    void constructorReadsPythonKwargsAndValidatesBounds() {
        Trainer trainer = new Trainer(new TestOptimizer(), new EchoEvaluator(),
                Map.of("num_parallel", 2, "early_stop_score", 0.75d));

        assertThat(trainer.getNumParallel()).isEqualTo(2);
        assertThat(trainer.getEarlyStopScore()).isEqualTo(0.75d);
        assertThatThrownBy(() -> new Trainer(new TestOptimizer(), new EchoEvaluator(), Map.of("num_parallel", 99)))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(((BaseError) error).getStatus())
                        .isEqualTo(StatusCode.TOOLCHAIN_AGENT_PARAM_ERROR));
    }

    @Test
    void predictAddsConversationIdAndConvertsInvokeFailuresToErrorDicts() {
        Trainer trainer = new Trainer(new TestOptimizer(), new EchoEvaluator());
        TestAgent agent = new TestAgent();
        CaseLoader cases = new CaseLoader(List.of(caseValue("pass"), caseValue("boom")));

        List<Map<String, Object>> predicts = trainer.predict(agent, cases);

        assertThat(predicts).hasSize(2);
        assertThat(predicts.get(0)).containsEntry("query", "pass").containsEntry("conversation_id", "case_0");
        assertThat(String.valueOf(predicts.get(1).get("error"))).contains("Get wrong result due to boom");
    }

    @Test
    void evaluateAveragesEvaluatedCaseScores() {
        Trainer trainer = new Trainer(new TestOptimizer(), new EchoEvaluator(), Map.of("num_parallel", 2));
        CaseLoader cases = new CaseLoader(List.of(caseValue("pass"), caseValue("fail")));

        Trainer.EvalResult result = trainer.evaluate(new TestAgent(), cases);

        assertThat(result.score()).isEqualTo(0.5d);
        assertThat(result.evaluatedCases()).hasSize(2);
    }

    @Test
    void trainStopsEarlyWhenValidationAlreadyMeetsTarget() {
        TestOptimizer optimizer = new TestOptimizer();
        Trainer trainer = new Trainer(optimizer, new EchoEvaluator(), Map.of("early_stop_score", 0.5d));
        TrackingCallbacks callbacks = new TrackingCallbacks();
        trainer.setCallbacks(callbacks);
        TestAgent agent = new TestAgent();
        CaseLoader cases = new CaseLoader(List.of(caseValue("pass")));

        LegacyBaseAgent result = trainer.train(agent, cases);

        assertThat(result).isSameAs(agent);
        assertThat(optimizer.updateCount).isZero();
        assertThat(callbacks.events).containsExactly("begin", "end");
    }

    @Test
    void trainRejectsAgentWithoutPythonGetLlmCallsOverride() {
        Trainer trainer = new Trainer(new TestOptimizer(), new EchoEvaluator());
        CaseLoader cases = new CaseLoader(List.of(caseValue("pass")));

        assertThatThrownBy(() -> trainer.train(new NoLlmCallsAgent(), cases))
                .isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(((BaseError) error).getStatus())
                        .isEqualTo(StatusCode.TOOLCHAIN_TRAINER_EXECUTION_ERROR))
                .hasMessageContaining("trainer only support current Agent right now");
    }

    @Test
    void updateAgentCopiesPromptsFromTextualParameterOrLlmCall() {
        Trainer trainer = new Trainer(new TestOptimizer(), new EchoEvaluator());
        TestAgent agent = new TestAgent();
        LLMCall replacement = llmCall("candidate", "new-system", "new-user");

        trainer.updateAgent(agent, Map.of("main", replacement));

        assertThat(agent.systemPrompt()).isEqualTo("new-system");
        assertThat(agent.userPrompt()).isEqualTo("new-user");

        TextualParameter textualParameter = new TextualParameter(llmCall("candidate-2", "textual-system", "textual-user"));
        trainer.updateAgent(agent, Map.of("main", textualParameter));

        assertThat(agent.systemPrompt()).isEqualTo("textual-system");
        assertThat(agent.userPrompt()).isEqualTo("textual-user");
    }

    @Test
    void parameterSearcherGeneratesCartesianProductAndFindsBestPrompt() {
        TestAgent agent = new TestAgent();
        Trainer trainer = new Trainer(new TestOptimizer(), new PromptEvaluator());
        ParameterSearcher searcher = new ParameterSearcher(trainer, new CaseLoader(List.of(caseValue("pass"))));
        Map<String, LLMCall> base = Map.of("main", llmCall("base", "base", "user"));
        Map<String, LLMCall> candidate = Map.of("main", llmCall("candidate", "better", "user"));

        List<Map<String, ?>> generated = ParameterSearcher.generateCandidates(List.of(base, candidate));
        ParameterSearcher.SearchResult result = searcher.searchBest(agent, 0.0d, base, List.of(candidate));

        assertThat(generated).hasSize(2);
        assertThat(result.score()).isEqualTo(1.0d);
        trainer.updateAgent(agent, result.parameters());
        assertThat(agent.systemPrompt()).isEqualTo("better");
    }

    private static Case caseValue(String query) {
        return new Case(new LinkedHashMap<>(Map.of("query", query)), new LinkedHashMap<>(Map.of("label", query)));
    }

    private static LLMCall llmCall(String id, String systemPrompt, String userPrompt) {
        Model model = new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage("ok")));
        return new LLMCall("unit-model", model, systemPrompt, userPrompt, false, false, id);
    }

    private static final class TestAgent extends LegacyBaseAgent {
        private final Map<String, LLMCall> llmCalls = new LinkedHashMap<>(Map.of(
                "main",
                llmCall("main", "base", "user")
        ));

        private TestAgent() {
            super(null);
        }

        @Override
        public CompletableFuture<Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
            if ("boom".equals(inputs.get("query"))) {
                return CompletableFuture.failedFuture(new IllegalStateException("boom"));
            }
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("query", inputs.get("query"));
            output.put("conversation_id", inputs.get("conversation_id"));
            output.put("system_prompt", systemPrompt());
            return CompletableFuture.completedFuture(output);
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            return List.of().iterator();
        }

        public Map<String, LLMCall> get_llm_calls() {
            return llmCalls;
        }

        private String systemPrompt() {
            return TuneUtils.getContentStringFromTemplate(llmCalls.get("main").getSystemPrompt());
        }

        private String userPrompt() {
            return TuneUtils.getContentStringFromTemplate(llmCalls.get("main").getUserPrompt());
        }
    }

    private static final class NoLlmCallsAgent extends LegacyBaseAgent {
        private NoLlmCallsAgent() {
            super(null);
        }

        @Override
        public CompletableFuture<Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
            return CompletableFuture.completedFuture(Map.of());
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            return List.of().iterator();
        }
    }

    private static final class EchoEvaluator extends BaseEvaluator {
        @Override
        public EvaluatedCase evaluate(Case caseValue, Map<String, Object> predict) {
            double score = "pass".equals(predict.get("query")) ? 1.0d : 0.0d;
            return new EvaluatedCase(caseValue, predict, score, "");
        }
    }

    private static final class PromptEvaluator extends BaseEvaluator {
        @Override
        public EvaluatedCase evaluate(Case caseValue, Map<String, Object> predict) {
            double score = "better".equals(predict.get("system_prompt")) ? 1.0d : 0.0d;
            return new EvaluatedCase(caseValue, predict, score, "");
        }
    }

    private static final class TestOptimizer extends BaseOptimizer {
        private int updateCount;

        @Override
        protected void doUpdate() {
            updateCount += 1;
            parameters.values().forEach(parameter -> parameter.getLlmCall().updateSystemPrompt("better"));
        }

        @Override
        protected void doBackward(List<EvaluatedCase> evaluatedCases) {
        }
    }

    private static final class TrackingCallbacks extends Callbacks {
        private final List<String> events = new ArrayList<>();

        @Override
        public void onTrainBegin(LegacyBaseAgent agent, Progress progress, List<EvaluatedCase> evalInfo) {
            events.add("begin");
        }

        @Override
        public void onTrainEnd(LegacyBaseAgent agent, Progress progress, List<EvaluatedCase> evalInfo) {
            events.add("end");
        }
    }
}
