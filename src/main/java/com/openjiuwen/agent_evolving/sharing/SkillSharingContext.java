/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.sharing;

/**
 * Mirrors Python's skill sharing context tuple in
 * {@code openjiuwen/agent_evolving/sharing/experience_sharer.py}.
 */
public class SkillSharingContext {

    private final String skillId;
    private final byte[] packageBytes;
    private final String skillName;
    private final String description;

    public SkillSharingContext(String skillId, byte[] packageBytes, String skillName, String description) {
        this.skillId = skillId == null ? "" : skillId;
        this.packageBytes = packageBytes == null ? new byte[0] : packageBytes.clone();
        this.skillName = skillName == null ? "" : skillName;
        this.description = description == null ? "" : description;
    }

    public String getSkillId() {
        return skillId;
    }

    public byte[] getPackageBytes() {
        return packageBytes.clone();
    }

    public String getSkillName() {
        return skillName;
    }

    public String getDescription() {
        return description;
    }
}
