/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.optimizer.adopt;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.operator.legacy.llm_call.LLMCall;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;
import com.openjiuwen.dev_tools.tune.TuneUtils;
import com.openjiuwen.dev_tools.tune.optimizer.InstructionOptimizer;
import com.openjiuwen.dev_tools.tune.optimizer.TextualParameter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ADOPT local-gradient optimizer.
 *
 * <p>Mirrors Python's {@code PartialOptimizer} in
 * {@code openjiuwen/dev_tools/tune/optimizer/adopt/adopt_optimizer.py}.</p>
 */
public class PartialOptimizer extends InstructionOptimizer {

    private final Model model;
    private final String modelName;

    public PartialOptimizer(Model model, String modelName) {
        this(model, modelName, null);
    }

    public PartialOptimizer(Model model, String modelName, Map<String, LLMCall> parameters) {
        super(Objects.requireNonNull(model, "model"), parameters);
        this.model = model;
        this.modelName = modelName;
    }

    @Override
    protected String getTextualGradient(String name, TextualParameter parameter, Object tools) {
        List<String> localGradients = calculateTextualGradientByBadCases(parameter, tools);
        if (localGradients.isEmpty()) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOLCHAIN_OPTIMIZER_BACKWARD_EXECUTION_ERROR,
                    "error_msg",
                    "Calculate local gradient of parameter: " + name + " failed."
            );
        }
        return reduceTextualGradient(parameter, localGradients);
    }

    List<String> calculateTextualGradientByBadCases(TextualParameter parameter, Object tools) {
        String nodePrompt = nodePrompt(parameter);
        List<String> localGradients = new ArrayList<>();
        for (EvaluatedCase badCase : badCases) {
            Map<String, Object> keywords = new LinkedHashMap<>();
            keywords.put("node_job", parameter.getDescription());
            keywords.put("node_input", String.valueOf(badCase.getInputs().get("inputs")));
            keywords.put("node_output", String.valueOf(badCase.getAnswer().get("answer")));
            keywords.put("modification", badCase.getReason());
            keywords.put("node_expected_output", String.valueOf(badCase.getLabel().get("label")));
            keywords.put("node_prompt", nodePrompt);
            List<BaseMessage> messages = new ArrayList<>(AdoptTemplates.GRADIENT_GENERATE_SYSTEM_PROMPT.toMessages());
            messages.addAll(AdoptTemplates.GRADIENT_GENERATE_USER_PROMPT.format(keywords).toMessages());
            localGradients.add(invokeModel(messages));
        }
        return localGradients;
    }

    String reduceTextualGradient(TextualParameter parameter, List<String> localGradients) {
        Map<String, Object> keywords = new LinkedHashMap<>();
        keywords.put("node_job", parameter.getDescription());
        keywords.put("all_reasons", String.valueOf(localGradients));
        keywords.put("current_prompt", nodePrompt(parameter));
        List<BaseMessage> messages = new ArrayList<>(AdoptTemplates.GRADIENT_REDUCE_SYSTEM_PROMPT.toMessages());
        messages.addAll(AdoptTemplates.GRADIENT_REDUCE_USER_PROMPT.format(keywords).toMessages());
        return invokeModel(messages);
    }

    private String invokeModel(List<BaseMessage> messages) {
        ModelInvokeOptions options = ModelInvokeOptions.builder()
                .model(modelName)
                .build();
        return model.invoke(messages, options).toCompletableFuture().join().getContentAsString();
    }

    private static String nodePrompt(TextualParameter parameter) {
        return "system prompt: " + TuneUtils.getContentStringFromTemplate(parameter.getLlmCall().getSystemPrompt())
                + "\nuser prompt: " + TuneUtils.getContentStringFromTemplate(parameter.getLlmCall().getUserPrompt());
    }
}
