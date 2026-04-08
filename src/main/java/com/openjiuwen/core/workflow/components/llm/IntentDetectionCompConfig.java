// coding: utf-8
/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

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

    /** Snake_case aliases for test compatibility (mirrors Python attribute names). */
    public void setModel_config(ModelRequestConfig modelConfig) { setModelConfig(modelConfig); }
    public void setModel_client_config(ModelClientConfig modelClientConfig) { setModelClientConfig(modelClientConfig); }
    public void setCategory_name_list(List<String> categoryNameList) { setCategoryNameList(categoryNameList); }
}
