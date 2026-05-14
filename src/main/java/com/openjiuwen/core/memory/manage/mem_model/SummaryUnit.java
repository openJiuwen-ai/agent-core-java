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
 * Summary memory unit.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SummaryUnit extends BaseMemoryUnit {
    private String summary;
    private String messageMemId;
    private String timestamp;

    public static SummaryUnitBuilder builder() {
        return new SummaryUnitBuilder();
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
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
        return MemoryType.SUMMARY;
    }

    public static final class SummaryUnitBuilder {
        private String summary;
        private String messageMemId;
        private String timestamp;

        public SummaryUnitBuilder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public SummaryUnitBuilder messageMemId(String messageMemId) {
            this.messageMemId = messageMemId;
            return this;
        }

        public SummaryUnitBuilder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public SummaryUnit build() {
            SummaryUnit unit = new SummaryUnit();
            unit.setSummary(summary);
            unit.setMessageMemId(messageMemId);
            unit.setTimestamp(timestamp);
            return unit;
        }
    }
}
