/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Mirrors Python's {@code BaseMessageChunk} in
 * {@code openjiuwen/core/foundation/llm/schema/message_chunk.py}.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BaseMessageChunk extends BaseMessage {

    public BaseMessageChunk(String role, Object content, String name) {
        super(role, content, name, null);
    }

    public BaseMessageChunk merge(Object other) {
        if (!(other instanceof BaseMessageChunk otherChunk)) {
            throw new IllegalArgumentException("Cannot merge BaseMessageChunk with " + other);
        }
        Object mergedContent = MessageChunkMerge.mergeParserContent(getContent(), otherChunk.getContent());
        return new BaseMessageChunk(
                getRole(),
                mergedContent,
                getName() != null ? getName() : otherChunk.getName()
        );
    }
}
