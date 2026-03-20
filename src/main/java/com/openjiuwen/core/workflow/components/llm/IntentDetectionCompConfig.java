// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.workflow.components.llm;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.List;

/**
 * Alias/extension of {@link com.openjiuwen.core.workflow.component.llm.IntentDetectionCompConfig}
 * with positional constructor for test compatibility.
 */
public class IntentDetectionCompConfig
        extends com.openjiuwen.core.workflow.component.llm.IntentDetectionCompConfig {

    /**
     * Positional constructor matching Python test usage:
     * IntentDetectionCompConfig(modelCfg, modelClientCfg, userPrompt, categoryNameList)
     */
    public IntentDetectionCompConfig(
            ModelRequestConfig modelConfig,
            ModelClientConfig modelClientConfig,
            String userPrompt,
            List<String> categoryNameList) {
        super();
        setModelConfig(modelConfig);
        setModelClientConfig(modelClientConfig);
        setUserPrompt(userPrompt != null ? userPrompt : "");
        setCategoryNameList(categoryNameList != null ? categoryNameList : List.of());
    }

    public IntentDetectionCompConfig() {
        super();
    }
}
