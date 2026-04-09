  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.dev_tools.tune.optimizer;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.operator.legacy.llm_call.LLMCall;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;
import com.openjiuwen.dev_tools.tune.TuneConstant;
import com.openjiuwen.dev_tools.tune.TuneUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Instruction optimizer for prompt tuning.
 *
 * <p>Mirrors Python's {@code InstructionOptimizer} in {@code openjiuwen.dev_tools.tune.optimizer.instruction_optimizer}.
 */
public class InstructionOptimizer extends BaseOptimizer {

    protected final Model model;

    /**
     * Creates an InstructionOptimizer.
     *
     * @param modelConfig the model request configuration
     * @param modelClientConfig the model client configuration
     * @param parameters the LLM call parameters
     */
    public InstructionOptimizer(ModelRequestConfig modelConfig,
                                ModelClientConfig modelClientConfig,
                                Map<String, LLMCall> parameters) {
        this(new Model(modelClientConfig, modelConfig), parameters);
    }

    protected InstructionOptimizer(Model model, Map<String, LLMCall> parameters) {
        super(parameters);
        this.model = model;
    }

    @Override
    protected void doBackward(List<EvaluatedCase> evaluatedCases) {
        for (Map.Entry<String, TextualParameter> entry : parameters.entrySet()) {
            String name = entry.getKey();
            TextualParameter param = entry.getValue();
            
            String textualGradient = getTextualGradient(name, param);
            if (!param.getLlmCall().getFreezeSystemPrompt()) {
                param.setGradient("system_prompt", textualGradient);
            }
            if (!param.getLlmCall().getFreezeUserPrompt()) {
                param.setGradient("user_prompt", textualGradient);
            }
        }
    }

    @Override
    protected void doUpdate() {
        for (Map.Entry<String, TextualParameter> entry : parameters.entrySet()) {
            TextualParameter param = entry.getValue();
            
            boolean freezeSystem = param.getLlmCall().getFreezeSystemPrompt();
            boolean freezeUser = param.getLlmCall().getFreezeUserPrompt();
            
            if (!freezeSystem && !freezeUser) {
                optimizeBothSystemAndUserPrompt(param);
            } else if (!freezeSystem) {
                optimizeSystemOrUserPrompt(param, "system_prompt");
            } else if (!freezeUser) {
                optimizeSystemOrUserPrompt(param, "user_prompt");
            }
        }
    }

    private void optimizeBothSystemAndUserPrompt(TextualParameter param) {
        String systemPrompt = TuneUtils.getContentStringFromTemplate(param.getLlmCall().getSystemPrompt());
        String userPrompt = TuneUtils.getContentStringFromTemplate(param.getLlmCall().getUserPrompt());
        String gradient = param.getGradient("system_prompt").orElse("");
        
        // Use model to optimize prompts
        String optimizedSystemPrompt = optimizePrompt(systemPrompt, gradient);
        String optimizedUserPrompt = optimizePrompt(userPrompt, gradient);
        
        param.getLlmCall().updateSystemPrompt(optimizedSystemPrompt);
        param.getLlmCall().updateUserPrompt(optimizedUserPrompt);
    }

    private void optimizeSystemOrUserPrompt(TextualParameter param, String promptType) {
        PromptTemplate targetPrompt = promptType.equals("system_prompt")
                ? param.getLlmCall().getSystemPrompt()
                : param.getLlmCall().getUserPrompt();
        String gradient = param.getGradient(promptType).orElse("");
        
        String optimizedPrompt = optimizePrompt(
                TuneUtils.getContentStringFromTemplate(targetPrompt), 
                gradient
        );
        
        if (promptType.equals("system_prompt")) {
            param.getLlmCall().updateSystemPrompt(optimizedPrompt);
        } else {
            param.getLlmCall().updateUserPrompt(optimizedPrompt);
        }
    }

    protected String getTextualGradient(String name, TextualParameter param) {
        String systemPrompt = TuneUtils.getContentStringFromTemplate(param.getLlmCall().getSystemPrompt());
        String userPrompt = TuneUtils.getContentStringFromTemplate(param.getLlmCall().getUserPrompt());
        String badCasesString = getBadCasesString();
        
        String prompt = String.format(
                CREATE_TEXTUAL_GRADIENT_TEMPLATE,
                systemPrompt, userPrompt, badCasesString
        );

        try {
            return model.invoke(prompt, null, null, null, null, null, null, null, null, null)
                    .getContentAsString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate textual gradient", e);
        }
    }

    private String optimizePrompt(String prompt, String gradient) {
        String optimizationPrompt = String.format(
                PROMPT_OPTIMIZE_TEMPLATE,
                prompt, getBadCasesString(), gradient
        );
        try {
            String response = model.invoke(optimizationPrompt, null, null, null, null, null, null, null, null, null)
                    .getContentAsString();
            return extractOptimizedPrompt(response, "PROMPT_OPTIMIZED");
        } catch (Exception e) {
            throw new RuntimeException("Failed to optimize prompt", e);
        }
    }

    private String getBadCasesString() {
        StringBuilder sb = new StringBuilder();
        for (EvaluatedCase evalCase : badCases) {
            sb.append(String.format(
                    "[question]: %s\n[expected answer]: %s\n[assistant answer]: %s\n[reason]: %s\n===\n",
                    evalCase.getInputs(),
                    evalCase.getLabel(),
                    evalCase.getAnswer(),
                    evalCase.getReason()
            ));
        }
        return sb.toString();
    }

    private static String extractOptimizedPrompt(String response, String tag) {
        Pattern pattern = Pattern.compile("<" + tag + ">(.*?)</" + tag + ">", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(response);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).trim();
    }

    // Template constants
    private static final String CREATE_TEXTUAL_GRADIENT_TEMPLATE = """
            作为提示词优化专家，分析以下提示词在错误实例中的问题：
            
            System提示词：
            %s
            
            User提示词：
            %s
            
            错误实例：
            %s
            
            请分析指令可能出错的原因并提供改进建议。
            """;

    private static final String PROMPT_OPTIMIZE_TEMPLATE = """
            作为提示词优化专家，优化以下提示词：
            
            原始提示词：
            %s
            
            错误实例：
            %s
            
            分析反馈：
            %s
            
            请输出优化后的提示词。
            """;
}
