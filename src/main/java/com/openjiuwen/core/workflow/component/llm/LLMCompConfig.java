/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow.component.llm;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.workflow.component.ComponentConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for LLM workflow component.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.workflow.components.llm.llm_comp.LLMCompConfig}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LLMCompConfig extends ComponentConfig {

    private String modelId;
    private ModelClientConfig modelClientConfig;
    private ModelRequestConfig modelConfig;
    private List<Map<String, Object>> templateContent = new ArrayList<>();
    private SystemMessage systemPromptTemplate;
    private UserMessage userPromptTemplate;
    private Map<String, Object> responseFormat = new LinkedHashMap<>();
    private Map<String, Object> outputConfig = new LinkedHashMap<>();
    private boolean enableHistory = false;
    private boolean cacheStream = false;
}
