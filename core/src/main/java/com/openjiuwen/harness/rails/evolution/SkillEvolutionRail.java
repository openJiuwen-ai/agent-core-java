/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.openjiuwen.harness.rails.CallbackContext;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Skill experience generation and approval rail.
 *
 * <p>Mirrors Python's {@code SkillEvolutionRail} in
 * {@code openjiuwen/harness/rails/evolution/skill_evolution_rail.py}.</p>
 */
public class SkillEvolutionRail extends EvolutionRail {

    public static final int MAX_PROCESSED_SIGNAL_KEYS = 500;

    private final Path skillsDir;
    private final Set<String> processedSignalKeys = new LinkedHashSet<>();
    private boolean autoSave = true;
    private boolean autoScan = true;

    public SkillEvolutionRail(Path skillsDir) {
        this.skillsDir = skillsDir;
        setPriority(78);
    }

    @Override
    public void afterToolCall(CallbackContext ctx) {
        super.afterToolCall(ctx);
        String signalKey = String.valueOf(ctx.getValues().getOrDefault("tool_name", ""));
        if (!signalKey.isBlank()) {
            processedSignalKeys.add(signalKey);
            trimProcessedSignals();
        }
    }

    public Path getSkillsDir() {
        return skillsDir;
    }

    public Set<String> getProcessedSignalKeys() {
        return new LinkedHashSet<>(processedSignalKeys);
    }

    public void clearProcessedSignals() {
        processedSignalKeys.clear();
    }

    public boolean isAutoSave() {
        return autoSave;
    }

    public void setAutoSave(boolean autoSave) {
        this.autoSave = autoSave;
    }

    public boolean isAutoScan() {
        return autoScan;
    }

    public void setAutoScan(boolean autoScan) {
        this.autoScan = autoScan;
    }

    private void trimProcessedSignals() {
        while (processedSignalKeys.size() > MAX_PROCESSED_SIGNAL_KEYS) {
            String first = processedSignalKeys.iterator().next();
            processedSignalKeys.remove(first);
        }
    }
}
