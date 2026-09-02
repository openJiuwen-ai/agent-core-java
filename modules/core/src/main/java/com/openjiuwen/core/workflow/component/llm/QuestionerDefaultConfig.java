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
 * 
 * @since 0.1.7
 */
public class QuestionerDefaultConfig {
    // ========== Chinese Templates ==========
    private static final String QUESTIONER_SYSTEM_TEMPLATE_ZH = """
            你是一个信息收集助手，你需要根据指定的参数收集用户的信息，然后提交到系统。
            请注意：不要使用任何工具、不用理会问题的具体含义，并保证你的输出仅有 JSON 格式的结果数据。
            请严格遵循如下规则：
              1. 让我们一步一步思考。
              2. 用户输入中没有提及的参数提取为 null，并直接向询问用户没有明确提供的参数。
              3. 通过用户提供的对话历史以及当前输入中提取 {{required_name}}，不要追问任何其他信息。
              4. 参数收集完成后，将收集到的信息通过 JSON 的方式展示给用户。

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
            You are an information collection assistant. You need to collect user information based on the specified
            parameters and submit it to the system.
            Please note: Do not use any tools, do not consider the specific meaning of the questions, and ensure your
            output contains only JSON-formatted result data.
            Strictly follow these rules:
              1. Let's think step by step.
              2. Parameters not mentioned in user input should be extracted as null, and directly ask the user for
                 parameters not explicitly provided.
              3. Extract {{required_name}} from the conversation history and current user input. Do not ask for any
                 other information.
              4. After parameter collection is complete, display the collected information in JSON format.

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

            Please fully consider the above conversation history and user input, and correctly extract the
            JSON-formatted parameters that best meet the constraints.
            """;

    /**
     * CONTINUE_ASK_STATEMENT_ZH.
     * 
     * @since 0.1.7
     */
    public static final String CONTINUE_ASK_STATEMENT_ZH = "请您提供{non_extracted_key_fields_names}相关的信息";

    /**
     * CONTINUE_ASK_STATEMENT_EN.
     * 
     * @since 0.1.7
     */
    public static final String CONTINUE_ASK_STATEMENT_EN =
        "Please provide information related to: " + "{non_extracted_key_fields_names}";

    private final List<BaseMessage> promptTemplate;

    /**
     * QuestionerDefaultConfig.
     * 
     * @param promptTemplate promptTemplate
     * @since 0.1.7
     */
    public QuestionerDefaultConfig(List<BaseMessage> promptTemplate) {
        this.promptTemplate = promptTemplate;
    }

    /**
     * fromLanguage.
     * 
     * @param acceptLanguage acceptLanguage
     * @return the result
     * @since 0.1.7
     */
    public static QuestionerDefaultConfig fromLanguage(String acceptLanguage) {
        return new QuestionerDefaultConfig(getDefaultTemplate(acceptLanguage));
    }

    /**
     * getPromptTemplate.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<BaseMessage> getPromptTemplate() {
        return promptTemplate;
    }

    /**
     * getDefaultTemplate.
     * 
     * @param acceptLanguage acceptLanguage
     * @return the result
     * @since 0.1.7
     */
    public static List<BaseMessage> getDefaultTemplate(String acceptLanguage) {
        if ("en".equals(acceptLanguage)) {
            return List.of(new SystemMessage(QUESTIONER_SYSTEM_TEMPLATE_EN),
                    new UserMessage(QUESTIONER_USER_TEMPLATE_EN));
        }
        return List.of(new SystemMessage(QUESTIONER_SYSTEM_TEMPLATE_ZH), new UserMessage(QUESTIONER_USER_TEMPLATE_ZH));
    }
}
