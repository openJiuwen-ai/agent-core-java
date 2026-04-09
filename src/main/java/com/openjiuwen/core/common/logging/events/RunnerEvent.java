/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.common.logging.events;

import com.openjiuwen.core.common.schema.BaseCard;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/** Runner event. */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class RunnerEvent extends BaseLogEvent {
    private String runnerId;
    private Object inputs;
    private Object outputs;
    private Object chunk;
    private Object envs;
    private String resourceId;
    private String resourceType;
    private Object tag;
    private BaseCard card;

    public RunnerEvent() {
        super();
    }

    @Override
    protected void addFieldsToMap(Map<String, Object> map) {
        putIfNotNull(map, "runner_id", runnerId);
        putIfNotNull(map, "inputs", inputs);
        putIfNotNull(map, "outputs", outputs);
        putIfNotNull(map, "chunk", chunk);
        putIfNotNull(map, "envs", envs);
        putIfNotNull(map, "resource_id", resourceId);
        putIfNotNull(map, "resource_type", resourceType);
        putIfNotNull(map, "tag", tag);
        putIfNotNull(map, "card", card);
    }
}
