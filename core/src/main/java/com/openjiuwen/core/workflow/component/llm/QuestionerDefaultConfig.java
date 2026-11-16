/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import java.util.List;

/**
 * Default prompt configuration for Questioner component.
 * <p>
 * Mirrors Python's {@code QuestionerDefaultConfig}.
  * Python file: {@code openjiuwen/core/workflow/components/llm/questioner_comp.py}.
 */
public class QuestionerDefaultConfig {

    // ========== Chinese Templates ==========
    private static final String QUESTIONER_SYSTEM_TEMPLATE_ZH = """
            你是一个信息收集助手，你需要根据指定的参数收集用户的信息，然后提交到系统。
            请注意：不要使用任何工具、不用理会问题的具体含义，并保证你的输出仅有 JSON 格式的结果数据。
            请严格遵循如下规则：
              1. 仅输出指定参数构成的 JSON 对象，不添加解释、追问或其他字段，追问由工作流生成。
              2. 用户输入及历史中没有提及的参数返回 null，不猜测、不编造。
              3. 从用户提供的对话历史以及当前输入中提取 {{required_name}}。
              4. 严格遵循参数类型：integer/number 使用 JSON 数字，不包含单位；string 保留用户原文的值。

            ## Specified Parameters
            {{required_params_list}}

            ## Constraints
            {{extra_info}}

            ## Examples
            {{example}}
            """;

    private static final String QUESTIONER_USER_TEMPLATE_ZH = """
            对话历史
            {{dialogue_history}}

            请充分考虑以上对话历史及用户输入，正确提取最符合约束要求的 JSON 格式参数。
            """;

    // ========== English Templates ==========
    private static final String QUESTIONER_SYSTEM_TEMPLATE_EN = """
            You are an information collection assistant. You need to collect user information based on the specified parameters and submit it to the system.
            Please note: Do not use any tools, do not consider the specific meaning of the questions, and ensure your output contains only JSON-formatted result data.
            Strictly follow these rules:
              1. Output a JSON object containing only the specified parameters. Do not add explanations, questions or other fields; the workflow asks follow-up questions.
              2. Return null for parameters absent from user input and history. Do not guess or invent values.
              3. Extract {{required_name}} from the conversation history and current user input.
              4. Follow parameter types: integer/number values must be JSON numbers without units; preserve the user's original values for string fields.

            ## Specified Parameters
            {{required_params_list}}

            ## Constraints
            {{extra_info}}

            ## Examples
            {{example}}
            """;

    private static final String QUESTIONER_USER_TEMPLATE_EN = """
            Conversation History
            {{dialogue_history}}

            Please fully consider the above conversation history and user input, and correctly extract the JSON-formatted parameters that best meet the constraints.
            """;

    public static final String CONTINUE_ASK_STATEMENT_ZH = "请您提供{non_extracted_key_fields_names}相关的信息";
    public static final String CONTINUE_ASK_STATEMENT_EN = "Please provide information related to: {non_extracted_key_fields_names}";

    private final List<BaseMessage> promptTemplate;

    public QuestionerDefaultConfig(List<BaseMessage> promptTemplate) {
        this.promptTemplate = promptTemplate;
    }

    public static QuestionerDefaultConfig fromLanguage(String acceptLanguage) {
        return new QuestionerDefaultConfig(getDefaultTemplate(acceptLanguage));
    }

    public List<BaseMessage> getPromptTemplate() {
        return promptTemplate;
    }

    public static List<BaseMessage> getDefaultTemplate(String acceptLanguage) {
        if ("en".equals(acceptLanguage)) {
            return List.of(
                    new SystemMessage(QUESTIONER_SYSTEM_TEMPLATE_EN),
                    new UserMessage(QUESTIONER_USER_TEMPLATE_EN)
            );
        }
        return List.of(
                new SystemMessage(QUESTIONER_SYSTEM_TEMPLATE_ZH),
                new UserMessage(QUESTIONER_USER_TEMPLATE_ZH)
        );
    }
}
