/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code tests.unit_tests.agent_evolving.evaluator.evaluator_pipeline.test_models} in
 * {@code tests/unit_tests/agent_evolving/evaluator/evaluator_pipeline/test_models.py}.
 */
class EvaluatorPipelineModelsTest {

    @Test
    void execResultSuccessPropertyReturnsTrueForZeroReturnCode() {
        ExecResult result = new ExecResult();
        result.setStdout("output");
        result.setReturncode(0);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void execResultSuccessPropertyReturnsFalseForNonZeroReturnCode() {
        ExecResult result = new ExecResult();
        result.setStderr("error");
        result.setReturncode(1);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execResultTimeoutPropertyIsStored() {
        ExecResult result = new ExecResult();
        result.setTimedOut(true);
        result.setReturncode(-1);

        assertThat(result.isTimedOut()).isTrue();
    }

    @Test
    void taskCreationUsesPythonDefaults() {
        Task task = new Task();
        task.setTaskId("test_task");
        task.setInstruction("Test instruction");

        assertThat(task.getTaskId()).isEqualTo("test_task");
        assertThat(task.getInstruction()).isEqualTo("Test instruction");
        assertThat(task.getMetadata()).isEmpty();
        assertThat(task.getEnvironmentSpec()).isEmpty();
        assertThat(task.isHasSkills()).isFalse();
        assertThat(task.getSkills()).isEmpty();
    }

    @Test
    void taskWithMetadataAndSkillsStoresValues() {
        Task task = new Task();
        task.setTaskId("test_task");
        task.setInstruction("Test");
        task.setMetadata(Map.of("key", "value"));
        task.setHasSkills(true);
        task.setSkills(List.of("skill1", "skill2"));

        assertThat(task.getMetadata()).containsEntry("key", "value");
        assertThat(task.isHasSkills()).isTrue();
        assertThat(task.getSkills()).containsExactly("skill1", "skill2");
    }

    @Test
    void agentContextDefaultValuesMatchPython() {
        AgentContext context = new AgentContext();

        assertThat(context.getIteration()).isEqualTo(1);
        assertThat(context.isHasSkill()).isFalse();
        assertThat(context.getPreviousResult()).isNull();
        assertThat(context.getEvolutionSuggestions()).isNull();
        assertThat(context.getEvolutionFiles()).isNull();
    }

    @Test
    void agentContextStoresCustomValues() {
        AgentContext context = new AgentContext();
        context.setIteration(3);
        context.setHasSkill(true);
        context.setEvolutionSuggestions("suggestion text");
        context.setNInputTokens(100);
        context.setNOutputTokens(200);

        assertThat(context.getIteration()).isEqualTo(3);
        assertThat(context.isHasSkill()).isTrue();
        assertThat(context.getEvolutionSuggestions()).isEqualTo("suggestion text");
        assertThat(context.getNInputTokens()).isEqualTo(100);
        assertThat(context.getNOutputTokens()).isEqualTo(200);
    }

    @Test
    void agentRunResultCreationStoresValues() {
        AgentRunResult result = new AgentRunResult();
        result.setFinalResponse("response");
        result.setTrajectory(List.of(Map.of("step", 1)));
        result.setExecutionTime(1.5);
        result.setTokensUsed(1000);

        assertThat(result.getFinalResponse()).isEqualTo("response");
        assertThat(result.getTrajectory()).containsExactly(Map.of("step", 1));
        assertThat(result.getExecutionTime()).isEqualTo(1.5);
        assertThat(result.getTokensUsed()).isEqualTo(1000);
    }

    @Test
    void evalResultPassedStoresValues() {
        EvalResult result = new EvalResult();
        result.setPassed(true);
        result.setPassRate(1.0);
        result.setTestOutput("All tests passed");
        result.setReturncode(0);

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getPassRate()).isEqualTo(1.0);
        assertThat(result.getReturncode()).isEqualTo(0);
    }

    @Test
    void evalResultFailedStoresFailedTests() {
        EvalResult result = new EvalResult();
        result.setPassed(false);
        result.setPassRate(0.5);
        result.setFailedTests(List.of("test1", "test2"));
        result.setReturncode(1);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.getPassRate()).isEqualTo(0.5);
        assertThat(result.getFailedTests()).containsExactly("test1", "test2");
    }

    @Test
    void skillDeltaDefaultsMatchPython() {
        SkillDelta delta = new SkillDelta();

        assertThat(delta.getSkills()).isEmpty();
        assertThat(delta.getEvolutions()).isEmpty();
        assertThat(delta.getEvolutionFiles()).isEmpty();
        assertThat(delta.isChanged()).isFalse();
    }

    @Test
    void skillDeltaWithContentStoresValues() {
        SkillDelta delta = new SkillDelta();
        delta.setSkills(Map.of("skill1", "content"));
        delta.setEvolutions(Map.of("skill1", "evo json"));
        delta.setChanged(true);

        assertThat(delta.isChanged()).isTrue();
        assertThat(delta.getSkills()).containsKey("skill1");
    }

    @Test
    void iterationResultCreationStoresNestedResults() {
        AgentRunResult agentResult = new AgentRunResult();
        agentResult.setFinalResponse("test");
        EvalResult evalResult = new EvalResult();
        evalResult.setPassed(true);
        SkillDelta skillDelta = new SkillDelta();

        IterationResult result = new IterationResult();
        result.setIteration(1);
        result.setAgentResult(agentResult);
        result.setEvalResult(evalResult);
        result.setSkillDelta(skillDelta);

        assertThat(result.getIteration()).isEqualTo(1);
        assertThat(result.getAgentResult().getFinalResponse()).isEqualTo("test");
        assertThat(result.getEvalResult().isPassed()).isTrue();
        assertThat(result.isSkillChanged()).isFalse();
    }

    @Test
    void pipelineResultCreationStoresValues() {
        PipelineResult result = new PipelineResult();
        result.setTaskId("task1");
        result.setAgentName("jiuwenswarm");
        result.setBenchmarkName("skillsbench");
        result.setTotalIterations(3);
        result.setConvergenceAchieved(true);
        result.setConvergenceType("convergence");
        result.setMetrics(Map.of("accuracy", 0.8));

        assertThat(result.getTaskId()).isEqualTo("task1");
        assertThat(result.getTotalIterations()).isEqualTo(3);
        assertThat(result.isConvergenceAchieved()).isTrue();
        assertThat(result.getMetrics()).containsEntry("accuracy", 0.8);
    }

    @Test
    void pipelineResultToDictUsesPythonKeys() {
        PipelineResult result = new PipelineResult();
        result.setTaskId("task1");
        result.setAgentName("jiuwenswarm");
        result.setBenchmarkName("skillsbench");
        result.setTotalIterations(2);
        result.setConvergenceAchieved(false);
        result.setMetrics(Map.of("score", 0.7));
        result.setOutputDir(Path.of("results"));

        Map<String, Object> resultDict = result.toDict();

        assertThat(resultDict)
                .containsEntry("task_id", "task1")
                .containsEntry("agent_name", "jiuwenswarm")
                .containsEntry("total_iterations", 2)
                .containsEntry("convergence_achieved", false)
                .containsEntry("metrics", Map.of("score", 0.7))
                .containsEntry("output_dir", "results");
    }
}
