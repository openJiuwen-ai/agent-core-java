/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.logging.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/** Data store related event. */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class StoreEvent extends BaseLogEvent {
    private String tableName;
    private Integer dataNum;

    public StoreEvent() {
        super();
        setModuleType(ModuleType.STORE);
    }

    @Override
    protected void addFieldsToMap(Map<String, Object> map) {
        putIfNotNull(map, "table_name", tableName);
        putIfNotNull(map, "data_num", dataNum);
    }
}
