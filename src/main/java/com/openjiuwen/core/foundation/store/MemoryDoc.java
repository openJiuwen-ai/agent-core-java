/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Memory document representing one persisted memory entry.
 *
 * <p>Mirrors Python's {@code MemoryDoc} in
 * {@code openjiuwen/core/foundation/store/base_memory_index.py}.</p>
 */
public class MemoryDoc {
    private String id = "";
    private String text = "";
    private String type = "";
    private ZonedDateTime timestamp = ZonedDateTime.now();
    private Map<String, Object> fields = new LinkedHashMap<>();

    public MemoryDoc() {
    }

    public MemoryDoc(String id, String text, String type, ZonedDateTime timestamp, Map<String, Object> fields) {
        this.id = id == null ? "" : id;
        this.text = text == null ? "" : text;
        this.type = type == null ? "" : type;
        this.timestamp = timestamp == null ? ZonedDateTime.now() : timestamp;
        this.fields = fields == null ? new LinkedHashMap<>() : new LinkedHashMap<>(fields);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id == null ? "" : id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type == null ? "" : type;
    }

    public ZonedDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(ZonedDateTime timestamp) {
        this.timestamp = timestamp == null ? ZonedDateTime.now() : timestamp;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public void setFields(Map<String, Object> fields) {
        this.fields = fields == null ? new LinkedHashMap<>() : new LinkedHashMap<>(fields);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("text", text);
        out.put("type", type);
        out.put("timestamp", timestamp);
        out.put("fields", new LinkedHashMap<>(fields));
        return out;
    }

    @SuppressWarnings("unchecked")
    public static MemoryDoc fromMap(Map<String, Object> values) {
        if (values == null) {
            return new MemoryDoc();
        }
        Object timestampValue = values.get("timestamp");
        ZonedDateTime parsedTimestamp = timestampValue instanceof ZonedDateTime zonedDateTime
                ? zonedDateTime
                : timestampValue instanceof String textValue ? ZonedDateTime.parse(textValue) : ZonedDateTime.now();
        Object fieldsValue = values.get("fields");
        Map<String, Object> parsedFields = fieldsValue instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map)
                : new LinkedHashMap<>();
        return new MemoryDoc(
                (String) values.get("id"),
                (String) values.get("text"),
                (String) values.get("type"),
                parsedTimestamp,
                parsedFields
        );
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MemoryDoc that)) {
            return false;
        }
        return Objects.equals(id, that.id)
                && Objects.equals(text, that.text)
                && Objects.equals(type, that.type)
                && Objects.equals(timestamp, that.timestamp)
                && Objects.equals(fields, that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, text, type, timestamp, fields);
    }
}
