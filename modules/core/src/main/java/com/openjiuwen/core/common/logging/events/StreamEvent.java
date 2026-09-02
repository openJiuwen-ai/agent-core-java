/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Stream related event — base class for all streaming events.
 * 
 * @since 0.1.7
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class StreamEvent extends BaseLogEvent {
    private String streamType;
    private Integer chunkIndex;
    private Integer frameCount;
    private String streamId;

    /**
     * StreamEvent.
     * 
     * @since 0.1.7
     */
    public StreamEvent() {
        super();
    }

    /**
     * addFieldsToMap.
     * 
     * @param map map
     * @since 0.1.7
     */
    @Override
    protected void addFieldsToMap(Map<String, Object> map) {
        putIfNotNull(map, "stream_type", streamType);
        putIfNotNull(map, "chunk_index", chunkIndex);
        putIfNotNull(map, "frame_count", frameCount);
        putIfNotNull(map, "stream_id", streamId);
    }
}
