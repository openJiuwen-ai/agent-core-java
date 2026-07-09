/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a skill with its metadata.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Skill {
    private String name;
    private String description;
    private String directory;
    @Builder.Default
    private long updateAt = 0L;

    /**
     * toString.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String toString() {
        return "Skill: " + name + "\nDescription: " + description + "\nDirectory: " + directory;
    }

    /**
     * Compact representation matching Python's {@code __repr__()}.
     * Truncates description to 30 chars.
     * 
     * @return a compact single-line representation
     * @since 0.1.7
     */
    public String toRepr() {
        String desc = description != null ? description : "";
        String truncated = desc.length() > 30 ? desc.substring(0, 30) + "..." : desc;
        return "[Skill: " + name + " / Description: " + truncated + " / Directory: " + directory + "]";
    }
}
