/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.prompt_builder.builder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.dev_tools.prompt_builder.BasePromptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mirrors Python's {@code openjiuwen.dev_tools.prompt_builder.builder.feedback_prompt_builder.FeedbackPromptBuilder}.
 */
public class FeedbackPromptBuilder extends BasePromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(FeedbackPromptBuilder.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String INSERT_STR = "[鐢ㄦ埛瑕佹彃鍏ョ殑浣嶇疆]";
    private static final String MODE_GENERAL = "general";
    private static final String MODE_SELECT = "select";
    private static final String MODE_INSERT = "insert";
    private static final int JSON_STRING_MAX_LENGTH = 10000;

    private Object template;

    public FeedbackPromptBuilder(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        super(modelConfig, modelClientConfig);
        this.template = PromptTemplateUtils.selectTemplate("zh-CN");
    }

    @Override
    public CompletableFuture<String> build(Object prompt, Object... args) {
        return UnwrappedCompletableFuture.supplyAsync(() -> {
            try {
                BuildParams params = parseBuildParams(args);
                this.template = PromptTemplateUtils.selectTemplate(params.language);
                if (prompt == null) {
                    isValidPrompt(null, params.feedback);
                }
                String promptStr = PromptTemplateUtils.getStringPrompt(prompt);
                isValidPrompt(promptStr, params.feedback);
                List<Object> messages = formatFeedbackTemplate(
                        promptStr,
                        params.feedback,
                        params.mode,
                        params.startPos,
                        params.endPos
                ).get();
                AssistantMessage response = model.invoke(messages, null, null, null, null, null, null, null, null, null);
                return response != null ? response.getContentAsString() : null;
            } catch (Exception exception) {
                log.error("Error building feedback template", exception);
                throw new RuntimeException(exception);
            }
        });
    }

    @Override
    public CompletableFuture<String> streamBuild(Object prompt, Object... args) {
        return UnwrappedCompletableFuture.supplyAsync(() -> {
            try {
                BuildParams params = parseBuildParams(args);
                this.template = PromptTemplateUtils.selectTemplate(params.language);
                if (prompt == null) {
                    isValidPrompt(null, params.feedback);
                }
                String promptStr = PromptTemplateUtils.getStringPrompt(prompt);
                isValidPrompt(promptStr, params.feedback);
                List<Object> messages = formatFeedbackTemplate(
                        promptStr,
                        params.feedback,
                        params.mode,
                        params.startPos,
                        params.endPos
                ).get();

                StringBuilder result = new StringBuilder();
                var iterator = model.stream(messages, null, null, null, null, null, null, null, null, null);
                while (iterator.hasNext()) {
                    var chunk = iterator.next();
                    result.append(chunk.getContentAsString());
                }
                return result.toString();
            } catch (Exception exception) {
                log.error("Error streaming feedback template", exception);
                throw new RuntimeException(exception);
            }
        });
    }

    private BuildParams parseBuildParams(Object... args) {
        BuildParams params = new BuildParams();
        if (args.length >= 1 && args[0] instanceof String) {
            params.feedback = (String) args[0];
        }
        if (args.length >= 2 && args[1] instanceof String) {
            params.mode = (String) args[1];
        }
        if (args.length >= 3 && args[2] instanceof Integer) {
            params.startPos = (Integer) args[2];
        }
        if (args.length >= 4 && args[3] instanceof Integer) {
            params.endPos = (Integer) args[3];
        }
        if (args.length >= 5 && args[4] instanceof String) {
            params.language = (String) args[4];
        }
        return params;
    }

    private CompletableFuture<List<Object>> formatFeedbackTemplate(
            String prompt,
            String feedback,
            String mode,
            Integer startPos,
            Integer endPos
    ) {
        if (MODE_INSERT.equals(mode)) {
            return formatFeedbackTemplateInsert(prompt, feedback, startPos);
        } else if (MODE_SELECT.equals(mode)) {
            return formatFeedbackTemplateSelect(prompt, feedback, startPos, endPos);
        } else {
            if (!MODE_GENERAL.equals(mode)) {
                log.warn("Invalid mode: {}, using `general` instead", mode);
            }
            return CompletableFuture.completedFuture(formatFeedbackTemplateGeneral(prompt, feedback));
        }
    }

    private List<Object> formatFeedbackTemplateGeneral(String prompt, String feedback) {
        PromptTemplate feedbackGeneralTemplate = PromptTemplateUtils.getTemplate(
                template,
                "PROMPT_FEEDBACK_GENERAL_TEMPLATE");
        Map<String, Object> formatParams = new HashMap<>();
        formatParams.put("original_prompt", prompt);
        formatParams.put("suggestion", feedback);
        return new ArrayList<>(feedbackGeneralTemplate.format(formatParams).toMessages());
    }

    private CompletableFuture<List<Object>> formatFeedbackTemplateInsert(String prompt, String feedback, Integer startPos) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                isIndexWithinBounds(prompt, MODE_INSERT, startPos, null);
                String optimizedFeedback = isFeedbackValid(prompt, feedback).get();
                String taggedPrompt = insertString(prompt, startPos);
                PromptTemplate feedbackInsertTemplate = PromptTemplateUtils.getTemplate(
                        template,
                        "PROMPT_FEEDBACK_INSERT_TEMPLATE");
                Map<String, Object> formatParams = new HashMap<>();
                formatParams.put("original_prompt", taggedPrompt);
                formatParams.put("suggestion", optimizedFeedback);
                return new ArrayList<>(feedbackInsertTemplate.format(formatParams).toMessages());
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    private CompletableFuture<List<Object>> formatFeedbackTemplateSelect(
            String prompt,
            String feedback,
            Integer startPos,
            Integer endPos
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                isIndexWithinBounds(prompt, MODE_SELECT, startPos, endPos);
                String optimizedFeedback = isFeedbackValid(prompt, feedback).get();
                String promptToModify = prompt.substring(startPos, endPos);
                PromptTemplate feedbackSelectTemplate = PromptTemplateUtils.getTemplate(
                        template,
                        "PROMPT_FEEDBACK_SELECT_TEMPLATE");
                Map<String, Object> formatParams = new HashMap<>();
                formatParams.put("original_prompt", prompt);
                formatParams.put("suggestion", optimizedFeedback);
                formatParams.put("pending_optimized_prompt", promptToModify);
                return new ArrayList<>(feedbackSelectTemplate.format(formatParams).toMessages());
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    private String insertString(String prompt, Integer insert) {
        if (insert == null) {
            return prompt;
        }
        return prompt.substring(0, insert) + INSERT_STR + prompt.substring(insert);
    }

    private CompletableFuture<String> isFeedbackValid(String prompt, String feedback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                PromptTemplate feedbackIntentTemplate = PromptTemplateUtils.getTemplate(
                        template,
                        "PROMPT_FEEDBACK_INTENT_TEMPLATE");
                Map<String, Object> formatParams = new HashMap<>();
                formatParams.put("original_prompt", prompt);
                formatParams.put("feedbacks", feedback);
                List<Object> messages = new ArrayList<>(feedbackIntentTemplate.format(formatParams).toMessages());
                AssistantMessage feedbackMessage = model.invoke(messages, null, null, null, null, null, null, null, null, null);

                IntentResult result = extractIntentFromResponses(feedbackMessage.getContentAsString());
                if (!result.intent || result.optimizedFeedback == null || result.optimizedFeedback.trim().isEmpty()) {
                    log.warn("Intent recognition failed, using original feedback instead");
                    return feedback;
                }
                return result.optimizedFeedback.trim();
            } catch (Exception exception) {
                log.warn("Intent recognition failed, using original feedback instead", exception);
                return feedback;
            }
        });
    }

    private boolean isIndexWithinBounds(String prompt, String mode, Integer startPos, Integer endPos) {
        if (MODE_SELECT.equals(mode)) {
            if (startPos == null || endPos == null) {
                throw ErrorHelper.buildError(StatusCode.TOOLCHAIN_FEEDBACK_TEMPLATE_EXECUTION_ERROR,
                        "error_msg", "start_pos and end_pos must be provided for select mode");
            }
            if (startPos < 0 || endPos <= startPos || endPos > prompt.length()) {
                throw ErrorHelper.buildError(StatusCode.TOOLCHAIN_FEEDBACK_TEMPLATE_EXECUTION_ERROR,
                        "error_msg", "start_pos and end_pos must satisfy: 0 <= start_pos < end_pos <= len(prompt)");
            }
            return true;
        } else if (MODE_INSERT.equals(mode)) {
            if (startPos == null) {
                throw ErrorHelper.buildError(StatusCode.TOOLCHAIN_FEEDBACK_TEMPLATE_EXECUTION_ERROR,
                        "error_msg", "start_pos must be provided for insert mode");
            }
            if (startPos < 0 || startPos > prompt.length()) {
                throw ErrorHelper.buildError(StatusCode.TOOLCHAIN_FEEDBACK_TEMPLATE_EXECUTION_ERROR,
                        "error_msg", "start_pos must satisfy: 0 <= start_pos <= len(prompt)");
            }
            return true;
        }
        return false;
    }

    private void isValidPrompt(String prompt, String feedback) {
        if (prompt == null || feedback == null) {
            throw ErrorHelper.buildError(StatusCode.TOOLCHAIN_FEEDBACK_TEMPLATE_EXECUTION_ERROR,
                    "error_msg", "prompt or feedback cannot be None");
        }
        if (prompt.trim().isEmpty() || feedback.trim().isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.TOOLCHAIN_FEEDBACK_TEMPLATE_EXECUTION_ERROR,
                    "error_msg", "prompt or feedback cannot be empty");
        }
    }

    private IntentResult extractIntentFromResponses(String inputJson) {
        Pattern pattern = Pattern.compile("```json(.{1," + JSON_STRING_MAX_LENGTH + "}?)```", Pattern.DOTALL);
        Matcher match = pattern.matcher(inputJson);

        if (match.find()) {
            String jsonStr = match.group(1).trim();
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsedJson = OBJECT_MAPPER.readValue(jsonStr, Map.class);
                Object intentObj = parsedJson.get("intent");
                boolean intent = "true".equalsIgnoreCase(String.valueOf(intentObj)) || Boolean.TRUE.equals(intentObj);
                String optimizedFeedback = (String) parsedJson.getOrDefault("optimized_feedback", "");
                return new IntentResult(intent, optimizedFeedback != null ? optimizedFeedback.trim() : "");
            } catch (Exception exception) {
                log.warn("Error parsing JSON from response", exception);
            }
        }
        return new IntentResult(false, "");
    }

    private static class IntentResult {
        boolean intent;
        String optimizedFeedback;

        IntentResult(boolean intent, String optimizedFeedback) {
            this.intent = intent;
            this.optimizedFeedback = optimizedFeedback;
        }
    }

    private static class BuildParams {
        String feedback;
        String mode = MODE_GENERAL;
        Integer startPos = null;
        Integer endPos = null;
        String language = "zh-CN";
    }
}
