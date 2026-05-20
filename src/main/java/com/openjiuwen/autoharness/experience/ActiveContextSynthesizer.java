/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.experience;

import com.openjiuwen.autoharness.schema.Experience;
import com.openjiuwen.autoharness.schema.ExperienceType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Public class ActiveContextSynthesizer used by the Java parity implementation.
 *
 * @since 1.0
 */
public class ActiveContextSynthesizer {
    private static final long ONE_DAY_SECS = 86_400L;
    private static final long SEVEN_DAY_SECS = 7L * ONE_DAY_SECS;
    private final ExperienceStore store;

    /**
     * Auto-generated for codecheck compliance.
     */
    public ActiveContextSynthesizer(String experienceDir) {
        this.store = new ExperienceStore(experienceDir);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String synthesize(List<Experience> experiences, int maxTokens) {
        if (experiences == null || experiences.isEmpty()) {
            return "";
        }
        Map<ExperienceType, List<Experience>> grouped = new EnumMap<>(ExperienceType.class);
        for (Experience experience : experiences) {
            grouped.computeIfAbsent(experience.getType(), ignored -> new ArrayList<>()).add(experience);
        }
        long now = System.currentTimeMillis() / 1000;
        grouped.values().forEach(items -> items.sort(
                Comparator.comparingDouble((Experience exp) -> timeWeight(exp.getTimestamp(), now)).reversed()
        ));
        StringBuilder builder = new StringBuilder();
        appendSection(builder, "### 近期优化经验", grouped.get(ExperienceType.OPTIMIZATION));
        appendSection(builder, "### 失败教训", grouped.get(ExperienceType.FAILURE));
        appendSection(builder, "### 关键洞察", grouped.get(ExperienceType.INSIGHT));
        int maxChars = (int) (maxTokens * 3.3);
        String result = builder.toString().trim();
        return result.length() > maxChars ? result.substring(0, maxChars) : result;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String loadAndSynthesize(int topK) throws IOException {
        return synthesize(store.listRecent(topK), 2000);
    }

    private static void appendSection(StringBuilder builder, String header, List<Experience> experiences) {
        if (experiences == null || experiences.isEmpty()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append("\n\n");
        }
        builder.append(header).append('\n');
        for (Experience experience : experiences) {
            builder.append("- ")
                    .append(experience.getTopic())
                    .append(": ")
                    .append(experience.getSummary());
            if (experience.getOutcome() != null && !experience.getOutcome().isBlank()) {
                builder.append(" (").append(experience.getOutcome()).append(')');
            }
            builder.append('\n');
        }
    }

    static double timeWeight(long timestamp, long now) {
        long age = now - timestamp;
        if (age <= ONE_DAY_SECS) {
            return 1.0;
        }
        if (age <= SEVEN_DAY_SECS) {
            return 0.5;
        }
        return 0.2;
    }
}
