/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.prompt_builder.builder;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.dev_tools.prompt_builder.BasePromptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code openjiuwen.dev_tools.prompt_builder.builder.meta_template_builder.MetaTemplateBuilder}.
 */
public class MetaTemplateBuilder extends BasePromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(MetaTemplateBuilder.class);
    static final String META_TEMPLATE_NAME_PREFIX = "META_TEMPLATE_";

    private final Map<String, PromptTemplate> metaTemplateManager = new HashMap<>();
    private Object template;

    public MetaTemplateBuilder(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        super(modelConfig, modelClientConfig);
        this.template = PromptTemplateUtils.selectTemplate("zh-CN");
    }

    public PromptTemplate getMetaTemplate(String templateName) {
        return metaTemplateManager.get(templateName);
    }

    public PromptTemplate popMetaTemplate(String templateName) {
        return metaTemplateManager.remove(templateName);
    }

    public void registerMetaTemplate(String name, Object metaTemplate) {
        String templateName = META_TEMPLATE_NAME_PREFIX + name;
        PromptTemplate templateToReg;

        if (metaTemplate instanceof String content) {
            templateToReg = PromptTemplate.builder().content(content).build();
        } else if (metaTemplate instanceof PromptTemplate promptTemplate) {
            templateToReg = copyPromptTemplate(promptTemplate);
        } else {
            throw ErrorHelper.buildError(
                    StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR,
                    "error_msg",
                    "failed to register meta-template: " + name
            );
        }
        metaTemplateManager.put(templateName, templateToReg);
    }

    @Override
    public CompletableFuture<String> build(Object prompt, Object... args) {
        return UnwrappedCompletableFuture.supplyAsync(() -> {
            try {
                BuildParams params = parseBuildParams(args);
                this.template = PromptTemplateUtils.selectTemplate(params.language);
                String promptStr = PromptTemplateUtils.getStringPrompt(prompt);
                isValidPrompt(promptStr, params.rawTools);
                List<Object> messages = formatMetaTemplate(
                        promptStr,
                        params.tools,
                        params.templateType,
                        params.customTemplateName
                );
                AssistantMessage response = model.invoke(messages, null, null, null, null, null, null, null, null, null);
                return response != null ? response.getContentAsString() : null;
            } catch (Exception exception) {
                log.error("Error building meta template", exception);
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
                String promptStr = PromptTemplateUtils.getStringPrompt(prompt);
                isValidPrompt(promptStr, params.rawTools);
                List<Object> messages = formatMetaTemplate(
                        promptStr,
                        params.tools,
                        params.templateType,
                        params.customTemplateName
                );

                StringBuilder result = new StringBuilder();
                var iterator = model.stream(messages, null, null, null, null, null, null, null, null, null);
                while (iterator.hasNext()) {
                    var chunk = iterator.next();
                    result.append(chunk.getContentAsString());
                }
                return result.toString();
            } catch (Exception exception) {
                log.error("Error streaming meta template", exception);
                throw new RuntimeException(exception);
            }
        });
    }

    private BuildParams parseBuildParams(Object... args) {
        BuildParams params = new BuildParams();
        if (args.length >= 1) {
            params.rawTools = args[0];
        }
        if (args.length >= 1 && args[0] instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<ToolInfo> tools = (List<ToolInfo>) args[0];
            params.tools = tools;
        }
        if (args.length >= 2 && args[1] instanceof String) {
            params.templateType = (String) args[1];
        }
        if (args.length >= 3 && args[2] instanceof String) {
            params.customTemplateName = (String) args[2];
        }
        if (args.length >= 4 && args[3] instanceof String) {
            params.language = (String) args[3];
        }
        return params;
    }

    private List<Object> formatMetaTemplate(
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

    private List<Object> formatPredefinedMetaTemplate(
            String templateType,
            String prompt,
            List<ToolInfo> tools
    ) {
        PromptTemplate metaSystemTemplate;
        PromptTemplate metaUserTemplate;

        if ("plan".equals(templateType)) {
            metaSystemTemplate = PromptTemplateUtils.getTemplate(template, "PROMPT_BUILD_PLAN_META_SYSTEM_TEMPLATE");
            metaUserTemplate = PromptTemplateUtils.getTemplate(template, "PROMPT_BUILD_PLAN_META_USER_TEMPLATE");
        } else {
            if (!"general".equals(templateType)) {
                log.warn("Invalid template_type: {}, using `general` instead", templateType);
            }
            metaSystemTemplate = PromptTemplateUtils.getTemplate(template, "PROMPT_BUILD_GENERAL_META_SYSTEM_TEMPLATE");
            metaUserTemplate = PromptTemplateUtils.getTemplate(template, "PROMPT_BUILD_GENERAL_META_USER_TEMPLATE");
        }

        Map<String, Object> formatParams = new HashMap<>();
        formatParams.put("instruction", prompt);
        formatParams.put("tools", tools != null ? tools.toString() : "None");

        List<Object> messages = new ArrayList<>(metaSystemTemplate.toMessages());
        messages.addAll(metaUserTemplate.format(formatParams).toMessages());
        return messages;
    }

    private List<Object> formatCustomMetaTemplate(
            String customMetaTemplateName,
            String prompt,
            List<ToolInfo> tools
    ) {
        if (customMetaTemplateName == null || customMetaTemplateName.isEmpty()) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR,
                    "error_msg",
                    "failed to get custom meta-template, please provide template name"
            );
        }

        String fullTemplateName = META_TEMPLATE_NAME_PREFIX + customMetaTemplateName;
        PromptTemplate customMetaTemplate = metaTemplateManager.get(fullTemplateName);
        if (customMetaTemplate == null) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR,
                    "error_msg",
                    "failed to get custom meta-template: " + fullTemplateName
            );
        }

        Map<String, Object> formatParams = new HashMap<>();
        formatParams.put("instruction", prompt);
        formatParams.put("tools", tools != null ? tools.toString() : "None");
        return new ArrayList<>(customMetaTemplate.format(formatParams).toMessages());
    }

    private void isValidPrompt(String prompt, Object tools) {
        if (prompt == null) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR,
                    "error_msg",
                    "prompt cannot be None"
            );
        }
        if (prompt.trim().isEmpty()) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR,
                    "error_msg",
                    "prompt cannot be empty"
            );
        }
        if (tools == null) {
            return;
        }
        if (!(tools instanceof List<?> toolList)) {
            throw ErrorHelper.buildError(
                    StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR,
                    "error_msg",
                    "each tool must be an instance of ToolInfo"
            );
        }
        for (Object tool : toolList) {
            if (!(tool instanceof ToolInfo)) {
                throw ErrorHelper.buildError(
                        StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR,
                        "error_msg",
                        "each tool must be an instance of ToolInfo"
                );
            }
        }
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

    private static class BuildParams {
        List<ToolInfo> tools = null;
        Object rawTools = null;
        String templateType = "general";
        String customTemplateName = null;
        String language = "zh-CN";
    }
}
