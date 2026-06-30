/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.interrupt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request payload describing a pending tool interruption.
 *
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterruptRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private String interruptId;
    private String message;

    @Builder.Default
    private Map<String, Object> context = new LinkedHashMap<String, Object>();

    @Builder.Default
    private Map<String, Object> payloadSchema = new LinkedHashMap<String, Object>();

    private String autoConfirmKey;
}
