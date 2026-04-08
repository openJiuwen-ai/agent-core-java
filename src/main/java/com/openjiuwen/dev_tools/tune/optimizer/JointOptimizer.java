/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.optimizer;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.operator.legacy.llm_call.LLMCall;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;
import com.openjiuwen.dev_tools.tune.TuneConstant;
import com.openjiuwen.dev_tools.tune.TuneUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Joint optimizer combining instruction and example optimization.
 *
 * <p>Mirrors Python's {@code JointOptimizer} in {@code openjiuwen.dev_tools.tune.optimizer.joint_optimizer}.
 */
public class JointOptimizer extends BaseOptimizer {

    private final InstructionOptimizer instructionOptimizer;
    private final ExampleOptimizer exampleOptimizer;
    private final ModelRequestConfig modelConfig;
    private final ModelClientConfig modelClientConfig;
    private final int numExamples;
    private boolean optimizeInstruction;

    /**
     * Creates a legacy-compatible JointOptimizer without bound prompt parameters.
     *
     * @param modelConfig the model request configuration
     * @param modelClientConfig the model client configuration
     * @param numExamples the number of examples to select
     */
    public JointOptimizer(ModelRequestConfig modelConfig,
                         ModelClientConfig modelClientConfig,
                         int numExamples) {
        this(modelConfig, modelClientConfig, new HashMap<>(), numExamples);
    }

    /**
     * Creates a JointOptimizer.
     *
     * @param modelConfig the model request configuration
     * @param modelClientConfig the model client configuration
     * @param parameters the LLM call parameters
     * @param numExamples the number of examples to select
     */
    public JointOptimizer(ModelRequestConfig modelConfig,
                         ModelClientConfig modelClientConfig,
                         Map<String, LLMCall> parameters,
                         int numExamples) {
        super(parameters);
        validateNumExamples(numExamples);
        this.modelConfig = modelConfig;
        this.modelClientConfig = modelClientConfig;
        this.numExamples = numExamples;
        
        // Create deep copies of parameters for sub-optimizers
        Map<String, LLMCall> instructionParams = copyParameters(parameters);
        Map<String, LLMCall> exampleParams = copyParameters(parameters);
        
        this.instructionOptimizer = new InstructionOptimizer(modelConfig, modelClientConfig, instructionParams);
        this.exampleOptimizer = new ExampleOptimizer(modelConfig, modelClientConfig, exampleParams, numExamples);
        this.optimizeInstruction = true;
    }

    public ModelRequestConfig getModelConfig() {
        return modelConfig;
    }

    public ModelClientConfig getModelClientConfig() {
        return modelClientConfig;
    }

    public int getNumExamples() {
        return numExamples;
    }

    @Override
    public void bindParameter(Map<String, LLMCall> params) {
        super.bindParameter(params);
        if (instructionOptimizer != null) {
            instructionOptimizer.bindParameter(copyParameters(params));
        }
        if (exampleOptimizer != null) {
            exampleOptimizer.bindParameter(copyParameters(params));
        }
    }

    @Override
    protected void doBackward(List<EvaluatedCase> evaluatedCases) {
        exampleOptimizer.initExamples(evaluatedCases);
        selectOptimizeStrategy();

        if (optimizeInstruction) {
            instructionOptimizer.backward(evaluatedCases);
            Map<String, TextualParameter> backwardParams = instructionOptimizer.getParameters();
            
            for (Map.Entry<String, TextualParameter> entry : parameters.entrySet()) {
                String name = entry.getKey();
                TextualParameter param = entry.getValue();
                TextualParameter backwardParam = backwardParams.get(name);
                
                if (backwardParam != null) {
                    param.setGradient("system_prompt", backwardParam.getGradient("system_prompt").orElse(null));
                    param.setGradient("user_prompt", backwardParam.getGradient("user_prompt").orElse(null));
                }
            }
        } else {
            exampleOptimizer.backward(evaluatedCases);
            Map<String, TextualParameter> backwardParams = exampleOptimizer.getParameters();
            
            for (Map.Entry<String, TextualParameter> entry : parameters.entrySet()) {
                String name = entry.getKey();
                TextualParameter param = entry.getValue();
                TextualParameter backwardParam = backwardParams.get(name);
                
                if (backwardParam != null) {
                    param.setGradient("system_prompt", backwardParam.getGradient("system_prompt").orElse(null));
                    param.setGradient("user_prompt", backwardParam.getGradient("user_prompt").orElse(null));
                }
            }
        }
    }

    @Override
    protected void doUpdate() {
        if (optimizeInstruction) {
            instructionOptimizer.update();
        }

        Map<String, TextualParameter> instrParams = instructionOptimizer.getParameters();
        Map<String, TextualParameter> exampleParams = exampleOptimizer.getParameters();

        for (Map.Entry<String, TextualParameter> entry : parameters.entrySet()) {
            String name = entry.getKey();
            TextualParameter param = entry.getValue();
            TextualParameter instrParam = instrParams.get(name);
            TextualParameter exampleParam = exampleParams.get(name);

            if (!param.getLlmCall().getFreezeUserPrompt() && instrParam != null) {
                String optimizedPrompt = exampleOptimizer.formatPrompt(
                        instrParam.getLlmCall().getUserPrompt(),
                        exampleParam != null ? exampleParam.getGradient("user_prompt").orElse(null) : null
                );
                param.getLlmCall().updateUserPrompt(optimizedPrompt);
            }

            if (!param.getLlmCall().getFreezeSystemPrompt() && instrParam != null) {
                String optimizedPrompt = TuneUtils.getContentStringFromTemplate(
                        instrParam.getLlmCall().getSystemPrompt()
                );
                
                if (param.getLlmCall().getFreezeUserPrompt() && exampleParam != null) {
                    optimizedPrompt = exampleOptimizer.formatPrompt(
                            instrParam.getLlmCall().getSystemPrompt(),
                            exampleParam.getGradient("system_prompt").orElse(null)
                    );
                }
                param.getLlmCall().updateSystemPrompt(optimizedPrompt);
            }
        }
    }

    private void selectOptimizeStrategy() {
        boolean needOptimizeExample = exampleOptimizer.getNumExamples() > 0;
        optimizeInstruction = !needOptimizeExample || new Random().nextBoolean();
    }

    private void validateNumExamples(int numExamples) {
        if (numExamples < TuneConstant.MIN_EXAMPLE_NUM || numExamples > TuneConstant.MAX_EXAMPLE_NUM) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOLCHAIN_OPTIMIZER_PARAM_ERROR,
                    "error_msg",
                    "num_examples should be between " + TuneConstant.MIN_EXAMPLE_NUM
                            + " and " + TuneConstant.MAX_EXAMPLE_NUM
            );
        }
    }

    private Map<String, LLMCall> copyParameters(Map<String, LLMCall> params) {
        if (params == null) {
            return new HashMap<>();
        }
        // Note: In production, implement deep copy
        return new HashMap<>(params);
    }
}
