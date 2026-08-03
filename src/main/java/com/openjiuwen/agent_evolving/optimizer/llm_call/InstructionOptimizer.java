/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.llm_call;

import com.openjiuwen.agent_evolving.TuneUtils;
import com.openjiuwen.agent_evolving.optimizer.TextualParameter;
import com.openjiuwen.agent_evolving.signal.EvolutionSignal;
import com.openjiuwen.agent_evolving.trajectory.Updates;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.prompt.assemble.PromptAssembler;
import com.openjiuwen.core.operator.Operator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Optimizes LLM prompts using textual gradients.
 *
 * <p>Mirrors Python's {@code InstructionOptimizer} in
 * {@code openjiuwen/agent_evolving/optimizer/llm_call/instruction_optimizer.py}.</p>
 */
public class InstructionOptimizer extends LLMCallOptimizerBase {

    private static final String SYSTEM_PROMPT_OPTIMIZED = "system_prompt_optimized";
    private static final String USER_PROMPT_OPTIMIZED = "user_prompt_optimized";
    private static final String TOOLS_DESCRIPTION = "None";
    private static final Set<String> FAILURE_SIGNAL_TYPES = Set.of(
            "execution_failure",
            "low_score",
            "user_correction",
            "collaboration_failure"
    );

    private final Model model;
    private final PromptTemplate promptInstructionOptimizeTemplate;
    private final PromptTemplate promptInstructionOptimizeBothTemplate;
    private final PromptTemplate createPromptTextualGradientTemplate;
    private final PromptTemplate createBadCaseTemplate;
    private final PromptTemplate placeholderRestoreTemplate;

    public InstructionOptimizer(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this(new Model(modelClientConfig, modelConfig));
    }

    InstructionOptimizer(Model model) {
        super();
        this.model = model;
        this.promptInstructionOptimizeTemplate = PromptTemplate.builder()
                .content(InstructionOptimizerTemplates.PROMPT_INSTRUCTION_OPTIMIZE_TEMPLATE)
                .build();
        this.promptInstructionOptimizeBothTemplate = PromptTemplate.builder()
                .content(InstructionOptimizerTemplates.PROMPT_INSTRUCTION_OPTIMIZE_BOTH_TEMPLATE)
                .build();
        this.createPromptTextualGradientTemplate = PromptTemplate.builder()
                .content(InstructionOptimizerTemplates.CREATE_PROMPT_TEXTUAL_GRADIENT_TEMPLATE)
                .build();
        this.createBadCaseTemplate = PromptTemplate.builder()
                .content(InstructionOptimizerTemplates.CREATE_BAD_CASE_TEMPLATE)
                .build();
        this.placeholderRestoreTemplate = PromptTemplate.builder()
                .content(InstructionOptimizerTemplates.PLACEHOLDER_RESTORE_TEMPLATE)
                .build();
    }

    @Override
    protected List<EvolutionSignal> selectSignals(List<EvolutionSignal> signals) {
        List<EvolutionSignal> selected = new ArrayList<>();
        for (EvolutionSignal signal : signals == null ? List.<EvolutionSignal>of() : signals) {
            Map<String, Object> context = signal.getContext() == null ? Map.of() : signal.getContext();
            Object score = context.getOrDefault("score", 1);
            boolean scoreIsZero = score instanceof Number number && Double.compare(number.doubleValue(), 0.0d) == 0;
            if (scoreIsZero || FAILURE_SIGNAL_TYPES.contains(signal.getSignalType())) {
                selected.add(signal);
            }
        }
        return selected;
    }

    @Override
    protected CompletionStage<Void> doBackward(List<EvolutionSignal> signals) {
        CompletionStage<Void> stage = CompletableFuture.completedFuture(null);
        for (Map.Entry<String, TextualParameter> entry : parameters.entrySet()) {
            stage = stage.thenCompose(ignored -> backwardOne(entry.getKey(), entry.getValue()));
        }
        return stage;
    }

    @Override
    protected Updates doStep() {
        Updates updates = new Updates();
        for (Map.Entry<String, TextualParameter> entry : parameters.entrySet()) {
            String operatorId = entry.getKey();
            TextualParameter parameter = entry.getValue();
            Object systemPrompt = parameter.getGradient(SYSTEM_PROMPT_OPTIMIZED);
            Object userPrompt = parameter.getGradient(USER_PROMPT_OPTIMIZED);
            if (hasValue(systemPrompt)) {
                updates.put(operatorId, SYSTEM_PROMPT, systemPrompt);
            }
            if (hasValue(userPrompt)) {
                updates.put(operatorId, USER_PROMPT, userPrompt);
            }
        }
        return updates.isEmpty() ? null : updates;
    }

    private CompletionStage<Void> backwardOne(String operatorId, TextualParameter parameter) {
        Operator operator = operators.get(operatorId);
        if (operator == null) {
            return CompletableFuture.completedFuture(null);
        }

        parameter.setGradient(SYSTEM_PROMPT_OPTIMIZED, null);
        parameter.setGradient(USER_PROMPT_OPTIMIZED, null);
        if (selectedSignals.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return generateTextualGradient(operator).thenCompose(textualGradient -> {
            if (!isTargetFrozen(operator, SYSTEM_PROMPT)) {
                parameter.setGradient(SYSTEM_PROMPT, textualGradient);
            }
            if (!isTargetFrozen(operator, USER_PROMPT)) {
                parameter.setGradient(USER_PROMPT, textualGradient);
            }

            boolean hasSystem = targets.contains(SYSTEM_PROMPT) && !isTargetFrozen(operator, SYSTEM_PROMPT);
            boolean hasUser = targets.contains(USER_PROMPT) && !isTargetFrozen(operator, USER_PROMPT);

            if (hasSystem && hasUser) {
                return optimizeBoth(operator, parameter).thenAccept(pair -> {
                    if (hasValue(pair.systemPrompt())) {
                        parameter.setGradient(SYSTEM_PROMPT_OPTIMIZED, pair.systemPrompt());
                    }
                    if (hasValue(pair.userPrompt())) {
                        parameter.setGradient(USER_PROMPT_OPTIMIZED, pair.userPrompt());
                    }
                });
            }
            if (hasSystem) {
                return optimizeSingle(operator, parameter, SYSTEM_PROMPT).thenAccept(value -> {
                    if (hasValue(value)) {
                        parameter.setGradient(SYSTEM_PROMPT_OPTIMIZED, value);
                    }
                });
            }
            if (hasUser) {
                return optimizeSingle(operator, parameter, USER_PROMPT).thenAccept(value -> {
                    if (hasValue(value)) {
                        parameter.setGradient(USER_PROMPT_OPTIMIZED, value);
                    }
                });
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    private CompletionStage<String> generateTextualGradient(Operator operator) {
        PromptTemplate systemTemplate = getPromptTemplate(operator, SYSTEM_PROMPT);
        PromptTemplate userTemplate = getPromptTemplate(operator, USER_PROMPT);
        List<BaseMessage> messages = createPromptTextualGradientTemplate.format(Map.of(
                "system_prompt", TuneUtils.getContentStringFromTemplate(systemTemplate),
                "user_prompt", TuneUtils.getContentStringFromTemplate(userTemplate),
                "bad_cases", formatBadCases(),
                "tools_description", TOOLS_DESCRIPTION
        )).toMessages();
        return invokeLlm(messages);
    }

    private CompletionStage<PromptPair> optimizeBoth(Operator operator, TextualParameter parameter) {
        PromptTemplate systemTemplate = getPromptTemplate(operator, SYSTEM_PROMPT);
        PromptTemplate userTemplate = getPromptTemplate(operator, USER_PROMPT);
        String systemPrompt = TuneUtils.getContentStringFromTemplate(systemTemplate);
        String userPrompt = TuneUtils.getContentStringFromTemplate(userTemplate);
        String gradient = gradientAsString(parameter, SYSTEM_PROMPT);

        List<BaseMessage> messages = promptInstructionOptimizeBothTemplate.format(Map.of(
                "system_prompt", systemPrompt,
                "user_prompt", userPrompt,
                "bad_cases", formatBadCases(),
                "reflections_on_bad_cases", gradient,
                "tools_description", TOOLS_DESCRIPTION
        )).toMessages();

        return invokeLlm(messages).thenCompose(response -> {
            String optimizedSystemPrompt = extractTag(response, "SYSTEM_PROMPT_OPTIMIZED");
            String optimizedUserPrompt = extractTag(response, "USER_PROMPT_OPTIMIZED");
            CompletionStage<String> systemStage = optimizedSystemPrompt == null
                    ? CompletableFuture.completedFuture(null)
                    : restorePlaceholders(systemPrompt, optimizedSystemPrompt);
            CompletionStage<String> userStage = optimizedUserPrompt == null
                    ? CompletableFuture.completedFuture(null)
                    : restorePlaceholders(userPrompt, optimizedUserPrompt);
            return systemStage.thenCombine(userStage, PromptPair::new);
        });
    }

    private CompletionStage<String> optimizeSingle(Operator operator, TextualParameter parameter, String promptType) {
        PromptTemplate targetTemplate = getPromptTemplate(operator, promptType);
        String targetPrompt = TuneUtils.getContentStringFromTemplate(targetTemplate);
        String gradient = gradientAsString(parameter, promptType);

        List<BaseMessage> messages = promptInstructionOptimizeTemplate.format(Map.of(
                "prompt_instruction", targetPrompt,
                "bad_cases", formatBadCases(),
                "reflections_on_bad_cases", gradient,
                "tools_description", TOOLS_DESCRIPTION
        )).toMessages();

        return invokeLlm(messages).thenCompose(response -> {
            String optimized = extractTag(response, "PROMPT_OPTIMIZED");
            if (optimized == null) {
                return CompletableFuture.completedFuture(null);
            }
            return restorePlaceholders(targetPrompt, optimized);
        });
    }

    private CompletionStage<String> restorePlaceholders(String originalPrompt, String optimizedPrompt) {
        List<String> originalKeys = new PromptAssembler(originalPrompt, "{{", "}}").getInputKeys();
        List<String> optimizedKeys = new PromptAssembler(optimizedPrompt, "{{", "}}").getInputKeys();
        LinkedHashSet<String> missing = new LinkedHashSet<>(originalKeys);
        missing.removeAll(optimizedKeys);
        if (missing.isEmpty()) {
            return CompletableFuture.completedFuture(optimizedPrompt);
        }

        List<BaseMessage> messages = placeholderRestoreTemplate.format(Map.of(
                "original_prompt", originalPrompt,
                "revised_prompt", optimizedPrompt,
                "all_placeholders", originalKeys.toString(),
                "missing_placeholders", missing.toString()
        )).toMessages();

        return invokeLlm(messages).thenApply(raw -> {
            List<String> restoredKeys = new PromptAssembler(raw, "{{", "}}").getInputKeys();
            LinkedHashSet<String> stillMissing = new LinkedHashSet<>(originalKeys);
            stillMissing.removeAll(restoredKeys);
            if (stillMissing.isEmpty()) {
                return raw;
            }
            String placeholderText = stillMissing.stream()
                    .map(placeholder -> "{{" + placeholder + "}}")
                    .collect(Collectors.joining("\n"));
            return raw + "\n" + placeholderText;
        });
    }

    private String formatBadCases() {
        StringJoiner joiner = new StringJoiner("");
        for (EvolutionSignal signal : selectedSignals) {
            Map<String, Object> context = signal.getContext() == null ? Map.of() : signal.getContext();
            PromptTemplate formatted = createBadCaseTemplate.format(Map.of(
                    "question", stringValue(context.get("question")),
                    "label", stringValue(context.get("label")),
                    "answer", stringValue(context.get("answer")),
                    "reason", stringValue(context.get("reason"))
            ));
            Object content = formatted.getContent();
            if (content != null) {
                joiner.add(String.valueOf(content));
            }
        }
        return joiner.toString();
    }

    private String extractTag(String response, String tag) {
        Pattern pattern = Pattern.compile("<" + tag + ">(.*?)</" + tag + ">", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(response == null ? "" : response);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1)
                .replace("<prompt_base>", "")
                .replace("</prompt_base>", "");
    }

    private CompletionStage<String> invokeLlm(List<BaseMessage> messages) {
        return model.invoke(messages).thenApply(InstructionOptimizer::contentAsString);
    }

    private static String contentAsString(AssistantMessage message) {
        return message == null ? "" : message.getContentAsString();
    }

    private static String gradientAsString(TextualParameter parameter, String key) {
        Object value = parameter.getGradient(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean hasValue(Object value) {
        return value != null && !String.valueOf(value).isEmpty();
    }

    private record PromptPair(String systemPrompt, String userPrompt) {
    }
}
