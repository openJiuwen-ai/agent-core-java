
package com.openjiuwen.dev_tools.tune;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.dev_tools.tune.evaluator.DefaultEvaluator;
import com.openjiuwen.dev_tools.tune.optimizer.JointOptimizer;
import com.openjiuwen.dev_tools.tune.trainer.Trainer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class DevToolsCompatibilityTest {
    @Test
    void legacyTrainerAndEvaluatorAccessorsRemainAvailable() {
        ModelRequestConfig modelConfig = modelConfig();
        ModelClientConfig clientConfig = clientConfig();
        DefaultEvaluator evaluator = new DefaultEvaluator(modelConfig, clientConfig, "exact_match");
        JointOptimizer optimizer = new JointOptimizer(modelConfig, clientConfig, 2);
        Trainer trainer = new Trainer(evaluator, optimizer, 5, 0.95);

        assertThat(evaluator.getModelConfig()).isSameAs(modelConfig);
        assertThat(evaluator.getModelClientConfig()).isSameAs(clientConfig);
        assertThat(evaluator.getMetric()).isEqualTo("exact_match");
        assertThat(optimizer.getModelConfig()).isSameAs(modelConfig);
        assertThat(optimizer.getModelClientConfig()).isSameAs(clientConfig);
        assertThat(optimizer.getNumExamples()).isEqualTo(2);
        assertThat(trainer.getEvaluator()).isSameAs(evaluator);
        assertThat(trainer.getOptimizer()).isSameAs(optimizer);
        assertThat(trainer.getNumParallel()).isEqualTo(5);
        assertThat(trainer.getEarlyStopScore()).isEqualTo(0.95);
    }

    @Test
    void legacyCaseLoaderAliasAndSnakeCaseAccessorRemainAvailable() {
        Case first = Case.builder().inputs(Map.of("query", "first")).label(Map.of("output", "A")).build();
        Case second = Case.builder().inputs(Map.of("query", "second")).label(Map.of("output", "B")).build();

        CaseLoader caseLoader = new CaseLoader(List.of(first, second));

        assertThat(caseLoader.size()).isEqualTo(2);
        assertThat(caseLoader.length()).isEqualTo(2);
        assertThat(caseLoader.get_cases()).hasSize(2);
        assertThat(caseLoader.get_cases()).extracting(Case::getCaseId).containsExactly("case_0", "case_1");

        caseLoader.shuffle();

        assertThat(caseLoader.get_cases()).hasSize(2);
        assertThat(caseLoader.get_cases()).extracting(Case::getCaseId).allMatch(caseId -> caseId.startsWith("case_"));
    }

    @Test
    void legacyJointOptimizerRangeValidationUsesToolchainStatus() {
        assertThatThrownBy(() -> new JointOptimizer(modelConfig(), clientConfig(), 21)).isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(((BaseError) error).getCode())
                        .isEqualTo(StatusCode.TOOLCHAIN_OPTIMIZER_PARAM_ERROR.code()))
                .hasMessageContaining("num_examples should be between 0 and 20");
    }

    private static ModelRequestConfig modelConfig() {
        return ModelRequestConfig.builder().modelName("test-model").build();
    }

    private static ModelClientConfig clientConfig() {
        return ModelClientConfig.builder().clientProvider("OpenAI").apiKey("test-key").apiBase("https://example.com/v1")
                .verifySsl(false).build();
    }
}