/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/** Performance metric related event. */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class PerformanceEvent extends BaseLogEvent {
    private String metricName;
    private Double metricValue;
    private String metricUnit;
    private String resourceType;
    private String operation;

    public PerformanceEvent() {
        super();
        setModuleType(ModuleType.SYSTEM);
    }

    @Override
    protected void addFieldsToMap(Map<String, Object> map) {
        putIfNotNull(map, "metric_name", metricName);
        putIfNotNull(map, "metric_value", metricValue);
        putIfNotNull(map, "metric_unit", metricUnit);
        putIfNotNull(map, "resource_type", resourceType);
        putIfNotNull(map, "operation", operation);
    }
}
