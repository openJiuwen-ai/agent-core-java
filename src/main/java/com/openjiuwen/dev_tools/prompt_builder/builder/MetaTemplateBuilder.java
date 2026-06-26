/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.prompt_builder.builder;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.dev_tools.prompt_builder.BasePromptBuilder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

/**
 * Prompt builder that asks an LLM to build concrete prompts from meta templates.
 *
 * <p>Mirrors Python's {@code MetaTemplateBuilder} in
 * {@code openjiuwen/dev_tools/prompt_builder/builder/meta_template_builder.py}.</p>
 */
public class MetaTemplateBuilder extends BasePromptBuilder {
    public static final String META_TEMPLATE_NAME_PREFIX = "META_TEMPLATE_";

    private static final String TEMPLATE_TYPE_GENERAL = "general";
    private static final String TEMPLATE_TYPE_PLAN = "plan";
    private static final String TEMPLATE_TYPE_OTHER = "other";
    private static final LoggerProtocol LOGGER = LogManager.getLogger("prompt_builder");

    private final Map<String, PromptTemplate> metaTemplateManager = new LinkedHashMap<>();
    private Map<String, PromptTemplate> template;

    public MetaTemplateBuilder(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        super(modelConfig, modelClientConfig);
        this.template = PromptBuilderUtils.selectTemplate();
    }

    public Map<String, PromptTemplate> getTemplate() {
        return template;
    }

    public Optional<PromptTemplate> getMetaTemplate(String templateName) {
        return Optional.ofNullable(metaTemplateManager.get(templateName));
    }

    public Optional<PromptTemplate> popMetaTemplate(String templateName) {
        return Optional.ofNullable(metaTemplateManager.remove(templateName));
    }

    public void registerMetaTemplate(String name, Object metaTemplate) {
        String templateName = META_TEMPLATE_NAME_PREFIX + name;
        PromptTemplate templateToRegister;
        if (metaTemplate instanceof String text) {
            templateToRegister = PromptTemplate.builder().content(text).build();
        } else if (metaTemplate instanceof PromptTemplate promptTemplate) {
            templateToRegister = promptTemplate.format(Map.of());
        } else {
            throw buildMetaError("failed to register meta-template: " + name);
        }
        metaTemplateManager.put(templateName, templateToRegister);
    }

    public CompletableFuture<Optional<String>> build(Object prompt) {
        return buildInternal(prompt, null, TEMPLATE_TYPE_GENERAL, null, "zh-CN");
    }

    public CompletableFuture<Optional<String>> build(Object prompt, List<ToolInfo> tools) {
        return buildInternal(prompt, tools, TEMPLATE_TYPE_GENERAL, null, "zh-CN");
    }

    public CompletableFuture<Optional<String>> build(
            Object prompt,
            List<ToolInfo> tools,
            String templateType,
            String customTemplateName,
            String language) {
        return buildInternal(prompt, tools, templateType, customTemplateName, language);
    }

    private CompletableFuture<Optional<String>> buildInternal(
            Object prompt,
            List<?> tools,
            String templateType,
            String customTemplateName,
            String language) {
        template = PromptBuilderUtils.selectTemplate(language);
        String promptText = PromptBuilderUtils.getStringPrompt(prompt);
        isValidPrompt(promptText, tools);
        List<BaseMessage> messages = formatMetaTemplate(promptText, tools, templateType, customTemplateName);
        return model.invoke(messages)
                .toCompletableFuture()
                .thenApply(response -> Optional.ofNullable(response == null ? null : response.getContentAsString()));
    }

    @Override
    public CompletableFuture<Optional<String>> build(List<Object> args, Map<String, Object> kwargs) {
        Object prompt = argument(args, kwargs, 0, "prompt", null);
        List<?> tools = listArgument(argument(args, kwargs, 1, "tools", null));
        String templateType = stringArgument(argument(args, kwargs, 2, "template_type", TEMPLATE_TYPE_GENERAL));
        String customTemplateName = stringArgument(argument(args, kwargs, 3, "custom_template_name", null));
        String language = stringArgument(argument(args, kwargs, 4, "language", "zh-CN"));
        return buildInternal(prompt, tools, templateType, customTemplateName, language == null ? "zh-CN" : language);
    }

    public Flow.Publisher<String> streamBuild(Object prompt, List<ToolInfo> tools) {
        return streamBuild(prompt, tools, TEMPLATE_TYPE_GENERAL, null, "zh-CN");
    }

    public Flow.Publisher<String> streamBuild(
            Object prompt,
            List<ToolInfo> tools,
            String templateType,
            String customTemplateName,
            String language) {
        template = PromptBuilderUtils.selectTemplate(language);
        String promptText = PromptBuilderUtils.getStringPrompt(prompt);
        isValidPrompt(promptText, tools);
        return new StreamBuildPublisher(promptText, tools, templateType, customTemplateName);
    }

    @Override
    public Flow.Publisher<?> streamBuild(List<Object> args, Map<String, Object> kwargs) {
        Object prompt = argument(args, kwargs, 0, "prompt", null);
        List<?> tools = listArgument(argument(args, kwargs, 1, "tools", null));
        String templateType = stringArgument(argument(args, kwargs, 2, "template_type", TEMPLATE_TYPE_GENERAL));
        String customTemplateName = stringArgument(argument(args, kwargs, 3, "custom_template_name", null));
        String language = stringArgument(argument(args, kwargs, 4, "language", "zh-CN"));
        template = PromptBuilderUtils.selectTemplate(language == null ? "zh-CN" : language);
        String promptText = PromptBuilderUtils.getStringPrompt(prompt);
        isValidPrompt(promptText, tools);
        return new StreamBuildPublisher(promptText, tools, templateType, customTemplateName);
    }

    List<BaseMessage> formatMetaTemplate(
            String prompt,
            List<?> tools,
            String templateType,
            String customTemplateName) {
        if (TEMPLATE_TYPE_OTHER.equals(templateType)) {
            return formatCustomMetaTemplate(customTemplateName, prompt, tools);
        }
        return formatPredefinedMetaTemplate(templateType, prompt, tools);
    }

    List<BaseMessage> formatPredefinedMetaTemplate(String templateType, String prompt, List<?> tools) {
        PromptTemplate metaSystemTemplate;
        PromptTemplate metaUserTemplate;
        if (TEMPLATE_TYPE_PLAN.equals(templateType)) {
            metaSystemTemplate = template.get("PROMPT_BUILD_PLAN_META_SYSTEM_TEMPLATE");
            metaUserTemplate = template.get("PROMPT_BUILD_PLAN_META_USER_TEMPLATE");
        } else {
            if (!TEMPLATE_TYPE_GENERAL.equals(templateType)) {
                LOGGER.warning("Invalid template_type, using `general` instead input_data={}, template_type={}",
                        prompt,
                        templateType);
            }
            metaSystemTemplate = template.get("PROMPT_BUILD_GENERAL_META_SYSTEM_TEMPLATE");
            metaUserTemplate = template.get("PROMPT_BUILD_GENERAL_META_USER_TEMPLATE");
        }

        List<BaseMessage> messages = new ArrayList<>(metaSystemTemplate.toMessages());
        messages.addAll(metaUserTemplate.format(Map.of(
                "instruction", prompt,
                "tools", pythonString(tools)
        )).toMessages());
        return messages;
    }

    List<BaseMessage> formatCustomMetaTemplate(String customMetaTemplateName, String prompt, List<?> tools) {
        if (customMetaTemplateName == null || customMetaTemplateName.isBlank()) {
            throw buildMetaError("failed to get custom meta-template, please provide template name");
        }
        String prefixedName = META_TEMPLATE_NAME_PREFIX + customMetaTemplateName;
        PromptTemplate customMetaTemplate = metaTemplateManager.get(prefixedName);
        if (customMetaTemplate == null) {
            throw buildMetaError("failed to get custom meta-template: " + prefixedName);
        }
        return customMetaTemplate.format(Map.of(
                "instruction", prompt,
                "tools", pythonString(tools)
        )).toMessages();
    }

    void isValidPrompt(String prompt, List<?> tools) {
        if (prompt == null) {
            throw buildMetaError("prompt cannot be None");
        }
        if (prompt.strip().isEmpty()) {
            throw buildMetaError("prompt cannot be empty");
        }
        if (tools != null && !tools.isEmpty() && tools.stream().anyMatch(tool -> !(tool instanceof ToolInfo))) {
            throw buildMetaError("each tool must be an instance of ToolInfo");
        }
    }

    private static BaseError buildMetaError(String errorMessage) {
        return ErrorHelper.buildError(
                StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR,
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
        return value == null ? null : String.valueOf(value);
    }

    private static List<?> listArgument(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            return list;
        }
        return List.of(value);
    }

    private static String pythonString(List<?> tools) {
        return tools == null ? "None" : tools.toString();
    }

    private final class StreamBuildPublisher implements Flow.Publisher<String> {
        private final String prompt;
        private final List<?> tools;
        private final String templateType;
        private final String customTemplateName;

        private StreamBuildPublisher(String prompt, List<?> tools, String templateType, String customTemplateName) {
            this.prompt = prompt;
            this.tools = tools;
            this.templateType = templateType;
            this.customTemplateName = customTemplateName;
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
                    try {
                        List<BaseMessage> messages = formatMetaTemplate(prompt, tools, templateType, customTemplateName);
                        publishChunks(messages, subscriber);
                    } catch (RuntimeException exception) {
                        if (!canceled) {
                            subscriber.onError(exception);
                        }
                    }
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
