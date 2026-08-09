/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Package bridge for workflow exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.workflow} package facade in
 * {@code openjiuwen/core/workflow/__init__.py}.</p>
 */
public final class WorkflowPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/workflow/__init__.py";

    public static final List<String> EXPORTED_SYMBOLS = List.of(
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

    public static final List<String> MODULE_SYMBOLS = buildModuleSymbols();
    public static final Map<String, String> EXPORT_SOURCES = buildExportSources();
    public static final Map<String, String> JAVA_TYPE_NAMES = buildJavaTypeNames();

    private WorkflowPackage() {
    }

    /**
     * Mirrors Python's {@code __all__}.
     *
     * @return exported symbol names in Python order
     */
    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }

    /**
     * Checks whether a symbol is re-exported by Python {@code __all__}.
     *
     * @param symbolName symbol name
     * @return {@code true} when the symbol is part of Python {@code __all__}
     */
    public static boolean exports(String symbolName) {
        return EXPORTED_SYMBOLS.contains(symbolName);
    }

    /**
     * Checks whether a symbol is imported into the Python module namespace.
     *
     * @param symbolName symbol name
     * @return {@code true} when direct module access exposes the symbol
     */
    public static boolean importsSymbol(String symbolName) {
        return MODULE_SYMBOLS.contains(symbolName);
    }

    /**
     * Returns the Python source object imported by the package facade.
     *
     * @param symbolName symbol name
     * @return dotted Python source object, or {@code null} when absent
     */
    public static String sourceFor(String symbolName) {
        return EXPORT_SOURCES.get(symbolName);
    }

    /**
     * Returns the Java type name expected to mirror the Python object.
     *
     * @param symbolName symbol name
     * @return fully qualified Java type name, or {@code null} when absent
     */
    public static String javaTypeNameFor(String symbolName) {
        return JAVA_TYPE_NAMES.get(symbolName);
    }

    private static List<String> buildModuleSymbols() {
        return List.of(
                "Workflow",
                "WorkflowCard",
                "WorkflowOutput",
                "WorkflowChunk",
                "WorkflowExecutionState",
                "execute_single_component",
                "generate_workflow_key",
                "WorkflowConfig",
                "ExceptionConfig",
                "WorkflowComponent",
                "ComponentExecutable",
                "ComponentComposable",
                "Input",
                "Output",
                "WorkflowComponentMetadata",
                "ComponentConfig",
                "ComponentState",
                "ComponentAbility",
                "SubWorkflowComponent",
                "Start",
                "End",
                "EndConfig",
                "BranchComponent",
                "LoopComponent",
                "LoopGroup",
                "LoopSetVariableComponent",
                "LoopBreakComponent",
                "LLMComponent",
                "LLMCompConfig",
                "QuestionerComponent",
                "QuestionerConfig",
                "FieldInfo",
                "IntentDetectionComponent",
                "IntentDetectionCompConfig",
                "ComponentKBConfig",
                "KnowledgeRetrievalComponent",
                "KnowledgeRetrievalCompConfig",
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
                "BranchRouter",
                "Branch",
                "Condition",
                "FuncCondition",
                "AlwaysTrue",
                "ExpressionCondition",
                "ArrayCondition",
                "NumberCondition",
                "Session",
                "create_workflow_session"
        );
    }

    private static Map<String, String> buildExportSources() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("Workflow", "openjiuwen.core.workflow.workflow.Workflow");
        sources.put("WorkflowCard", "openjiuwen.core.workflow.base.WorkflowCard");
        sources.put("WorkflowOutput", "openjiuwen.core.workflow.base.WorkflowOutput");
        sources.put("WorkflowChunk", "openjiuwen.core.workflow.base.WorkflowChunk");
        sources.put("WorkflowExecutionState", "openjiuwen.core.workflow.base.WorkflowExecutionState");
        sources.put("execute_single_component", "openjiuwen.core.workflow._workflow.execute_single_component");
        sources.put("generate_workflow_key", "openjiuwen.core.workflow.base.generate_workflow_key");
        sources.put("WorkflowConfig", "openjiuwen.core.workflow.workflow_config.WorkflowConfig");
        sources.put("ExceptionConfig", "openjiuwen.core.workflow.workflow_config.ExceptionConfig");
        sources.put("WorkflowComponent", "openjiuwen.core.workflow.components.component.WorkflowComponent");
        sources.put("ComponentExecutable", "openjiuwen.core.workflow.components.component.ComponentExecutable");
        sources.put("ComponentComposable", "openjiuwen.core.workflow.components.component.ComponentComposable");
        sources.put("Input", "openjiuwen.core.workflow.components.component.Input");
        sources.put("Output", "openjiuwen.core.workflow.components.component.Output");
        sources.put("WorkflowComponentMetadata", "openjiuwen.core.workflow.components.base.WorkflowComponentMetadata");
        sources.put("ComponentConfig", "openjiuwen.core.workflow.components.base.ComponentConfig");
        sources.put("ComponentState", "openjiuwen.core.workflow.components.base.ComponentState");
        sources.put("ComponentAbility", "openjiuwen.core.workflow.components.base.ComponentAbility");
        sources.put("SubWorkflowComponent", "openjiuwen.core.workflow.components.flow.workflow_comp.SubWorkflowComponent");
        sources.put("Start", "openjiuwen.core.workflow.components.flow.start_comp.Start");
        sources.put("End", "openjiuwen.core.workflow.components.flow.end_comp.End");
        sources.put("EndConfig", "openjiuwen.core.workflow.components.flow.end_comp.EndConfig");
        sources.put("BranchComponent", "openjiuwen.core.workflow.components.flow.branch_comp.BranchComponent");
        sources.put("LoopComponent", "openjiuwen.core.workflow.components.flow.loop.loop_comp.LoopComponent");
        sources.put("LoopGroup", "openjiuwen.core.workflow.components.flow.loop.loop_comp.LoopGroup");
        sources.put("LoopSetVariableComponent", "openjiuwen.core.workflow.components.flow.loop.loop_comp.LoopSetVariableComponent");
        sources.put("LoopBreakComponent", "openjiuwen.core.workflow.components.flow.loop.loop_comp.LoopBreakComponent");
        sources.put("LLMComponent", "openjiuwen.core.workflow.components.llm.llm_comp.LLMComponent");
        sources.put("LLMCompConfig", "openjiuwen.core.workflow.components.llm.llm_comp.LLMCompConfig");
        sources.put("QuestionerComponent", "openjiuwen.core.workflow.components.llm.questioner_comp.QuestionerComponent");
        sources.put("QuestionerConfig", "openjiuwen.core.workflow.components.llm.questioner_comp.QuestionerConfig");
        sources.put("FieldInfo", "openjiuwen.core.workflow.components.llm.questioner_comp.FieldInfo");
        sources.put("IntentDetectionComponent", "openjiuwen.core.workflow.components.llm.intent_detection_comp.IntentDetectionComponent");
        sources.put("IntentDetectionCompConfig", "openjiuwen.core.workflow.components.llm.intent_detection_comp.IntentDetectionCompConfig");
        sources.put("ComponentKBConfig", "openjiuwen.core.workflow.components.resource.knowledge_retrieval_comp.ComponentKBConfig");
        sources.put("KnowledgeRetrievalComponent", "openjiuwen.core.workflow.components.resource.knowledge_retrieval_comp.KnowledgeRetrievalComponent");
        sources.put("KnowledgeRetrievalCompConfig", "openjiuwen.core.workflow.components.resource.knowledge_retrieval_comp.KnowledgeRetrievalCompConfig");
        sources.put("ToolComponent", "openjiuwen.core.workflow.components.tool.tool_comp.ToolComponent");
        sources.put("ToolComponentConfig", "openjiuwen.core.workflow.components.tool.tool_comp.ToolComponentConfig");
        sources.put("HTTPRequestComponent", "openjiuwen.core.workflow.components.tool.http.HTTPRequestComponent");
        sources.put("HttpComponentConfig", "openjiuwen.core.workflow.components.tool.http.HttpComponentConfig");
        sources.put("HttpRequestParamConfig", "openjiuwen.core.workflow.components.tool.http.HttpRequestParamConfig");
        sources.put("HttpAuthConfig", "openjiuwen.core.workflow.components.tool.http.HttpAuthConfig");
        sources.put("HttpRequestBodyConfig", "openjiuwen.core.workflow.components.tool.http.HttpRequestBodyConfig");
        sources.put("HttpResponseHandlingConfig", "openjiuwen.core.workflow.components.tool.http.HttpResponseHandlingConfig");
        sources.put("HttpAdvancedOptionsConfig", "openjiuwen.core.workflow.components.tool.http.HttpAdvancedOptionsConfig");
        sources.put("HttpRetryConfig", "openjiuwen.core.workflow.components.tool.http.HttpRetryConfig");
        sources.put("HttpRateLimitConfig", "openjiuwen.core.workflow.components.tool.http.HttpRateLimitConfig");
        sources.put("HttpAuthType", "openjiuwen.core.workflow.components.tool.http.HttpAuthType");
        sources.put("HttpContentType", "openjiuwen.core.workflow.components.tool.http.HttpContentType");
        sources.put("HttpResponseFormat", "openjiuwen.core.workflow.components.tool.http.HttpResponseFormat");
        sources.put("BranchRouter", "openjiuwen.core.workflow.components.flow.branch_router.BranchRouter");
        sources.put("Branch", "openjiuwen.core.workflow.components.flow.branch_router.Branch");
        sources.put("Condition", "openjiuwen.core.workflow.components.condition.condition.Condition");
        sources.put("FuncCondition", "openjiuwen.core.workflow.components.condition.condition.FuncCondition");
        sources.put("AlwaysTrue", "openjiuwen.core.workflow.components.condition.condition.AlwaysTrue");
        sources.put("ExpressionCondition", "openjiuwen.core.workflow.components.condition.expression.ExpressionCondition");
        sources.put("ArrayCondition", "openjiuwen.core.workflow.components.condition.array.ArrayCondition");
        sources.put("NumberCondition", "openjiuwen.core.workflow.components.condition.number.NumberCondition");
        sources.put("Session", "openjiuwen.core.session.workflow.Session");
        sources.put("create_workflow_session", "openjiuwen.core.session.workflow.create_workflow_session");
        return Collections.unmodifiableMap(sources);
    }

    private static Map<String, String> buildJavaTypeNames() {
        Map<String, String> javaTypeNames = new LinkedHashMap<>();
        javaTypeNames.put("Workflow", "com.openjiuwen.core.workflow.Workflow");
        javaTypeNames.put("WorkflowCard", "com.openjiuwen.core.workflow.WorkflowCard");
        javaTypeNames.put("WorkflowOutput", "com.openjiuwen.core.workflow.WorkflowOutput");
        javaTypeNames.put("WorkflowChunk", "com.openjiuwen.core.workflow.WorkflowChunk");
        javaTypeNames.put("WorkflowExecutionState", "com.openjiuwen.core.workflow.WorkflowExecutionState");
        javaTypeNames.put("execute_single_component", "com.openjiuwen.core.workflow.ComponentExecutionHelper");
        javaTypeNames.put("generate_workflow_key", "com.openjiuwen.core.workflow.WorkflowKeys");
        javaTypeNames.put("WorkflowConfig", "com.openjiuwen.core.workflow.WorkflowConfig");
        javaTypeNames.put("ExceptionConfig", "com.openjiuwen.core.workflow.ExceptionConfig");
        javaTypeNames.put("WorkflowComponent", "com.openjiuwen.core.workflow.component.WorkflowComponent");
        javaTypeNames.put("ComponentExecutable", "com.openjiuwen.core.workflow.ComponentExecutable");
        javaTypeNames.put("ComponentComposable", "com.openjiuwen.core.workflow.ComponentComposable");
        javaTypeNames.put("Input", "com.openjiuwen.core.workflow.component.IOConfig");
        javaTypeNames.put("Output", "com.openjiuwen.core.workflow.component.IOConfig");
        javaTypeNames.put("WorkflowComponentMetadata", "com.openjiuwen.core.workflow.component.WorkflowComponentMetadata");
        javaTypeNames.put("ComponentConfig", "com.openjiuwen.core.workflow.component.ComponentConfig");
        javaTypeNames.put("ComponentState", "com.openjiuwen.core.workflow.component.ComponentState");
        javaTypeNames.put("ComponentAbility", "com.openjiuwen.core.workflow.component.ComponentAbility");
        javaTypeNames.put("SubWorkflowComponent", "com.openjiuwen.core.workflow.component.SubWorkflowComponent");
        javaTypeNames.put("Start", "com.openjiuwen.core.workflow.component.Start");
        javaTypeNames.put("End", "com.openjiuwen.core.workflow.component.End");
        javaTypeNames.put("EndConfig", "com.openjiuwen.core.workflow.component.EndConfig");
        javaTypeNames.put("BranchComponent", "com.openjiuwen.core.workflow.component.BranchComponent");
        javaTypeNames.put("LoopComponent", "com.openjiuwen.core.workflow.component.LoopComponent");
        javaTypeNames.put("LoopGroup", "com.openjiuwen.core.workflow.component.loop.LoopGroup");
        javaTypeNames.put("LoopSetVariableComponent", "com.openjiuwen.core.workflow.component.loop.LoopSetVariableComponent");
        javaTypeNames.put("LoopBreakComponent", "com.openjiuwen.core.workflow.component.loop.LoopBreakComponent");
        javaTypeNames.put("LLMComponent", "com.openjiuwen.core.workflow.component.llm.LLMComponent");
        javaTypeNames.put("LLMCompConfig", "com.openjiuwen.core.workflow.component.llm.LLMCompConfig");
        javaTypeNames.put("QuestionerComponent", "com.openjiuwen.core.workflow.component.llm.QuestionerComponent");
        javaTypeNames.put("QuestionerConfig", "com.openjiuwen.core.workflow.component.llm.QuestionerConfig");
        javaTypeNames.put("FieldInfo", "com.openjiuwen.core.workflow.component.llm.FieldInfo");
        javaTypeNames.put("IntentDetectionComponent", "com.openjiuwen.core.workflow.component.IntentDetectionComponent");
        javaTypeNames.put("IntentDetectionCompConfig", "com.openjiuwen.core.workflow.component.llm.IntentDetectionCompConfig");
        javaTypeNames.put("ComponentKBConfig", "com.openjiuwen.core.workflow.component.resource.ComponentKBConfig");
        javaTypeNames.put("KnowledgeRetrievalComponent", "com.openjiuwen.core.workflow.component.resource.KnowledgeRetrievalComponent");
        javaTypeNames.put("KnowledgeRetrievalCompConfig", "com.openjiuwen.core.workflow.component.resource.KnowledgeRetrievalCompConfig");
        javaTypeNames.put("ToolComponent", "com.openjiuwen.core.workflow.component.tool.ToolComponent");
        javaTypeNames.put("ToolComponentConfig", "com.openjiuwen.core.workflow.component.tool.ToolComponentConfig");
        javaTypeNames.put("HTTPRequestComponent", "com.openjiuwen.core.workflow.component.tool.http.HTTPRequestComponent");
        javaTypeNames.put("HttpComponentConfig", "com.openjiuwen.core.workflow.component.tool.http.HttpComponentConfig");
        javaTypeNames.put("HttpRequestParamConfig", "com.openjiuwen.core.workflow.component.tool.http.HttpRequestParamConfig");
        javaTypeNames.put("HttpAuthConfig", "com.openjiuwen.core.workflow.component.tool.http.HttpAuthConfig");
        javaTypeNames.put("HttpRequestBodyConfig", "com.openjiuwen.core.workflow.component.tool.http.HttpRequestBodyConfig");
        javaTypeNames.put("HttpResponseHandlingConfig", "com.openjiuwen.core.workflow.component.tool.http.HttpResponseHandlingConfig");
        javaTypeNames.put("HttpAdvancedOptionsConfig", "com.openjiuwen.core.workflow.component.tool.http.HttpAdvancedOptionsConfig");
        javaTypeNames.put("HttpRetryConfig", "com.openjiuwen.core.workflow.component.tool.http.HttpRetryConfig");
        javaTypeNames.put("HttpRateLimitConfig", "com.openjiuwen.core.workflow.component.tool.http.HttpRateLimitConfig");
        javaTypeNames.put("HttpAuthType", "com.openjiuwen.core.workflow.component.tool.http.HttpAuthType");
        javaTypeNames.put("HttpContentType", "com.openjiuwen.core.workflow.component.tool.http.HttpContentType");
        javaTypeNames.put("HttpResponseFormat", "com.openjiuwen.core.workflow.component.tool.http.HttpResponseFormat");
        javaTypeNames.put("BranchRouter", "com.openjiuwen.core.workflow.BranchRouter");
        javaTypeNames.put("Branch", "com.openjiuwen.core.workflow.Branch");
        javaTypeNames.put("Condition", "com.openjiuwen.core.workflow.condition.Condition");
        javaTypeNames.put("FuncCondition", "com.openjiuwen.core.workflow.condition.FuncCondition");
        javaTypeNames.put("AlwaysTrue", "com.openjiuwen.core.workflow.condition.AlwaysTrue");
        javaTypeNames.put("ExpressionCondition", "com.openjiuwen.core.workflow.condition.ExpressionCondition");
        javaTypeNames.put("ArrayCondition", "com.openjiuwen.core.workflow.condition.ArrayCondition");
        javaTypeNames.put("NumberCondition", "com.openjiuwen.core.workflow.condition.NumberCondition");
        javaTypeNames.put("Session", "com.openjiuwen.core.session.WorkflowSession");
        javaTypeNames.put("create_workflow_session", "com.openjiuwen.core.session.WorkflowSession#createWorkflowSession");
        return Collections.unmodifiableMap(javaTypeNames);
    }
}
