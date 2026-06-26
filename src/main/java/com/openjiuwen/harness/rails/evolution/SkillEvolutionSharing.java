/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import java.util.ArrayList;
import java.util.List;

/**
 * Sharing helpers for generated skill experience records.
 *
 * <p>Mirrors Python's {@code SkillEvolutionSharingMixin} in
 * {@code openjiuwen/harness/rails/evolution/skill_evolution_sharing.py}.</p>
 */
public class SkillEvolutionSharing {

    public static final String SHARED_RECORD_CONTEXT_MARKER = "[shared origin=";
    public static final int DEFAULT_SHARING_MAX_UPLOAD_RETRIES = 3;

    private final List<String> sharedRecords = new ArrayList<>();

    public void recordSharedExperience(String record) {
        if (record != null && !record.isBlank() && !sharedRecords.contains(record)) {
            sharedRecords.add(record);
        }
    }

    public List<String> getSharedRecords() {
        return new ArrayList<>(sharedRecords);
    }
}
