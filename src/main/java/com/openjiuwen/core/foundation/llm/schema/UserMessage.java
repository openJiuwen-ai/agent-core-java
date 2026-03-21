/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.llm.schema;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * User message in an LLM conversation.
 * <p>
 * Mirrors Python's {@code UserMessage} model.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserMessage extends BaseMessage {

    public UserMessage(String content) {
        super("user", content);
    }

    public UserMessage(String content, String name) {
        this(content);
        setName(name);
    }

    @Override
    public String getRole() {
        String r = super.getRole();
        return r != null ? r : "user";
    }
}
