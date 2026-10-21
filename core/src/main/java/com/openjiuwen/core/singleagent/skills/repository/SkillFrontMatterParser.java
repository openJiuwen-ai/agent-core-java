/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills.repository;

import java.util.HashMap;
import java.util.Map;

/** Parses YAML front matter key-value pairs from a SKILL.md text body. */
final class SkillFrontMatterParser {

    private SkillFrontMatterParser() {}

    static Map<String, String> parse(String text) {
        Map<String, String> result = new HashMap<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        String[] lines = text.lines().toArray(String[]::new);
        if (lines.length == 0 || !"---".equals(lines[0].trim())) {
            return result;
        }
        int end = -1;
        for (int i = 1; i < lines.length; i++) {
            if ("---".equals(lines[i].trim())) {
                end = i;
                break;
            }
        }
        if (end < 0) {
            return result;
        }
        for (int i = 1; i < end; i++) {
            String line = lines[i];
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator).strip();
            String value = line.substring(separator + 1).strip();
            if (!key.isEmpty()) {
                result.put(key, value);
            }
        }
        return result;
    }
}
