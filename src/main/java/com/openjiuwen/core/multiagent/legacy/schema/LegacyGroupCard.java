/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.legacy.schema;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Legacy Group Card.
 * <p>
 * Mirrors Python's legacy {@code GroupCard} in {@code multi_agent/legacy/schema/group_card.py}.
 *
 * @deprecated Use {@link com.openjiuwen.core.multiagent.schema.GroupCard}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Deprecated
public class LegacyGroupCard extends BaseCard {

    private List<AgentCard> agentCard = new ArrayList<>();

    private String topic = "";
}
