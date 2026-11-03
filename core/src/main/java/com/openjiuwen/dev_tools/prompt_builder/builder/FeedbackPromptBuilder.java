/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.prompt_builder.builder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.dev_tools.prompt_builder.BasePromptBuilder;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt builder that applies user feedback to an existing prompt.
 *
 * <p>Mirrors Python's {@code FeedbackPromptBuilder} in
 * {@code openjiuwen/dev_tools/prompt_builder/builder/feedback_prompt_builder.py}.</p>
 */
public class FeedbackPromptBuilder extends BasePromptBuilder {
    public static final String INSERT_STR = "[鐢ㄦ埛瑕佹彃鍏ョ殑浣嶇疆]";
    public static final String MODE_GENERAL = "general";
    public static final String MODE_SELECT = "select";
    public static final String MODE_INSERT = "insert";
    public static final int JSON_STRING_MAX_LENGTH = 10000;

    private static final LoggerProtocol LOGGER = LogManager.getLogger("prompt_builder");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> STRING_OBJECT_MAP = new TypeReference<>() {
    };
    private static final Pattern INTENT_JSON_PATTERN = Pattern.compile(
            "```json(.{1," + JSON_STRING_MAX_LENGTH + "}?)```",
            Pattern.DOTALL
    );

    private Map<String, PromptTemplate> template;

    public FeedbackPromptBuilder(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        super(modelConfig, modelClientConfig);
        this.template = PromptBuilderUtils.selectTemplate();
    }

    public Map<String, PromptTemplate> getTemplate() {
        return template;
    }

    /**
     * 0.1.12-compatible varargs facade.
     *
     * @param prompt prompt to optimize
     * @param args feedback, mode, positions, and optional language
     * @return optimized prompt text
     */
    public CompletableFuture<String> build(Object prompt, Object... args) {
        Object[] safeArgs = args == null ? new Object[0] : args;
        String feedback = stringArgument(safeArgs.length >= 1 ? safeArgs[0] : null);
        String mode = stringArgument(safeArgs.length >= 2 ? safeArgs[1] : MODE_GENERAL);
        Integer startPos = integerArgument(safeArgs.length >= 3 ? safeArgs[2] : null);
        Integer endPos = integerArgument(safeArgs.length >= 4 ? safeArgs[3] : null);
        String language = stringArgument(safeArgs.length >= 5 ? safeArgs[4] : "zh-CN");
        return build(prompt, feedback, mode, startPos, endPos, language == null ? "zh-CN" : language);
    }

    public CompletableFuture<String> build(Object prompt, String feedback) {
        return build(prompt, feedback, MODE_GENERAL, null, null, "zh-CN");
    }

    public CompletableFuture<String> build(Object prompt, String feedback, String mode) {
        return build(prompt, feedback, mode, null, null, "zh-CN");
    }

    public CompletableFuture<String> build(Object prompt, String feedback, String mode, Integer startPos) {
        return build(prompt, feedback, mode, startPos, null, "zh-CN");
    }

    public CompletableFuture<String> build(
            Object prompt,
            String feedback,
            String mode,
            Integer startPos,
            Integer endPos) {
        return build(prompt, feedback, mode, startPos, endPos, "zh-CN");
    }

    public CompletableFuture<String> build(
            Object prompt,
            String feedback,
            String mode,
            Integer startPos,
            Integer endPos,
            String language) {
        return optionalToString(buildOptional(prompt, feedback, mode, startPos, endPos, language));
    }

    private CompletableFuture<Optional<String>> buildOptional(
            Object prompt,
            String feedback,
            String mode,
            Integer startPos,
            Integer endPos,
            String language) {
        template = PromptBuilderUtils.selectTemplate(language);
        isValidRawPromptAndFeedback(prompt, feedback);
        String promptText = PromptBuilderUtils.getStringPrompt(prompt);
        isValidPrompt(promptText, feedback);
        return formatFeedbackTemplate(promptText, feedback, mode, startPos, endPos)
                .thenCompose(messages -> model.invoke(messages).toCompletableFuture())
                .thenApply(response -> Optional.ofNullable(response == null ? null : response.getContentAsString()));
    }

    @Override
    public CompletableFuture<Optional<String>> build(List<Object> args, Map<String, Object> kwargs) {
        Object prompt = argument(args, kwargs, 0, "prompt", null);
        String feedback = stringArgument(argument(args, kwargs, 1, "feedback", null));
        String mode = stringArgument(argument(args, kwargs, 2, "mode", MODE_GENERAL));
        Integer startPos = integerArgument(argument(args, kwargs, 3, "start_pos", null));
        Integer endPos = integerArgument(argument(args, kwargs, 4, "end_pos", null));
        String language = stringArgument(argument(args, kwargs, 5, "language", "zh-CN"));
        return buildOptional(prompt, feedback, mode, startPos, endPos, language == null ? "zh-CN" : language);
    }

    public BasePromptBuilder.PromptBuilderStreamResult streamBuild(Object prompt, String feedback) {
        return streamBuild(prompt, feedback, MODE_GENERAL, null, null, "zh-CN");
    }

    /**
     * 0.1.12-compatible varargs streaming facade.
     *
     * @param prompt prompt to optimize
     * @param args feedback, mode, positions, and optional language
     * @return concatenated streamed response text
     */
    public BasePromptBuilder.PromptBuilderStreamResult streamBuild(Object prompt, Object... args) {
        Object[] safeArgs = args == null ? new Object[0] : args;
        String feedback = stringArgument(safeArgs.length >= 1 ? safeArgs[0] : null);
        String mode = stringArgument(safeArgs.length >= 2 ? safeArgs[1] : MODE_GENERAL);
        Integer startPos = integerArgument(safeArgs.length >= 3 ? safeArgs[2] : null);
        Integer endPos = integerArgument(safeArgs.length >= 4 ? safeArgs[3] : null);
        String language = stringArgument(safeArgs.length >= 5 ? safeArgs[4] : "zh-CN");
        return streamBuild(
                prompt,
                feedback,
                mode,
                startPos,
                endPos,
                language == null ? "zh-CN" : language);
    }

    public BasePromptBuilder.PromptBuilderStreamResult streamBuild(
            Object prompt,
            String feedback,
            String mode,
            Integer startPos) {
        return streamBuild(prompt, feedback, mode, startPos, null, "zh-CN");
    }

    public BasePromptBuilder.PromptBuilderStreamResult streamBuild(
            Object prompt,
            String feedback,
            String mode,
            Integer startPos,
            Integer endPos,
            String language) {
        return collectPublisher(streamBuildPublisher(prompt, feedback, mode, startPos, endPos, language));
    }

    private Flow.Publisher<String> streamBuildPublisher(
            Object prompt,
            String feedback,
            String mode,
            Integer startPos,
            Integer endPos,
            String language) {
        template = PromptBuilderUtils.selectTemplate(language);
        isValidRawPromptAndFeedback(prompt, feedback);
        String promptText = PromptBuilderUtils.getStringPrompt(prompt);
        isValidPrompt(promptText, feedback);
        return new StreamBuildPublisher(promptText, feedback, mode, startPos, endPos);
    }

    @Override
    public Flow.Publisher<?> streamBuild(List<Object> args, Map<String, Object> kwargs) {
        Object prompt = argument(args, kwargs, 0, "prompt", null);
        String feedback = stringArgument(argument(args, kwargs, 1, "feedback", null));
        String mode = stringArgument(argument(args, kwargs, 2, "mode", MODE_GENERAL));
        Integer startPos = integerArgument(argument(args, kwargs, 3, "start_pos", null));
        Integer endPos = integerArgument(argument(args, kwargs, 4, "end_pos", null));
        String language = stringArgument(argument(args, kwargs, 5, "language", "zh-CN"));
        return streamBuildPublisher(prompt, feedback, mode, startPos, endPos, language == null ? "zh-CN" : language);
    }

    CompletableFuture<List<BaseMessage>> formatFeedbackTemplate(
            String prompt,
            String feedback,
            String mode,
            Integer startPos,
            Integer endPos) {
        if (MODE_INSERT.equals(mode)) {
            return formatFeedbackTemplateInsert(prompt, feedback, startPos);
        }
        if (MODE_SELECT.equals(mode)) {
            return formatFeedbackTemplateSelect(prompt, feedback, startPos, endPos);
        }
        if (!MODE_GENERAL.equals(mode)) {
            LOGGER.warning("Invalid mode, using `general` instead input_data={}, mode={}", prompt, mode);
        }
        return CompletableFuture.completedFuture(formatFeedbackTemplateGeneral(prompt, feedback));
    }

    List<BaseMessage> formatFeedbackTemplateGeneral(String prompt, String feedback) {
        PromptTemplate feedbackGeneralTemplate = template.get("PROMPT_FEEDBACK_GENERAL_TEMPLATE");
        return feedbackGeneralTemplate.format(Map.of(
                "original_prompt", prompt,
                "suggestion", feedback
        )).toMessages();
    }

    CompletableFuture<List<BaseMessage>> formatFeedbackTemplateInsert(
            String prompt,
            String feedback,
            Integer startPos) {
        isIndexWithinBounds(prompt, MODE_INSERT, startPos, null);
        return isFeedbackValid(prompt, feedback).thenApply(optimizedFeedback -> {
            String taggedPrompt = insertString(prompt, startPos);
            PromptTemplate feedbackInsertTemplate = template.get("PROMPT_FEEDBACK_INSERT_TEMPLATE");
            return feedbackInsertTemplate.format(Map.of(
                    "original_prompt", taggedPrompt,
                    "suggestion", optimizedFeedback
            )).toMessages();
        });
    }

    CompletableFuture<List<BaseMessage>> formatFeedbackTemplateSelect(
            String prompt,
            String feedback,
            Integer startPos,
            Integer endPos) {
        isIndexWithinBounds(prompt, MODE_SELECT, startPos, endPos);
        return isFeedbackValid(prompt, feedback).thenApply(optimizedFeedback -> {
            String promptToModify = prompt.substring(startPos, endPos);
            PromptTemplate feedbackSelectTemplate = template.get("PROMPT_FEEDBACK_SELECT_TEMPLATE");
            return feedbackSelectTemplate.format(Map.of(
                    "original_prompt", prompt,
                    "suggestion", optimizedFeedback,
                    "pending_optimized_prompt", promptToModify
            )).toMessages();
        });
    }

    String insertString(String prompt, Integer insert) {
        return prompt.substring(0, insert) + INSERT_STR + prompt.substring(insert);
    }

    CompletableFuture<String> isFeedbackValid(String prompt, String feedback) {
        PromptTemplate feedbackIntentTemplate = template.get("PROMPT_FEEDBACK_INTENT_TEMPLATE");
        List<BaseMessage> messages = feedbackIntentTemplate.format(Map.of(
                "original_prompt", prompt,
                "feedbacks", feedback
        )).toMessages();
        return model.invoke(messages)
                .toCompletableFuture()
                .thenApply(feedbackMessage -> {
                    IntentResult result = extractIntentFromResponses(
                            feedbackMessage == null ? "" : feedbackMessage.getContentAsString());
                    if (!result.intent() || result.optimizedFeedback().strip().isEmpty()) {
                        LOGGER.warning("Intent recognition failed, using original feedback instead model_name={}", model);
                        return feedback;
                    }
                    return result.optimizedFeedback().strip();
                })
                .exceptionally(error -> {
                    LOGGER.warning("Intent recognition failed, using original feedback instead model_name={}", model);
                    return feedback;
                });
    }

    boolean isIndexWithinBounds(String prompt, String mode, Integer startPos, Integer endPos) {
        if (MODE_SELECT.equals(mode)) {
            if (startPos == null || endPos == null) {
                throw buildFeedbackError("start_pos and end_pos must be provided for int type");
            }
            if (0 <= startPos && startPos < endPos && endPos <= prompt.length()) {
                return true;
            }
            throw buildFeedbackError(
                    "start_pos and end_pos must be provided for select mode. "
                            + "Additionally, they must satisfy the conditions: "
                            + "0 <= start_pos < end_pos <= len(prompt)."
            );
        }
        if (MODE_INSERT.equals(mode)) {
            if (startPos == null) {
                throw buildFeedbackError("start_pos must be provided for int type");
            }
            if (0 <= startPos && startPos <= prompt.length()) {
                return true;
            }
            throw buildFeedbackError(
                    "start_pos must be provided for insert mode. "
                            + "Additionally, it must satisfy the conditions: "
                            + "0 <= start_pos <= len(prompt)."
            );
        }
        return false;
    }

    void isValidPrompt(String prompt, String feedback) {
        if (prompt == null || feedback == null) {
            throw buildFeedbackError("prompt or feedback cannot be None");
        }
        if (prompt.strip().isEmpty() || feedback.strip().isEmpty()) {
            throw buildFeedbackError("prompt or feedback cannot be empty");
        }
    }

    void isValidRawPromptAndFeedback(Object prompt, String feedback) {
        if (prompt == null || feedback == null) {
            throw buildFeedbackError("prompt or feedback cannot be None");
        }
    }

    IntentResult extractIntentFromResponses(String inputJson) {
        Matcher matcher = INTENT_JSON_PATTERN.matcher(inputJson == null ? "" : inputJson);
        if (!matcher.find()) {
            return new IntentResult(false, "");
        }
        try {
            Map<String, Object> parsedJson = OBJECT_MAPPER.readValue(matcher.group(1).strip(), STRING_OBJECT_MAP);
            boolean intent = isPythonTruthyIntent(parsedJson.getOrDefault("intent", false));
            Object feedbackValue = parsedJson.getOrDefault("optimized_feedback", "");
            if (!(feedbackValue instanceof String optimizedFeedback)) {
                return new IntentResult(false, "");
            }
            return new IntentResult(intent, optimizedFeedback.strip());
        } catch (Exception exception) {
            return new IntentResult(false, "");
        }
    }

    private static boolean isPythonTruthyIntent(Object intentValue) {
        if (Boolean.TRUE.equals(intentValue)) {
            return true;
        }
        if (intentValue instanceof String text) {
            return "true".equals(text) || "True".equals(text);
        }
        if (intentValue instanceof Number number) {
            return number.intValue() == 1;
        }
        return false;
    }

    private static BaseError buildFeedbackError(String errorMessage) {
        return ErrorHelper.buildError(
                StatusCode.TOOLCHAIN_FEEDBACK_TEMPLATE_EXECUTION_ERROR,
                "error_msg",
                errorMessage
        );
    }

    private static Object argument(
            List<Object> args,
            Map<String, Object> kwargs,
            int index,
            String key,
            Object defaultValue) {
        if (args != null && index < args.size()) {
            return args.get(index);
        }
        if (kwargs != null && kwargs.containsKey(key)) {
            return kwargs.get(key);
        }
        return defaultValue;
    }

    private static String stringArgument(Object value) {
        return value instanceof String text ? text : null;
    }

    private static Integer integerArgument(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static BasePromptBuilder.PromptBuilderStreamResult collectPublisher(Flow.Publisher<String> publisher) {
        return new BasePromptBuilder.PromptBuilderStreamResult(publisher);
    }

    private static CompletableFuture<String> optionalToString(CompletableFuture<Optional<String>> future) {
        return future.thenApply(value -> value.orElse(null));
    }

    record IntentResult(boolean intent, String optimizedFeedback) {
        IntentResult {
            optimizedFeedback = optimizedFeedback == null ? "" : optimizedFeedback;
        }
    }

    private final class StreamBuildPublisher implements Flow.Publisher<String> {
        private final String prompt;
        private final String feedback;
        private final String mode;
        private final Integer startPos;
        private final Integer endPos;

        private StreamBuildPublisher(String prompt, String feedback, String mode, Integer startPos, Integer endPos) {
            this.prompt = prompt;
            this.feedback = feedback;
            this.mode = mode;
            this.startPos = startPos;
            this.endPos = endPos;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super String> subscriber) {
            Objects.requireNonNull(subscriber, "subscriber");
            subscriber.onSubscribe(new Flow.Subscription() {
                private boolean started;
                private boolean canceled;

                @Override
                public void request(long n) {
                    if (n <= 0) {
                        subscriber.onError(new IllegalArgumentException("non-positive subscription request"));
                        return;
                    }
                    if (started) {
                        return;
                    }
                    started = true;
                    formatFeedbackTemplate(prompt, feedback, mode, startPos, endPos)
                            .thenAccept(messages -> publishChunks(messages, subscriber))
                            .exceptionally(error -> {
                                if (!canceled) {
                                    subscriber.onError(error);
                                }
                                return null;
                            });
                }

                @Override
                public void cancel() {
                    canceled = true;
                }

                private void publishChunks(List<BaseMessage> messages, Flow.Subscriber<? super String> target) {
                    try {
                        Iterator<AssistantMessageChunk> iterator = model.stream(messages);
                        while (!canceled && iterator.hasNext()) {
                            target.onNext(iterator.next().getContentAsString());
                        }
                        if (!canceled) {
                            target.onComplete();
                        }
                    } catch (RuntimeException exception) {
                        if (!canceled) {
                            target.onError(exception);
                        }
                    }
                }
            });
        }
    }
}
