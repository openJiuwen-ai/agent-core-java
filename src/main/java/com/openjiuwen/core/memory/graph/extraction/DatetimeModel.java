/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Representing datetime (unused).
 * 
 * @since 0.1.7
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DatetimeModel extends MultilingualBaseModel {
    @SchemaDescription("{{[year]}}")
    private int year;
    @SchemaDescription("{{[month]}}")
    private int month;
    @SchemaDescription("{{[day]}}")
    private int day;
    @SchemaDescription("{{[hour]}}")
    private int hour;
    @SchemaDescription("{{[minute]}}")
    private int minute;
    @SchemaDescription("{{[second]}}")
    private int second;

    @Override
    public Map<String, Object> responseFormat() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("year", Map.of("type", "integer"));
        properties.put("month", Map.of("type", "integer"));
        properties.put("day", Map.of("type", "integer"));
        properties.put("hour", Map.of("type", "integer"));
        properties.put("minute", Map.of("type", "integer"));
        properties.put("second", Map.of("type", "integer"));
        schema.put("properties", properties);
        schema.put("required", java.util.List.of("year", "month", "day", "hour", "minute", "second"));
        return schema;
    }
}
