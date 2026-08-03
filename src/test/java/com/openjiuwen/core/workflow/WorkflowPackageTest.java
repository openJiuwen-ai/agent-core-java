/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's package facade in
 * {@code openjiuwen/core/workflow/__init__.py}.
 */
class WorkflowPackageTest {

    @Test
    void exportedSymbolsMatchPythonAllOrder() {
        assertThat(WorkflowPackage.PYTHON_MODULE)
                .isEqualTo("openjiuwen/core/workflow/__init__.py");

        assertThat(WorkflowPackage.all()).containsExactly(
                "Workflow",
                "WorkflowCard",
                "WorkflowOutput",
                "WorkflowChunk",
                "WorkflowExecutionState",
                "execute_single_component",
                "WorkflowComponent",
                "ComponentExecutable",
                "ComponentComposable",
                "WorkflowComponentMetadata",
                "ComponentConfig",
                "ComponentState",
                "ComponentAbility",
                "LLMComponent",
                "LLMCompConfig",
                "IntentDetectionComponent",
                "IntentDetectionCompConfig",
                "QuestionerComponent",
                "QuestionerConfig",
                "FieldInfo",
                "Start",
                "End",
                "EndConfig",
                "SubWorkflowComponent",
                "BranchComponent",
                "LoopComponent",
                "LoopGroup",
                "LoopBreakComponent",
                "LoopSetVariableComponent",
                "BranchRouter",
                "Branch",
                "ToolComponent",
                "ToolComponentConfig",
                "HTTPRequestComponent",
                "HttpComponentConfig",
                "HttpRequestParamConfig",
                "HttpAuthConfig",
                "HttpRequestBodyConfig",
                "HttpResponseHandlingConfig",
                "HttpAdvancedOptionsConfig",
                "HttpRetryConfig",
                "HttpRateLimitConfig",
                "HttpAuthType",
                "HttpContentType",
                "HttpResponseFormat",
                "ComponentKBConfig",
                "KnowledgeRetrievalComponent",
                "KnowledgeRetrievalCompConfig",
                "Condition",
                "FuncCondition",
                "ExpressionCondition",
                "ArrayCondition",
                "NumberCondition",
                "AlwaysTrue",
                "generate_workflow_key",
                "Session",
                "create_workflow_session"
        );
    }

    @Test
    void moduleSymbolsIncludeNonAllImportsFromWorkflowConfig() {
        assertThat(WorkflowPackage.importsSymbol("WorkflowConfig")).isTrue();
        assertThat(WorkflowPackage.importsSymbol("ExceptionConfig")).isTrue();
        assertThat(WorkflowPackage.exports("WorkflowConfig")).isFalse();
        assertThat(WorkflowPackage.exports("ExceptionConfig")).isFalse();
        assertThat(WorkflowPackage.sourceFor("ExceptionConfig"))
                .isEqualTo("openjiuwen.core.workflow.workflow_config.ExceptionConfig");
        assertThat(WorkflowPackage.javaTypeNameFor("ExceptionConfig"))
                .isEqualTo("com.openjiuwen.core.workflow.ExceptionConfig");
    }

    @Test
    void moduleSymbolsIncludeInputOutputButPythonAllDoesNot() {
        assertThat(WorkflowPackage.importsSymbol("Input")).isTrue();
        assertThat(WorkflowPackage.importsSymbol("Output")).isTrue();
        assertThat(WorkflowPackage.exports("Input")).isFalse();
        assertThat(WorkflowPackage.exports("Output")).isFalse();
    }

    @Test
    void exportedSymbolsAreImmutable() {
        assertThatThrownBy(() -> WorkflowPackage.all().add("unexpected"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
