/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.operator;

import com.openjiuwen.agent_evolving.ApplyResult;
import com.openjiuwen.agent_evolving.Protocols;
import com.openjiuwen.agent_evolving.UpdateValue;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Base self-evolution parameter handle.
 *
 * <p>Mirrors Python's {@code Operator} in
 * {@code openjiuwen/core/operator/base.py}.
 */
public abstract class Operator {

    /**
     * Unique operator identifier used for attribution and checkpointing.
     *
     * @return operator identifier
     */
    public abstract String getOperatorId();

    /**
     * Describes operator tunables. Frozen parameters must be excluded.
     *
     * @return tunable definitions keyed by tunable name
     */
    public abstract Map<String, TunableSpec> getTunables();

    /**
     * Returns current operator state for checkpointing and rollback.
     *
     * @return serializable state snapshot
     */
    public abstract Map<String, Object> getState();

    /**
     * Applies one direct parameter replacement.
     *
     * @param target parameter name
     * @param value new value
     */
    public abstract void setParameter(String target, Object value);

    /**
     * Applies a structured update using the Python compatibility behavior.
     *
     * @param target parameter name
     * @param update structured update request
     * @return apply result
     */
    public ApplyResult applyUpdate(String target, UpdateValue update) {
        if (!Objects.equals(update.getMode(), Protocols.REPLACE_MODE)
                || !Objects.equals(update.getEffect(), Protocols.STATE_EFFECT)) {
            return ApplyResult.builder()
                    .operatorId(getOperatorId())
                    .target(target)
                    .applied(false)
                    .mode(update.getMode())
                    .effect(update.getEffect())
                    .value(update.getPayload())
                    .errors(List.of(
                            "unsupported update mode/effect for compatibility operator: "
                                    + update.getMode() + "/" + update.getEffect()
                    ))
                    .metadata(update.getMetadata())
                    .build();
        }

        Map<String, Object> beforeState = getState();
        setParameter(target, update.getPayload());
        Map<String, Object> afterState = getState();
        return ApplyResult.builder()
                .operatorId(getOperatorId())
                .target(target)
                .applied(!Objects.equals(beforeState, afterState))
                .mode(update.getMode())
                .effect(update.getEffect())
                .value(update.getPayload())
                .metadata(update.getMetadata())
                .build();
    }

    /**
     * Restores state from a checkpoint snapshot.
     *
     * @param state state snapshot
     */
    public abstract void loadState(Map<String, Object> state);
}
