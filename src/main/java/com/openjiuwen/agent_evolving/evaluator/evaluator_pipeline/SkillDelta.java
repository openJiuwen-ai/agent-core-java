/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Skill diff between iterations.
 * <p>
 * Mirrors Python's {@code SkillDelta} in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/models.py}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillDelta {

    private Map<String, String> skills = new LinkedHashMap<>();
    private Map<String, String> evolutions = new LinkedHashMap<>();
    private Map<String, Map<String, String>> evolutionFiles = new LinkedHashMap<>();
    private boolean changed = false;
}
