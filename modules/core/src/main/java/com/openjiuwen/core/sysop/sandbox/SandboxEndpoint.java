/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resolved sandbox endpoint descriptor.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxEndpoint {
    private String baseUrl;

    private String sandboxId;
}
