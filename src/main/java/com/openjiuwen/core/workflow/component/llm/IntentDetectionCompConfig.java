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
 * Configuration for IntentDetection workflow component.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.llm.intent_detection_comp.IntentDetectionCompConfig}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class IntentDetectionCompConfig extends ComponentConfig {

    private String modelId;
    private ModelClientConfig modelClientConfig;
    private ModelRequestConfig modelConfig;
    private List<String> categoryNameList = new ArrayList<>();
    private String userPrompt = "";
    private List<String> exampleContent = new ArrayList<>();
    private boolean enableHistory = false;
    private int chatHistoryMaxTurn = 3;
    private String acceptLanguage = "zh";
}
