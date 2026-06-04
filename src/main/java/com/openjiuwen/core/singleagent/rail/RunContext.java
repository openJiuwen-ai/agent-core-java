/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Optional runtime context attached to {@link InvokeInputs}.
 *
 * <p>Mirrors Python's {@code RunContext} in
 * {@code openjiuwen.core.single_agent.rail.base}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunContext {

    private HeartbeatReason reason;
    private String sessionId;
    private String contextMode;

    @Builder.Default
    private Map<String, Object> extra = new LinkedHashMap<>();
}
