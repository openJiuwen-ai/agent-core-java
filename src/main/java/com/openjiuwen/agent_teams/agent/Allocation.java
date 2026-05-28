/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One allocator result.
 * <p>
 * Carries the picked pool entry plus the position needed to persist a
 * DB reference. Helpers materialize the runtime config and the DB ref
 * so call sites never touch the entry directly.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_teams.agent.model_allocator.Allocation}.
 */
public class Allocation {

    private final ModelPoolEntry entry;
    private final int groupIndex;

    public Allocation(ModelPoolEntry entry, int groupIndex) {
        this.entry = entry;
        this.groupIndex = groupIndex;
    }

    public ModelPoolEntry getEntry() {
        return entry;
    }

    public int getGroupIndex() {
        return groupIndex;
    }

    public Map<String, Object> toTeamModelConfig() {
        if (entry == null) {
            return new LinkedHashMap<>();
        }
        return entry.toTeamModelConfig();
    }

    public Map<String, Object> toDbRef() {
        Map<String, Object> ref = new LinkedHashMap<>();
        ref.put("model_name", entry != null ? entry.getModelName() : null);
        ref.put("model_index", groupIndex);
        return ref;
    }
}