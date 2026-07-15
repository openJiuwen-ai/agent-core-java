/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Public class PossibleTimezone used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class PossibleTimezone extends MultilingualBaseModel {
    @SchemaDescription("{{[tz_name]}}")
    private String name;
    @SchemaDescription("{{[tz_offset]}}")
    private String offsetFromUtc;
    @SchemaDescription("{{[tz_reason]}}")
    private String reasoning;
}
