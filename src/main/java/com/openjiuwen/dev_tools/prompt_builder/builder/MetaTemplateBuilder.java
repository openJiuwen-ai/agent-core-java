// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.dev_tools.prompt_builder.builder;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 提示词元模板构建器。
 * 对应 Python {@code MetaTemplateBuilder}，接受 ModelRequestConfig 和 ModelClientConfig。
 */
public class MetaTemplateBuilder {
    private final ModelRequestConfig modelConfig;
    private final ModelClientConfig modelClientConfig;
    /** key: META_TEMPLATE_{name}, value: template string */
    private final Map<String, String> metaTemplates = new HashMap<>();

    private static final String META_TEMPLATE_NAME_PREFIX = "META_TEMPLATE_";

    public MetaTemplateBuilder(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this.modelConfig = modelConfig;
        this.modelClientConfig = modelClientConfig;
    }

    /**
     * 注册自定义元模板。
     * 对应 Python {@code register_meta_template(name, meta_template)}。
     *
     * @param name    模板名称（不含前缀）
     * @param content String 或 PromptTemplate
     */
    public void registerMetaTemplate(String name, Object content) {
        String templateName = META_TEMPLATE_NAME_PREFIX + name;
        if (content instanceof String s) {
            metaTemplates.put(templateName, s);
        } else if (content instanceof PromptTemplate pt) {
            Object c = pt.getContent();
            metaTemplates.put(templateName, c != null ? c.toString() : "");
        } else {
            throw new IllegalArgumentException(
                    "failed to register meta-template: " + name
                            + ", content must be String or PromptTemplate but was: "
                            + content.getClass().getName());
        }
    }

    /**
     * 构建提示词（阻塞，需要真实 LLM 连接）。
     * 对应 Python {@code async def build(...)}。
     *
     * @param prompt             提示词字符串
     * @param tools              工具列表（可为 null）
     * @param templateType       模板类型："general" / "plan" / "other"
     * @param customTemplateName templateType 为 "other" 时必填的模板名称
     * @param language           语言："zh-CN" / "en-US"
     * @return Mono 包含生成结果
     */
    public Mono<Optional<String>> build(Object prompt, Object tools, String templateType,
                                        String customTemplateName, String language) {
        validateBuildParams(prompt, tools, templateType, customTemplateName);
        return Mono.error(new UnsupportedOperationException("Stub: requires real LLM connection."));
    }

    /**
     * 流式构建提示词（需要真实 LLM 连接）。
     * 对应 Python {@code async def stream_build(...)}。
     */
    public Flux<String> streamBuild(Object prompt, Object tools, String templateType,
                                    String customTemplateName, String language) {
        validateBuildParams(prompt, tools, templateType, customTemplateName);
        return Flux.error(new UnsupportedOperationException("Stub: requires real LLM connection."));
    }

    /**
     * 参数校验，对应 Python {@code _is_valid_prompt} 和 {@code _format_meta_template} 前置检查。
     */
    private void validateBuildParams(Object prompt, Object tools, String templateType,
                                     String customTemplateName) {
        // prompt 类型检查
        if (!(prompt instanceof String)) {
            throw new IllegalArgumentException("prompt must be a String");
        }
        // prompt 不能为空（对应 Python _is_valid_prompt: prompt.strip()）
        if (((String) prompt).isBlank()) {
            throw new IllegalArgumentException("prompt cannot be empty");
        }
        // tools 类型检查
        if (tools != null && !(tools instanceof List<?>)) {
            throw new IllegalArgumentException("tools must be List or null");
        }
        // other 类型模板校验（对应 Python _format_custom_meta_template 前置检查）
        if ("other".equals(templateType)) {
            if (customTemplateName == null || customTemplateName.isBlank()) {
                throw new IllegalArgumentException(
                        "failed to get custom meta-template, please provide template name");
            }
            String key = META_TEMPLATE_NAME_PREFIX + customTemplateName;
            if (!metaTemplates.containsKey(key)) {
                throw new IllegalArgumentException(
                        "failed to get custom meta-template: " + key);
            }
            if (metaTemplates.get(key).isBlank()) {
                throw new IllegalArgumentException(
                        "registered meta template cannot be empty");
            }
        }
    }
}
