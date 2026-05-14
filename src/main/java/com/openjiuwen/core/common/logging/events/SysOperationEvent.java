/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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

    public static SysOperationEventBuilder builder() {
        return new SysOperationEventBuilder();
    }

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

    public static final class SysOperationEventBuilder {
        private String operationName;
        private String operationMode;
        private String operationDesc;
        private String methodName;
        private Map<String, Object> methodParams;
        private Map<String, Object> methodResult;
        private Double methodExecTimeMs;

        public SysOperationEventBuilder operationName(String operationName) { this.operationName = operationName; return this; }
        public SysOperationEventBuilder operationMode(String operationMode) { this.operationMode = operationMode; return this; }
        public SysOperationEventBuilder operationDesc(String operationDesc) { this.operationDesc = operationDesc; return this; }
        public SysOperationEventBuilder methodName(String methodName) { this.methodName = methodName; return this; }
        public SysOperationEventBuilder methodParams(Map<String, Object> methodParams) { this.methodParams = methodParams; return this; }
        public SysOperationEventBuilder methodResult(Map<String, Object> methodResult) { this.methodResult = methodResult; return this; }
        public SysOperationEventBuilder methodExecTimeMs(Double methodExecTimeMs) { this.methodExecTimeMs = methodExecTimeMs; return this; }

        public SysOperationEvent build() {
            SysOperationEvent event = new SysOperationEvent();
            event.operationName = operationName;
            event.operationMode = operationMode;
            event.operationDesc = operationDesc;
            event.methodName = methodName;
            event.methodParams = methodParams;
            event.methodResult = methodResult;
            event.methodExecTimeMs = methodExecTimeMs;
            return event;
        }
    }
}
