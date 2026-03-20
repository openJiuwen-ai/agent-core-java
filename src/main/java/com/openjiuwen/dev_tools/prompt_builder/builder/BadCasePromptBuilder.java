/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.dev_tools.prompt_builder.builder;

import com.openjiuwen.agent_evolving.dataset.EvaluatedCase;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ValidationError;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.dev_tools.prompt_builder.BasePromptBuilder;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Builds improved prompts based on bad case examples.
 *
 * <p>Mirrors Python's {@code BadCasePromptBuilder}.</p>
 */
public class BadCasePromptBuilder extends BasePromptBuilder {

    private static final int MAX_CASES_LIMIT = 10;

    public BadCasePromptBuilder(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        super(modelConfig, modelClientConfig);
    }

    /**
     * Validate input prompt and cases.
     *
     * @throws ValidationError if validation fails
     */
    private void validateInput(String prompt, List<EvaluatedCase> cases) {
        if (prompt == null) {
            throw new ValidationError(
                    StatusCode.TOOLCHAIN_FEEDBACK_TEMPLATE_EXECUTION_ERROR,
                    Map.of("error_msg", "prompt cannot be None"));
        }
        if (prompt.trim().isEmpty()) {
            throw new ValidationError(
                    StatusCode.TOOLCHAIN_BAD_CASE_TEMPLATE_EXECUTION_ERROR,
                    Map.of("error_msg", "prompt cannot be empty"));
        }
        if (cases == null || cases.isEmpty()) {
            throw new ValidationError(
                    StatusCode.TOOLCHAIN_BAD_CASE_TEMPLATE_EXECUTION_ERROR,
                    Map.of("error_msg", "The cases cannot be empty"));
        }
        if (cases.size() > MAX_CASES_LIMIT) {
            throw new ValidationError(
                    StatusCode.TOOLCHAIN_BAD_CASE_TEMPLATE_EXECUTION_ERROR,
                    Map.of("error_msg", "The number of cases cannot exceed " + MAX_CASES_LIMIT));
        }
    }

    /**
     * Build an improved prompt synchronously (wrapped in Mono).
     * Validation is performed eagerly before wrapping in Mono.
     *
     * @param prompt the original prompt
     * @param cases  list of bad-case evaluated cases
     * @return Mono emitting the improved prompt string
     */
    @Override
    public Mono<String> build(String prompt, List<EvaluatedCase> cases) {
        // Validate eagerly so ValidationError propagates synchronously (matches Python behaviour)
        validateInput(prompt, cases);
        String result = buildPromptFromCases(prompt, cases);
        return Mono.just(result);
    }

    /**
     * Build an improved prompt as a reactive stream.
     * Validation is performed eagerly before returning the Flux.
     *
     * @param prompt the original prompt
     * @param cases  list of bad-case evaluated cases
     * @return Flux emitting string chunks
     */
    @Override
    public Flux<String> streamBuild(String prompt, List<EvaluatedCase> cases) {
        // Validate eagerly
        validateInput(prompt, cases);
        String result = buildPromptFromCases(prompt, cases);
        // Split into roughly 20-char chunks to simulate streaming
        String[] chunks = result.split("(?<=\\G.{20})");
        return Flux.fromArray(chunks);
    }

    /**
     * Core logic: format bad cases into a prompt-improvement analysis string.
     * When model API credentials are configured, invokes the model.
     * Otherwise returns a locally-constructed analysis string.
     */
    private String buildPromptFromCases(String prompt, List<EvaluatedCase> cases) {
        StringBuilder sb = new StringBuilder();
        sb.append("Original prompt: ").append(prompt).append("\n\n");
        sb.append("Bad cases analysis:\n");
        for (int i = 0; i < cases.size(); i++) {
            EvaluatedCase ec = cases.get(i);
            sb.append("Case ").append(i + 1).append(":\n");
            sb.append("  Input: ").append(ec.getInputs()).append("\n");
            sb.append("  Expected: ").append(ec.getLabel()).append("\n");
            sb.append("  Got: ").append(ec.getAnswer()).append("\n");
            if (ec.getReason() != null && !ec.getReason().isEmpty()) {
                sb.append("  Reason: ").append(ec.getReason()).append("\n");
            }
        }
        sb.append("\nImproved prompt: ").append(prompt);

        // Only attempt real model invocation when credentials are present
        String apiBase = modelClientConfig.getApiBase();
        String apiKey = modelClientConfig.getApiKey();
        if (apiBase != null && !apiBase.isEmpty() && apiKey != null && !apiKey.isEmpty()) {
            try {
                List<UserMessage> messages = List.of(new UserMessage(sb.toString()));
                Object response = model.invoke(messages, null, null, null, null, null, null, null, null, null);
                if (response instanceof com.openjiuwen.core.foundation.llm.schema.AssistantMessage am) {
                    Object content = am.getContent();
                    if (content != null) {
                        return content.toString();
                    }
                }
            } catch (Exception e) {
                // Fall through to return the locally-built string
            }
        }
        return sb.toString();
    }
}
