/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.update;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a memory with its action status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryActionItem {
    private String id;
    private String content;
    private MemoryStatus status;

    public static MemoryActionItemBuilder builder() {
        return new MemoryActionItemBuilder();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public MemoryStatus getStatus() {
        return status;
    }

    public void setStatus(MemoryStatus status) {
        this.status = status;
    }

    public static final class MemoryActionItemBuilder {
        private String id;
        private String content;
        private MemoryStatus status;

        public MemoryActionItemBuilder id(String id) { this.id = id; return this; }
        public MemoryActionItemBuilder content(String content) { this.content = content; return this; }
        public MemoryActionItemBuilder status(MemoryStatus status) { this.status = status; return this; }

        public MemoryActionItem build() {
            MemoryActionItem item = new MemoryActionItem();
            item.setId(id);
            item.setContent(content);
            item.setStatus(status);
            return item;
        }
    }
}
