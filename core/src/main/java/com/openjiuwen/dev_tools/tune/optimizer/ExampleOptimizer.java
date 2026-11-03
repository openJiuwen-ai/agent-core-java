/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.optimizer;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.operator.legacy.llm_call.LLMCall;
import com.openjiuwen.dev_tools.tune.Case;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;
import com.openjiuwen.dev_tools.tune.TuneConstant;
import com.openjiuwen.dev_tools.tune.TuneUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Few-shot example optimizer for legacy prompt tuning.
 *
 * <p>Mirrors Python's {@code ExampleOptimizer} in
 * {@code openjiuwen/dev_tools/tune/optimizer/example_optimizer.py}.</p>
 */
public class ExampleOptimizer extends BaseOptimizer {

    public static final PromptTemplate EXAMPLE_SELECTION_TEMPLATE = PromptTemplate.builder()
            .content("""
                    作为提示词优化专家,我的任务是帮助代理高效且成功地完成任务。
                    当前任务描述:
                    [任务描述]
                    {{task_description}}
                    请从以下回答错误的数据或正确但又代表性的示例集合中选择最具代表性的{{num_examples}}个示例,以解决上述任务中的任何问题。
                    当前的错误示例集是
                    {{examples}}

                    选择出最具代表性示例集的标号,用列表形式输出,输出格式为:
                    ```list
                    [索引1, 索引2,...]
                    ```
                    例如输出3个示例:
                    ```list
                    [0, 2, 4]
                    ```
                    1. 输出的索引列表必须满足{{num_examples}}个
                    2. 输出必须被'```list```'包裹

                    [请选择示例]
                    """)
            .build();

    private final Model model;
    private final int numExamples;

    public ExampleOptimizer(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this(modelConfig, modelClientConfig, null, TuneConstant.DEFAULT_EXAMPLE_NUM);
    }

    public ExampleOptimizer(ModelRequestConfig modelConfig,
                            ModelClientConfig modelClientConfig,
                            Map<String, LLMCall> parameters,
                            int numExamples) {
        super(parameters);
        model = new Model(modelClientConfig, modelConfig);
        if (numExamples < TuneConstant.MIN_EXAMPLE_NUM || numExamples > TuneConstant.MAX_EXAMPLE_NUM) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOLCHAIN_OPTIMIZER_PARAM_ERROR,
                    "error_msg",
                    "num_examples should be between " + TuneConstant.MIN_EXAMPLE_NUM + " and "
                            + TuneConstant.MAX_EXAMPLE_NUM
            );
        }
        this.numExamples = numExamples;
    }

    public int getNumExamples() {
        return numExamples;
    }

    public int num_examples() {
        return getNumExamples();
    }

    @Override
    protected void doBackward(List<EvaluatedCase> evaluatedCases) {
        if (numExamples <= 0) {
            Loggers.AGENT.info("skip do example optimization.");
            return;
        }

        for (TextualParameter parameter : parameters.values()) {
            LLMCall llmCall = parameter.getLlmCall();
            List<Case> selectedExamples = selectBestExamples(
                    llmCall.getSystemPrompt(),
                    llmCall.getUserPrompt(),
                    evaluatedCases
            );
            String examples = TuneUtils.convertCasesToExamples(selectedExamples);
            if (!llmCall.getFreezeSystemPrompt()) {
                parameter.setGradient("system_prompt", examples);
            }
            if (!llmCall.getFreezeUserPrompt()) {
                parameter.setGradient("user_prompt", examples);
            }
        }
    }

    @Override
    protected void doUpdate() {
        for (TextualParameter parameter : parameters.values()) {
            LLMCall llmCall = parameter.getLlmCall();
            if (!llmCall.getFreezeUserPrompt()) {
                String optimizedPrompt = formatPrompt(llmCall.getUserPrompt(), parameter.getGradient("user_prompt"));
                llmCall.updateUserPrompt(optimizedPrompt);
            } else if (!llmCall.getFreezeSystemPrompt()) {
                String optimizedPrompt = formatPrompt(llmCall.getSystemPrompt(), parameter.getGradient("system_prompt"));
                llmCall.updateSystemPrompt(optimizedPrompt);
            }
        }
    }

    public void initExamples(List<EvaluatedCase> evaluatedCases) {
        List<Case> preSelectedExamples = sampleExample(numExamples, safeEvaluatedCases(evaluatedCases));
        String examples = TuneUtils.convertCasesToExamples(preSelectedExamples);
        for (TextualParameter parameter : parameters.values()) {
            LLMCall llmCall = parameter.getLlmCall();
            if (!llmCall.getFreezeSystemPrompt()) {
                parameter.setGradient("system_prompt", examples);
            }
            if (!llmCall.getFreezeUserPrompt()) {
                parameter.setGradient("user_prompt", examples);
            }
        }
    }

    public void init_examples(List<EvaluatedCase> evaluatedCases) {
        initExamples(evaluatedCases);
    }

    public String formatPrompt(PromptTemplate prompt, String gradient) {
        String content = TuneUtils.getContentStringFromTemplate(prompt);
        if (gradient == null) {
            return content;
        }
        return String.join("\n", content, gradient);
    }

    public String format_prompt(PromptTemplate prompt, String gradient) {
        return formatPrompt(prompt, gradient);
    }

    List<Case> sampleExample(int requestedExamples, List<EvaluatedCase> evaluatedCases) {
        List<EvaluatedCase> dataset = safeEvaluatedCases(evaluatedCases);
        List<EvaluatedCase> errorCases = getBadCases(dataset);
        if (requestedExamples >= dataset.size()) {
            return dataset.stream().map(EvaluatedCase::getCase).collect(Collectors.toCollection(ArrayList::new));
        }

        List<EvaluatedCase> sampledExamples = new ArrayList<>();
        if (!errorCases.isEmpty()) {
            int numErrorExamples = Math.min(requestedExamples, errorCases.size());
            sampledExamples.addAll(sample(errorCases, numErrorExamples));
        }

        if (sampledExamples.size() < requestedExamples) {
            int remainingCount = requestedExamples - sampledExamples.size();
            List<EvaluatedCase> remainingExamples = new ArrayList<>(dataset);
            remainingExamples.removeAll(sampledExamples);
            sampledExamples.addAll(sample(remainingExamples, remainingCount));
        } else {
            sampledExamples = sample(sampledExamples, requestedExamples);
        }
        return sampledExamples.stream().map(EvaluatedCase::getCase).collect(Collectors.toCollection(ArrayList::new));
    }

    List<Case> fillMissingExample(List<Case> selectedExamples, List<EvaluatedCase> evaluatedCases) {
        List<Case> selected = selectedExamples == null ? new ArrayList<>() : new ArrayList<>(selectedExamples);
        List<EvaluatedCase> cases = safeEvaluatedCases(evaluatedCases);
        int numToSelect = Math.min(numExamples, cases.size());
        int fillCount = numToSelect - selected.size();
        if (fillCount <= 0) {
            return selected;
        }
        Set<String> selectedCaseIds = selected.stream()
                .map(Case::getCaseId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Case> remainingCases = cases.stream()
                .map(EvaluatedCase::getCase)
                .filter(caseValue -> !selectedCaseIds.contains(caseValue.getCaseId()))
                .collect(Collectors.toCollection(ArrayList::new));
        selected.addAll(sample(remainingCases, fillCount));
        return selected;
    }

    List<Case> selectBestExamples(PromptTemplate systemPrompt,
                                  PromptTemplate userPrompt,
                                  List<EvaluatedCase> evaluatedCases) {
        List<EvaluatedCase> cases = safeEvaluatedCases(evaluatedCases);
        List<Case> preSelectedExamples = sampleExamplesFromCases(cases);
        if (preSelectedExamples.size() <= numExamples) {
            return preSelectedExamples;
        }

        String examplesString = "";
        for (int index = 0; index < preSelectedExamples.size(); index++) {
            Case example = preSelectedExamples.get(index);
            examplesString += "index: " + index + "\n"
                    + "question: " + pythonStyleValue(example.getInputs()) + "\n"
                    + "assistant answer: " + pythonStyleValue(example.getLabel());
            if (index + 1 < preSelectedExamples.size()) {
                examplesString += "\n";
            }
        }

        Map<String, Object> keywords = new LinkedHashMap<>();
        keywords.put("task_description", TuneUtils.getContentStringFromTemplate(systemPrompt) + "\n"
                + TuneUtils.getContentStringFromTemplate(userPrompt));
        keywords.put("num_examples", numExamples);
        keywords.put("examples", examplesString);
        List<BaseMessage> messages = EXAMPLE_SELECTION_TEMPLATE.format(keywords).toMessages();

        try {
            String response = model.invoke(messages).toCompletableFuture().join().getContentAsString();
            List<Case> selectedExamples = extractSelectedExamplesFromResponse(response, preSelectedExamples);
            if (selectedExamples.size() < numExamples) {
                selectedExamples = fillMissingExample(selectedExamples, cases);
            }
            return selectedExamples;
        } catch (RuntimeException exception) {
            Loggers.AGENT.warning("Error occur while selecting best examples: {}", exception.getMessage());
            return sampleExample(numExamples, cases);
        }
    }

    List<Case> sampleExamplesFromCases(List<EvaluatedCase> evaluatedCases) {
        List<EvaluatedCase> cases = safeEvaluatedCases(evaluatedCases);
        if (numExamples >= cases.size()) {
            return cases.stream().map(EvaluatedCase::getCase).collect(Collectors.toCollection(ArrayList::new));
        }

        List<Case> examples = badCases.stream()
                .map(EvaluatedCase::getCase)
                .collect(Collectors.toCollection(ArrayList::new));
        if (examples.size() > TuneConstant.DEFAULT_MAX_NUM_SAMPLE_ERROR_CASES) {
            return sample(examples, TuneConstant.DEFAULT_MAX_NUM_SAMPLE_ERROR_CASES);
        }

        if (examples.size() < Math.min(numExamples, cases.size())) {
            examples = fillMissingExample(examples, cases);
        }
        return examples;
    }

    List<Case> extractSelectedExamplesFromResponse(String response, List<Case> errorCases) {
        List<Object> bestExampleList = TuneUtils.parseListFromLlmResponse(response);
        if (bestExampleList == null) {
            throw new IllegalArgumentException("LLM response does not contain a valid list block");
        }
        List<Case> selectedExamples = new ArrayList<>();
        int limit = Math.min(numExamples, bestExampleList.size());
        for (int index = 0; index < limit; index++) {
            Object rawIndex = bestExampleList.get(index);
            if (!(rawIndex instanceof Number number)) {
                throw new IllegalArgumentException("example index must be numeric");
            }
            selectedExamples.add(errorCases.get(number.intValue()));
        }
        return selectedExamples;
    }

    private static List<EvaluatedCase> safeEvaluatedCases(List<EvaluatedCase> evaluatedCases) {
        return evaluatedCases == null ? List.of() : evaluatedCases;
    }

    private static <T> List<T> sample(List<T> values, int count) {
        if (count <= 0 || values.isEmpty()) {
            return new ArrayList<>();
        }
        if (count >= values.size()) {
            return new ArrayList<>(values);
        }
        List<T> shuffled = new ArrayList<>(values);
        Collections.shuffle(shuffled);
        return new ArrayList<>(shuffled.subList(0, count));
    }

    private static String pythonStyleValue(Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof String text) {
            return "'" + text + "'";
        }
        if (value instanceof Boolean bool) {
            return bool ? "True" : "False";
        }
        if (value instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                parts.add(pythonStyleValue(entry.getKey()) + ": " + pythonStyleValue(entry.getValue()));
            }
            return "{" + String.join(", ", parts) + "}";
        }
        if (value instanceof List<?> list) {
            return "[" + list.stream().map(ExampleOptimizer::pythonStyleValue).collect(Collectors.joining(", ")) + "]";
        }
        return String.valueOf(value);
    }
}
