/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data structure for read file operation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadFileData {
    private String path;

    /**
     * File content.
     * <p>
     * When mode is "text", this is a {@code String}.
     * When mode is "bytes", this is a {@code byte[]} (raw binary content).
     * Mirrors Python's {@code Union[str, bytes]}.
     */
    private Object content;

    /** File read mode: "text" or "bytes". */
    private String mode;

    /**
     * Get content as String (for text mode).
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getContentAsString() {
        if (content instanceof String s) {
            return s;
        }
        return content != null ? content.toString() : null;
    }

    /**
     * Get content as byte[] (for bytes mode).
     * 
     * @return the result
     * @since 0.1.7
     */
    public byte[] getContentAsBytes() {
        if (content instanceof byte[] b) {
            return b;
        }
        if (content instanceof String s) {
            return s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        return new byte[0];
    }
}
