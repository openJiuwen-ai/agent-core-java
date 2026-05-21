/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.interrupt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * Request for user interruption/confirmation.
 *
 * <p>Mirrors Python's {@code InterruptRequest} in
 * {@code openjiuwen.core.single_agent.interrupt.response}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterruptRequest {

    @Builder.Default
    private String message = "";

    @Builder.Default
    private Map<String, Object> payloadSchema = new HashMap<>();

    @Builder.Default
    private String autoConfirmKey = "";
}