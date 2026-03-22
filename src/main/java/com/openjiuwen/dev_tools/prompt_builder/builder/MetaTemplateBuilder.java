/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.dev_tools.prompt_builder.builder;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Java translation of Python's MetaTemplateBuilder.
 */
public class MetaTemplateBuilder {

    static final String META_TEMPLATE_NAME_PREFIX = "META_TEMPLATE_";

    private final Model model;
    private final Map<String, PromptTemplate> metaTemplateManager = new HashMap<>();
    private Class<?> template = PromptTemplatesZh.class;

    public MetaTemplateBuilder(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this.model = new Model(modelClientConfig, modelConfig);
    }

    public PromptTemplate getMetaTemplate(String templateName) {
        return metaTemplateManager.get(templateName);
    }

    public PromptTemplate popMetaTemplate(String templateName) {
        return metaTemplateManager.remove(templateName);
    }

    public void registerMetaTemplate(String name, Object metaTemplate) {
        String templateName = META_TEMPLATE_NAME_PREFIX + name;
        PromptTemplate templateToRegister;
        if (metaTemplate instanceof String content) {
            templateToRegister = PromptTemplate.builder().content(content).build();
        } else if (metaTemplate instanceof PromptTemplate promptTemplate) {
            templateToRegister = copyPromptTemplate(promptTemplate);
        } else {
            throw ErrorHelper.buildError(
                    StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR,
                    "error_msg",
                    "failed to register meta-template: " + name
            );
        }
        metaTemplateManager.put(templateName, templateToRegister);
    }

    public Mono<Optional<String>> build(
            Object prompt,
            Object tools,
            String templateType,
            String customTemplateName,
            String language
    ) {
        return Mono.fromCallable(() -> {
            template = resolveTemplate(language);
            String promptContent = PromptTemplateUtils.getStringPrompt(prompt);
            List<ToolInfo> toolInfos = normalizeTools(tools);
            isValidPrompt(promptContent, toolInfos);
            List<BaseMessage> messages = formatMetaTemplate(promptContent, toolInfos, templateType, customTemplateName);
            AssistantMessage response = model.invoke(messages, null, null, null, null, null, null, null, null, null);
            if (response == null || response.getContent() == null) {
                return Optional.empty();
            }
            return Optional.of(response.getContentAsString());
        });
    }

    public Flux<String> streamBuild(
            Object prompt,
            Object tools,
            String templateType,
            String customTemplateName,
            String language
    ) {
        return Flux.defer(() -> {
            try {
                template = resolveTemplate(language);
                String promptContent = PromptTemplateUtils.getStringPrompt(prompt);
                List<ToolInfo> toolInfos = normalizeTools(tools);
                isValidPrompt(promptContent, toolInfos);
                List<BaseMessage> messages = formatMetaTemplate(promptContent, toolInfos, templateType, customTemplateName);
                Iterator<AssistantMessageChunk> iterator =
                        model.stream(messages, null, null, null, null, null, null, null, null, null);
                Iterable<AssistantMessageChunk> iterable = () -> iterator;
                return Flux.fromIterable(iterable).map(chunk -> chunk != null ? chunk.getContentAsString() : "");
            } catch (RuntimeException exception) {
                return Flux.error(exception);
            } catch (Exception exception) {
                return Flux.error(Exceptions.propagate(exception));
            }
        });
    }

    private List<BaseMessage> formatMetaTemplate(
            String prompt,
            List<ToolInfo> tools,
            String templateType,
            String customTemplateName
    ) {
        if ("other".equals(templateType)) {
            return formatCustomMetaTemplate(customTemplateName, prompt, tools);
        }
        return formatPredefinedMetaTemplate(templateType, prompt, tools);
    }

    private List<BaseMessage> formatPredefinedMetaTemplate(
            String templateType,
            String prompt,
            List<ToolInfo> tools
    ) {
        PromptTemplate metaSystemTemplate;
        PromptTemplate metaUserTemplate;
        if ("plan".equals(templateType)) {
            metaSystemTemplate = getTemplateField("PROMPT_BUILD_PLAN_META_SYSTEM_TEMPLATE");
            metaUserTemplate = getTemplateField("PROMPT_BUILD_PLAN_META_USER_TEMPLATE");
        } else {
            metaSystemTemplate = getTemplateField("PROMPT_BUILD_GENERAL_META_SYSTEM_TEMPLATE");
            metaUserTemplate = getTemplateField("PROMPT_BUILD_GENERAL_META_USER_TEMPLATE");
        }

        Map<String, Object> formatParams = new HashMap<>();
        formatParams.put("instruction", prompt);
        formatParams.put("tools", stringifyTools(tools));

        List<BaseMessage> messages = new ArrayList<>(metaSystemTemplate.toMessages());
        messages.addAll(metaUserTemplate.format(formatParams).toMessages());
        return messages;
    }

    private List<BaseMessage> formatCustomMetaTemplate(
            String customMetaTemplateName,
            String prompt,
            List<ToolInfo> tools
    ) {
        if (customMetaTemplateName == null || customMetaTemplateName.isBlank()) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR,
                    "error_msg",
                    "failed to get custom meta-template, please provide template name"
            );
        }

        String templateName = META_TEMPLATE_NAME_PREFIX + customMetaTemplateName;
        PromptTemplate customMetaTemplate = metaTemplateManager.get(templateName);
        if (customMetaTemplate == null) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR,
                    "error_msg",
                    "failed to get custom meta-template: " + templateName
            );
        }

        Map<String, Object> formatParams = new HashMap<>();
        formatParams.put("instruction", prompt);
        formatParams.put("tools", stringifyTools(tools));
        return customMetaTemplate.format(formatParams).toMessages();
    }

    private void isValidPrompt(String prompt, List<ToolInfo> tools) {
        if (prompt == null) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR,
                    "error_msg",
                    "prompt cannot be None"
            );
        }
        if (prompt.isBlank()) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR,
                    "error_msg",
                    "prompt cannot be empty"
            );
        }
        if (tools != null && tools.stream().anyMatch(tool -> !(tool instanceof ToolInfo))) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR,
                    "error_msg",
                    "each tool must be an instance of ToolInfo"
            );
        }
    }

    @SuppressWarnings("unchecked")
    private List<ToolInfo> normalizeTools(Object tools) {
        if (tools == null) {
            return null;
        }
        if (!(tools instanceof List<?> list)) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR,
                    "error_msg",
                    "each tool must be an instance of ToolInfo"
            );
        }
        for (Object item : list) {
            if (!(item instanceof ToolInfo)) {
                throw ErrorHelper.buildError(
                        StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR,
                        "error_msg",
                        "each tool must be an instance of ToolInfo"
                );
            }
        }
        return (List<ToolInfo>) list;
    }

    private String stringifyTools(List<ToolInfo> tools) {
        return tools == null ? "None" : String.valueOf(tools);
    }

    private PromptTemplate getTemplateField(String fieldName) {
        try {
            Field field = template.getField(fieldName);
            return (PromptTemplate) field.get(null);
        } catch (ReflectiveOperationException exception) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR,
                    "error_msg",
                    "failed to load meta-template: " + fieldName
            );
        }
    }

    private Class<?> resolveTemplate(String language) {
        Object resolved = PromptTemplateUtils.selectTemplate(language);
        if (resolved instanceof Class<?> templateClass) {
            return templateClass;
        }
        return PromptTemplatesZh.class;
    }

    private PromptTemplate copyPromptTemplate(PromptTemplate templateToCopy) {
        Object content = templateToCopy.getContent();
        Object copiedContent;
        if (content instanceof String) {
            copiedContent = content;
        } else if (content instanceof List<?>) {
            copiedContent = templateToCopy.toMessages();
        } else {
            copiedContent = content;
        }
        return PromptTemplate.builder()
                .name(templateToCopy.getName())
                .content(copiedContent)
                .placeholderPrefix(templateToCopy.getPlaceholderPrefix())
                .placeholderSuffix(templateToCopy.getPlaceholderSuffix())
                .build();
    }
}
