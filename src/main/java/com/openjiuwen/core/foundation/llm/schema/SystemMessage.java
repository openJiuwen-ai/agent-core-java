/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Mirrors Python's {@code SystemMessage} in
 * {@code openjiuwen/core/foundation/llm/schema/message.py}.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SystemMessage extends BaseMessage {

    public SystemMessage(String content) {
        super("system", content);
    }

    public SystemMessage(String content, String name) {
        this(content);
        setName(name);
    }

    @Override
    public String getRole() {
        String value = super.getRole();
        return value != null ? value : "system";
    }
}
