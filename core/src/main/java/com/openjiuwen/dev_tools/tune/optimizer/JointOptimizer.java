/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.optimizer;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.operator.legacy.llm_call.LLMCall;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;
import com.openjiuwen.dev_tools.tune.TuneConstant;
import com.openjiuwen.dev_tools.tune.TuneUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

/**
 * Joint optimizer that alternates instruction and example optimization.
 *
 * <p>Mirrors Python's {@code JointOptimizer} in
 * {@code openjiuwen/dev_tools/tune/optimizer/joint_optimizer.py}.</p>
 */
public class JointOptimizer extends BaseOptimizer {

    private final InstructionOptimizer instructionOptimizer;
    private final ExampleOptimizer exampleOptimizer;
    private final ModelRequestConfig modelConfig;
    private final ModelClientConfig modelClientConfig;
    private final int numExamples;
    private final BooleanSupplier strategyChooser;
    private boolean optimizeInstruction = true;

    public JointOptimizer(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this(modelConfig, modelClientConfig, null, TuneConstant.DEFAULT_EXAMPLE_NUM);
    }

    public JointOptimizer(ModelRequestConfig modelConfig,
                          ModelClientConfig modelClientConfig,
                          int numExamples) {
        this(modelConfig, modelClientConfig, null, numExamples);
    }

    public JointOptimizer(ModelRequestConfig modelConfig,
                          ModelClientConfig modelClientConfig,
                          Map<String, LLMCall> parameters) {
        this(modelConfig, modelClientConfig, parameters, TuneConstant.DEFAULT_EXAMPLE_NUM);
    }

    public JointOptimizer(ModelRequestConfig modelConfig,
                          ModelClientConfig modelClientConfig,
                          Map<String, LLMCall> parameters,
                          int numExamples) {
        this(modelConfig, modelClientConfig, parameters, numExamples,
                () -> ThreadLocalRandom.current().nextBoolean());
    }

    JointOptimizer(ModelRequestConfig modelConfig,
                   ModelClientConfig modelClientConfig,
                   Map<String, LLMCall> parameters,
                   int numExamples,
                   BooleanSupplier strategyChooser) {
        this(
                modelConfig,
                modelClientConfig,
                numExamples,
                new InstructionOptimizer(modelConfig, modelClientConfig),
                new ExampleOptimizer(modelConfig, modelClientConfig, null, numExamples),
                parameters,
                strategyChooser
        );
    }

    JointOptimizer(InstructionOptimizer instructionOptimizer,
                   ExampleOptimizer exampleOptimizer,
                   Map<String, LLMCall> parameters,
                   BooleanSupplier strategyChooser) {
        this(null,
                null,
                exampleOptimizer == null ? TuneConstant.DEFAULT_EXAMPLE_NUM : exampleOptimizer.getNumExamples(),
                instructionOptimizer,
                exampleOptimizer,
                parameters,
                strategyChooser);
    }

    private JointOptimizer(ModelRequestConfig modelConfig,
                           ModelClientConfig modelClientConfig,
                           int numExamples,
                           InstructionOptimizer instructionOptimizer,
                           ExampleOptimizer exampleOptimizer,
                           Map<String, LLMCall> parameters,
                           BooleanSupplier strategyChooser) {
        super(null);
        this.modelConfig = modelConfig;
        this.modelClientConfig = modelClientConfig;
        this.numExamples = numExamples;
        this.instructionOptimizer = instructionOptimizer;
        this.exampleOptimizer = exampleOptimizer;
        this.strategyChooser = strategyChooser;
        bindParameter(parameters);
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
    public void bindParameter(Map<String, LLMCall> llmCalls) {
        super.bindParameter(llmCalls);
        if (instructionOptimizer != null) {
            instructionOptimizer.bindParameter(copyParameters(llmCalls));
        }
        if (exampleOptimizer != null) {
            exampleOptimizer.bindParameter(copyParameters(llmCalls));
        }
    }

    @Override
    protected void doBackward(List<EvaluatedCase> evaluatedCases) {
        List<EvaluatedCase> cases = evaluatedCases == null ? List.of() : evaluatedCases;
        exampleOptimizer.initExamples(cases);
        selectOptimizeStrategy();
        for (Map.Entry<String, TextualParameter> entry : parameters.entrySet()) {
            Map<String, TextualParameter> backwardParameters;
            if (optimizeInstruction) {
                instructionOptimizer.backward(cases);
                backwardParameters = instructionOptimizer.parameters();
            } else {
                exampleOptimizer.backward(cases);
                backwardParameters = exampleOptimizer.parameters();
            }
            TextualParameter backwardParameter = backwardParameters.get(entry.getKey());
            entry.getValue().setGradient("system_prompt", backwardParameter.getGradient("system_prompt"));
            entry.getValue().setGradient("user_prompt", backwardParameter.getGradient("user_prompt"));
        }
    }

    @Override
    protected void doUpdate() {
        if (optimizeInstruction) {
            instructionOptimizer.update();
        }
        Map<String, TextualParameter> instructionParameters = instructionOptimizer.parameters();
        Map<String, TextualParameter> exampleParameters = exampleOptimizer.parameters();
        for (Map.Entry<String, TextualParameter> entry : parameters.entrySet()) {
            String name = entry.getKey();
            LLMCall llmCall = entry.getValue().getLlmCall();
            TextualParameter instructionParameter = instructionParameters.get(name);
            TextualParameter exampleParameter = exampleParameters.get(name);

            if (!llmCall.getFreezeUserPrompt()) {
                String optimizedPrompt = exampleOptimizer.formatPrompt(
                        instructionParameter.getLlmCall().getUserPrompt(),
                        exampleParameter.getGradient("user_prompt")
                );
                llmCall.updateUserPrompt(optimizedPrompt);
            }
            if (!llmCall.getFreezeSystemPrompt()) {
                String optimizedPrompt = TuneUtils.getContentStringFromTemplate(
                        instructionParameter.getLlmCall().getSystemPrompt()
                );
                if (llmCall.getFreezeUserPrompt()) {
                    optimizedPrompt = exampleOptimizer.formatPrompt(
                            instructionParameter.getLlmCall().getSystemPrompt(),
                            exampleParameter.getGradient("system_prompt")
                    );
                }
                llmCall.updateSystemPrompt(optimizedPrompt);
            }
        }
    }

    void selectOptimizeStrategy() {
        boolean needOptimizeExample = exampleOptimizer.getNumExamples() > 0;
        optimizeInstruction = needOptimizeExample ? strategyChooser.getAsBoolean() : true;
    }

    boolean isOptimizeInstruction() {
        return optimizeInstruction;
    }

    InstructionOptimizer getInstructionOptimizer() {
        return instructionOptimizer;
    }

    ExampleOptimizer getExampleOptimizer() {
        return exampleOptimizer;
    }

    private static Map<String, LLMCall> copyParameters(Map<String, LLMCall> llmCalls) {
        return llmCalls == null ? null : new LinkedHashMap<>(llmCalls);
    }
}
