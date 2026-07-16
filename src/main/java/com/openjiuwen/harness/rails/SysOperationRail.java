/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

/**
 * Public class SysOperationRail used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class SysOperationRail extends DeepAgentRail {
    /**
     * priority.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int priority() {
        return 60;
    }

    /**
     * describe.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String describe() {
        return "Expose sys_operation tools";
    }
}
