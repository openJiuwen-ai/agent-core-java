/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Data store related event.
 * 
 * @since 0.1.7
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class StoreEvent extends BaseLogEvent {
    private String tableName;
    private Integer dataNum;

    /**
     * StoreEvent.
     * 
     * @since 0.1.7
     */
    public StoreEvent() {
        super();
        setModuleType(ModuleType.STORE);
    }

    /**
     * addFieldsToMap.
     * 
     * @param map map
     * @since 0.1.7
     */
    @Override
    protected void addFieldsToMap(Map<String, Object> map) {
        putIfNotNull(map, "table_name", tableName);
        putIfNotNull(map, "data_num", dataNum);
    }
}
