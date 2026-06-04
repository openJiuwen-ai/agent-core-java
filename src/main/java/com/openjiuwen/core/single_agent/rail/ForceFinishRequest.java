/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.rail;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Signal to terminate an agent loop and return a result immediately.
 *
 * <p>Mirrors Python's {@code ForceFinishRequest} in
 * {@code openjiuwen.core.single_agent.rail.base}.</p>
 */
@Data
@Builder
public class ForceFinishRequest {
    private Map<String, Object> result;
}
