/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;

import java.util.Map;
import java.util.Objects;

/**
 * Mirrors Python's {@code OffloadAssistantMessage} in
 * {@code openjiuwen/core/context_engine/schema/messages.py}.
 */
public class OffloadAssistantMessage extends AssistantMessage implements OffloadMessage {
    @JsonProperty("offload_type")
    private String offloadType;

    @JsonProperty("offload_handle")
    private String offloadHandle;

    public OffloadAssistantMessage() {
    }

    public OffloadAssistantMessage(String content, String offloadHandle, String offloadType) {
        super(Objects.requireNonNull(content, "content"));
        this.offloadHandle = Objects.requireNonNull(offloadHandle, "offloadHandle");
        this.offloadType = Objects.requireNonNull(offloadType, "offloadType");
    }

    @Override
    public String getOffloadType() {
        return offloadType;
    }

    @Override
    public void setOffloadType(String offloadType) {
        this.offloadType = Objects.requireNonNull(offloadType, "offloadType");
    }

    @Override
    public String getOffloadHandle() {
        return offloadHandle;
    }

    @Override
    public void setOffloadHandle(String offloadHandle) {
        this.offloadHandle = Objects.requireNonNull(offloadHandle, "offloadHandle");
    }

    @Override
    public Map<String, Object> modelDump() {
        return OffloadMessages.appendOffloadFields(super.modelDump(), this);
    }

    @Override
    public Map<String, Object> model_dump() {
        return modelDump();
    }
}
