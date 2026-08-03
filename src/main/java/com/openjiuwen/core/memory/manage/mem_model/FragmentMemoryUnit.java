/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import java.util.Objects;

/**
 * Fragment memory unit.
 *
 * <p>Mirrors Python's {@code FragmentMemoryUnit} in
 * {@code openjiuwen/core/memory/manage/mem_model/memory_unit.py}.</p>
 */
public class FragmentMemoryUnit extends BaseMemoryUnit {
    private String content;
    private String messageMemId;
    private String timestamp = "";
    private OperationType operationType;

    public FragmentMemoryUnit() {
    }

    public FragmentMemoryUnit(
            MemoryType memType,
            String memId,
            String content,
            String messageMemId,
            String timestamp,
            OperationType operationType
    ) {
        super(memType, memId);
        this.content = content;
        this.messageMemId = messageMemId;
        this.timestamp = timestamp == null ? "" : timestamp;
        this.operationType = operationType;
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
        this.timestamp = timestamp == null ? "" : timestamp;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public void setOperationType(OperationType operationType) {
        this.operationType = operationType;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FragmentMemoryUnit that)) {
            return false;
        }
        return super.equals(that)
                && Objects.equals(content, that.content)
                && Objects.equals(messageMemId, that.messageMemId)
                && Objects.equals(timestamp, that.timestamp)
                && operationType == that.operationType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), content, messageMemId, timestamp, operationType);
    }
}
