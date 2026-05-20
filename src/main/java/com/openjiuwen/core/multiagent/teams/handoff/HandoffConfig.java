/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class HandoffConfig used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class HandoffConfig {
    private AgentCard startAgent;

    @Builder.Default
    private int maxHandoffs = 10;

    @Builder.Default
    private List<HandoffRoute> routes = new ArrayList<>();

    private Predicate<HandoffOrchestrator> terminationCondition;
}
