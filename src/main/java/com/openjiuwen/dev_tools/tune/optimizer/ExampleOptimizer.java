/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.optimizer;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.operator.legacy.llm_call.LLMCall;
import com.openjiuwen.dev_tools.tune.Case;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;
import com.openjiuwen.dev_tools.tune.TuneConstant;
import com.openjiuwen.dev_tools.tune.TuneUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Example optimizer for prompt tuning.
 *
 * <p>Mirrors Python's {@code ExampleOptimizer} in {@code openjiuwen.dev_tools.tune.optimizer.example_optimizer}.
 */
public class ExampleOptimizer extends BaseOptimizer {

    private final Model model;
    private final int numExamples;

    /**
     * Creates an ExampleOptimizer.
     *
     * @param modelConfig the model request configuration
     * @param modelClientConfig the model client configuration
     * @param parameters the LLM call parameters
     * @param numExamples the number of examples to select
     */
    public ExampleOptimizer(ModelRequestConfig modelConfig,
                           ModelClientConfig modelClientConfig,
                           Map<String, LLMCall> parameters,
                           int numExamples) {
        super(parameters);
        this.model = new Model(modelClientConfig, modelConfig);
        
        if (numExamples < TuneConstant.MIN_EXAMPLE_NUM || numExamples > TuneConstant.MAX_EXAMPLE_NUM) {
            throw new IllegalArgumentException(
                    "num_examples should be between " + TuneConstant.MIN_EXAMPLE_NUM + 
                    " and " + TuneConstant.MAX_EXAMPLE_NUM
            );
        }
        this.numExamples = numExamples;
    }

    /**
     * Gets the number of examples.
     *
     * @return the number of examples
     */
    public int getNumExamples() {
        return numExamples;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    protected void doBackward(List<EvaluatedCase> evaluatedCases) {
        if (numExamples <= 0) {
            return;
        }

        for (Map.Entry<String, TextualParameter> entry : parameters.entrySet()) {
            TextualParameter param = entry.getValue();
            
            List<Case> selectedExamples = selectBestExamples(
                    param.getLlmCall().getSystemPrompt(),
                    param.getLlmCall().getUserPrompt(),
                    evaluatedCases
            );
            
            if (!param.getLlmCall().getFreezeSystemPrompt()) {
                param.setGradient("system_prompt", TuneUtils.convertCasesToExamples(selectedExamples));
            }
            if (!param.getLlmCall().getFreezeUserPrompt()) {
                param.setGradient("user_prompt", TuneUtils.convertCasesToExamples(selectedExamples));
            }
        }
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    protected void doUpdate() {
        for (Map.Entry<String, TextualParameter> entry : parameters.entrySet()) {
            TextualParameter param = entry.getValue();
            
            if (!param.getLlmCall().getFreezeUserPrompt()) {
                String optimizedPrompt = formatPrompt(
                        param.getLlmCall().getUserPrompt(),
                        param.getGradient("user_prompt").orElse(null)
                );
                param.getLlmCall().updateUserPrompt(optimizedPrompt);
            } else if (!param.getLlmCall().getFreezeSystemPrompt()) {
                String optimizedPrompt = formatPrompt(
                        param.getLlmCall().getSystemPrompt(),
                        param.getGradient("system_prompt").orElse(null)
                );
                param.getLlmCall().updateSystemPrompt(optimizedPrompt);
            }
        }
    }

    /**
     * Initializes examples from evaluated cases.
     *
     * @param evaluatedCases the evaluated cases
     */
    public void initExamples(List<EvaluatedCase> evaluatedCases) {
        List<Case> preSelectedExamples = sampleExamples(numExamples, evaluatedCases);
        
        for (TextualParameter param : parameters.values()) {
            if (!param.getLlmCall().getFreezeSystemPrompt()) {
                param.setGradient("system_prompt", TuneUtils.convertCasesToExamples(preSelectedExamples));
            }
            if (!param.getLlmCall().getFreezeUserPrompt()) {
                param.setGradient("user_prompt", TuneUtils.convertCasesToExamples(preSelectedExamples));
            }
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String formatPrompt(PromptTemplate prompt, String gradient) {
        String content = TuneUtils.getContentStringFromTemplate(prompt);
        if (gradient == null || gradient.isEmpty()) {
            return content;
        }
        return content + "\n" + gradient;
    }

    private List<Case> sampleExamples(int num, List<EvaluatedCase> evaluatedCases) {
        if (num >= evaluatedCases.size()) {
            return evaluatedCases.stream().map(EvaluatedCase::getCase).toList();
        }

        List<EvaluatedCase> sampled = new ArrayList<>();
        List<EvaluatedCase> errors = new ArrayList<>(badCases);
        
        if (!errors.isEmpty()) {
            int numError = Math.min(num, errors.size());
            Collections.shuffle(errors);
            sampled.addAll(errors.subList(0, numError));
        }

        if (sampled.size() < num) {
            int remaining = num - sampled.size();
            List<EvaluatedCase> remainingCases = evaluatedCases.stream()
                    .filter(c -> !sampled.contains(c))
                    .collect(Collectors.toList());
            Collections.shuffle(remainingCases);
            sampled.addAll(remainingCases.subList(0, Math.min(remaining, remainingCases.size())));
        }

        return sampled.stream().map(EvaluatedCase::getCase).toList();
    }

    private List<Case> selectBestExamples(PromptTemplate systemPrompt,
                                          PromptTemplate userPrompt,
                                          List<EvaluatedCase> evaluatedCases) {
        List<Case> preSelected = sampleExamplesFromCases(evaluatedCases);
        
        if (preSelected.size() <= numExamples) {
            return preSelected;
        }

        StringBuilder examplesString = new StringBuilder();
        for (int i = 0; i < preSelected.size(); i++) {
            Case example = preSelected.get(i);
            examplesString.append(String.format(
                    "index: %d\nquestion: %s\nassistant answer: %s\n",
                    i, example.getInputs(), example.getLabel()
            ));
        }

        String prompt = String.format(
                EXAMPLE_SELECTION_TEMPLATE,
                TuneUtils.getContentStringFromTemplate(systemPrompt) + "\n" +
                TuneUtils.getContentStringFromTemplate(userPrompt),
                numExamples,
                examplesString.toString()
        );

        try {
            String response = model.invoke(prompt, null, null, null, null, null, null, null, null, null)
                    .getContentAsString();
            Optional<List<Object>> indices = TuneUtils.parseListFromLlmResponse(response);
            
            if (indices.isPresent()) {
                return indices.get().stream()
                        .mapToInt(obj -> ((Number) obj).intValue())
                        .filter(i -> i >= 0 && i < preSelected.size())
                        .limit(numExamples)
                        .mapToObj(preSelected::get)
                        .toList();
            }
        } catch (Exception e) {
            return sampleExamples(numExamples, evaluatedCases);
        }

        return sampleExamples(numExamples, evaluatedCases);
    }

    private List<Case> sampleExamplesFromCases(List<EvaluatedCase> evaluatedCases) {
        if (numExamples >= evaluatedCases.size()) {
            return evaluatedCases.stream().map(EvaluatedCase::getCase).toList();
        }

        List<Case> examples = badCases.stream()
                .limit(TuneConstant.DEFAULT_MAX_NUM_SAMPLE_ERROR_CASES)
                .map(EvaluatedCase::getCase)
                .collect(Collectors.toList());

        if (examples.size() < Math.min(numExamples, evaluatedCases.size())) {
            examples = fillMissingExamples(examples, evaluatedCases);
        }

        return examples;
    }

    private List<Case> fillMissingExamples(List<Case> selected, List<EvaluatedCase> evaluatedCases) {
        int numToSelect = Math.min(numExamples, evaluatedCases.size());
        int numToFill = numToSelect - selected.size();
        
        Set<String> selectedIds = selected.stream()
                .map(Case::getCaseId)
                .collect(Collectors.toSet());
        
        List<Case> remaining = evaluatedCases.stream()
                .map(EvaluatedCase::getCase)
                .filter(c -> !selectedIds.contains(c.getCaseId()))
                .collect(Collectors.toList());
        
        Collections.shuffle(remaining);
        selected.addAll(remaining.subList(0, Math.min(numToFill, remaining.size())));
        
        return selected;
    }

    private static final String EXAMPLE_SELECTION_TEMPLATE = """
            作为提示词优化专家，从以下示例中选择最具代表性的%d个示例：
            
            任务描述：
            %s
            
            示例集合：
            %s
            
            请输出选择的示例索引列表，格式为：[索引1, 索引2, ...]
            """;
}
