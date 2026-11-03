/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Metadata for a single session entry stored in sessions metadata.
 *
 * <p>Mirrors Python's {@code SessionMeta} in
 * {@code openjiuwen/core/session/session_controller/schema.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionMeta {

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("created_at")
    private double createdAt;

    @JsonProperty("updated_at")
    private double updatedAt;

    @JsonProperty("version")
    private int version;

    @JsonProperty("is_active")
    private boolean active;

    @JsonProperty("data_container_type")
    private String dataContainerType = DataContainerFactory.DEFAULT_DATA_CONTAINER_TYPE;

    public SessionMeta() {
    }

    public SessionMeta(String sessionId, double createdAt, double updatedAt, int version, boolean active) {
        this(sessionId, createdAt, updatedAt, version, active, DataContainerFactory.DEFAULT_DATA_CONTAINER_TYPE);
    }

    public SessionMeta(String sessionId,
                       double createdAt,
                       double updatedAt,
                       int version,
                       boolean active,
                       String dataContainerType) {
        this.sessionId = sessionId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
        this.active = active;
        setDataContainerType(dataContainerType);
    }

    public static SessionMeta createNew(String sessionId) {
        return createNew(sessionId, 1, DataContainerFactory.DEFAULT_DATA_CONTAINER_TYPE);
    }

    public static SessionMeta createNew(String sessionId, int version) {
        return createNew(sessionId, version, DataContainerFactory.DEFAULT_DATA_CONTAINER_TYPE);
    }

    public static SessionMeta createNew(String sessionId, int version, String dataContainerType) {
        double now = utcSeconds();
        return new SessionMeta(sessionId, now, now, version, true, dataContainerType);
    }

    public static SessionMeta create_new(String sessionId, int version, String dataContainerType) {
        return createNew(sessionId, version, dataContainerType);
    }

    public void updateTimestamp() {
        this.updatedAt = utcSeconds();
    }

    public void update_timestamp() {
        updateTimestamp();
    }

    public void incrementVersion() {
        this.version += 1;
    }

    public void increment_version() {
        incrementVersion();
    }

    public Map<String, Object> toMap() {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("session_id", sessionId);
        result.put("created_at", createdAt);
        result.put("updated_at", updatedAt);
        result.put("version", version);
        result.put("is_active", active);
        result.put("data_container_type", dataContainerType);
        return result;
    }

    public Map<String, Object> to_dict() {
        return toMap();
    }

    public static SessionMeta fromMap(Map<String, Object> data) {
        if (!data.containsKey("data_container_type")) {
            try {
                data.put("data_container_type", DataContainerFactory.DEFAULT_DATA_CONTAINER_TYPE);
            } catch (RuntimeException ignored) {
                // Immutable Java maps cannot mirror Python's in-place default insertion.
            }
        }
        return new SessionMeta(
                stringValue(data.get("session_id")),
                doubleValue(data.get("created_at")),
                doubleValue(data.get("updated_at")),
                intValue(data.get("version")),
                booleanValue(data.get("is_active")),
                stringValue(data.get("data_container_type"))
        );
    }

    public static SessionMeta from_dict(Map<String, Object> data) {
        return fromMap(data);
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public double getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(double createdAt) {
        this.createdAt = createdAt;
    }

    public double getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(double updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getDataContainerType() {
        return dataContainerType;
    }

    public void setDataContainerType(String dataContainerType) {
        this.dataContainerType = dataContainerType == null
                ? DataContainerFactory.DEFAULT_DATA_CONTAINER_TYPE
                : dataContainerType;
    }

    private static double utcSeconds() {
        return Instant.now().toEpochMilli() / 1000.0D;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return value == null ? 0.0D : Double.parseDouble(String.valueOf(value));
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? 0 : Integer.parseInt(String.valueOf(value));
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }
}
