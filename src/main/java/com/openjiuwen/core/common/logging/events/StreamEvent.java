/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.common.logging.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/** Stream related event — base class for all streaming events. */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class StreamEvent extends BaseLogEvent {
    private String streamType;
    private Integer chunkIndex;
    private Integer frameCount;
    private String streamId;

    public StreamEvent() {
        super();
    }

    @Override
    protected void addFieldsToMap(Map<String, Object> map) {
        putIfNotNull(map, "stream_type", streamType);
        putIfNotNull(map, "chunk_index", chunkIndex);
        putIfNotNull(map, "frame_count", frameCount);
        putIfNotNull(map, "stream_id", streamId);
    }
}
