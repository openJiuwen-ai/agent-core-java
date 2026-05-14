/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory;

import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Memory information containing id, content, and type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemInfo {
    @Builder.Default
    private String memId = "";
    @Builder.Default
    private String content = "";
    @Builder.Default
    private MemoryType type = MemoryType.FRAGMENT_MEMORY;

    public static MemInfoBuilder builder() {
        return new MemInfoBuilder();
    }

    public String getMemId() {
        return memId;
    }

    public void setMemId(String memId) {
        this.memId = memId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public MemoryType getType() {
        return type;
    }

    public void setType(MemoryType type) {
        this.type = type;
    }

    public static final class MemInfoBuilder {
        private String memId = "";
        private String content = "";
        private MemoryType type = MemoryType.FRAGMENT_MEMORY;

        public MemInfoBuilder memId(String memId) { this.memId = memId; return this; }
        public MemInfoBuilder content(String content) { this.content = content; return this; }
        public MemInfoBuilder type(MemoryType type) { this.type = type; return this; }

        public MemInfo build() {
            return new MemInfo(memId, content, type);
        }
    }
}
