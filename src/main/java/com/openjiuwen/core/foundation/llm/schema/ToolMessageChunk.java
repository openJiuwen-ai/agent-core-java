/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Mirrors Python's {@code ToolMessageChunk} in
 * {@code openjiuwen/core/foundation/llm/schema/message_chunk.py}.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ToolMessageChunk extends ToolMessage {

    public ToolMessageChunk merge(Object other) {
        if (!(other instanceof ToolMessageChunk otherChunk)) {
            throw new IllegalArgumentException("Cannot merge ToolMessageChunk with " + other);
        }
        return ToolMessageChunk.builder()
                .role("tool")
                .content(getContentAsString() + otherChunk.getContentAsString())
                .toolCallId((otherChunk.getToolCallId() != null && !otherChunk.getToolCallId().isEmpty())
                        ? otherChunk.getToolCallId()
                        : getToolCallId())
                .build();
    }
}
