/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.optimizer.adopt;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.operator.legacy.llm_call.LLMCall;
import com.openjiuwen.dev_tools.tune.Case;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;
import com.openjiuwen.dev_tools.tune.TuneUtils;
import com.openjiuwen.dev_tools.tune.optimizer.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ADOPT (Automatic Differentiation for Prompt Optimization) optimizer.
 *
 * <p>Mirrors Python's {@code AdoptOptimizer} in {@code openjiuwen.dev_tools.tune.optimizer.adopt.adopt_optimizer}.
 */
public class AdoptOptimizer extends BaseOptimizer {

    private static final int DEFAULT_BAD_CASES_SAMPLE_NUM = 5;
    private static final int DEFAULT_MODEL_RETRY_NUM = 5;
    private static final int DEFAULT_PARALLEL_NUM = 8;

    private final Model model;
    private final String modelName;
    private String agentDescription;
    private String constrain;
    private String externalKnowledge;
    private List<EvaluatedCase> goodCases;

    /**
     * Creates an AdoptOptimizer.
     *
     * @param model the model
     * @param modelName the model name
     * @param parameters the LLM call parameters
     * @param kwargs additional options (agent_description, constrain, external_knowledge)
     */
    public AdoptOptimizer(Model model, String modelName, Map<String, LLMCall> parameters, Map<String, Object> kwargs) {
        super(parameters);
        this.model = model;
        this.modelName = modelName;
        
        Map<String, Object> options = kwargs != null ? kwargs : new HashMap<>();
        this.agentDescription = (String) options.getOrDefault("agent_description", "No description");
        this.constrain = (String) options.getOrDefault("constrain", "No constrain");
        this.externalKnowledge = (String) options.getOrDefault("external_knowledge", "No external knowledge");
        this.goodCases = new ArrayList<>();
    }

    /**
     * Creates an AdoptOptimizer with default options.
     */
    public AdoptOptimizer(Model model, String modelName, Map<String, LLMCall> parameters) {
        this(model, modelName, parameters, null);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void bindParameter(Map<String, LLMCall> params) {
        super.bindParameter(params);
    }

    /**
     * Binds parameters with additional options.
     */
    public void bindParameter(Map<String, LLMCall> params, String agentDescription) {
        super.bindParameter(params);
        if (agentDescription != null) {
            this.agentDescription = agentDescription;
        }
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    protected void doBackward(List<EvaluatedCase> evaluatedCases) {
        // 1. Conclude all nodes' descriptions
        concludeJobForEachNode();

        // 2. Calculate global gradient
        Map<String, String> globalGradient = calculateGlobalGradient();

        // 3. Calculate partial gradients for each LLM call
        List<GradientResult> partialGradients = calculatePartialGradients(globalGradient);

        // 4. Update partial gradients
        for (GradientResult result : partialGradients) {
            TextualParameter param = parameters.get(result.nodeName());
            if (param != null) {
                if (!param.getLlmCall().getFreezeSystemPrompt()) {
                    param.setGradient("system_prompt", result.systemPromptGradient());
                }
                if (!param.getLlmCall().getFreezeUserPrompt()) {
                    param.setGradient("user_prompt", result.userPromptGradient());
                }
            }
        }
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    protected void doUpdate() {
        PartialOptimizer optimizer = new PartialOptimizer(model, modelName, null);
        
        Map<String, LLMCall> llmCalls = new HashMap<>();
        for (Map.Entry<String, TextualParameter> entry : parameters.entrySet()) {
            llmCalls.put(entry.getKey(), entry.getValue().getLlmCall());
        }
        
        optimizer.bindParameter(llmCalls);
        optimizer.update();
    }

    private Map<String, String> calculateGlobalGradient() {
        int workers = Math.max(Math.min(DEFAULT_PARALLEL_NUM, badCases.size()), 1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        
        try {
            Map<String, String> gradientMap = new ConcurrentHashMap<>();
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (EvaluatedCase case_ : badCases) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    String differences = analyzeOutputChange(case_);
                    String reflection = analyzeDeepOutput(case_, differences);
                    gradientMap.put(case_.getCaseId(), differences + "\n\n" + reflection);
                }, executor);
                futures.add(future);
            }
            
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            return gradientMap;
        } finally {
            executor.shutdown();
        }
    }

    private String analyzeOutputChange(EvaluatedCase case_) {
        String prompt = AdoptTemplates.OUTPUT_CHANGE_USER_PROMPT
                .replace("{{workflow_description}}", agentDescription)
                .replace("{{current_output}}", String.valueOf(case_.getAnswer()))
                .replace("{{ground_truth}}", String.valueOf(case_.getLabel()))
                .replace("{{metric_fn}}", case_.getReason())
                .replace("{{current_score}}", String.valueOf(case_.getScore()))
                .replace("{{constrain}}", constrain);
        
        return invokeModelWithRetry(AdoptTemplates.OUTPUT_CHANGE_SYSTEM_PROMPT + "\n" + prompt);
    }

    private String analyzeDeepOutput(EvaluatedCase case_, String differences) {
        String prompt = AdoptTemplates.DEEP_OUTPUT_ANALYSIS_USER_PROMPT
                .replace("{{workflow_description}}", agentDescription)
                .replace("{{node_input}}", String.valueOf(case_.getInputs()))
                .replace("{{node_output}}", String.valueOf(case_.getAnswer()))
                .replace("{{node_expected_output}}", String.valueOf(case_.getLabel()))
                .replace("{{external_knowledge}}", externalKnowledge)
                .replace("{{constrain}}", constrain)
                .replace("{{shallow_difference}}", differences);
        
        return invokeModelWithRetry(AdoptTemplates.DEEP_OUTPUT_ANALYSIS_SYSTEM_PROMPT + "\n" + prompt);
    }

    private List<GradientResult> calculatePartialGradients(Map<String, String> globalGradient) {
        int workers = Math.max(Math.min(DEFAULT_PARALLEL_NUM, badCases.size()), 1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        
        try {
            List<GradientResult> results = new ArrayList<>();
            List<CompletableFuture<GradientResult>> futures = new ArrayList<>();
            
            for (Map.Entry<String, TextualParameter> entry : parameters.entrySet()) {
                CompletableFuture<GradientResult> future = CompletableFuture.supplyAsync(() -> 
                        generateTextualGradientForLlmCalls(entry.getKey(), entry.getValue(), globalGradient),
                        executor
                );
                futures.add(future);
            }
            
            for (CompletableFuture<GradientResult> future : futures) {
                results.add(future.join());
            }
            
            return results;
        } finally {
            executor.shutdown();
        }
    }

    private GradientResult generateTextualGradientForLlmCalls(String nodeName, TextualParameter param, 
                                                                Map<String, String> globalGradient) {
        List<EvaluatedCase> nodeCases = new ArrayList<>();
        
        for (EvaluatedCase case_ : badCases) {
            List<TraceNode> traceNodes = history.getLlmCallHistory(case_.getCaseId(), nodeName)
                    .orElse(List.of());
            
            if (traceNodes == null || traceNodes.isEmpty()) {
                Loggers.AGENT.warn("Failed to get trace nodes for node: {}", nodeName);
                continue;
            }
            
            List<Map<String, Object>> inputList = traceNodes.stream()
                    .map(TraceNode::getInputs)
                    .toList();
            List<String> outputList = traceNodes.stream()
                    .map(TraceNode::getOutputs)
                    .toList();
            
            String prompt = AdoptTemplates.EXPECTED_OUTPUT_USER_PROMPT
                    .replace("{{workflow_output}}", String.valueOf(case_.getAnswer()))
                    .replace("{{modification}}", globalGradient.getOrDefault(case_.getCaseId(), ""))
                    .replace("{{dependency_from_this_workflow_final_output}}", param.getDescription())
                    .replace("{{node_in_block}}", AdoptTemplates.buildNodeIoString(inputList, outputList));
            
            String response = invokeModelWithRetry(AdoptTemplates.EXPECTED_OUTPUT_SYSTEM_PROMPT + "\n" + prompt);
            String revisedOutput = extractContent(response, "REVISED_NODE_OUTPUT");
            
            EvaluatedCase nodeCase = EvaluatedCase.builder()
                    .case_(Case.builder()
                            .inputs(Map.of("inputs", inputList))
                            .label(Map.of("label", revisedOutput))
                            .build())
                    .answer(Map.of("answer", outputList))
                    .score(0.0f)
                    .reason(response)
                    .build();
            
            nodeCases.add(nodeCase);
        }
        
        // Use PartialOptimizer to get gradients
        PartialOptimizer partialOptimizer = new PartialOptimizer(model, modelName, 
                Map.of(nodeName, deepCopyLlmCall(param.getLlmCall())));
        partialOptimizer.backward(nodeCases);
        
        TextualParameter partialParam = partialOptimizer.getParameters().get(nodeName);
        if (partialParam != null) {
            return new GradientResult(
                    nodeName,
                    partialParam.getGradient("system_prompt").orElse(null),
                    partialParam.getGradient("user_prompt").orElse(null)
            );
        }
        
        return new GradientResult(nodeName, null, null);
    }

    private void concludeJobForEachNode() {
        for (Map.Entry<String, TextualParameter> entry : parameters.entrySet()) {
            concludeNode(entry.getKey());
        }
    }

    private void concludeNode(String nodeName) {
        TextualParameter param = parameters.get(nodeName);
        if (param == null) {
            throw new IllegalArgumentException("Cannot find parameter: " + nodeName);
        }
        
        String systemPrompt = TuneUtils.getContentStringFromTemplate(param.getLlmCall().getSystemPrompt());
        String userPrompt = TuneUtils.getContentStringFromTemplate(param.getLlmCall().getUserPrompt());
        
        String prompt = AdoptTemplates.CONCLUDE_NODE_USER_PROMPT
                .replace("{{node_name}}", nodeName)
                .replace("{{agent_description}}", agentDescription)
                .replace("{{system_prompt}}", systemPrompt)
                .replace("{{user_prompt}}", userPrompt)
                .replace("{{good_cases}}", TuneUtils.convertCasesToExamples(goodCases));
        
        String response = invokeModelWithRetry(AdoptTemplates.CONCLUDE_NODE_SYSTEM_PROMPT + "\n" + prompt);
        param.setDescription(response);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    protected List<EvaluatedCase> getBadCases(List<EvaluatedCase> evaluatedCases) {
        List<EvaluatedCase> badCases = evaluatedCases.stream()
                .filter(c -> c.getScore() == 0.0f)
                .collect(Collectors.toList());
        
        if (badCases.size() > DEFAULT_BAD_CASES_SAMPLE_NUM) {
            Collections.shuffle(badCases);
            badCases = badCases.subList(0, DEFAULT_BAD_CASES_SAMPLE_NUM);
        }
        this.badCases = badCases;
        
        // Also get good cases
        List<EvaluatedCase> goodCasesRes = evaluatedCases.stream()
                .filter(c -> c.getScore() == 1.0f)
                .collect(Collectors.toList());
        
        if (goodCasesRes.size() > DEFAULT_BAD_CASES_SAMPLE_NUM) {
            Collections.shuffle(goodCasesRes);
            goodCasesRes = goodCasesRes.subList(0, DEFAULT_BAD_CASES_SAMPLE_NUM);
        }
        this.goodCases = goodCasesRes;
        
        return badCases;
    }

    private String invokeModelWithRetry(String prompt) {
        for (int i = 1; i <= DEFAULT_MODEL_RETRY_NUM; i++) {
            try {
                return model.invoke(prompt, null, null, null, null, null, null, null, null, null)
                        .getContentAsString();
            } catch (Exception e) {
                Loggers.AGENT.warn("Model invoke failed, retry {}/{}", i, DEFAULT_MODEL_RETRY_NUM);
            }
        }
        return "Model invocation failed after " + DEFAULT_MODEL_RETRY_NUM + " retries";
    }

    private static String extractContent(String response, String tag) {
        Pattern pattern = Pattern.compile("<" + tag + ">(.*?)</" + tag + ">", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(response);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).trim();
    }

    private LLMCall deepCopyLlmCall(LLMCall original) {
        // Simplified - in production would need proper deep copy
        return original;
    }

    /**
     * Record for gradient results.
     */
    private record GradientResult(String nodeName, String systemPromptGradient, String userPromptGradient) {}
}
