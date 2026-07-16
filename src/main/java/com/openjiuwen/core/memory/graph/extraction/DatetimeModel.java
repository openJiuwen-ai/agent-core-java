/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import lombok.Data;
import lombok.EqualsAndHashCode;

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
}
