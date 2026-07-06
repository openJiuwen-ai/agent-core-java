/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.schema.deep_agent_spec;

import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Serializable system operation specification.
 * Mirrors Python SysOperationSpec.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SysOperationSpec {

    private String id;
    @Builder.Default
    private OperationMode mode = OperationMode.LOCAL;
    @Builder.Default
    private LocalWorkConfig workConfig = null;
    @Builder.Default
    private SandboxGatewayConfig gatewayConfig = null;
}
