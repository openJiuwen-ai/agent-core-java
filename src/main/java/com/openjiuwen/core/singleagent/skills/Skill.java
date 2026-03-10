// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.singleagent.skills;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a skill with its metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Skill {

    private String name;
    private String description;
    private String directory;

    @Override
    public String toString() {
        return "Skill: " + name + "\nDescription: " + description + "\nDirectory: " + directory;
    }
}
