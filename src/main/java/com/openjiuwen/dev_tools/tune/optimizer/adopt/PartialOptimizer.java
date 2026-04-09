  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.dev_tools.tune.optimizer.adopt;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.operator.legacy.llm_call.LLMCall;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;
import com.openjiuwen.dev_tools.tune.TuneUtils;
import com.openjiuwen.dev_tools.tune.optimizer.InstructionOptimizer;
import com.openjiuwen.dev_tools.tune.optimizer.TextualParameter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Partial optimizer for ADOPT - extends InstructionOptimizer with local gradient calculation.
 *
 * <p>Mirrors Python's {@code PartialOptimizer} in {@code openjiuwen.dev_tools.tune.optimizer.adopt.adopt_optimizer}.
 */
public class PartialOptimizer extends InstructionOptimizer {

    /**
     * Creates a PartialOptimizer.
     *
     * @param model the model
     * @param modelName the model name
     * @param parameters the LLM call parameters
     */
    public PartialOptimizer(Model model, String modelName, Map<String, LLMCall> parameters) {
        super(model, parameters);
    }

    @Override
    protected String getTextualGradient(String name, TextualParameter param) {
        List<String> localGradients = calculateTextualGradientByBadCases(param);
        
        if (localGradients == null || localGradients.isEmpty()) {
            throw new IllegalStateException("Calculate local gradient of parameter: " + name + " failed.");
        }
        
        return reduceTextualGradient(param, localGradients);
    }

    private List<String> calculateTextualGradientByBadCases(TextualParameter param) {
        String nodePrompt = String.format(
                "system prompt: %s\nuser prompt: %s",
                TuneUtils.getContentStringFromTemplate(param.getLlmCall().getSystemPrompt()),
                TuneUtils.getContentStringFromTemplate(param.getLlmCall().getUserPrompt())
        );
        
        return badCases.stream()
                .map(badCase -> generateGradientForCase(param, nodePrompt, badCase))
                .toList();
    }

    private String generateGradientForCase(TextualParameter param, String nodePrompt, EvaluatedCase badCase) {
        String prompt = AdoptTemplates.GRADIENT_GENERATE_USER_PROMPT
                .replace("{{node_job}}", param.getDescription())
                .replace("{{node_input}}", String.valueOf(badCase.getInputs().get("inputs")))
                .replace("{{node_output}}", String.valueOf(badCase.getAnswer().get("answer")))
                .replace("{{modification}}", badCase.getReason())
                .replace("{{node_expected_output}}", String.valueOf(badCase.getLabel().get("label")))
                .replace("{{node_prompt}}", nodePrompt);
        
        return invokeModel(AdoptTemplates.GRADIENT_GENERATE_SYSTEM_PROMPT + "\n" + prompt);
    }

    private String reduceTextualGradient(TextualParameter param, List<String> localGradients) {
        String nodePrompt = String.format(
                "system prompt: %s\nuser prompt: %s",
                TuneUtils.getContentStringFromTemplate(param.getLlmCall().getSystemPrompt()),
                TuneUtils.getContentStringFromTemplate(param.getLlmCall().getUserPrompt())
        );
        
        String prompt = AdoptTemplates.GRADIENT_REDUCE_USER_PROMPT
                .replace("{{all_reasons}}", localGradients.toString())
                .replace("{{node_job}}", param.getDescription())
                .replace("{{current_prompt}}", nodePrompt);
        
        return invokeModel(AdoptTemplates.GRADIENT_REDUCE_SYSTEM_PROMPT + "\n" + prompt);
    }

    private String invokeModel(String prompt) {
        try {
            return model.invoke(prompt, null, null, null, null, null, null, null, null, null)
                    .getContentAsString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke partial optimizer model", e);
        }
    }
}
