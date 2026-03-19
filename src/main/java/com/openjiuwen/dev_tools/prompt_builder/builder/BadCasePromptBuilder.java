// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.dev_tools.prompt_builder.builder;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.dev_tools.prompt_builder.BasePromptBuilder;
import com.openjiuwen.dev_tools.tune.EvaluatedCase;
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
 * 反例提示词构建器
 * <p>
 * Mirrors Python's {@code openjiuwen.dev_tools.prompt_builder.builder.badcase_prompt_builder.BadCasePromptBuilder}
 */
public class BadCasePromptBuilder extends BasePromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(BadCasePromptBuilder.class);
    private static final int MAX_CASES_LIMIT = 10;

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
    public BadCasePromptBuilder(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        super(modelConfig, modelClientConfig);
        this.template = PromptTemplateUtils.selectTemplate("zh-CN");
    }

    @Override
    public CompletableFuture<String> build(Object prompt, Object... args) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                @SuppressWarnings("unchecked")
                List<EvaluatedCase> cases = args.length >= 1 ? (List<EvaluatedCase>) args[0] : null;
                String language = args.length >= 2 && args[1] instanceof String ? (String) args[1] : "zh-CN";
                
                this.template = PromptTemplateUtils.selectTemplate(language);
                String promptStr = PromptTemplateUtils.getStringPrompt(prompt);
                List<Object> messages = formatBadCaseTemplate(promptStr, cases).get();
                AssistantMessage response = model.invoke(messages, null, null, null, null, null, null, null, null, null);
                return response != null ? response.getContentAsString() : null;
            } catch (Exception e) {
                log.error("Error building bad case template", e);
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<String> streamBuild(Object prompt, Object... args) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                @SuppressWarnings("unchecked")
                List<EvaluatedCase> cases = args.length >= 1 ? (List<EvaluatedCase>) args[0] : null;
                String language = args.length >= 2 && args[1] instanceof String ? (String) args[1] : "zh-CN";
                
                this.template = PromptTemplateUtils.selectTemplate(language);
                String promptStr = PromptTemplateUtils.getStringPrompt(prompt);
                List<Object> messages = formatBadCaseTemplate(promptStr, cases).get();
                
                StringBuilder result = new StringBuilder();
                var iterator = model.stream(messages, null, null, null, null, null, null, null, null, null);
                while (iterator.hasNext()) {
                    var chunk = iterator.next();
                    result.append(chunk.getContentAsString());
                }
                return result.toString();
            } catch (Exception e) {
                log.error("Error streaming bad case template", e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 格式化反例模板
     */
    private CompletableFuture<List<Object>> formatBadCaseTemplate(String prompt, List<EvaluatedCase> cases) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String feedback = getFeedbackFromBadCase(prompt, cases).get();
                PromptTemplate badCaseOptimizeTemplate = PromptTemplatesZh.PROMPT_BAD_CASE_OPTIMIZE_TEMPLATE;
                Map<String, Object> formatParams = new HashMap<>();
                formatParams.put("original_prompt", prompt);
                formatParams.put("feedback", feedback);
                return new ArrayList<>(badCaseOptimizeTemplate.format(formatParams).toMessages());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 从反例获取反馈
     */
    private CompletableFuture<String> getFeedbackFromBadCase(String prompt, List<EvaluatedCase> cases) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                validateInput(prompt, cases);
                String badCaseString = buildBadCaseString(cases);
                PromptTemplate analyzeTemplate = PromptTemplatesZh.PROMPT_BAD_CASE_ANALYZE_TEMPLATE;
                Map<String, Object> formatParams = new HashMap<>();
                formatParams.put("original_prompt", prompt);
                formatParams.put("bad_cases", badCaseString);
                List<Object> messages = new ArrayList<>(analyzeTemplate.format(formatParams).toMessages());
                AssistantMessage response = model.invoke(messages, null, null, null, null, null, null, null, null, null);
                return parseFeedbackSummary(response);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 解析反馈摘要
     */
    private String parseFeedbackSummary(AssistantMessage response) {
        String content = response.getContentAsString();
        
        // Extract intent
        Pattern intentPattern = Pattern.compile("<intent>((?:(?!<intent>).)*?)</intent>", Pattern.DOTALL);
        Matcher intentMatcher = intentPattern.matcher(content);
        while (intentMatcher.find()) {
            String intentText = intentMatcher.group(1).trim();
            if ("false".equalsIgnoreCase(intentText)) {
                log.warn("Failed to get intent");
            }
        }
        
        // Extract summary
        Pattern summaryPattern = Pattern.compile("<summary>((?:(?!</summary>).)*?)</summary>", Pattern.DOTALL);
        Matcher summaryMatcher = summaryPattern.matcher(content);
        String parseSummary = content;
        if (summaryMatcher.find()) {
            parseSummary = summaryMatcher.group(1).trim();
        }
        return parseSummary;
    }

    /**
     * 构建反例字符串
     */
    private String buildBadCaseString(List<EvaluatedCase> cases) {
        PromptTemplate badCaseTemplate = PromptTemplatesZh.FORMAT_BAD_CASE_TEMPLATE;
        StringBuilder sb = new StringBuilder();
        for (EvaluatedCase evaluatedCase : cases) {
            Map<String, Object> formatParams = new HashMap<>();
            formatParams.put("question", evaluatedCase.getInputs() != null ? evaluatedCase.getInputs().toString() : "");
            formatParams.put("label", evaluatedCase.getLabel() != null ? evaluatedCase.getLabel().toString() : "");
            formatParams.put("answer", evaluatedCase.getAnswer() != null ? evaluatedCase.getAnswer().toString() : "");
            formatParams.put("reason", evaluatedCase.getReason() != null ? evaluatedCase.getReason() : "");
            
            List<BaseMessage> messages = badCaseTemplate.format(formatParams).toMessages();
            for (BaseMessage msg : messages) {
                sb.append(msg.getContentAsString());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 验证输入
     */
    private void validateInput(String prompt, List<EvaluatedCase> cases) {
        if (prompt == null) {
            throw ErrorHelper.buildError(StatusCode.TOOLCHAIN_FEEDBACK_TEMPLATE_EXECUTION_ERROR,
                    "error_msg", "prompt cannot be None");
        }
        if (prompt.trim().isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.TOOLCHAIN_BAD_CASE_TEMPLATE_EXECUTION_ERROR,
                    "error_msg", "prompt cannot be empty");
        }
        if (cases == null || cases.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.TOOLCHAIN_BAD_CASE_TEMPLATE_EXECUTION_ERROR,
                    "error_msg", "The cases cannot be empty");
        }
        if (cases.size() > MAX_CASES_LIMIT) {
            throw ErrorHelper.buildError(StatusCode.TOOLCHAIN_BAD_CASE_TEMPLATE_EXECUTION_ERROR,
                    "error_msg", "The number of cases cannot exceed " + MAX_CASES_LIMIT);
        }
    }
}
