/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.components.llm;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.List;

/**
 * Alias/extension of {@link com.openjiuwen.core.workflow.component.llm.IntentDetectionCompConfig}
 * with positional constructor for test compatibility.
 * 
 * @since 0.1.7
 */
public class IntentDetectionCompConfig extends com.openjiuwen.core.workflow.component.llm.IntentDetectionCompConfig {
    /**
     * IntentDetectionCompConfig.
     * 
     * @param modelConfig modelConfig
     * @param modelClientConfig modelClientConfig
     * @param userPrompt userPrompt
     * @param categoryNameList categoryNameList
     * @since 0.1.7
     */
    public IntentDetectionCompConfig(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig,
            String userPrompt, List<String> categoryNameList) {
        super();
        setModelConfig(modelConfig);
        setModelClientConfig(modelClientConfig);
        setUserPrompt(userPrompt != null ? userPrompt : "");
        setCategoryNameList(categoryNameList != null ? categoryNameList : List.of());
    }

    /**
     * IntentDetectionCompConfig.
     * 
     * @since 0.1.7
     */
    public IntentDetectionCompConfig() {
        super();
    }

    /**
     * Snake_case aliases for test compatibility (mirrors Python attribute names).
     * 
     * @param modelConfig modelConfig
     * @since 0.1.7
     */
    public void setModel_config(ModelRequestConfig modelConfig) {
        setModelConfig(modelConfig);
    }

    /**
     * setModel_client_config.
     * 
     * @param modelClientConfig modelClientConfig
     * @since 0.1.7
     */
    public void setModel_client_config(ModelClientConfig modelClientConfig) {
        setModelClientConfig(modelClientConfig);
    }

    /**
     * setCategory_name_list.
     * 
     * @param categoryNameList categoryNameList
     * @since 0.1.7
     */
    public void setCategory_name_list(List<String> categoryNameList) {
        setCategoryNameList(categoryNameList);
    }
}
