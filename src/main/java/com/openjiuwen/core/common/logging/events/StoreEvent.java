/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public StoreEvent() {
        super();
        setModuleType(ModuleType.STORE);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    protected void addFieldsToMap(Map<String, Object> map) {
        putIfNotNull(map, "table_name", tableName);
        putIfNotNull(map, "data_num", dataNum);
    }
}
