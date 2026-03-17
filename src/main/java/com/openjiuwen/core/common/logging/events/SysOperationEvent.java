/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.logging.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/** SysOperation event. */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SysOperationEvent extends BaseLogEvent {
    private String operationName;
    private String operationMode;
    private String operationDesc;
    private String methodName;
    private Map<String, Object> methodParams;
    private Map<String, Object> methodResult;
    private Double methodExecTimeMs;

    public SysOperationEvent() {
        super();
        setModuleType(ModuleType.SYS_OPERATION);
    }

    @Override
    protected void addFieldsToMap(Map<String, Object> map) {
        putIfNotNull(map, "operation_name", operationName);
        putIfNotNull(map, "operation_mode", operationMode);
        putIfNotNull(map, "operation_desc", operationDesc);
        putIfNotNull(map, "method_name", methodName);
        putIfNotNull(map, "method_params", methodParams);
        putIfNotNull(map, "method_result", methodResult);
        putIfNotNull(map, "method_exec_time_ms", methodExecTimeMs);
    }
}
