/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Fragment memory unit.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FragmentMemoryUnit extends BaseMemoryUnit {
    private String fragmentType;
    private String content;
    private String messageMemId;
    private String timestamp;

    public static FragmentMemoryUnitBuilder builder() {
        return new FragmentMemoryUnitBuilder();
    }

    public String getFragmentType() {
        return fragmentType;
    }

    public void setFragmentType(String fragmentType) {
        this.fragmentType = fragmentType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMessageMemId() {
        return messageMemId;
    }

    public void setMessageMemId(String messageMemId) {
        this.messageMemId = messageMemId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public MemoryType getMemType() {
        return MemoryType.FRAGMENT_MEMORY;
    }

    public static final class FragmentMemoryUnitBuilder {
        private String fragmentType;
        private String content;
        private String messageMemId;
        private String timestamp;

        public FragmentMemoryUnitBuilder fragmentType(String fragmentType) {
            this.fragmentType = fragmentType;
            return this;
        }

        public FragmentMemoryUnitBuilder content(String content) {
            this.content = content;
            return this;
        }

        public FragmentMemoryUnitBuilder messageMemId(String messageMemId) {
            this.messageMemId = messageMemId;
            return this;
        }

        public FragmentMemoryUnitBuilder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public FragmentMemoryUnit build() {
            FragmentMemoryUnit unit = new FragmentMemoryUnit();
            unit.setFragmentType(fragmentType);
            unit.setContent(content);
            unit.setMessageMemId(messageMemId);
            unit.setTimestamp(timestamp);
            return unit;
        }
    }
}
