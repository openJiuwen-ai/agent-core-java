/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.workflow.component.ComponentConfig;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the Questioner workflow component.
 * <p>
 * Mirrors Python's {@code QuestionerConfig}.
 * 
 * @since 0.1.7
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QuestionerConfig extends ComponentConfig {
    private String modelId;
    private ModelClientConfig modelClientConfig;
    private ModelRequestConfig modelConfig;

    /**
     * ResponseType.REPLY_DIRECTLY.getValue.
     * 
     * @since 0.1.7
     */
    private String responseType = ResponseType.REPLY_DIRECTLY.getValue();
    private String questionContent = "";
    private boolean extractFieldsFromResponse = true;

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<FieldInfo> fieldNames = new ArrayList<>();
    private int maxResponse = 3;
    private boolean withChatHistory = false;
    private int chatHistoryMaxRounds = 5;
    private String extraPromptForFieldsExtraction = "";
    private String exampleContent = "";
    private String acceptLanguage = "zh";
}
