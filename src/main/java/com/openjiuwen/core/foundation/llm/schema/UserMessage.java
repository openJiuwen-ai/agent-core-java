  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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

    /**
     * Creates a user message with the given content.
     *
     * @param content the message content
     */
    public UserMessage(String content) {
        super("user", content);
    }

    /**
     * Creates a user message with the given content and name.
     *
     * @param content the message content
     * @param name    the sender name
     */
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
