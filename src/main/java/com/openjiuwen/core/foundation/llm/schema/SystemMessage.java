/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
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

    /**
     * Creates a system message with the given content.
     *
     * @param content the message content
     */
    public SystemMessage(String content) {
        super("system", content);
    }

    /**
     * Creates a system message with the given content and name.
     *
     * @param content the message content
     * @param name    the sender name
     */
    public SystemMessage(String content, String name) {
        this(content);
        setName(name);
    }

    @Override
    public String getRole() {
        String r = super.getRole();
        return r != null ? r : "system";
    }
}
