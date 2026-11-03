/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.pipelines;

import com.openjiuwen.auto_harness.stages.BaseStage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Slot to stage class binding for a pipeline.
 *
 * <p>Mirrors Python's {@code PipelineStageMap} in
 * {@code openjiuwen/auto_harness/pipelines/base.py}.</p>
 */
public class PipelineStageMap {

    private final Map<String, Class<? extends BaseStage>> mapping;

    public PipelineStageMap() {
        this(Map.of());
    }

    public PipelineStageMap(Map<String, Class<? extends BaseStage>> mapping) {
        this.mapping = new LinkedHashMap<>(mapping == null ? Map.of() : mapping);
    }

    public BaseStage resolve(String slot) {
        Class<? extends BaseStage> stageClass = mapping.get(slot);
        if (stageClass == null) {
            throw new NoSuchElementException("No stage bound for slot '" + slot + "'");
        }
        try {
            return stageClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to instantiate stage for slot '" + slot + "'", e);
        }
    }

    public Map<String, Class<? extends BaseStage>> getMapping() {
        return new LinkedHashMap<>(mapping);
    }
}
