/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.workflow.component.llm;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Internal output cache used during questioner processing.
 */
@Data
public class OutputCache {
    private Object userResponse = "";
    private String question = "";
    private Map<String, Object> keyFields = new LinkedHashMap<>();
}
