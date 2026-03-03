/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.llm.schema;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * Base streaming message chunk for accumulation via {@link #merge(BaseMessageChunk)}.
 * <p>
 * Mirrors Python's {@code BaseMessageChunk} model.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BaseMessageChunk extends BaseMessage {

    public BaseMessageChunk(String role, Object content, String name) {
        super(role, content, name);
    }

    /**
     * Merge another chunk into this one (content concatenation).
     *
     * @param other the chunk to merge
     * @return a new merged chunk
     */
    public BaseMessageChunk merge(BaseMessageChunk other) {
        if (other == null) {
            return this;
        }
        Object combinedContent = mergeContent(this.getContent(), other.getContent());
        return new BaseMessageChunk(
                this.getRole(),
                combinedContent,
                this.getName() != null ? this.getName() : other.getName()
        );
    }

    /**
     * Merge content fields based on type compatibility.
     */
    @SuppressWarnings("unchecked")
    protected static Object mergeContent(Object left, Object right) {
        if (left instanceof String ls && right instanceof String rs) {
            return ls + rs;
        }
        if (left instanceof List<?> ll && right instanceof List<?> rl) {
            var merged = new java.util.ArrayList<Object>(ll);
            merged.addAll(rl);
            return merged;
        }
        return right;
    }
}
