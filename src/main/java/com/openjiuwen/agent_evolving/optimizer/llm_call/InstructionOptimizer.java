// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.agent_evolving.optimizer.llm_call;

import com.openjiuwen.agent_evolving.optimizer.TextualParameter;
import com.openjiuwen.agent_evolving.trajectory.Updates;
import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.agent_evolving.TuneUtils;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private final Object model;  // Model instance

    /**
     * Create instruction optimizer.
     *
     * @param modelConfig       LLM request configuration
     * @param modelClientConfig LLM client configuration
     */
    public InstructionOptimizer(Object modelConfig, Object modelClientConfig) {
        super();
        this.model = null;  // Placeholder - actual implementation would create Model
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
            if (!isTargetFrozen(op, "system_prompt")) {
                param.setGradient("system_prompt", textualGradient);
            }
            if (!isTargetFrozen(op, "user_prompt")) {
                param.setGradient("user_prompt", textualGradient);
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

            boolean hasSys = targets.contains("system_prompt") && !isTargetFrozen(op, "system_prompt");
            boolean hasUsr = targets.contains("user_prompt") && !isTargetFrozen(op, "user_prompt");

            if (hasSys && hasUsr) {
                String[] result = optimizeBoth(op, param);
                if (result[0] != null) {
                    updates.put(opId, "system_prompt", result[0]);
                }
                if (result[1] != null) {
                    updates.put(opId, "user_prompt", result[1]);
                }
            } else if (hasSys) {
                String val = optimizeSingle(op, param, "system_prompt");
                if (val != null) {
                    updates.put(opId, "system_prompt", val);
                }
            } else if (hasUsr) {
                String val = optimizeSingle(op, param, "user_prompt");
                if (val != null) {
                    updates.put(opId, "user_prompt", val);
                }
            }
        }

        return updates.isEmpty() ? null : updates;
    }

    private String generateTextualGradient(Object op) {
        String systemPrompt = getPromptTemplate(op, "system_prompt");
        String userPrompt = getPromptTemplate(op, "user_prompt");
        String badCasesStr = formatBadCases();

        // Build prompt for LLM
        String prompt = buildGradientPrompt(systemPrompt, userPrompt, badCasesStr);

        try {
            // In actual implementation, would call model.invoke
            return "";  // Placeholder
        } catch (Exception e) {
            return "";
        }
    }

    private String[] optimizeBoth(Object op, TextualParameter param) {
        String systemPrompt = getPromptTemplate(op, "system_prompt");
        String userPrompt = getPromptTemplate(op, "user_prompt");
        String gradient = param.getGradient("system_prompt");
        String badCasesStr = formatBadCases();

        // Build prompt for LLM
        String prompt = buildOptimizeBothPrompt(systemPrompt, userPrompt, gradient, badCasesStr);

        try {
            // In actual implementation, would call model.invoke
            String response = "";  // Placeholder

            String sysPrompt = extractTag(response, "SYSTEM_PROMPT_OPTIMIZED");
            String usrPrompt = extractTag(response, "USER_PROMPT_OPTIMIZED");

            sysPrompt = sysPrompt != null ? restorePlaceholders(systemPrompt, sysPrompt) : null;
            usrPrompt = usrPrompt != null ? restorePlaceholders(userPrompt, usrPrompt) : null;

            return new String[]{sysPrompt, usrPrompt};
        } catch (Exception e) {
            return new String[]{null, null};
        }
    }

    private String optimizeSingle(Object op, TextualParameter param, String promptType) {
        String targetPrompt = getPromptTemplate(op, promptType);
        String gradient = param.getGradient(promptType);
        String badCasesStr = formatBadCases();

        // Build prompt for LLM
        String prompt = buildOptimizeSinglePrompt(targetPrompt, gradient, badCasesStr);

        try {
            // In actual implementation, would call model.invoke
            String response = "";  // Placeholder

            String optimized = extractTag(response, "PROMPT_OPTIMIZED");
            if (optimized != null) {
                optimized = restorePlaceholders(targetPrompt, optimized);
            }

            return optimized;
        } catch (Exception e) {
            return null;
        }
    }

    private String formatBadCases() {
        StringBuilder sb = new StringBuilder();
        for (EvaluatedCase evalCase : getBadCases()) {
            sb.append("[question]: ").append(evalCase.getInputs()).append("\n");
            sb.append("[expected answer]: ").append(evalCase.getLabel()).append("\n");
            sb.append("[assistant answer]: ").append(evalCase.getAnswer()).append("\n");
            sb.append("[reason]: ").append(evalCase.getReason()).append("\n");
            sb.append("===\n");
        }
        return sb.toString();
    }

    private String extractTag(String response, String tag) {
        Pattern pattern = Pattern.compile("<" + tag + ">(.*?)</" + tag + ">", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(response);
        if (!matcher.find()) {
            return null;
        }
        String content = matcher.group(1);
        return content.replace("<prompt_base>", "").replace("</prompt_base>", "");
    }

    private String restorePlaceholders(String originalPrompt, String optimizedPrompt) {
        // Simplified placeholder restoration
        // In actual implementation, would use PromptAssembler
        return optimizedPrompt;
    }

    // Prompt building methods (simplified)
    private String buildGradientPrompt(String systemPrompt, String userPrompt, String badCases) {
        return "";
    }

    private String buildOptimizeBothPrompt(String systemPrompt, String userPrompt, 
                                           String gradient, String badCases) {
        return "";
    }

    private String buildOptimizeSinglePrompt(String targetPrompt, String gradient, String badCases) {
        return "";
    }
}