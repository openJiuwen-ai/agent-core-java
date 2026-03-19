// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

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
 * 元模板构建器
 * <p>
 * Mirrors Python's {@code openjiuwen.dev_tools.prompt_builder.builder.meta_template_builder.MetaTemplateBuilder}
 */
public class MetaTemplateBuilder extends BasePromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(MetaTemplateBuilder.class);
    private static final String META_TEMPLATE_NAME_PREFIX = "META_TEMPLATE_";

    /**
     * 元模板管理器
     */
    private final Map<String, PromptTemplate> metaTemplateManager = new HashMap<>();

    /**
     * 当前模板
     */
    private Object template;

    /**
     * 构造函数
     *
     * @param modelConfig       模型请求配置
     * @param modelClientConfig 模型客户端配置
     */
    public MetaTemplateBuilder(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        super(modelConfig, modelClientConfig);
        this.template = PromptTemplateUtils.selectTemplate("zh-CN");
    }

    /**
     * 获取元模板
     *
     * @param templateName 模板名称
     * @return 模板对象
     */
    public Object getMetaTemplate(String templateName) {
        return metaTemplateManager.get(templateName);
    }

    /**
     * 移除并返回元模板
     *
     * @param templateName 模板名称
     * @return 模板对象
     */
    public Object popMetaTemplate(String templateName) {
        return metaTemplateManager.remove(templateName);
    }

    /**
     * 注册元模板
     *
     * @param name         模板名称
     * @param metaTemplate 模板内容（String 或 PromptTemplate）
     */
    public void registerMetaTemplate(String name, Object metaTemplate) {
        String templateName = META_TEMPLATE_NAME_PREFIX + name;
        PromptTemplate templateToReg;
        
        if (metaTemplate instanceof String) {
            templateToReg = PromptTemplate.builder().content(metaTemplate).build();
        } else if (metaTemplate instanceof PromptTemplate pt) {
            // Create a copy
            templateToReg = PromptTemplate.builder()
                    .name(pt.getName())
                    .content(pt.getContent())
                    .placeholderPrefix(pt.getPlaceholderPrefix())
                    .placeholderSuffix(pt.getPlaceholderSuffix())
                    .build();
        } else {
            throw ErrorHelper.buildError(StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR,
                    "error_msg", "failed to register meta-template: " + name);
        }
        metaTemplateManager.put(templateName, templateToReg);
    }

    @Override
    public CompletableFuture<String> build(Object prompt, Object... args) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                BuildParams params = parseBuildParams(args);
                this.template = PromptTemplateUtils.selectTemplate(params.language);
                String promptStr = PromptTemplateUtils.getStringPrompt(prompt);
                isValidPrompt(promptStr, params.tools);
                List<Object> messages = formatMetaTemplate(promptStr, params.tools, params.templateType, params.customTemplateName);
                AssistantMessage response = model.invoke(messages, null, null, null, null, null, null, null, null, null);
                return response != null ? response.getContentAsString() : null;
            } catch (Exception e) {
                log.error("Error building meta template", e);
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<String> streamBuild(Object prompt, Object... args) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                BuildParams params = parseBuildParams(args);
                this.template = PromptTemplateUtils.selectTemplate(params.language);
                String promptStr = PromptTemplateUtils.getStringPrompt(prompt);
                isValidPrompt(promptStr, params.tools);
                List<Object> messages = formatMetaTemplate(promptStr, params.tools, params.templateType, params.customTemplateName);
                
                StringBuilder result = new StringBuilder();
                var iterator = model.stream(messages, null, null, null, null, null, null, null, null, null);
                while (iterator.hasNext()) {
                    var chunk = iterator.next();
                    result.append(chunk.getContentAsString());
                }
                return result.toString();
            } catch (Exception e) {
                log.error("Error streaming meta template", e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 解析构建参数
     */
    private BuildParams parseBuildParams(Object... args) {
        BuildParams params = new BuildParams();
        if (args.length >= 1 && args[0] instanceof List) {
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

    /**
     * 格式化元模板
     */
    private List<Object> formatMetaTemplate(String prompt, List<ToolInfo> tools, String templateType, String customTemplateName) {
        if ("other".equals(templateType)) {
            return formatCustomMetaTemplate(customTemplateName, prompt, tools);
        } else {
            return formatPredefinedMetaTemplate(templateType, prompt, tools);
        }
    }

    /**
     * 格式化预定义元模板
     */
    private List<Object> formatPredefinedMetaTemplate(String templateType, String prompt, List<ToolInfo> tools) {
        PromptTemplate metaSystemTemplate;
        PromptTemplate metaUserTemplate;
        
        if ("plan".equals(templateType)) {
            metaSystemTemplate = PromptTemplatesZh.PROMPT_BUILD_PLAN_META_SYSTEM_TEMPLATE;
            metaUserTemplate = PromptTemplatesZh.PROMPT_BUILD_PLAN_META_USER_TEMPLATE;
        } else {
            if (!"general".equals(templateType)) {
                log.warn("Invalid template_type: {}, using `general` instead", templateType);
            }
            metaSystemTemplate = PromptTemplatesZh.PROMPT_BUILD_GENERAL_META_SYSTEM_TEMPLATE;
            metaUserTemplate = PromptTemplatesZh.PROMPT_BUILD_GENERAL_META_USER_TEMPLATE;
        }
        
        List<Object> messages = new ArrayList<>(metaSystemTemplate.toMessages());
        Map<String, Object> formatParams = new HashMap<>();
        formatParams.put("instruction", prompt);
        formatParams.put("tools", tools != null ? tools.toString() : "null");
        messages.addAll(metaUserTemplate.format(formatParams).toMessages());
        return messages;
    }

    /**
     * 格式化自定义元模板
     */
    private List<Object> formatCustomMetaTemplate(String customMetaTemplateName, String prompt, List<ToolInfo> tools) {
        if (customMetaTemplateName == null || customMetaTemplateName.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR,
                    "error_msg", "failed to get custom meta-template, please provide template name");
        }
        
        String fullTemplateName = META_TEMPLATE_NAME_PREFIX + customMetaTemplateName;
        PromptTemplate customMetaTemplate = metaTemplateManager.get(fullTemplateName);
        if (customMetaTemplate == null) {
            throw ErrorHelper.buildError(StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR,
                    "error_msg", "failed to get custom meta-template: " + fullTemplateName);
        }
        
        Map<String, Object> formatParams = new HashMap<>();
        formatParams.put("instruction", prompt);
        formatParams.put("tools", tools != null ? tools.toString() : "null");
        return new ArrayList<>(customMetaTemplate.format(formatParams).toMessages());
    }

    /**
     * 验证提示词有效性
     */
    private void isValidPrompt(String prompt, List<ToolInfo> tools) {
        if (prompt == null) {
            throw ErrorHelper.buildError(StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR,
                    "error_msg", "prompt cannot be None");
        }
        if (prompt.trim().isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR,
                    "error_msg", "prompt cannot be empty");
        }
        if (tools != null) {
            for (Object tool : tools) {
                if (!(tool instanceof ToolInfo)) {
                    throw ErrorHelper.buildError(StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR,
                            "error_msg", "each tool must be an instance of ToolInfo");
                }
            }
        }
    }

    /**
     * 构建参数
     */
    private static class BuildParams {
        List<ToolInfo> tools = null;
        String templateType = "general";
        String customTemplateName = null;
        String language = "zh-CN";
    }
}