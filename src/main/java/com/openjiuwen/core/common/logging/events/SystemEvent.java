  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.common.logging.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/** System-level event. */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SystemEvent extends BaseLogEvent {
    private String systemVersion;
    private Map<String, Object> systemConfig;
    private Map<String, Object> resourceUsage;

    public SystemEvent() {
        super();
        setModuleType(ModuleType.SYSTEM);
    }

    @Override
    protected void addFieldsToMap(Map<String, Object> map) {
        putIfNotNull(map, "system_version", systemVersion);
        putIfNotNull(map, "system_config", systemConfig);
        putIfNotNull(map, "resource_usage", resourceUsage);
    }
}
