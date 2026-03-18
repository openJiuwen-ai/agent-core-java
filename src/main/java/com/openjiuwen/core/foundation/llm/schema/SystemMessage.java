/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.llm.schema;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * System message in an LLM conversation.
 * <p>
 * Mirrors Python's {@code SystemMessage} model.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SystemMessage extends BaseMessage {

    public SystemMessage(String content) {
        super("system", content);
    }

    @Override
    public String getRole() {
        String r = super.getRole();
        return r != null ? r : "system";
    }
}
