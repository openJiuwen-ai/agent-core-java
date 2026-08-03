/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;

import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Mirrors Python's {@code MemInfo} in
 * {@code openjiuwen/core/memory/long_term_memory.py}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MemInfo {
    @JsonProperty("mem_id")
    private String memId = "";

    @JsonProperty("content")
    private String content = "";

    @JsonProperty("type")
    private MemoryType type = MemoryType.USER_PROFILE;

    @JsonProperty("timestamp")
    private ZonedDateTime timestamp;

    public MemInfo() {
    }

    public MemInfo(String memId, String content, MemoryType type, ZonedDateTime timestamp) {
        this.memId = memId == null ? "" : memId;
        this.content = content == null ? "" : content;
        this.type = type == null ? MemoryType.USER_PROFILE : type;
        this.timestamp = timestamp;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getMemId() {
        return memId;
    }

    public void setMemId(String memId) {
        this.memId = memId == null ? "" : memId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content == null ? "" : content;
    }

    public MemoryType getType() {
        return type;
    }

    public void setType(MemoryType type) {
        this.type = type == null ? MemoryType.USER_PROFILE : type;
    }

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(ZonedDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MemInfo that)) {
            return false;
        }
        return Objects.equals(memId, that.memId)
                && Objects.equals(content, that.content)
                && type == that.type
                && Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memId, content, type, timestamp);
    }

    public static final class Builder {
        private String memId = "";
        private String content = "";
        private MemoryType type = MemoryType.USER_PROFILE;
        private ZonedDateTime timestamp;

        private Builder() {
        }

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

        public Builder timestamp(ZonedDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public MemInfo build() {
            return new MemInfo(memId, content, type, timestamp);
        }
    }
}
