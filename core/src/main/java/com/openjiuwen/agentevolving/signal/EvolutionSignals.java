/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.signal;

import java.util.HashMap;
import java.util.Map;

/**
 * Mirrors Python's helper functions in {@code openjiuwen/agent_evolving/signal/base.py}.
 */
public final class EvolutionSignals {

    private EvolutionSignals() {
    }

    public static EvolutionSignal makeEvolutionSignal(
            String signalType,
            String section,
            String excerpt,
            String toolName,
            String skillName,
            String source,
            Map<String, Object> context
    ) {
        Map<String, Object> mergedContext = new HashMap<>();
        if (context != null) {
            mergedContext.putAll(context);
        }
        if (source != null) {
            mergedContext.putIfAbsent("source", source);
        }
        if (toolName != null) {
            mergedContext.putIfAbsent("tool_name", toolName);
        }
        return EvolutionSignal.builder()
                .signalType(signalType)
                .section(section)
                .excerpt(excerpt)
                .skillName(skillName)
                .context(mergedContext.isEmpty() ? null : mergedContext)
                .build();
    }

    public static String getSignalSource(EvolutionSignal signal) {
        if (signal == null || signal.getContext() == null) {
            return null;
        }
        Object source = signal.getContext().get("source");
        return source == null ? null : String.valueOf(source);
    }

    public static String[] makeSignalFingerprint(EvolutionSignal signal) {
        Map<String, Object> context = signal.getContext();
        Object toolName = context == null ? null : context.get("tool_name");
        return new String[] {
                signal.getSignalType(),
                toolName == null ? "" : String.valueOf(toolName),
                signal.getSkillName() == null ? "" : signal.getSkillName(),
                signal.getExcerpt() == null ? "" : signal.getExcerpt().substring(0, Math.min(200, signal.getExcerpt().length()))
        };
    }
}
