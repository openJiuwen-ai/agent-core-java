/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving;

import java.util.List;

/**
 * Priority-aware score for a detected skill reference.
 *
 * <p>Mirrors Python's {@code SkillReferenceScore} in
 * {@code openjiuwen/agent_evolving/utils.py}.</p>
 */
public final class SkillReferenceScore {

    private int skillToolHits;
    private int skillsPathHits;
    private int legacySkillMdHits;

    public SkillReferenceScore() {
    }

    public SkillReferenceScore(int skillToolHits, int skillsPathHits, int legacySkillMdHits) {
        this.skillToolHits = skillToolHits;
        this.skillsPathHits = skillsPathHits;
        this.legacySkillMdHits = legacySkillMdHits;
    }

    public List<Integer> rankingKey() {
        return List.of(skillToolHits, skillsPathHits, legacySkillMdHits);
    }

    public void incrementSkillToolHits() {
        skillToolHits++;
    }

    public void incrementSkillsPathHits() {
        skillsPathHits++;
    }

    public void incrementLegacySkillMdHits() {
        legacySkillMdHits++;
    }

    public int getSkillToolHits() {
        return skillToolHits;
    }

    public int getSkillsPathHits() {
        return skillsPathHits;
    }

    public int getLegacySkillMdHits() {
        return legacySkillMdHits;
    }
}
