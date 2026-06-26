/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Input schema for memory write.
 *
 * <p>Mirrors Python's {@code MemoryWriteInput} in
 * {@code openjiuwen/core/workflow/components/resource/memory_write_comp.py}.</p>
 */
public class MemoryWriteInput {

    @JsonProperty("messages")
    private List<BaseMessage> messages = new ArrayList<>();

    @JsonProperty("timestamp")
    private ZonedDateTime timestamp;

    private Map<String, Object> extraFields = new LinkedHashMap<>();

    public MemoryWriteInput() {
    }

    public MemoryWriteInput(List<BaseMessage> messages, ZonedDateTime timestamp, Map<String, Object> extraFields) {
        setMessages(messages);
        this.timestamp = timestamp;
        setExtraFields(extraFields);
    }

    public List<BaseMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    public void setMessages(List<BaseMessage> messages) {
        this.messages = messages == null ? new ArrayList<>() : new ArrayList<>(messages);
    }

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(ZonedDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getExtraFields() {
        return new LinkedHashMap<>(extraFields);
    }

    public void setExtraFields(Map<String, Object> extraFields) {
        this.extraFields = extraFields == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extraFields);
    }
}
