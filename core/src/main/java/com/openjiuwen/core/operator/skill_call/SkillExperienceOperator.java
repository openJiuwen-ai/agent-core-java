/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.skill_call;

import com.openjiuwen.agentevolving.ApplyResult;
import com.openjiuwen.agentevolving.Protocols;
import com.openjiuwen.agentevolving.UpdateValue;
import com.openjiuwen.core.operator.PreviewableOperator;
import com.openjiuwen.core.operator.TunableSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Mirrors Python's {@code SkillExperienceOperator} in
 * {@code openjiuwen/core/operator/skill_call/base.py}.
 */
public class SkillExperienceOperator extends PreviewableOperator {

    private final String skillName;
    private final BiConsumer<String, Object> onParameterUpdated;

    public SkillExperienceOperator(String skillName) {
        this(skillName, null);
    }

    public SkillExperienceOperator(String skillName, BiConsumer<String, Object> onParameterUpdated) {
        this.skillName = skillName;
        this.onParameterUpdated = onParameterUpdated;
    }

    @Override
    public String getOperatorId() {
        return "skill_experience_" + skillName;
    }

    @Override
    public Map<String, TunableSpec> getTunables() {
        return Map.of(
                Protocols.EXPERIENCES_TARGET,
                new TunableSpec(
                        Protocols.EXPERIENCES_TARGET,
                        "skill_experience",
                        "content",
                        Map.of("type", "record")
                )
        );
    }

    @Override
    public void setParameter(String target, Object value) {
        if (!Protocols.EXPERIENCES_TARGET.equals(target) || value == null) {
            return;
        }
        List<Object> items = toList(value);
        if (onParameterUpdated != null) {
            onParameterUpdated.accept(target, items);
        }
    }

    @Override
    public ApplyResult previewUpdate(String target, UpdateValue update) {
        if (!Protocols.EXPERIENCES_TARGET.equals(target)) {
            return ApplyResult.builder()
                    .operatorId(getOperatorId())
                    .target(target)
                    .applied(false)
                    .mode(update.getMode())
                    .effect(update.getEffect())
                    .value(update.getPayload())
                    .changeType(update.getChangeType())
                    .errors(List.of("unsupported target for SkillExperienceOperator: " + target))
                    .metadata(new LinkedHashMap<>(update.getMetadata()))
                    .build();
        }

        if (!Protocols.PENDING_CHANGE_EFFECT.equals(update.getEffect())
                || !Set.of(Protocols.APPEND_MODE, Protocols.MERGE_MODE).contains(update.getMode())) {
            return ApplyResult.builder()
                    .operatorId(getOperatorId())
                    .target(target)
                    .applied(false)
                    .mode(update.getMode())
                    .effect(update.getEffect())
                    .value(update.getPayload())
                    .changeType(update.getChangeType())
                    .errors(List.of(
                            "unsupported update mode/effect for SkillExperienceOperator: "
                                    + update.getMode() + "/" + update.getEffect()
                    ))
                    .metadata(new LinkedHashMap<>(update.getMetadata()))
                    .build();
        }

        List<Object> records = toList(update.getPayload());
        Map<String, Object> metadata = new LinkedHashMap<>(update.getMetadata());
        metadata.put("skill_name", skillName);
        return ApplyResult.builder()
                .operatorId(getOperatorId())
                .target(target)
                .applied(!records.isEmpty())
                .mode(update.getMode())
                .effect(update.getEffect())
                .value(update.getPayload())
                .records(records)
                .changeType(update.getChangeType())
                .lifecycleStage(Protocols.LOCAL_APPLY_COMPLETED)
                .metadata(metadata)
                .build();
    }

    @Override
    public Map<String, Object> getState() {
        return Map.of();
    }

    @Override
    public void loadState(Map<String, Object> state) {
        // Python implementation is intentionally stateless.
    }

    protected String getSkillName() {
        return skillName;
    }

    private static List<Object> toList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        List<Object> items = new ArrayList<>();
        items.add(value);
        return items;
    }
}
