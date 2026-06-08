/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import java.util.Objects;

/**
 * Summary memory unit with fixed summary memory type.
 *
 * <p>Mirrors Python's {@code SummaryUnit} in
 * {@code openjiuwen/core/memory/manage/mem_model/memory_unit.py}.</p>
 */
public class SummaryUnit extends BaseMemoryUnit {
    private String summary;
    private String messageMemId;
    private String timestamp = "";

    public SummaryUnit() {
        super(MemoryType.SUMMARY, "");
    }

    public SummaryUnit(String memId, String summary, String messageMemId, String timestamp) {
        super(MemoryType.SUMMARY, memId);
        this.summary = summary;
        this.messageMemId = messageMemId;
        this.timestamp = timestamp == null ? "" : timestamp;
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
        this.timestamp = timestamp == null ? "" : timestamp;
    }

    @Override
    public void setMemType(MemoryType memType) {
        super.setMemType(MemoryType.SUMMARY);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SummaryUnit that)) {
            return false;
        }
        return super.equals(that)
                && Objects.equals(summary, that.summary)
                && Objects.equals(messageMemId, that.messageMemId)
                && Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), summary, messageMemId, timestamp);
    }
}
