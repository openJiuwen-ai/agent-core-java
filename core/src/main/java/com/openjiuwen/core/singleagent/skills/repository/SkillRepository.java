/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills.repository;

import com.openjiuwen.core.singleagent.skills.Skill;
import com.openjiuwen.core.singleagent.skills.SkillSource;

import java.util.List;

/**
 * SPI for loading Skill snapshots from a single origin layer.
 *
 * <p>Implementations must return immutable snapshots; refresh capability is not part of the public
 * SPI.
 *
 * @since 0.1.17
 */
public interface SkillRepository {
    /** Returns the origin layer represented by this repository. */
    SkillSource source();

    /** Loads a read-only snapshot of every valid Skill in this layer. */
    List<Skill> loadAll();
}
