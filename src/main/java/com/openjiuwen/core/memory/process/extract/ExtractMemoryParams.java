/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.memory.process.extract;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Parameters for memory extraction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractMemoryParams {
    private String userId;
    private String scopeId;
    private List<BaseMessage> messages;
    private List<BaseMessage> historyMessages;
    /**
     * Tuple: (modelName, modelClient)
     */
    private Map.Entry<String, Model> baseChatModel;
}
