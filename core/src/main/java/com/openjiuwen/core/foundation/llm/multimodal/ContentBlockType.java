/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.multimodal;

import java.util.Optional;

/**
 * Recognized {@code type} values of the multimodal content block contract.
 *
 * <p>{@link #VIDEO} only reserves the enumeration slot in this release; the
 * agent entry rejects video blocks explicitly instead of processing them.
 * {@link #TOOL_RESULT} belongs to the tool-result channel contract and is
 * rejected at the agent entry as well.</p>
 *
 * @since 0.1.17
 */
public enum ContentBlockType {
    /** Plain text block; the {@code text} field is required. */
    TEXT("text"),

    /** Image block; the {@code source} field is required. */
    IMAGE("image"),

    /** Reserved video slot; not processed in this release. */
    VIDEO("video"),

    /** Attachment reference block; the {@code source} field is required. */
    FILE("file"),

    /** Tool result block; only valid on the tool-result channel. */
    TOOL_RESULT("tool_result");

    private final String wireValue;

    ContentBlockType(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Return the wire representation used in content block maps.
     *
     * @return lower-case type name, never {@code null}
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * Resolve a wire type name to an enumeration constant.
     *
     * @param value wire type name; may be {@code null}
     * @return matched constant, or empty when the value is not recognized
     */
    public static Optional<ContentBlockType> fromWire(String value) {
        for (ContentBlockType type : values()) {
            if (type.wireValue.equals(value)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
