package com.openjiuwen.agentevolving;

import com.openjiuwen.agentevolving.evaluator.metrics.ExactMatchMetric;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentEvolvingExampleSupportTest {

    @Test
    void shouldExposeCalculatorAndNl2SqlCaseLoaders() {
        assertThat(AgentEvolvingExampleSupport.calculatorCaseLoader().size()).isEqualTo(1);
        assertThat(AgentEvolvingExampleSupport.nl2sqlCaseLoader().size()).isEqualTo(1);
        assertThat(AgentEvolvingExampleSupport.calculatorCaseLoader().getCases().get(0).getInputs())
                .containsEntry("ground_truth", "4");
    }

    @Test
    void shouldExposeExactMatchMetricAndBaselineSummary() {
        ExactMatchMetric metric = AgentEvolvingExampleSupport.exactMatchMetric();
        assertThat(metric.compute("A", "a", java.util.Map.of())).isEqualTo(1.0);
        assertThat(AgentEvolvingExampleSupport.describeCurrentJavaBaseline())
                .contains("dataset/evaluator/trainer")
                .contains("not fully ported");
    }
}
