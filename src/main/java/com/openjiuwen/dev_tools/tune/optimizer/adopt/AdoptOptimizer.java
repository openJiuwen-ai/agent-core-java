/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.optimizer.adopt;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.operator.legacy.llm_call.LLMCall;
import com.openjiuwen.core.operator.legacy.llm_call.LegacyOptimizerCallback;
import com.openjiuwen.dev_tools.tune.Case;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;
import com.openjiuwen.dev_tools.tune.TuneUtils;
import com.openjiuwen.dev_tools.tune.optimizer.BaseOptimizer;
import com.openjiuwen.dev_tools.tune.optimizer.TextualParameter;
import com.openjiuwen.dev_tools.tune.optimizer.TraceNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ADOPT optimizer for decomposing workflow output feedback into node prompt gradients.
 *
 * <p>Mirrors Python's {@code AdoptOptimizer} in
 * {@code openjiuwen/dev_tools/tune/optimizer/adopt/adopt_optimizer.py}.</p>
 */
public class AdoptOptimizer extends BaseOptimizer {

    public static final int DEFAULT_BAD_CASES_SAMPLE_NUM = 5;
    public static final int DEFAULT_MODEL_RETRY_NUM = 5;
    public static final int DEFAULT_PARALLEL_NUM = 8;

    private final Model model;
    private final String modelName;
    private String agentDescription;
    private String constrain;
    private String externalKnowledge;
    private List<EvaluatedCase> goodCases = new ArrayList<>();

    public AdoptOptimizer(Model model, String modelName) {
        this(model, modelName, null, null);
    }

    public AdoptOptimizer(Model model, String modelName, Map<String, LLMCall> parameters) {
        this(model, modelName, parameters, null);
    }

    public AdoptOptimizer(Model model, String modelName, Map<String, LLMCall> parameters,
                          Map<String, Object> options) {
        super(parameters);
        this.modelName = modelName;
        this.model = new ModelWithRetry(Objects.requireNonNull(model, "model"), modelName);
        this.agentDescription = optionString(options, "agent_description", "No description");
        this.constrain = optionString(options, "constrain", "No constrain");
        this.externalKnowledge = optionString(options, "external_knowledge", "No external knowledge");
    }

    public void bindParameter(Map<String, LLMCall> parameters, Map<String, Object> options) {
        super.bindParameter(parameters);
        String updatedDescription = optionString(options, "agent_description", null);
        if (updatedDescription != null) {
            agentDescription = updatedDescription;
        }
    }

    public void bind_parameter(Map<String, LLMCall> parameters, Map<String, Object> options) {
        bindParameter(parameters, options);
    }

    @Override
    protected void doBackward(List<EvaluatedCase> evaluatedCases) {
        concludeJobForEachNode();
        Map<String, String> globalGradient = calculateGlobalGradient();
        List<GradientResult> partialGradients = calculatePartialGradients(globalGradient);
        for (GradientResult result : partialGradients) {
            TextualParameter parameter = parameters.get(result.nodeName());
            if (parameter == null) {
                continue;
            }
            if (!parameter.getLlmCall().getFreezeSystemPrompt()) {
                parameter.setGradient("system_prompt", result.systemPromptGradient());
            }
            if (!parameter.getLlmCall().getFreezeUserPrompt()) {
                parameter.setGradient("user_prompt", result.userPromptGradient());
            }
        }
    }

    @Override
    protected void doUpdate() {
        PartialOptimizer optimizer = new PartialOptimizer(model, modelName);
        Map<String, LLMCall> llmCalls = new LinkedHashMap<>();
        for (Map.Entry<String, TextualParameter> entry : parameters.entrySet()) {
            llmCalls.put(entry.getKey(), entry.getValue().getLlmCall());
        }
        optimizer.bindParameter(llmCalls);
        optimizer.update();
    }

    Map<String, String> calculateGlobalGradient() {
        if (badCases.isEmpty()) {
            return new LinkedHashMap<>();
        }
        int workers = Math.max(Math.min(DEFAULT_PARALLEL_NUM, badCases.size()), 1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<CompletableFuture<GradientEntry>> futures = new ArrayList<>();
            for (EvaluatedCase caseValue : badCases) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    String differences = analyzeOutputChange(caseValue);
                    String reflection = analyzeDeepOutput(caseValue, differences);
                    return new GradientEntry(caseValue.getCaseId(), differences + "\n\n" + reflection);
                }, executor));
            }
            Map<String, String> gradients = new LinkedHashMap<>();
            for (CompletableFuture<GradientEntry> future : futures) {
                GradientEntry entry = future.join();
                gradients.put(entry.caseId(), entry.gradient());
            }
            return gradients;
        } finally {
            executor.shutdown();
        }
    }

    List<GradientResult> calculatePartialGradients(Map<String, String> globalGradient) {
        int workers = Math.max(Math.min(DEFAULT_PARALLEL_NUM, badCases.size()), 1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Map.Entry<String, TextualParameter>> entries = new ArrayList<>(parameters.entrySet());
            List<CompletableFuture<GradientResult>> futures = new ArrayList<>();
            for (Map.Entry<String, TextualParameter> entry : entries) {
                futures.add(CompletableFuture.supplyAsync(
                        () -> generateTextualGradientForLlmCalls(entry.getKey(), entry.getValue(), globalGradient),
                        executor
                ));
            }
            List<GradientResult> results = new ArrayList<>();
            for (CompletableFuture<GradientResult> future : futures) {
                results.add(future.join());
            }
            return results;
        } finally {
            executor.shutdown();
        }
    }

    GradientResult generateTextualGradientForLlmCalls(String nodeName, TextualParameter parameter,
                                                      Map<String, String> globalGradient) {
        List<EvaluatedCase> nodeCases = new ArrayList<>();
        for (EvaluatedCase caseValue : badCases) {
            List<TraceNode> traceNodes = history.getLlmCallHistory(caseValue.getCaseId(), nodeName);
            if (traceNodes == null || traceNodes.isEmpty()) {
                Loggers.AGENT.warn("failed to get trace nodes for node case_id={} node_name={}",
                        caseValue.getCaseId(), nodeName);
                continue;
            }
            List<Map<String, Object>> inputList = traceNodes.stream()
                    .map(TraceNode::getInputs)
                    .collect(Collectors.toCollection(ArrayList::new));
            List<String> outputList = traceNodes.stream()
                    .map(TraceNode::getOutputs)
                    .collect(Collectors.toCollection(ArrayList::new));
            String response = expectedNodeOutput(caseValue, parameter, globalGradient, inputList, outputList);
            String revisedOutput = extractContentFromResponse(response, "REVISED_NODE_OUTPUT");

            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("inputs", inputList);
            Map<String, Object> label = new LinkedHashMap<>();
            label.put("label", revisedOutput);
            Map<String, Object> answer = new LinkedHashMap<>();
            answer.put("answer", outputList);
            nodeCases.add(new EvaluatedCase(new Case(inputs, label, null, caseValue.getCaseId()),
                    answer, 0.0d, response));
        }

        LLMCall llmCall = parameter.getLlmCall();
        LegacyOptimizerCallback callback = llmCall.getOptimizerCallback();
        llmCall.setOptimizerCallback(null);
        try {
            PartialOptimizer partialOptimizer = new PartialOptimizer(model, modelName, Map.of(nodeName, llmCall));
            partialOptimizer.backward(nodeCases);
            TextualParameter partialParameter = partialOptimizer.parameters().get(nodeName);
            return new GradientResult(
                    nodeName,
                    partialParameter.getGradient("system_prompt"),
                    partialParameter.getGradient("user_prompt")
            );
        } finally {
            llmCall.setOptimizerCallback(callback);
        }
    }

    private String expectedNodeOutput(EvaluatedCase caseValue, TextualParameter parameter,
                                      Map<String, String> globalGradient,
                                      List<Map<String, Object>> inputList, List<String> outputList) {
        Map<String, Object> keywords = new LinkedHashMap<>();
        keywords.put("workflow_output", String.valueOf(caseValue.getAnswer()));
        keywords.put("modification", globalGradient.getOrDefault(caseValue.getCaseId(), ""));
        keywords.put("dependency_from_this_workflow_final_output", parameter.getDescription());
        keywords.put("node_in_block", AdoptTemplates.buildNodeIoString(inputList, outputList));
        List<BaseMessage> messages = new ArrayList<>(AdoptTemplates.EXPECTED_OUTPUT_SYSTEM_PROMPT.toMessages());
        messages.addAll(AdoptTemplates.EXPECTED_OUTPUT_USER_PROMPT.format(keywords).toMessages());
        return invokeModel(messages);
    }

    private String analyzeOutputChange(EvaluatedCase caseValue) {
        Map<String, Object> keywords = new LinkedHashMap<>();
        keywords.put("workflow_description", agentDescription);
        keywords.put("current_output", String.valueOf(caseValue.getAnswer()));
        keywords.put("ground_truth", String.valueOf(caseValue.getLabel()));
        keywords.put("metric_fn", String.valueOf(caseValue.getReason()));
        keywords.put("current_score", String.valueOf(caseValue.getScore()));
        keywords.put("constrain", constrain);
        List<BaseMessage> messages = new ArrayList<>(AdoptTemplates.OUTPUT_CHANGE_SYSTEM_PROMPT.toMessages());
        messages.addAll(AdoptTemplates.OUTPUT_CHANGE_USER_PROMPT.format(keywords).toMessages());
        return invokeModel(messages);
    }

    private String analyzeDeepOutput(EvaluatedCase caseValue, String differences) {
        Map<String, Object> keywords = new LinkedHashMap<>();
        keywords.put("workflow_description", agentDescription);
        keywords.put("node_input", String.valueOf(caseValue.getInputs()));
        keywords.put("node_output", String.valueOf(caseValue.getAnswer()));
        keywords.put("node_expected_output", String.valueOf(caseValue.getLabel()));
        keywords.put("external_knowledge", externalKnowledge);
        keywords.put("shallow_difference", differences);
        keywords.put("constrain", constrain);
        List<BaseMessage> messages = new ArrayList<>(AdoptTemplates.DEEP_OUTPUT_ANALYSIS_SYSTEM_PROMPT.toMessages());
        messages.addAll(AdoptTemplates.DEEP_OUTPUT_ANALYSIS_USER_PROMPT.format(keywords).toMessages());
        return invokeModel(messages);
    }

    private void concludeJobForEachNode() {
        for (String name : parameters.keySet()) {
            concludeNode(name);
        }
    }

    private void concludeNode(String nodeName) {
        TextualParameter parameter = parameters.get(nodeName);
        if (parameter == null) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOLCHAIN_AGENT_PARAM_ERROR,
                    "error_msg",
                    "Cannot find parameter: " + nodeName
            );
        }
        Map<String, Object> keywords = new LinkedHashMap<>();
        keywords.put("node_name", nodeName);
        keywords.put("agent_description", agentDescription);
        keywords.put("system_prompt", TuneUtils.getContentStringFromTemplate(parameter.getLlmCall().getSystemPrompt()));
        keywords.put("user_prompt", TuneUtils.getContentStringFromTemplate(parameter.getLlmCall().getUserPrompt()));
        keywords.put("good_cases", TuneUtils.convertCasesToExamples(goodCases));
        List<BaseMessage> messages = new ArrayList<>(AdoptTemplates.CONCLUDE_NODE_SYSTEM_PROMPT.toMessages());
        messages.addAll(AdoptTemplates.CONCLUDE_NODE_USER_PROMPT.format(keywords).toMessages());
        parameter.setDescription(invokeModel(messages));
    }

    @Override
    protected List<EvaluatedCase> getBadCases(List<EvaluatedCase> evaluatedCases) {
        List<EvaluatedCase> safeCases = evaluatedCases == null ? List.of() : evaluatedCases;
        List<EvaluatedCase> selectedBadCases = safeCases.stream()
                .filter(caseValue -> Double.compare(caseValue.getScore(), 0.0d) == 0)
                .collect(Collectors.toCollection(ArrayList::new));
        if (selectedBadCases.size() > DEFAULT_BAD_CASES_SAMPLE_NUM) {
            selectedBadCases = sample(selectedBadCases, DEFAULT_BAD_CASES_SAMPLE_NUM);
        }
        badCases = selectedBadCases;

        List<EvaluatedCase> selectedGoodCases = safeCases.stream()
                .filter(caseValue -> Double.compare(caseValue.getScore(), 1.0d) == 0)
                .collect(Collectors.toCollection(ArrayList::new));
        if (selectedGoodCases.size() > DEFAULT_BAD_CASES_SAMPLE_NUM) {
            selectedGoodCases = sample(selectedGoodCases, DEFAULT_BAD_CASES_SAMPLE_NUM);
        }
        goodCases = selectedGoodCases;
        return badCases;
    }

    public static String extractContentFromResponse(String response, String tag) {
        Pattern pattern = Pattern.compile("<" + Pattern.quote(tag) + ">(.*?)</" + Pattern.quote(tag) + ">",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(response == null ? "" : response);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private String invokeModel(List<BaseMessage> messages) {
        ModelInvokeOptions options = ModelInvokeOptions.builder()
                .model(modelName)
                .temperature(0.3f)
                .topP(0.7f)
                .build();
        return model.invoke(messages, options).toCompletableFuture().join().getContentAsString();
    }

    private static String optionString(Map<String, Object> options, String key, String fallback) {
        if (options == null || !options.containsKey(key) || options.get(key) == null) {
            return fallback;
        }
        return String.valueOf(options.get(key));
    }

    private static <T> List<T> sample(List<T> values, int count) {
        if (values.size() <= count) {
            return new ArrayList<>(values);
        }
        List<T> shuffled = new ArrayList<>(values);
        Collections.shuffle(shuffled);
        return new ArrayList<>(shuffled.subList(0, count));
    }

    record GradientResult(String nodeName, String systemPromptGradient, String userPromptGradient) {
    }

    private record GradientEntry(String caseId, String gradient) {
    }

    /**
     * Retrying model wrapper used by ADOPT prompt analysis calls.
     *
     * <p>Mirrors Python's nested {@code ModelWithRetry} in
     * {@code openjiuwen/dev_tools/tune/optimizer/adopt/adopt_optimizer.py}.</p>
     */
    static final class ModelWithRetry extends Model {
        private final Model delegate;
        private final String defaultModelName;

        private ModelWithRetry(Model delegate, String defaultModelName) {
            super((messages, modelConfig, modelClientConfig, options) ->
                    CompletableFuture.completedFuture(new AssistantMessage("")));
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.defaultModelName = defaultModelName;
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            ModelInvokeOptions resolvedOptions = withDefaultOptions(options);
            for (int index = 1; index <= DEFAULT_MODEL_RETRY_NUM; index += 1) {
                try {
                    AssistantMessage message = delegate.invoke(messages, resolvedOptions).toCompletableFuture().join();
                    return CompletableFuture.completedFuture(message);
                } catch (RuntimeException exception) {
                    Loggers.AGENT.warn("Failed to invoke model while doing optimization,retry model_name={} retry_num={}",
                            resolvedOptions.getModel(), (double) index / DEFAULT_MODEL_RETRY_NUM);
                }
            }
            Loggers.AGENT.error("Failed to invoke the model, please check if the model is available. model_name={}",
                    resolvedOptions.getModel());
            throw ErrorHelper.buildError(
                    StatusCode.TOOLCHAIN_OPTIMIZER_PARAM_ERROR,
                    "error_msg",
                    "Failed to invoke the model"
            );
        }

        private ModelInvokeOptions withDefaultOptions(ModelInvokeOptions options) {
            ModelInvokeOptions resolved = options == null ? ModelInvokeOptions.builder().build() : options;
            return resolved.toBuilder()
                    .model(resolved.getModel() == null ? defaultModelName : resolved.getModel())
                    .temperature(resolved.getTemperature() == null ? 0.3f : resolved.getTemperature())
                    .topP(resolved.getTopP() == null ? 0.7f : resolved.getTopP())
                    .build();
        }
    }
}
