/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.multimodal;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.util.List;
import java.util.Map;

/**
 * Normalizer of multimodal agent input into ordered content blocks.
 *
 * <p>Covers the four accepted input shapes: plain strings, explicit content
 * block lists (internal contract or legacy OpenAI {@code text}/{@code
 * image_url} parts), structured messages carrying {@code content}, and
 * {@link BaseMessage} instances. Parsing failures fail explicitly with
 * {@code CONTENT_INVALID} locators; they never fall back to stringifying the
 * structure. Order and count are preserved: blocks are not sorted, deduped
 * or removed.</p>
 *
 * @since 0.1.17
 */
public interface MultimodalContentNormalizer {
    /**
     * Normalize one input into ordered content blocks.
     *
     * @param input string, content block list, structured message map or
     *              {@link BaseMessage}; may be {@code null}
     * @return immutable ordered blocks, never {@code null} or empty
     * @throws com.openjiuwen.core.common.exception.BaseError with
     *         {@code reason=CONTENT_INVALID} when the structure violates the
     *         contract; details carry the block id, field and input index
     */
    List<ContentBlock> normalize(Object input);

    /**
     * Decide whether a query value must take the multimodal content branch.
     *
     * <p>Arbitrary business maps are never auto-detected as content blocks:
     * a map qualifies only through an explicit {@code content} key, and a
     * list qualifies only when every item is a map carrying a string
     * {@code type}. Empty lists keep the legacy text branch so existing
     * business inputs behave unchanged.</p>
     *
     * @param value candidate query value; may be {@code null}
     * @return {@code true} when the value must be parsed as content blocks
     */
    static boolean isMultimodalContent(Object value) {
        if (value instanceof BaseMessage) {
            return true;
        }
        if (value instanceof Map<?, ?> map) {
            return map.containsKey("content");
        }
        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return false;
            }
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> block) || !(block.get("type") instanceof String)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
