/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.models;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One model allocator result.
 *
 * <p>Mirrors Python's {@code Allocation} in
 * {@code openjiuwen/agent_teams/models/allocator.py}.</p>
 */
public final class Allocation implements com.openjiuwen.agent_teams.agent.AgentConfigurator.Allocation {

    private final ModelPoolEntry entry;
    private final int groupIndex;

    public Allocation(ModelPoolEntry entry, int groupIndex) {
        this.entry = Objects.requireNonNull(entry, "entry");
        this.groupIndex = groupIndex;
    }

    @Override
    public ModelPoolEntry.TeamModelConfig toTeamModelConfig() {
        return entry.toTeamModelConfig();
    }

    public Map<String, Object> toDbRef() {
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("model_name", entry.getModelName());
        ref.put("model_index", groupIndex);
        return ref;
    }

    public ModelPoolEntry getEntry() {
        return entry;
    }

    public int getGroupIndex() {
        return groupIndex;
    }
}
