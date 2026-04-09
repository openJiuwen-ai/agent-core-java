  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.multiagent.schema;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Group Identity Card.
 * Mirrors Python's {@code GroupCard} in {@code multi_agent/schema/group_card.py}.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class GroupCard extends BaseCard {

    @Builder.Default
    private List<AgentCard> agentCards = new ArrayList<>();

    @Builder.Default
    private String topic = "";

    @Builder.Default
    private String version = "1.0.0";

    @Builder.Default
    private List<String> tags = new ArrayList<>();
}
