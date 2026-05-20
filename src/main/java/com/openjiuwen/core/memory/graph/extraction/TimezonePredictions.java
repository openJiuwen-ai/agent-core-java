/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import lombok.Data;

import java.util.List;

/**
 * Public class TimezonePredictions used by the Java parity implementation.
 *
 * @since 1.0
 */
@Data
public class TimezonePredictions extends MultilingualBaseModel {
    @SchemaDescription("{{[tz_list]}}")
    private List<PossibleTimezone> extractedRelations;
}
