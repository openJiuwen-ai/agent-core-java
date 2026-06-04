/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer;

import com.openjiuwen.dev_tools.agent_builder.builders.workflow.IntentionDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Workflow designer — generates workflow designs from user requirements.
 * <p>
 * Mirrors Python's {@code WorkflowDesigner} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer.workflow_designer}.
 */
public class WorkflowDesigner {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowDesigner.class);
    private final Object llm;

    public WorkflowDesigner() {
        this(null);
    }

    public WorkflowDesigner(Object llm) {
        this.llm = llm;
    }

    public Object getLlm() {
        return llm;
    }

    public static String parseReflectionResult(String reflectionResult) {
        if (reflectionResult == null) {
            return "";
        }
        for (String separator : List.of("## New Workflow Design", " New Workflow Design")) {
            int index = reflectionResult.indexOf(separator);
            if (index >= 0) {
                return reflectionResult.substring(index + separator.length()).strip();
            }
        }
        return reflectionResult;
    }

    /**
     * Basic design: input requirements, functional modules, and implementation steps.
     * <p>
     * Mirrors Python's {@code basic_design} method.
     */
    public String basicDesign(String userInput, String toolList) {
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", BasicDesignPrompt.SYSTEM_PROMPT),
                Map.of("role", "user", "content", BasicDesignPrompt.formatUserPrompt(userInput, toolList))
        );
        return invokeWorkflowLlm(messages);
    }

    /**
     * Branch design: identify branch points and branch structure.
     * <p>
     * Mirrors Python's {@code branch_design} method.
     */
    public String branchDesign(String userInput, String basicResult) {
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", BranchDesignPrompt.SYSTEM_PROMPT),
                Map.of("role", "user", "content", BranchDesignPrompt.formatUserPrompt(userInput, basicResult))
        );
        return invokeWorkflowLlm(messages);
    }

    /**
     * Reflection evaluation: evaluate and output optimized workflow design.
     * <p>
     * Mirrors Python's {@code reflection_evaluation} method.
     */
    public String reflectionEvaluation(String userInput, String basicResult, String branchResult) {
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", ReflectionEvaluatePrompt.SYSTEM_PROMPT),
                Map.of(
                        "role",
                        "user",
                        "content",
                        ReflectionEvaluatePrompt.formatUserPrompt(userInput, basicResult, branchResult)
                )
        );
        return parseReflectionResult(invokeWorkflowLlm(messages));
    }

    /**
     * Execute complete SE workflow design process.
     * <p>
     * Mirrors Python's {@code design} method.
     */
    public String design(String userInput, String toolList) {
        LOG.info("[WorkflowDesigner] Starting complete workflow design process");
        String basicResult = basicDesign(userInput, toolList);
        String branchResult = branchDesign(userInput, basicResult);
        return reflectionEvaluation(userInput, basicResult, branchResult);
    }

    /** Design a workflow from the given requirements. */
    public Map<String, Object> design(Map<String, Object> requirements) {
        LOG.info("[WorkflowDesigner] Designing workflow from requirements");
        Map<String, Object> design = new LinkedHashMap<>();
        design.put("nodes", Collections.emptyList());
        design.put("edges", Collections.emptyList());
        design.put("status", "designed");
        return design;
    }

    private String invokeWorkflowLlm(List<Map<String, Object>> messages) {
        if (llm == null) {
            return "";
        }
        try {
            return IntentionDetector.invokeLlmContent(llm, messages);
        } catch (Exception e) {
            throw new IllegalStateException("Workflow design LLM invocation failed: " + e.getMessage(), e);
        }
    }
}
