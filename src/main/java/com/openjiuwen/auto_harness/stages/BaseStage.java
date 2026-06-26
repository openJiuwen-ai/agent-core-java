/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageSpec;
import com.openjiuwen.core.session.stream.OutputSchema;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base interface for all stages.
 *
 * <p>Mirrors Python's {@code BaseStage} in
 * {@code openjiuwen/auto_harness/stages/base.py}.</p>
 */
public abstract class BaseStage {

    public String name() {
        return "";
    }

    public String displayName() {
        return "";
    }

    public String description() {
        return "";
    }

    public String slot() {
        return "";
    }

    public List<String> consumes() {
        return List.of();
    }

    public List<String> produces() {
        return List.of();
    }

    public String scope() {
        return "session";
    }

    public StageSpec spec() {
        return StageSpec.builder()
                .name(name())
                .stageCls(getClass())
                .description(description())
                .consumes(List.copyOf(consumes()))
                .produces(List.copyOf(produces()))
                .scope(scope())
                .slot(slot())
                .build();
    }

    public Iterator<Object> stream(BaseExecutionContext ctx) {
        throw new UnsupportedOperationException("Stage stream not implemented");
    }

    public static Object scopeOutputEventStage(Object event, String stage) {
        if (stage == null || stage.isEmpty() || !(event instanceof OutputSchema output)) {
            return event;
        }
        if (!"message".equals(output.getType()) && !"stage_result".equals(output.getType())) {
            return event;
        }
        if (!(output.getPayload() instanceof Map<?, ?> sourcePayload)) {
            return event;
        }
        Object currentStage = sourcePayload.get("stage");
        if (stage.equals(currentStage)) {
            return event;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : sourcePayload.entrySet()) {
            payload.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        payload.put("stage", stage);
        return new OutputSchema(output.getType(), output.getIndex(), payload);
    }
}
