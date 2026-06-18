/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.workflow_designer;

import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Three-stage workflow designer.
 *
 * <p>Mirrors Python's {@code WorkflowDesigner} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/workflow_designer/workflow_designer.py}.</p>
 */
public class WorkflowDesigner {

    private static final LoggerProtocol LOGGER = LogManager.getLogger("agent_builder");

    private final Model llm;

    public WorkflowDesigner(Model llm) {
        this.llm = Objects.requireNonNull(llm, "llm");
    }

    public String basicDesign(String userInput, String toolList) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("user_query", userInput);
        values.put("tool_list", toolList);
        String userPrompt = BasicDesignPrompt.BASIC_DESIGN_USER_PROMPT_TEMPLATE
                .format(values)
                .toMessages()
                .get(0)
                .getContentAsString();
        return invoke(List.of(
                new SystemMessage(BasicDesignPrompt.BASIC_DESIGN_SYSTEM_PROMPT),
                new UserMessage(userPrompt)
        ));
    }

    public String branchDesign(String userInput, String basicResult) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("user_query", userInput);
        values.put("basic_design", basicResult);
        String userPrompt = BranchDesignPrompt.BRANCH_DESIGN_USER_PROMPT_TEMPLATE
                .format(values)
                .toMessages()
                .get(0)
                .getContentAsString();
        return invoke(List.of(
                new SystemMessage(BranchDesignPrompt.BRANCH_DESIGN_SYSTEM_PROMPT),
                new UserMessage(userPrompt)
        ));
    }

    public String reflectionEvaluation(String userInput, String basicResult, String branchResult) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("user_query", userInput);
        values.put("basic_design", basicResult);
        values.put("branch_design", branchResult);
        String userPrompt = ReflectionEvaluatePrompt.REFLECTION_EVALUATE_USER_PROMPT_TEMPLATE
                .format(values)
                .toMessages()
                .get(0)
                .getContentAsString();
        return parseReflectionResult(invoke(List.of(
                new SystemMessage(ReflectionEvaluatePrompt.REFLECTION_EVALUATE_SYSTEM_PROMPT),
                new UserMessage(userPrompt)
        )));
    }

    public static String parseReflectionResult(String reflectionResult) {
        for (String separator : List.of("## New Workflow Design", " New Workflow Design")) {
            String[] parts = reflectionResult.split(java.util.regex.Pattern.quote(separator), 2);
            if (parts.length > 1) {
                return parts[1].strip();
            }
        }
        return reflectionResult;
    }

    public String design(String userInput, String toolList) {
        LOGGER.info("Starting complete workflow design process (SE design)");
        LOGGER.debug("Step 1/3: Basic design");
        String basicResult = basicDesign(userInput, toolList);
        LOGGER.debug("Basic design completed");

        LOGGER.debug("Step 2/3: Branch design");
        String branchResult = branchDesign(userInput, basicResult);
        LOGGER.debug("Branch design completed");

        LOGGER.debug("Step 3/3: Reflection evaluation");
        String reflectionResult = reflectionEvaluation(userInput, basicResult, branchResult);
        LOGGER.debug("Reflection evaluation completed");
        LOGGER.debug("SE workflow design process completed");
        return reflectionResult;
    }

    private String invoke(List<BaseMessage> messages) {
        AssistantMessage response = llm.invoke(messages).toCompletableFuture().join();
        return response.getContentAsString();
    }
}
