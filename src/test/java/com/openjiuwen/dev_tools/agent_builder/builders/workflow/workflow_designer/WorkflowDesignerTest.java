/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python behavior in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/workflow_designer/workflow_designer.py}.
 *
 * <p>Mirrors Python's {@code TestWorkflowDesigner} in
 * {@code tests/unit_tests/dev_tools/agent_builder/builders/workflow/workflow_designer/test_workflow_designer.py}.</p>
 */
class WorkflowDesignerTest {

    @Test
    void constructorStoresLlm() throws ReflectiveOperationException {
        Model model = modelReturning("unused");
        WorkflowDesigner designer = new WorkflowDesigner(model);

        assertThat(llmField(designer)).isSameAs(model);
    }

    @Test
    void constructorAllowsNullLlm() throws ReflectiveOperationException {
        WorkflowDesigner designer = new WorkflowDesigner(null);

        assertThat(llmField(designer)).isNull();
    }

    @Test
    void parseReflectionResultExtractsAfterChineseEvaluationSection() {
        String result = WorkflowDesigner.parseReflectionResult(
                "## 问题评估\n无问题\n## New Workflow Design\nFinal design"
        );

        assertThat(result).contains("Final design");
    }

    @Test
    void parseReflectionResultExtractsAfterEnglishSeparator() {
        String result = WorkflowDesigner.parseReflectionResult("Evaluation\n New Workflow Design\nFinal design");

        assertThat(result).contains("Final design");
    }

    @Test
    void parseReflectionResultReturnsOriginalWithoutSeparator() {
        assertThat(WorkflowDesigner.parseReflectionResult("Just design content")).isEqualTo("Just design content");
    }

    @Test
    void designRunsBasicBranchAndReflectionInOrder() {
        List<List<BaseMessage>> captured = new ArrayList<>();
        Model model = new Model((messages, modelConfig, modelClientConfig, options) -> {
            captured.add(messages);
            String content = switch (captured.size()) {
                case 1 -> "basic-result";
                case 2 -> "branch-result";
                default -> "analysis\n## New Workflow Design\nfinal-design";
            };
            return CompletableFuture.completedFuture(new AssistantMessage(content));
        });
        WorkflowDesigner designer = new WorkflowDesigner(model);

        String result = designer.design("make workflow", "search API");

        assertThat(result).isEqualTo("final-design");
        assertThat(captured).hasSize(3);
        assertThat(captured.get(0).get(1).getContentAsString()).contains("make workflow");
        assertThat(captured.get(0).get(1).getContentAsString()).contains("search API");
        assertThat(captured.get(1).get(1).getContentAsString()).contains("basic-result");
        assertThat(captured.get(2).get(1).getContentAsString()).contains("basic-result");
        assertThat(captured.get(2).get(1).getContentAsString()).contains("branch-result");
    }

    private static Model llmField(WorkflowDesigner designer) throws ReflectiveOperationException {
        Field field = WorkflowDesigner.class.getDeclaredField("llm");
        field.setAccessible(true);
        return (Model) field.get(designer);
    }

    private static Model modelReturning(String response) {
        return new Model((messages, modelConfig, modelClientConfig, options) ->
                CompletableFuture.completedFuture(new AssistantMessage(response)));
    }
}
