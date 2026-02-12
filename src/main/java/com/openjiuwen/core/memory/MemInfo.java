/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */

package com.openjiuwen.core.memory;

import com.openjiuwen.core.memory.manage.memmodel.MemoryType;

/**
 * Memory information record.
 * <p>
 * Corresponds to Python: long_term_memory.py - MemInfo
 */
public record MemInfo(
        String memId,
        String content,
        MemoryType type
) {
    /**
     * Create a MemInfo with default values.
     */
    public MemInfo() {
        this("", "", MemoryType.USER_PROFILE);
    }

    /**
     * Create a MemInfo with specified values.
     *
     * @param memId   Memory ID
     * @param content Memory content
     * @param type    Memory type
     */
    public MemInfo {
        if (memId == null) {
            memId = "";
        }
        if (content == null) {
            content = "";
        }
        if (type == null) {
            type = MemoryType.USER_PROFILE;
        }
    }

    /**
     * Builder for MemInfo.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String memId = "";
        private String content = "";
        private MemoryType type = MemoryType.USER_PROFILE;

        public Builder memId(String memId) {
            this.memId = memId;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder type(MemoryType type) {
            this.type = type;
            return this;
        }

        public MemInfo build() {
            return new MemInfo(memId, content, type);
        }
    }
}

