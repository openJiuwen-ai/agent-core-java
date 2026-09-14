/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.experience;

import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Experience;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExperienceType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Synthesizes recent experiences into an active-context markdown string.
 *
 * <p>Mirrors Python's {@code ActiveContextSynthesizer} in
 * {@code openjiuwen/auto_harness/experience/synthesizer.py}.</p>
 */
public class ActiveContextSynthesizer {

    private static final int ONE_DAY_SECONDS = 86_400;
    private static final int SEVEN_DAYS_SECONDS = 7 * ONE_DAY_SECONDS;
    private static final double WEIGHT_RECENT = 1.0;
    private static final double WEIGHT_MEDIUM = 0.5;
    private static final double WEIGHT_OLD = 0.2;
    private static final double CHARS_PER_TOKEN = 3.3;
    private static final Map<ExperienceType, String> SECTION_HEADERS = sectionHeaders();

    private final ExperienceStore store;

    public ActiveContextSynthesizer(String experienceDir) {
        this.store = new ExperienceStore(experienceDir);
    }

    public CompletableFuture<String> synthesize(List<Experience> experiences) {
        return synthesize(experiences, 2000);
    }

    public CompletableFuture<String> synthesize(List<Experience> experiences, int maxTokens) {
        if (experiences == null || experiences.isEmpty()) {
            return CompletableFuture.completedFuture("");
        }

        double now = epochSeconds();
        Map<ExperienceType, List<WeightedExperience>> grouped = new LinkedHashMap<>();
        for (Experience experience : experiences) {
            ExperienceType type = experience.getType();
            grouped.computeIfAbsent(type, ignored -> new ArrayList<>())
                    .add(new WeightedExperience(timeWeight(experience.getTimestamp(), now), experience));
        }
        for (List<WeightedExperience> items : grouped.values()) {
            items.sort(Comparator.comparingDouble(WeightedExperience::weight).reversed());
        }

        int maxChars = (int) (maxTokens * CHARS_PER_TOKEN);
        List<String> parts = new ArrayList<>();
        int budget = maxChars;

        for (ExperienceType experienceType : List.of(
                ExperienceType.OPTIMIZATION,
                ExperienceType.FAILURE,
                ExperienceType.INSIGHT)) {
            List<WeightedExperience> items = grouped.get(experienceType);
            if (items == null || items.isEmpty()) {
                continue;
            }

            List<String> sectionLines = new ArrayList<>();
            sectionLines.add(SECTION_HEADERS.get(experienceType));
            for (WeightedExperience item : items) {
                sectionLines.add(formatLine(item.experience()));
            }
            String section = String.join("\n", sectionLines);
            if (section.length() > budget) {
                parts.add(pythonPrefix(section, budget));
                break;
            }
            parts.add(section);
            budget -= section.length() + 1;
        }

        return CompletableFuture.completedFuture(String.join("\n\n", parts));
    }

    public CompletableFuture<String> loadAndSynthesize() {
        return loadAndSynthesize(30);
    }

    public CompletableFuture<String> loadAndSynthesize(int topK) {
        return store.listRecent(topK).thenCompose(this::synthesize);
    }

    static double timeWeight(double timestamp, double now) {
        double age = now - timestamp;
        if (age <= ONE_DAY_SECONDS) {
            return WEIGHT_RECENT;
        }
        if (age <= SEVEN_DAYS_SECONDS) {
            return WEIGHT_MEDIUM;
        }
        return WEIGHT_OLD;
    }

    static String formatLine(Experience experience) {
        String base = "- " + stringValue(experience.getTopic()) + ": " + stringValue(experience.getSummary());
        String outcome = stringValue(experience.getOutcome());
        return outcome.isEmpty() ? base : base + " (" + outcome + ")";
    }

    private static String pythonPrefix(String text, int end) {
        int resolvedEnd = end >= 0 ? Math.min(end, text.length()) : Math.max(text.length() + end, 0);
        return text.substring(0, resolvedEnd);
    }

    private static Map<ExperienceType, String> sectionHeaders() {
        Map<ExperienceType, String> headers = new LinkedHashMap<>();
        headers.put(ExperienceType.OPTIMIZATION, "### 近期优化经验");
        headers.put(ExperienceType.FAILURE, "### 失败教训");
        headers.put(ExperienceType.INSIGHT, "### 关键洞察");
        return headers;
    }

    private static String stringValue(String value) {
        return value == null ? "" : value;
    }

    private static double epochSeconds() {
        return System.currentTimeMillis() / 1000.0;
    }

    private record WeightedExperience(double weight, Experience experience) {
    }
}
