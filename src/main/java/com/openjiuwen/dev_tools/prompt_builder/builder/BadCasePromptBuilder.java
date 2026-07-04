/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.prompt_builder.builder;

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
import com.openjiuwen.dev_tools.tune.EvaluatedCase;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Prompt builder that optimizes prompts from evaluated bad cases.
 *
 * <p>Mirrors Python's {@code BadCasePromptBuilder} in
 * {@code openjiuwen/dev_tools/prompt_builder/builder/badcase_prompt_builder.py}.</p>
 */
public class BadCasePromptBuilder extends BasePromptBuilder {
    public static final int MAX_CASES_LIMIT = 10;

    private static final LoggerProtocol LOGGER = LogManager.getLogger("prompt_builder");
    private static final Pattern INTENT_PATTERN = Pattern.compile("<intent>((?:(?!<intent>).)*?)</intent>",
            Pattern.DOTALL);
    private static final Pattern SUMMARY_PATTERN = Pattern.compile("<summary>((?:(?!</summary>).)*?)</summary>",
            Pattern.DOTALL);

    private Map<String, PromptTemplate> template;

    public BadCasePromptBuilder(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
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
     * @param args cases and optional language
     * @return optimized prompt text
     */
    public CompletableFuture<String> build(Object prompt, Object... args) {
        Object[] safeArgs = args == null ? new Object[0] : args;
        List<EvaluatedCase> cases = evaluatedCases(safeArgs.length >= 1 ? safeArgs[0] : null);
        String language = safeArgs.length >= 2 && safeArgs[1] instanceof String text ? text : "zh-CN";
        return build(prompt, cases, language).thenApply(value -> value.orElse(null));
    }

    public CompletableFuture<Optional<String>> build(Object prompt, List<EvaluatedCase> cases) {
        return build(prompt, cases, "zh-CN");
    }

    public CompletableFuture<Optional<String>> build(Object prompt, List<EvaluatedCase> cases, String language) {
        template = PromptBuilderUtils.selectTemplate(language);
        String promptText = PromptBuilderUtils.getStringPrompt(prompt);
        return formatBadCaseTemplate(promptText, cases)
                .thenCompose(messages -> model.invoke(messages).toCompletableFuture())
                .thenApply(response -> Optional.ofNullable(response == null ? null : response.getContentAsString()));
    }

    @Override
    public CompletableFuture<Optional<String>> build(List<Object> args, Map<String, Object> kwargs) {
        Object prompt = argument(args, kwargs, 0, "prompt", null);
        List<EvaluatedCase> cases = evaluatedCases(argument(args, kwargs, 1, "cases", List.of()));
        String language = String.valueOf(argument(args, kwargs, 2, "language", "zh-CN"));
        return build(prompt, cases, language);
    }

    public Flow.Publisher<String> streamBuild(Object prompt, List<EvaluatedCase> cases) {
        return streamBuild(prompt, cases, "zh-CN");
    }

    /**
     * 0.1.12-compatible varargs streaming facade.
     *
     * @param prompt prompt to optimize
     * @param args cases and optional language
     * @return concatenated streamed response text
     */
    public CompletableFuture<String> streamBuild(Object prompt, Object... args) {
        Object[] safeArgs = args == null ? new Object[0] : args;
        List<EvaluatedCase> cases = evaluatedCases(safeArgs.length >= 1 ? safeArgs[0] : null);
        String language = safeArgs.length >= 2 && safeArgs[1] instanceof String text ? text : "zh-CN";
        return collectPublisher(streamBuild(prompt, cases, language));
    }

    public Flow.Publisher<String> streamBuild(Object prompt, List<EvaluatedCase> cases, String language) {
        template = PromptBuilderUtils.selectTemplate(language);
        String promptText = PromptBuilderUtils.getStringPrompt(prompt);
        return new StreamBuildPublisher(promptText, cases);
    }

    @Override
    public Flow.Publisher<?> streamBuild(List<Object> args, Map<String, Object> kwargs) {
        Object prompt = argument(args, kwargs, 0, "prompt", null);
        List<EvaluatedCase> cases = evaluatedCases(argument(args, kwargs, 1, "cases", List.of()));
        String language = String.valueOf(argument(args, kwargs, 2, "language", "zh-CN"));
        return streamBuild(prompt, cases, language);
    }

    CompletableFuture<List<BaseMessage>> formatBadCaseTemplate(String prompt, List<EvaluatedCase> cases) {
        return getFeedbackFromBadCase(prompt, cases).thenApply(feedback -> {
            PromptTemplate badCaseOptimizeTemplate = template.get("PROMPT_BAD_CASE_OPTIMIZE_TEMPLATE");
            return badCaseOptimizeTemplate.format(Map.of(
                    "original_prompt", prompt,
                    "feedback", feedback == null ? "" : feedback
            )).toMessages();
        });
    }

    CompletableFuture<String> getFeedbackFromBadCase(String prompt, List<EvaluatedCase> cases) {
        validateInput(prompt, cases);
        String badCaseString = buildBadCaseString(cases);
        PromptTemplate analyzeTemplate = template.get("PROMPT_BAD_CASE_ANALYZE_TEMPLATE");
        List<BaseMessage> messages = analyzeTemplate.format(Map.of(
                "original_prompt", prompt,
                "bad_cases", badCaseString
        )).toMessages();
        return model.invoke(messages)
                .toCompletableFuture()
                .thenApply(this::parseFeedbackSummary);
    }

    String parseFeedbackSummary(AssistantMessage response) {
        String content = response == null ? "" : response.getContentAsString();
        List<String> intents = new ArrayList<>();
        Matcher intentMatcher = INTENT_PATTERN.matcher(content);
        while (intentMatcher.find()) {
            intents.add(intentMatcher.group(1).strip());
        }
        if (intents.contains("false")) {
            LOGGER.warning("Failed to get intent input_data={}", content);
        }

        List<String> summaries = new ArrayList<>();
        Matcher summaryMatcher = SUMMARY_PATTERN.matcher(content);
        while (summaryMatcher.find()) {
            summaries.add(summaryMatcher.group(1).strip());
        }
        if (summaries.isEmpty()) {
            return content;
        }
        return summaries.get(summaries.size() - 1);
    }

    String buildBadCaseString(List<EvaluatedCase> cases) {
        PromptTemplate badCaseTemplate = template.get("FORMAT_BAD_CASE_TEMPLATE");
        return safeCases(cases).stream()
                .map(item -> badCaseTemplate.format(Map.of(
                        "question", pythonString(item.getCase().getInputs()),
                        "label", pythonString(item.getCase().getLabel()),
                        "answer", pythonString(item.getAnswer()),
                        "reason", item.getReason()
                )).getContent())
                .map(String::valueOf)
                .collect(Collectors.joining("\n"));
    }

    void validateInput(String prompt, List<EvaluatedCase> cases) {
        if (prompt == null) {
            throw buildPromptError(
                    StatusCode.TOOLCHAIN_FEEDBACK_TEMPLATE_EXECUTION_ERROR,
                    "prompt cannot be None");
        }
        if (prompt.strip().isEmpty()) {
            throw buildPromptError(
                    StatusCode.TOOLCHAIN_BAD_CASE_TEMPLATE_EXECUTION_ERROR,
                    "prompt cannot be empty");
        }
        if (cases == null || cases.isEmpty()) {
            throw buildPromptError(
                    StatusCode.TOOLCHAIN_BAD_CASE_TEMPLATE_EXECUTION_ERROR,
                    "The cases cannot be empty");
        }
        if (cases.size() > MAX_CASES_LIMIT) {
            throw buildPromptError(
                    StatusCode.TOOLCHAIN_BAD_CASE_TEMPLATE_EXECUTION_ERROR,
                    "The number of cases cannot exceed " + MAX_CASES_LIMIT);
        }
    }

    private static BaseError buildPromptError(StatusCode status, String errorMessage) {
        return ErrorHelper.buildError(status, "error_msg", errorMessage);
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

    @SuppressWarnings("unchecked")
    private static List<EvaluatedCase> evaluatedCases(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list && list.stream().allMatch(EvaluatedCase.class::isInstance)) {
            return (List<EvaluatedCase>) list;
        }
        throw new IllegalArgumentException("cases must be List<EvaluatedCase>");
    }

    private static List<EvaluatedCase> safeCases(List<EvaluatedCase> cases) {
        return cases == null ? List.of() : cases;
    }

    private static CompletableFuture<String> collectPublisher(Flow.Publisher<String> publisher) {
        CompletableFuture<String> future = new CompletableFuture<>();
        StringBuilder result = new StringBuilder();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {
                result.append(item == null ? "" : item);
            }

            @Override
            public void onError(Throwable throwable) {
                future.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                future.complete(result.toString());
            }
        });
        return future;
    }

    private static String pythonString(Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof Map<?, ?> map) {
            return pythonMapString(map);
        }
        if (value instanceof List<?> list) {
            return pythonListString(list);
        }
        return String.valueOf(value);
    }

    private static String pythonRepr(Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof String text) {
            return "'" + text.replace("\\", "\\\\").replace("'", "\\'") + "'";
        }
        if (value instanceof Map<?, ?> map) {
            return pythonMapString(map);
        }
        if (value instanceof List<?> list) {
            return pythonListString(list);
        }
        if (value instanceof Boolean bool) {
            return bool ? "True" : "False";
        }
        return String.valueOf(value);
    }

    private static String pythonMapString(Map<?, ?> map) {
        return map.entrySet().stream()
                .map(entry -> pythonRepr(entry.getKey()) + ": " + pythonRepr(entry.getValue()))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    private static String pythonListString(List<?> list) {
        return list.stream()
                .map(BadCasePromptBuilder::pythonRepr)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private final class StreamBuildPublisher implements Flow.Publisher<String> {
        private final String prompt;
        private final List<EvaluatedCase> cases;

        private StreamBuildPublisher(String prompt, List<EvaluatedCase> cases) {
            this.prompt = prompt;
            this.cases = cases;
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
                    formatBadCaseTemplate(prompt, cases)
                            .thenAccept(messages -> publishChunks(messages, subscriber, this))
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

                private void publishChunks(
                        List<BaseMessage> messages,
                        Flow.Subscriber<? super String> target,
                        Flow.Subscription subscription) {
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
