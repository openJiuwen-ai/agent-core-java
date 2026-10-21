/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills.repository;

import com.openjiuwen.core.singleagent.skills.Skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges multiple Skill repositories from low to high priority.
 *
 * <p>Same-name Skills from higher-priority repositories override lower-priority versions. Output
 * order is stable. Removing a high-priority Skill falls back to the lower-priority version on the
 * next load.
 *
 * @since 0.1.17
 */
public final class CompositeSkillRepository implements SkillRepository {
    private final List<SkillRepository> repositories;

    public CompositeSkillRepository(List<SkillRepository> repositories) {
        this.repositories = repositories == null ? List.of() : List.copyOf(repositories);
    }

    @Override
    public com.openjiuwen.core.singleagent.skills.SkillSource source() {
        return com.openjiuwen.core.singleagent.skills.SkillSource.FILESYSTEM;
    }

    @Override
    public List<Skill> loadAll() {
        Map<String, Skill> merged = new LinkedHashMap<>();
        for (SkillRepository repository : repositories) {
            for (Skill skill : repository.loadAll()) {
                if (skill != null && skill.getName() != null && !skill.getName().isBlank()) {
                    merged.put(skill.getName(), skill);
                }
            }
        }
        return List.copyOf(new ArrayList<>(merged.values()));
    }
}
