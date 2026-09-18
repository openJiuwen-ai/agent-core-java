/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.experience;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ExperienceTypeUtils {

    private ExperienceTypeUtils() {
    }

    static String newPendingChangeId() {
        return "skill_evolve_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    static String utcNowIso() {
        return OffsetDateTime.now(ZoneOffset.UTC).toString();
    }

    static <T> List<T> copyList(List<T> values) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(values);
    }

    static List<String> copyStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(values);
    }

    static List<Map<String, Object>> copyMessageList(List<Map<String, Object>> values) {
        if (values == null) {
            return null;
        }
        List<Map<String, Object>> copied = new ArrayList<>(values.size());
        for (Map<String, Object> value : values) {
            copied.add(copyMap(value));
        }
        return copied;
    }

    static Map<String, Object> copyMap(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(values);
    }
}
