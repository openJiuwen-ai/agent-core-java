/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.llm_call;

import com.openjiuwen.agent_evolving.TuneUtils;
import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.agent_evolving.optimizer.TextualParameter;
import com.openjiuwen.agent_evolving.trajectory.Updates;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.prompt.assemble.PromptAssembler;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Optimizes LLM prompts using textual gradients.
 *
 * <p>Uses LLM to:
 * <ol>
 *   <li>backward(): Generate textual gradients explaining why prompts failed</li>
 *   <li>update(): Generate improved prompts based on gradients</li>
 * </ol>
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.llm_call.instruction_optimizer.InstructionOptimizer}.
 */
public class InstructionOptimizer extends LLMCallOptimizerBase {

    private static final String SYSTEM_PROMPT = "system_prompt";
    private static final String USER_PROMPT = "user_prompt";
    private static final String TOOLS_DESCRIPTION = "None";

    private final Model model;
    private final PromptTemplate promptInstructionOptimizeTemplate;
    private final PromptTemplate promptInstructionOptimizeBothTemplate;
    private final PromptTemplate createPromptTextualGradientTemplate;
    private final PromptTemplate createBadCaseTemplate;
    private final PromptTemplate placeholderRestoreTemplate;

    /**
     * Create instruction optimizer.
     *
     * @param modelConfig       LLM request configuration
     * @param modelClientConfig LLM client configuration
     */
    public InstructionOptimizer(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this(new Model(modelClientConfig, modelConfig));
    }

    InstructionOptimizer(Model model) {
        super();
        this.model = Objects.requireNonNull(model, "model");
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
    protected void doBackward(List<EvaluatedCase> evaluatedCases) {
        for (Map.Entry<String, TextualParameter> entry : parameters.entrySet()) {
            String opId = entry.getKey();
            TextualParameter param = entry.getValue();
            Object op = operators.get(opId);
            if (op == null) {
                continue;
            }

            String textualGradient = generateTextualGradient(op);
            if (!isTargetFrozen(op, SYSTEM_PROMPT)) {
                param.setGradient(SYSTEM_PROMPT, textualGradient);
            }
            if (!isTargetFrozen(op, USER_PROMPT)) {
                param.setGradient(USER_PROMPT, textualGradient);
            }
        }
    }

    @Override
    protected Updates doStep() {
        Updates updates = new Updates();

        for (Map.Entry<String, TextualParameter> entry : parameters.entrySet()) {
            String opId = entry.getKey();
            TextualParameter param = entry.getValue();
            Object op = operators.get(opId);
            if (op == null) {
                continue;
            }

            boolean hasSys = targets.contains(SYSTEM_PROMPT) && !isTargetFrozen(op, SYSTEM_PROMPT);
            boolean hasUsr = targets.contains(USER_PROMPT) && !isTargetFrozen(op, USER_PROMPT);

            if (hasSys && hasUsr) {
                String[] result = optimizeBoth(op, param);
                if (result[0] != null) {
                    updates.put(opId, SYSTEM_PROMPT, result[0]);
                }
                if (result[1] != null) {
                    updates.put(opId, USER_PROMPT, result[1]);
                }
            } else if (hasSys) {
                String value = optimizeSingle(op, param, SYSTEM_PROMPT);
                if (value != null) {
                    updates.put(opId, SYSTEM_PROMPT, value);
                }
            } else if (hasUsr) {
                String value = optimizeSingle(op, param, USER_PROMPT);
                if (value != null) {
                    updates.put(opId, USER_PROMPT, value);
                }
            }
        }

        return updates.isEmpty() ? null : updates;
    }

    protected String invokeModel(List<?> messages) {
        try {
            AssistantMessage response = model.invoke(messages, null, null, null, null, null, null, null, null, null);
            return response != null ? response.getContentAsString() : "";
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private String generateTextualGradient(Object op) {
        PromptTemplate systemTemplate = getPromptTemplate(op, SYSTEM_PROMPT);
        PromptTemplate userTemplate = getPromptTemplate(op, USER_PROMPT);
        List<?> messages = createPromptTextualGradientTemplate.format(Map.of(
                SYSTEM_PROMPT, TuneUtils.getContentStringFromTemplate(systemTemplate),
                USER_PROMPT, TuneUtils.getContentStringFromTemplate(userTemplate),
                "bad_cases", formatBadCases(),
                "tools_description", TOOLS_DESCRIPTION
        )).toMessages();
        return invokeModel(messages);
    }

    private String[] optimizeBoth(Object op, TextualParameter param) {
        PromptTemplate systemTemplate = getPromptTemplate(op, SYSTEM_PROMPT);
        PromptTemplate userTemplate = getPromptTemplate(op, USER_PROMPT);
        String systemPrompt = TuneUtils.getContentStringFromTemplate(systemTemplate);
        String userPrompt = TuneUtils.getContentStringFromTemplate(userTemplate);
        String gradient = nullToEmpty(param.getGradient(SYSTEM_PROMPT));

        List<?> messages = promptInstructionOptimizeBothTemplate.format(Map.of(
                SYSTEM_PROMPT, systemPrompt,
                USER_PROMPT, userPrompt,
                "bad_cases", formatBadCases(),
                "reflections_on_bad_cases", gradient,
                "tools_description", TOOLS_DESCRIPTION
        )).toMessages();

        String response = invokeModel(messages);
        String optimizedSystemPrompt = extractTag(response, "SYSTEM_PROMPT_OPTIMIZED");
        String optimizedUserPrompt = extractTag(response, "USER_PROMPT_OPTIMIZED");

        if (optimizedSystemPrompt != null) {
            optimizedSystemPrompt = restorePlaceholders(systemPrompt, optimizedSystemPrompt);
        }
        if (optimizedUserPrompt != null) {
            optimizedUserPrompt = restorePlaceholders(userPrompt, optimizedUserPrompt);
        }

        return new String[]{optimizedSystemPrompt, optimizedUserPrompt};
    }

    private String optimizeSingle(Object op, TextualParameter param, String promptType) {
        PromptTemplate targetTemplate = getPromptTemplate(op, promptType);
        String targetPrompt = TuneUtils.getContentStringFromTemplate(targetTemplate);
        String gradient = nullToEmpty(param.getGradient(promptType));

        List<?> messages = promptInstructionOptimizeTemplate.format(Map.of(
                "prompt_instruction", targetPrompt,
                "bad_cases", formatBadCases(),
                "reflections_on_bad_cases", gradient,
                "tools_description", TOOLS_DESCRIPTION
        )).toMessages();

        String response = invokeModel(messages);
        String optimized = extractTag(response, "PROMPT_OPTIMIZED");
        if (optimized != null) {
            return restorePlaceholders(targetPrompt, optimized);
        }
        return null;
    }

    private String formatBadCases() {
        StringJoiner joiner = new StringJoiner("");
        for (EvaluatedCase evaluatedCase : getBadCases()) {
            PromptTemplate formatted = createBadCaseTemplate.format(Map.of(
                    "question", String.valueOf(evaluatedCase.getInputs()),
                    "label", String.valueOf(evaluatedCase.getLabel()),
                    "answer", String.valueOf(evaluatedCase.getAnswer()),
                    "reason", String.valueOf(evaluatedCase.getReason())
            ));
            Object content = formatted.getContent();
            if (content instanceof String text) {
                joiner.add(text);
            } else if (content != null) {
                joiner.add(String.valueOf(content));
            }
        }
        return joiner.toString();
    }

    private String extractTag(String response, String tag) {
        Pattern pattern = Pattern.compile("<" + tag + ">(.*?)</" + tag + ">", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(nullToEmpty(response));
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1)
                .replace("<prompt_base>", "")
                .replace("</prompt_base>", "");
    }

    private String restorePlaceholders(String originalPrompt, String optimizedPrompt) {
        List<String> originalKeys = new PromptAssembler(originalPrompt, "{{", "}}").getInputKeys();
        List<String> optimizedKeys = new PromptAssembler(optimizedPrompt, "{{", "}}").getInputKeys();

        LinkedHashSet<String> missing = new LinkedHashSet<>(originalKeys);
        missing.removeAll(optimizedKeys);
        if (missing.isEmpty()) {
            return optimizedPrompt;
        }

        String restoredPrompt = invokeModel(placeholderRestoreTemplate.format(Map.of(
                "original_prompt", originalPrompt,
                "revised_prompt", optimizedPrompt,
                "all_placeholders", originalKeys.toString(),
                "missing_placeholders", missing.toString()
        )).toMessages());

        List<String> restoredKeys = new PromptAssembler(restoredPrompt, "{{", "}}").getInputKeys();
        LinkedHashSet<String> stillMissing = new LinkedHashSet<>(originalKeys);
        stillMissing.removeAll(restoredKeys);
        if (!stillMissing.isEmpty()) {
            String appendedPlaceholders = stillMissing.stream()
                    .map(placeholder -> "{{" + placeholder + "}}")
                    .collect(Collectors.joining("\n"));
            return restoredPrompt + "\n" + appendedPlaceholders;
        }
        return restoredPrompt;
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
